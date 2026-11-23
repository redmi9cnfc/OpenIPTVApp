package com.kraftplay.openiptv.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import com.kraftplay.openiptv.ui.tr
import java.util.Locale

object VoiceInputHelper {

    fun createRecognizerIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().language)
            putExtra(RecognizerIntent.EXTRA_PROMPT, tr("Произнесите название канала..."))
        }
    }

    fun startSpeechRecognition(
        context: Context,
        onResult: (String) -> Unit,
        onError: ((String) -> Unit)? = null,
        onFallbackIntent: ((Intent) -> Unit)? = null
    ) {
        val fallbackIntent = createRecognizerIntent()

        // 1. В первую очередь пробуем запустить системный диалог голосового ввода
        if (onFallbackIntent != null) {
            try {
                onFallbackIntent(fallbackIntent)
                return
            } catch (e: Exception) {
                // Если запустить системное окно не удалось, переходим к SpeechRecognizer
            }
        }

        // 2. Использование программного SpeechRecognizer
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            val msg = tr("Голосовой ввод недоступен на этом устройстве")
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            onError?.invoke(msg)
            return
        }

        try {
            val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

            speechRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    val errorMsg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> tr("Речь не распознана")
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> tr("Превышено время ожидания")
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> tr("Необходимы разрешения для микрофона")
                        else -> tr("Ошибка распознавания речи")
                    }
                    Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                    onError?.invoke(errorMsg)
                    runCatching { speechRecognizer.destroy() }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val query = matches[0]
                        onResult(query)
                    }
                    runCatching { speechRecognizer.destroy() }
                }

                override fun onPartialResults(partialResults: Bundle?) {}

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            speechRecognizer.startListening(fallbackIntent)
        } catch (e: Exception) {
            val msg = tr("Ошибка голосового ввода: {0}", e.localizedMessage)
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            onError?.invoke(msg)
        }
    }
}
