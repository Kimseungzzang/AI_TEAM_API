package local.webterminal.controller

import local.webterminal.dto.TeamCreateRequest
import local.webterminal.dto.TeamResponse
import local.webterminal.service.TeamService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/teams")
class TeamController(private val teamService: TeamService) {

    @GetMapping
    fun getTeamsByUserId(@RequestParam userId: Long): List<TeamResponse> {
        return teamService.getTeamsAndMembersByUserId(userId)
    }

    @PostMapping // New PostMapping for creating a team
    fun createTeam(@RequestBody request: TeamCreateRequest): TeamResponse {
        return teamService.createTeam(request)
    }
}
