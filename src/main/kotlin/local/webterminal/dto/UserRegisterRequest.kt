package local.webterminal.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class UserRegisterRequest(
    @field:NotBlank(message = "Username cannot be blank")
    @field:Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    val username: String,

    @field:NotBlank(message = "Password cannot be blank")
    @field:Size(min = 6, max = 100, message = "Password must be at least 6 characters long")
    val password: String
)
