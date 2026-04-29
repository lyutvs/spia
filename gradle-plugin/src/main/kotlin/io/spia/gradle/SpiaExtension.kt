package io.spia.gradle

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class ClientOptions @Inject constructor(objects: ObjectFactory) {
    val baseUrl: Property<String> = objects.property(String::class.java)
}

abstract class NpmPackageOptions @Inject constructor(objects: ObjectFactory) {
    /** Required — e.g. "@org/api-sdk". No convention; the task will error clearly if not set. */
    val name: Property<String> = objects.property(String::class.java)

    /** npm package version. Defaults to the root project version. */
    val version: Property<String> = objects.property(String::class.java)

    /** Directory (relative to project dir) where the npm package is assembled. */
    val outputDir: Property<String> = objects.property(String::class.java)
        .convention("build/npm")
}

abstract class SpiaExtension @Inject constructor(private val objects: ObjectFactory) {
    abstract val outputPath: Property<String>
    abstract val enumStyle: Property<String>
    abstract val longType: Property<String>
    abstract val apiClient: Property<String>

    abstract val schemaOutput: Property<String>

    val clientOptions: ClientOptions = objects.newInstance(ClientOptions::class.java)

    fun clientOptions(action: Action<ClientOptions>) {
        action.execute(clientOptions)
    }

    abstract val openApiOutput: Property<String>

    var splitByController: Boolean = false

    val npmPackage: NpmPackageOptions = objects.newInstance(NpmPackageOptions::class.java)

    fun npmPackage(action: Action<NpmPackageOptions>) {
        action.execute(npmPackage)
    }
}
