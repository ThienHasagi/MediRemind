package com.example.mediremind.ui.viewmodel

data class HomeUiState(
    val morningDoses: List<DoseUiItem> = emptyList(),
    val afternoonDoses: List<DoseUiItem> = emptyList(),
    val eveningDoses: List<DoseUiItem> = emptyList(),
    val asNeededDoses: List<DoseUiItem> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val isOffline: Boolean = false
) {
    val hasAnyDose: Boolean
        get() = morningDoses.isNotEmpty() ||
            afternoonDoses.isNotEmpty() ||
            eveningDoses.isNotEmpty() ||
            asNeededDoses.isNotEmpty()
}

data class DoseUiItem(
    val doseId: Long,
    val medicineId: Long,
    val scheduleId: Long,
    val medicineName: String,
    val dosage: String,
    val instruction: String,
    val reminderTime: String,
    val isTaken: Boolean,
    val takenTimestamp: Long?,
    val repeatType: String = "DAILY",
    val notes: String = ""
) {
    val isAsNeeded: Boolean
        get() = repeatType == "AS_NEEDED" || reminderTime == "Khi cần"
}
