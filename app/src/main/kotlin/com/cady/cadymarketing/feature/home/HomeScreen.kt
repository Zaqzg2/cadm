package com.cady.cadymarketing.feature.home

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cady.cadymarketing.core.components.CadyScreen
import com.cady.cadymarketing.core.components.PlaceholderContent

@Composable
fun HomeScreen() {
    CadyScreen(title = "الرئيسية") { padding ->
        PlaceholderContent(
            icon = Icons.Filled.Home,
            title = "الرئيسية",
            description = "مؤشرات الحملات والتنبيهات وفكرة اليوم.",
            phase = 3,
            modifier = Modifier.padding(padding),
        )
    }
}
