package local.webterminal.controller

import local.webterminal.dto.ProjectCreateRequest
import local.webterminal.dto.ProjectResponse
import local.webterminal.service.ProjectService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/projects")
class ProjectController(private val projectService: ProjectService) {

    @GetMapping
    fun getProjectsByTeamId(@RequestParam teamId: Long): List<ProjectResponse> {
        return projectService.getProjectsByTeamId(teamId)
    }

    @PostMapping
    fun createProject(@RequestBody request: ProjectCreateRequest): ProjectResponse {
        return projectService.createProject(request)
    }
}
