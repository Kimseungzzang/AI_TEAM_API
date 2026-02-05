package local.webterminal.entity

import jakarta.persistence.*

@Entity
@Table(name = "members")
class Member(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var role: MemberRole,

    @Lob
    var config: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    var team: Team
) {
    override fun toString(): String {
        return "Member(id=$id, name='$name', role=$role)"
    }
}
