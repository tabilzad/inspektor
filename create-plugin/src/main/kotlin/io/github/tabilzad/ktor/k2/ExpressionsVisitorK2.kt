package io.github.tabilzad.ktor.k2

import io.github.tabilzad.ktor.*
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
import org.jetbrains.kotlin.fir.resolve.fqName
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.fir.visitors.FirDefaultVisitor
import org.jetbrains.kotlin.name.ClassId
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

    override fun visitElement(expression: FirElement, parent: KtorElement?): List<KtorElement> {
        return parent.wrapAsList()
    }

    // Evaluation Order 1
    override fun visitSimpleFunction(simpleFunction: FirSimpleFunction, parent: KtorElement?): List<KtorElement> {
        val extractedTags = simpleFunction.findTags(session)
        val isDeprecated = simpleFunction.findDeprecated()
        val descriptor = parent ?: RouteDescriptor("/", tags = extractedTags, isDeprecated = isDeprecated)
        simpleFunction.acceptChildren(this, descriptor)
        return descriptor.wrapAsList()
    }

    // Evaluation Order 2
    override fun visitDeclaration(declaration: FirDeclaration, parent: KtorElement?): List<KtorElement> =
        if (declaration is FirSimpleFunction) {
            declaration.body?.accept(this, parent) ?: emptyList()
        } else {
            emptyList()
        }

    // Evaluation Order 3
    @Suppress("NestedBlockDepth")
    override fun visitBlock(block: FirBlock, parent: KtorElement?): List<KtorElement> {

        if (parent is EndpointDescriptor && parent.body == null) {

            val receiveCall = block.statements.findCallExpressionWith(ClassIds.KTOR_RECEIVE)
            val respondsDsl = block.statements.filterCallExpressionWith(ClassIds.KTOR_RESPONDS_NO_OP) +
                    block.statements.filterCallExpressionWith(ClassIds.KTOR_RESPONDS_NOTHING_NO_OP)

            val queryParam = block.statements.findQueryParameterExpression()
            if (queryParam.isNotEmpty()) parent.parameters = parent.parameters merge queryParam.toSet()

            val headerParam = block.statements.findHeaderParameterExpression()
            if (headerParam.isNotEmpty()) parent.parameters = parent.parameters merge headerParam.toSet()

            if (respondsDsl.isNotEmpty()) {

                val responses = respondsDsl.map { respondsCallable ->
                    val docs = respondsCallable.source?.findCorrespondingComment()
                    val resolvedCallableSymbol = respondsCallable.toResolvedCallableSymbol()
                    val type = if (resolvedCallableSymbol?.callableId?.asSingleFqName() == ClassIds.KTOR_RESPONDS_NOTHING_NO_OP) {
                            BuiltinTypes().nothingType.coneType
                        } else {
                            (respondsCallable.typeArguments.first() as FirTypeProjectionWithVariance).typeRef.coneType
                        }
                    val code = ((respondsCallable.arguments.first() as? FirPropertyAccessExpression)
                        ?.calleeReference as? FirResolvedNamedReference)
                        ?.name?.asString()

                    val descriptionExpression = respondsCallable.arguments.lastOrNull()
                    val description = descriptionExpression?.accept(StringResolutionVisitor(session), "")?.ifBlank { null }

                    val status = HttpCodeResolver.resolve(code)
                    KtorK2ResponseBag(
                        descr = description ?: docs ?: "",
                        status = status,
                        type = type,
                        isCollection = false
                    )
                }.resolveToOpenSpecFormat()

                parent.responses = parent.responses?.plus(responses) ?: responses
            }

            if (receiveCall != null && config.requestBody) {
                parent.body = receiveCall.resolvedType.generateDescriptor()
            }
        }

        return block.statements.flatMap { it.accept(this, parent) }
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

    @Suppress("LongMethod", "NestedBlockDepth", "CyclomaticComplexMethod")
    private fun FirFunctionCall.lookForKtorElements(
        parent: KtorElement?,
        tagsFromAnnotation: Set<String>?,
        isDeprecated: Boolean?
    ): KtorElement? {
        val resolvedExp = toResolvedCallableReference(session)
        val expName = resolvedExp?.name?.asString() ?: ""
        if (isARouteDefinition() || ExpType.METHOD.labels.contains(expName)) {
            val pathValue = resolvePath()

            when {
                ExpType.ROUTE.labels.contains(expName) -> {
                    when (parent) {
                        null -> {
                            return pathValue?.let {
                                RouteDescriptor(it, tags = tagsFromAnnotation, isDeprecated = isDeprecated)
                            } ?: RouteDescriptor(expName, tags = tagsFromAnnotation, isDeprecated = isDeprecated)
                        }

                        is RouteDescriptor -> {
                            val newElement = RouteDescriptor(
                                pathValue.toString(),
                                tags = parent.tags merge tagsFromAnnotation,
                                isDeprecated = parent.isDeprecated optionalAnd isDeprecated
                            )
                            parent.children.add(newElement)
                            return newElement
                        }

                        is EndpointDescriptor -> log?.report(
                            CompilerMessageSeverity.WARNING,
                            "Route definition found under the endpoint",
                            getLocation()
                        )
                    }
                }

                ExpType.METHOD.labels.contains(expName) -> {
                    val descr = findDocsDescription(session)
                    val responses = findRespondsAnnotation(session)?.resolveToOpenSpecFormat()
                    val params = typeArguments

                    val endpoint = EndpointDescriptor(
                        path = null,
                        method = expName,
                        description = descr.description,
                        summary = descr.summary,
                        operationId = descr.operationId,
                        tags = descr.tags merge tagsFromAnnotation,
                        responses = responses
                    )

                    val resource = findResource(endpoint)
                    val type = params.firstOrNull()?.toConeTypeProjection()?.type
                    val newElement = resource ?: endpoint.copy(path = pathValue, body = type?.toEndpointBody())

                    when (parent) {
                        null -> {
                            return if (resource != null && newElement is RouteDescriptor) {
                                newElement
                            } else {
                                RouteDescriptor("/", children = mutableListOf(newElement), isDeprecated = isDeprecated)
                            }
                        }

                        is RouteDescriptor -> {
                            parent.children.add(newElement)
                            return if (resource != null && newElement is RouteDescriptor) {
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
                        }
                    }
                }
            }
        }

        return null
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
        val pathExpressionIndex = (expression.calleeReference.resolved?.resolvedSymbol?.fir as FirFunction)
            .valueParameters.indexOfFirst { it.name.asString() == "path" }

        val pathExpression = expression.arguments.getOrElse(pathExpressionIndex) {
            expression.arguments.find { it is FirLiteralExpression }
        }
        val resolvedPathValue = pathExpression?.accept(StringResolutionVisitor(session), "")
        return resolvedPathValue
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
            if (kotlinType?.isNothing == true) {
                response.status to OpenApiSpec.ResponseDetails(response.descr, null)
            } else {
                val schema = response.type?.generateDescriptor()
                response.status to OpenApiSpec.ResponseDetails(
                    response.descr,
                    mapOf(
                        ContentType.APPLICATION_JSON to mapOf(
                            "schema" to if (response.isCollection) {
                                OpenApiSpec.TypeDescriptor(
                                    type = "array",
                                    items = OpenApiSpec.TypeDescriptor(type = null, ref = schema?.ref)
                                )
                            } else {
                                schema ?: OpenApiSpec.TypeDescriptor("object")
                            }
                        )
                    )
                )
            }
        }

    @OptIn(SymbolInternals::class)
    private fun FirFunctionCall.findResource(
        endpoint: EndpointDescriptor
    ): KtorElement? {
        val params = typeArguments
        return if (isInPackage(ClassIds.KTOR_RESOURCES)) {
            when (params.size) {
                1 -> {
                    val type = params.firstOrNull()?.toConeTypeProjection()?.type
                    when {
                        type.isKtorResourceAnnotated() -> {
                            type?.toRegularClassSymbol(session)
                                ?.fir
                                ?.accept(ResourceClassVisitor(session, config, endpoint), null)
                        }

                        else -> null
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
        } else {
            null
        }
    }

    private fun List<FirStatement>.findQueryParameterExpression(): List<ParamSpec> {
        val queryParams = mutableListOf<String>()
        (this + flatMap { it.allChildren }).filterIsInstance<FirFunctionCall>()
            .forEach {
                it.accept(
                    ParametersVisitor(
                        session,
                        listOf(
                            ClassIds.KTOR_QUERY_PARAM,
                            ClassIds.KTOR_RAW_QUERY_PARAM,
                            ClassIds.KTOR_3_QUERY_PARAM,
                            ClassIds.KTOR_3_RAW_QUERY_PARAM,
                        )
                    ),
                    queryParams
                )
            }
        return queryParams.map { QueryParamSpec(it) }
    }

    private fun List<FirStatement>.findHeaderParameterExpression(): List<ParamSpec> {
        val headerParams = mutableListOf<String>()
        flatMap { it.allChildren }.filterIsInstance<FirFunctionCall>()
            .forEach {
                it.accept(
                    ParametersVisitor(
                        session,
                        listOf(
                            ClassIds.KTOR_HEADER_PARAM,
                            ClassIds.KTOR_3_HEADER_PARAM,
                            ClassIds.KTOR_HEADER_ACCESSOR,
                            ClassIds.KTOR_3_HEADER_ACCESSOR,
                        )
                    ),
                    headerParams
                )
            }

        filterIsInstance<FirFunctionCall>()
            .forEach {
                it.accept(
                    ParametersVisitor(session, listOf(ClassIds.KTOR_HEADER_ACCESSOR)),
                    headerParams
                )
            }

        return headerParams.map { HeaderParamSpec(it) }
    }

    private fun List<FirStatement>.findCallExpressionWith(callable: FqName): FirFunctionCall? {

        val receiveFunctionCall = filterIsInstance<FirFunctionCall>()
            .find { it.toResolvedCallableSymbol()?.callableId?.asSingleFqName() == callable }

        if (receiveFunctionCall == null) {
            return flatMap { it.allChildren }.filterIsInstance<FirFunctionCall>()
                .find { it.toResolvedCallableSymbol()?.callableId?.asSingleFqName() == callable }
        }
        return receiveFunctionCall
    }

    private fun List<FirStatement>.filterCallExpressionWith(callable: FqName): List<FirFunctionCall> {

        val callables = filterIsInstance<FirFunctionCall>()
            .filter { it.toResolvedCallableSymbol()?.callableId?.asSingleFqName() == callable }

        if (callables.isEmpty()) {
            return flatMap { it.allChildren }.filterIsInstance<FirFunctionCall>()
                .filter { it.toResolvedCallableSymbol()?.callableId?.asSingleFqName() == callable }
        }
        return callables
    }

    private fun FirQualifiedAccessExpression?.isARouteDefinition(): Boolean {
        return this?.resolvedType?.classId == ClassIds.KTOR_ROUTE
    }

    private fun ConeKotlinType?.isKtorResourceAnnotated(): Boolean =
        this?.toRegularClassSymbol(session)?.hasAnnotation(ClassIds.KTOR_RESOURCE_ANNOTATION, session) == true

    @OptIn(SymbolInternals::class)
    private fun FirRegularClassSymbol.findAnnotation(classId: ClassId): FirAnnotation? {
        return annotations.find { it.fqName(session) == classId.asSingleFqName() }
    }

    private fun FirFunctionCall.findAnnotation(classId: ClassId): FirAnnotation? {
        return annotations.find { it.fqName(session) == classId.asSingleFqName() }
    }

    private fun FirFunctionCall.isInPackage(fqName: FqName): Boolean =
        toResolvedCallableSymbol()?.callableId?.packageName == fqName

    @OptIn(PrivateForInline::class)
    private fun FirStatement.findTags(session: FirSession): Set<String>? {
        val annotation = findAnnotation(ClassIds.KTOR_TAGS_ANNOTATION, session) ?: return null
        val resolved = FirExpressionEvaluator.evaluateAnnotationArguments(annotation, session)
        return resolved?.entries?.find { it.key.asString() == "tags" }?.value?.result?.accept(
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
            val mapping = resolved?.entries?.find { it.key.asString() == "mapping" }?.value?.result
            mapping?.accept(RespondsAnnotationVisitor(session), null)
        }
    }
}
