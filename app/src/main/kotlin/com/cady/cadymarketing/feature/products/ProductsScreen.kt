package com.cady.cadymarketing.feature.products

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cady.cadymarketing.core.components.CadyScreen
import com.cady.cadymarketing.core.components.PlaceholderContent

@Composable
fun ProductsScreen(onBack: () -> Unit) {
    CadyScreen(title = "الأصناف والعروض", onBack = onBack, withBottomInset = true) { padding ->
        PlaceholderContent(
            icon = Icons.Filled.Inventory2,
            title = "الأصناف والعروض",
            description = "الأصناف والعروض من كادي، مع ربط صورة محلية بكل صنف.",
            phase = 2,
            modifier = Modifier.padding(padding),
        )
    }
}
