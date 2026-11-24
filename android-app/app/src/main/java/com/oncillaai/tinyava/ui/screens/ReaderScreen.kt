package com.oncillaai.tinyava.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oncillaai.tinyava.ui.theme.LocalAvaColors

@Composable
fun ReaderScreen(
    text: String,
    onTextChange: (String) -> Unit,
    sentences: List<String>,
    activeSentenceIndex: Int,
    isSpeaking: Boolean,
    statusMessage: String,
    onPasteAndRead: () -> Unit,
    onPlayOrStop: () -> Unit,
    onPrevSentence: () -> Unit,
    onNextSentence: () -> Unit,
    onSaveWav: () -> Unit,
) {
    val c = LocalAvaColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Huge 1-tap Clipboard Paste & Read CTA (huge win for blind users!)
        Button(
            onClick = onPasteAndRead,
            colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.accentInk),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp),
        ) {
            Icon(Icons.Rounded.ContentPaste, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text("چسباندن از کلیپ‌بورد و خواندن", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        // Editable RTL Persian Text Input
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            label = { Text("متن فارسی برای روخوانی") },
            textStyle = TextStyle(fontSize = 18.sp, lineHeight = 32.sp, color = c.ink),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = c.card,
                unfocusedContainerColor = c.card,
                focusedBorderColor = c.accent,
                unfocusedBorderColor = c.line,
                focusedTextColor = c.ink,
                unfocusedTextColor = c.ink,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp),
        )

        // Active sentence highlight card for low-vision users
        if (isSpeaking && activeSentenceIndex in sentences.indices) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = c.soft,
                border = BorderStroke(1.5.dp, c.accent),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "جمله ${activeSentenceIndex + 1} از ${sentences.size}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = c.accent,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = sentences[activeSentenceIndex],
                        fontSize = 18.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = c.ink,
                    )
                }
            }
        }

        Text(
            text = "نکته: با وارد کردن اعراب (ـَ ـِ ـُ) می‌توانید تلفظ دقیق کلمات و کسره اضافه را کنترل کنید.",
            fontSize = 13.sp,
            color = c.muted,
        )

        if (statusMessage.isNotBlank()) {
            Text(
                text = statusMessage,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = c.accent,
            )
        }

        Spacer(Modifier.height(4.dp))

        // Large Accessible Transport Bar (Prev / Play-Stop / Next)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onPrevSentence,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, c.line),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = c.card, contentColor = c.ink),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 60.dp)
                    .semantics { contentDescription = "جمله قبلی" },
            ) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = null)
            }

            Button(
                onClick = onPlayOrStop,
                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.accentInk),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(2f)
                    .heightIn(min = 60.dp),
            ) {
                Icon(
                    if (isSpeaking) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isSpeaking) "توقف" else "پخش متن",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            OutlinedButton(
                onClick = onNextSentence,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, c.line),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = c.card, contentColor = c.ink),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 60.dp)
                    .semantics { contentDescription = "جمله بعدی" },
            ) {
                Icon(Icons.Rounded.SkipNext, contentDescription = null)
            }
        }

        // Save WAV button
        OutlinedButton(
            onClick = onSaveWav,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.5.dp, c.line),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = c.card, contentColor = c.ink),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        ) {
            Icon(Icons.Rounded.Save, contentDescription = null, tint = c.accent)
            Spacer(Modifier.width(8.dp))
            Text("ذخیره فایل صوتی (WAV)", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}
