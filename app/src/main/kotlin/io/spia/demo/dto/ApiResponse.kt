package io.spia.demo.dto

data class ApiResponse<D, E>(
    val data: D? = null,
    val error: E? = null,
    val success: Boolean,
)
