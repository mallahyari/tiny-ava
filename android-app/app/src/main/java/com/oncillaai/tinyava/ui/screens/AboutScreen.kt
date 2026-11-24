package com.oncillaai.tinyava.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oncillaai.tinyava.BuildConfig
import com.oncillaai.tinyava.ui.theme.LocalAvaColors

@Composable
fun AboutScreen() {
    val c = LocalAvaColors.current
    val ctx = LocalContext.current

    fun openUri(url: String) {
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = c.card,
            border = BorderStroke(1.5.dp, c.line),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("آوا · tiny-ava", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = c.accent)
                Spacer(Modifier.height(4.dp))
                Text(
                    "ساخته‌شده توسط مهدی اله یاری (OncillaAI)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.ink,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "تبدیل متن به گفتار فارسی سریع، سبک و ۱۰۰٪ آفلاین؛ طراحی‌شده با تمرکز ویژه بر دسترس‌پذیری و کاربران نابینا و کم‌بینا.",
                    fontSize = 13.5.sp,
                    lineHeight = 22.sp,
                    color = c.muted,
                    textAlign = TextAlign.Center,
                )
            }
        }

        AboutLinkRow("وب‌سایت OncillaAI", "oncillaai.com") { openUri("https://oncillaai.com") }
        AboutLinkRow("صفحه مدل در Hugging Face", "huggingface.co/mehdi-hf/tiny-ava") {
            openUri("https://huggingface.co/mehdi-hf/tiny-ava")
        }
        AboutLinkRow("گزارش تلفظ اشتباه یا پیشنهاد صدا", "mehdi@oncillaai.com") {
            openUri("mailto:mehdi@oncillaai.com?subject=tiny-ava%20Android%20Feedback")
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = c.card,
            border = BorderStroke(1.5.dp, c.line),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("مشخصات مدل و مجوز", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.muted)
                Text("• نسخه برنامه: ${BuildConfig.VERSION_NAME}", fontSize = 13.5.sp, color = c.ink)
                Text("• موتور صدا: tiny-ava (پریسا، ۴.۵M، ۲۴kHz) — CC BY 4.0", fontSize = 13.5.sp, color = c.ink)
                Text("• تبدیل خط به آوا: persian-g2p-tiny (۸.۳M) — MIT", fontSize = 13.5.sp, color = c.ink)
                Text("• کد برنامه: Apache-2.0 (مبتنی بر معماری tiny-voca)", fontSize = 13.5.sp, color = c.ink)
                Text("• حریم خصوصی: ۱۰۰٪ آفلاین، بدون جمع‌آوری هیچ‌گونه داده", fontSize = 13.5.sp, color = c.ink)
            }
        }
    }
}

@Composable
private fun AboutLinkRow(title: String, subtitle: String, onClick: () -> Unit) {
    val c = LocalAvaColors.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = c.card,
        border = BorderStroke(1.5.dp, c.line),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.ink)
                Text(subtitle, fontSize = 12.5.sp, color = c.muted)
            }
            Icon(Icons.Rounded.OpenInNew, contentDescription = null, tint = c.accent)
        }
    }
}
