package io.spia.processor.test_support

import com.tschuchort.compiletesting.SourceFile

fun validationStubs(): List<SourceFile> = listOf(
    SourceFile.kotlin(
        "ValidationFakes.kt",
        """
        package io.spia.fake

        import jakarta.validation.constraints.Size
        import jakarta.validation.constraints.Min
        import jakarta.validation.constraints.Max
        import jakarta.validation.constraints.Pattern
        import org.springframework.web.bind.annotation.GetMapping
        import org.springframework.web.bind.annotation.PostMapping
        import org.springframework.web.bind.annotation.RequestBody
        import org.springframework.web.bind.annotation.RequestMapping
        import org.springframework.web.bind.annotation.RestController

        data class ValRequest(
            @field:Size(min = 1, max = 50) val name: String,
            @field:Min(0) @field:Max(120) val age: Int,
            @field:Pattern(regexp = "^abc${'$'}") val code: String,
        )

        data class ValWrapper<T>(
            @field:Size(min = 1, max = 30) val name: String,
            val items: List<T>,
        )

        data class ValItem(val id: String)

        @RestController
        @RequestMapping("/val")
        class ValController {
            @PostMapping("/sub")
            fun sub(@RequestBody body: ValRequest): ValRequest = body

            @GetMapping("/wrap")
            fun wrap(): ValWrapper<ValItem> = ValWrapper("n", listOf(ValItem("1")))
        }
        """.trimIndent(),
    ),
    SourceFile.kotlin(
        "JakartaValidationStubs.kt",
        """
        package jakarta.validation.constraints

        @Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY)
        annotation class Size(val min: Int = 0, val max: Int = Int.MAX_VALUE, val message: String = "")

        @Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY)
        annotation class Min(val value: Long = 0, val message: String = "")

        @Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY)
        annotation class Max(val value: Long = Long.MAX_VALUE, val message: String = "")

        @Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY)
        annotation class Pattern(val regexp: String = "", val message: String = "")

        @Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY)
        annotation class NotNull(val message: String = "")

        @Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY)
        annotation class NotBlank(val message: String = "")

        @Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY)
        annotation class Email(val message: String = "")
        """.trimIndent(),
    ),
)
