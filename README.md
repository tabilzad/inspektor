<p align="center">
  <img width="200" height="200" src="https://github.com/user-attachments/assets/da646150-24f3-43af-8b8f-188848f284a5" />
</p>

# InspeKtor
### Open API (Swagger) generator for Ktor
[![Test and Publish to SonarType](https://github.com/tabilzad/inspektor/actions/workflows/gradle-publish.yml/badge.svg)](https://github.com/tabilzad/inspektor/actions/workflows/gradle-publish.yml)

This plugin implements a plug and play solution for generating OpenAPI (Swagger) specification for your Ktor server on any platform with minimal effort - no need to modify your existing code, no special DSL wrappers etc.
Just annotate your route(s) definitions with `@GenerateOpenApi` and `openapi.yaml` will be generated at build time.

Take a look at the [Sample Project](https://github.com/tabilzad/ktor-inspektor-example) to get started. 
## How to apply the plugin

```groovy
plugins {
    id("io.github.tabilzad.inspektor") version "0.8.4-alpha"
}

swagger {
    documentation {
        generateRequestSchemas = true
        hideTransientFields = true
        hidePrivateAndInternalFields = true
        deriveFieldRequirementFromTypeNullability = true
        info {
            title = "Ktor Server Title"
            description = "Ktor Server Description"
            version = "1.0"
            contact {
                name = "Inspektor"
                url = "https://github.com/tabilzad/inspektor"
            }
        }
    }

    pluginOptions {
        format = "yaml" // or json
    }
}
```

## Supported Features

| Feature                      | isSupported | type      |
|------------------------------|-------------|-----------|
| Path/Endpoint definitions    | ✅           | Automatic |
| Ktor Resources               | ✅           | Automatic |
| Request Schemas              | ✅           | Automatic |
| Response Schemas             | ✅           | Explicit  |
| Endpoint/Scheme Descriptions | ✅           | Explicit  |
| Endpoint Tagging             | ✅           | Explicit  |


## Plugin Configuration

### Documentation options

| Option                         | Default Value                             | Explanation                                                                                 |
|--------------------------------|-------------------------------------------|---------------------------------------------------------------------------------------------|
| `info.title`                   | `"Open API Specification"`                | Title for the API specification that is generated                                           |
| `info.description`             | `"Generated using Ktor Docs Plugin"`      | A brief description for the generated API specification                                     |
| `info.version`                 | `"1.0.0"`                                 | Specifies the version for the generated API specification                                   |
| `generateRequestSchemas`       | `true`                                    | Determines if request body schemas should <br/>be automatically resolved and included       |
| `hideTransientFields`          | `true`                                    | Controls whether fields marked with `@Transient` <br/> are omitted in schema outputs        |
| `hidePrivateAndInternalFields` | `true`                                    | Opts to exclude fields with `private` or `internal` modifiers from schema outputs           |
| `deriveFieldRequirementFromTypeNullability` | `true`                       | Automatically derive object fields' requirement from its type nullability                   |
| `servers`                      | []                                        | List of server URLs to be included in the spec  (ex: `listOf("http://localhost:8080")`      |

### Plugin options
| Option                         | Default Value                                | Explanation                                                                                 |
|--------------------------------|----------------------------------------------|---------------------------------------------------------------------------------------------|
| `enabled`                      | `true`                                       | Enable/Disables the plugin                                                                  |
| `saveInBuild`                  | `true`                                       | Decides if the generated specification file should <br/> be saved in the `build/` directory |
| `format`                       | `yaml`                                       | The chosen format for the OpenAPI specification <br/>(options: json/yaml)                   |
| `filePath`                     | `$modulePath/build/resources/main/openapi/`  | The designated absolute path for saving <br/> the generated specification file              |

## How to use the plugin

### Generating endpoint specifications

Annotate the specific route definitions you want the OpenAPI specification to be generated for.

```kotlin

@GenerateOpenApi
fun Route.ordersRouting() {
    route("/v1") {
        post("/order1") {
            /*...*/
        }
    }
}

```

You could also annotate the entire `Application` module with multiple/nested route definitions. The plugin will
recursively visit each `Route`. extension and generate its documentation.

```kotlin

@GenerateOpenApi
fun Application.ordersModule() {
    routing {
        routeOne()
        routeTwo()
    }
}

fun Route.routeOne() {
    route("/v1") { /*...*/ }
}

fun Route.routeTwo() {
    route("/v2") { /*...*/ }
    routeThree()
}

```

### Endpoint and field descriptions

Describe endpoints or schema fields.

```kotlin
@KtorSchema("this is my request")
data class RequestSample(
    @KtorField("this is a string")
    val string: String,
    val int: Int,
    val double: Double,
    @KtorField(description = "this is instant", typr = "string", format = "date-time")
    val date: Instant
)

@GenerateOpenApi
fun Route.ordersRouting() {
    route("/v1") {
        @KtorDescription(
            summary = "Create Order",
            description = "This endpoint will create an order",
        )
        post("/create") {
            call.receive<RequestSample>()
        }

        route("/orders") {
            @KtorDescription(
                summary = "All Orders",
                description = "This endpoint will return a list of all orders"
            )
            get { /*...*/ }
        }
    }
}
```

### Responses
Defining response schemas and their corresponding HTTP status codes are done via `@KtorResponds` annotation on an endpoint or `responds<T>(HttpStatusCode)` inline extension on a `RouteContext`. The latter is the preferred way since it is capable of defining types with generics. On the annotation `kotlin.Nothing` is treated specially and will result in empty response body for statutes like `204 NO_CONTENT`, alternatively use `respondsNothing` extension.

```kotlin
@GenerateOpenApi
fun Route.ordersRouting() {
    route("/v1") {
        @KtorResponds(
               [
                   ResponseEntry("200", Order::class, description = "Created order"),
                   ResponseEntry("204", Nothing::class),
                   ResponseEntry("400", ErrorResponseSample::class, description = "Invalid order payload")
               ]
        )
        post("/create") { /*...*/ }
        @KtorResponds([ResponseEntry("200", Order::class, isCollection=true, description = "Get all orders")])
        get("/orders") { /*...*/ }
    }
}
```
```kotlin
@GenerateOpenApi
fun Route.ordersRouting() {
    route("/v1") {
        post("/create") {
          // Creates order
          responds<Order>(HttpStatusCode.Ok)
          respondsNothing(HttpStatusCode.NoContent)
          // Invalid order payload
          responds<ErrorResponseSample>(HttpStatusCode.BadRequest)
          /*...*/
        }
        get("/orders"){
           // Get all orders
           responds<List<Order>>(HttpStatusCode.NoContent)
           /*...*/
        }
    }
}
```

### Tagging

Using tags enables the categorization of individual endpoints into designated groups. 
Tags specified at the parent route will propogate down to all endpoints contained within it.

```kotlin
@Tag(["Orders"])
fun Route.ordersRouting() {
    route("/v1") {
        post("/create") { /*...*/ }
        get("/orders") { /*...*/ }
    }
    route("/v2") {
        post("/create") { /*...*/ }
        get("/orders") { /*...*/ }
    }
}
```
On the other hand, if the tags are specified with `@KtorDescription` or `@Tag` annotation on an endpoint, they are associated exclusively with that particular endpoint.

```kotlin
@GenerateOpenApi
fun Route.ordersRouting() {
    route("/v1") {
        @KtorDescription(tags = ["Order Operations"])
        post("/order") { /*...*/ }
        @Tag(["Cart Operations"])
        get("/cart") { /*...*/ }
    }
}
```

## Planned Features

* Automatic Response resolution
* Support for polymorphic types with discriminators 
* Option for an automatic tag resolution from module/route function declaration
* Tag descriptions

## Sample Specification

![sample](https://github.com/tabilzad/inspektor/assets/16094286/6d0b0a6a-5925-4f52-ad23-11b1c44b43a1)




