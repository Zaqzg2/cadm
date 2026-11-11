package com.cady.cadymarketing

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cady.cadymarketing.app.AppRoot
import com.cady.cadymarketing.core.theme.CadyMarketingTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CadyMarketingTheme {
                AppRoot()
            }
        }
    }
}
