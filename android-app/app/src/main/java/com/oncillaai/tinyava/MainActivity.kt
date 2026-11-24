package com.oncillaai.tinyava

import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oncillaai.tinyava.data.SettingsState
import com.oncillaai.tinyava.engine.PersianTextNormalizer
import com.oncillaai.tinyava.engine.SimpleAudioPlayer
import com.oncillaai.tinyava.engine.TinyAvaEngine
import com.oncillaai.tinyava.ui.screens.AboutScreen
import com.oncillaai.tinyava.ui.screens.DictionaryScreen
import com.oncillaai.tinyava.ui.screens.HomeScreen
import com.oncillaai.tinyava.ui.screens.ReaderScreen
import com.oncillaai.tinyava.ui.screens.VoiceSettingsScreen
import com.oncillaai.tinyava.ui.theme.LocalAvaColors
import com.oncillaai.tinyava.ui.theme.TinyAvaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

private enum class Tab(val label: String) {
    HOME("خانه"),
    READER("روخوانی"),
    VOICE("صدا"),
    DICT("واژه‌نامه"),
}

class MainActivity : ComponentActivity() {

    private val player = SimpleAudioPlayer()
    private val sharedTextState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncomingIntent(intent)

        val app = application as TinyAvaApp
        setContent {
            val settings by app.settingsStore.state.collectAsState()
            val sharedText by sharedTextState
            TinyAvaTheme(mode = settings.themeMode) {
                TinyAvaRoot(
                    app = app,
                    settings = settings,
                    player = player,
                    incomingSharedText = sharedText,
                    onConsumedSharedText = { sharedTextState.value = null },
                    onOpenTtsSettings = { openAndroidTtsSettings() },
                    onReadClipboard = { readClipboardText() },
                    onSaveWavToDownloads = { samples -> saveWavToDownloads(samples) },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onDestroy() {
        player.stop()
        super.onDestroy()
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val txt = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
            if (!txt.isNullOrEmpty()) sharedTextState.value = txt
        }
    }

    private fun openAndroidTtsSettings() {
        val intent = Intent("com.android.settings.TTS_SETTINGS").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { startActivity(intent) }.onFailure {
            Toast.makeText(this, "لطفاً تنظیمات ← دسترس‌پذیری ← تبدیل متن به گفتار را باز کنید", Toast.LENGTH_LONG).show()
        }
    }

    private fun readClipboardText(): String? {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()?.trim()
    }

    private fun saveWavToDownloads(samples: FloatArray): String {
        val fileName = "tiny-ava-${System.currentTimeMillis()}.wav"
        val app = application as TinyAvaApp
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "audio/wav")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Cannot create file in Downloads")
            val tmp = File(cacheDir, fileName)
            app.engine.writeWavFile(samples, tmp)
            contentResolver.openOutputStream(uri)?.use { out ->
                tmp.inputStream().use { it.copyTo(out) }
            }
            tmp.delete()
            "در پوشه Downloads ذخیره شد: $fileName"
        } else {
            val outDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: filesDir
            val out = File(outDir, fileName)
            app.engine.writeWavFile(samples, out)
            "ذخیره شد: ${out.name}"
        }
    }
}

@Composable
private fun TinyAvaRoot(
    app: TinyAvaApp,
    settings: SettingsState,
    player: SimpleAudioPlayer,
    incomingSharedText: String?,
    onConsumedSharedText: () -> Unit,
    onOpenTtsSettings: () -> Unit,
    onReadClipboard: () -> String?,
    onSaveWavToDownloads: (FloatArray) -> String,
) {
    val c = LocalAvaColors.current
    val scope = rememberCoroutineScope()

    var currentTab by remember { mutableStateOf(Tab.HOME) }
    var showAbout by remember { mutableStateOf(false) }
    var engineReady by remember { mutableStateOf(app.engine.isLoaded) }

    var readerText by remember {
        mutableStateOf("سلام، حال شما چطور است؟ بارانِ ریزی روی آسفالتِ خیسِ خیابان می‌بارد. امروز هوای تهران بسیار دلپذیر است.")
    }
    var activeSentenceIdx by remember { mutableIntStateOf(-1) }
    var isSpeaking by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf("") }
    var speakJob by remember { mutableStateOf<Job?>(null) }
    val cancelFlag = remember { AtomicBoolean(false) }

    val sentences = remember(readerText) { PersianTextNormalizer.splitSentences(readerText) }

    LaunchedEffect(Unit) {
        if (!engineReady) {
            withContext(Dispatchers.IO) { app.engine.ensureLoaded() }
            engineReady = true
        }
    }

    fun stopSpeaking() {
        cancelFlag.set(true)
        player.stop()
        speakJob?.cancel()
        speakJob = null
        isSpeaking = false
        activeSentenceIdx = -1
    }

    fun speakSentences(list: List<String>, startIndex: Int = 0) {
        stopSpeaking()
        if (list.isEmpty()) return
        cancelFlag.set(false)
        isSpeaking = true
        statusMsg = "در حال تولید صدا…"
        speakJob = scope.launch(Dispatchers.IO) {
            try {
                for (idx in startIndex until list.size) {
                    if (cancelFlag.get()) break
                    withContext(Dispatchers.Main) {
                        activeSentenceIdx = idx
                        statusMsg = "در حال خواندن جمله ${idx + 1} از ${list.size}"
                    }
                    val wave = app.engine.synthesizeSentence(
                        rawSentence = list[idx],
                        settings = app.settingsStore.state.value,
                        isCancelled = { cancelFlag.get() },
                    )
                    if (cancelFlag.get()) break
                    if (wave.isNotEmpty()) {
                        val pcm = app.engine.floatToPcm16Bytes(wave)
                        player.playBlocking(pcm) { cancelFlag.get() }
                    }
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isSpeaking = false
                    activeSentenceIdx = -1
                    statusMsg = ""
                }
            }
        }
    }

    fun speakSingle(text: String) {
        if (isSpeaking) {
            stopSpeaking()
        } else {
            speakSentences(PersianTextNormalizer.splitSentences(text), 0)
        }
    }

    // Handle Android Share Intent ("خواندن با آوا")
    LaunchedEffect(incomingSharedText) {
        if (!incomingSharedText.isNullOrBlank()) {
            readerText = incomingSharedText
            currentTab = Tab.READER
            showAbout = false
            onConsumedSharedText()
            speakSentences(PersianTextNormalizer.splitSentences(incomingSharedText), 0)
        }
    }

    Scaffold(
        containerColor = c.bg,
        topBar = {
            Column(modifier = Modifier.statusBarsPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = if (showAbout) "درباره آوا" else when (currentTab) {
                                Tab.HOME -> "آوا (tiny-ava)"
                                Tab.READER -> "روخوانی متن"
                                Tab.VOICE -> "تنظیمات صدا"
                                Tab.DICT -> "واژه‌نامه تلفظ"
                            },
                            fontSize = 21.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = c.ink,
                        )
                        Text(
                            text = if (showAbout) "نسخه ${BuildConfig.VERSION_NAME} · OncillaAI" else when (currentTab) {
                                Tab.HOME -> "موتور گفتار فارسی روی دستگاه"
                                Tab.READER -> "پشتیبانی از کلیپ‌بورد و اعراب"
                                Tab.VOICE -> "اعمال مستقیم روی TalkBack"
                                Tab.DICT -> "اصلاح دائمی کلمات و نام‌ها"
                            },
                            fontSize = 12.5.sp,
                            color = c.muted,
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (showAbout) {
                            IconButton(
                                onClick = { showAbout = false },
                                modifier = Modifier
                                    .size(44.dp)
                                    .semantics { contentDescription = "بازگشت" },
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, tint = c.ink)
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = c.soft,
                                border = BorderStroke(1.dp, c.line),
                            ) {
                                Text(
                                    "آفلاین",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = c.ink,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                            Surface(
                                shape = CircleShape,
                                color = c.soft,
                                border = BorderStroke(1.dp, c.line),
                                modifier = Modifier.size(42.dp),
                            ) {
                                IconButton(
                                    onClick = { showAbout = true },
                                    modifier = Modifier.semantics { contentDescription = "درباره آوا و سازنده" },
                                ) {
                                    Icon(Icons.Rounded.Info, contentDescription = null, tint = c.ink)
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = c.line)
            }
        },
        bottomBar = {
            if (!showAbout) {
                NavigationBar(containerColor = c.card) {
                    val items = listOf(
                        Tab.HOME to Icons.Rounded.Home,
                        Tab.READER to Icons.Rounded.Description,
                        Tab.VOICE to Icons.Rounded.Tune,
                        Tab.DICT to Icons.Rounded.Book,
                    )
                    for ((tab, icon) in items) {
                        NavigationBarItem(
                            selected = currentTab == tab,
                            onClick = { currentTab = tab },
                            icon = { Icon(icon, contentDescription = null) },
                            label = { Text(tab.label, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = c.accent,
                                selectedTextColor = c.accent,
                                indicatorColor = c.soft,
                                unselectedIconColor = c.muted,
                                unselectedTextColor = c.muted,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (showAbout) {
                AboutScreen()
            } else {
                when (currentTab) {
                    Tab.HOME -> HomeScreen(
                        settings = settings,
                        engineReady = engineReady,
                        isSpeaking = isSpeaking,
                        onOpenTtsSettings = onOpenTtsSettings,
                        onQuickTest = {
                            speakSingle("سلام، این صدای پریسا از موتور گفتار آفلاین آوا است.")
                        },
                        onOpenReader = { currentTab = Tab.READER },
                        onOpenDictionary = { currentTab = Tab.DICT },
                    )

                    Tab.READER -> ReaderScreen(
                        text = readerText,
                        onTextChange = { readerText = it },
                        sentences = sentences,
                        activeSentenceIndex = activeSentenceIdx,
                        isSpeaking = isSpeaking,
                        statusMessage = statusMsg,
                        onPasteAndRead = {
                            val clip = onReadClipboard()
                            if (clip.isNullOrBlank()) {
                                statusMsg = "کلیپ‌بورد خالی است."
                            } else {
                                readerText = clip
                                speakSentences(PersianTextNormalizer.splitSentences(clip), 0)
                            }
                        },
                        onPlayOrStop = {
                            if (isSpeaking) stopSpeaking() else speakSentences(sentences, 0)
                        },
                        onPrevSentence = {
                            val prev = (if (activeSentenceIdx > 0) activeSentenceIdx - 1 else 0)
                            speakSentences(sentences, prev)
                        },
                        onNextSentence = {
                            val next = (activeSentenceIdx + 1).coerceAtMost((sentences.size - 1).coerceAtLeast(0))
                            speakSentences(sentences, next)
                        },
                        onSaveWav = {
                            if (sentences.isEmpty()) return@ReaderScreen
                            statusMsg = "در حال ساخت فایل صوتی WAV…"
                            scope.launch(Dispatchers.IO) {
                                val chunks = mutableListOf<FloatArray>()
                                val gap = FloatArray((TinyAvaEngine.SAMPLE_RATE * 0.12f).toInt())
                                for (i in sentences.indices) {
                                    val w = app.engine.synthesizeSentence(sentences[i], app.settingsStore.state.value)
                                    if (w.isNotEmpty()) {
                                        chunks.add(w)
                                        if (i < sentences.lastIndex) chunks.add(gap)
                                    }
                                }
                                val total = chunks.sumOf { it.size }
                                val full = FloatArray(total)
                                var off = 0
                                for (ch in chunks) {
                                    ch.copyInto(full, off)
                                    off += ch.size
                                }
                                val msg = if (total > 0) {
                                    runCatching { onSaveWavToDownloads(full) }.getOrElse { "خطا در ذخیره فایل: ${it.message}" }
                                } else {
                                    "متنی برای تبدیل پیدا نشد."
                                }
                                withContext(Dispatchers.Main) { statusMsg = msg }
                            }
                        },
                    )

                    Tab.VOICE -> VoiceSettingsScreen(
                        settings = settings,
                        onSpeedChange = { v -> app.settingsStore.update { it.copy(speed = v) } },
                        onPitchChange = { v -> app.settingsStore.update { it.copy(pitchSemitones = v) } },
                        onThemeChange = { m -> app.settingsStore.update { it.copy(themeMode = m) } },
                        onToggleNumbers = { b -> app.settingsStore.update { it.copy(convertNumbers = b) } },
                        onToggleEnglish = { b -> app.settingsStore.update { it.copy(convertEnglishUi = b) } },
                        onToggleCache = { b ->
                            app.settingsStore.update { it.copy(enablePhraseCache = b) }
                            if (!b) app.engine.clearPhraseCache()
                        },
                        onPlaySample = {
                            speakSingle("سلام، ساعت چهارده و سی دقیقه است و باتری گوشی هشتاد و پنج درصد شارژ دارد.")
                        },
                    )

                    Tab.DICT -> DictionaryScreen(
                        entries = settings.customDictionary,
                        onAdd = { s, r, n ->
                            app.settingsStore.addDictEntry(s, r, n)
                            app.engine.clearPhraseCache()
                        },
                        onEdit = { oldS, newS, newR, n ->
                            app.settingsStore.updateDictEntry(oldS, newS, newR, n)
                            app.engine.clearPhraseCache()
                        },
                        onDelete = { s ->
                            app.settingsStore.removeDictEntry(s)
                            app.engine.clearPhraseCache()
                        },
                        onTestSpeak = { word -> speakSingle(word) },
                    )
                }
            }
        }
    }
}
