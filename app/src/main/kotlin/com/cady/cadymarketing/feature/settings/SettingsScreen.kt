package com.cady.cadymarketing.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cady.cadymarketing.core.components.CadyScreen
import com.cady.cadymarketing.core.components.PlaceholderContent

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    CadyScreen(title = "الفريق والإعدادات", onBack = onBack, withBottomInset = true) { padding ->
        PlaceholderContent(
            icon = Icons.Filled.Settings,
            title = "الفريق والإعدادات",
            description = "الدور، والبوابة مع اختبار الاتصال، والنسخ الاحتياطي، وسجل العمليات.",
            phase = 5,
            modifier = Modifier.padding(padding),
        )
    }
}
