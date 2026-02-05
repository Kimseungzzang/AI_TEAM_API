package local.webterminal.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class TeamCreateRequest(
    @field:NotBlank(message = "Team name cannot be blank")
    val name: String,
    val project: String?,
    @field:NotNull(message = "User ID cannot be null")
    val userId: Long
)
