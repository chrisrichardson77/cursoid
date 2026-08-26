package dev.agentsforcursor.ui.components

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

class VoiceInput(
    val available: Boolean,
    val start: () -> Unit,
)

/**
 * Dictation via the system speech recognizer, which keeps the microphone permission with the
 * recognizer app instead of this one.
 */
@Composable
fun rememberVoiceInput(prompt: String, onResult: (String) -> Unit): VoiceInput {
    val context = LocalContext.current

    val intent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        }
    }

    val available = remember {
        context.packageManager.queryIntentActivities(intent, 0).isNotEmpty()
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let(onResult)
    }

    return remember(available) {
        VoiceInput(available = available, start = { launcher.launch(intent) })
    }
}
