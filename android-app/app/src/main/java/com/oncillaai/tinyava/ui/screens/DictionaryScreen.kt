package com.oncillaai.tinyava.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oncillaai.tinyava.data.DictEntry
import com.oncillaai.tinyava.ui.theme.LocalAvaColors

@Composable
fun DictionaryScreen(
    entries: List<DictEntry>,
    onAdd: (source: String, replacement: String, note: String) -> Unit,
    onEdit: (oldSource: String, newSource: String, newReplacement: String, note: String) -> Unit,
    onDelete: (source: String) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onTestSpeak: (String) -> Unit,
) {
    val c = LocalAvaColors.current
    var showAddDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<DictEntry?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = { showAddDialog = true },
            colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.accentInk),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("افزودن کلمه جدید", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = onExport,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, c.line),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 50.dp)
                    .semantics { contentDescription = "خروجی گرفتن از واژه‌نامه به صورت فایل JSON" },
            ) {
                Icon(Icons.Rounded.Upload, contentDescription = null, tint = c.ink)
                Spacer(Modifier.width(6.dp))
                Text("خروجی (Export)", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = c.ink)
            }
            OutlinedButton(
                onClick = onImport,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, c.line),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 50.dp)
                    .semantics { contentDescription = "وارد کردن واژه‌نامه از فایل JSON یا متنی" },
            ) {
                Icon(Icons.Rounded.Download, contentDescription = null, tint = c.ink)
                Spacer(Modifier.width(6.dp))
                Text("ورودی (Import)", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = c.ink)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(entries, key = { it.source }) { item ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = c.card,
                    border = BorderStroke(1.5.dp, c.line),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .clickable { editingEntry = item },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${item.source}  ←  ${item.replacement}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = c.ink,
                            )
                            if (item.note.isNotBlank()) {
                                Text(item.note, fontSize = 12.5.sp, color = c.muted)
                            }
                        }
                        IconButton(
                            onClick = { onTestSpeak(item.replacement) },
                            modifier = Modifier
                                .size(44.dp)
                                .semantics { contentDescription = "تست تلفظ ${item.replacement}" },
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, tint = c.accent)
                        }
                        IconButton(
                            onClick = { editingEntry = item },
                            modifier = Modifier
                                .size(44.dp)
                                .semantics { contentDescription = "ویرایش ${item.source}" },
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = null, tint = c.ink)
                        }
                        IconButton(
                            onClick = { onDelete(item.source) },
                            modifier = Modifier
                                .size(44.dp)
                                .semantics { contentDescription = "حذف ${item.source}" },
                        ) {
                            Icon(Icons.Rounded.Delete, contentDescription = null, tint = c.muted)
                        }
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = c.card,
            border = BorderStroke(1.5.dp, c.line),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "هر کلمه‌ای که اینجا اضافه یا ویرایش کنید هم در TalkBack و هم در بخش روخوانی متن اعمال می‌شود.",
                fontSize = 13.sp,
                color = c.muted,
                modifier = Modifier.padding(14.dp),
            )
        }
    }

    if (showAddDialog) {
        DictEntryDialog(
            title = "افزودن تلفظ دلخواه",
            initialSource = "",
            initialReplacement = "",
            initialNote = "",
            onTestSpeak = onTestSpeak,
            onDismiss = { showAddDialog = false },
            onSave = { s, r, n ->
                onAdd(s, r, n)
                showAddDialog = false
            },
        )
    }

    editingEntry?.let { entry ->
        DictEntryDialog(
            title = "ویرایش تلفظ دلخواه",
            initialSource = entry.source,
            initialReplacement = entry.replacement,
            initialNote = entry.note,
            onTestSpeak = onTestSpeak,
            onDismiss = { editingEntry = null },
            onSave = { s, r, n ->
                onEdit(entry.source, s, r, n)
                editingEntry = null
            },
        )
    }
}

@Composable
private fun DictEntryDialog(
    title: String,
    initialSource: String,
    initialReplacement: String,
    initialNote: String,
    onTestSpeak: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (source: String, replacement: String, note: String) -> Unit,
) {
    var src by remember(initialSource) { mutableStateOf(initialSource) }
    var dst by remember(initialReplacement) { mutableStateOf(initialReplacement) }
    var note by remember(initialNote) { mutableStateOf(initialNote) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = src,
                    onValueChange = { src = it },
                    label = { Text("کلمه اصلی (مثلاً تست یا Wi-Fi)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = dst,
                    onValueChange = { dst = it },
                    label = { Text("تلفظ دلخواه (مثلاً تُست یا وای‌فای)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("توضیح کوتاه (اختیاری)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (dst.isNotBlank()) {
                    OutlinedButton(
                        onClick = { onTestSpeak(dst.trim()) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("تست تلفظ قبل از ذخیره")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (src.isNotBlank() && dst.isNotBlank()) {
                        onSave(src, dst, note)
                    }
                },
            ) { Text("ذخیره") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        },
    )
}
