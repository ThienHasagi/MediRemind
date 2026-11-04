package com.example.mediremind.data.remote

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ElderlyProfileModel(
    val pairCode: String = "",
    val emergencyPhone: String = "",
    val createdAt: Long = 0L
)

data class MedicineModel(
    val id: String = "",
    val pairCode: String = "",
    val name: String = "",
    val dosage: String = "",
    val instruction: String = "",
    val scheduleTimes: List<String> = emptyList(),
    val repeatType: String = "DAILY",
    val startDateEpochDay: Long = 0L,
    val notes: String = "",
    val createdAt: Long = 0L
)

data class DoseLogModel(
    val pairCode: String,
    val scheduleId: Long,
    val medicineName: String,
    val dateEpochDay: Long,
    val isTaken: Boolean,
    val takenTime: String,
    val timestamp: Long,
    val medicineId: Long = -1L,
    val dosage: String = "",
    val instruction: String = "",
    val reminderTime: String = "",
    val takenTimestamp: Long? = if (timestamp > 0L) timestamp else null
)

typealias ElderlyProfile = ElderlyProfileModel
typealias FirestoreMedicineModel = MedicineModel
typealias RemoteDoseLog = DoseLogModel

class FirestoreRepository(
    context: Context
) {
    private val firestore: FirebaseFirestore? by lazy {
        runCatching {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            FirebaseFirestore.getInstance()
        }.getOrNull()
    }

    suspend fun createElderlyProfile(pairCode: String) {
        val db = requireNotNull(firestore) { "Firebase chưa được cấu hình." }
        val normalizedPairCode = pairCode.trim().uppercase()
        val data = mapOf(
            "pairCode" to normalizedPairCode,
            "emergencyPhone" to "",
            "createdAt" to System.currentTimeMillis(),
            "serverCreatedAt" to Timestamp.now()
        )
        db.collection(ELDERLY_PROFILES)
            .document(normalizedPairCode)
            .set(data)
            .await()
    }

    suspend fun connectCaregiver(pairCode: String, caregiverPhone: String): Boolean {
        val db = requireNotNull(firestore) { "Firebase chưa được cấu hình." }
        val normalizedPairCode = pairCode.trim().uppercase()
        val document = db.collection(ELDERLY_PROFILES)
            .document(normalizedPairCode)
            .get()
            .await()

        if (!document.exists()) return false

        db.collection(ELDERLY_PROFILES)
            .document(normalizedPairCode)
            .update("emergencyPhone", caregiverPhone)
            .await()

        return true
    }

    fun observeElderlyProfile(pairCode: String): Flow<ElderlyProfileModel?> = callbackFlow {
        val db = firestore
        if (db == null) {
            close(IllegalStateException("Không thể kết nối Firebase."))
            awaitClose { }
            return@callbackFlow
        }
        val normalizedPairCode = pairCode.trim().uppercase()
        val registration = db.collection(ELDERLY_PROFILES)
            .document(normalizedPairCode)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                if (snapshot?.exists() != true) {
                    trySend(null)
                } else {
                    trySend(
                        ElderlyProfileModel(
                            pairCode = snapshot.getString("pairCode").orEmpty(),
                            emergencyPhone = snapshot.getString("emergencyPhone").orEmpty(),
                            createdAt = snapshot.getLong("createdAt") ?: 0L
                        )
                    )
                }
            }

        awaitClose { registration.remove() }
    }

    suspend fun addOrUpdateMedicine(
        pairCode: String,
        id: String? = null,
        name: String,
        dosage: String,
        instruction: String,
        scheduleTimes: List<String>,
        repeatType: String = "DAILY",
        startDateEpochDay: Long = LocalDate.now().toEpochDay(),
        notes: String = ""
    ): String {
        val db = requireNotNull(firestore) { "Firebase chưa được cấu hình." }
        val document = if (id.isNullOrBlank()) {
            db.collection(MEDICINES).document()
        } else {
            db.collection(MEDICINES).document(id)
        }
        val medicine = MedicineModel(
            id = document.id,
            pairCode = pairCode.trim().uppercase(),
            name = name.trim(),
            dosage = dosage.trim(),
            instruction = instruction.trim(),
            scheduleTimes = scheduleTimes.map { it.trim() }.filter { it.isNotBlank() }.sorted(),
            repeatType = repeatType.trim(),
            startDateEpochDay = startDateEpochDay,
            notes = notes.trim(),
            createdAt = System.currentTimeMillis()
        )
        document.set(medicine.toMap()).await()
        return document.id
    }

    suspend fun deleteMedicine(docId: String) {
        val db = requireNotNull(firestore) { "Firebase chưa được cấu hình." }
        db.collection(MEDICINES)
            .document(docId)
            .delete()
            .await()
    }

    fun observeMedicines(pairCode: String, protectLocalData: Boolean = false): Flow<List<MedicineModel>> = callbackFlow {
        val db = firestore
        if (db == null) {
            close(IllegalStateException("Không thể kết nối Firebase."))
            awaitClose { }
            return@callbackFlow
        }

        val registration = db.collection(MEDICINES)
            .whereEqualTo("pairCode", pairCode.trim().uppercase())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                // An empty offline cache is not evidence that the caregiver deleted medicines.
                if (protectLocalData && snapshot?.metadata?.isFromCache == true && snapshot.isEmpty) {
                    return@addSnapshotListener
                }
                val medicines = snapshot?.documents.orEmpty()
                    .mapNotNull { document ->
                        MedicineModel(
                            id = document.getString("id") ?: document.id,
                            pairCode = document.getString("pairCode") ?: return@mapNotNull null,
                            name = document.getString("name").orEmpty(),
                            dosage = document.getString("dosage").orEmpty(),
                            instruction = document.getString("instruction").orEmpty(),
                            scheduleTimes = document.getStringListField("scheduleTimes"),
                            repeatType = document.getString("repeatType") ?: "DAILY",
                            startDateEpochDay = document.getLong("startDateEpochDay") ?: 0L,
                            notes = document.getString("notes").orEmpty(),
                            createdAt = document.getLong("createdAt") ?: 0L
                        )
                    }
                    .sortedByDescending { it.createdAt }

                trySend(medicines)
            }

        awaitClose { registration.remove() }
    }

    suspend fun syncDoseLog(pairCode: String, log: DoseLogModel) {
        val db = requireNotNull(firestore) { "Firebase chưa được cấu hình." }
        val normalizedPairCode = pairCode.trim().uppercase().ifBlank { log.pairCode }
        val syncedLog = log.copy(pairCode = normalizedPairCode)

        db.collection(DOSE_LOGS)
            .document(syncedLog.documentId())
            .set(syncedLog.toMap())
            .await()
    }

    suspend fun upsertDoseLog(log: RemoteDoseLog) {
        syncDoseLog(log.pairCode, log)
    }

    fun observeTodayDoseLogs(
        pairCode: String,
        dateEpochDay: Long = LocalDate.now().toEpochDay()
    ): Flow<List<DoseLogModel>> = callbackFlow {
        val db = firestore
        if (db == null) {
            close(IllegalStateException("Không thể kết nối Firebase."))
            awaitClose { }
            return@callbackFlow
        }
        val registration = db.collection(DOSE_LOGS)
            .whereEqualTo("pairCode", pairCode.trim().uppercase())
            .whereEqualTo("dateEpochDay", dateEpochDay)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val logs = snapshot?.documents.orEmpty().mapNotNull { document ->
                    val timestamp = document.getLong("timestamp")
                        ?: document.getLong("takenTimestamp")
                        ?: 0L
                    DoseLogModel(
                        pairCode = document.getString("pairCode") ?: return@mapNotNull null,
                        scheduleId = document.getLong("scheduleId") ?: return@mapNotNull null,
                        medicineName = document.getString("medicineName").orEmpty(),
                        dateEpochDay = document.getLong("dateEpochDay") ?: dateEpochDay,
                        isTaken = document.getBoolean("isTaken") == true,
                        takenTime = document.getString("takenTime").orEmpty(),
                        timestamp = timestamp,
                        medicineId = document.getLong("medicineId") ?: -1L,
                        dosage = document.getString("dosage").orEmpty(),
                        instruction = document.getString("instruction").orEmpty(),
                        reminderTime = document.getString("reminderTime").orEmpty(),
                        takenTimestamp = document.getLong("takenTimestamp") ?: timestamp.takeIf { it > 0L }
                    )
                }.sortedBy { it.reminderTime }
                trySend(logs)
            }

        awaitClose { registration.remove() }
    }

    suspend fun addMedicine(
        pairCode: String,
        name: String,
        dosage: String,
        instruction: String,
        times: List<String>
    ): String {
        return addOrUpdateMedicine(
            pairCode = pairCode,
            name = name,
            dosage = dosage,
            instruction = instruction,
            scheduleTimes = times
        )
    }

    private fun DoseLogModel.documentId(): String {
        return "${pairCode}_${dateEpochDay}_${scheduleId}"
    }

    companion object {
        private const val ELDERLY_PROFILES = "elderly_profiles"
        private const val DOSE_LOGS = "dose_logs"
        private const val MEDICINES = "medicines"
    }
}

private fun MedicineModel.toMap(): Map<String, Any> {
    return mapOf(
        "id" to id,
        "pairCode" to pairCode,
        "name" to name,
        "dosage" to dosage,
        "instruction" to instruction,
        "scheduleTimes" to scheduleTimes,
        "repeatType" to repeatType,
        "startDateEpochDay" to startDateEpochDay,
        "notes" to notes,
        "createdAt" to createdAt
    )
}

private fun DoseLogModel.toMap(): Map<String, Any?> {
    val effectiveTimestamp = timestamp.takeIf { it > 0L } ?: takenTimestamp ?: System.currentTimeMillis()
    return mapOf(
        "pairCode" to pairCode,
        "scheduleId" to scheduleId,
        "medicineName" to medicineName,
        "dateEpochDay" to dateEpochDay,
        "isTaken" to isTaken,
        "takenTime" to takenTime.ifBlank { effectiveTimestamp.formatHourMinute() },
        "timestamp" to effectiveTimestamp,
        "medicineId" to medicineId,
        "dosage" to dosage,
        "instruction" to instruction,
        "reminderTime" to reminderTime,
        "takenTimestamp" to takenTimestamp,
        "updatedAt" to Timestamp.now()
    )
}

private fun Long.formatHourMinute(): String {
    return DateTimeFormatter.ofPattern("HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(this))
}

private fun com.google.firebase.firestore.DocumentSnapshot.getStringListField(
    field: String
): List<String> {
    return (get(field) as? List<*>)
        .orEmpty()
        .mapNotNull { it as? String }
}
