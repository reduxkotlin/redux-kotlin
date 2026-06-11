package org.reduxkotlin.devtools.inapp

import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import org.reduxkotlin.devtools.ui.DevToolsTab
import kotlin.test.Test

/**
 * Guards the release no-op host's one job: rendering the app content. A regression that drops
 * `content()` from the no-op `ReduxDevToolsHost` would blank every release build that wraps its
 * root in the host — and no debug-side test would ever see it.
 */
class ReduxDevToolsHostNoOpRenderTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun noop_host_renders_the_app_content() = runComposeUiTest {
        setContent {
            ReduxDevToolsHost { BasicText("app content") }
        }
        onNodeWithText("app content").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun noop_host_renders_content_with_an_explicit_config() = runComposeUiTest {
        setContent {
            ReduxDevToolsHost(InAppConfig(startTab = DevToolsTab.STATE)) { BasicText("configured content") }
        }
        onNodeWithText("configured content").assertIsDisplayed()
    }
}
