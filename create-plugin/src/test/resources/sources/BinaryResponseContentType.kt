package sources

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.github.tabilzad.ktor.annotations.KtorResponds
import io.github.tabilzad.ktor.annotations.ResponseEntry
import io.github.tabilzad.ktor.annotations.responds
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.routing.*

@GenerateOpenApi
fun Application.binaryResponseContentType() {
    routing {
        // DSL: explicit application/pdf with ByteArray body → emits {type: string, format: binary}
        post("/pdf") {
            responds<ByteArray>(HttpStatusCode.OK, contentType = "application/pdf", description = "PDF document")
        }

        // DSL: explicit image/png with ByteArray body
        post("/png") {
            responds<ByteArray>(HttpStatusCode.OK, contentType = "image/png")
        }

        // Annotation form with contentType
        @KtorResponds(
            [
                ResponseEntry(
                    "200",
                    ByteArray::class,
                    description = "PDF document",
                    contentType = "application/pdf"
                ),
            ]
        )
        post("/pdfAnnotation") {
        }
    }
}
