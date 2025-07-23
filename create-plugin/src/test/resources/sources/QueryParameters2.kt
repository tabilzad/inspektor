package sources

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import sources.Constants.EMPLOYEE_ID
import sources.Constants.EMPLOYEE_ID2
import sources.Constants.EMPLOYEE_ID3
import sources.requests.SimpleRequest

object Constants {

    const val EMPLOYEE_ID = "employeeId"
    const val EMPLOYEE_ID2 = "employeeId2"
    const val EMPLOYEE_ID3 = "employeeId3"
}

@GenerateOpenApi
fun Application.queryParametersTest2() {
    routing {
        route("/v1") {
            get("/constantResolution") {
                call.request.queryParameters[EMPLOYEE_ID].let {
                    println(it)
                }
            }
            get("/fullConstantResolution") {
                call.request.queryParameters[Constants.EMPLOYEE_ID2].let {
                    println(it)
                }
            }

            get("/multipleQueryParams") {
                val param1 = call.request.queryParameters[EMPLOYEE_ID]
                val param2 = call.request.queryParameters[Constants.EMPLOYEE_ID2]
                call.request.queryParameters[EMPLOYEE_ID3].let { param3 ->


                }
            }

            get("/multipleNestedQueryParams") {
                call.request.queryParameters[EMPLOYEE_ID].onPresentQ {

                }.onAbsentOrBlankQ {
                    call.request.queryParameters[EMPLOYEE_ID2]
                    println()
                }
            }

            get("/unusedParams") {
                // unused param
                call.request.queryParameters[EMPLOYEE_ID]
            }

            post("/queryWithNestedReceive") {
                call.receive<SimpleRequest>().let {
                    call.request.queryParameters[EMPLOYEE_ID].let { param ->

                    }
                }
            }
        }
    }
}

inline fun String?.onAbsentOrBlankQ(handle: () -> Unit): String? =
    if (this.isNullOrBlank()) this.also { handle() } else this

inline fun String?.onPresentQ(handle: (String) -> Unit): String? =
    if (this.isNullOrBlank()) this else this.also(handle)
