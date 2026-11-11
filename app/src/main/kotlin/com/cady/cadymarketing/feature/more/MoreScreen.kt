package com.cady.cadymarketing.feature.more

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cady.cadymarketing.BuildConfig
import com.cady.cadymarketing.core.components.CadyScreen
import com.cady.cadymarketing.core.navigation.Routes

private data class MoreEntry(
    val route: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
)

private val moreEntries = listOf(
    MoreEntry(Routes.PRODUCTS, "الأصناف والعروض", "الأصناف والعروض من كادي مع صورة محلية لكل صنف", Icons.Filled.Inventory2),
    MoreEntry(Routes.IDENTITY, "الهوية", "الاسم والألوان والشعار والنبرة والعبارات الممنوعة", Icons.Filled.Palette),
    MoreEntry(Routes.TEMPLATES, "القوالب والمفردات", "بنك المفردات والصيغ الجاهزة للتحرير", Icons.Filled.Description),
    MoreEntry(Routes.LIBRARY, "المكتبة", "كل المحتوى مع بحث وتصفية بالحالة", Icons.Filled.PhotoLibrary),
    MoreEntry(Routes.AUDIENCE, "الجمهور", "شرائح العملاء وقائمة الإرسال", Icons.Filled.Groups),
    MoreEntry(Routes.REPORTS, "التقارير", "نتائج الحملات", Icons.Filled.BarChart),
    MoreEntry(Routes.SETTINGS, "الفريق والإعدادات", "الدور والبوابة والنسخ الاحتياطي وسجل العمليات", Icons.Filled.Settings),
)

/**
 * The door to every secondary screen, plus the account. [email] is null in preview mode (no Firebase).
 */
@Composable
fun MoreScreen(
    email: String?,
    onNavigate: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    var showSignOutDialog by remember { mutableStateOf(false) }

    CadyScreen(title = "المزيد") { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { AccountCard(email = email) }
            items(moreEntries) { entry ->
                MoreEntryCard(entry = entry, onClick = { onNavigate(entry.route) })
            }
            item {
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = { showSignOutDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (email != null) "تسجيل الخروج" else "الخروج من المعاينة")
                }
            }
            item {
                Text(
                    text = "كادي للتسويق · الإصدار ${BuildConfig.VERSION_NAME}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("تسجيل الخروج") },
            text = {
                Text(
                    if (email != null) {
                        "تحتاج إلى اتصال بالإنترنت لتسجيل الدخول مرة أخرى."
                    } else {
                        "ستعود إلى شاشة إعداد Firebase."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutDialog = false
                        onSignOut()
                    },
                ) { Text("خروج") }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) { Text("إلغاء") }
            },
        )
    }
}

@Composable
private fun AccountCard(email: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(icon = Icons.Filled.Person)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = if (email != null) "الحساب" else "وضع المعاينة",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = email ?: "Firebase غير مربوط، فلا يوجد حساب مسجّل",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun MoreEntryCard(entry: MoreEntry, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(icon = entry.icon)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = entry.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = entry.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun IconBadge(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}
