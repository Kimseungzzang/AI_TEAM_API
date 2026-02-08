package local.webterminal.service

import local.webterminal.dto.MemberResponse
import local.webterminal.dto.ProjectResponse
import local.webterminal.dto.TeamCreateRequest
import local.webterminal.dto.TeamResponse
import local.webterminal.entity.Team
import local.webterminal.entity.User
import local.webterminal.repository.TeamRepository
import local.webterminal.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.File

@Service
class TeamService(
    private val userRepository: UserRepository,
    private val teamRepository: TeamRepository
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(TeamService::class.java)
    }

    @Value("\${workspace.base-path:./workspaces}")
    private lateinit var workspaceBasePath: String

    @Transactional(readOnly = true)
    fun getTeamsAndMembersByUserId(userId: Long): List<TeamResponse> {
        val user: User = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User not found with id: $userId") }

        return user.teams.map { team ->
            TeamResponse(
                team.id,
                team.name,
                team.config,
                team.projects.map { project ->
                    ProjectResponse(
                        project.id,
                        project.name,
                        project.workSpaceUrl,
                        project.config
                    )
                },
                team.members.map { member ->
                    MemberResponse(
                        member.id,
                        member.name,
                        member.role,
                        member.config
                    )
                }
            )
        }
    }

    @Transactional
    fun createTeam(request: TeamCreateRequest): TeamResponse {
        val user: User = userRepository.findById(request.userId)
            .orElseThrow { IllegalArgumentException("User not found with id: ${request.userId}") }

        val team = Team(
            name = request.name,
            user = user
        )

        // 팀 폴더 구조 생성
        val configPath = createTeamFolderStructure(request.name)
        team.config = configPath
        val savedTeam = teamRepository.save(team)

        return TeamResponse(
            savedTeam.id,
            savedTeam.name,
            savedTeam.config,
            emptyList(),
            emptyList()
        )
    }

    private fun createTeamFolderStructure(teamName: String): String {
        val safeTeamName = teamName.replace(Regex("[^a-zA-Z0-9가-힣._-]"), "_")

        // 팀 폴더
        val teamDir = File(workspaceBasePath, safeTeamName)
        teamDir.mkdirs()

        // members 폴더
        val membersDir = File(teamDir, "members")
        membersDir.mkdirs()

        // projects 폴더
        val projectsDir = File(teamDir, "projects")
        projectsDir.mkdirs()

        // 팀 md 파일 생성
        val teamMdFile = File(teamDir, "${safeTeamName}.md")
        if (!teamMdFile.exists()) {
            teamMdFile.writeText("""
                |# ${teamName} Team
                |
                |## Rules
                |
                |## Projects
                |
            """.trimMargin())
        }

        val canonicalPath = teamMdFile.canonicalPath
        LOGGER.info("Created team folder structure: {}", canonicalPath)
        return canonicalPath
    }

    /**
     * 팀 폴더 경로 반환
     */
    fun getTeamFolderPath(teamName: String): String {
        val safeTeamName = teamName.replace(Regex("[^a-zA-Z0-9가-힣._-]"), "_")
        return File(workspaceBasePath, safeTeamName).absolutePath
    }
}
