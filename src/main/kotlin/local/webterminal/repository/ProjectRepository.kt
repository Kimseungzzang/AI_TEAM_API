package local.webterminal.repository

import local.webterminal.entity.Project
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ProjectRepository : JpaRepository<Project, Long> {
    fun findByTeamId(teamId: Long): List<Project>
}
