package com.example.weightsmart.core.auth

/** Role → Permissions mapping. */
object Rbac {
    private val map: Map<Role, Set<Permission>> = mapOf(
        Role.USER to setOf(
            Permission.ViewSelfData,
            Permission.EditSelfData,
            Permission.DeleteSelfData
        ),
        Role.ADMIN to Permission::class.sealedSubclasses
            .mapNotNull { it.objectInstance }
            .toSet()
    )

    /** True if the given role is granted the permission. */
    fun grants(role: Role, perm: Permission): Boolean =
        map[role]?.contains(perm) == true
}
