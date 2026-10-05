package com.jsnu.laundry

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jsnu.laundry.ui.SettingScreen
import com.jsnu.laundry.ui.ThemeSelector
import com.jsnu.laundry.ui.theme.JSNULaundryTheme
import com.jsnu.laundry.viewmodel.LaundryViewModel
import org.junit.Assert.assertEquals
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 设置页交互验证：复现「设置里的选项点不出来」问题。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi", application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingInteractionTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `主题选择器点击_onClick触发`() {
        var clicked = false
        composeRule.setContent {
            JSNULaundryTheme {
                ThemeSelector(mode = 0, onChange = { clicked = true })
            }
        }
        composeRule.onNodeWithText("浅色").performClick()
        composeRule.waitForIdle()
        assertTrue("ThemeSelector 的 onClick 未触发", clicked)
    }

    @Test
    fun `整页点击主题浅色_themeMode变为1`() {
        val vm = LaundryViewModel(ApplicationProvider.getApplicationContext())
        composeRule.setContent {
            JSNULaundryTheme {
                SettingScreen(viewModel = vm, onBack = {})
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("浅色").performClick()
        composeRule.waitUntil(10_000) { vm.uiState.value.pref.themeMode == 1 }
    }
}

/** DataStore 链路：绕过 Main looper，直接用 runBlocking 验证读写 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PrefsStoreLinkTest {

    @Test
    fun `setThemeMode写入后prefs流读回`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = com.jsnu.laundry.data.prefs.PrefsStore(ctx)
        kotlinx.coroutines.runBlocking {
            store.setThemeMode(2)
            val pref = store.prefs.first()
            assertEquals(2, pref.themeMode)
            store.setThemeMode(0)
        }
    }

    @Test
    fun `toggleTracked写入后读回`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = com.jsnu.laundry.data.prefs.PrefsStore(ctx)
        kotlinx.coroutines.runBlocking {
            store.toggleTracked(42L)
            val pref = store.prefs.first()
            assertEquals(setOf("42"), pref.tracked)
            store.toggleTracked(42L)
        }
    }
}
