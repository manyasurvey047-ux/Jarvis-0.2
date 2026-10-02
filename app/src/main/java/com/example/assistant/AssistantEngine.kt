package com.example.assistant

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.chat.ChatRepository
import com.example.tools.ToolExecutionEngine
import com.example.voice.VoiceSpeaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class AssistantEngine(
    private val context: Context,
    private val toolEngine: ToolExecutionEngine
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    fun resolveApiKey(): String {
        val prefs = context.getSharedPreferences("ZoyaPrefs", Context.MODE_PRIVATE)
        val savedKey = prefs.getString("api_key", "") ?: ""
        if (savedKey.isNotBlank()) return savedKey

        if (BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY") {
            return BuildConfig.GEMINI_API_KEY
        }

        if (BuildConfig.ENV_GEMINI_KEY.isNotBlank() && BuildConfig.ENV_GEMINI_KEY != "MY_GEMINI_API_KEY") {
            return BuildConfig.ENV_GEMINI_KEY
        }

        return ""
    }

    fun processQuery(query: String, isVoice: Boolean = false, onReply: ((String) -> Unit)? = null) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return

        // 1. Record user message in persistent chat history
        ChatRepository.addMessage(sender = "user", content = trimmed, isVoice = isVoice)

        scope.launch {
            // 2. First check for direct, instant action commands (sub-second local execution)
            val directHandled = handleDirectCommands(trimmed)
            if (directHandled != null) {
                // Save assistant reply
                ChatRepository.addMessage(sender = "assistant", content = directHandled, isVoice = isVoice)
                VoiceSpeaker.speak(directHandled)
                withContext(Dispatchers.Main) {
                    onReply?.invoke(directHandled)
                }
                return@launch
            }

            // 3. Otherwise, call Gemini 3.8 Flash for intelligent response & tool execution
            val apiKey = resolveApiKey()
            if (apiKey.isEmpty()) {
                val errorMsg = "API Key nahi mila. Kripya Settings me jakar apna Gemini API Key dalein."
                ChatRepository.addMessage(sender = "assistant", content = errorMsg)
                VoiceSpeaker.speak(errorMsg)
                withContext(Dispatchers.Main) {
                    onReply?.invoke(errorMsg)
                }
                return@launch
            }

            try {
                val aiReply = callGeminiApi(trimmed, apiKey)
                ChatRepository.addMessage(sender = "assistant", content = aiReply, isVoice = isVoice)
                VoiceSpeaker.speak(aiReply)
                withContext(Dispatchers.Main) {
                    onReply?.invoke(aiReply)
                }
            } catch (e: Exception) {
                Log.e("AssistantEngine", "Error querying Gemini", e)
                val fallbackReply = generateSmartFallbackReply(trimmed)
                ChatRepository.addMessage(sender = "assistant", content = fallbackReply, isVoice = isVoice)
                VoiceSpeaker.speak(fallbackReply)
                withContext(Dispatchers.Main) {
                    onReply?.invoke(fallbackReply)
                }
            }
        }
    }

    private suspend fun handleDirectCommands(text: String): String? {
        val lower = text.lowercase()

        // Screen Vision / Screen Share Controls
        if (lower.contains("screen share on") || lower.contains("enable screen vision") || lower.contains("screen vision on") ||
            lower.contains("start screen share") || lower.contains("screen dekho") || lower.contains("screen share chalu")) {
            com.example.vision.ScreenVisionEngine.instance?.setVisionActive(true)
            return "Screen Vision enabled. Main screen analyze kar raha hoon. Aap aadesh dijiye, sir."
        }
        if (lower.contains("screen share off") || lower.contains("disable screen vision") || lower.contains("screen vision off") ||
            lower.contains("stop screen share") || lower.contains("screen share band")) {
            com.example.vision.ScreenVisionEngine.instance?.setVisionActive(false)
            return "Screen Vision disabled kar di gayi hai."
        }

        // Autonomous Visual Task Trigger (e.g. Developer options, Chrome search, wireless debugging, click on screen)
        if (lower.contains("developer option") || lower.contains("wireless debugging") ||
            (lower.contains("open") && lower.contains("search") && lower.contains("result")) ||
            (lower.contains("screen") && (lower.contains("click") || lower.contains("tap") || lower.contains("dhundo") || lower.contains("find"))) ||
            (com.example.vision.ScreenVisionEngine.instance?.isVisionActive?.value == true && (lower.contains("click") || lower.contains("tap") || lower.contains("scroll") || lower.contains("turn on") || lower.contains("enable")))) {
            com.example.vision.ScreenVisionEngine.instance?.executeAutonomousTask(text)
            return "Visual AI task start ho gaya hai. Main screen examine karke task execute kar raha hoon..."
        }

        // YouTube
        if (lower.contains("yt open") || lower.contains("open yt") || lower.contains("open youtube") || 
            lower.contains("youtube open") || lower.contains("youtube chalao") || lower.contains("youtube kholo") ||
            lower == "yt" || lower == "youtube") {
            toolEngine.execute("openApp", buildJsonObject { put("packageName", "YouTube") })
            return "YouTube open kar diya gaya hai!"
        }

        // WhatsApp
        if (lower.contains("whatsapp open") || lower.contains("open whatsapp") || lower.contains("whatsapp kholo") ||
            lower.contains("whatsapp chalao") || lower == "whatsapp") {
            toolEngine.execute("openApp", buildJsonObject { put("packageName", "WhatsApp") })
            return "WhatsApp open kar diya gaya hai!"
        }

        // Camera / Selfie
        if (lower.contains("camera open") || lower.contains("open camera") || lower.contains("camera chalao") ||
            lower.contains("camera kholo") || lower.contains("photo khicho") || lower.contains("selfie")) {
            val mode = if (lower.contains("selfie")) "selfie" else "photo"
            toolEngine.execute("openCamera", buildJsonObject { put("mode", mode) })
            return if (mode == "selfie") "Selfie camera open kar diya!" else "Camera open kar diya gaya hai!"
        }

        // Torch / Flashlight ON
        if (lower.contains("torch on") || lower.contains("flashlight on") || lower.contains("torch chalao") ||
            lower.contains("torch jalao") || lower.contains("flashlight chalao") || lower.contains("light on")) {
            toolEngine.execute("toggleTorch", buildJsonObject { put("state", "on") })
            return "Flashlight ON kar di gayi hai!"
        }

        // Torch / Flashlight OFF
        if (lower.contains("torch off") || lower.contains("flashlight off") || lower.contains("torch band") ||
            lower.contains("flashlight band") || lower.contains("light off")) {
            toolEngine.execute("toggleTorch", buildJsonObject { put("state", "off") })
            return "Flashlight band kar di gayi hai!"
        }

        // Volume Controls
        if (lower.contains("volume badhao") || lower.contains("volume up") || lower.contains("awaz badhao")) {
            toolEngine.execute("adjustVolume", buildJsonObject { put("direction", "up") })
            return "Volume badha diya hai!"
        }
        if (lower.contains("volume kam") || lower.contains("volume down") || lower.contains("awaz kam")) {
            toolEngine.execute("adjustVolume", buildJsonObject { put("direction", "down") })
            return "Volume kam kar diya hai!"
        }
        if (lower.contains("mute") || lower.contains("silent")) {
            toolEngine.execute("adjustVolume", buildJsonObject { put("direction", "mute") })
            return "Phone silent / mute kar diya hai!"
        }

        // Wi-Fi / Bluetooth
        if (lower.contains("wifi") || lower.contains("wi-fi")) {
            toolEngine.execute("toggleWifi", buildJsonObject { put("action", "settings") })
            return "Wi-Fi settings open kar di gayi hain!"
        }
        if (lower.contains("bluetooth")) {
            toolEngine.execute("toggleBluetooth", buildJsonObject { put("action", "settings") })
            return "Bluetooth settings open kar di gayi hain!"
        }

        // Anti-theft
        if (lower.contains("charger shield") || lower.contains("theft on") || lower.contains("anti theft")) {
            com.example.security.AntiTheftManager.toggleChargerShield(true)
            return "Anti-Theft Charger Shield activate ho gaya hai!"
        }

        // Direct Call command (e.g. "call Rahul", "Rahul ko call karo")
        val callRegex = Regex("(?:call|phone lagao|ko call karo|ko phone karo)\\s+([a-zA-Z0-9 ]+)", RegexOption.IGNORE_CASE)
        val callMatch = callRegex.find(text)
        if (callMatch != null) {
            val contactName = callMatch.groupValues[1].replace("ko", "").trim()
            if (contactName.isNotEmpty()) {
                val res = toolEngine.execute("searchAndCallContact", buildJsonObject {
                    put("contactName", contactName)
                    put("useDialer", true)
                })
                return res
            }
        }

        return null
    }

    private suspend fun callGeminiApi(prompt: String, apiKey: String): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent?key=$apiKey"

        val requestBodyJson = buildJsonObject {
            putJsonArray("contents") {
                add(buildJsonObject {
                    put("role", "user")
                    putJsonArray("parts") {
                        add(buildJsonObject { put("text", prompt) })
                    }
                })
            }
            putJsonObject("systemInstruction") {
                putJsonArray("parts") {
                    add(buildJsonObject {
                        put("text", "You are M.J, an ultra-fast, helpful AI assistant on the user's Android phone. Speak naturally, warmly, and politely in everyday Hinglish or Hindi/English. Keep responses short and conversational. When asked to perform actions, acknowledge clearly.")
                    })
                }
            }
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = requestBodyJson.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        val response = httpClient.newCall(request).execute()
        val bodyStr = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            Log.e("AssistantEngine", "API error: ${response.code} $bodyStr")
            throw RuntimeException("API error: ${response.code}")
        }

        val respJson = json.parseToJsonElement(bodyStr).jsonObject
        val candidates = respJson["candidates"]?.jsonArray
        val firstCandidate = candidates?.firstOrNull()?.jsonObject
        val content = firstCandidate?.get("content")?.jsonObject
        val parts = content?.get("parts")?.jsonArray

        val textReply = parts?.firstNotNullOfOrNull {
            it.jsonObject["text"]?.jsonPrimitive?.content
        }

        return textReply?.trim() ?: "Aapki request process ho gayi hai."
    }

    private fun generateSmartFallbackReply(query: String): String {
        val lower = query.lowercase()
        return when {
            lower.contains("hii") || lower.contains("hello") || lower.contains("hlo") ->
                "Namaste! Main M.J hoon, aapka smart assistant. Bataiye main aapki kya madad kar sakta hoon?"
            lower.contains("kaisa") || lower.contains("kaise ho") || lower.contains("how are you") ->
                "Main ekdam badhiya hoon! Aap bataiye, aaj kya task perform karna hai?"
            lower.contains("naam") || lower.contains("who are you") || lower.contains("kaun ho") ->
                "Main M.J hoon, aapka personal AI phone assistant."
            else ->
                "Maine aapki baat note kar li hai: $query. Main aapke aadesh par kaam kar raha hoon."
        }
    }
}
