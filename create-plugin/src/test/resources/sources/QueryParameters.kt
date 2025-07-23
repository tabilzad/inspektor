package sources

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.ktor.server.application.*
import io.ktor.server.routing.*
import sources.MyStringQueries.queryParam

enum class MyQueries(val param: String) {
    ORDER_ID("orderId")
}

object MyStringQueries {
    val queryParam = "my_string_query_param"
}

@GenerateOpenApi
fun Application.queryParametersTest() {
    routing {
        route("/v1") {
            get("/order") {
                call.request.queryParameters["my_query_param"].let{
                    println(it)
                }
            }

            get("/orderWithPath/{myPathParameter}") {
                call.request.queryParameters["my_query_param2"].let{
                    println(it)
                }
            }
            get("/order2") {
                val query = call.request.queryParameters["my_query_param"]
            }

            get("/order3") {
                val query1 = call.request.queryParameters["my_query_param"]
                call.request.queryParameters["my_query_param2"].let { query2 ->
                    println(query1 + query2)
                }
            }
            get("/order3WIthPath/{pathParameter}") {
                val query1 = call.request.queryParameters["my_query_param"]
                call.request.queryParameters["my_query_param2"].let { query2 ->
                    println(query1 + query2)
                }
            }
            get("/order4") {
               val param =  call.request.rawQueryParameters["my_query_param"]
            }
            get("/order5") {
                call.request.queryParameters[MyQueries.ORDER_ID.param].let{
                    println(it)
                }
            }
            get("/order6") {
                call.request.queryParameters[queryParam].let{
                    val k = "notParam"+ "notParam"

                    println(it)
                }
            }
            get("/order7") {
                call.request.queryParameters["param_part1" + "param_part2"].let{
                    "notParam"+ "notParam"
                    println(it)
                }
            }
            get("/order8") {
                // DotQualified reference to a property inside string template
                call.request.queryParameters["param_part1_${MyStringQueries.queryParam}"].let{
                    println(it)
                }
            }
            get("/order9") {
                // Reference to a property inside string template
                call.request.queryParameters["param_part1_${queryParam}"].let{
                    println(it)
                }
            }
            get("/order10") {
                // unused param
                call.request.queryParameters["unused_param_part1_${queryParam}"]
            }
//           not supported yet
//            get("/order11") {
//                call.request.queryParameters[MyQueries.ORDER_ID.param + "param2"].let{
//                    println(it)
//                }
//            }
        }
    }
}
