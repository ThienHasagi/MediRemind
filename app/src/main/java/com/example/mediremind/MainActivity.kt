package com.example.mediremind

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.mediremind.data.ai.ParsedMedicineUiModel
import com.example.mediremind.data.local.UserRole
import com.example.mediremind.ui.screens.AddEditMedicineScreen
import com.example.mediremind.ui.screens.CameraCaptureScreen
import com.example.mediremind.ui.screens.CaregiverDashboardScreen
import com.example.mediremind.ui.screens.CaregiverSettingsScreen
import com.example.mediremind.ui.screens.ElderlyHomeScreen
import com.example.mediremind.ui.screens.RoleSelectionScreen
import com.example.mediremind.ui.theme.MediRemindTheme
import com.example.mediremind.ui.viewmodel.CaregiverDashboardViewModel
import com.example.mediremind.ui.viewmodel.HomeViewModel
import com.example.mediremind.ui.viewmodel.RoleSelectionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.catch
import com.example.mediremind.data.userMessage
import androidx.activity.compose.BackHandler

class MainActivity : ComponentActivity() {
    private var pendingEmergencyPhone: String? = null
    private var permissionMessage by mutableStateOf<String?>(null)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) permissionMessage = "Chưa cấp quyền thông báo. Hãy bật Thông báo trong cài đặt để nhận nhắc uống thuốc."
        rescheduleAlarmsFromDatabase()
    }

    private val callPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val phone = pendingEmergencyPhone
        pendingEmergencyPhone = null
        if (!phone.isNullOrBlank()) {
            if (granted) {
                callPhoneNumber(phone)
            } else {
                permissionMessage = "Chưa cấp quyền gọi điện. Hãy bật quyền Điện thoại trong cài đặt rồi bấm gọi lại."
            }
        }
    }

    private val homeViewModel: HomeViewModel by viewModels {
        val app = application as MediRemindApp
        HomeViewModel.factory(
            repository = app.repository,
            firestoreRepository = app.firestoreRepository
        )
    }

    private val roleSelectionViewModel: RoleSelectionViewModel by viewModels {
        val app = application as MediRemindApp
        RoleSelectionViewModel.factory(
            userPreferences = app.userPreferences,
            firestoreRepository = app.firestoreRepository
        )
    }

    private val caregiverDashboardViewModel: CaregiverDashboardViewModel by viewModels {
        val app = application as MediRemindApp
        CaregiverDashboardViewModel.factory(app.firestoreRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        rescheduleAlarmsFromDatabase()

        val app = application as MediRemindApp
        setContent {
            MediRemindTheme {
                permissionMessage?.let { message ->
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { permissionMessage = null },
                        title = { androidx.compose.material3.Text("Cần kiểm tra quyền") },
                        text = { androidx.compose.material3.Text(message) },
                        confirmButton = { androidx.compose.material3.TextButton(onClick = {
                            permissionMessage = null
                            runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
                        }) { androidx.compose.material3.Text("Mở cài đặt") } },
                        dismissButton = { androidx.compose.material3.TextButton(onClick = { permissionMessage = null }) { androidx.compose.material3.Text("Đóng") } }
                    )
                }
                androidx.compose.foundation.layout.Column {
                com.example.mediremind.ui.screens.ConnectionStatus()
                MediRemindRoleRouter(
                    app = app,
                    homeViewModel = homeViewModel,
                    roleSelectionViewModel = roleSelectionViewModel,
                    caregiverDashboardViewModel = caregiverDashboardViewModel,
                    onCallPhoneClick = ::requestCallPermissionAndCall
                )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        rescheduleAlarmsFromDatabase()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestExactAlarmPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

        val alarmManager = getSystemService(AlarmManager::class.java)
        if (alarmManager.canScheduleExactAlarms()) return

        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:$packageName")
        }
        runCatching { startActivity(intent) }.onFailure {
            permissionMessage = "Không mở được quyền báo thức. Vui lòng kiểm tra cài đặt ứng dụng."
        }
    }

    private fun requestCallPermissionAndCall(phone: String) {
        if (!phone.replace(" ", "").matches(Regex("^\\+?[0-9]{8,15}$"))) {
            permissionMessage = "Số điện thoại chưa hợp lệ. Cập nhật số trong cài đặt máy Con cái."
            return
        }

        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            callPhoneNumber(phone)
        } else {
            pendingEmergencyPhone = phone
            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }

    private fun callPhoneNumber(phone: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_CALL, Uri.fromParts("tel", phone.replace(" ", ""), null))
            startActivity(intent)
        }.onFailure {
            permissionMessage = "Không thể gọi trực tiếp. Kiểm tra quyền Điện thoại, SIM và ứng dụng gọi điện rồi thử lại."
        }
    }

    private fun openDialer(phone: String = "") {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
        startActivity(intent)
    }

    private fun rescheduleAlarmsFromDatabase() {
        val app = application as MediRemindApp
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { app.alarmScheduler.rescheduleAll() }
        }
    }
}

@Composable
private fun MediRemindRoleRouter(
    app: MediRemindApp,
    homeViewModel: HomeViewModel,
    roleSelectionViewModel: RoleSelectionViewModel,
    caregiverDashboardViewModel: CaregiverDashboardViewModel,
    onCallPhoneClick: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val userRole by app.userPreferences.userRoleFlow.collectAsStateWithLifecycle(
        initialValue = UserRole.NONE
    )
    val pairCode by app.userPreferences.pairCodeFlow.collectAsStateWithLifecycle(
        initialValue = ""
    )
    val emergencyPhone by app.userPreferences.emergencyPhoneFlow.collectAsStateWithLifecycle(
        initialValue = ""
    )
    val parentPhone by app.userPreferences.parentPhoneFlow.collectAsStateWithLifecycle(
        initialValue = ""
    )
    var caregiverScreen by remember { mutableStateOf(CaregiverScreen.Dashboard) }
    var parsedMedicine by remember { mutableStateOf<ParsedMedicineUiModel?>(null) }
    val caregiverState by caregiverDashboardViewModel.uiState.collectAsStateWithLifecycle()
    var profileRetry by remember { mutableStateOf(0) }
    var profileError by remember { mutableStateOf<String?>(null) }
    BackHandler(userRole == UserRole.CAREGIVER && caregiverScreen != CaregiverScreen.Dashboard) {
        if (!caregiverState.isSaving) caregiverScreen = CaregiverScreen.Dashboard
    }
    profileError?.let { message ->
        androidx.compose.material3.AlertDialog(onDismissRequest = { profileError = null },
            title = { androidx.compose.material3.Text("Chưa cập nhật được liên hệ") },
            text = { androidx.compose.material3.Text(message) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { profileError = null; profileRetry++ }) { androidx.compose.material3.Text("Thử lại") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { profileError = null }) { androidx.compose.material3.Text("Đóng") } })
    }

    LaunchedEffect(userRole, pairCode, profileRetry) {
        caregiverDashboardViewModel.setPairCode(if (userRole == UserRole.CAREGIVER) pairCode else "")
        if (userRole != UserRole.ELDERLY) homeViewModel.stopRemoteMedicineSync()
        if (userRole == UserRole.ELDERLY && pairCode.isNotBlank()) {
            app.firestoreRepository.observeElderlyProfile(pairCode).catch { profileError = it.userMessage() }.collectLatest { profile ->
                if (profile != null) {
                    app.userPreferences.saveEmergencyPhone(profile.emergencyPhone)
                }
            }
        }
    }

    when (userRole) {
        UserRole.NONE -> RoleSelectionScreen(
            viewModel = roleSelectionViewModel
        )

        UserRole.ELDERLY -> ElderlyHomeScreen(
            viewModel = homeViewModel,
            pairCode = pairCode,
            emergencyPhone = emergencyPhone,
            onCallChildClick = onCallPhoneClick
        )

        UserRole.CAREGIVER -> when (caregiverScreen) {
            CaregiverScreen.Dashboard -> CaregiverDashboardScreen(
                viewModel = caregiverDashboardViewModel,
                pairCode = pairCode,
                onQuickCallClick = {
                    if (parentPhone.isNotBlank()) {
                        onCallPhoneClick(parentPhone)
                    } else {
                        caregiverScreen = CaregiverScreen.Settings
                    }
                },
                onSettingsClick = {
                    caregiverScreen = CaregiverScreen.Settings
                },
                onAiPrescriptionClick = {
                    caregiverScreen = CaregiverScreen.Camera
                },
                onManualMedicineClick = {
                    caregiverDashboardViewModel.newDraft()
                    parsedMedicine = null
                    caregiverScreen = CaregiverScreen.AddEdit
                }
            )

            CaregiverScreen.Settings -> CaregiverSettingsScreen(
                pairCode = pairCode,
                parentPhone = parentPhone,
                caregiverPhone = emergencyPhone,
                onBackClick = {
                    caregiverScreen = CaregiverScreen.Dashboard
                },
                onSaveParentPhone = { phone ->
                        app.userPreferences.saveParentPhone(phone)
                },
                onSaveCaregiverPhone = { phone ->
                    kotlinx.coroutines.withTimeout(20_000) {
                        check(app.firestoreRepository.connectCaregiver(pairCode, phone)) { "Mã ghép đôi không tồn tại." }
                        app.userPreferences.saveEmergencyPhone(phone)
                    }
                },
                onResetRoleClick = {
                    coroutineScope.launch {
                        app.userPreferences.resetPreferences()
                        roleSelectionViewModel.resetState()
                        caregiverScreen = CaregiverScreen.Dashboard
                    }
                }
            )

            CaregiverScreen.Camera -> CameraCaptureScreen(
                onBackClick = {
                    caregiverScreen = CaregiverScreen.Dashboard
                },
                onParsedMedicines = { medicines ->
                    caregiverDashboardViewModel.newDraft()
                    parsedMedicine = medicines.firstOrNull()
                    caregiverScreen = CaregiverScreen.AddEdit
                }
            )

            CaregiverScreen.AddEdit -> AddEditMedicineScreen(
                initialMedicine = parsedMedicine,
                isSaving = caregiverState.isSaving,
                errorMessage = caregiverState.errorMessage,
                onBackClick = {
                    caregiverScreen = CaregiverScreen.Dashboard
                },
                onSaveClick = { name, dosage, instruction, scheduleTimes, repeatType, startDate ->
                    caregiverDashboardViewModel.addMedicine(
                        name = name,
                        dosage = dosage,
                        instruction = instruction,
                        scheduleTimes = scheduleTimes,
                        repeatType = repeatType,
                        startDateEpochDay = startDate,
                        onSaved = {
                            parsedMedicine = null
                            caregiverScreen = CaregiverScreen.Dashboard
                        }
                    )
                }
            )
        }
    }
}

private enum class CaregiverScreen {
    Dashboard,
    Settings,
    Camera,
    AddEdit
}
