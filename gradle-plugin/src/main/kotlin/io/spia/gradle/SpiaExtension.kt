package io.spia.gradle

import org.gradle.api.provider.Property

interface SpiaExtension {
    val outputPath: Property<String>
    val enumStyle: Property<String>
    val longType: Property<String>
    val apiClient: Property<String>

    // Reserved slots for upcoming tasks — uncomment and implement when the task lands:
    // var schemaOutput: String = "none"          // task 07 (Zod schema output path)
    // var openApiOutput: String = "none"         // task 12 (OpenAPI spec output path)
    // var splitByController: Boolean = false     // task 18 (split output per controller)
    // val clientOptions: ClientOptions           // task 08-10 (nested client DSL block)
    // val npmPackage: NpmPackageOptions?         // task 21 (npm publish options)
}
