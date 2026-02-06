package local.webterminal.service

import local.webterminal.dto.MemberResponse
import local.webterminal.dto.MemberCreateRequest // Import MemberCreateRequest
import local.webterminal.entity.Member // Import Member entity
import local.webterminal.repository.MemberRepository
import local.webterminal.repository.TeamRepository // Import TeamRepository
import local.webterminal.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Files
import java.nio.file.Path

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

        val safeName = request.name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val baseDir = Path.of(System.getProperty("user.dir"), "member-configs")
        Files.createDirectories(baseDir)
        val mdPath = baseDir.resolve("$safeName.md").toAbsolutePath().normalize()
        if (!Files.exists(mdPath)) {
            val initial = StringBuilder()
                .append("# ").append(request.name).append('\n')
                .append('\n')
                .append("## rule\n")
                .append("- 첫 시작일 시 인사하기\n")
                .append("- 대화 내용을 이 md 파일에 기록하기\n")
                .append('\n')
            Files.writeString(mdPath, initial.toString())
        }

        val baseConfig = request.config?.trim().orEmpty()
        if (baseConfig.isNotBlank()) {
            val extra = StringBuilder()
                .append("## config\n")
                .append(baseConfig)
                .append('\n')
            Files.writeString(mdPath, extra.toString(), java.nio.file.StandardOpenOption.APPEND)
        }

        val member = Member(
            name = request.name,
            role = request.role,
            config = mdPath.toString(),
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
