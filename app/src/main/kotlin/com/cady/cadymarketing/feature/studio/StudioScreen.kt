package com.cady.cadymarketing.feature.studio

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cady.cadymarketing.core.components.CadyScreen
import com.cady.cadymarketing.core.components.PlaceholderContent

@Composable
fun StudioScreen(onClose: () -> Unit) {
    CadyScreen(title = "الاستوديو", onClose = onClose, withBottomInset = true) { padding ->
        PlaceholderContent(
            icon = Icons.Filled.Brush,
            title = "الاستوديو",
            description = "صمّم الملصق واكتب النص وافحصه وشاركه بنقرة. شاشة كاملة بلا شريط سفلي، وزر ✕ يعيدك.",
            phase = 1,
            modifier = Modifier.padding(padding),
        )
    }
}
