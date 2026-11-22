package com.cady.cadymarketing.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cady.cadymarketing.core.components.CadyCard
import com.cady.cadymarketing.core.components.CadyScreen
import com.cady.cadymarketing.core.components.Pill
import com.cady.cadymarketing.domain.engine.TeamRules
import com.cady.cadymarketing.domain.model.TeamMember

/**
 * فريق التسويق (the manager's). The manager adds marketers with a user name and a password; each marketer writes posts and
 * sends them for approval. A marketer can be switched off at any time: she loses access at her next opening.
 */
@Composable
fun TeamScreen(
    onBack: () -> Unit,
    viewModel: TeamViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }

    CadyScreen(
        title = "فريق التسويق",
        onBack = onBack,
        withBottomInset = true,
        actions = {
            IconButton(onClick = viewModel::refresh, enabled = !state.loading) {
                Icon(Icons.Filled.Refresh, contentDescription = "تحديث")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                CadyCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("كيف يعمل الفريق", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "أنت المدير (حساب كادي). المسوّق يكتب المنشورات ويرسلها لاعتمادك، ولا يرى الجمهور ولا التقارير ولا الهوية. يدخل باسم المستخدم وكلمة المرور اللذين تضعهما له.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) { Text("إضافة مسوّق") }
                    }
                }
            }
            state.justAdded?.let { username ->
                item {
                    CadyCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("أُضيف المسوّق", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "يدخل في كادي للتسويق باسم المستخدم «$username» (أو $username@${TeamRules.LOGIN_DOMAIN}) وبكلمة المرور التي كتبتها. أعطه إياهما.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            TextButton(onClick = viewModel::dismissAdded) { Text("حسنًا") }
                        }
                    }
                }
            }
            state.error?.let { message ->
                item { Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
            if (state.loading) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                    }
                }
            } else if (state.members.isEmpty() && state.error == null) {
                item {
                    Text(
                        "لا مسوّقين بعد. اضغط «إضافة مسوّق».",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.members, key = { it.uid }) { member ->
                MemberCard(
                    member = member,
                    busy = state.busyUid == member.uid,
                    onActive = { active -> viewModel.setActive(member, active) },
                )
            }
        }
    }

    if (showAdd) {
        AddMarketerDialog(
            adding = state.adding,
            error = state.addError,
            onAdd = { name, username, password -> viewModel.add(name, username, password) { showAdd = false } },
            onDismiss = { showAdd = false },
        )
    }
}

@Composable
private fun MemberCard(member: TeamMember, busy: Boolean, onActive: (Boolean) -> Unit) {
    CadyCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(member.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    member.username,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Pill(text = if (member.active) "نشط" else "موقوف")
            }
            Spacer(Modifier.width(8.dp))
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.padding(4.dp))
            } else {
                Switch(checked = member.active, onCheckedChange = onActive)
            }
        }
    }
}

@Composable
private fun AddMarketerDialog(
    adding: Boolean,
    error: String?,
    onAdd: (String, String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val ltr = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr)
    AlertDialog(
        onDismissRequest = { if (!adding) onDismiss() },
        title = { Text("إضافة مسوّق") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("الاسم") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("اسم المستخدم (إنجليزي)") },
                    textStyle = ltr,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("كلمة المرور (6 أحرف على الأقل)") },
                    textStyle = ltr,
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (adding) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                        Text("جارٍ إنشاء الحساب…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(name, username, password) }, enabled = !adding) { Text("إضافة") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !adding) { Text("إلغاء") }
        },
    )
}
