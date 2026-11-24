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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oncillaai.tinyava.data.AppThemeMode
import com.oncillaai.tinyava.data.SettingsState
import com.oncillaai.tinyava.ui.theme.LocalAvaColors
import kotlin.math.abs

@Composable
fun VoiceSettingsScreen(
    settings: SettingsState,
    onSpeedChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
    onThemeChange: (AppThemeMode) -> Unit,
    onToggleNumbers: (Boolean) -> Unit,
    onToggleEnglish: (Boolean) -> Unit,
    onToggleCache: (Boolean) -> Unit,
    onPlaySample: () -> Unit,
) {
    val c = LocalAvaColors.current
    val presets = listOf(1.0f, 1.5f, 2.0f, 2.5f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Speed card with TalkBack fast presets (1.0x .. 2.5x)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = c.card,
            border = BorderStroke(1.5.dp, c.line),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("سرعت گفتار", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.ink)
                    Text("${"%.2f".format(settings.speed)}×", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.accent)
                }
                Slider(
                    value = settings.speed,
                    onValueChange = onSpeedChange,
                    valueRange = 0.7f..2.5f,
                    colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (p in presets) {
                        val selected = abs(settings.speed - p) < 0.06f
                        Button(
                            onClick = { onSpeedChange(p) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) c.ink else c.soft,
                                contentColor = if (selected) c.card else c.ink,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                        ) {
                            Text("${"%.1f".format(p)}×", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Pitch card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = c.card,
            border = BorderStroke(1.5.dp, c.line),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("زیر و بمی صدا (Pitch)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.ink)
                    val sign = if (settings.pitchSemitones > 0f) "+" else ""
                    Text(
                        "$sign${"%.1f".format(settings.pitchSemitones)} st",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = c.accent,
                    )
                }
                Slider(
                    value = settings.pitchSemitones,
                    onValueChange = onPitchChange,
                    valueRange = -4.0f..4.0f,
                    steps = 15,
                    colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent),
                )
            }
        }

        // Theme selector (Light / Dark / High Contrast for low-vision users)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = c.card,
            border = BorderStroke(1.5.dp, c.line),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("ظاهر و کنتراست نمایش", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.ink)
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (mode in AppThemeMode.entries) {
                        val selected = settings.themeMode == mode
                        Button(
                            onClick = { onThemeChange(mode) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) c.accent else c.soft,
                                contentColor = if (selected) c.accentInk else c.ink,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                        ) {
                            Text(mode.labelFa, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // TalkBack speech toggles
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = c.card,
            border = BorderStroke(1.5.dp, c.line),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                AccessibleToggleRow(
                    title = "خواندن اعداد و ساعت فارسی",
                    subtitle = "تبدیل ۱۲:۳۰ و ۸۵٪ به حروف",
                    checked = settings.convertNumbers,
                    onCheckedChange = onToggleNumbers,
                )
                HorizontalDivider(color = c.line)
                AccessibleToggleRow(
                    title = "تلفظ کلمات انگلیسی رایج",
                    subtitle = "Wi-Fi، WhatsApp، Bluetooth و…",
                    checked = settings.convertEnglishUi,
                    onCheckedChange = onToggleEnglish,
                )
                HorizontalDivider(color = c.line)
                AccessibleToggleRow(
                    title = "حافظه فوری منوها (Cache)",
                    subtitle = "پخش زیر ۵ میلی‌ثانیه برای دکمه‌های تکراری",
                    checked = settings.enablePhraseCache,
                    onCheckedChange = onToggleCache,
                )
            }
        }

        // Test sample CTA
        Button(
            onClick = onPlaySample,
            colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.accentInk),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp),
        ) {
            Icon(Icons.Rounded.VolumeUp, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text("پخش جمله آزمایشی با تنظیمات فعلی", fontSize = 15.5.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AccessibleToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val c = LocalAvaColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .padding(vertical = 8.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = c.ink)
            Text(subtitle, fontSize = 12.5.sp, color = c.muted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = c.accentInk,
                checkedTrackColor = c.accent,
            ),
        )
    }
}
