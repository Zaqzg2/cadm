package com.cady.cadymarketing

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cady.cadymarketing.app.AppRoot
import com.cady.cadymarketing.core.theme.CadyMarketingTheme
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
            val uiStyle by viewModel.uiStyle.collectAsStateWithLifecycle()
            CadyMarketingTheme(brandColor = brandColor, style = uiStyle) {
                AppRoot(
                    openContentId = openContentId,
                    onOpenContentHandled = { openContentId = null },
                )
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
    }
}
