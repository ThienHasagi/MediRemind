package com.example.mediremind.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mediremind.data.remote.FirestoreRepository
import com.example.mediremind.data.remote.RemoteDoseLog
import com.example.mediremind.data.repository.MedicineRepository
import com.example.mediremind.data.repository.TodayDose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.example.mediremind.data.userMessage
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repository: MedicineRepository,
    private val firestoreRepository: FirestoreRepository? = null
) : ViewModel() {
    private val day = MutableStateFlow(LocalDate.now().toEpochDay())
    private val todayEpochDay get() = day.value
    private val error = MutableStateFlow<String?>(null)
    private var syncPairCode = ""
    private var uploadJob: Job? = null
    init {
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                val today = LocalDate.now().toEpochDay()
                if (day.value != today) { optimisticTakenIds.value = emptySet(); day.value = today }
            }
        }
    }
    private val optimisticTakenIds = MutableStateFlow<Set<Long>>(emptySet())
    private var currentDoses: Map<Long, DoseUiItem> = emptyMap()
    private var remoteMedicineSyncJob: Job? = null

    val uiState: StateFlow<HomeUiState> = combine(
        day.flatMapLatest { repository.observeTodayDoses(it) },
        optimisticTakenIds,
        error
    ) { doses, optimisticIds, message ->
            val uiItems = doses.map { it.toUiItem(optimisticIds) }
                .sortedBy { it.reminderTime }

            currentDoses = uiItems.associateBy { it.doseId }
            val regularItems = uiItems.filterNot { it.isAsNeeded }
            val asNeededItems = uiItems.filter { it.isAsNeeded }

            HomeUiState(
                morningDoses = regularItems.filter { it.localTime()?.let { time -> time in morningRange } == true },
                afternoonDoses = regularItems.filter { it.localTime()?.let { time -> time in afternoonRange } == true },
                eveningDoses = regularItems.filter { it.localTime() == null || it.localTime()?.let { time -> time !in morningRange && time !in afternoonRange } == true },
                asNeededDoses = asNeededItems,
                isLoading = false,
                errorMessage = message
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState()
        )

    fun onTakeMedicine(doseId: Long, pairCode: String = "") {
        val dose = currentDoses[doseId] ?: return
        if (dose.isTaken) return
        if (doseId in optimisticTakenIds.value) return
        uploadJob?.cancel()

        optimisticTakenIds.value = optimisticTakenIds.value + doseId
        viewModelScope.launch {
            try {
            val takenAt = System.currentTimeMillis()
            repository.markDoseTaken(
                medicineId = dose.medicineId,
                scheduleId = dose.scheduleId,
                dateEpochDay = todayEpochDay,
                takenTimestamp = takenAt
            )
            syncDoseToFirestore(
                dose = dose.copy(isTaken = true, takenTimestamp = takenAt),
                pairCode = pairCode
            )
            } catch (failure: Exception) { error.value = failure.userMessage() }
            finally { optimisticTakenIds.value = optimisticTakenIds.value - doseId }
        }
    }

    fun syncVisibleDosesToFirestore(pairCode: String) {
        if (pairCode.isBlank()) return

        val doses = currentDoses.values.toList()
        if (uploadJob?.isActive == true) return
        uploadJob = viewModelScope.launch {
            doses.forEach { dose ->
                syncDoseToFirestore(dose = currentDoses[dose.doseId] ?: dose, pairCode = pairCode)
            }
            try {
                withTimeout(20_000) {
                    repository.savedHistory(pairCode).forEach { log ->
                        firestoreRepository?.syncDoseLog(pairCode, log)
                    }
                }
            } catch (failure: Exception) {
                error.value = failure.userMessage() + " Lịch sử trên máy vẫn được giữ; bấm Thử lại để đồng bộ."
            }
        }
    }

    fun startRemoteMedicineSync(pairCode: String) {
        if (pairCode.isBlank() || firestoreRepository == null) return
        if (remoteMedicineSyncJob?.isActive == true && syncPairCode == pairCode) return
        remoteMedicineSyncJob?.cancel()
        syncPairCode = pairCode

        remoteMedicineSyncJob = viewModelScope.launch(Dispatchers.IO) {
            try {
            firestoreRepository.observeMedicines(pairCode, protectLocalData = true).collect { remoteMedicines ->
                repository.syncRemoteMedicines(remoteMedicines)
            }
            } catch (failure: Exception) {
                error.value = failure.userMessage() + " Lịch đã lưu trên máy vẫn được giữ."
            }
        }
    }

    fun stopRemoteMedicineSync() {
        remoteMedicineSyncJob?.cancel()
        uploadJob?.cancel()
        syncPairCode = ""
    }

    fun retry(pairCode: String) {
        error.value = null
        remoteMedicineSyncJob?.cancel()
        remoteMedicineSyncJob = null
        startRemoteMedicineSync(pairCode)
        syncVisibleDosesToFirestore(pairCode)
    }

    fun seedSampleData() {
        viewModelScope.launch {
            repository.seedSampleDataIfNeeded()
        }
    }

    private fun TodayDose.toUiItem(optimisticTakenIds: Set<Long>): DoseUiItem {
        val isOptimisticTaken = doseId in optimisticTakenIds
        return DoseUiItem(
            doseId = doseId,
            medicineId = medicineId,
            scheduleId = scheduleId,
            medicineName = medicineName,
            dosage = dosage,
            instruction = instruction,
            reminderTime = reminderTime,
            isTaken = isTaken || isOptimisticTaken,
            takenTimestamp = takenTimestamp ?: if (isOptimisticTaken) System.currentTimeMillis() else null,
            repeatType = repeatType,
            notes = notes
        )
    }

    private fun DoseUiItem.localTime(): LocalTime? = runCatching { LocalTime.parse(reminderTime) }.getOrNull()

    private suspend fun syncDoseToFirestore(dose: DoseUiItem, pairCode: String) {
        if (pairCode.isBlank()) return
        val effectiveTimestamp = dose.takenTimestamp ?: System.currentTimeMillis()
        try { withTimeout(15_000) { firestoreRepository?.syncDoseLog(
            pairCode = pairCode,
            RemoteDoseLog(
                pairCode = pairCode,
                scheduleId = dose.scheduleId,
                medicineName = dose.medicineName,
                dateEpochDay = todayEpochDay,
                isTaken = dose.isTaken,
                takenTime = if (dose.isTaken) effectiveTimestamp.formatTakenTime() else "",
                timestamp = effectiveTimestamp,
                medicineId = dose.medicineId,
                dosage = dose.dosage,
                instruction = dose.instruction,
                reminderTime = dose.reminderTime,
                takenTimestamp = dose.takenTimestamp
            )
        ) } } catch (failure: Exception) {
            error.value = failure.userMessage() + " Nhật ký trên máy vẫn được giữ; bấm Thử lại để đồng bộ."
        }
    }

    private val morningRange = LocalTime.of(6, 0)..LocalTime.of(11, 59)
    private val afternoonRange = LocalTime.of(12, 0)..LocalTime.of(17, 59)
    private val eveningRange = LocalTime.of(18, 0)..LocalTime.of(23, 59)

    companion object {
        fun factory(
            repository: MedicineRepository,
            firestoreRepository: FirestoreRepository? = null
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                        return HomeViewModel(
                            repository = repository,
                            firestoreRepository = firestoreRepository
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}

private fun Long.formatTakenTime(): String {
    val formatter = DateTimeFormatter
        .ofPattern("HH:mm", Locale("vi", "VN"))
        .withZone(ZoneId.systemDefault())
    return formatter.format(Instant.ofEpochMilli(this))
}
