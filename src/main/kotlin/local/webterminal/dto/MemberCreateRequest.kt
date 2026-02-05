package local.webterminal.dto

import local.webterminal.entity.MemberRole

data class MemberCreateRequest(
    val name: String,
    val role: MemberRole,
    val config: String?,
    val teamId: Long
)
