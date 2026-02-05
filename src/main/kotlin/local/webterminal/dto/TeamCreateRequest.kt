package local.webterminal.dto

data class TeamCreateRequest(
    val name: String,
    val project: String?,
    val userId: Long
)
