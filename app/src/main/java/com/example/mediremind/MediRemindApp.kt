package com.example.mediremind

import android.app.Application
import com.example.mediremind.alarm.AndroidAlarmScheduler
import com.example.mediremind.data.local.AppDatabase
import com.example.mediremind.data.local.UserPreferences
import com.example.mediremind.data.remote.FirestoreRepository
import com.example.mediremind.data.repository.MedicineRepository

class MediRemindApp : Application() {
    val userPreferences: UserPreferences by lazy {
        UserPreferences(this)
    }

    val firestoreRepository: FirestoreRepository by lazy {
        FirestoreRepository(this)
    }

    val alarmScheduler: AndroidAlarmScheduler by lazy {
        AndroidAlarmScheduler(this)
    }

    val repository: MedicineRepository by lazy {
        val database = AppDatabase.getInstance(this)
        MedicineRepository(
            medicineDao = database.medicineDao(),
            doseLogDao = database.doseLogDao(),
            alarmScheduler = alarmScheduler
        )
    }
}
