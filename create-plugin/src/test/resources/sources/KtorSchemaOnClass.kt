package sources

import io.github.tabilzad.ktor.annotations.KtorSchema
import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import java.time.Instant


@KtorSchema(
    type = "string",
    format = "iso8601",
    description = "This is serialized as ISO8601 formated time string"
)
data class DescribedClass(
    val field1: String,
    val field2: Int,
    val field3: NestedObject,
    val field4: Instant
)
// will be ignored
data class NestedObject(
    val subField: List<String>,
    val subField2: String
)

@GenerateOpenApi
fun Application.testDescriptionBody() {
    routing {
        route("/v1") {
            post("/requestWithDescribedFields") {
                call.receive<DescribedClass>()
            }
        }
    }
}
