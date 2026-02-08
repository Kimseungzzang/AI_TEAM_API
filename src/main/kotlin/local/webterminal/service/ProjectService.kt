package local.webterminal.service

import local.webterminal.dto.ProjectCreateRequest
import local.webterminal.dto.ProjectResponse
import local.webterminal.entity.Project
import local.webterminal.repository.ProjectRepository
import local.webterminal.repository.TeamRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.File

@Service
class ProjectService(
    private val projectRepository: ProjectRepository,
    private val teamRepository: TeamRepository
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(ProjectService::class.java)
    }

    @Value("\${workspace.base-path:./workspaces}")
    private lateinit var workspaceBasePath: String

    @Transactional
    fun createProject(request: ProjectCreateRequest): ProjectResponse {
        val team = teamRepository.findById(request.teamId)
            .orElseThrow { IllegalArgumentException("Team not found with id: ${request.teamId}") }

        val safeTeamName = team.name.replace(Regex("[^a-zA-Z0-9가-힣._-]"), "_")
        val safeProjectName = request.name.replace(Regex("[^a-zA-Z0-9가-힣._-]"), "_")

        // 팀/projects 폴더 경로
        val projectsDir = File(workspaceBasePath, "$safeTeamName/projects")
        projectsDir.mkdirs()

        // 워크스페이스 파일 생성
        val workspaceFile = File(projectsDir, "${safeProjectName}_workSpace.md")
        val workSpaceUrl = workspaceFile.canonicalPath

        workspaceFile.writeText("""
            |# ${request.name} Workspace
            |
            |## Team: ${team.name}
            |
            |---
            |
            |## Notes
            |
        """.trimMargin())

        LOGGER.info("Created workspace file: {}", workSpaceUrl)

        // 프로젝트 엔티티 저장
        val project = Project(
            name = request.name,
            workSpaceUrl = workSpaceUrl,
            config = workSpaceUrl,
            team = team
        )
        val savedProject = projectRepository.save(project)

        // team.md 파일에 프로젝트 정보 추가
        updateTeamMdFile(safeTeamName, request.name, workSpaceUrl)

        return ProjectResponse(
            savedProject.id,
            savedProject.name,
            savedProject.workSpaceUrl,
            savedProject.config
        )
    }

    @Transactional(readOnly = true)
    fun getProjectsByTeamId(teamId: Long): List<ProjectResponse> {
        return projectRepository.findByTeamId(teamId).map { project ->
            ProjectResponse(
                project.id,
                project.name,
                project.workSpaceUrl,
                project.config
            )
        }
    }

    private fun updateTeamMdFile(safeTeamName: String, projectName: String, workSpaceUrl: String) {
        // 팀 폴더 내의 팀 md 파일
        val teamDir = File(workspaceBasePath, safeTeamName)
        val teamMdFile = File(teamDir, "${safeTeamName}.md")

        if (!teamMdFile.exists()) {
            // team.md 파일이 없으면 생성
            teamMdFile.writeText("""
                |# ${safeTeamName} Team
                |
                |## Rules
                |
                |## Projects
                |
                |- ${projectName}: ${workSpaceUrl}
                |
            """.trimMargin())
        } else {
            // 기존 파일에 프로젝트 추가
            val content = teamMdFile.readText()
            val projectLine = "- ${projectName}: ${workSpaceUrl}\n"

            if (content.contains("## Projects")) {
                // Projects 섹션 찾아서 그 다음 줄에 추가
                val lines = content.lines().toMutableList()
                val projectsIndex = lines.indexOfFirst { it.trim() == "## Projects" }
                if (projectsIndex >= 0) {
                    lines.add(projectsIndex + 1, "")
                    lines.add(projectsIndex + 2, projectLine.trim())
                    teamMdFile.writeText(lines.joinToString("\n"))
                } else {
                    teamMdFile.appendText("\n${projectLine}")
                }
            } else {
                // Projects 섹션이 없으면 끝에 추가
                teamMdFile.appendText("\n## Projects\n\n${projectLine}")
            }
        }

        LOGGER.info("Updated team.md file: {}", teamMdFile.absolutePath)
    }
}
