package com.example.voiceassistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

class AssistantService : Service(), TextToSpeech.OnInitListener {

    // Change this phrase to whatever you like (lowercase)
    private val wakeWords = listOf("hey buddy", "hey body", "hey bud", "hi buddy")

    private enum class Mode { WAKE, COMMAND }

    private var mode = Mode.WAKE
    private var recognizer: SpeechRecognizer? = null
    private lateinit var tts: TextToSpeech
    private lateinit var commands: CommandHandler
    private val main = Handler(Looper.getMainLooper())
    private var running = true

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        tts = TextToSpeech(this, this)
        commands = CommandHandler(this) { say(it) }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            say("Speech recognition is not available on this phone")
            return
        }
        listen(0)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts.language = Locale.getDefault()
    }

    private fun startAsForeground() {
        val channelId = "assistant"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(channelId, "Voice assistant", NotificationManager.IMPORTANCE_LOW)
        )
        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Assistant is listening")
            .setContentText("Say \"Hey buddy\" then your command")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1, notification)
        }
    }

    // Start one listening round after a delay (ms)
    private fun listen(delayMs: Long) {
        if (!running) return
        main.postDelayed({
            if (!running) return@postDelayed
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(listener)
                startListening(
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                        )
                    }
                )
            }
        }, delayMs)
    }

    private val listener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.lowercase(Locale.getDefault())
                ?.trim()

            if (text.isNullOrEmpty()) {
                listen(300)
                return
            }

            if (mode == Mode.WAKE) {
                val wake = wakeWords.firstOrNull { text.contains(it) }
                if (wake == null) {
                    listen(300) // not for us, keep waiting
                } else {
                    val command = text.substringAfter(wake).trim()
                    if (command.isNotEmpty()) {
                        commands.handle(command)
                        listen(3000) // wait for the spoken reply to finish
                    } else {
                        beep()
                        mode = Mode.COMMAND
                        listen(200)
                    }
                }
            } else {
                mode = Mode.WAKE
                commands.handle(text)
                listen(3000)
            }
        }

        override fun onError(error: Int) {
            mode = Mode.WAKE
            listen(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1500 else 400)
        }

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun beep() {
        try {
            ToneGenerator(AudioManager.STREAM_MUSIC, 80)
                .startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        } catch (_: Exception) {}
    }

    private fun say(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reply")
    }

    override fun onDestroy() {
        running = false
        main.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        tts.shutdown()
        super.onDestroy()
    }
}
