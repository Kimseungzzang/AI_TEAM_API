package local.webterminal.entity

import jakarta.persistence.*

@Entity
@Table(name = "projects")
class Project(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false)
    var name: String,

    @Column(name = "work_space_url")
    var workSpaceUrl: String? = null,

    @Column(name = "config")
    var config: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    var team: Team
) {
    override fun toString(): String {
        return "Project(id=$id, name='$name', workSpaceUrl=$workSpaceUrl)"
    }
}
