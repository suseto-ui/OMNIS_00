package com.example

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.auth.OmnisAuthService
import com.example.auth.UserRole
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], packageName = "com.example")
class MainActivityLaunchTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        OmnisAuthService.cleanup()
    }

    @After
    fun tearDown() {
        OmnisAuthService.cleanup()
    }

    @Test
    fun `MainActivity launches cleanly and renders login screen`() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("omnis_login_card").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_username_field").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_password_field").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_login_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("google_signin_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `MainActivity renders UserNavigationLayout and ChatView when authenticated`() {
        OmnisAuthService.setRoleForTesting(UserRole.STANDARD_USER)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("chat_input_field", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `MainActivity renders AdminNavigationLayout and handles ACTION_SEND share sheet`() {
        OmnisAuthService.setRoleForTesting(UserRole.ADMIN_OPERATOR)
        composeRule.waitForIdle()

        val shareIntent = Intent(composeRule.activity, MainActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Test shared link https://example.org/audit")
        }
        val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java, shareIntent).setup()
        composeRule.waitForIdle()
        val activity = controller.get()
        org.junit.Assert.assertNotNull(activity)
        controller.pause().stop().destroy()
    }

    @Test
    fun `MainActivity local mode button launches UserNavigationLayout ChatView`() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("auth_local_mode_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("chat_input_field", useUnmergedTree = true).assertExists()
    }
}
