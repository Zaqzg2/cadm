package com.cady.cadymarketing.core.role

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.cady.cadymarketing.domain.engine.Feature
import com.cady.cadymarketing.domain.engine.Permissions
import com.cady.cadymarketing.domain.model.MktRole

/**
 * The role of the person using the app, for every screen under the shell (the shell is only shown once the role is known).
 * Outside a shell nobody has a role, and so no access.
 */
val LocalRole = staticCompositionLocalOf { MktRole.NONE }

/** True when the person's role may use [feature]. */
@Composable
fun canUse(feature: Feature): Boolean = Permissions.can(LocalRole.current, feature)
