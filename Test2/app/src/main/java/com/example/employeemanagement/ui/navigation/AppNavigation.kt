package com.example.employeemanagement.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.employeemanagement.data.local.AppDatabase
import com.example.employeemanagement.data.session.AccountRole
import com.example.employeemanagement.data.session.SharedPrefManager
import com.example.employeemanagement.data.session.UserSession
import com.example.employeemanagement.ui.attendance.AttendanceViewModel
import com.example.employeemanagement.ui.auth.AuthViewModel
import com.example.employeemanagement.ui.auth.LoginScreen
import com.example.employeemanagement.ui.dashboard.AdminDashboardScreen
import com.example.employeemanagement.ui.dashboard.EmployeeDashboardScreen
import com.example.employeemanagement.ui.employee.EmployeeViewModel
import com.example.employeemanagement.ui.salary.SalaryViewModel

object AppRoute {
    const val LOGIN = "login"
    const val ADMIN_DASHBOARD = "admin_dashboard"
    const val EMPLOYEE_DASHBOARD = "employee_dashboard"

    fun forSession(session: UserSession?): String = when (session?.role) {
        AccountRole.ADMIN -> ADMIN_DASHBOARD
        AccountRole.EMPLOYEE -> EMPLOYEE_DASHBOARD
        else -> LOGIN
    }
}

@Composable
fun AppNavigation(authViewModel: AuthViewModel, database: AppDatabase, sessionManager: SharedPrefManager) {
    val uiState by authViewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val initialRoute = remember { AppRoute.forSession(uiState.session) }
    val targetRoute = AppRoute.forSession(uiState.session)
    val backStackEntry by navController.currentBackStackEntryAsState()

    LaunchedEffect(targetRoute, backStackEntry?.destination?.route) {
        val currentRoute = backStackEntry?.destination?.route
        if (currentRoute != null && currentRoute != targetRoute) {
            navController.navigate(targetRoute) {
                // Discard the previous session's graph, ViewModels and saved tab state.
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = initialRoute,
        modifier = Modifier.fillMaxSize(),
        enterTransition = { fadeIn(tween(150)) },
        exitTransition = { fadeOut(tween(150)) },
    ) {
        composable(AppRoute.LOGIN) {
            if (uiState.session == null) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LoginScreen(uiState, authViewModel::login, authViewModel::clearError, Modifier.safeDrawingPadding())
                }
            }
        }
        composable(AppRoute.ADMIN_DASHBOARD) {
            uiState.session?.takeIf { it.role == AccountRole.ADMIN }?.let { session ->
                val employeeViewModel: EmployeeViewModel = viewModel(factory = remember(database, sessionManager) {
                    EmployeeViewModel.Factory(database.employeeDao(), sessionManager)
                })
                val salaryViewModel: SalaryViewModel = viewModel(factory = remember(database, sessionManager) {
                    SalaryViewModel.Factory(database, sessionManager)
                })
                AdminDashboardScreen(session, employeeViewModel, salaryViewModel, authViewModel::logout)
            }
        }
        composable(AppRoute.EMPLOYEE_DASHBOARD) {
            uiState.session?.takeIf { it.role == AccountRole.EMPLOYEE }?.let { session ->
                val attendanceViewModel: AttendanceViewModel = viewModel(factory = remember(database, sessionManager) {
                    AttendanceViewModel.Factory(database, sessionManager)
                })
                val salaryViewModel: SalaryViewModel = viewModel(factory = remember(database, sessionManager) {
                    SalaryViewModel.Factory(database, sessionManager)
                })
                EmployeeDashboardScreen(session, attendanceViewModel, salaryViewModel, authViewModel::logout)
            }
        }
    }
}
