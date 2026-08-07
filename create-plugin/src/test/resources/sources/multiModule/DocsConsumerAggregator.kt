package com.example.server

import com.example.docsonly.ExternalAuditRecord
import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*

@GenerateOpenApi
fun Application.auditModule() {
    routing {
        post("/audit") {
            // The record type comes from a COMPILED docs-only contributor: its KDocs are
            // not in the binary and must arrive through the sidecar.
            val record = call.receive<ExternalAuditRecord>()
            println(record)
        }
    }
}
