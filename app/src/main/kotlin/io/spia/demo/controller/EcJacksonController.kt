package io.spia.demo.controller

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class JacksonUserRequest(
    @JsonProperty("user_name")
    @JsonAlias(value = ["name", "userName"])
    val userName: String,

    @JsonInclude(JsonInclude.Include.NON_NULL)
    val bio: String? = null,
)

data class JacksonUserResponse(
    @JsonProperty("user_name")
    val userName: String,

    @JsonInclude(JsonInclude.Include.NON_NULL)
    val bio: String? = null,
)

@RestController
@RequestMapping("/api/ec-jackson")
class EcJacksonController {

    @PostMapping("/users")
    fun createUser(@RequestBody request: JacksonUserRequest): JacksonUserResponse {
        return JacksonUserResponse(
            userName = request.userName,
            bio = request.bio,
        )
    }

    @GetMapping("/users/sample")
    fun sampleUser(): JacksonUserResponse {
        return JacksonUserResponse(userName = "demo")
    }
}
