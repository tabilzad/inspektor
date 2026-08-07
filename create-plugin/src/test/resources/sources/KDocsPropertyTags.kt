package sources

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*

/**
 * Request documented with property tags instead of inline field KDocs.
 *
 * @property name Display name of the created thing.
 * @property count How many instances to create,
 *     defaults to a single instance.
 * @property inline Tag text that must lose to the inline KDoc.
 */
data class TaggedRequest(
    val name: String,
    val count: Int?,
    /** Inline KDoc wins over the class-level property tag. */
    val inline: String,
)

@GenerateOpenApi
fun Application.kdocsPropertyTagsTest() {
    routing {
        route("/v5") {
            post("/tagged") {
                val body = call.receive<TaggedRequest>()
                println(body)
            }
        }
    }
}
