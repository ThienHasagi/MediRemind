package com.example.employeemanagement.ui.salary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.employeemanagement.data.local.AppDatabase
import com.example.employeemanagement.data.local.model.SalaryDetails
import com.example.employeemanagement.data.session.AccountRole
import com.example.employeemanagement.data.session.SharedPrefManager
import com.example.employeemanagement.domain.salary.SalaryCalculator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

data class SalaryPeriod(val month: Int, val year: Int)
data class SalaryUiState(val isWorking: Boolean = false, val error: String? = null, val message: String? = null)

@OptIn(ExperimentalCoroutinesApi::class)
class SalaryViewModel(
    private val database: AppDatabase,
    private val sessionManager: SharedPrefManager,
) : ViewModel() {
    private val today = Calendar.getInstance()
    private val _period = MutableStateFlow(SalaryPeriod(today.get(Calendar.MONTH) + 1, today.get(Calendar.YEAR)))
    val period = _period.asStateFlow()
    private val _uiState = MutableStateFlow(SalaryUiState())
    val uiState = _uiState.asStateFlow()
    private val calculator = SalaryCalculator(database)

    val salaries = _period.flatMapLatest { period ->
        val session = sessionManager.getSession()
        val rows: Flow<List<SalaryDetails>> = when (session?.role) {
            AccountRole.ADMIN -> database.salaryDao().observeMonthlyDetails(period.month, period.year)
            AccountRole.EMPLOYEE -> session.employeeId?.let {
                database.salaryDao().observeEmployeeMonthlyDetails(it, period.month, period.year)
            } ?: flowOf(emptyList())
            else -> flowOf(emptyList())
        }
        rows.catch { exception ->
            if (exception is CancellationException) throw exception
            _uiState.update { it.copy(error = "Không thể tải bảng lương.") }
            emit(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectPeriod(month: String, year: String) {
        if (_uiState.value.isWorking) return
        val monthNumber = month.trim().toIntOrNull()
        val yearNumber = year.trim().toIntOrNull()
        if (monthNumber == null || monthNumber !in 1..12 || yearNumber == null || yearNumber !in 1..9999) {
            _uiState.update { it.copy(error = "Tháng phải từ 1–12, năm từ 1–9999.") }
            return
        }
        _uiState.value = SalaryUiState()
        _period.value = SalaryPeriod(monthNumber, yearNumber)
    }

    fun calculateMonthlySalaries() {
        if (_uiState.value.isWorking || !checkAdmin()) return
        val selected = _period.value
        performOperation {
            val count = calculator.calculateAllEmployees(selected.month, selected.year)
            if (count == 0) "Chưa có nhân viên để tính lương."
            else "Đã tính lương tháng ${selected.month}/${selected.year} cho $count nhân viên."
        }
    }

    fun markAsPaid(salaryId: Long) {
        if (_uiState.value.isWorking || !checkAdmin()) return
        performOperation {
            if (database.salaryDao().markAsPaid(salaryId) == 1) "Đã đánh dấu thanh toán."
            else "Bảng lương đã được thanh toán hoặc không còn tồn tại."
        }
    }

    fun clearMessage() { _uiState.update { it.copy(message = null) } }

    private fun checkAdmin(): Boolean {
        val allowed = sessionManager.getSession()?.role == AccountRole.ADMIN
        if (!allowed) _uiState.update { it.copy(error = "Chỉ Admin được tính lương hoặc xác nhận thanh toán.") }
        return allowed
    }

    private fun performOperation(operation: suspend () -> String) {
        _uiState.value = SalaryUiState(isWorking = true)
        viewModelScope.launch {
            try {
                _uiState.value = SalaryUiState(message = operation())
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: IllegalArgumentException) {
                _uiState.value = SalaryUiState(error = exception.message ?: "Dữ liệu tính lương không hợp lệ.")
            } catch (_: Exception) {
                _uiState.value = SalaryUiState(error = "Không thể lưu bảng lương. Vui lòng thử lại.")
            }
        }
    }

    class Factory(private val database: AppDatabase, private val session: SharedPrefManager) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SalaryViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return SalaryViewModel(database, session) as T
        }
    }
}
