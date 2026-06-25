package io.github.tabilzad.ktor.k2.inference

import io.github.tabilzad.ktor.k2.visitors.KtorK2ResponseBag
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall

/**
 * A composable unit that, given a single resolved call inside a route handler, produces zero or more
 * inferred response descriptions. Rules are independent and combined as a list, so adding a new kind
 * of inference is adding a rule rather than editing a `when` (OCP).
 *
 * Implementations must be side-effect free and return an empty list for calls they don't recognise.
 */
internal fun interface ResponseInferenceRule {
    fun infer(call: FirFunctionCall, session: FirSession): List<KtorK2ResponseBag>
}
