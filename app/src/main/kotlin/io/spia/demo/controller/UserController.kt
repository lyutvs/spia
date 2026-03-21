package io.spia.demo.controller

import io.spia.demo.dto.ApiResponse
import io.spia.demo.dto.CreateUserRequest
import io.spia.demo.dto.Page
import io.spia.demo.dto.UpdateUserRequest
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
            address = io.spia.demo.dto.Address(
                street = "1 Infinite Loop",
                city = "Cupertino",
                zipCode = "95014",
            ),
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

    /** Update an existing user. */
    @PutMapping("/{id}")
    fun updateUser(
        @PathVariable id: Long,
        @RequestBody request: UpdateUserRequest,
    ): UserProfileDto {
        return UserProfileDto(
            id = id,
            name = "Demo User",
            email = request.email ?: "demo@example.com",
            bio = request.bio,
        )
    }

    /** Delete a user. */
    @DeleteMapping("/{id}")
    fun deleteUser(@PathVariable id: Long) {
        // no-op stub
    }

    /** List users with simple pagination metadata. */
    @GetMapping("/list")
    fun listUsers(): Page<UserProfileDto> {
        return Page(
            content = emptyList(),
            totalElements = 0L,
            page = 0,
            size = 20,
        )
    }

    /** Return a user wrapped in a multi-parameter generic response envelope. */
    @GetMapping("/{id}/wrapped")
    fun wrappedUser(@PathVariable id: Long): ApiResponse<UserProfileDto, String> {
        return ApiResponse(
            data = UserProfileDto(
                id = id,
                name = "Demo User",
                email = "demo@example.com",
                bio = null,
            ),
            error = null,
            success = true,
        )
    }
}
