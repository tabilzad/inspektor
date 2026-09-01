package sources

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.github.tabilzad.ktor.annotations.KtorSchema
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

@Deprecated("Use ModernAmount instead")
@KtorSchema(description = "legacy dollar amount", type = "number")
data class LegacyAmount(val value: Int)

/** Events emitted by the legacy pipeline. */
@Deprecated("Use NewEvent instead")
sealed class LegacyEvent {
    data class Created(val id: String) : LegacyEvent()

    /** Kept only for replaying old streams. */
    @Deprecated("Use Created instead")
    data class Renamed(val old: String) : LegacyEvent()
}

data class EventWrapper(
    val amount: LegacyAmount,
    val event: LegacyEvent
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
            post("/events") {
                call.receive<EventWrapper>()
            }
        }
    }
}
