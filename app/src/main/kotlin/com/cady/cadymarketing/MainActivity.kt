package com.cady.cadymarketing

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cady.cadymarketing.app.AppRoot
import com.cady.cadymarketing.core.theme.CadyMarketingTheme
import com.cady.cadymarketing.domain.model.ThemeMode
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    /** The post a reminder notification asked to open; the shell opens it in the Studio and clears this. */
    private var openContentId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openContentId = intent?.getStringExtra(EXTRA_OPEN_CONTENT)
        enableEdgeToEdge()
        setContent {
            val brandColor by viewModel.brandColor.collectAsStateWithLifecycle()
            val appearance by viewModel.appearance.collectAsStateWithLifecycle()
            val look = appearance
            // The saved look is read in a few milliseconds; the window waits for it rather than
            // opening in the classic style and then jumping to the chosen one.
            if (look != null) {
                val systemDark = isSystemInDarkTheme()
                val dark = when (look.mode) {
                    ThemeMode.SYSTEM -> systemDark
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }
                // The status and navigation bar icons follow the app's own light/dark choice, not only the phone's.
                DisposableEffect(dark) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                        ) { dark },
                        navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                    )
                    onDispose { }
                }
                CadyMarketingTheme(darkTheme = dark, brandColor = brandColor, style = look.style) {
                    AppRoot(
                        openContentId = openContentId,
                        onOpenContentHandled = { openContentId = null },
                    )
                }
            }
        }
    }

    /** A notification tapped while the app is already open comes here (the activity is single-top). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openContentId = intent.getStringExtra(EXTRA_OPEN_CONTENT)
    }

    companion object {
        const val EXTRA_OPEN_CONTENT = "open_content_id"

        // The same scrims the system uses behind a three-button navigation bar.
        private val LIGHT_SCRIM = android.graphics.Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
        private val DARK_SCRIM = android.graphics.Color.argb(0x80, 0x1B, 0x1B, 0x1B)
    }
}
