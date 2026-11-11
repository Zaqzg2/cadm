package com.cady.cadymarketing.feature.reports

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cady.cadymarketing.core.components.CadyScreen
import com.cady.cadymarketing.core.components.PlaceholderContent

@Composable
fun ReportsScreen(onBack: () -> Unit) {
    CadyScreen(title = "التقارير", onBack = onBack, withBottomInset = true) { padding ->
        PlaceholderContent(
            icon = Icons.Filled.BarChart,
            title = "التقارير",
            description = "نتائج الحملات يدويًا وتلقائيًا من طلبات الموقع بربط رمز الحملة.",
            phase = 4,
            modifier = Modifier.padding(padding),
        )
    }
}
