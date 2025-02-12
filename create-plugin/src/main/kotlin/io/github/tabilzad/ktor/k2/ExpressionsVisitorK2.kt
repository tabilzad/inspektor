package io.github.tabilzad.ktor.k2

import io.github.tabilzad.ktor.*
import io.github.tabilzad.ktor.annotations.KtorDescription
import io.github.tabilzad.ktor.annotations.KtorResponds
import io.github.tabilzad.ktor.k1.visitors.KtorDescriptionBag
import io.github.tabilzad.ktor.k1.visitors.toSwaggerType
import io.github.tabilzad.ktor.k2.visitors.*
import io.github.tabilzad.ktor.output.OpenApiSpec
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.isValueClass
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.expressions.*
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

    init {
        println("BeginK2 Visitor")
    }

    val classNames = mutableListOf<OpenApiSpec.ObjectType>()

    override fun visitElement(expression: FirElement, parent: KtorElement?): List<KtorElement> {
        return parent.wrapAsList()
    }

    // Evaluation Order 1
    override fun visitSimpleFunction(simpleFunction: FirSimpleFunction, parent: KtorElement?): List<KtorElement> {

        val extractedTags = simpleFunction.findTags(session)
        val c = parent ?: DocRoute("/", tags = extractedTags)

        simpleFunction.acceptChildren(this, c)
        return c.wrapAsList()
    }

    // Evaluation Order 2
    override fun visitDeclaration(declaration: FirDeclaration, parent: KtorElement?): List<KtorElement> {
        return if (declaration is FirSimpleFunction) {
            declaration.body?.accept(this, parent) ?: emptyList()
        } else {
            emptyList()
        }
    }

    // Evaluation Order 3
    @Suppress("NestedBlockDepth")
    override fun visitBlock(block: FirBlock, parent: KtorElement?): List<KtorElement> {

        if (parent is EndPoint && parent.body == null) {

            val receiveCall = block.statements.findReceiveCallExpression()

            val queryParam = block.statements.findQueryParameterExpression()
            if (queryParam.isNotEmpty()) parent.parameters = parent.parameters merge queryParam.toSet()

            val headerParam = block.statements.findHeaderParameterExpression()
            if (headerParam.isNotEmpty()) parent.parameters = parent.parameters merge headerParam.toSet()

            if (receiveCall != null) {
                val kotlinType = receiveCall.resolvedType
                if (kotlinType.isStringOrPrimitive()) {
                    parent.body = OpenApiSpec.ObjectType(type = kotlinType.toString().toSwaggerType())
                } else {
                    if (config.requestBody) {
                        parent.body = kotlinType.generateTypeAndVisitMemberDescriptors()
                    }
                }
            }
        }

        return block.statements.flatMap { it.accept(this, parent) }
    }

    @OptIn(SymbolInternals::class)
    private fun ConeKotlinType.generateTypeAndVisitMemberDescriptors(): OpenApiSpec.ObjectType {

        val jetTypeFqName = fqNameStr()

        val kdocs = toRegularClassSymbol(session)
            ?.fir
            ?.getKDocComments(config)

        val annotatedDescription = findDocsDescription(session)

        val objectType = OpenApiSpec.ObjectType(
            type = "object",
            properties = mutableMapOf(),
            fqName = jetTypeFqName,
            description = kdocs ?: annotatedDescription?.description ?: annotatedDescription?.summary,
            contentBodyRef = "#/components/schemas/$jetTypeFqName",
        )

        if (isValueClass(session)) {
            return objectType.copy(
                type = properties(session)?.firstOrNull()
                    ?.resolvedReturnType
                    ?.className()
                    ?.toSwaggerType(),
                properties = null
            ).also {
                if (!classNames.names.contains(jetTypeFqName)) {
                    classNames.add(it)
                }
            }
        } else {

            if (!classNames.names.contains(jetTypeFqName)) {

                classNames.add(objectType)

                getMembers(session, config).forEach { d ->

                    val classDescriptorVisitor = ClassDescriptorVisitorK2(config, session, context)
                    d.accept(classDescriptorVisitor, objectType)
                    classNames.addAll(classDescriptorVisitor.classNames)
                }
            }
            return objectType
        }
    }

    private fun List<FirStatement>.findQueryParameterExpression(): List<ParamSpec> {
        val queryParams = mutableListOf<String>()
        flatMap { it.allChildren }.filterIsInstance<FirFunctionCall>()
            .forEach {
                it.accept(
                    ParametersVisitor(
                        session,
                        listOf(ClassIds.KTOR_QUERY_PARAM, ClassIds.KTOR_RAW_QUERY_PARAM)
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
                        listOf(ClassIds.KTOR_HEADER_PARAM, ClassIds.KTOR_HEADER_ACCESSOR)
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

    private fun List<FirStatement>.findReceiveCallExpression(): FirFunctionCall? {

        val receiveFunctionCall = filterIsInstance<FirFunctionCall>()
            .find { it.toResolvedCallableSymbol()?.callableId?.asSingleFqName() == ClassIds.KTOR_RECEIVE }

        if (receiveFunctionCall == null) {
            return flatMap { it.allChildren }.filterIsInstance<FirFunctionCall>()
                .find { it.toResolvedCallableSymbol()?.callableId?.asSingleFqName() == ClassIds.KTOR_RECEIVE }
        }
        return receiveFunctionCall
    }

    private fun FirQualifiedAccessExpression?.isARouteDefinition(): Boolean {
        return this?.resolvedType?.classId == ClassIds.KTOR_ROUTE
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

    @OptIn(SymbolInternals::class)
    @Suppress("LongMethod", "NestedBlockDepth", "CyclomaticComplexMethod")
    override fun visitFunctionCall(functionCall: FirFunctionCall, parent: KtorElement?): List<KtorElement> {
        val resolvedExp = functionCall.toResolvedCallableReference(session)
        val expName = resolvedExp?.name?.asString() ?: ""
        val tagsFromAnnotation = functionCall.findTags(session)

        val resultElement = if (functionCall.isARouteDefinition() || ExpType.METHOD.labels.contains(expName)) {
            val pathValue = functionCall.resolvePath()

            when {
                ExpType.ROUTE.labels.contains(expName) -> {
                    when (parent) {
                        null -> {
                            pathValue?.let {
                                DocRoute(it, tags = tagsFromAnnotation)
                            } ?: DocRoute(expName, tags = tagsFromAnnotation)
                        }

                        is DocRoute -> {
                            val newElement = DocRoute(
                                pathValue.toString(),
                                tags = parent.tags merge tagsFromAnnotation
                            )
                            parent.children.add(newElement)
                            newElement
                        }

                        else -> null
                    }
                }

                ExpType.METHOD.labels.contains(expName) -> {
                    val descr = functionCall.findDocsDescription(session)
                    val responses = functionCall.findRespondsAnnotation(session)?.resolveToOpenSpecFormat()
                    val params = functionCall.typeArguments

                    val endpoint = EndPoint(
                        path = null,
                        method = expName,
                        description = descr.description,
                        summary = descr.summary,
                        operationId = descr.operationId,
                        tags = descr.tags merge tagsFromAnnotation,
                        responses = responses
                    )

                    val resource = functionCall.findResource(endpoint)
                    val type = params.firstOrNull()?.toConeTypeProjection()?.type
                    val newElement = resource ?: endpoint.copy(path = pathValue, body = type?.toEndpointBody())
                    when (parent) {
                        null -> newElement
                        is DocRoute -> {
                            parent.children.add(newElement)
                            newElement
                        }

                        else -> {
                            log?.report(
                                CompilerMessageSeverity.WARNING,
                                "Endpoints can't have Endpoint as routes",
                                functionCall.getLocation()
                            )
                            null
                        }
                    }
                }

                else -> null
            }
        } else {
            null
        }

        functionCall.findLambda()?.accept(this, resultElement ?: parent) ?: run {
            val declaration = functionCall.calleeReference.toResolvedFunctionSymbol()?.fir
            val tagsFromDeclaration = declaration?.findTags(session)

            if (parent is DocRoute) {
                val acceptedElements = declaration?.accept(this, null)?.onEach {
                    it.tags = it.tags merge tagsFromAnnotation merge tagsFromDeclaration
                }
                parent.children.addAll(acceptedElements ?: emptyList())
            } else {
                declaration?.accept(this, parent)
            }
        }

        return listOfNotNull(resultElement ?: parent)
    }

    @OptIn(SymbolInternals::class)
    private fun FirFunctionCall.findResource(
        endpoint: EndPoint
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
            val schema = if (kotlinType?.isStringOrPrimitive() == true) {
                OpenApiSpec.SchemaType(
                    type = kotlinType.toString().toSwaggerType()
                )
            } else if (kotlinType?.lookupTagIfAny?.name?.asString() == "Instant" ||
                kotlinType?.lookupTagIfAny?.name?.asString() == "LocalDateTime") {
                OpenApiSpec.SchemaType(
                    type = "string",
                    format = "date-time"
                )
            } else {
                val typeRef = response.type?.generateTypeAndVisitMemberDescriptors()
                OpenApiSpec.SchemaType(
                    `$ref` = "${typeRef?.contentBodyRef}"
                )
            }

            if (kotlinType?.isNothing == true) {
                response.status to OpenApiSpec.ResponseDetails(response.descr, null)
            } else {
                response.status to OpenApiSpec.ResponseDetails(
                    response.descr,
                    mapOf(
                        ContentType.APPLICATION_JSON to mapOf(
                            "schema" to if (response.isCollection) {
                                OpenApiSpec.SchemaType(
                                    type = "array",
                                    items = OpenApiSpec.SchemaRef(
                                        type = schema.type,
                                        `$ref` = schema.`$ref`
                                    )
                                )
                            } else {
                                schema
                            }
                        )
                    )
                )
            }
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

    private fun ConeKotlinType.toEndpointBody(): OpenApiSpec.ObjectType? {
        return if (isStringOrPrimitive()) {
            OpenApiSpec.ObjectType(type = toString().toSwaggerType())
        } else {
            if (config.requestBody) {
                generateTypeAndVisitMemberDescriptors()
            } else {
                null
            }
        }
    }

    private fun ConeKotlinType?.isKtorResourceAnnotated(): Boolean =
        this?.toRegularClassSymbol(session)?.hasAnnotation(ClassIds.KTOR_RESOURCE_ANNOTATION, session) == true

    private fun FirRegularClassSymbol.findAnnotation(classId: ClassId): FirAnnotation? {
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

    private fun FirFunctionCall.findDocsDescription(session: FirSession): KtorDescriptionBag {
        val docsAnnotation = findAnnotationNamed(KtorDescription::class.simpleName!!)
            ?: return KtorDescriptionBag()

        return docsAnnotation.extractDescription(session)
    }

    @OptIn(PrivateForInline::class)
    private fun FirFunctionCall.findRespondsAnnotation(session: FirSession): List<KtorK2ResponseBag>? {
        val annotation = findAnnotationNamed(KtorResponds::class.simpleName!!)
        return annotation?.let {
            val resolved = FirExpressionEvaluator.evaluateAnnotationArguments(annotation, session)
            val mapping = resolved?.entries?.find { it.key.asString() == "mapping" }?.value?.result
            mapping?.accept(RespondsAnnotationVisitor(session), null)
        }
    }
}
