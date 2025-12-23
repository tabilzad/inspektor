# Ktor Resources

Integration with Ktor's type-safe routing plugin.

## Overview

[Ktor Resources](https://ktor.io/docs/type-safe-routing.html) provides type-safe routing using Kotlin classes. InspeKtor
automatically documents routes defined with Resources.

## Setup

Add the Ktor Resources plugin to your project:

```kotlin title="build.gradle.kts"
dependencies {
    implementation("io.ktor:ktor-server-resources:$ktor_version")
}
```

Install the plugin in your application:

```kotlin
fun Application.module() {
    install(Resources)

    routing {
        // Resource-based routes
    }
}
```

## Basic Resources

Define your resources as classes with the `@Resource` annotation:

```kotlin
import io.ktor.resources.*

@Resource("/users")
class Users {
    @Resource("{id}")
    class Id(val parent: Users = Users(), val id: Long)

    @Resource("search")
    class Search(val parent: Users = Users(), val query: String)
}
```

Use them in your routes:

```kotlin
@GenerateOpenApi
fun Application.module() {
    install(Resources)

    routing {
        // GET /users
        get<Users> {
            responds<List<User>>(HttpStatusCode.OK)
        }

        // GET /users/{id}
        get<Users.Id> { params ->
            responds<User>(HttpStatusCode.OK)
            val userId = params.id
        }

        // GET /users/search?query=...
        get<Users.Search> { params ->
            responds<List<User>>(HttpStatusCode.OK)
            val searchQuery = params.query
        }
    }
}
```

## Generated OpenAPI

The above generates:

```yaml
paths:
  /users:
    get:
      responses:
        "200":
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/User'

  /users/{id}:
    get:
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: integer
            format: int64
      responses:
        "200":
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/User'

  /users/search:
    get:
      parameters:
        - name: query
          in: query
          required: true
          schema:
            type: string
      responses:
        "200":
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/User'
```

## Query Parameters

Resource properties become query parameters:

```kotlin
@Resource("/products")
class Products(
    val category: String? = null,
    val minPrice: Double? = null,
    val maxPrice: Double? = null,
    val sort: String = "name",
    val page: Int = 1,
    val size: Int = 20
)
```

Usage:

```kotlin
get<Products> { params ->
    responds<Page<Product>>(HttpStatusCode.OK)

    val products = productService.find(
        category = params.category,
        minPrice = params.minPrice,
        maxPrice = params.maxPrice,
        sort = params.sort,
        page = params.page,
        size = params.size
    )
}
```

Request: `GET /products?category=electronics&minPrice=100&sort=price&page=2`

## Path Parameters

Nested resources with parameters:

```kotlin
@Resource("/organizations")
class Organizations {
    @Resource("{orgId}")
    class ById(val parent: Organizations = Organizations(), val orgId: String) {
        @Resource("teams")
        class Teams(val parent: ById) {
            @Resource("{teamId}")
            class ById(val parent: Teams, val teamId: String) {
                @Resource("members")
                class Members(val parent: ById)
            }
        }
    }
}
```

Routes:

```kotlin
// GET /organizations/{orgId}/teams/{teamId}/members
get<Organizations.ById.Teams.ById.Members> { params ->
    responds<List<Member>>(HttpStatusCode.OK)

    val orgId = params.parent.parent.parent.orgId
    val teamId = params.parent.teamId
}
```

## HTTP Methods

Resources work with all HTTP methods:

```kotlin
@Resource("/articles")
class Articles {
    @Resource("{id}")
    class Id(val parent: Articles = Articles(), val id: Long)
}

routing {
    // GET /articles
    get<Articles> { responds<List<Article>>(HttpStatusCode.OK) }

    // POST /articles
    post<Articles> {
        responds<Article>(HttpStatusCode.Created)
        val request = call.receive<CreateArticleRequest>()
    }

    // GET /articles/{id}
    get<Articles.Id> { responds<Article>(HttpStatusCode.OK) }

    // PUT /articles/{id}
    put<Articles.Id> {
        responds<Article>(HttpStatusCode.OK)
        val request = call.receive<UpdateArticleRequest>()
    }

    // DELETE /articles/{id}
    delete<Articles.Id> { respondsNothing(HttpStatusCode.NoContent) }
}
```

## Descriptions with Resources

Use `@KtorDescription` with resource routes:

```kotlin
@KtorDescription(summary = "List all users")
get<Users> {
    responds<List<User>>(HttpStatusCode.OK)
}

@KtorDescription(
    summary = "Get user by ID",
    description = "Retrieves a user by their unique identifier"
)
get<Users.Id> { params ->
    responds<User>(HttpStatusCode.OK)
    responds<ErrorResponse>(HttpStatusCode.NotFound)
}
```

## Tags with Resources

Apply tags using the `@Tag` annotation:

```kotlin
@Tag(["Users"])
fun Route.userResourceRoutes() {
    get<Users> { }
    post<Users> { }
    get<Users.Id> { }
    put<Users.Id> { }
    delete<Users.Id> { }
}
```

## Optional Parameters

Use nullable types or default values for optional parameters:

```kotlin
@Resource("/events")
class Events(
    val startDate: String? = null,     // Optional query param
    val endDate: String? = null,       // Optional query param
    val limit: Int = 100               // Optional with default
)
```

## Complete Example

```kotlin
// Resource definitions
@Resource("/api/v1/users")
class UsersResource {
    @Resource("{userId}")
    class ById(
        val parent: UsersResource = UsersResource(),
        val userId: Long
    ) {
        @Resource("posts")
        class Posts(val parent: ById) {
            @Resource("{postId}")
            class ById(val parent: Posts, val postId: Long)
        }
    }

    @Resource("search")
    class Search(
        val parent: UsersResource = UsersResource(),
        val query: String,
        val active: Boolean? = null,
        val page: Int = 1,
        val size: Int = 20
    )
}

// Route definitions
@GenerateOpenApi
fun Application.module() {
    install(Resources)

    routing {
        @Tag(["Users"])
        userRoutes()
    }
}

fun Route.userRoutes() {
    @KtorDescription(summary = "List all users")
    get<UsersResource> {
        responds<List<User>>(HttpStatusCode.OK)
    }

    @KtorDescription(summary = "Search users")
    get<UsersResource.Search> { params ->
        responds<Page<User>>(HttpStatusCode.OK)
    }

    @KtorDescription(summary = "Get user by ID")
    get<UsersResource.ById> { params ->
        responds<User>(HttpStatusCode.OK)
        responds<ErrorResponse>(HttpStatusCode.NotFound)
    }

    @KtorDescription(summary = "Get user's posts")
    get<UsersResource.ById.Posts> { params ->
        responds<List<Post>>(HttpStatusCode.OK)
    }

    @KtorDescription(summary = "Get specific post")
    get<UsersResource.ById.Posts.ById> { params ->
        responds<Post>(HttpStatusCode.OK)
    }
}
```

## Best Practices

1. **Organize resources by domain** - Group related resources together
2. **Use meaningful class names** - `Users.ById` is clearer than `Users.Id`
3. **Provide defaults** for optional parameters
4. **Document query parameters** in KtorDescription
5. **Keep nesting reasonable** - Deeply nested resources are hard to use
