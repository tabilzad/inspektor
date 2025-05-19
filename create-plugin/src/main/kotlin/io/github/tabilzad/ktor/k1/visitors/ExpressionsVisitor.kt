package io.github.tabilzad.ktor.k1.visitors

import io.github.tabilzad.ktor.*
import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.github.tabilzad.ktor.annotations.KtorDescription
import io.github.tabilzad.ktor.annotations.KtorResponds
import io.github.tabilzad.ktor.k1.findAnnotation
import io.github.tabilzad.ktor.k1.hasAnnotation
import io.github.tabilzad.ktor.output.OpenApiSpec
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.js.descriptorUtils.getKotlinTypeFqName
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.argumentIndex
import org.jetbrains.kotlin.psi.psiUtil.getAnnotationEntries
import org.jetbrains.kotlin.psi.psiUtil.getCallNameExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.utils.addToStdlib.UnsafeCastFunction
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

internal class ExpressionsVisitor(
    private val config: PluginConfiguration,
    private val context: BindingContext
) : KtVisitor<List<KtorElement>, KtorElement?>() {
    init {
        println("BeginVisitor")
    }

    val classNames = mutableListOf<OpenApiSpec.TypeDescriptor>()
    override fun visitProperty(property: KtProperty, data: KtorElement?): List<KtorElement> {
        val dotQualified = property.children.filterIsInstance<KtDotQualifiedExpression>()
        val arrayAccess = property.children.filterIsInstance<KtArrayAccessExpression>()
        return dotQualified.plus(arrayAccess).flatMap { it.accept(this, data) }
    }

    override fun visitArrayAccessExpression(
        expression: KtArrayAccessExpression,
        parent: KtorElement?
    ): List<KtorElement> {

        if (expression.isKtorQueryParam() && parent is EndPoint) {

            val queryParamValue = resolveQueryParamFromArrayAccessIndex(expression, this@ExpressionsVisitor.context)

            if (queryParamValue != null) {
                parent.parameters = parent.parameters merge queryParamValue
            }
        }

        return parent?.let { listOf(it) } ?: emptyList()
    }

    // Evaluation Order 2
    override fun visitDeclaration(dcl: KtDeclaration, data: KtorElement?): List<KtorElement> {
        return if (dcl is KtNamedFunction) {
            dcl.bodyExpression?.accept(this, data) ?: emptyList()
        } else {
            emptyList()
        }
    }

    // Evaluation Order 3
    override fun visitBlockExpression(
        expression: KtBlockExpression,
        parent: KtorElement?
    ): List<KtorElement> {
        return if (parent is EndPoint) {
            expression.statements
                .flatMap {
                    it?.accept(this, parent) ?: emptyList()
                }
        } else {
            val results = expression.statements
                .flatMap {
                    it.accept(this, parent)
                }

            results
        }
    }

    private fun KtDotQualifiedExpression.isValidReceive(): Boolean {
        val receiver = receiverExpression
        val selector = selectorExpression
        return receiver.text == "call" &&
                selector is KtCallExpression &&
                selector.text.contains("receive") &&
                selector.typeArguments.size == 1
    }

    private fun KtExpression.findReceiveCallExpression(): KtExpression? {

        if (this is KtDotQualifiedExpression && isValidReceive()) {
            return this
        }

        val deepSearchInExpressions = children
            .filterIsInstance<KtExpression>()

        val deepSeachLambdas = children
            .filterIsInstance<KtLambdaArgument>()
            .mapNotNull { it.getLambdaExpression() }

        for (subExpression in deepSearchInExpressions) {
            val findReceiveCallExpression = subExpression.findReceiveCallExpression()
            if (findReceiveCallExpression != null) {
                return findReceiveCallExpression
            }
        }
        for (subExpression in deepSeachLambdas) {
            val findReceiveCallExpression = subExpression.findReceiveCallExpression()
            if (findReceiveCallExpression != null) {
                return findReceiveCallExpression
            }
        }

        return null
    }

    private fun KtExpression.findArrayAccessExpressions(
        params: MutableList<KtArrayAccessExpression> = mutableListOf()
    ): List<KtArrayAccessExpression> {

        if (this is KtArrayAccessExpression && isKtorQueryParam()) {
            return params.apply {
                add(this@findArrayAccessExpressions)
            }
        }

        val deepSearchInExpressions = children
            .filterIsInstance<KtExpression>()

        val deepSearchLambdas = children
            .filterIsInstance<KtLambdaArgument>()
            .mapNotNull { it.getLambdaExpression() }

        for (subExpression in deepSearchInExpressions) {
            subExpression.findArrayAccessExpressions(params)
        }
        for (subExpression in deepSearchLambdas) {
            subExpression.findArrayAccessExpressions(params)
        }

        return params
    }

    private fun KotlinType?.isPrimitiveOrString(): Boolean {
        if (this == null) return false
        return KotlinBuiltIns.isPrimitiveType(this) || KotlinBuiltIns.isString(this)
    }

    private fun KtExpression.isKtorApplicationCall(): Boolean {
        return (children
            .firstIsInstanceOrNull<KtReferenceExpression>()
            ?.getType(this@ExpressionsVisitor.context)
            ?.getKotlinTypeFqName(false)
            ?.endsWith("ApplicationCall") == true) ||
                (children
                    .firstIsInstanceOrNull<KtDotQualifiedExpression>()
                    ?.receiverExpression
                    ?.getType(this@ExpressionsVisitor.context)
                    ?.getKotlinTypeFqName(false)
                    ?.endsWith("ApplicationCall") == true) || (
                children.firstIsInstanceOrNull<KtArrayAccessExpression>()
                    ?.children
                    ?.firstIsInstanceOrNull<KtDotQualifiedExpression>()
                    ?.getType(this@ExpressionsVisitor.context)
                    ?.getKotlinTypeFqName(false)
                    ?.endsWith("ApplicationCall") == true
                )
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun resolveQueryParamFromArrayAccessIndex(
        arrayAccessExpression: KtArrayAccessExpression?,
        bindingContext: BindingContext
    ): Set<QueryParamSpec>? {
        if (arrayAccessExpression == null) return null
        val indices = arrayAccessExpression.indexExpressions

        return buildSet {
            for (indexExpression in indices) {
                when (indexExpression) {

                    is KtReferenceExpression -> {
                        val resolvedCall = indexExpression.getResolvedCall(bindingContext)
                        val resultingDescriptor = resolvedCall?.resultingDescriptor

                        if (resultingDescriptor is PropertyDescriptor) {
                            resultingDescriptor.compileTimeInitializer?.value?.toString()?.let {
                                add(QueryParamSpec(it))
                            }
                        }
                    }

                    is KtStringTemplateExpression -> {

                        indexExpression.entries.fold("") { acc, next ->
                            var r = acc
                            when (next) {
                                is KtLiteralStringTemplateEntry -> {
                                    r = acc + next.text
                                }

                                is KtBlockStringTemplateEntry -> {
                                    val reference = next.children
                                        .firstIsInstanceOrNull<KtReferenceExpression>()
                                        .getResolvedCall(bindingContext)?.resultingDescriptor

                                    val dotQualifiedReference = next.children
                                        .firstIsInstanceOrNull<KtDotQualifiedExpression>()
                                        .getResolvedCall(bindingContext)?.resultingDescriptor

                                    val ref = reference ?: dotQualifiedReference

                                    if (ref is PropertyDescriptor) {
                                        r = acc + ref.compileTimeInitializer?.value?.toString()
                                    }
                                }
                            }
                            r
                        }.let {
                            add(QueryParamSpec(it))
                        }
                    }

                    is KtBinaryExpression -> {
                        evaluateBinaryExpression(indexExpression).let {
                            add(QueryParamSpec(it))
                        }
                    }

                    is KtDotQualifiedExpression -> {
                        val receiverExpression = indexExpression.receiverExpression
                        val selectorExpression = indexExpression.selectorExpression
                        when (receiverExpression) {
                            is KtDotQualifiedExpression -> {
                                val receiver = receiverExpression.receiverExpression
                                val selector = receiverExpression.selectorExpression

                                if (receiver is KtNameReferenceExpression && selector is KtNameReferenceExpression) {
                                    // The left-hand side is an enum class
                                    val enumClassDescriptor = bindingContext[BindingContext.REFERENCE_TARGET, receiver]
                                    // The right-hand side is an enum entry
                                    val enumEntryDescriptor = bindingContext[BindingContext.REFERENCE_TARGET, selector]
                                    if (enumClassDescriptor is ClassDescriptor && enumClassDescriptor.kind == ClassKind.ENUM_CLASS
                                        && enumEntryDescriptor is ClassDescriptor && enumEntryDescriptor.kind == ClassKind.ENUM_ENTRY
                                    ) {
                                        val enumEntryName = enumEntryDescriptor.findPsi()
                                            ?.children
                                            ?.filterIsInstance<KtInitializerList>()
                                            ?.firstOrNull()
                                            ?.text
                                            ?.replace("[()\"]".toRegex(), "")

                                        if (enumEntryName != null) {
                                            add(QueryParamSpec(enumEntryName))
                                        }
                                    }
                                }
                            }
                        }

                        when (selectorExpression) {
                            is KtReferenceExpression -> {
                                val resolvedCall = indexExpression.getResolvedCall(bindingContext)
                                val resultingDescriptor = resolvedCall?.resultingDescriptor

                                if (resultingDescriptor is PropertyDescriptor) {
                                    resultingDescriptor.compileTimeInitializer?.value?.toString()?.let {
                                        add(QueryParamSpec(it))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }.ifEmpty { null }
    }

    private fun KtExpression.deepSearchOfAppCall(): Boolean {
        return if (this.isKtorApplicationCall()) {
            true
        } else if (children.isNotEmpty()) {
            return children.filterIsInstance<KtExpression>()
                .any { it.deepSearchOfAppCall() }
        } else {
            false
        }
    }

    private fun KtExpression.isKtorQueryParam() = text.contains("call.request.queryParameters")
            || text.contains("call.request.rawQueryParameters")

    // check for package path
    private fun KtExpression.hasKtorReceive() = text.contains("call.receive")

    @Suppress("NestedBlockDepth")
    override fun visitDotQualifiedExpression(
        expression: KtDotQualifiedExpression,
        parent: KtorElement?
    ): List<KtorElement> {

        if (expression.deepSearchOfAppCall()) {
            // this expression has ApplicationCall references somewhere

            if (parent is EndPoint) {
                // this expression has call.receive references somewhere
                if (expression.hasKtorReceive()) {

                    val receiveCall = expression.findReceiveCallExpression()

                    val kotlinType = receiveCall?.getType(context)

                    kotlinType?.let {
                        if (!kotlinType.isPrimitiveOrString()) {
                            if (config.requestBody) {
                                parent.body = kotlinType.generateTypeAndVisitMemberDescriptors()
                            }
                        } else {
                            parent.body = OpenApiSpec.TypeDescriptor(type = kotlinType.toString().toSwaggerType())
                        }
                    }
                } else {
                    val last = expression.children
                        .filterIsInstance<KtExpression>()
                        .find { it.deepSearchOfAppCall() }?.firstChild

                    if (last is KtExpression) {
                        last.accept(this, parent)
                    }
                }

                if (expression.isKtorQueryParam()) {
                    val arrayExpressions = expression.findArrayAccessExpressions()

                    val queryParamValue = arrayExpressions.flatMap {
                        resolveQueryParamFromArrayAccessIndex(it, this@ExpressionsVisitor.context).orEmpty()
                    }.toSet()

                    parent.parameters = parent.parameters merge queryParamValue
                }
            }
        } else {
            expression.children.firstIsInstanceOrNull<KtCallExpression>()?.visitFunctionDeclaration(parent)
        }
        return parent?.let { listOf(it) } ?: emptyList()
    }

    private fun KotlinType.generateTypeAndVisitMemberDescriptors(): OpenApiSpec.TypeDescriptor {
        val jetTypeFqName = getKotlinTypeFqName(false)
        val r = OpenApiSpec.TypeDescriptor(
            type = "object",
            fqName = jetTypeFqName,
            ref = "#/components/schemas/$jetTypeFqName",
        )
        if (!classNames.names.contains(jetTypeFqName)) {

            classNames.add(r)

            memberScope
                .forEachVariable(config) { d ->
                    val classDescriptorVisitor = ClassDescriptorVisitor(config, context)
                    d.accept(classDescriptorVisitor, r)
                    classNames.addAll(classDescriptorVisitor.classNames)
                }
        }
        return r
    }

    private fun ResolvedCall<out CallableDescriptor>?.isARouteDefinition(): Boolean {

        val doesExtendRoute = this?.resultingDescriptor
            ?.extensionReceiverParameter
            ?.type
            ?.getKotlinTypeFqName(false)?.contains("Route") == true

        val doesContainPathValue = this?.valueArguments
            ?.keys
            ?.map { it.name.toString() }
            ?.contains("path") == true

        return doesExtendRoute && doesContainPathValue
    }

    override fun visitAnnotatedExpression(exp: KtAnnotatedExpression, parent: KtorElement?): List<KtorElement> {
        return if (exp.baseExpression is KtCallExpression) {
            visitCallExpressionInternal(exp.baseExpression as KtCallExpression, parent)
        } else if (exp.baseExpression != null) {
            super.visitAnnotatedExpression(exp, parent)
        } else {
            parent.wrapAsList()
        }
    }

    private fun KtExpression.visitFunctionDeclaration(parent: KtorElement?): List<KtorElement>? {
        return if (this is KtCallExpression) {
            resolveToDescriptor(this, this@ExpressionsVisitor.context)?.let {
                inspectFunctionBody(it, parent)
            }
        } else {
            null
        }
    }

    private fun resolveToDescriptor(
        callExpression: KtCallExpression,
        bindingContext: BindingContext
    ): CallableDescriptor? {
        // This retrieves the resolved call from the call expression using the binding context.
        val resolvedCall = callExpression.getResolvedCall(bindingContext)
        return resolvedCall?.resultingDescriptor
    }

    private fun inspectFunctionBody(descriptor: CallableDescriptor, parent: KtorElement?): List<KtorElement> {
        val source = DescriptorToSourceUtils.getSourceFromDescriptor(descriptor)
        return if (source is KtFunction) {
            val newTags = mutableSetOf<String>()
            if (source.hasAnnotation(GenerateOpenApi::class.simpleName)) {
                val annotation = source.findAnnotation(GenerateOpenApi::class.simpleName)
                var tags =
                    annotation?.valueArguments?.find { it.getArgumentName()?.asName?.asString() == "tags" }

                if (tags == null) {
                    tags = annotation?.valueArgumentList?.arguments?.find { it.argumentIndex == 0 }
                }

                tags?.getArgumentExpression()?.extractTags()?.let { extractedTags ->
                    newTags.addAll(extractedTags)
                }
            }
            source.bodyExpression?.accept(this, parent)?.onEach { route ->
                route.forEachEndpointRecursively {
                    it.tags = it.tags merge newTags.ifEmpty { null }
                }
            } ?: emptyList()
        } else {
            emptyList()
        }
    }

    private fun KtorElement.forEachEndpointRecursively(onEach: (EndPoint) -> Unit) {
        when (this) {
            is EndPoint -> {
                onEach(this)
            }

            is DocRoute -> {
                this.children.forEach {
                    it.forEachEndpointRecursively(onEach)
                }
            }
        }
    }

    @Suppress("LongMethod", "NestedBlockDepth", "CyclomaticComplexMethod")
    private fun visitCallExpressionInternal(expression: KtCallExpression, parent: KtorElement?): List<KtorElement> {
        println("visitCallExpression $parent")
        val expName = expression.getCallNameExpression()?.text.toString()
        var resultElement: KtorElement? = parent

        val resolvedExp = expression.getResolvedCall(context)

        if (resolvedExp?.isARouteDefinition() == true || ExpType.METHOD.labels.contains(expName)) {

            val pathKey = resolvedExp?.valueArguments?.keys?.find { it.name.toString().contains("path") }

            val routePathArg = resolvedExp?.valueArguments?.get(pathKey)
                ?.arguments
                ?.firstOrNull()
                ?.getArgumentExpression()
                ?.text
                ?.replace("\"", "")

            if (ExpType.ROUTE.labels.contains(expName)) {
                if (parent == null) {
                    println("Adding new route")
                    resultElement = routePathArg?.let {
                        DocRoute(routePathArg)
                    } ?: run {
                        DocRoute(expName)
                    }
                } else {
                    if (parent is DocRoute) {
                        // we are under some base route definition
                        val newElement = DocRoute(
                            routePathArg.toString(),
                            tags = parent.tags
                        )

                        resultElement = newElement
                        parent.children.add(newElement)
                    }
                }
            } else if (ExpType.METHOD.labels.contains(expName)) {
                val (summary, descr, tags) = expression.findDocsDescription()
                val responds = expression.findRespondsAnnotation(context)

                val responses = responds?.associate { response ->
                    val typeRef = response.type.generateTypeAndVisitMemberDescriptors()
                    if (!response.isCollection) {
                        response.status to
                                OpenApiSpec.ResponseDetails(
                                    response.descr,
                                    mapOf(
                                        ContentType.APPLICATION_JSON to mapOf(
                                            "schema" to OpenApiSpec.TypeDescriptor(
                                                type = null,
                                                ref = "${typeRef.ref}"
                                            )
                                        )
                                    )
                                )
                    } else {
                        response.status to
                                OpenApiSpec.ResponseDetails(
                                    response.descr,
                                    mapOf(
                                        ContentType.APPLICATION_JSON to mapOf(
                                            "schema" to OpenApiSpec.TypeDescriptor(
                                                type = "array",
                                                items = OpenApiSpec.TypeDescriptor(
                                                    type = null,
                                                    ref = "${typeRef.ref}"
                                                )
                                            )
                                        )
                                    )
                                )
                    }
                }

                if (parent == null) {
                    resultElement = routePathArg?.let {
                        EndPoint(
                            routePathArg,
                            expName,
                            description = descr,
                            summary = summary,
                            tags = tags,
                            responses = responses
                        )
                    } ?: EndPoint(expName, description = descr, summary = summary, tags = tags, responses = responses)
                } else {
                    if (parent is DocRoute) {
                        val endPoint = EndPoint(
                            routePathArg,
                            expName,
                            description = descr,
                            summary = summary,
                            tags = tags,
                            responses = responses
                        )
                        resultElement = endPoint
                        parent.children.add(resultElement)
                    } else {
                        throw IllegalArgumentException("Endpoints cant have Endpoint as routes")
                    }
                }
            } else if (parent == null) {
                resultElement = DocRoute()
            }
        }

        val lambda = expression.lambdaArguments.lastOrNull()
        return if (lambda != null) { // visiting lambda of call expression

            val res = lambda.getLambdaExpression()?.accept(this, resultElement)

            resultElement.wrapAsList().ifEmpty {
                res.orEmpty()
            }
        } else { // the expression doesn't have lambda so visit its implementation to look for potential routes
            val res = expression.visitFunctionDeclaration(resultElement)
            resultElement.wrapAsList().ifEmpty {
                res.orEmpty()
            }
        }
    }

    override fun visitCallExpression(
        expression: KtCallExpression,
        parent: KtorElement?
    ): List<KtorElement> = visitCallExpressionInternal(expression, parent)

    private fun KtorElement?.wrapAsList() = this?.let { listOf(this) } ?: emptyList()

    // Evaluation Order 1
    override fun visitNamedFunction(function: KtNamedFunction, parent: KtorElement?): List<KtorElement> {

        var newParent = parent
        if (function.hasAnnotation(GenerateOpenApi::class.simpleName)) {
            val annotation = function.findAnnotation(GenerateOpenApi::class.simpleName)
            var tags =
                annotation?.valueArguments?.find { it.getArgumentName()?.asName?.asString() == "tags" }

            if (tags == null) {
                tags = annotation?.valueArgumentList?.arguments?.find { it.argumentIndex == 0 }
            }

            val extractedTags = tags?.getArgumentExpression()?.extractTags()
            if (parent != null) {
                parent.tags = parent.tags merge extractedTags
            } else {
                newParent = DocRoute("/", tags = extractedTags)
            }
        }

        return super.visitNamedFunction(function, newParent)
    }

    override fun visitLambdaExpression(lambdaExpression: KtLambdaExpression, parent: KtorElement?): List<KtorElement> {
        println("visitLambdaExpression $parent")
        return lambdaExpression.bodyExpression?.accept(this, parent) ?: parent.wrapAsList()
    }
}

internal data class KtorDescriptionBag(
    val summary: String? = null,
    val description: String? = null,
    val tags: Set<String>? = null,
    val operationId: String? = null,
    val isRequired: Boolean? = false,
    val explicitType: String? = null,
    val serializedAs: ConeKotlinType? = null,
    val format: String? = null
)

internal data class KtorResponseBag(
    val descr: String,
    val status: String,
    val type: KotlinType,
    val isCollection: Boolean
)

@OptIn(UnsafeCastFunction::class)
private fun KtExpression.findDocsDescription(): KtorDescriptionBag {

    return getAnnotationEntries().find { it ->
        it.typeReference?.typeElement.safeAs<KtUserType>()?.referencedName == KtorDescription::class.simpleName!!
    }?.let { annotationDescriptor ->

        var summary = annotationDescriptor.valueArguments.find { it.getArgumentName()?.asName?.asString() == "summary" }

        if (summary == null) {
            summary = annotationDescriptor.valueArgumentList?.arguments?.find { it.argumentIndex == 0 }
        }

        var desc =
            annotationDescriptor.valueArguments.find { it.getArgumentName()?.asName?.asString() == "description" }

        if (desc == null) {
            desc = annotationDescriptor.valueArgumentList?.arguments?.find { it.argumentIndex == 1 }
        }

        var tags =
            annotationDescriptor.valueArguments.find { it.getArgumentName()?.asName?.asString() == "tags" }

        if (tags == null) {
            tags = annotationDescriptor.valueArgumentList?.arguments?.find { it.argumentIndex == 2 }
        }

        KtorDescriptionBag(
            summary?.getArgumentExpression().evaluateStringConcatenation(),
            desc?.getArgumentExpression().evaluateStringConcatenation(),
            tags?.getArgumentExpression().extractTags()
        )
    } ?: KtorDescriptionBag()
}

private fun KtCallExpression.findArgumentWithName(name: String): KtValueArgument? =
    valueArguments.find { it.getArgumentName()?.asName?.asString() == name }

@OptIn(UnsafeCastFunction::class)
@Suppress("MagicNumber")
private fun KtExpression.findRespondsAnnotation(context: BindingContext): List<KtorResponseBag>? {
    return getAnnotationEntries().find { it ->
        it.typeReference?.typeElement.safeAs<KtUserType>()?.referencedName == KtorResponds::class.simpleName!!
    }?.let { annotationDescriptor ->

        annotationDescriptor.valueArguments
            .map { it.getArgumentExpression() }
            .firstIsInstanceOrNull<KtCollectionLiteralExpression>()
            ?.children
            ?.filterIsInstance<KtCallExpression>()
            ?.mapNotNull {
                val statusArg = it.findArgumentWithName("status")
                    ?: it.valueArguments.getOrNull(0)
                val classArg = it.findArgumentWithName("type")
                    ?: it.valueArguments.getOrNull(1)
                val isCollection = it.findArgumentWithName("isCollection")
                    ?: it.valueArguments.getOrNull(2)
                val descArg = it.findArgumentWithName("description")
                    ?: it.valueArguments.getOrNull(3)

                val type = classArg?.getArgumentExpression()?.getType(context)?.arguments?.firstOrNull()?.type
                if (type != null) {
                    KtorResponseBag(
                        status = statusArg?.text?.replace("\"", "") ?: "Unknown Status",
                        descr = descArg?.getArgumentExpression().evaluateStringConcatenation() ?: "",
                        type = type,
                        isCollection = isCollection?.text?.toBooleanStrictOrNull() ?: false
                    )
                } else {
                    null
                }
            }
    }
}

fun evaluateBinaryExpression(expression: KtBinaryExpression): String {
    val leftExpression = expression.left
    val rightExpression = expression.right

    val leftValue = when (leftExpression) {
        is KtBinaryExpression -> evaluateBinaryExpression(leftExpression)
        is KtStringTemplateExpression -> leftExpression.node.text.replace("\"", "")
        else -> leftExpression?.text?.replace("\"", "")
    }

    val rightValue = when (rightExpression) {
        is KtBinaryExpression -> evaluateBinaryExpression(rightExpression)
        is KtStringTemplateExpression -> rightExpression.node.text.replace("\"", "")
        else -> rightExpression?.text?.replace("\"", "")
    }

    return leftValue + rightValue
}

fun KtExpression?.evaluateStringConcatenation(): String? {
    return when (this) {
        is KtStringTemplateExpression -> {
            entries.joinToString(separator = "") { entry ->
                when (entry) {
                    is KtStringTemplateEntryWithExpression -> entry.expression?.evaluateStringConcatenation() ?: ""
                    else -> entry.text
                }
            }
        }

        is KtBinaryExpression -> evaluateBinaryExpression(this)
        else -> this?.text
    }
}

fun KtExpression?.extractTags(): Set<String>? {
    return when (this) {
        is KtCollectionLiteralExpression -> {
            innerExpressions.mapNotNull { it?.evaluateStringConcatenation() }
        }

        else -> null
    }?.toSet()
}

val ClassDescriptor.enumEntries: Set<ClassDescriptor>
    get() = DescriptorUtils.getAllDescriptors(this.unsubstitutedInnerClassesScope)
        .filter(DescriptorUtils::isEnumEntry)
        .filterIsInstance<ClassDescriptor>()
        .toSet()
