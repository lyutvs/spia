package io.spia.gradle

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class ClientOptions @Inject constructor(objects: ObjectFactory) {
    val baseUrl: Property<String> = objects.property(String::class.java)
}

abstract class SpiaExtension @Inject constructor(private val objects: ObjectFactory) {
    abstract val outputPath: Property<String>
    abstract val enumStyle: Property<String>
    abstract val longType: Property<String>
    abstract val apiClient: Property<String>

    val clientOptions: ClientOptions = objects.newInstance(ClientOptions::class.java)

    fun clientOptions(action: Action<ClientOptions>) {
        action.execute(clientOptions)
    }

    // Reserved slots for upcoming tasks — uncomment and implement when the task lands:
    // var schemaOutput: String = "none"          // task 07 (Zod schema output path)
    // var openApiOutput: String = "none"         // task 12 (OpenAPI spec output path)
    // var splitByController: Boolean = false     // task 18 (split output per controller)
    // val npmPackage: NpmPackageOptions?         // task 21 (npm publish options)
}
