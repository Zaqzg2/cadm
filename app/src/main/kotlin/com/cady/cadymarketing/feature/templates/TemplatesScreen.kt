package com.cady.cadymarketing.feature.templates

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cady.cadymarketing.core.components.CadyScreen
import com.cady.cadymarketing.core.components.PlaceholderContent

@Composable
fun TemplatesScreen(onBack: () -> Unit) {
    CadyScreen(title = "القوالب والمفردات", onBack = onBack, withBottomInset = true) { padding ->
        PlaceholderContent(
            icon = Icons.Filled.Description,
            title = "القوالب والمفردات",
            description = "تحرير بنك المفردات والصيغ. الافتراضي مُضمَّن في التطبيق وتعدّله فوقه.",
            phase = 2,
            modifier = Modifier.padding(padding),
        )
    }
}
