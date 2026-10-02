package com.example.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.ZoyaForegroundService
import com.example.assistant.AssistantEngine
import com.example.chat.ChatRepository
import com.example.memory.MemoryStore
import com.example.notification.NotificationStore
import com.example.tools.ToolExecutionEngine
import com.example.voice.VoiceInputManager
import com.example.voice.VoiceSpeaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun JarvisHudScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentUrl by remember { mutableStateOf("file:///android_asset/hud.html") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isPowerOn by remember { mutableStateOf(true) }

    val toolEngine = remember { ToolExecutionEngine(context) }
    val assistantEngine = remember { AssistantEngine(context, toolEngine) }
    val visionEngine = remember { com.example.vision.ScreenVisionEngine.init(context, toolEngine) }

    fun escapeJs(text: String): String {
        return text.replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
    }

    fun callJs(script: String) {
        scope.launch(Dispatchers.Main) {
            webViewRef?.evaluateJavascript(script, null)
        }
    }

    LaunchedEffect(visionEngine) {
        visionEngine.onVisionStatusUpdated = { active, statusText ->
            callJs("if(window.setVisionStatus) window.setVisionStatus($active, '${escapeJs(statusText)}');")
        }
        visionEngine.onTaskProgressUpdated = { step, desc, reasoning ->
            callJs("if(window.onVisionTaskProgress) window.onVisionTaskProgress($step, '${escapeJs(desc)}', '${escapeJs(reasoning)}');")
        }
    }

    val timeFormatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val hudClockFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    val voiceInputManager = remember {
        VoiceInputManager(context) { recognizedText ->
            val timeStr = timeFormatter.format(Date())
            callJs("window.addChatMessage('user', '${escapeJs(recognizedText)}', '$timeStr');")
            callJs("window.setOrbState('thinking', 0.6);")

            assistantEngine.processQuery(recognizedText, isVoice = true) { reply ->
                val replyTime = timeFormatter.format(Date())
                callJs("window.addChatMessage('jarvis', '${escapeJs(reply)}', '$replyTime');")
                callJs("window.setOrbState('speaking', 0.9);")

                scope.launch {
                    delay(3000)
                    callJs("window.setOrbState('idle', 0.0);")
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val micGranted = perms[Manifest.permission.RECORD_AUDIO] == true
        if (micGranted) {
            Toast.makeText(context, "Microphone access enabled", Toast.LENGTH_SHORT).show()
        }
    }

    fun syncAllChatHistory() {
        val messages = ChatRepository.messages.value
        val turnsArray = JSONArray()
        for (m in messages) {
            val obj = JSONObject()
            val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(m.timestamp))
            obj.put("formattedTime", timeStr)
            if (m.sender == "user") {
                obj.put("userTranscript", m.content)
            } else {
                obj.put("jarvisResponse", m.content)
            }
            turnsArray.put(obj)
        }
        val jsonStr = turnsArray.toString()
        callJs("window.syncChatTurns('$jsonStr');")
    }

    fun getPermissionsJson(): JSONObject {
        val perms = JSONObject()
        val mic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
        val contacts = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val battery = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm?.isIgnoringBatteryOptimizations(context.packageName) == true
        } else true
        val settings = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.System.canWrite(context)
        } else true
        val accessibility = com.example.accessibility.ZoyaAccessibilityService.instance != null

        perms.put("mic", mic)
        perms.put("notification", notif)
        perms.put("battery", battery)
        perms.put("contacts", contacts)
        perms.put("settings", settings)
        perms.put("accessibility", accessibility)
        return perms
    }

    fun getInitialSettingsJson(): String {
        val prefs = context.getSharedPreferences("ZoyaPrefs", Context.MODE_PRIVATE)
        val apiKey = assistantEngine.resolveApiKey()
        val model = prefs.getString("model", "models/gemini-3.8-flash") ?: "models/gemini-3.8-flash"
        val voice = prefs.getString("voice", "Aoede") ?: "Aoede"
        val personality = prefs.getString("personality", "Assistant Mode") ?: "Assistant Mode"
        val userName = prefs.getString("user_name", "Sir") ?: "Sir"
        val ytEnabled = prefs.getBoolean("youtube_enabled", true)
        val ytKey = prefs.getString("yt_api_key", "") ?: ""

        val json = JSONObject()
        json.put("apiKey", apiKey)
        json.put("model", model)
        json.put("voice", voice)
        json.put("personality", personality)
        json.put("userName", userName)
        json.put("youtubeEnabled", ytEnabled)
        json.put("youtubeApiKey", ytKey)
        json.put("permissions", getPermissionsJson())
        return json.toString()
    }

    // Live clock ticker
    LaunchedEffect(Unit) {
        voiceInputManager.init()
        VoiceSpeaker.init(context)
        while (true) {
            val clockStr = hudClockFormatter.format(Date())
            callJs("if(window.setLiveTime) window.setLiveTime('$clockStr');")
            callJs("if(window.setConnectionStatus) window.setConnectionStatus('LIVE');")
            delay(15000)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceInputManager.destroy()
        }
    }

    BackHandler(enabled = currentUrl.contains("settings.html")) {
        currentUrl = "file:///android_asset/hud.html"
        webViewRef?.loadUrl(currentUrl)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1013))
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                try {
                    val cacheDir = java.io.File(ctx.cacheDir, "WebView/Default/HTTP Cache/Code Cache/js")
                    if (!cacheDir.exists()) {
                        cacheDir.mkdirs()
                    }
                    cacheDir.setReadable(true, false)
                    cacheDir.setWritable(true, false)
                    cacheDir.setExecutable(true, false)
                } catch (e: Exception) {
                    android.util.Log.w("JarvisHudScreen", "WebView cache dir preparation failed", e)
                }

                WebView(ctx).apply {
                    webViewRef = this
                    setBackgroundColor(0) // transparent background

                    val hasDri = java.io.File("/dev/dri").exists()
                    if (!hasDri) {
                        setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                    }

                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.cacheMode = WebSettings.LOAD_NO_CACHE

                    // Bridge 1: Main HUD Bridge
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun onOrbClicked() {
                            scope.launch(Dispatchers.Main) {
                                if (voiceInputManager.isListening.value) {
                                    voiceInputManager.stopListening()
                                    callJs("window.setOrbState('idle', 0.0);")
                                } else {
                                    val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                    if (hasMic) {
                                        voiceInputManager.startListening()
                                        callJs("window.setOrbState('listening', 0.85);")
                                    } else {
                                        permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                                    }
                                }
                            }
                        }

                        @JavascriptInterface
                        fun onMicClicked() {
                            onOrbClicked()
                        }

                        @JavascriptInterface
                        fun onPowerClicked() {
                            scope.launch(Dispatchers.Main) {
                                isPowerOn = !isPowerOn
                                callJs("window.setPowerState($isPowerOn);")
                                if (isPowerOn) {
                                    val intent = Intent(context, ZoyaForegroundService::class.java)
                                    ContextCompat.startForegroundService(context, intent)
                                    Toast.makeText(context, "Jarvis Systems Online", Toast.LENGTH_SHORT).show()
                                } else {
                                    val intent = Intent(context, ZoyaForegroundService::class.java)
                                    context.stopService(intent)
                                    voiceInputManager.stopListening()
                                    VoiceSpeaker.stop()
                                    callJs("window.setOrbState('idle', 0.0);")
                                    Toast.makeText(context, "Jarvis Standby / Sleep", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }

                        @JavascriptInterface
                        fun onSettingsClicked() {
                            scope.launch(Dispatchers.Main) {
                                currentUrl = "file:///android_asset/settings.html"
                                loadUrl(currentUrl)
                            }
                        }

                        @JavascriptInterface
                        fun onHistoryClicked() {
                            scope.launch(Dispatchers.Main) {
                                syncAllChatHistory()
                                Toast.makeText(context, "Chat History Synced", Toast.LENGTH_SHORT).show()
                            }
                        }

                        @JavascriptInterface
                        fun toggleScreenVision() {
                            scope.launch(Dispatchers.Main) {
                                val active = visionEngine.toggleVisionMode()
                                callJs("if(window.setVisionStatus) window.setVisionStatus($active, '${if (active) "Vision Active" else "Vision Standby"}');")
                            }
                        }

                        @JavascriptInterface
                        fun startVisionTask(goal: String) {
                            scope.launch(Dispatchers.Main) {
                                visionEngine.executeAutonomousTask(goal)
                            }
                        }

                        @JavascriptInterface
                        fun confirmSafety(approved: Boolean) {
                            scope.launch(Dispatchers.Main) {
                                visionEngine.confirmPendingSafety(approved)
                            }
                        }

                        @JavascriptInterface
                        fun stopVisionTask() {
                            scope.launch(Dispatchers.Main) {
                                visionEngine.stopCurrentTask()
                            }
                        }
                    }, "JarvisBridge")

                    // Bridge 2: Settings Bridge
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun onBackClicked() {
                            scope.launch(Dispatchers.Main) {
                                currentUrl = "file:///android_asset/hud.html"
                                loadUrl(currentUrl)
                            }
                        }

                        @JavascriptInterface
                        fun getInitialSettings(): String {
                            return getInitialSettingsJson()
                        }

                        @JavascriptInterface
                        fun saveSettings(jsonStr: String) {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val json = JSONObject(jsonStr)
                                    val prefs = context.getSharedPreferences("ZoyaPrefs", Context.MODE_PRIVATE)
                                    val editor = prefs.edit()

                                    if (json.has("apiKey")) editor.putString("api_key", json.getString("apiKey"))
                                    if (json.has("model")) editor.putString("model", json.getString("model"))
                                    if (json.has("voice")) editor.putString("voice", json.getString("voice"))
                                    if (json.has("personality")) editor.putString("personality", json.getString("personality"))
                                    if (json.has("userName")) editor.putString("user_name", json.getString("userName"))
                                    if (json.has("youtubeEnabled")) editor.putBoolean("youtube_enabled", json.getBoolean("youtubeEnabled"))
                                    if (json.has("youtubeApiKey")) editor.putString("yt_api_key", json.getString("youtubeApiKey"))
                                    editor.apply()

                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Jarvis Settings Saved", Toast.LENGTH_SHORT).show()
                                        currentUrl = "file:///android_asset/hud.html"
                                        loadUrl(currentUrl)
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Error saving settings: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }

                        @JavascriptInterface
                        fun pasteApiKey() {
                            scope.launch(Dispatchers.Main) {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = clipboard.primaryClip
                                if (clip != null && clip.itemCount > 0) {
                                    val text = clip.getItemAt(0).text?.toString() ?: ""
                                    callJs("setApiKey('${escapeJs(text)}');")
                                }
                            }
                        }

                        @JavascriptInterface
                        fun pasteYouTubeKey() {
                            scope.launch(Dispatchers.Main) {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = clipboard.primaryClip
                                if (clip != null && clip.itemCount > 0) {
                                    val text = clip.getItemAt(0).text?.toString() ?: ""
                                    callJs("setYouTubeKey('${escapeJs(text)}');")
                                }
                            }
                        }

                        @JavascriptInterface
                        fun requestPermission(type: String) {
                            scope.launch(Dispatchers.Main) {
                                when (type.lowercase()) {
                                    "mic" -> permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                                    "notifications" -> {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                                        }
                                    }
                                    "contacts" -> permissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE))
                                    "battery" -> {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                                data = Uri.parse("package:${context.packageName}")
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            }
                                            context.startActivity(intent)
                                        }
                                    }
                                    "settings" -> {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                                                data = Uri.parse("package:${context.packageName}")
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            }
                                            context.startActivity(intent)
                                        }
                                    }
                                    "accessibility" -> {
                                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(intent)
                                    }
                                }
                                delay(1000)
                                callJs("updatePermissions(${getPermissionsJson()});")
                            }
                        }

                        @JavascriptInterface
                        fun requestAllPermissions() {
                            scope.launch(Dispatchers.Main) {
                                val list = mutableListOf(
                                    Manifest.permission.RECORD_AUDIO,
                                    Manifest.permission.READ_CONTACTS,
                                    Manifest.permission.CALL_PHONE
                                )
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    list.add(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                permissionLauncher.launch(list.toTypedArray())
                            }
                        }

                        @JavascriptInterface
                        fun clearMemories() {
                            scope.launch(Dispatchers.IO) {
                                MemoryStore.clearAllMemories()
                                ChatRepository.clearAll()
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Memory Core & History Cleared", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }

                        @JavascriptInterface
                        fun viewMemories() {
                            scope.launch(Dispatchers.Main) {
                                val memories = MemoryStore.memories.value
                                val count = memories.size
                                Toast.makeText(context, "Stored Facts: $count memory items active", Toast.LENGTH_LONG).show()
                            }
                        }

                        @JavascriptInterface
                        fun validateYouTubeKey(key: String) {
                            scope.launch(Dispatchers.Main) {
                                if (key.length > 20) {
                                    callJs("onYouTubeKeyValidated(true, 'API Key format valid & ready');")
                                } else {
                                    callJs("onYouTubeKeyValidated(false, 'Key is too short or invalid');")
                                }
                            }
                        }
                    }, "JarvisSettingsBridge")

                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            if (url?.contains("hud.html") == true) {
                                val clockStr = hudClockFormatter.format(Date())
                                callJs("window.setLiveTime('$clockStr');")
                                callJs("window.setConnectionStatus('LIVE');")
                                val isVision = visionEngine.isVisionActive.value
                                val vStatus = visionEngine.currentStatus.value
                                callJs("if(window.setVisionStatus) window.setVisionStatus($isVision, '${escapeJs(vStatus)}');")
                                syncAllChatHistory()
                            } else if (url?.contains("settings.html") == true) {
                                val initJson = getInitialSettingsJson()
                                callJs("initSettings($initJson);")
                            }
                        }
                    }

                    loadUrl(currentUrl)
                }
            },
            update = { wv ->
                webViewRef = wv
            }
        )
    }
}
