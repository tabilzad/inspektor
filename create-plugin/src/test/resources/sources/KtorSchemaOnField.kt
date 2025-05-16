package sources

import io.github.tabilzad.ktor.annotations.KtorSchema
import io.github.tabilzad.ktor.annotations.KtorField
import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import java.time.Instant

@KtorSchema(
    description = "This has described fields"
)
data class DescribedClassWithFields(
    @KtorField(
        type = "string",
        format = "date-time",
    )
    val myInstant: Instant,
    @KtorField(
        type = "string",
        pattern = """^\d{3}-\d{2}-\d{4}$""",
    )
    val field2: Int,
    @KtorField(
        description = "description on a property",
    )
    val password: ValueClassLike,
)

@KtorSchema(
    type = "string",
    format = "password",
    description = "description on the type"
)
data class ValueClassLike(val pass: String)

@GenerateOpenApi
fun Application.testDescriptionBody() {
    routing {
        route("/v1") {
            post("/requestWithDescribedFieldsInClass") {
                call.receive<DescribedClassWithFields>()
            }
        }
    }
}
