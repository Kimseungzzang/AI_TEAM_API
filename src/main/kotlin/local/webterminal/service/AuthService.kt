package local.webterminal.service

import local.webterminal.dto.UserLoginRequest
import local.webterminal.dto.UserRegisterRequest
import local.webterminal.dto.UserResponse
import local.webterminal.entity.User
import local.webterminal.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(
    private val userRepository: UserRepository
) {

    @Transactional
    fun register(request: UserRegisterRequest): UserResponse {
        if (userRepository.findByUsername(request.username).isPresent) {
            throw IllegalArgumentException("Username already exists")
        }

        val newUser = User(
            username = request.username,
            password = request.password
        )
        val savedUser = userRepository.save(newUser)
        return UserResponse(savedUser.id, savedUser.username)
    }

    @Transactional(readOnly = true)
    fun login(request: UserLoginRequest): UserResponse {
        val user = userRepository.findByUsername(request.username)
            .orElseThrow { IllegalArgumentException("Invalid username or password") }

        if (request.password != user.password) {
            throw IllegalArgumentException("Invalid username or password")
        }

        return UserResponse(user.id, user.username)
    }
}
