package com.example.employeemanagement.ui.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.example.employeemanagement.R
import com.example.employeemanagement.data.session.UserSession
import com.example.employeemanagement.ui.attendance.AttendanceScreen
import com.example.employeemanagement.ui.attendance.AttendanceViewModel
import com.example.employeemanagement.ui.employee.EmployeeManagementScreen
import com.example.employeemanagement.ui.employee.EmployeeViewModel
import com.example.employeemanagement.ui.salary.AdminSalaryScreen
import com.example.employeemanagement.ui.salary.EmployeeSalaryScreen
import com.example.employeemanagement.ui.salary.SalaryViewModel

@Composable
fun AdminDashboardScreen(
    session: UserSession,
    employeeViewModel: EmployeeViewModel,
    salaryViewModel: SalaryViewModel,
    onLogout: () -> Unit,
) {
    DashboardScaffold(
        session = session,
        title = stringResource(R.string.admin_dashboard),
        labels = listOf(stringResource(R.string.tab_employees), stringResource(R.string.tab_admin_salary)),
        symbols = listOf("NV", "₫"),
        onLogout = onLogout,
    ) { tab ->
        when (tab) {
            0 -> EmployeeManagementScreen(employeeViewModel)
            1 -> AdminSalaryScreen(salaryViewModel)
        }
    }
}

@Composable
fun EmployeeDashboardScreen(
    session: UserSession,
    attendanceViewModel: AttendanceViewModel,
    salaryViewModel: SalaryViewModel,
    onLogout: () -> Unit,
) {
    DashboardScaffold(
        session = session,
        title = stringResource(R.string.employee_dashboard),
        labels = listOf(stringResource(R.string.tab_attendance), stringResource(R.string.tab_employee_salary)),
        symbols = listOf("✓", "₫"),
        onLogout = onLogout,
    ) { tab ->
        when (tab) {
            0 -> AttendanceScreen(attendanceViewModel)
            1 -> EmployeeSalaryScreen(salaryViewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardScaffold(
    session: UserSession,
    title: String,
    labels: List<String>,
    symbols: List<String>,
    onLogout: () -> Unit,
    content: @Composable (Int) -> Unit,
) {
    var selectedTab by rememberSaveable(session.userId) { mutableIntStateOf(0) }
    val savedTabState = rememberSaveableStateHolder()
    BackHandler(enabled = selectedTab != 0) { selectedTab = 0 }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            stringResource(R.string.welcome_user, session.username),
                            style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = { TextButton(onClick = onLogout) { Text(stringResource(R.string.logout)) } },
            )
        },
        bottomBar = {
            NavigationBar {
                labels.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Text(symbols[index]) },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            AnimatedContent(
                targetState = selectedTab,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                label = "dashboardTab",
            ) { tab ->
                savedTabState.SaveableStateProvider(tab) { content(tab) }
            }
        }
    }
}
