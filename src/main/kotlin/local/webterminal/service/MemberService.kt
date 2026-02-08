package local.webterminal.service

import local.webterminal.dto.MemberResponse
import local.webterminal.dto.MemberCreateRequest
import local.webterminal.entity.Member
import local.webterminal.repository.MemberRepository
import local.webterminal.repository.TeamRepository
import local.webterminal.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.File

@Service
class MemberService(
    private val memberRepository: MemberRepository,
    private val userRepository: UserRepository,
    private val teamRepository: TeamRepository
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MemberService::class.java)
    }

    @Value("\${workspace.base-path:./workspaces}")
    private lateinit var workspaceBasePath: String

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

        val safeMemberName = request.name.replace(Regex("[^a-zA-Z0-9가-힣._-]"), "_")
        val safeTeamName = team.name.replace(Regex("[^a-zA-Z0-9가-힣._-]"), "_")

        // 팀/members 폴더 경로
        val membersDir = File(workspaceBasePath, "$safeTeamName/members")
        membersDir.mkdirs()

        // 멤버 config 파일 생성
        val mdFile = File(membersDir, "$safeMemberName.md")
        val mdPath = mdFile.canonicalPath

        if (!mdFile.exists()) {
            val initial = StringBuilder()
                .append("# ").append(request.name).append('\n')
                .append('\n')
                .append('\n')
            mdFile.writeText(initial.toString())
        }

        val baseConfig = request.config?.trim().orEmpty()
        if (baseConfig.isNotBlank()) {
            val extra = StringBuilder()
                .append("## config\n")
                .append(baseConfig)
                .append('\n')
            mdFile.appendText(extra.toString())
        }

        LOGGER.info("Created member config file: {}", mdPath)

        val member = Member(
            name = request.name,
            role = request.role,
            config = mdPath,
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

    @Transactional
    fun deleteMember(memberId: Long) {
        val member = memberRepository.findById(memberId)
            .orElseThrow { IllegalArgumentException("Member not found with id: $memberId") }
        val configPath = member.config

        memberRepository.delete(member)

        if (!configPath.isNullOrBlank()) {
            val file = File(configPath)
            if (file.exists()) {
                val deleted = file.delete()
                LOGGER.info("Deleted member config file: {}, success={}", configPath, deleted)
            }
        }
    }
}
