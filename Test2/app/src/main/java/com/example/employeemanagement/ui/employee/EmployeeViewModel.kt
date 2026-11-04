package com.example.employeemanagement.ui.employee

import android.database.sqlite.SQLiteConstraintException
import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.employeemanagement.data.local.dao.EmployeeDao
import com.example.employeemanagement.data.local.entity.Employee
import com.example.employeemanagement.data.session.AccountRole
import com.example.employeemanagement.data.session.SharedPrefManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EmployeeForm(
    val code: String = "",
    val fullname: String = "",
    val email: String = "",
    val phone: String = "",
    val hourlyRate: String = "",
)

data class EmployeeUiState(
    val editorOpen: Boolean = false,
    val editingEmployee: Employee? = null,
    val form: EmployeeForm = EmployeeForm(),
    val fieldErrors: Map<String, String> = emptyMap(),
    val pendingDelete: Employee? = null,
    val isSaving: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class EmployeeViewModel(
    private val employeeDao: EmployeeDao,
    private val sessionManager: SharedPrefManager,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()
    private val _uiState = MutableStateFlow(EmployeeUiState())
    val uiState = _uiState.asStateFlow()

    val employees = _query.flatMapLatest { query ->
        employeeDao.search(query.trim()).catch { exception ->
            if (exception is CancellationException) throw exception
            _uiState.update { it.copy(error = "Không thể tải danh sách nhân viên.") }
            emit(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun search(query: String) { _query.value = query }

    fun openAddDialog() {
        if (!_uiState.value.isSaving) _uiState.value = EmployeeUiState(editorOpen = true)
    }

    fun openEditDialog(employee: Employee) {
        if (_uiState.value.isSaving) return
        _uiState.value = EmployeeUiState(
            editorOpen = true,
            editingEmployee = employee,
            form = EmployeeForm(employee.code, employee.fullname, employee.email, employee.phone, employee.hourlyRate.toString()),
        )
    }

    fun updateForm(form: EmployeeForm) {
        if (!_uiState.value.isSaving) {
            _uiState.update { it.copy(form = form, fieldErrors = emptyMap(), error = null) }
        }
    }

    fun dismissDialogs() {
        if (!_uiState.value.isSaving) _uiState.value = EmployeeUiState()
    }

    fun requestDelete(employee: Employee) {
        if (!_uiState.value.isSaving) _uiState.value = EmployeeUiState(pendingDelete = employee)
    }

    fun clearMessage() { _uiState.update { it.copy(message = null) } }

    fun addEmployee() {
        if (_uiState.value.editingEmployee == null) saveEmployee()
    }

    fun updateEmployee() {
        if (_uiState.value.editingEmployee != null) saveEmployee()
    }

    private fun saveEmployee() {
        val state = _uiState.value
        if (state.isSaving || !state.editorOpen || !checkAdmin()) return
        val form = state.form
        val rate = form.hourlyRate.trim().toDoubleOrNull()
        val errors = buildMap {
            if (form.code.isBlank()) put("code", "Vui lòng nhập mã nhân viên.")
            else if (form.code.trim().any { it.isWhitespace() }) put("code", "Mã nhân viên không chứa khoảng trắng.")
            if (form.fullname.isBlank()) put("fullname", "Vui lòng nhập họ tên.")
            if (!Patterns.EMAIL_ADDRESS.matcher(form.email.trim()).matches()) put("email", "Vui lòng nhập email hợp lệ.")
            if (!Regex("^(0[0-9]{9}|\\+84[0-9]{9})$").matches(form.phone.trim())) {
                put("phone", "Nhập 10 chữ số bắt đầu bằng 0 hoặc +84 và 9 chữ số.")
            }
            if (rate == null || !rate.isFinite() || rate <= 0) put("hourlyRate", "Lương theo giờ phải là số dương hợp lệ.")
        }
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(fieldErrors = errors, error = null) }
            return
        }
        val employee = Employee(
            id = state.editingEmployee?.id ?: 0,
            code = form.code.trim(),
            fullname = form.fullname.trim(),
            email = form.email.trim(),
            phone = form.phone.trim(),
            hourlyRate = requireNotNull(rate),
            accountId = state.editingEmployee?.accountId ?: 0,
        )
        performMutation(if (state.editingEmployee == null) "Đã thêm nhân viên." else "Đã cập nhật nhân viên.") {
            if (state.editingEmployee == null) employeeDao.insertWithAccount(employee)
            else employeeDao.updateWithAccount(employee)
        }
    }

    fun deleteEmployee() {
        val employee = _uiState.value.pendingDelete ?: return
        if (_uiState.value.isSaving || !checkAdmin()) return
        performMutation("Đã xóa nhân viên và tài khoản liên kết.") {
            employeeDao.deleteWithAccount(employee.id)
        }
    }

    private fun checkAdmin(): Boolean {
        val isAdmin = sessionManager.getSession()?.role == AccountRole.ADMIN
        if (!isAdmin) _uiState.update { it.copy(error = "Chức năng này chỉ dành cho Admin.") }
        return isAdmin
    }

    private fun performMutation(message: String, operation: suspend () -> Unit) {
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                operation()
                _uiState.value = EmployeeUiState(message = message)
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: SQLiteConstraintException) {
                _uiState.update { it.copy(isSaving = false, error = "Mã nhân viên hoặc tên đăng nhập đã tồn tại.") }
            } catch (_: Exception) {
                _uiState.update { it.copy(isSaving = false, error = "Không thể lưu thay đổi. Vui lòng tải lại và thử lại.") }
            }
        }
    }

    class Factory(private val dao: EmployeeDao, private val session: SharedPrefManager) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(EmployeeViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return EmployeeViewModel(dao, session) as T
        }
    }
}
