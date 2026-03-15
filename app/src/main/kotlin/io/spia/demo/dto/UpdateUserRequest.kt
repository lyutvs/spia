package io.spia.demo.dto

data class UpdateUserRequest(
    val email: String? = null,
    val bio: String? = null,
)
