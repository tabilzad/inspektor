package sources.precompiled

import io.ktor.http.*
import kotlinx.serialization.Serializable
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.response.*
import io.ktor.server.routing.*

object MyConsts {
    const val BASE_PATH = "/base"

    object ObjectType {
        const val ORDERS = "orders"
    }

    object Route {
        const val ALL = "queryAll"
        const val LATEST = "queryLatest"
    }
}

@Serializable
@Resource(MyConsts.BASE_PATH)
class OrdersResources {
    @Serializable
    @Resource(MyConsts.ObjectType.ORDERS)
    class Orders(val parent: OrdersResources = OrdersResources()) {
        @Serializable
        @Resource(MyConsts.Route.ALL)
        class QueryAll(val parent: OrdersResources.Orders = OrdersResources.Orders())

        @Serializable
        @Resource(MyConsts.Route.LATEST)
        class QueryLatest(val parent: OrdersResources.Orders = OrdersResources.Orders())
    }
}