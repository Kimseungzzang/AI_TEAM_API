package local.webterminal.controller

import local.webterminal.dto.MemberResponse
import local.webterminal.dto.MemberCreateRequest // Import MemberCreateRequest
import local.webterminal.service.MemberService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/members")
class MemberController(private val memberService: MemberService) {

    @GetMapping
    fun getMembersByUsername(@RequestParam username: String): List<MemberResponse> {
        return memberService.getMembersByUsername(username)
    }

    @PostMapping // New PostMapping for creating a member
    fun createMember(@RequestBody request: MemberCreateRequest): MemberResponse {
        return memberService.createMember(request)
    }

    @DeleteMapping("/{memberId}")
    fun deleteMember(@PathVariable memberId: Long) {
        memberService.deleteMember(memberId)
    }
}
