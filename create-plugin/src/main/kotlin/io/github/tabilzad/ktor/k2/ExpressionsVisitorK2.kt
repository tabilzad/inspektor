package io.github.tabilzad.ktor.k2

import io.github.tabilzad.ktor.*
import io.github.tabilzad.ktor.annotations.KtorDescription
import io.github.tabilzad.ktor.annotations.KtorResponds
import io.github.tabilzad.ktor.visitors.KtorDescriptionBag
import io.github.tabilzad.ktor.visitors.toSwaggerType
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.utils.classId
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.references.resolved
import org.jetbrains.kotlin.fir.references.toResolvedFunctionSymbol
import org.jetbrains.kotlin.fir.resolve.firClassLike
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.fir.visitors.FirDefaultVisitor
import org.jetbrains.kotlin.util.PrivateForInline

internal class ExpressionsVisitorK2(
    private val config: PluginConfiguration,
    private val context: CheckerContext,
    private val session: FirSession,
    private val log: MessageCollector,
) : FirDefaultVisitor<List<KtorElement>, KtorElement?>() {

    init {
        println("BeginK2 Visitor")
    }

    val classNames = mutableListOf<OpenApiSpec.ObjectType>()

    override fun visitElement(expression: FirElement, parent: KtorElement?): List<KtorElement> {
        return parent.wrapAsList()
    }

    @OptIn(PrivateForInline::class)
    private fun FirFunction.findTags(session: FirSession): Set<String>? {
        val annotation = findAnnotation(ClassIds.KTOR_TAGS_ANNOTATION, session) ?: return null
        val resolved = FirExpressionEvaluator.evaluateAnnotationArguments(annotation, session)
        return resolved?.entries?.find { it.key.asString() == "tags" }?.value?.result?.accept(
            StringArrayLiteralVisitor(),
            emptyList()
        )?.toSet()
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
    override fun visitBlock(block: FirBlock, parent: KtorElement?): List<KtorElement> {

        if (parent is EndPoint) {

            val receiveCall = block.statements.findReceiveCallExpression()
            val queryParam = block.statements.findQueryParameterExpression()

            if (queryParam.isNotEmpty()) {
                parent.queryParameters = parent.queryParameters merge queryParam.toSet()
            }

            if (receiveCall != null) {
                val kotlinType = receiveCall.resolvedType
                if (kotlinType.isPrimitiveOrNullablePrimitive || kotlinType.isString || kotlinType.isNullableString) {
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


    private fun ConeKotlinType.generateTypeAndVisitMemberDescriptors(): OpenApiSpec.ObjectType {

        val jetTypeFqName = fqNameStr()

        val objectType = OpenApiSpec.ObjectType(
            type = "object",
            properties = mutableMapOf(),
            fqName = jetTypeFqName,
            contentBodyRef = "#/components/schemas/${jetTypeFqName}",
        )
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

    private fun List<FirStatement>.findQueryParameterExpression(): List<String> {
        val queryParams = mutableListOf<String>()
        flatMap { it.allChildren }.filterIsInstance<FirFunctionCall>()
            .forEach { it.accept(QueryParamsVisitor(session), queryParams) }
        return queryParams
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


    private fun FirElement?.isKtorApplicationCall(): Boolean {
        return if (this is FirQualifiedAccessExpression) {
            extensionReceiver?.resolvedType?.classId == ClassIds.KTOR_APPLICATION
        } else {
            false
        }
    }

    private fun FirSimpleFunction.isKtorApplicationCall(): Boolean {
        val classId = receiverParameter?.typeRef?.firClassLike(session)?.classId
        return classId == ClassIds.KTOR_APPLICATION || classId == ClassIds.KTOR_ROUTE
    }

    private fun FirQualifiedAccessExpression?.isARouteDefinition(): Boolean {
        return this?.resolvedType?.classId == ClassIds.KTOR_ROUTE
    }


    @OptIn(SymbolInternals::class)
    private fun FirFunctionCall.extractArguments(): Map<String, String> {
        val expression = this
        val names = (expression.calleeReference.resolved?.resolvedSymbol?.fir as FirFunction).valueParameters.map {
            it.name.asString()
        }

        val values = expression.arguments
            .filterIsInstance<FirLiteralExpression>()
            .map { it.value }
            .filterIsInstance<String>()

        return names.zip(values).toMap()
    }

    private fun FirFunctionCall.findLambda(): FirAnonymousFunctionExpression? {
        return arguments
            .filterIsInstance<FirAnonymousFunctionExpression>()
            .lastOrNull()
    }


    @OptIn(SymbolInternals::class)
    override fun visitFunctionCall(functionCall: FirFunctionCall, data: KtorElement?): List<KtorElement> {
        var resultElement: KtorElement? = null
        val resolvedExp = functionCall.toResolvedCallableReference(session)
        val expName = resolvedExp?.name?.asString() ?: ""

        val tagsFromAnnotation = functionCall.calleeReference.toResolvedFunctionSymbol()?.fir?.findTags(session)
        if (functionCall.isARouteDefinition() || ExpType.METHOD.labels.contains(expName)) {

            val args = functionCall.extractArguments()

            val routePathArg = args.entries.find { it.key == "path" }?.value

            if (ExpType.ROUTE.labels.contains(expName)) {
                if (data == null) {
                    resultElement = routePathArg?.let {
                        DocRoute(routePathArg)
                    } ?: run {
                        DocRoute(expName)
                    }
                } else {
                    if (data is DocRoute) {
                        val newElement = DocRoute(
                            routePathArg.toString(),
                            tags = data.tags merge tagsFromAnnotation
                        )

                        resultElement = newElement
                        data.children.add(newElement)
                    }
                }
            } else if (ExpType.METHOD.labels.contains(expName)) {
                val (summary, descr, tags) = functionCall.findDocsDescription(session)
                val responds = functionCall.findRespondsAnnotation(session)
                val responses = responds?.associate { response ->

                    val kotlinType = response.type

                    val schema = if (kotlinType?.isPrimitiveOrNullablePrimitive == true || kotlinType?.isString == true || kotlinType?.isNullableString == true) {
                        OpenApiSpec.SchemaType(
                            type = kotlinType.toString().toSwaggerType()
                        )
                    } else {

                        val typeRef = response.type?.generateTypeAndVisitMemberDescriptors()
                        OpenApiSpec.SchemaType(
                            `$ref` = "${typeRef?.contentBodyRef}"
                        )
                    }
                    if (!response.isCollection) {
                        response.status to
                                OpenApiSpec.ResponseDetails(
                                    response.descr ?: "",
                                    mapOf(
                                        ContentType.APPLICATION_JSON to mapOf(
                                            "schema" to schema
                                        )
                                    )
                                )
                    } else {
                        response.status to
                                OpenApiSpec.ResponseDetails(
                                    response.descr ?: "",
                                    mapOf(
                                        ContentType.APPLICATION_JSON to mapOf(
                                            "schema" to OpenApiSpec.SchemaType(
                                                type = "array",
                                                items = OpenApiSpec.SchemaRef(
                                                    type = schema.type,
                                                    `$ref` = schema.`$ref`
                                                )
                                            )
                                        )
                                    )
                                )
                    }
                }

                if (data == null) {
                    resultElement = routePathArg?.let {
                        EndPoint(
                            routePathArg,
                            expName,
                            description = descr,
                            summary = summary,
                            tags = tags merge tagsFromAnnotation,
                            responses = responses
                        )
                    } ?: EndPoint(
                        expName,
                        description = descr,
                        summary = summary,
                        tags = tags merge tagsFromAnnotation,
                        responses = responses
                    )
                } else {
                    if (data is DocRoute) {
                        val endPoint = EndPoint(
                            routePathArg,
                            expName,
                            description = descr,
                            summary = summary,
                            tags = tags merge tagsFromAnnotation,
                            responses = responses
                        )
                        resultElement = endPoint
                        data.children.add(endPoint)
                    } else {
                        log.report(CompilerMessageSeverity.WARNING, "Endpoints cant have Endpoint as routes", null)
                    }
                }
            }
        }
        val lambda = functionCall.findLambda()
        if (lambda != null) {
            lambda.accept(this, resultElement ?: data)
        } else {

            val declaration = functionCall.calleeReference.toResolvedFunctionSymbol()?.fir

            // val tagsFromAnnotation = declaration?.findTags(session)

            if (data is DocRoute) {
                val accept = declaration?.accept(this, null)

                accept?.onEach {
                    it.tags = it.tags merge tagsFromAnnotation
                }
                data.children.addAll(accept ?: emptyList())
            } else {
                declaration?.accept(this, data)
            }
        }

        return (resultElement ?: data).wrapAsList()
    }


    override fun visitAnonymousFunctionExpression(
        anonymousFunctionExpression: FirAnonymousFunctionExpression,
        data: KtorElement?
    ): List<KtorElement> = anonymousFunctionExpression.anonymousFunction.body?.accept(this, data) ?: data.wrapAsList()

    override fun visitAnonymousFunction(
        anonymousFunction: FirAnonymousFunction,
        parent: KtorElement?
    ): List<KtorElement> = anonymousFunction.body?.accept(this, parent) ?: parent.wrapAsList()

}


internal data class KtorK2ResponseBag(
    val descr: String,
    val status: String,
    val type: ConeKotlinType?,
    val isCollection: Boolean = false
)

private fun KtorElement?.wrapAsList() = this?.let { listOf(this) } ?: emptyList()

@OptIn(PrivateForInline::class)
private fun FirFunctionCall.findDocsDescription(session: FirSession): KtorDescriptionBag {
    val docsAnnotation = findAnnotation(KtorDescription::class.simpleName!!)
        ?: return KtorDescriptionBag()

    val resolved = FirExpressionEvaluator.evaluateAnnotationArguments(docsAnnotation, session)

    val summary = resolved?.entries?.find { it.key.asString() == "summary" }?.value?.result
    val descr = resolved?.entries?.find { it.key.asString() == "description" }?.value?.result
    val tags = resolved?.entries?.find { it.key.asString() == "tags" }?.value?.result

    return KtorDescriptionBag(
        summary = summary?.accept(StringResolutionVisitor(), ""),
        descr = descr?.accept(StringResolutionVisitor(), ""),
        tags = tags?.accept(StringArrayLiteralVisitor(), emptyList())?.toSet()
    )
}

@OptIn(PrivateForInline::class)
private fun FirFunctionCall.findRespondsAnnotation(session: FirSession): List<KtorK2ResponseBag>? {
    val annotation = findAnnotation(KtorResponds::class.simpleName!!)
    return annotation?.let {
        val resolved = FirExpressionEvaluator.evaluateAnnotationArguments(annotation, session)
        val mapping = resolved?.entries?.find { it.key.asString() == "mapping" }?.value?.result
        mapping?.accept(RespondsAnnotationVisitor(), null)
    }
}
