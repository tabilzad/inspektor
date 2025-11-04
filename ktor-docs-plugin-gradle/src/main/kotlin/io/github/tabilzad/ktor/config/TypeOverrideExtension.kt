package io.github.tabilzad.ktor.config

import io.github.tabilzad.ktor.model.TypeOverride
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

open class TypeOverrideExtension @Inject constructor(objects: ObjectFactory) {

    /**
     * A container of TypeOverride objects, keyed by the FQN.
     * Consumers can declare overrides either via `typeOverride("fqName") { ... }`
     * or inside the `typeOverrides { }` block.
     */
    private val typeOverrides: NamedDomainObjectContainer<TypeOverride> =
        objects.domainObjectContainer(TypeOverride::class.java) { fqName ->
            // when someone creates an entry by name, we set fqName automatically
            TypeOverride(fqName = fqName)
        }

    fun typeOverrides(action: Action<NamedDomainObjectContainer<TypeOverride>>) =
        action.execute(typeOverrides)

    /**
     * Shortcut for single override entries:
     *   openApi {
     *     typeOverride("java.time.Instant") { ... }
     *   }
     */
    fun typeOverride(fqName: String, action: Action<TypeOverride>) {
        typeOverrides.create(fqName, action)
    }

    internal fun getOverrides() = typeOverrides
}
