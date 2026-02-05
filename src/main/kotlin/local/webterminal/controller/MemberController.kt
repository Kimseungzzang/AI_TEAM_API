package local.webterminal.controller

import local.webterminal.dto.MemberResponse
import local.webterminal.dto.MemberCreateRequest // Import MemberCreateRequest
import local.webterminal.service.MemberService
import org.springframework.web.bind.annotation.*
import jakarta.validation.Valid // Import for validation

@RestController
@RequestMapping("/api/members")
class MemberController(private val memberService: MemberService) {

    @GetMapping
    fun getMembersByUsername(@RequestParam username: String): List<MemberResponse> {
        return memberService.getMembersByUsername(username)
    }

    @PostMapping // New PostMapping for creating a member
    fun createMember(@Valid @RequestBody request: MemberCreateRequest): MemberResponse {
        return memberService.createMember(request)
    }
}
