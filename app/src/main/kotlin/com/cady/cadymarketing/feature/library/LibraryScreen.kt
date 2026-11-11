package com.cady.cadymarketing.feature.library

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cady.cadymarketing.core.components.CadyScreen
import com.cady.cadymarketing.core.components.PlaceholderContent

@Composable
fun LibraryScreen(onBack: () -> Unit) {
    CadyScreen(title = "المكتبة", onBack = onBack, withBottomInset = true) { padding ->
        PlaceholderContent(
            icon = Icons.Filled.PhotoLibrary,
            title = "المكتبة",
            description = "بحث وتصفية المحتوى بالحالة: مسودة، بانتظار الموافقة، معتمد، منشور، مؤرشف.",
            phase = 3,
            modifier = Modifier.padding(padding),
        )
    }
}
