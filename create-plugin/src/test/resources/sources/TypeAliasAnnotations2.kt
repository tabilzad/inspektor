package sources

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.github.tabilzad.ktor.annotations.KtorSchema
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*

data class TypeWithCutomerSerializer(val field: String, val anotherField: Int)

@KtorSchema(description = "Custom serialized type")
data class SerializableType(val diaplayName: String)

data class SomeGenericRequest(
    val typealiasedField: TypeAliasedString
)

typealias TypeAliasedString = @KtorSchema(serializedAs = SerializableType::class) TypeWithCutomerSerializer

@GenerateOpenApi
fun Application.responseBody() {
    routing {
        route("/v3") {
            post("/postGenericRequest") {
                call.receive<SomeGenericRequest>()
            }
        }
    }
}
