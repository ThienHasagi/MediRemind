package com.example.employeemanagement.ui.auth

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.employeemanagement.R
import com.example.employeemanagement.data.local.AppDatabase
import com.example.employeemanagement.data.session.AccountRole
import com.example.employeemanagement.data.session.SharedPrefManager
import com.example.employeemanagement.data.session.UserSession
import com.example.employeemanagement.ui.navigation.AppNavigation
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var preferences: SharedPrefManager
    private val store = ViewModelStore()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(AppDatabase.SeedCallback()).build()
        preferences = SharedPrefManager(context, "auth_navigation_test")
        preferences.logout()
    }

    @After
    fun tearDown() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
        database.close()
        preferences.logout()
    }

    private fun showApp(): AuthViewModel {
        lateinit var viewModel: AuthViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel = ViewModelProvider(
                store, AuthViewModel.Factory(database, preferences),
            )[AuthViewModel::class.java]
        }
        composeRule.setContent { MaterialTheme { AppNavigation(viewModel, database, preferences) } }
        return viewModel
    }

    private fun enterCredentials(username: String, password: String) {
        composeRule.onNodeWithText(context.getString(R.string.username)).performTextInput(username)
        composeRule.onNodeWithText(context.getString(R.string.password)).performTextInput(password)
        // The heading also says "Đăng nhập", so select the button by its click action.
        composeRule.onNode(
            androidx.compose.ui.test.hasText(context.getString(R.string.login_action)) and
                androidx.compose.ui.test.hasClickAction(),
        ).performClick()
    }

    @Test
    fun loginAsEmployeeShowsDashboardAndLogoutReturnsToForm() {
        val viewModel = showApp()
        enterCredentials("emp01", "123456")
        composeRule.waitUntil(10_000) { viewModel.uiState.value.session != null }
        composeRule.onNodeWithText(context.getString(R.string.employee_dashboard)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.welcome_user, "emp01")).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.tab_employee_salary)).performClick()
        composeRule.onNodeWithText("Bảng lương của tôi").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.tab_attendance)).performClick()
        composeRule.onNodeWithText("Check In").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.logout)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.username)).assertIsDisplayed()
        assertFalse(preferences.isLoggedIn)
    }

    @Test
    fun wrongPasswordShowsErrorAndKeepsLoginForm() {
        val viewModel = showApp()
        enterCredentials("admin", "wrong")
        composeRule.waitUntil(10_000) { viewModel.uiState.value.error != null }
        composeRule.onNodeWithText(context.getString(R.string.error_invalid_credentials)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.username)).assertIsDisplayed()
        assertFalse(preferences.isLoggedIn)
    }

    @Test
    fun existingAdminSessionOpensDashboardDirectly() {
        preferences.saveSession(UserSession(userId = 1L, role = AccountRole.ADMIN, username = "admin"))
        showApp()
        composeRule.onNodeWithText(context.getString(R.string.admin_dashboard)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.tab_admin_salary)).performClick()
        composeRule.onNodeWithText("Tính toán lương tháng").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.tab_employees)).performClick()
        composeRule.onNodeWithText("+ Thêm nhân viên").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.logout)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.username)).assertIsDisplayed()
    }
}
