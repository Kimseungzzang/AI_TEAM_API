package local.webterminal.controller

import local.webterminal.dto.UserLoginRequest
import local.webterminal.dto.UserRegisterRequest
import local.webterminal.dto.UserResponse
import local.webterminal.service.AuthService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(private val authService: AuthService) {

    @PostMapping("/register")
    fun register(@RequestBody request: UserRegisterRequest): ResponseEntity<UserResponse> {
        val registeredUser = authService.register(request)
        return ResponseEntity(registeredUser, HttpStatus.CREATED)
    }

    @PostMapping("/login")
    fun login(@RequestBody request: UserLoginRequest): ResponseEntity<UserResponse> {
        val authenticatedUser = authService.login(request)
        return ResponseEntity(authenticatedUser, HttpStatus.OK)
    }
}
