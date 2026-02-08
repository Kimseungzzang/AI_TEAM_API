package local.webterminal.dto

data class TeamResponse(
    val id: Long?,
    val name: String,
    val config: String?,
    val projects: List<ProjectResponse>,
    val members: List<MemberResponse>
)
