package io.github.tabilzad.ktor.k2

import io.github.tabilzad.ktor.*
import io.github.tabilzad.ktor.k2.inference.CallRespondInference
import io.github.tabilzad.ktor.k2.inference.ResponseInferenceRule
import io.github.tabilzad.ktor.k2.visitors.*
import io.github.tabilzad.ktor.output.OpenApiSpec
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.fir.BuiltinTypes
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.references.resolved
import org.jetbrains.kotlin.fir.references.toResolvedFunctionSymbol
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.fir.visitors.FirDefaultVisitor
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi
import org.jetbrains.kotlin.util.PrivateForInline

internal class ExpressionsVisitorK2(
    private val config: PluginConfiguration,
    private val context: CheckerContext,
    private val session: FirSession,
    private val log: MessageCollector?
) : FirDefaultVisitor<List<KtorElement>, KtorElement?>() {

    val classNames = mutableListOf<OpenApiSpec.TypeDescriptor>()

    // Composable response-inference rules (OCP): adding a new inference is adding a rule here.
    private val responseInferenceRules: List<ResponseInferenceRule> = listOf(CallRespondInference())

    override fun visitElement(expression: FirElement, parent: KtorElement?): List<KtorElement> {
        return parent.wrapAsList()
    }

    // Evaluation Order 1
    override fun visitNamedFunction(namedFunction: FirNamedFunction, parent: KtorElement?): List<KtorElement> {
        val extractedTags = namedFunction.findTags(session)
        val isDeprecated = namedFunction.findDeprecated()
        val declaredHeaders = namedFunction.findDeclaredHeaders(session)
        val descriptor = parent ?: RouteDescriptor(
            "/",
            tags = extractedTags,
            isDeprecated = isDeprecated,
            headers = declaredHeaders
        )
        namedFunction.acceptChildren(this, descriptor)
        return descriptor.wrapAsList()
    }

    // Evaluation Order 2
    override fun visitDeclaration(declaration: FirDeclaration, parent: KtorElement?): List<KtorElement> =
        if (declaration is FirNamedFunction) {
            declaration.body?.accept(this, parent) ?: emptyList()
        } else {
            emptyList()
        }

    // Evaluation Order 3
    override fun visitBlock(block: FirBlock, parent: KtorElement?): List<KtorElement> {
        if (parent is EndpointDescriptor) {
            extractParameters(block.statements, parent)
            extractRespondsDsl(block.statements, parent)
            extractRequestBody(block.statements, parent)
        }
        return block.statements.flatMap { it.accept(this, parent) }
    }

    private fun extractParameters(statements: List<FirStatement>, endpoint: EndpointDescriptor) {
        val queryVisitor = ParametersVisitor(
            session,
            listOf(
                ClassIds.KTOR_QUERY_PARAM,
                ClassIds.KTOR_RAW_QUERY_PARAM,
                ClassIds.KTOR_3_QUERY_PARAM,
                ClassIds.KTOR_3_RAW_QUERY_PARAM
            ),
            config
        )
        val headerVisitor = ParametersVisitor(
            session,
            listOf(
                ClassIds.KTOR_HEADER_PARAM,
                ClassIds.KTOR_3_HEADER_PARAM,
                ClassIds.KTOR_HEADER_ACCESSOR,
                ClassIds.KTOR_3_HEADER_ACCESSOR
            ),
            config,
            implicitHeaderAccessors = ClassIds.KTOR_IMPLICIT_HEADER_ACCESSORS
        )

        val queryParams = mutableListOf<QueryParamSpec>()
        val headerParams = mutableListOf<HeaderParamSpec>()

        statements.forEach { statement ->
            // KDoc on the local `val` a parameter value is assigned to documents the parameters
            // extracted from that statement; it wins over KDoc on a referenced name constant.
            val statementDoc = (statement as? FirProperty)
                ?.getKDocComments(config)
                ?.ifBlank { null }

            val calls = (listOf(statement) + statement.allChildren).filterIsInstance<FirFunctionCall>()

            val query = mutableListOf<ParamMeta>()
            val headers = mutableListOf<ParamMeta>()
            calls.forEach {
                it.accept(queryVisitor, query)
                it.accept(headerVisitor, headers)
            }
            // A key built only from expressions that can't be statically resolved joins to an
            // empty name — drop it rather than emitting a nameless parameter.
            queryParams += query.filter { it.name.isNotBlank() }
                .map { QueryParamSpec(it.name, description = statementDoc ?: it.description) }
            headerParams += headers.filter { it.name.isNotBlank() }
                .map { HeaderParamSpec(it.name, description = statementDoc ?: it.description) }
        }

        if (queryParams.isNotEmpty()) endpoint.parameters = endpoint.parameters merge queryParams.toSet()
        if (headerParams.isNotEmpty()) endpoint.parameters = endpoint.parameters merge headerParams.toSet()
    }

    private fun extractRespondsDsl(statements: List<FirStatement>, endpoint: EndpointDescriptor) {
        val respondsCalls = statements.filterCallsWith(ClassIds.KTOR_RESPONDS_NO_OP) +
                statements.filterCallsWith(ClassIds.KTOR_RESPONDS_NOTHING_NO_OP)

        if (respondsCalls.isEmpty()) return

        val responses = respondsCalls.map { it.toResponseBag() }.resolveToOpenSpecFormat()
        endpoint.responses = endpoint.responses?.plus(responses) ?: responses
    }

    /**
     * Infers responses for an endpoint from the `call.respond*(...)` calls reachable on its handler's
     * execution path, and merges them into [endpoint] filling only the status codes not already declared
     * by `@KtorResponds` / `responds<T>()` (explicit declarations always win). Gated by
     * [PluginConfiguration.inferResponseSchemas].
     *
     * The walk descends through control-flow, nested lambdas (scoping functions such as `withContext`
     * and custom DSLs that wrap the response), and **follows extracted handler functions** (those passed
     * a Ktor type, e.g. `get { handle(call) }`) with a cycle guard. A `respond` inside a detached builder
     * (`launch`/`async`) is therefore also attributed — see the documented limitation.
     *
     * If analysis of one endpoint throws it is logged and skipped rather than failing the build.
     */
    private fun FirFunctionCall.inferResponsesInto(endpoint: EndpointDescriptor) {
        if (!config.inferResponseSchemas) return
        val handlerBody = findLambda()?.anonymousFunction?.body ?: return
        try {
            val respondCalls = mutableListOf<FirFunctionCall>()
            handlerBody.acceptChildren(RespondCallCollector(mutableSetOf(), respondCalls))

            val inferred = respondCalls
                .flatMap { call -> responseInferenceRules.flatMap { rule -> rule.infer(call, session) } }
                .resolveToOpenSpecFormat()

            if (inferred.isEmpty()) return
            endpoint.responses = mergeFillingGaps(endpoint.responses, inferred)
        } catch (ex: Throwable) {
            log?.report(
                CompilerMessageSeverity.WARNING,
                "Failed to infer responses for endpoint: ${ex.message}",
                null
            )
        }
    }

    /**
     * Collects candidate `respond*` calls anywhere in the handler — including inside nested lambdas
     * (scoping functions / custom DSLs) — and follows Ktor-typed extracted functions (cycle-guarded).
     * Descending into all lambdas mirrors the upstream Ktor plugin and maximizes coverage of DSL-wrapped
     * responses; the trade-off is that a `respond` inside a detached `launch`/`async` is also collected.
     */
    private inner class RespondCallCollector(
        private val visited: MutableSet<FirBasedSymbol<*>>,
        private val calls: MutableList<FirFunctionCall>
    ) : FirVisitorVoid() {

        override fun visitElement(element: FirElement) {
            element.acceptChildren(this)
        }

        @OptIn(SymbolInternals::class)
        override fun visitFunctionCall(functionCall: FirFunctionCall) {
            calls.add(functionCall)
            functionCall.acceptChildren(this)

            // Interprocedural: follow a user handler-helper's body once (e.g. get { handle(call) }).
            val symbol = functionCall.calleeReference.toResolvedFunctionSymbol() ?: return
            if (symbol in visited || !functionCall.passesKtorType()) return
            visited.add(symbol)
            symbol.fir.body?.acceptChildren(this)
        }
    }

    private fun FirFunctionCall.passesKtorType(): Boolean {
        val receiverAndArgTypes = buildList {
            dispatchReceiver?.let { add(it.resolvedType) }
            extensionReceiver?.let { add(it.resolvedType) }
            resolvedArgumentMapping?.keys?.forEach { add(it.resolvedType) }
        }
        return receiverAndArgTypes.any {
            it.classId?.packageFqName?.asString()?.startsWith("io.ktor") == true
        }
    }

    /** Keeps existing (explicit) responses and adds inferred ones only for absent status codes. */
    private fun mergeFillingGaps(
        existing: Map<String, OpenApiSpec.ResponseDetails>?,
        inferred: Map<String, OpenApiSpec.ResponseDetails>
    ): Map<String, OpenApiSpec.ResponseDetails> = buildMap {
        existing?.let { putAll(it) }
        inferred.forEach { (status, details) -> putIfAbsent(status, details) }
    }

    private fun FirFunctionCall.toResponseBag(): KtorK2ResponseBag {
        val docs = source?.findCorrespondingComment()
        val resolvedCallableSymbol = toResolvedCallableSymbol()
        val isNothingResponse = resolvedCallableSymbol
            ?.callableId?.asSingleFqName() == ClassIds.KTOR_RESPONDS_NOTHING_NO_OP

        val type = if (isNothingResponse) {
            BuiltinTypes().nothingType.coneType
        } else {
            // Guard against a missing/star type argument instead of crashing the compilation;
            // treat an unresolvable responds<T>() as an empty (Nothing) response.
            (typeArguments.firstOrNull() as? FirTypeProjectionWithVariance)?.typeRef?.coneType
                ?: BuiltinTypes().nothingType.coneType
        }

        val argByName = resolvedArgumentMapping?.entries
            ?.associate { it.value.name.asString() to it.key }
            .orEmpty()

        val code = ((argByName["status"] as? FirPropertyAccessExpression)
            ?.calleeReference as? FirResolvedNamedReference)
            ?.name?.asString()

        val description = (argByName["description"] as? FirLiteralExpression)
            ?.accept(StringResolutionVisitor(session), "")
            ?.ifBlank { null }

        val contentType = (argByName["contentType"] as? FirLiteralExpression)
            ?.accept(StringResolutionVisitor(session), "")
            ?.ifBlank { null }
            ?: "application/json"

        return KtorK2ResponseBag(
            descr = description ?: docs ?: "",
            status = HttpCodeResolver.resolve(code),
            type = type,
            isCollection = false,
            contentType = contentType
        )
    }

    private fun extractRequestBody(statements: List<FirStatement>, endpoint: EndpointDescriptor) {
        if (endpoint.body != null || !config.requestBody) return
        val receiveCall = statements.findCallWith(ClassIds.KTOR_RECEIVE) ?: return
        endpoint.body = receiveCall.resolvedType.generateDescriptor()
    }

    @OptIn(SymbolInternals::class)
    override fun visitFunctionCall(functionCall: FirFunctionCall, parent: KtorElement?): List<KtorElement> {

        val tagsFromAnnotation = functionCall.findTags(session)
        val isDeprecated = functionCall.findDeprecated()
        val resultElement = functionCall.lookForKtorElements(parent, tagsFromAnnotation, isDeprecated)
        functionCall.findLambda()?.accept(this, resultElement ?: parent) ?: run {
            val declaration = functionCall.calleeReference.toResolvedFunctionSymbol()?.fir
            val tagsFromDeclaration = declaration?.findTags(session)
            val deprecated = declaration?.findDeprecated()

            if (parent is RouteDescriptor) {
                val acceptedElements = declaration?.accept(this, null)?.onEach {
                    it.tags = it.tags merge tagsFromAnnotation merge tagsFromDeclaration
                    it.isDeprecated = it.isDeprecated optionalAnd deprecated optionalAnd isDeprecated
                }
                parent.children.addAll(acceptedElements ?: emptyList())
            } else {
                declaration?.accept(this, parent)
            }
        }

        return listOfNotNull(resultElement ?: parent)
    }

    private fun FirFunctionCall.lookForKtorElements(
        parent: KtorElement?,
        tagsFromAnnotation: Set<String>?,
        isDeprecated: Boolean?
    ): KtorElement? {
        val resolvedExp = toResolvedCallableReference(session)
        val expName = resolvedExp?.name?.asString() ?: ""

        if (!isARouteDefinition() && !ExpType.METHOD.labels.contains(expName)) return null

        val pathValue = resolvePath()
        val declaredHeaders = findDeclaredHeaders(session)

        return when {
            ExpType.ROUTE.labels.contains(expName) ->
                handleRouteElement(parent, pathValue, expName, tagsFromAnnotation, isDeprecated, declaredHeaders)

            ExpType.METHOD.labels.contains(expName) ->
                handleMethodElement(parent, pathValue, tagsFromAnnotation, isDeprecated, expName, declaredHeaders)

            else -> null
        }
    }

    private fun FirFunctionCall.handleRouteElement(
        parent: KtorElement?,
        pathValue: String?,
        expName: String,
        tagsFromAnnotation: Set<String>?,
        isDeprecated: Boolean?,
        declaredHeaders: Set<HeaderParamSpec>?
    ): KtorElement? {
        return when (parent) {
            null -> {
                pathValue?.let {
                    RouteDescriptor(it, tags = tagsFromAnnotation, isDeprecated = isDeprecated, headers = declaredHeaders)
                } ?: RouteDescriptor(expName, tags = tagsFromAnnotation, isDeprecated = isDeprecated, headers = declaredHeaders)
            }

            is RouteDescriptor -> {
                val newElement = RouteDescriptor(
                    pathValue.toString(),
                    tags = parent.tags merge tagsFromAnnotation,
                    isDeprecated = parent.isDeprecated optionalAnd isDeprecated,
                    headers = declaredHeaders
                )
                parent.children.add(newElement)
                newElement
            }

            is EndpointDescriptor -> {
                log?.report(
                    CompilerMessageSeverity.WARNING,
                    "Route definition found under the endpoint",
                    getLocation()
                )
                null
            }

            else -> null
        }
    }

    @OptIn(SymbolInternals::class)
    @Suppress("LongParameterList")
    private fun FirFunctionCall.handleMethodElement(
        parent: KtorElement?,
        pathValue: String?,
        tagsFromAnnotation: Set<String>?,
        isDeprecated: Boolean?,
        expName: String,
        declaredHeaders: Set<HeaderParamSpec>?
    ): KtorElement? {
        val descr = findDocsDescription(session)
        val responses = findRespondsAnnotation(session)?.resolveToOpenSpecFormat()

        val endpoint = EndpointDescriptor(
            path = null,
            method = expName,
            description = descr.description,
            summary = descr.summary,
            operationId = descr.operationId,
            tags = descr.tags merge tagsFromAnnotation,
            responses = responses,
            parameters = declaredHeaders
        )

        // Fill response gaps from inferred call.respond* schemas (explicit DSL/annotation still wins).
        inferResponsesInto(endpoint)

        val resource = findResource(endpoint)
        val type = typeArguments.firstOrNull()?.toConeTypeProjection()?.type
        val newElement = resource ?: endpoint.copy(path = pathValue, body = type?.toEndpointBody())

        return when (parent) {
            null -> {
                if (resource != null && newElement is RouteDescriptor) {
                    newElement
                } else {
                    RouteDescriptor("/", children = mutableListOf(newElement), isDeprecated = isDeprecated)
                }
            }

            is RouteDescriptor -> {
                parent.children.add(newElement)
                if (resource != null && newElement is RouteDescriptor) {
                    newElement.findFirstEndpoint()
                } else {
                    newElement
                }
            }

            else -> {
                log?.report(
                    CompilerMessageSeverity.WARNING,
                    "Endpoints can't have Endpoint as routes",
                    getLocation()
                )
                null
            }
        }
    }

    private fun KtorElement.findFirstEndpoint(): EndpointDescriptor? {
        if (this is EndpointDescriptor) return this
        if (this is RouteDescriptor) {
            for (child in this.children) {
                child.findFirstEndpoint()?.let { return it }
            }
        }
        return null
    }

    override fun visitReturnExpression(returnExpression: FirReturnExpression, data: KtorElement?): List<KtorElement> {
        val funCall = returnExpression.result as? FirFunctionCall
        funCall?.accept(this, data)
        return super.visitReturnExpression(returnExpression, data)
    }

    override fun visitAnonymousFunctionExpression(
        anonymousFunctionExpression: FirAnonymousFunctionExpression,
        data: KtorElement?
    ): List<KtorElement> = anonymousFunctionExpression.anonymousFunction.body?.accept(this, data) ?: data.wrapAsList()

    override fun visitAnonymousFunction(
        anonymousFunction: FirAnonymousFunction,
        data: KtorElement?
    ): List<KtorElement> = anonymousFunction.body?.accept(this, data) ?: data.wrapAsList()

    @OptIn(SymbolInternals::class)
    private fun ConeKotlinType.generateDescriptor(): OpenApiSpec.TypeDescriptor? {
        val annotatedDescription = findDocsDescriptionOnType(session)
        val classDescriptorVisitor = ClassDescriptorVisitorK2(config, session, context)
        val visited = classDescriptorVisitor.collectDataTypes(annotatedDescription?.serializedAs ?: this)
        classNames.addAll(classDescriptorVisitor.classNames)
        return visited
    }

    private fun ConeKotlinType.toEndpointBody(): OpenApiSpec.TypeDescriptor? {
        return if (isStringOrPrimitive()) {
            OpenApiSpec.TypeDescriptor(type = toString().toSwaggerType())
        } else {
            if (config.requestBody) {
                generateDescriptor()
            } else {
                null
            }
        }
    }

    @OptIn(SymbolInternals::class)
    private fun FirFunctionCall.resolvePath(): String? {
        val expression = this
        val pathFunction = expression.calleeReference.resolved?.resolvedSymbol?.fir as? FirFunction
        val pathExpressionIndex = pathFunction
            ?.valueParameters?.indexOfFirst { it.name.asString() == "path" } ?: -1

        val pathExpression = expression.arguments.getOrElse(pathExpressionIndex) {
            expression.arguments.find { it is FirLiteralExpression }
        }
        return pathExpression?.accept(StringResolutionVisitor(session), "")
    }

    private fun FirFunctionCall.findLambda(): FirAnonymousFunctionExpression? {
        return arguments
            .filterIsInstance<FirAnonymousFunctionExpression>()
            .lastOrNull()
    }

    private fun FirFunctionCall.getLocation(): CompilerMessageLocation? {
        val psi = source?.psi
        val filePath = psi?.containingFile?.virtualFile?.path
        val textRange = psi?.textRange
        val document = psi?.containingFile?.viewProvider?.document

        return if (filePath != null && textRange != null && document != null) {
            val startOffset = textRange.startOffset
            val lineNumber = document.getLineNumber(startOffset) + 1
            val columnNumber = startOffset - document.getLineStartOffset(lineNumber - 1) + 1
            CompilerMessageLocation.create(
                path = filePath,
                line = lineNumber,
                column = columnNumber,
                lineContent = null
            )
        } else {
            null
        }
    }

    private fun List<KtorK2ResponseBag>.resolveToOpenSpecFormat() =
        associate { response ->
            val kotlinType = response.type
            when {
                // Content type known, schema intentionally omitted (streaming/file/erased body).
                response.noSchema -> response.status to OpenApiSpec.ResponseDetails(
                    response.descr,
                    mapOf(response.contentType to emptyMap<String, OpenApiSpec.TypeDescriptor>())
                )

                kotlinType?.isNothing == true ->
                    response.status to OpenApiSpec.ResponseDetails(response.descr, null)

                else -> response.status to OpenApiSpec.ResponseDetails(
                    response.descr,
                    mapOf(
                        response.contentType to mapOf("schema" to response.toResponseSchema())
                    )
                )
            }
        }

    private fun KtorK2ResponseBag.toResponseSchema(): OpenApiSpec.TypeDescriptor {
        val typeName = type?.classId?.shortClassName?.asString()
        val isJsonLike = contentType == "application/json" || contentType.endsWith("+json")
        if (!isJsonLike && typeName == "ByteArray") {
            return OpenApiSpec.TypeDescriptor(type = "string", format = "binary")
        }
        val schema = type?.generateDescriptor()
        return if (isCollection) {
            OpenApiSpec.TypeDescriptor(
                type = "array",
                items = OpenApiSpec.TypeDescriptor(type = null, ref = schema?.ref)
            )
        } else {
            schema ?: OpenApiSpec.TypeDescriptor("object")
        }
    }

    @OptIn(SymbolInternals::class)
    private fun FirFunctionCall.findResource(
        endpoint: EndpointDescriptor
    ): KtorElement? {
        val params = typeArguments
        if (!isInPackage(ClassIds.KTOR_RESOURCES)) return null

        return when (params.size) {
            1 -> {
                val type = params.firstOrNull()?.toConeTypeProjection()?.type
                if (type.isKtorResourceAnnotated()) {
                    type?.toRegularClassSymbol(session)
                        ?.fir
                        ?.accept(ResourceClassVisitor(session, config, endpoint), null)
                } else {
                    null
                }
            }

            2 -> {
                val firstType = params.firstOrNull()?.toConeTypeProjection()?.type
                val secondType = params.lastOrNull()?.toConeTypeProjection()?.type
                firstType?.toRegularClassSymbol(session)
                    ?.fir
                    ?.accept(
                        ResourceClassVisitor(
                            session,
                            config,
                            endpoint.copy(body = secondType?.toEndpointBody())
                        ), null
                    )
            }

            else -> {
                log?.report(
                    CompilerMessageSeverity.WARNING,
                    "Unknown Ktor function ${toResolvedCallableReference(session)?.name}",
                    getLocation()
                )
                null
            }
        }
    }

    private fun List<FirStatement>.findCallWith(callable: FqName): FirFunctionCall? {
        return filterIsInstance<FirFunctionCall>()
            .find { it.toResolvedCallableSymbol()?.callableId?.asSingleFqName() == callable }
            ?: flatMap { it.allChildren }.filterIsInstance<FirFunctionCall>()
                .find { it.toResolvedCallableSymbol()?.callableId?.asSingleFqName() == callable }
    }

    private fun List<FirStatement>.filterCallsWith(callable: FqName): List<FirFunctionCall> {
        val directMatches = filterIsInstance<FirFunctionCall>()
            .filter { it.toResolvedCallableSymbol()?.callableId?.asSingleFqName() == callable }

        return directMatches.ifEmpty {
            flatMap { it.allChildren }.filterIsInstance<FirFunctionCall>()
                .filter { it.toResolvedCallableSymbol()?.callableId?.asSingleFqName() == callable }
        }
    }

    private fun FirQualifiedAccessExpression?.isARouteDefinition(): Boolean {
        return this?.resolvedType?.classId == ClassIds.KTOR_ROUTE
    }

    private fun ConeKotlinType?.isKtorResourceAnnotated(): Boolean =
        this?.toRegularClassSymbol(session)?.hasAnnotation(ClassIds.KTOR_RESOURCE_ANNOTATION, session) == true

    private fun FirFunctionCall.isInPackage(fqName: FqName): Boolean =
        toResolvedCallableSymbol()?.callableId?.packageName == fqName

    @OptIn(PrivateForInline::class)
    private fun FirStatement.findTags(session: FirSession): Set<String>? {
        val annotation = findAnnotation(ClassIds.KTOR_TAGS_ANNOTATION, session) ?: return null
        val resolved = FirExpressionEvaluator.evaluateAnnotationArguments(annotation, session)
        return resolved.entries.find { it.key.asString() == "tags" }?.value?.result?.accept(
            StringArrayLiteralVisitor(),
            emptyList()
        )?.toSet()
    }

    private fun FirStatement.findDeprecated(): Boolean? = if (findAnnotationNamed(ClassIds.DEPRECATED) != null) {
        true
    } else {
        null
    }

    private fun FirFunctionCall.findDocsDescription(session: FirSession): KtorDescriptionBag {
        val docsAnnotation = findAnnotationNamed(ClassIds.KTOR_DESCRIPTION)
            ?: return KtorDescriptionBag()

        return docsAnnotation.extractDescription(session)
    }

    @OptIn(PrivateForInline::class)
    private fun FirFunctionCall.findRespondsAnnotation(session: FirSession): List<KtorK2ResponseBag>? {
        val annotation = findAnnotationNamed(ClassIds.KTOR_RESPONDS)
        return annotation?.let {
            val resolved = FirExpressionEvaluator.evaluateAnnotationArguments(annotation, session)
            val mapping = resolved.entries.find { it.key.asString() == "mapping" }?.value?.result
            mapping?.accept(RespondsAnnotationVisitor(session), null)
        }
    }

    /**
     * Reads a `@KtorHeaders(headers = [HeaderParam(...)])` annotation from an endpoint or
     * `route(...)` expression, or from an annotated route module function.
     */
    @OptIn(PrivateForInline::class)
    private fun FirStatement.findDeclaredHeaders(session: FirSession): Set<HeaderParamSpec>? {
        val annotation = findAnnotationNamed(ClassIds.KTOR_HEADERS) ?: return null
        val resolved = FirExpressionEvaluator.evaluateAnnotationArguments(annotation, session)
        val headers = resolved.entries.find { it.key.asString() == "headers" }?.value?.result
        return headers?.accept(HeadersAnnotationVisitor(session), null)?.toSet()?.ifEmpty { null }
    }
}
