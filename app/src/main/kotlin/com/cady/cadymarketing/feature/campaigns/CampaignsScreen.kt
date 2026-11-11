package com.cady.cadymarketing.feature.campaigns

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cady.cadymarketing.core.components.CadyScreen
import com.cady.cadymarketing.core.components.PlaceholderContent

@Composable
fun CampaignsScreen() {
    CadyScreen(title = "الحملات") { padding ->
        PlaceholderContent(
            icon = Icons.Filled.Campaign,
            title = "الحملات",
            description = "حملة لكل مستند، برمز وروابط تتبّع ورمز QR يُولَّد على الجهاز.",
            phase = 3,
            modifier = Modifier.padding(padding),
        )
    }
}
