package io.spia.demo.controller

import io.spia.demo.dto.CreateUserRequest
import io.spia.demo.dto.UserProfileDto
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/users")
class UserController {

    /** Retrieve a user profile. */
    @GetMapping("/{id}")
    fun getUserProfile(@PathVariable id: Long): UserProfileDto {
        return UserProfileDto(
            id = id,
            name = "Demo User",
            email = "demo@example.com",
            bio = "Hello!",
        )
    }

    /** Create a new user. */
    @PostMapping
    fun createUser(@RequestBody request: CreateUserRequest): UserProfileDto {
        return UserProfileDto(
            id = 1L,
            name = request.name,
            email = request.email,
            bio = null,
        )
    }
}
