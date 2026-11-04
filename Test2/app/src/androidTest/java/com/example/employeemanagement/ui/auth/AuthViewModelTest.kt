package com.example.employeemanagement.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.employeemanagement.data.local.AppDatabase
import com.example.employeemanagement.data.local.entity.Account
import com.example.employeemanagement.data.session.AccountRole
import com.example.employeemanagement.data.session.SharedPrefManager
import com.example.employeemanagement.data.session.UserSession
import com.example.employeemanagement.ui.navigation.AppRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthViewModelTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var preferences: SharedPrefManager
    private lateinit var viewModel: AuthViewModel
    private val store = ViewModelStore()
    private val preferenceName = "auth_integration_test"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(AppDatabase.SeedCallback())
            .build()
        preferences = SharedPrefManager(context, preferenceName)
        preferences.logout()
        recreateViewModel()
    }

    @After
    fun tearDown() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
        database.close()
        preferences.logout()
    }

    private fun recreateViewModel() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            store.clear()
            viewModel = ViewModelProvider(
                store, AuthViewModel.Factory(database, preferences),
            )[AuthViewModel::class.java]
        }
    }

    private suspend fun login(username: String, password: String): AuthUiState {
        withContext(Dispatchers.Main) { viewModel.login(username, password) }
        return withTimeout(5000) { viewModel.uiState.first { !it.isLoading } }
    }

    @Test
    fun adminLoginPersistsAndRestoresSessionThenLogoutClearsEveryField() = runBlocking {
        val result = login(" admin ", "123456")
        assertNull(result.error)
        assertEquals(AccountRole.ADMIN, result.session?.role)
        assertEquals(AppRoute.ADMIN_DASHBOARD, AppRoute.forSession(result.session))
        assertTrue(preferences.isLoggedIn)
        assertEquals(requireNotNull(database.accountDao().getByUsername("admin")).id, preferences.userId)
        assertEquals(SharedPrefManager.NO_ID, preferences.employeeId)

        // A new manager and a new ViewModel restore the persisted session.
        assertEquals(result.session, SharedPrefManager(context, preferenceName).getSession())
        recreateViewModel()
        assertEquals(result.session, viewModel.uiState.value.session)

        withContext(Dispatchers.Main) { viewModel.logout() }
        assertNull(viewModel.uiState.value.session)
        assertFalse(preferences.isLoggedIn)
        assertEquals(SharedPrefManager.NO_ID, preferences.userId)
        assertEquals(SharedPrefManager.NO_ID, preferences.employeeId)
        assertEquals("", preferences.role)
        assertEquals("", preferences.username)
        assertEquals(AppRoute.LOGIN, AppRoute.forSession(viewModel.uiState.value.session))
        recreateViewModel()
        assertNull(viewModel.uiState.value.session)
    }

    @Test
    fun employeeLoginStoresEmployeeIdAndSwitchingToAdminRemovesIt() = runBlocking {
        val result = login("emp01", "123456")
        val employee = requireNotNull(database.employeeDao().getByCode("NV01"))
        assertEquals(employee.accountId, preferences.userId)
        assertEquals(employee.id, preferences.employeeId)
        assertEquals(employee.id, result.session?.employeeId)
        assertEquals(AppRoute.EMPLOYEE_DASHBOARD, AppRoute.forSession(result.session))

        withContext(Dispatchers.Main) { viewModel.logout() }
        assertNull(login("admin", "123456").session?.employeeId)
        assertEquals(SharedPrefManager.NO_ID, preferences.employeeId)
    }

    @Test
    fun invalidCredentialsAndBlankFieldsNeverCreateSession() = runBlocking {
        assertEquals(AuthError.EMPTY_FIELDS, login(" ", "123456").error)
        assertEquals(AuthError.INVALID_CREDENTIALS, login("admin", "wrong").error)
        assertEquals(AuthError.INVALID_CREDENTIALS, login("unknown", "123456").error)
        assertEquals(AuthError.INVALID_CREDENTIALS, login("admin", "123456 ").error)
        assertFalse(preferences.isLoggedIn)
        assertNull(preferences.getSession())
    }

    @Test
    fun missingEmployeeAndUnsupportedRoleNeverCreateSession() = runBlocking {
        database.accountDao().insert(Account(username = "orphan", password = "123456", role = AccountRole.EMPLOYEE))
        database.accountDao().insert(Account(username = "other", password = "123456", role = "OTHER"))
        assertEquals(AuthError.EMPLOYEE_NOT_FOUND, login("orphan", "123456").error)
        assertEquals(AuthError.UNSUPPORTED_ROLE, login("other", "123456").error)
        assertFalse(preferences.isLoggedIn)
    }

    @Test
    fun replacingEmployeeSessionWithAdminClearsOldEmployeeId() {
        preferences.saveSession(UserSession(2L, 10L, AccountRole.EMPLOYEE, "emp01"))
        preferences.saveSession(UserSession(userId = 1L, role = AccountRole.ADMIN, username = "admin"))
        assertEquals(SharedPrefManager.NO_ID, preferences.employeeId)
        assertNull(preferences.getSession()?.employeeId)
    }

    @Test
    fun incompleteStoredSessionIsNotAccepted() {
        context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE).edit()
            .putBoolean("isLoggedIn", true)
            .putLong("userId", 2L)
            .putString("role", AccountRole.EMPLOYEE)
            .putString("username", "emp01")
            .apply()
        assertFalse(preferences.isLoggedIn)
        assertNull(preferences.getSession())
    }
}
