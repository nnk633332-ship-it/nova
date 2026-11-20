package com.nova.assistant

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    companion object {
        private const val AUDIO_PERMISSION = 9001
        private const val CAPTURE_REQUEST = 9002
    }

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var conversationActive = false
    private var awaitingCaptureResult = false

    private lateinit var statusView: TextView
    private lateinit var heardView: TextView
    private lateinit var replyView: TextView
    private lateinit var listenButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        buildDashboard()
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            statusView.text = "এই ফোনে Speech Recognition পরিষেবা পাওয়া যাচ্ছে না।"
            listenButton.isEnabled = false
        }
    }

    private fun buildDashboard() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 28, 24, 24)
            setBackgroundColor(0xFF071426.toInt())
        }
        fun text(value: String, size: Float): TextView = TextView(this).apply {
            text = value
            textSize = size
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(8, 10, 8, 10)
        }

        page.addView(text("NOVA", 32f))
        page.addView(text("NORMAL MODE  •  Voice Assistant", 15f))
        statusView = text("প্রস্তুত — কথা বলতে Listen চাপো", 16f)
        heardView = text("তুমি যা বলবে তা এখানে দেখা যাবে", 14f)
        replyView = text("NOVA-র উত্তর এখানে দেখা যাবে", 17f)
        page.addView(statusView)
        page.addView(heardView)
        page.addView(replyView)

        listenButton = actionButton("🎙 কথা বলা শুরু করো") { startConversation() }
        page.addView(listenButton)
        page.addView(actionButton("কথা বলা বন্ধ করো") { stopConversation("কথোপকথন বন্ধ।") })
        page.addView(actionButton("স্ক্রিনশট নাও") { requestScreenshot() })
        page.addView(actionButton("Wi-Fi সেটিংস খোলো") { openSystem(Intent(Settings.Panel.ACTION_WIFI), "Wi-Fi সেটিংস খুলছি।") })
        page.addView(actionButton("Bluetooth সেটিংস খোলো") { openSystem(Intent(Settings.Panel.ACTION_BLUETOOTH), "Bluetooth সেটিংস খুলছি।") })
        page.addView(actionButton("ফোনের Settings খোলো") { openSystem(Intent(Settings.ACTION_SETTINGS), "ফোনের Settings খুলছি।") })
        page.addView(actionButton("Battery দেখাও") { showBattery() })
        page.addView(actionButton("NOVA কী করতে পারে?") {
            say("আমি তোমার কথা শুনতে পারি, উত্তর দিতে পারি, Wi-Fi ও Bluetooth সেটিংস খুলতে পারি, ব্যাটারি জানাতে পারি এবং তোমার অনুমতিতে একটি স্ক্রিনশট নিতে পারি।")
        })
        page.addView(text("মাইক্রোফোন ও স্ক্রিনশটের জন্য Android-এর অনুমতি প্রয়োজন।", 12f))

        val scroll = ScrollView(this).apply { addView(page) }
        setContentView(scroll)
    }

    private fun actionButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { action() }
        }

    override fun onInit(result: Int) {
        ttsReady = result == TextToSpeech.SUCCESS
        if (ttsReady) {
            val languageResult = tts?.setLanguage(Locale("bn", "IN"))
            if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
                languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.getDefault())
            }
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    runOnUiThread {
                        if (conversationActive && !awaitingCaptureResult) listenOnce()
                    }
                }
                override fun onError(utteranceId: String?) {
                    runOnUiThread {
                        if (conversationActive && !awaitingCaptureResult) listenOnce()
                    }
                }
            })
        }
    }

    private fun startConversation() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), AUDIO_PERMISSION)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            say("এই ফোনে speech recognition পাওয়া যাচ্ছে না।")
            return
        }
        conversationActive = true
        listenButton.isEnabled = false
        listenOnce()
    }

    private fun listenOnce() {
        if (!conversationActive || awaitingCaptureResult) return
        try {
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { sr ->
                sr.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) { statusView.text = "NOVA শুনছে…" }
                    override fun onBeginningOfSpeech() { statusView.text = "তোমার কথা শুনছি…" }
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() { statusView.text = "কথা বুঝছি…" }
                    override fun onError(error: Int) {
                        statusView.text = "শোনা যায়নি। আবার Listen চাপো।"
                        conversationActive = false
                        listenButton.isEnabled = true
                    }
                    override fun onResults(results: Bundle?) {
                        val words = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val phrase = words?.firstOrNull().orEmpty()
                        heardView.text = "তুমি বলেছ: $phrase"
                        handleConversation(phrase)
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
            val request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-IN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "bn-IN")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }
            recognizer?.startListening(request)
        } catch (_: Exception) {
            conversationActive = false
            listenButton.isEnabled = true
            statusView.text = "ভয়েস পরিষেবা চালু করা যায়নি।"
        }
    }

    private fun handleConversation(raw: String) {
        val q = raw.trim().lowercase(Locale.ROOT)
        if (q.isBlank()) {
            say("আমি ঠিকমতো শুনতে পাইনি। আবার বলবে?")
            return
        }
        when {
            hasAny(q, "বন্ধ", "থামো", "stop", "exit", "শেষ করো") ->
                stopConversation("ঠিক আছে, কথোপকথন বন্ধ করছি।")
            hasAny(q, "স্ক্রিনশট", "screenshot", "screen shot", "আমার অ্যাপের স্ক্রিন") -> {
                say("স্ক্রিনশট নেওয়ার জন্য Android-এর অনুমতি চাইছি।")
                requestScreenshot()
            }
            hasAny(q, "ওয়াইফাই", "ওয়াইফাই", "wifi", "wi-fi") ->
                openSystem(Intent(Settings.Panel.ACTION_WIFI), "Wi-Fi সেটিংস খুলছি।")
            hasAny(q, "ব্লুটুথ", "bluetooth") ->
                openSystem(Intent(Settings.Panel.ACTION_BLUETOOTH), "Bluetooth সেটিংস খুলছি।")
            hasAny(q, "ব্যাটারি", "চার্জ", "battery", "charge") -> showBattery()
            hasAny(q, "সেটিংস", "settings") ->
                openSystem(Intent(Settings.ACTION_SETTINGS), "ফোনের Settings খুলছি।")
            hasAny(q, "সাহায্য", "help", "কি করতে পারো", "কী করতে পারো") ->
                say("তুমি আমাকে স্ক্রিনশট, Wi-Fi, Bluetooth, ব্যাটারি ও Settings সম্পর্কে বলতে পারো। সাধারণ কথার উত্তরও দিতে পারি, তবে এখনো অনলাইন AI যুক্ত হয়নি।")
            hasAny(q, "তোমার নাম", "তুমি কে", "নোভা", "তোমার পরিচয়") ->
                say("আমার নাম NOVA। আমি তোমার ফোনের সহকারী অ্যাপ।")
            hasAny(q, "হ্যালো", "হাই", "hello", "hi", "নমস্কার", "কেমন আছ") ->
                say("হ্যালো! আমি শুনছি। কী জানতে চাও?")
            hasAny(q, "সময়", "কটা বাজে", "time") ->
                say("ফোনের ঘড়িতে বর্তমান সময় দেখতে পারো। এখনো সময়ের আলাদা সংযোগ যোগ করা হয়নি।")
            else ->
                say("তোমার কথা শুনেছি। $raw । এই মুহূর্তে আমার সাধারণ কথোপকথন স্থানীয় নিয়মে চলে; পূর্ণ AI উত্তর ব্যবস্থা এখনো যুক্ত হয়নি।")
        }
    }

    private fun hasAny(text: String, vararg terms: String): Boolean =
        terms.any { text.contains(it) }

    private fun say(message: String) {
        statusView.text = "NOVA উত্তর দিচ্ছে…"
        replyView.text = "NOVA: $message"
        if (ttsReady) {
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "nova_reply")
        } else if (conversationActive && !awaitingCaptureResult) {
            // Text remains visible; user can tap Listen again if speech output is unavailable.
            conversationActive = false
            listenButton.isEnabled = true
            statusView.text = "উত্তর দেখানো হয়েছে। আবার Listen চাপো।"
        }
    }

    private fun stopConversation(message: String) {
        conversationActive = false
        awaitingCaptureResult = false
        try { recognizer?.cancel() } catch (_: Exception) {}
        listenButton.isEnabled = true
        statusView.text = message
        replyView.text = "NOVA: $message"
        if (ttsReady) tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "nova_stop")
    }

    private fun requestScreenshot() {
        awaitingCaptureResult = true
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(manager.createScreenCaptureIntent(), CAPTURE_REQUEST)
        } catch (_: Exception) {
            awaitingCaptureResult = false
            say("স্ক্রিনশটের অনুমতি চাওয়া যায়নি।")
        }
    }

    @Deprecated("Android activity result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != CAPTURE_REQUEST) return
        awaitingCaptureResult = false
        if (resultCode == RESULT_OK && data != null) {
            val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
            }
            try {
                if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(serviceIntent)
                else startService(serviceIntent)
                say("অনুমতি পেয়েছি। একটি স্ক্রিনশট Pictures/NOVA ফোল্ডারে সেভ করার চেষ্টা করছি।")
            } catch (_: Exception) {
                say("স্ক্রিনশট সার্ভিস চালু করা যায়নি।")
            }
        } else {
            say("স্ক্রিনশটের অনুমতি দেওয়া হয়নি।")
        }
        if (conversationActive) {
            // Continue only after the consent screen returns; the next reply cycle is user-controlled.
            listenButton.isEnabled = false
        }
    }

    private fun openSystem(intent: Intent, message: String) {
        say(message)
        conversationActive = false
        listenButton.isEnabled = true
        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "এই সেটিংস স্ক্রিনটি ফোনে পাওয়া যাচ্ছে না।", Toast.LENGTH_LONG).show()
        }
    }

    private fun showBattery() {
        val manager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val message = if (level in 0..100) "তোমার ফোনের ব্যাটারি $level শতাংশ।"
                      else "ব্যাটারির তথ্য পাওয়া যাচ্ছে না।"
        say(message)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == AUDIO_PERMISSION) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                startConversation()
            } else {
                say("কথা শোনার জন্য মাইক্রোফোনের অনুমতি প্রয়োজন।")
            }
        }
    }

    override fun onDestroy() {
        conversationActive = false
        recognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
