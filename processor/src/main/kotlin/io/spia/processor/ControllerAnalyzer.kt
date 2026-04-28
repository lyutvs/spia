package io.spia.processor

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import io.spia.processor.model.*

class ControllerAnalyzer(private val typeResolver: TypeResolver, private val logger: KSPLogger? = null) {

    /**
     * Collects global error responses from all `@ControllerAdvice` / `@RestControllerAdvice`
     * classes supplied. These are merged into every endpoint's `errorResponses` map.
     */
    fun collectAdviceErrors(adviceClasses: List<KSClassDeclaration>): Map<Int, TypeInfo> {
        val result = mutableMapOf<Int, TypeInfo>()
        for (advice in adviceClasses) {
            for (fn in advice.declarations.filterIsInstance<KSFunctionDeclaration>()) {
                val (status, type) = extractExceptionHandlerInfo(fn) ?: continue
                result[status] = type
            }
        }
        return result
    }

    fun analyze(controller: KSClassDeclaration, globalErrors: Map<Int, TypeInfo> = emptyMap()): ControllerInfo {
        // Controllers are always plain classes (ClassKind.CLASS) — this also accepts Java
        // @RestController classes which KSP surfaces with the same ClassKind.
        require(controller.classKind == ClassKind.CLASS) {
            "ControllerAnalyzer.analyze() expects a CLASS, got ${controller.classKind} for ${controller.qualifiedName?.asString()}"
        }

        // Collect local @ExceptionHandler methods inside this controller
        val localErrors = mutableMapOf<Int, TypeInfo>()
        for (fn in controller.declarations.filterIsInstance<KSFunctionDeclaration>()) {
            val (status, type) = extractExceptionHandlerInfo(fn) ?: continue
            localErrors[status] = type
        }

        // Merge: local errors override global ones for this controller
        val mergedErrors: Map<Int, TypeInfo> = globalErrors + localErrors

        val basePath = extractBasePath(controller)
        val endpoints = controller.declarations
            .filterIsInstance<KSFunctionDeclaration>()
            .mapNotNull { analyzeEndpoint(it, mergedErrors) }
            .toList()

        return ControllerInfo(
            name = controller.simpleName.asString(),
            basePath = basePath,
            endpoints = endpoints,
        )
    }

    /**
     * If `fn` is annotated with `@ExceptionHandler` and `@ResponseStatus`, returns the
     * (httpStatusCode, returnTypeInfo) pair. Returns null otherwise.
     */
    private fun extractExceptionHandlerInfo(fn: KSFunctionDeclaration): Pair<Int, TypeInfo>? {
        val hasExceptionHandler = fn.annotations.any {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == SpringAnnotations.EXCEPTION_HANDLER
        }
        if (!hasExceptionHandler) return null

        val statusCode = extractResponseStatusCode(fn) ?: return null
        val returnType = fn.returnType?.resolve()?.let { typeResolver.resolve(it) }
            ?: TypeInfo.Primitive("void")
        return statusCode to returnType
    }

    /**
     * Reads the HTTP status code from `@ResponseStatus` on `fn`.
     * Supports both `code` / `value` attributes which hold an `HttpStatus` enum reference.
     * The enum ordinal in Spring HttpStatus maps to the actual HTTP code, but we read the
     * annotation value name (e.g. "NOT_FOUND") and convert known names to codes.
     */
    private fun extractResponseStatusCode(fn: KSFunctionDeclaration): Int? {
        val annotation = fn.annotations.firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == SpringAnnotations.RESPONSE_STATUS
        } ?: return null

        // The `value` or `code` argument holds a KSType referencing the HttpStatus enum entry.
        val arg = annotation.arguments.firstOrNull { it.name?.asString() == "value" || it.name?.asString() == "code" }
        val enumName = when (val v = arg?.value) {
            is com.google.devtools.ksp.symbol.KSType -> v.declaration.simpleName.asString()
            else -> v?.toString()?.substringAfterLast('.')?.substringAfterLast('$')
        } ?: return null

        return HTTP_STATUS_MAP[enumName]
    }

    private fun extractBasePath(controller: KSClassDeclaration): String {
        val requestMapping = controller.annotations.firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == SpringAnnotations.REQUEST_MAPPING
        } ?: return ""

        return extractPathFromAnnotation(requestMapping)
    }

    private fun analyzeEndpoint(function: KSFunctionDeclaration, errorResponses: Map<Int, TypeInfo> = emptyMap()): EndpointInfo? {
        val (httpMethod, annotation) = findHttpMethodAnnotation(function) ?: return null

        val path = extractPathFromAnnotation(annotation)
        val parameters = function.parameters.mapNotNull { analyzeParameter(it) }

        val returnType = function.returnType?.resolve()?.let { typeResolver.resolve(it) }
            ?: TypeInfo.Primitive("void")

        val jsdoc = function.docString?.trim()?.takeIf { it.isNotBlank() }

        return EndpointInfo(
            functionName = function.simpleName.asString(),
            httpMethod = httpMethod,
            path = path,
            parameters = parameters,
            returnType = returnType,
            jsdoc = jsdoc,
            errorResponses = errorResponses,
        )
    }

    private fun findHttpMethodAnnotation(function: KSFunctionDeclaration): Pair<HttpMethod, KSAnnotation>? {
        for (annotation in function.annotations) {
            val fqn = annotation.annotationType.resolve().declaration.qualifiedName?.asString() ?: continue
            val method = SpringAnnotations.HTTP_METHOD_ANNOTATIONS[fqn] ?: continue
            return HttpMethod.valueOf(method) to annotation
        }
        return null
    }

    private fun extractPathFromAnnotation(annotation: KSAnnotation): String {
        val valueArg = annotation.arguments.firstOrNull {
            it.name?.asString() == "value" || it.name?.asString() == "path"
        }
        val value = valueArg?.value
        val path = when (value) {
            is List<*> -> value.firstOrNull()?.toString() ?: ""
            is String -> value
            else -> {
                // Try the first unnamed argument
                annotation.arguments.firstOrNull()?.value?.let {
                    when (it) {
                        is List<*> -> it.firstOrNull()?.toString() ?: ""
                        is String -> it
                        else -> ""
                    }
                } ?: ""
            }
        }
        return path.removeSurrounding("\"")
    }

    private fun analyzeParameter(param: KSValueParameter): ParameterInfo? {
        val paramName = param.name?.asString() ?: return null
        val paramType = param.type.resolve().let { typeResolver.resolve(it) }

        for (annotation in param.annotations) {
            val fqn = annotation.annotationType.resolve().declaration.qualifiedName?.asString() ?: continue
            val kind = when (fqn) {
                SpringAnnotations.PATH_VARIABLE -> ParameterKind.PATH
                SpringAnnotations.REQUEST_BODY -> ParameterKind.BODY
                SpringAnnotations.REQUEST_PARAM -> ParameterKind.QUERY
                SpringAnnotations.REQUEST_HEADER -> ParameterKind.HEADER
                SpringAnnotations.REQUEST_PART -> ParameterKind.MULTIPART
                SpringAnnotations.MODEL_ATTRIBUTE -> ParameterKind.MODEL_ATTRIBUTE
                SpringAnnotations.COOKIE_VALUE -> ParameterKind.COOKIE
                SpringAnnotations.REQUEST_ATTRIBUTE -> ParameterKind.REQUEST_ATTRIBUTE
                SpringAnnotations.MATRIX_VARIABLE -> ParameterKind.MATRIX_VARIABLE
                else -> continue
            }

            if (kind == ParameterKind.QUERY) {
                val defaultValue = extractRequestParamDefaultValue(annotation)
                val requiredAttr = extractRequestParamRequired(annotation)
                // Spring semantics: a defaultValue implicitly makes the parameter optional.
                val required = if (defaultValue != null) false else requiredAttr
                return ParameterInfo(
                    name = paramName,
                    type = paramType,
                    kind = kind,
                    required = required,
                    defaultValue = defaultValue,
                )
            }

            if (kind == ParameterKind.PATH) {
                val bindingName = annotation.arguments
                    .firstOrNull { it.name?.asString() == "value" }?.value as? String
                    ?: annotation.arguments.firstOrNull { it.name?.asString() == "name" }?.value as? String
                    ?: paramName
                val effectiveName = bindingName.takeIf { it.isNotBlank() } ?: paramName
                return ParameterInfo(name = effectiveName, type = paramType, kind = kind)
            }

            if (kind == ParameterKind.HEADER) {
                val headerName = annotation.arguments
                    .firstOrNull { it.name?.asString() == "value" }?.value as? String
                    ?: annotation.arguments.firstOrNull { it.name?.asString() == "name" }?.value as? String
                val effectiveHeaderName = headerName?.takeIf { it.isNotBlank() } ?: paramName
                return ParameterInfo(
                    name = paramName,
                    type = paramType,
                    kind = kind,
                    headerName = effectiveHeaderName,
                )
            }

            if (kind == ParameterKind.REQUEST_ATTRIBUTE) {
                // @RequestAttribute is server-side only — skip from the generated TS signature.
                logger?.warn(
                    "@RequestAttribute parameter '$paramName' is server-side only and will be excluded from the generated TypeScript SDK.",
                    param
                )
                return null
            }

            if (kind == ParameterKind.COOKIE) {
                val cookieName = annotation.arguments
                    .firstOrNull { it.name?.asString() == "value" }?.value as? String
                    ?: annotation.arguments.firstOrNull { it.name?.asString() == "name" }?.value as? String
                val effectiveCookieName = cookieName?.takeIf { it.isNotBlank() } ?: paramName
                return ParameterInfo(
                    name = paramName,
                    type = paramType,
                    kind = kind,
                    headerName = effectiveCookieName,
                )
            }

            return ParameterInfo(name = paramName, type = paramType, kind = kind)
        }

        return null
    }

    private fun extractRequestParamRequired(annotation: KSAnnotation): Boolean {
        val arg = annotation.arguments.firstOrNull { it.name?.asString() == "required" } ?: return true
        return arg.value as? Boolean ?: true
    }

    private fun extractRequestParamDefaultValue(annotation: KSAnnotation): String? {
        val arg = annotation.arguments.firstOrNull { it.name?.asString() == "defaultValue" } ?: return null
        val value = arg.value as? String ?: return null
        if (value.isEmpty()) return null
        // Real user-supplied defaults are short and printable. Spring's
        // ValueConstants.DEFAULT_NONE sentinel is ~20+ chars of whitespace/control codes
        // and never produces a useful letter/digit. If no ASCII alphanumeric character is
        // present, treat as sentinel — version-independent.
        val hasAlphaNum = value.any { c -> c.code in 0x30..0x39 || c.code in 0x41..0x5A || c.code in 0x61..0x7A }
        if (!hasAlphaNum) return null
        return value
    }

    companion object {
        /** Maps Spring HttpStatus enum names to their integer codes. */
        private val HTTP_STATUS_MAP: Map<String, Int> = mapOf(
            "CONTINUE" to 100,
            "SWITCHING_PROTOCOLS" to 101,
            "OK" to 200,
            "CREATED" to 201,
            "ACCEPTED" to 202,
            "NO_CONTENT" to 204,
            "MOVED_PERMANENTLY" to 301,
            "FOUND" to 302,
            "NOT_MODIFIED" to 304,
            "BAD_REQUEST" to 400,
            "UNAUTHORIZED" to 401,
            "FORBIDDEN" to 403,
            "NOT_FOUND" to 404,
            "METHOD_NOT_ALLOWED" to 405,
            "CONFLICT" to 409,
            "GONE" to 410,
            "UNPROCESSABLE_ENTITY" to 422,
            "TOO_MANY_REQUESTS" to 429,
            "INTERNAL_SERVER_ERROR" to 500,
            "NOT_IMPLEMENTED" to 501,
            "BAD_GATEWAY" to 502,
            "SERVICE_UNAVAILABLE" to 503,
        )
    }
}
