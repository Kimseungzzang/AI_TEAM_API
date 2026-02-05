package local.webterminal.service

import local.webterminal.dto.MemberResponse
import local.webterminal.dto.MemberCreateRequest // Import MemberCreateRequest
import local.webterminal.entity.Member // Import Member entity
import local.webterminal.repository.MemberRepository
import local.webterminal.repository.TeamRepository // Import TeamRepository
import local.webterminal.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MemberService(
    private val memberRepository: MemberRepository,
    private val userRepository: UserRepository,
    private val teamRepository: TeamRepository // Inject TeamRepository
) {

    @Transactional(readOnly = true)
    fun getMembersByUsername(username: String): List<MemberResponse> {
        val user = userRepository.findByUsername(username)
            .orElseThrow { IllegalArgumentException("User not found with username: $username") }

        val members = mutableListOf<MemberResponse>()
        user.teams.forEach { team ->
            team.members.forEach { member ->
                members.add(
                    MemberResponse(
                        member.id,
                        member.name,
                        member.role,
                        member.config
                    )
                )
            }
        }
        return members.distinctBy { it.id }
    }

    @Transactional
    fun createMember(request: MemberCreateRequest): MemberResponse {
        val team = teamRepository.findById(request.teamId)
            .orElseThrow { IllegalArgumentException("Team not found with id: ${request.teamId}") }

        val member = Member(
            name = request.name,
            role = request.role,
            config = request.config,
            team = team
        )
        val savedMember = memberRepository.save(member)
        return MemberResponse(
            savedMember.id,
            savedMember.name,
            savedMember.role,
            savedMember.config
        )
    }
}
