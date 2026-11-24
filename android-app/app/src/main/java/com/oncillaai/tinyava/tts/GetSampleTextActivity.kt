package com.oncillaai.tinyava.tts

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech

/** Supplies the Persian sample sentence when the user taps "Listen to an example" in Android Settings. */
class GetSampleTextActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val result = Intent().apply {
            putExtra(
                TextToSpeech.Engine.EXTRA_SAMPLE_TEXT,
                "سلام، این صدای پریسا از موتور گفتار فارسی و آفلاین آوا است.",
            )
        }
        setResult(TextToSpeech.LANG_AVAILABLE, result)
        finish()
    }
}
