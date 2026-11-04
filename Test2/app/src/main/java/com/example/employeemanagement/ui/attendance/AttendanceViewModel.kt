package com.example.employeemanagement.ui.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.employeemanagement.data.local.AppDatabase
import com.example.employeemanagement.data.local.entity.WorkLog
import com.example.employeemanagement.data.session.AccountRole
import com.example.employeemanagement.data.session.SharedPrefManager
import com.example.employeemanagement.domain.attendance.AttendanceTime
import com.example.employeemanagement.domain.attendance.AttendanceValidation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AttendanceUiState(
    val date: String = AttendanceTime.now().date,
    val currentTime: String = AttendanceTime.now().time,
    val selectedMonth: String = AttendanceTime.now().month,
    val today: WorkLog? = null,
    val history: List<WorkLog> = emptyList(),
    val monthlyHours: Double = 0.0,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val canCheckIn: Boolean = false,
    val canCheckOut: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

private data class AttendanceOperation(val saving: Boolean = false, val error: String? = null, val message: String? = null)
private data class AttendanceHistory(val logs: List<WorkLog> = emptyList(), val error: String? = null)

class AttendanceViewModel(
    private val database: AppDatabase,
    private val sessionManager: SharedPrefManager,
) : ViewModel() {
    private val employeeId = sessionManager.getSession()?.takeIf { it.role == AccountRole.EMPLOYEE }?.employeeId
    private val operation = MutableStateFlow(AttendanceOperation())
    // null follows the current month automatically, including across midnight/month end.
    private val selectedMonth = MutableStateFlow<String?>(null)
    private val clock = flow {
        while (true) {
            emit(AttendanceTime.now())
            delay(1000)
        }
    }
    private val history: Flow<AttendanceHistory> = if (employeeId == null) {
        flowOf(AttendanceHistory(error = "Phiên nhân viên không hợp lệ. Vui lòng đăng nhập lại."))
    } else {
        database.workLogDao().getHistoryByEmployee(employeeId)
            .map { AttendanceHistory(logs = it) }
            .catch { exception ->
                if (exception is CancellationException) throw exception
                emit(AttendanceHistory(error = "Không thể tải lịch sử chấm công."))
            }
    }

    val uiState = combine(clock, history, selectedMonth, operation) { now, records, month, action ->
        val today = records.logs.firstOrNull { it.workDate == now.date }
        val shownMonth = month ?: now.month
        val monthlyLogs = records.logs.filter { it.workDate.startsWith("$shownMonth-") }
        val available = employeeId != null && records.error == null && !action.saving
        AttendanceUiState(
            date = now.date,
            currentTime = now.time,
            selectedMonth = shownMonth,
            today = today,
            history = monthlyLogs,
            monthlyHours = monthlyLogs.filter { it.status == "COMPLETED" && it.checkOutTime != null }.sumOf { it.totalHours },
            isLoading = false,
            isSaving = action.saving,
            canCheckIn = available && today == null,
            canCheckOut = available && today?.status == "CHECKED_IN" && today.checkOutTime == null,
            error = action.error ?: records.error,
            message = action.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AttendanceUiState())

    fun moveMonth(offset: Int) {
        selectedMonth.value = AttendanceTime.shiftMonth(uiState.value.selectedMonth, offset)
    }

    fun showCurrentMonth() { selectedMonth.value = null }
    fun clearMessage() { operation.update { it.copy(message = null) } }

    fun checkIn() = performAttendance { id ->
        database.withTransaction {
            val now = AttendanceTime.now()
            val existing = database.workLogDao().getTodayWorkLog(id, now.date)
            AttendanceValidation.validateCheckIn(existing)
            // The existence check and insert share one transaction, so concurrent taps
            // from different ViewModels cannot both insert a record for this date.
            database.workLogDao().insert(WorkLog(
                employeeId = id,
                workDate = now.date,
                checkInTime = now.time,
                checkOutTime = null,
                totalHours = 0.0,
                status = "CHECKED_IN",
            ))
            "Check In thành công lúc ${now.time}."
        }
    }

    fun checkOut() = performAttendance { id ->
        database.withTransaction {
            val now = AttendanceTime.now()
            val today = AttendanceValidation.validateCheckOut(
                database.workLogDao().getTodayWorkLog(id, now.date), now.date,
            )
            val hours = AttendanceTime.calculateHours(today.checkInTime, now.time)
            val updated = database.workLogDao().completeAttendance(today.id, id, now.date, now.time, hours)
            require(updated == 1) { "Bản ghi đã thay đổi hoặc đã Check Out. Vui lòng kiểm tra lại." }
            "Check Out thành công lúc ${now.time}."
        }
    }

    private fun performAttendance(action: suspend (Long) -> String) {
        if (operation.value.saving) return
        val session = sessionManager.getSession()
        if (session?.role != AccountRole.EMPLOYEE || employeeId == null || session.employeeId != employeeId) {
            operation.value = AttendanceOperation(error = "Phiên nhân viên không hợp lệ. Vui lòng đăng nhập lại.")
            return
        }
        operation.value = AttendanceOperation(saving = true)
        viewModelScope.launch {
            try {
                val employee = database.employeeDao().getById(employeeId)
                require(employee?.accountId == session.userId) { "Hồ sơ nhân viên không còn tồn tại hoặc không khớp tài khoản." }
                operation.value = AttendanceOperation(message = action(employeeId))
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: IllegalArgumentException) {
                operation.value = AttendanceOperation(error = exception.message ?: "Dữ liệu chấm công không hợp lệ.")
            } catch (_: Exception) {
                operation.value = AttendanceOperation(error = "Không thể lưu chấm công. Vui lòng thử lại.")
            }
        }
    }

    class Factory(private val database: AppDatabase, private val session: SharedPrefManager) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AttendanceViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return AttendanceViewModel(database, session) as T
        }
    }
}
