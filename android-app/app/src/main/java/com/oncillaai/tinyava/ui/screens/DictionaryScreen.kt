package com.oncillaai.tinyava.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
    onDelete: (source: String) -> Unit,
    onTestSpeak: (String) -> Unit,
) {
    val c = LocalAvaColors.current
    var showDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = { showDialog = true },
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
                        .heightIn(min = 64.dp),
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
                                .size(48.dp)
                                .semantics { contentDescription = "تست تلفظ ${item.replacement}" },
                        ) {
                            Icon(Icons.Rounded.VolumeUp, contentDescription = null, tint = c.accent)
                        }
                        IconButton(
                            onClick = { onDelete(item.source) },
                            modifier = Modifier
                                .size(48.dp)
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
                text = "هر کلمه‌ای که اینجا اضافه کنید هم در TalkBack و هم در بخش روخوانی متن اعمال می‌شود.",
                fontSize = 13.sp,
                color = c.muted,
                modifier = Modifier.padding(14.dp),
            )
        }
    }

    if (showDialog) {
        var src by remember { mutableStateOf("") }
        var dst by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("افزودن تلفظ دلخواه", fontWeight = FontWeight.Bold) },
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
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (src.isNotBlank() && dst.isNotBlank()) {
                            onAdd(src, dst, "دلخواه")
                            showDialog = false
                        }
                    },
                ) { Text("ذخیره") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("انصراف") }
            },
        )
    }
}
