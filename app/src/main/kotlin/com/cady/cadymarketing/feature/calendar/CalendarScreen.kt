package com.cady.cadymarketing.feature.calendar

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cady.cadymarketing.core.components.CadyScreen
import com.cady.cadymarketing.core.components.PlaceholderContent

@Composable
fun CalendarScreen() {
    CadyScreen(title = "التقويم") { padding ->
        PlaceholderContent(
            icon = Icons.Filled.CalendarMonth,
            title = "التقويم",
            description = "شبكة شهرية بالمحتوى والحملات والمناسبات.",
            phase = 3,
            modifier = Modifier.padding(padding),
        )
    }
}
