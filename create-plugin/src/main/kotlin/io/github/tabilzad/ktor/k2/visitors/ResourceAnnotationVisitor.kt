package io.github.tabilzad.ktor.k2.visitors

import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirAnnotationArgumentMapping
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.visitors.FirDefaultVisitor

internal class ResourceAnnotationVisitor(private val session: FirSession) : FirDefaultVisitor<String?, String?>() {

    override fun visitElement(element: FirElement, data: String?): String? {
        // the bellow overrides didn't match the specific FirElement we attempted to visit
        // or this is the exist point so just return what we've collected.
        return data
    }

    override fun visitAnnotationCall(annotationCall: FirAnnotationCall, data: String?): String? {
        return annotationCall.argumentMapping.accept(this, data)
    }

    override fun visitAnnotation(annotation: FirAnnotation, data: String?): String? {
        return annotation.argumentMapping.accept(this, data)
    }

    override fun visitAnnotationArgumentMapping(
        annotationArgumentMapping: FirAnnotationArgumentMapping,
        data: String?
    ): String? {
        return annotationArgumentMapping.mapping.entries.firstOrNull()?.let { (_, value) ->
            value.accept(StringResolutionVisitor(session), "")
        } ?: data
    }
}
