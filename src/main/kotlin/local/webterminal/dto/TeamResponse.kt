package local.webterminal.dto

data class TeamResponse(
    val id: Long?,
    val name: String,
    val project: String?,
    val members: List<MemberResponse>
)
