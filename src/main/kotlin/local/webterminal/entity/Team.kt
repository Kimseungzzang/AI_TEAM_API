package local.webterminal.entity

import jakarta.persistence.*

@Entity
@Table(name = "teams")
class Team(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false)
    var name: String,

    @Column(name = "config")
    var config: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @OneToMany(mappedBy = "team", cascade = [CascadeType.ALL], orphanRemoval = true)
    var members: MutableList<Member> = mutableListOf(),

    @OneToMany(mappedBy = "team", cascade = [CascadeType.ALL], orphanRemoval = true)
    var projects: MutableList<Project> = mutableListOf()
) {
    override fun toString(): String {
        return "Team(id=$id, name='$name')"
    }
}
