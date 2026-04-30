package io.spia.e2e.dto

data class Page<T>(
    val items: List<T>,
    val page: Int,
    val total: Int,
)
