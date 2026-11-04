package com.example.mediremind.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mediremind.data.local.UserPreferences
import com.example.mediremind.data.remote.FirestoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import com.example.mediremind.data.userMessage

data class RoleSelectionUiState(
    val isLoading: Boolean = false,
    val generatedPairCode: String = "",
    val errorMessage: String? = null
)

class RoleSelectionViewModel(
    private val userPreferences: UserPreferences,
    private val firestoreRepository: FirestoreRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(RoleSelectionUiState())
    val uiState: StateFlow<RoleSelectionUiState> = _uiState.asStateFlow()
    fun resetState() { _uiState.value = RoleSelectionUiState() }

    fun selectElderly() {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.value = RoleSelectionUiState(isLoading = true)
            val pairCode = UserPreferences.generatePairCode()
            runCatching {
                withTimeout(20_000) { firestoreRepository.createElderlyProfile(pairCode) }
                userPreferences.saveElderlyProfile(pairCode)
            }.onSuccess {
                _uiState.value = RoleSelectionUiState(generatedPairCode = pairCode)
            }.onFailure { error ->
                _uiState.value = RoleSelectionUiState(
                    isLoading = false,
                    errorMessage = error.userMessage()
                )
            }
        }
    }

    fun connectCaregiver(pairCode: String, caregiverPhone: String) {
        if (_uiState.value.isLoading) return
        val normalizedPairCode = pairCode.trim().uppercase()
        val normalizedPhone = caregiverPhone.trim()

        if (normalizedPairCode.isBlank() || normalizedPhone.isBlank()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Vui lòng nhập đủ mã ghép đôi và số điện thoại."
            )
            return
        }
        if (!normalizedPhone.replace(" ", "").matches(Regex("^\\+?[0-9]{8,15}$"))) {
            _uiState.value = _uiState.value.copy(errorMessage = "Số điện thoại phải có 8–15 chữ số, có thể bắt đầu bằng +.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            runCatching {
                val connected = withTimeout(20_000) { firestoreRepository.connectCaregiver(
                    pairCode = normalizedPairCode,
                    caregiverPhone = normalizedPhone
                ) }
                if (!connected) {
                    error("Mã ghép đôi không tồn tại.")
                }
                userPreferences.saveCaregiverProfile(
                    pairCode = normalizedPairCode,
                    emergencyPhone = normalizedPhone
                )
                _uiState.value = RoleSelectionUiState()
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = if (error.message == "Mã ghép đôi không tồn tại.") error.message else error.userMessage()
                )
            }
        }
    }

    companion object {
        fun factory(
            userPreferences: UserPreferences,
            firestoreRepository: FirestoreRepository
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(RoleSelectionViewModel::class.java)) {
                        return RoleSelectionViewModel(
                            userPreferences = userPreferences,
                            firestoreRepository = firestoreRepository
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}
