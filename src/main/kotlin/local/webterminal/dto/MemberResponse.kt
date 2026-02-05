package local.webterminal.dto

import local.webterminal.entity.MemberRole

data class MemberResponse(
    val id: Long?,
    val name: String,
    val role: MemberRole,
    val config: String?
)
