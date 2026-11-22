package com.cady.cadymarketing.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** The moment between signing in and knowing the role (a saved role shows at once; this is only the very first sign-in). */
@Composable
fun RoleLoadingScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(14.dp))
        Text("جارٍ التحقق من صلاحيتك…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The role could not be found out and none was saved (no connection on the first sign-in). */
@Composable
fun RoleFailedScreen(message: String, onRetry: () -> Unit, onSignOut: () -> Unit) {
    RoleMessage(
        icon = Icons.Filled.WifiOff,
        title = "تعذّر التحقق من صلاحيتك",
        message = "$message\nتحتاج إلى اتصال بالإنترنت في أول دخول فقط.",
        retryLabel = "إعادة المحاولة",
        onRetry = onRetry,
        onSignOut = onSignOut,
    )
}

/** Signed in, but neither a Kady manager nor on the marketing team. */
@Composable
fun NoAccessScreen(email: String?, onRetry: () -> Unit, onSignOut: () -> Unit) {
    RoleMessage(
        icon = Icons.Filled.Lock,
        title = "لا تملك صلاحية استخدام كادي للتسويق",
        message = (if (email != null) "الحساب $email ليس" else "هذا الحساب ليس") +
            " مديرًا في كادي ولا مضافًا إلى فريق التسويق. اطلب من المدير إضافتك مسوّقًا، ثم اضغط «إعادة التحقق».",
        retryLabel = "إعادة التحقق",
        onRetry = onRetry,
        onSignOut = onSignOut,
    )
}

@Composable
private fun RoleMessage(
    icon: ImageVector,
    title: String,
    message: String,
    retryLabel: String,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text(retryLabel) }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("تسجيل الخروج") }
    }
}
