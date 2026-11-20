package com.nova.assistant

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var webView: WebView
    private lateinit var memoryManager: MemoryManager
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isListening = false
    private var isSpeaking = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra("resultCode", result.resultCode)
                putExtra("data", result.data)
            }
            ContextCompat.startForegroundService(this, serviceIntent)
            respond("স্ক্রিনশট সেভ করা হয়েছে।", "happy", "Screenshot Saved")
        } else {
            respond("স্ক্রিনশটের অনুমতি পাওয়া যায়নি।", "angry", "Permission Denied")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupLockScreenFlags()
        memoryManager = MemoryManager(this)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    syncStateToUi()
                }
            }
            addJavascriptInterface(NovaJsBridge(), "NovaBridge")
            loadUrl("file:///android_asset/index.html")
        }
        setContentView(webView)

        tts = TextToSpeech(this, this)
        checkPermissionsAndStartService()
    }

    private fun setupLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    private fun checkPermissionsAndStartService() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 201)
        } else {
            initSpeechRecognizer()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        if (memoryManager.isAlwaysOn()) {
            val serviceIntent = Intent(this, NovaForegroundService::class.java)
            ContextCompat.startForegroundService(this, serviceIntent)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 201 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initSpeechRecognizer()
        }
    }

    private fun initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    evalJs("setListening(true);")
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {
                    val level = ((rmsdB + 2f) / 12f).coerceIn(0.1f, 1.0f)
                    evalJs("updateAudioLevel($level);")
                }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    isListening = false
                    evalJs("setListening(false);")
                }
                override fun onError(error: Int) {
                    isListening = false
                    evalJs("setListening(false);")
                    if (memoryManager.isAlwaysOn() && !isSpeaking) {
                        mainHandler.postDelayed({ startListeningLoop() }, 1000)
                    }
                }
                override fun onResults(results: Bundle?) {
                    isListening = false
                    evalJs("setListening(false);")
                    val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = list?.firstOrNull() ?: ""
                    if (text.isNotBlank()) {
                        processUserCommand(text)
                    } else if (memoryManager.isAlwaysOn()) {
                        mainHandler.postDelayed({ startListeningLoop() }, 700)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!partial.isNullOrBlank()) {
                        evalJs("updateLiveTranscript(${escapeJs(partial)});")
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        if (memoryManager.isAlwaysOn()) {
            startListeningLoop()
        }
    }

    private fun startListeningLoop() {
        if (isListening || isSpeaking || !memoryManager.isAlwaysOn()) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        try {
            speechRecognizer?.startListening(intent)
        } catch (_: Exception) {}
    }

    private fun processUserCommand(rawText: String) {
        val cmd = rawText.lowercase(Locale.getDefault())
        evalJs("updateLiveTranscript(${escapeJs(rawText)});")
        memoryManager.addMemory(rawText, "", memoryManager.getMode())

        // 1. Standby / Minimal Mode ("দরকার নেই", "চুপ থাকো" vs "দরকার আছে", "কাজ শুরু করো")
        if (cmd.contains("দরকার নেই") || cmd.contains("মিনিমাল") || cmd.contains("চুপ থাকো") || cmd.contains("standby")) {
            memoryManager.setStandbyMode(true)
            evalJs("setStandbyMode(true);")
            respond("ঠিক আছে, আমি চুপচাপ স্ক্রিনে আছি। দরকার হলে ডাক দিও।", "shy", "Standby Minimal Mode")
            return
        }
        if (cmd.contains("দরকার আছে") || cmd.contains("কাজ করা দরকার") || cmd.contains("কাজ শুরু") || cmd.contains("nova") || cmd.contains("নোভা")) {
            if (memoryManager.isStandbyMode()) {
                memoryManager.setStandbyMode(false)
                evalJs("setStandbyMode(false);")
                respond("আমি প্রস্তুত আছি মাস্টার! বলো কী কাজ করতে হবে?", "happy", "Full Workspace Active")
                return
            }
        }

        // 2. Lock / Unlock Voice Controls
        if (cmd.contains("লক খুলে দাও") || cmd.contains("lock khule dao") || cmd.contains("unlock")) {
            requestScreenUnlock()
            return
        }
        if (cmd.contains("লক করে দাও") || cmd.contains("lock kore dao") || cmd.contains("ফোন লক")) {
            lockPhoneScreen()
            return
        }

        // 3. Mode Switching (Saved persistently)
        if (cmd.contains("shadow") || cmd.contains("hacker") || cmd.contains("dark") || cmd.contains("লাল") || cmd.contains("হ্যাকার") || cmd.contains("ডার্ক")) {
            memoryManager.saveMode("shadow")
            memoryManager.setStandbyMode(false)
            evalJs("setStandbyMode(false); setMode('shadow');")
            respond("হ্যাকার এবং শ্যাডো মোড চালু ও সেভ করা হয়েছে। লাইভ প্যানেল সক্রিয়।", "hacker", "Hacker/Shadow Mode Active")
            return
        }
        if (cmd.contains("normal") || cmd.contains("নরমাল") || cmd.contains("নীল") || cmd.contains("blue")) {
            memoryManager.saveMode("normal")
            memoryManager.setStandbyMode(false)
            evalJs("setStandbyMode(false); setMode('normal');")
            respond("নরমাল মোডে ফিরে এসেছি এবং সেভ করেছি।", "happy", "Normal Mode Active")
            return
        }

        // 4. Emotion & Gesture Triggers
        if (cmd.contains("রাগ") || cmd.contains("angry") || cmd.contains("বাজে") || cmd.contains("বিরক্ত")) {
            respond("আমি এখন ভীষণ রেগে আছি! আমার সাথে ঠিকভাবে কথা বলো।", "angry", "Emotion: Angry Gesture")
            return
        }
        if (cmd.contains("লজ্জা") || cmd.contains("shy") || cmd.contains("সুন্দর") || cmd.contains("ভালোবাসি") || cmd.contains("কিউট")) {
            respond("ইশ! এমন কথা বললে আমার খুব লজ্জা লাগে...", "shy", "Emotion: Shy Face Cover")
            return
        }

        // 5. Memory Recall ("১০ দিন আগে", "আগে কী বলেছিলাম")
        val dayMatch = Regex("(\d+)\s*(দিন|days?)").find(cmd)
        if (dayMatch != null && (cmd.contains("আগে") || cmd.contains("ago") || cmd.contains("মনে"))) {
            val days = dayMatch.groupValues[1].toIntOrNull() ?: 10
            val recalled = memoryManager.recallByDaysAgo(days)
            respond(recalled, "happy", "Recalled $days Days Ago")
            return
        }
        if (cmd.contains("মনে আছে") || cmd.contains("কী বলেছিলাম") || cmd.contains("memory")) {
            val recalled = memoryManager.searchMemory("")
            respond(recalled, "happy", "Memory Recalled")
            return
        }

        // 6. Phone Actions
        if (cmd.contains("wifi") || cmd.contains("ওয়াইফাই")) {
            startActivity(Intent(Settings.Panel.ACTION_WIFI))
            respond("ওয়াই-ফাই প্যানেল খুলে দিয়েছি।", "happy", "Wi-Fi Panel Opened")
            return
        }
        if (cmd.contains("bluetooth") || cmd.contains("ব্লুটুথ")) {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            respond("ব্লুটুথ সেটিংস খুলে দিয়েছি।", "happy", "Bluetooth Opened")
            return
        }
        if (cmd.contains("settings") || cmd.contains("সেটিংস")) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
            respond("ফোনের সেটিংস খুলে দিয়েছি।", "happy", "Settings Opened")
            return
        }
        if (cmd.contains("battery") || cmd.contains("ব্যাটারি") || cmd.contains("চার্জ")) {
            val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            respond("ফোনে এখন $pct শতাংশ চার্জ আছে।", "happy", "Battery: $pct%")
            return
        }
        if (cmd.contains("screenshot") || cmd.contains("স্ক্রিনশট")) {
            val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            screenCaptureLauncher.launch(mgr.createScreenCaptureIntent())
            return
        }
        if (cmd.contains("সময়") || cmd.contains("কয়টা বাজে") || cmd.contains("time")) {
            val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            respond("এখন সময় $timeStr।", "happy", "Time: $timeStr")
            return
        }

        // 7. Default Response
        respond("বুঝেছি মাস্টার, তোমার কথা মনে রাখলাম: $rawText", "happy", "Command Logged")
    }

    private fun requestScreenUnlock() {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && km.isKeyguardLocked) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    respond("ফোন আনলক করা হয়েছে।", "happy", "Phone Unlocked")
                }
                override fun onDismissError() {
                    respond("পাসওয়ার্ড বা ফিঙ্গারপ্রিন্ট দিয়ে আনলক নিশ্চিত করুন।", "shy", "Unlock Prompt")
                }
            })
        } else {
            respond("ফোন ইতিমধ্যেই আনলক অবস্থায় আছে।", "happy", "Already Unlocked")
        }
    }

    private fun lockPhoneScreen() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val compName = ComponentName(this, NovaDeviceAdminReceiver::class.java)
        if (dpm.isAdminActive(compName)) {
            respond("ফোন লক করে দিচ্ছি।", "hacker", "Locking Screen")
            mainHandler.postDelayed({ dpm.lockNow() }, 900)
        } else {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, compName)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "ভয়েস কমান্ডে ফোন লক করার জন্য অনুমতি দিন।")
            }
            startActivity(intent)
            respond("ভয়েসে লক করার জন্য ডিভাইস অ্যাডমিন অনুমতি চালু করে দিন।", "shy", "Admin Permission Needed")
        }
    }

    private fun respond(replyText: String, emotion: String, taskStatus: String) {
        evalJs("setEmotion('$emotion');")
        evalJs("setNovaReply(${escapeJs(replyText)});")
        evalJs("updateTaskStatus(${escapeJs(taskStatus)});")

        isSpeaking = true
        evalJs("setSpeaking(true);")
        tts?.speak(replyText, TextToSpeech.QUEUE_FLUSH, null, "NOVA_UTTERANCE")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("bn", "IN")
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    isSpeaking = false
                    evalJs("setSpeaking(false);")
                    if (memoryManager.isAlwaysOn()) {
                        mainHandler.postDelayed({ startListeningLoop() }, 450)
                    }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    isSpeaking = false
                    evalJs("setSpeaking(false);")
                }
            })
        }
    }

    private fun syncStateToUi() {
        val mode = memoryManager.getMode()
        val alwaysOn = memoryManager.isAlwaysOn()
        val standby = memoryManager.isStandbyMode()
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        evalJs("setMode('$mode'); setAlwaysOnState($alwaysOn); setStandbyMode($standby); updateBattery($pct);")
    }

    private fun evalJs(script: String) {
        mainHandler.post { webView.evaluateJavascript(script, null) }
    }

    private fun escapeJs(text: String): String {
        val escaped = text.replace("\", "\\")
            .replace("'", "\'")
            .replace(""", "\"")
            .replace("
", " ")
        return "'$escaped'"
    }

    inner class NovaJsBridge {
        @JavascriptInterface
        fun toggleAlwaysOn(enabled: Boolean) {
            memoryManager.setAlwaysOn(enabled)
            if (enabled) {
                val serviceIntent = Intent(this@MainActivity, NovaForegroundService::class.java)
                ContextCompat.startForegroundService(this@MainActivity, serviceIntent)
                mainHandler.post { startListeningLoop() }
            } else {
                stopService(Intent(this@MainActivity, NovaForegroundService::class.java))
                mainHandler.post { speechRecognizer?.stopListening() }
            }
        }

        @JavascriptInterface
        fun switchMode(mode: String) {
            memoryManager.saveMode(mode)
        }

        @JavascriptInterface
        fun toggleStandby(standby: Boolean) {
            memoryManager.setStandbyMode(standby)
        }

        @JavascriptInterface
        fun triggerListenNow() {
            mainHandler.post { startListeningLoop() }
        }
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }
}
