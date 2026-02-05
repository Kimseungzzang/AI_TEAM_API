package local.webterminal.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import local.webterminal.entity.MemberRole

data class MemberCreateRequest(
    @field:NotBlank(message = "Member name cannot be blank")
    val name: String,
    @field:NotNull(message = "Member role cannot be null")
    val role: MemberRole,
    val config: String?,
    @field:NotNull(message = "Team ID cannot be null")
    val teamId: Long
)
