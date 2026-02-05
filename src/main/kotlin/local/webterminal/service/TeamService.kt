package local.webterminal.service

import local.webterminal.dto.MemberResponse
import local.webterminal.dto.TeamCreateRequest
import local.webterminal.dto.TeamResponse
import local.webterminal.entity.Team
import local.webterminal.entity.User
import local.webterminal.repository.TeamRepository
import local.webterminal.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TeamService(
    private val userRepository: UserRepository,
    private val teamRepository: TeamRepository
) {

    @Transactional(readOnly = true)
    fun getTeamsAndMembersByUserId(userId: Long): List<TeamResponse> {
        val user: User = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User not found with id: $userId") }

        return user.teams.map { team ->
            TeamResponse(
                team.id,
                team.name,
                team.project,
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
            project = request.project,
            user = user
        )
        val savedTeam = teamRepository.save(team)
        return TeamResponse(
            savedTeam.id,
            savedTeam.name,
            savedTeam.project,
            emptyList()
        )
    }
}
