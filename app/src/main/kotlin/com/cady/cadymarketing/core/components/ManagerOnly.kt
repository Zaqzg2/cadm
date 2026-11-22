package com.cady.cadymarketing.core.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** What a marketer sees where a screen is the manager's: one sentence, and the way back. */
@Composable
fun ManagerOnlyScreen(title: String, onBack: () -> Unit) {
    CadyScreen(title = title, onBack = onBack, withBottomInset = true) { padding ->
        EmptyState(
            icon = Icons.Filled.Lock,
            title = "للمدير فقط",
            message = "هذه الشاشة متاحة لحساب المدير. اطلب من المدير ما تحتاجه منها.",
            modifier = Modifier.padding(padding),
        )
    }
}
