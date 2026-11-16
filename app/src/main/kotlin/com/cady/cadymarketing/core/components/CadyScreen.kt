package com.cady.cadymarketing.core.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The frame every screen sits in: a centred top bar and the page colour behind it.
 *
 * @param onClose shows an X (full-screen workspaces such as the Studio).
 * @param onBack shows a back arrow (screens reached from "More").
 * @param actions icon buttons at the end of the top bar.
 * @param snackbarHost where the screen's messages appear (pass a SnackbarHost to show some).
 * @param withBottomInset true when the screen has no bottom bar under it, so its content must clear
 *        the system navigation bar itself. Tab screens leave it false: the bar already does.
 */
@Composable
fun CadyScreen(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    withBottomInset: Boolean = false,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    when {
                        onClose != null -> IconButton(onClick = onClose) {
                            Icon(Icons.Filled.Close, contentDescription = "إغلاق")
                        }
                        onBack != null -> IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                        }
                        else -> Unit
                    }
                },
                actions = actions,
            )
        },
        snackbarHost = snackbarHost,
        contentWindowInsets = if (withBottomInset) WindowInsets.navigationBars else WindowInsets(0, 0, 0, 0),
        content = content,
    )
}
