package io.github.tabilzad.ktor.annotations

import io.ktor.http.*
import io.ktor.server.routing.*

inline fun <reified T> RoutingContext.responds(status: HttpStatusCode): Unit = Unit
fun RoutingContext.respondsNothing(status: HttpStatusCode): Unit = Unit
