package com.cady.cadymarketing.core.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cady.cadymarketing.core.theme.cady
import com.cady.cadymarketing.domain.model.CampaignPhase
import com.cady.cadymarketing.domain.model.ContentStatus

/** The status of a post as a small label, coloured by what it means (green: ready, amber: waiting, red: sent back). */
@Composable
fun StatusPill(status: ContentStatus, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.cady
    val (background, foreground) = when (status) {
        ContentStatus.DRAFT -> scheme.secondaryContainer to scheme.onSecondaryContainer
        ContentStatus.PENDING -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        ContentStatus.APPROVED -> extra.success.copy(alpha = 0.16f) to extra.success
        ContentStatus.PUBLISHED -> scheme.primaryContainer to scheme.onPrimaryContainer
        ContentStatus.ARCHIVED -> scheme.surfaceContainerHigh to scheme.onSurfaceVariant
        ContentStatus.RETURNED -> scheme.errorContainer to scheme.onErrorContainer
    }
    Pill(text = status.label, modifier = modifier, background = background, foreground = foreground)
}

/** Where a campaign stands as a small label. */
@Composable
fun PhasePill(phase: CampaignPhase, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.cady
    val (background, foreground) = when (phase) {
        CampaignPhase.RUNNING -> extra.success.copy(alpha = 0.16f) to extra.success
        CampaignPhase.UPCOMING -> extra.info.copy(alpha = 0.16f) to extra.info
        CampaignPhase.OVERDUE -> scheme.errorContainer to scheme.onErrorContainer
        CampaignPhase.PAUSED -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        CampaignPhase.ENDED -> scheme.surfaceContainerHigh to scheme.onSurfaceVariant
    }
    Pill(text = phase.label, modifier = modifier, background = background, foreground = foreground)
}
