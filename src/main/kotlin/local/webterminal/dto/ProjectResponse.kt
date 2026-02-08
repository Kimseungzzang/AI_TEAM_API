package local.webterminal.dto

data class ProjectResponse(
    val id: Long?,
    val name: String,
    val workSpaceUrl: String?,
    val config: String?
)
