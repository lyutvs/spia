package io.spia.processor

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import io.spia.processor.model.*

class ControllerAnalyzer(private val typeResolver: TypeResolver) {

    fun analyze(controller: KSClassDeclaration): ControllerInfo {
        // Controllers are always plain classes (ClassKind.CLASS) — this also accepts Java
        // @RestController classes which KSP surfaces with the same ClassKind.
        require(controller.classKind == ClassKind.CLASS) {
            "ControllerAnalyzer.analyze() expects a CLASS, got ${controller.classKind} for ${controller.qualifiedName?.asString()}"
        }

        val basePath = extractBasePath(controller)
        val endpoints = controller.declarations
            .filterIsInstance<KSFunctionDeclaration>()
            .mapNotNull { analyzeEndpoint(it) }
            .toList()

        return ControllerInfo(
            name = controller.simpleName.asString(),
            basePath = basePath,
            endpoints = endpoints,
        )
    }

    private fun extractBasePath(controller: KSClassDeclaration): String {
        val requestMapping = controller.annotations.firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == SpringAnnotations.REQUEST_MAPPING
        } ?: return ""

        return extractPathFromAnnotation(requestMapping)
    }

    private fun analyzeEndpoint(function: KSFunctionDeclaration): EndpointInfo? {
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
}
