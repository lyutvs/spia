package io.spia.processor

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import io.spia.processor.model.*

class ControllerAnalyzer(private val typeResolver: TypeResolver) {

    fun analyze(controller: KSClassDeclaration): ControllerInfo {
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
                else -> continue
            }
            return ParameterInfo(name = paramName, type = paramType, kind = kind)
        }

        return null
    }
}
