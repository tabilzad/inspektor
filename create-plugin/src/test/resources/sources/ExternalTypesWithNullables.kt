package sources

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import sources.precompiled.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*

data class MyLocalType(
    val optionalField: String?,
    val requiredField: String,
    val optionalWithInitializer: String = "default",
    val optionalWithInitializerFromOther: String = requiredField,
    val externalType: MyExternalType,
)

@GenerateOpenApi
fun Application.requiredOrOptionalFields() {
    routing {
        route("/v1") {
            post("/requiredOrOptionalFieldsOnLocalType") {
                call.receive<MyLocalType>()
            }
            post("/requiredOrOptionalFieldsOnExternalType") {
                call.receive<MyExternalType>()
            }
        }
    }
}
