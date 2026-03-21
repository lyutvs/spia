package io.spia.demo.dto

data class Page<T>(
    val content: List<T>,
    val totalElements: Long,
    val page: Int,
    val size: Int,
)
