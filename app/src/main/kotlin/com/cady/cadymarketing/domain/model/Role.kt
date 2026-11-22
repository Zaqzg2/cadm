package com.cady.cadymarketing.domain.model

/**
 * Who is using the app, and so what each screen shows.
 * The manager is the Kady manager account; a marketer is a person the manager added to the marketing team.
 */
enum class MktRole(val code: String, val label: String) {
    MANAGER("manager", "مدير"),
    MARKETER("marketer", "مسوّق"),

    /** Signed in, but neither a Kady manager nor on the marketing team: the app opens nothing for this account. */
    NONE("none", "بلا صلاحية"),
    ;

    companion object {
        fun fromCode(code: String?): MktRole = entries.firstOrNull { it.code == code } ?: NONE
    }
}

/** One person on the marketing team (`mkt_team/{uid}`). Only marketers are listed: the manager comes from Kady. */
data class TeamMember(
    val uid: String,
    val name: String,
    val username: String,
    val role: MktRole,
    val active: Boolean,
    val updatedAt: Long,
)
