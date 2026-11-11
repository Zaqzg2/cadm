package com.cady.cadymarketing.feature.identity

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cady.cadymarketing.core.components.CadyScreen
import com.cady.cadymarketing.core.components.PlaceholderContent

@Composable
fun IdentityScreen(onBack: () -> Unit) {
    CadyScreen(title = "الهوية", onBack = onBack, withBottomInset = true) { padding ->
        PlaceholderContent(
            icon = Icons.Filled.Palette,
            title = "الهوية",
            description = "الاسم والألوان والشعار والنبرة والعبارات الممنوعة.",
            phase = 2,
            modifier = Modifier.padding(padding),
        )
    }
}
