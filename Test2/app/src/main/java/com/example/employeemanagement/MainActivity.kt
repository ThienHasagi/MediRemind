package com.example.employeemanagement

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import com.example.employeemanagement.data.local.AppDatabase
import com.example.employeemanagement.data.session.SharedPrefManager
import com.example.employeemanagement.ui.auth.AuthViewModel
import com.example.employeemanagement.ui.navigation.AppNavigation

class MainActivity : ComponentActivity() {
    private val database by lazy { AppDatabase.getInstance(applicationContext) }
    private val sessionManager by lazy { SharedPrefManager(applicationContext) }
    private val authViewModel: AuthViewModel by viewModels {
        AuthViewModel.Factory(database, sessionManager)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                AppNavigation(authViewModel, database, sessionManager)
            }
        }
    }
}
