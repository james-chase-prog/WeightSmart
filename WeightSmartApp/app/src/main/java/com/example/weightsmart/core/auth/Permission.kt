package com.example.weightsmart.core.auth

/** Fine-grained permissions checked at runtime. */
sealed class Permission(val code: String) {
    // Self data
    data object ViewSelfData : Permission("view_self_data")
    data object EditSelfData : Permission("edit_self_data")
    data object DeleteSelfData : Permission("delete_self_data")

    // Others' data (e.g., coach/admin)
    data object ViewOthersData : Permission("view_others_data")
    data object EditOthersData : Permission("edit_others_data")

    // User management (admin)
    data object ManageUsers : Permission("manage_users")
}
