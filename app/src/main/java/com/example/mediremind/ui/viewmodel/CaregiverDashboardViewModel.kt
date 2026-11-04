package com.example.mediremind.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mediremind.data.remote.FirestoreMedicineModel
import com.example.mediremind.data.remote.FirestoreRepository
import com.example.mediremind.data.remote.RemoteDoseLog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import com.example.mediremind.data.userMessage
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withTimeout

data class CaregiverDashboardUiState(
    val pairCode: String = "",
    val selectedDateEpochDay: Long = LocalDate.now().toEpochDay(),
    val doseLogs: List<RemoteDoseLog> = emptyList(),
    val medicines: List<FirestoreMedicineModel> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class CaregiverDashboardViewModel(
    private val firestoreRepository: FirestoreRepository
) : ViewModel() {
    private val pairCode = MutableStateFlow("")
    private val selectedDateEpochDay = MutableStateFlow(LocalDate.now().toEpochDay())
    private val refresh = MutableStateFlow(0)
    private val actionError = MutableStateFlow<String?>(null)
    private val saving = MutableStateFlow(false)
    private var draftId = java.util.UUID.randomUUID().toString()

    private val content = combine(
        pairCode,
        selectedDateEpochDay,
        refresh
    ) { code, epochDay, _ -> code to epochDay }
        .flatMapLatest { (code, epochDay) ->
            if (code.isBlank()) {
                flowOf(CaregiverDashboardUiState(isLoading = false))
            } else {
                combine(
                    firestoreRepository.observeTodayDoseLogs(code, epochDay),
                    firestoreRepository.observeMedicines(code)
                ) { logs, medicines ->
                    CaregiverDashboardUiState(
                        pairCode = code,
                        selectedDateEpochDay = epochDay,
                        doseLogs = logs,
                        medicines = medicines,
                        isLoading = false
                    )
                }.onStart { emit(CaregiverDashboardUiState(pairCode = code, selectedDateEpochDay = epochDay)) }
                    .catch { emit(CaregiverDashboardUiState(pairCode = code, selectedDateEpochDay = epochDay,
                        isLoading = false, errorMessage = it.userMessage())) }
            }
        }
    val uiState = combine(content, actionError, saving) { state, error, busy ->
        state.copy(errorMessage = error ?: state.errorMessage, isSaving = busy)
    }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CaregiverDashboardUiState()
        )

    fun setPairCode(code: String) {
        if (pairCode.value != code) { actionError.value = null; draftId = java.util.UUID.randomUUID().toString() }
        pairCode.value = code
    }

    fun retry() { actionError.value = null; refresh.value++ }
    fun newDraft() { actionError.value = null; draftId = java.util.UUID.randomUUID().toString() }

    fun setSelectedDate(epochDay: Long) {
        selectedDateEpochDay.value = minOf(epochDay, LocalDate.now().toEpochDay())
    }

    fun addMedicine(
        name: String,
        dosage: String,
        instruction: String,
        scheduleTimes: List<String>,
        repeatType: String = "DAILY",
        startDateEpochDay: Long = LocalDate.now().toEpochDay(),
        notes: String = "",
        onSaved: () -> Unit
    ) {
        val code = pairCode.value
        if (saving.value) return
        if (code.isBlank()) { actionError.value = "Chưa ghép đôi. Vui lòng ghép đôi lại."; return }
        saving.value = true
        actionError.value = null

        viewModelScope.launch {
            runCatching {
                withTimeout(20_000) {
                firestoreRepository.addOrUpdateMedicine(
                    id = draftId,
                    pairCode = code,
                    name = name,
                    dosage = dosage,
                    instruction = instruction,
                    scheduleTimes = scheduleTimes,
                    repeatType = repeatType,
                    startDateEpochDay = startDateEpochDay,
                    notes = notes
                )
                }
            }.onSuccess {
                draftId = java.util.UUID.randomUUID().toString()
                onSaved()
            }.onFailure { actionError.value = it.userMessage() }
            saving.value = false
        }
    }

    fun deleteMedicine(medicineId: String) {
        if (saving.value) return
        saving.value = true
        actionError.value = null
        viewModelScope.launch {
            runCatching {
                withTimeout(20_000) { firestoreRepository.deleteMedicine(medicineId) }
            }.onFailure { actionError.value = it.userMessage() + " Bạn có thể chọn xóa lại thuốc này." }
            saving.value = false
        }
    }

    companion object {
        fun factory(firestoreRepository: FirestoreRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(CaregiverDashboardViewModel::class.java)) {
                        return CaregiverDashboardViewModel(firestoreRepository) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}
