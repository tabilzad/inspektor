package io.github.tabilzad.ktor.config

import io.github.tabilzad.ktor.model.Info
import org.gradle.api.Action

class InfoConfigBuilder {
    var title: String? = null
    var description: String? = null
    var version: String? = null

    private var contact: Info.Contact? = null
    private var license: Info.License? = null

    fun contact(action: Action<Info.Contact>) {
        contact = Info.Contact().also(action::execute)
    }

    fun license(action: Action<Info.License>) {
        license = Info.License().also(action::execute)
    }

    fun build(): Info = Info(
        title = title,
        description = description,
        version = version,
        contact = contact,
        license = license
    )
}
