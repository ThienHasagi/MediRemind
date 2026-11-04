package com.example.mediremind.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.random.Random

enum class UserRole {
    ELDERLY,
    CAREGIVER,
    NONE
}

private val Context.userPreferencesDataStore by preferencesDataStore(
    name = "user_preferences"
)

class UserPreferences(
    private val context: Context
) {
    val userRoleFlow: Flow<UserRole> = context.userPreferencesDataStore.data.map { preferences ->
        val savedValue = preferences[USER_ROLE_KEY]
        UserRole.entries.firstOrNull { it.name == savedValue } ?: UserRole.NONE
    }

    val pairCodeFlow: Flow<String> = context.userPreferencesDataStore.data.map { preferences ->
        preferences[PAIR_CODE_KEY].orEmpty()
    }

    val emergencyPhoneFlow: Flow<String> = context.userPreferencesDataStore.data.map { preferences ->
        preferences[EMERGENCY_PHONE_KEY].orEmpty()
    }

    val parentPhoneFlow: Flow<String> = context.userPreferencesDataStore.data.map { preferences ->
        preferences[PARENT_PHONE_KEY].orEmpty()
    }

    suspend fun saveRole(role: UserRole) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[USER_ROLE_KEY] = role.name
        }
    }

    suspend fun savePairCode(pairCode: String) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[PAIR_CODE_KEY] = pairCode
        }
    }

    suspend fun saveEmergencyPhone(phone: String) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[EMERGENCY_PHONE_KEY] = phone
        }
    }

    suspend fun saveParentPhone(phone: String) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[PARENT_PHONE_KEY] = phone
        }
    }

    suspend fun saveElderlyProfile(pairCode: String) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[PAIR_CODE_KEY] = pairCode
            preferences[USER_ROLE_KEY] = UserRole.ELDERLY.name
        }
    }

    suspend fun saveCaregiverProfile(pairCode: String, emergencyPhone: String) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[PAIR_CODE_KEY] = pairCode
            preferences[EMERGENCY_PHONE_KEY] = emergencyPhone
            preferences[USER_ROLE_KEY] = UserRole.CAREGIVER.name
        }
    }

    suspend fun resetPreferences() {
        context.userPreferencesDataStore.edit { preferences ->
            preferences.clear()
        }
    }

    companion object {
        private val USER_ROLE_KEY = stringPreferencesKey("user_role")
        private val PAIR_CODE_KEY = stringPreferencesKey("pair_code")
        private val EMERGENCY_PHONE_KEY = stringPreferencesKey("emergency_phone")
        private val PARENT_PHONE_KEY = stringPreferencesKey("parent_phone")

        fun generatePairCode(): String {
            val number = Random.nextInt(from = 1000, until = 10_000)
            return "BA-$number"
        }
    }
}
