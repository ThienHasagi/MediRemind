package com.example.employeemanagement.data.session

object AccountRole {
    const val ADMIN = "ADMIN"
    const val EMPLOYEE = "EMPLOYEE"
}

data class UserSession(
    val userId: Long,
    val employeeId: Long? = null,
    val role: String,
    val username: String,
)
