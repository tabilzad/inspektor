package sources

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*

/** A legacy payload kept for old clients. */
@Deprecated("Use CurrentPayload instead")
data class LegacyPayload(
    val id: String,
    val amount: Int
)

@Deprecated("Use the status endpoint instead")
enum class LegacyStatus {
    ACTIVE, INACTIVE
}

data class CurrentPayload(
    /** The legacy identifier. */
    @Deprecated("Use id instead")
    val legacyId: String?,
    @Deprecated("")
    val oldFlag: Boolean?,
    val id: String,
    val status: LegacyStatus?
)

@GenerateOpenApi
fun Application.deprecatedSchemaModule() {
    routing {
        route("/v1") {
            post("/legacy") {
                call.receive<LegacyPayload>()
            }
            post("/current") {
                call.receive<CurrentPayload>()
            }
        }
    }
}
