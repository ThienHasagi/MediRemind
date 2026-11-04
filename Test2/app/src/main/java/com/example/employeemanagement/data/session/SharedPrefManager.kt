package com.example.employeemanagement.data.session

import android.content.Context

class SharedPrefManager(
    context: Context,
    preferenceName: String = "employee_session",
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        preferenceName, Context.MODE_PRIVATE,
    )

    val isLoggedIn: Boolean
        get() = getSession() != null

    /** Account.id; NO_ID when logged out. */
    val userId: Long
        get() = preferences.getLong(KEY_USER_ID, NO_ID)

    /** Employee.id; NO_ID for ADMIN or when logged out. */
    val employeeId: Long
        get() = preferences.getLong(KEY_EMPLOYEE_ID, NO_ID)

    val role: String
        get() = preferences.getString(KEY_ROLE, "").orEmpty()

    val username: String
        get() = preferences.getString(KEY_USERNAME, "").orEmpty()

    fun getSession(): UserSession? {
        if (!preferences.getBoolean(KEY_LOGGED_IN, false)) return null
        val storedUserId = userId
        val storedEmployeeId = employeeId
        val storedRole = role
        val storedUsername = username
        if (storedUserId <= 0 || storedUsername.isBlank()) return null
        if (storedRole != AccountRole.ADMIN && storedRole != AccountRole.EMPLOYEE) return null
        if (storedRole == AccountRole.EMPLOYEE && storedEmployeeId <= 0) return null
        return UserSession(
            userId = storedUserId,
            employeeId = storedEmployeeId.takeIf { storedRole == AccountRole.EMPLOYEE },
            role = storedRole,
            username = storedUsername,
        )
    }

    fun saveSession(session: UserSession) {
        require(session.userId > 0 && session.username.isNotBlank())
        require(session.role == AccountRole.ADMIN || session.role == AccountRole.EMPLOYEE)
        require(session.role != AccountRole.EMPLOYEE || (session.employeeId ?: NO_ID) > 0)

        // One editor operation prevents partial sessions and removes a previous employee ID.
        preferences.edit().clear()
            .putLong(KEY_USER_ID, session.userId)
            .putString(KEY_ROLE, session.role)
            .putString(KEY_USERNAME, session.username)
            .apply {
                if (session.role == AccountRole.EMPLOYEE) {
                    putLong(KEY_EMPLOYEE_ID, requireNotNull(session.employeeId))
                }
            }
            .putBoolean(KEY_LOGGED_IN, true)
            .apply()
    }

    fun logout() {
        preferences.edit().clear().apply()
    }

    companion object {
        const val NO_ID = -1L
        private const val KEY_LOGGED_IN = "isLoggedIn"
        private const val KEY_USER_ID = "userId"
        private const val KEY_EMPLOYEE_ID = "employeeId"
        private const val KEY_ROLE = "role"
        private const val KEY_USERNAME = "username"
    }
}
