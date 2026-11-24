package com.oncillaai.tinyava.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oncillaai.tinyava.data.SettingsState
import com.oncillaai.tinyava.ui.theme.LocalAvaColors

@Composable
fun HomeScreen(
    settings: SettingsState,
    engineReady: Boolean,
    isSpeaking: Boolean,
    onOpenTtsSettings: () -> Unit,
    onQuickTest: () -> Unit,
    onOpenReader: () -> Unit,
    onOpenDictionary: () -> Unit,
) {
    val c = LocalAvaColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Accessible Engine Ready Status Banner
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = c.okBg,
            border = BorderStroke(1.5.dp, c.okLine),
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = if (engineReady) {
                        "موتور گفتار آوا آماده است. صدای پریسا، ۲۴ کیلوهرتز، صد در صد آفلاین."
                    } else {
                        "در حال بارگذاری موتور گفتار آفلاین آوا."
                    }
                },
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(c.okInk),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (engineReady) "موتور گفتار آوا آماده است" else "در حال آماده‌سازی موتور آوا…",
                        color = c.okInk,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "صدای پریسا · ۲۴ کیلوهرتز · ۴.۵ میلیون پارامتر",
                        color = c.okInk.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                    )
                }
            }
        }

        // Primary 60dp CTA: Open Android System TTS Settings for TalkBack
        Button(
            onClick = onOpenTtsSettings,
            colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.accentInk),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp),
        ) {
            Icon(Icons.Rounded.Settings, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text(
                text = "انتخاب آوا به عنوان موتور TalkBack",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        // Quick Voice Test Button (58dp)
        OutlinedButton(
            onClick = onQuickTest,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.5.dp, c.line),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = c.card, contentColor = c.ink),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.VolumeUp, contentDescription = null, tint = c.accent)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (isSpeaking) "در حال پخش صدا… (لمس برای توقف)" else "پخش تست سریع صدا",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = "پریسا · ${"%.1f".format(settings.speed)}×",
                    fontSize = 13.sp,
                    color = c.muted,
                )
            }
        }

        // Quick Action Card: Reader
        HomeActionCard(
            title = "روخوانی متن و کلیپ‌بورد",
            subtitle = "متن کپی‌شده را با یک لمس بخواند",
            icon = Icons.Rounded.Description,
            onClick = onOpenReader,
        )

        // Quick Action Card: Custom Dictionary
        HomeActionCard(
            title = "واژه‌نامه تلفظ اختصاصی",
            subtitle = "تنظیم تلفظ کلمات انگلیسی و هم‌نویسه (${settings.customDictionary.size} مورد)",
            icon = Icons.Rounded.Book,
            onClick = onOpenDictionary,
        )

        Spacer(Modifier.height(4.dp))

        // Privacy Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = c.card,
            border = BorderStroke(1.5.dp, c.line),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "وضعیت حریم خصوصی",
                    color = c.muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "همه پردازش‌ها (تبدیل متن به آوا و تولید صدا) ۱۰۰٪ داخل گوشی انجام می‌شود. هیچ متنی به اینترنت ارسال نمی‌شود.",
                    color = c.muted,
                    fontSize = 13.5.sp,
                    lineHeight = 22.sp,
                )
            }
        }
    }
}

@Composable
private fun HomeActionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    val c = LocalAvaColors.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = c.card,
        border = BorderStroke(1.5.dp, c.line),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 70.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {},
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.ink)
                Spacer(Modifier.height(2.dp))
                Text(text = subtitle, fontSize = 13.sp, color = c.muted)
            }
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.soft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = c.accent)
            }
        }
    }
}
