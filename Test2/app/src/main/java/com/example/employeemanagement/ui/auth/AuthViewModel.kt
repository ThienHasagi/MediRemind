package com.example.employeemanagement.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.employeemanagement.data.local.AppDatabase
import com.example.employeemanagement.data.session.AccountRole
import com.example.employeemanagement.data.session.SharedPrefManager
import com.example.employeemanagement.data.session.UserSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthError {
    EMPTY_FIELDS,
    INVALID_CREDENTIALS,
    EMPLOYEE_NOT_FOUND,
    UNSUPPORTED_ROLE,
    DATABASE_ERROR,
}

data class AuthUiState(
    val isLoading: Boolean = false,
    val session: UserSession? = null,
    val error: AuthError? = null,
)

class AuthViewModel(
    private val database: AppDatabase,
    private val sharedPrefManager: SharedPrefManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState(session = sharedPrefManager.getSession()))
    val uiState = _uiState.asStateFlow()
    private var loginJob: Job? = null

    fun login(username: String, password: String) {
        if (_uiState.value.isLoading || _uiState.value.session != null) return
        val normalizedUsername = username.trim()
        if (normalizedUsername.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(error = AuthError.EMPTY_FIELDS) }
            return
        }
        _uiState.update { it.copy(isLoading = true, error = null) }
        loginJob = viewModelScope.launch {
            try {
                // Read the account and its employee together for a consistent result.
                val result = database.withTransaction {
                    val account = database.accountDao().login(normalizedUsername, password)
                        ?: return@withTransaction LoginResult.Failure(AuthError.INVALID_CREDENTIALS)
                    val employeeId = when (account.role) {
                        AccountRole.ADMIN -> null
                        AccountRole.EMPLOYEE -> database.employeeDao().getByAccountId(account.id)?.id
                            ?: return@withTransaction LoginResult.Failure(AuthError.EMPLOYEE_NOT_FOUND)
                        else -> return@withTransaction LoginResult.Failure(AuthError.UNSUPPORTED_ROLE)
                    }
                    LoginResult.Success(UserSession(
                        userId = account.id,
                        employeeId = employeeId,
                        role = account.role,
                        username = account.username,
                    ))
                }
                when (result) {
                    is LoginResult.Success -> {
                        sharedPrefManager.saveSession(result.session)
                        _uiState.value = AuthUiState(session = result.session)
                    }
                    is LoginResult.Failure -> _uiState.value = AuthUiState(error = result.error)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                _uiState.value = AuthUiState(error = AuthError.DATABASE_ERROR)
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun logout() {
        loginJob?.cancel()
        sharedPrefManager.logout()
        _uiState.value = AuthUiState()
    }

    private sealed interface LoginResult {
        data class Success(val session: UserSession) : LoginResult
        data class Failure(val error: AuthError) : LoginResult
    }

    class Factory(
        private val database: AppDatabase,
        private val sharedPrefManager: SharedPrefManager,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AuthViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return AuthViewModel(database, sharedPrefManager) as T
        }
    }
}
