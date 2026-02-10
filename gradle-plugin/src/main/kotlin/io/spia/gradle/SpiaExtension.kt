package io.spia.gradle

import org.gradle.api.provider.Property

interface SpiaExtension {
    val outputPath: Property<String>
    val enumStyle: Property<String>
    val longType: Property<String>
    val apiClient: Property<String>
}
