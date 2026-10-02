package com.example.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.accessibility.ZoyaAccessibilityService
import com.example.chat.ChatRepository
import com.example.tools.ToolExecutionEngine
import com.example.voice.VoiceSpeaker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class ScreenVisionEngine(
    private val context: Context,
    private val toolEngine: ToolExecutionEngine
) {
    companion object {
        private const val TAG = "ScreenVisionEngine"
        private const val MAX_TASK_STEPS = 12

        @Volatile
        var instance: ScreenVisionEngine? = null
            private set

        fun init(context: Context, toolEngine: ToolExecutionEngine): ScreenVisionEngine {
            val engine = ScreenVisionEngine(context.applicationContext, toolEngine)
            instance = engine
            return engine
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val _isVisionActive = MutableStateFlow(false)
    val isVisionActive = _isVisionActive.asStateFlow()

    private val _currentStatus = MutableStateFlow("Vision Standby")
    val currentStatus = _currentStatus.asStateFlow()

    private val _isTaskRunning = MutableStateFlow(false)
    val isTaskRunning = _isTaskRunning.asStateFlow()

    // Safety pause awaiting confirmation
    private var pendingSafetyAction: (() -> Unit)? = null
    private var activeTaskJob: Job? = null

    // Listeners for HUD UI updates
    var onVisionStatusUpdated: ((Boolean, String) -> Unit)? = null
    var onTaskProgressUpdated: ((step: Int, description: String, reasoning: String) -> Unit)? = null

    fun toggleVisionMode(): Boolean {
        val next = !_isVisionActive.value
        setVisionActive(next)
        return next
    }

    fun setVisionActive(active: Boolean) {
        _isVisionActive.value = active
        val statusText = if (active) "Vision Active — Monitoring Display" else "Vision Standby"
        _currentStatus.value = statusText
        notifyVisionStatus(active, statusText)

        if (active) {
            val hasAccessibility = ZoyaAccessibilityService.instance != null
            val msg = if (hasAccessibility) {
                "Screen Vision enabled, sir. I can see your screen and execute tasks autonomously."
            } else {
                "Screen Vision enabled. Please ensure Accessibility Service is active in device Settings to allow clicks."
            }
            ChatRepository.addMessage(sender = "assistant", content = msg)
            VoiceSpeaker.speak(msg)
        } else {
            stopCurrentTask()
            val msg = "Screen Vision paused."
            ChatRepository.addMessage(sender = "assistant", content = msg)
            VoiceSpeaker.speak(msg)
        }
    }

    fun stopCurrentTask() {
        activeTaskJob?.cancel()
        activeTaskJob = null
        pendingSafetyAction = null
        _isTaskRunning.value = false
        _currentStatus.value = if (_isVisionActive.value) "Vision Active" else "Vision Standby"
        notifyVisionStatus(_isVisionActive.value, _currentStatus.value)
    }

    fun confirmPendingSafety(approved: Boolean) {
        val action = pendingSafetyAction
        pendingSafetyAction = null
        if (approved && action != null) {
            ChatRepository.addMessage(sender = "user", content = "Confirm: Proceed with action")
            VoiceSpeaker.speak("Confirmed. Proceeding now.")
            scope.launch {
                action.invoke()
            }
        } else {
            ChatRepository.addMessage(sender = "user", content = "Cancelled by user")
            VoiceSpeaker.speak("Action cancelled as requested.")
            stopCurrentTask()
        }
    }

    fun executeAutonomousTask(goal: String) {
        if (!_isVisionActive.value) {
            setVisionActive(true)
        }

        activeTaskJob?.cancel()
        activeTaskJob = scope.launch {
            _isTaskRunning.value = true
            val goalClean = goal.trim()
            Log.i(TAG, "Starting Autonomous Screen Vision Task: $goalClean")

            val initialAck = "Understood, sir. Initiating visual execution for: $goalClean"
            ChatRepository.addMessage(sender = "assistant", content = initialAck)
            VoiceSpeaker.speak(initialAck)

            val stepHistory = mutableListOf<String>()
            var stepCount = 0
            var completed = false

            while (isActive && stepCount < MAX_TASK_STEPS && !completed) {
                stepCount++
                val stepPrefix = "Step $stepCount: "
                updateStatus("Analyzing screen for step $stepCount...")
                onTaskProgressUpdated?.invoke(stepCount, "Analyzing screen...", "Extracting UI and visual grounding")

                // 1. Capture current screen frame & UI element hierarchy
                val screenContext = captureScreenContext()
                Log.d(TAG, "Screen captured: ${screenContext.elements.size} interactive elements detected in ${screenContext.packageName}")

                // 2. Query Gemini Vision with Multimodal Screen + UI Map + Goal
                val decision = queryVisionModel(goalClean, stepCount, stepHistory, screenContext)
                if (decision == null) {
                    val err = "Could not parse visual response. Pausing autonomous task."
                    ChatRepository.addMessage(sender = "assistant", content = err)
                    VoiceSpeaker.speak(err)
                    break
                }

                Log.i(TAG, "Vision Decision: ${decision.action} -> ${decision.targetDescription} (${decision.reasoning})")
                onTaskProgressUpdated?.invoke(stepCount, decision.targetDescription, decision.reasoning)

                // 3. Safety Guard Enforcement
                if (decision.safetyRisk) {
                    val question = decision.safetyQuestion ?: "Safety Check: Do you want me to proceed with ${decision.targetDescription}?"
                    ChatRepository.addMessage(sender = "assistant", content = question)
                    VoiceSpeaker.speak(question)
                    updateStatus("Awaiting User Confirmation")

                    // Store continuation action
                    pendingSafetyAction = {
                        scope.launch {
                            executeAction(decision)
                            delay(1200)
                            // Resume remaining steps
                            executeAutonomousTask(goalClean)
                        }
                    }
                    _isTaskRunning.value = false
                    return@launch
                }

                // 4. Check for Completion
                if (decision.isCompleted || decision.action == "COMPLETE") {
                    completed = true
                    val doneMsg = if (decision.spokenMessage.isNotBlank()) decision.spokenMessage else "Task successfully completed, sir."
                    ChatRepository.addMessage(sender = "assistant", content = doneMsg)
                    VoiceSpeaker.speak(doneMsg)
                    updateStatus("Task Complete")
                    break
                }

                // 5. Announce and execute action
                if (decision.spokenMessage.isNotBlank()) {
                    VoiceSpeaker.speak(decision.spokenMessage)
                }

                val actionResult = executeAction(decision)
                stepHistory.add("Step $stepCount (${decision.action}): ${decision.targetDescription} -> Result: $actionResult")

                // 6. Verification delay: allow UI transitions to complete
                updateStatus("Verifying action...")
                delay(1300)

                // Check verification expectation
                if (decision.verificationExpectation != null) {
                    val postScreen = captureScreenContext()
                    val verified = verifyScreenState(decision.verificationExpectation, postScreen)
                    Log.d(TAG, "Step $stepCount Verification (${decision.verificationExpectation}): $verified")
                }
            }

            if (!completed && stepCount >= MAX_TASK_STEPS) {
                val maxNotice = "Reached step limit for this task. Please verify current screen."
                ChatRepository.addMessage(sender = "assistant", content = maxNotice)
                VoiceSpeaker.speak(maxNotice)
            }

            _isTaskRunning.value = false
            updateStatus(if (_isVisionActive.value) "Vision Active" else "Vision Standby")
        }
    }

    private suspend fun captureScreenContext(): ScreenUiContext {
        val inst = ZoyaAccessibilityService.instance
        val elements = ZoyaAccessibilityService.extractDetailedUiTree()
        val packageName = inst?.rootInActiveWindow?.packageName?.toString() ?: "unknown"

        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels

        val bitmap = suspendCancellableCoroutine<Bitmap?> { cont ->
            ZoyaAccessibilityService.takeScreenshotAsync { bmp ->
                cont.resume(bmp)
            }
        }

        val base64Img = bitmap?.let { encodeBitmapToBase64(it) }

        return ScreenUiContext(
            elements = elements,
            screenshotBase64 = base64Img,
            screenWidth = width,
            screenHeight = height,
            packageName = packageName
        )
    }

    private fun encodeBitmapToBase64(bitmap: Bitmap): String? {
        return try {
            // Scale bitmap down to max 1024 to optimize speed and API payload
            val maxDim = 1024
            val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                val scale = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
                val matrix = Matrix().apply { postScale(scale, scale) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }

            val stream = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 75, stream)
            val bytes = stream.toByteArray()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Error encoding screenshot to Base64", e)
            null
        }
    }

    private suspend fun queryVisionModel(
        goal: String,
        stepNumber: Int,
        history: List<String>,
        screenContext: ScreenUiContext
    ): ScreenVisionDecision? {
        val apiKey = resolveApiKey()
        if (apiKey.isEmpty()) {
            Log.e(TAG, "Gemini API Key missing for Screen Vision")
            return null
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent?key=$apiKey"

        val promptText = buildString {
            append("You are JARVIS, an autonomous visual AI phone agent. You are operating on the user's active Android screen to fulfill their request.\n\n")
            append("USER GOAL: \"$goal\"\n")
            append("CURRENT STEP: #$stepNumber\n\n")
            if (history.isNotEmpty()) {
                append("PREVIOUS COMPLETED STEPS:\n")
                history.forEach { append("  • ").append(it).append("\n") }
                append("\n")
            }
            append("CURRENT SCREEN INFORMATION:\n")
            append(screenContext.toPromptSummary())
            append("\n\n")
            append("INSTRUCTIONS:\n")
            append("1. Examine the visual screenshot (if provided) and the detected UI element index (#ID, label, type, coordinates).\n")
            append("2. Determine the next concrete physical action:\n")
            append("   - CLICK: tap a specific button, switch, or clickable element by targetId or (clickX, clickY).\n")
            append("   - TYPE: type search terms or text into an input field or search bar.\n")
            append("   - SCROLL: scroll 'down' or 'up' if the target element is likely further down the screen (e.g. Developer Options in Settings).\n")
            append("   - NAVIGATE_APP: open an app (e.g. Settings, Chrome, YouTube) if not currently in that app.\n")
            append("   - BACK / HOME: navigate back or return home.\n")
            append("   - COMPLETE: goal has been achieved.\n")
            append("3. ACCURACY & VISUAL GROUNDING:\n")
            append("   - Ground targets to the UI elements list whenever possible (#ID).\n")
            append("   - If clicking an item, provide the matching targetId and its center (clickX, clickY).\n")
            append("   - Do NOT guess blindly. If multiple similar buttons exist, pick the exact one matching spoken intent.\n")
            append("4. SAFETY PROTOCOL:\n")
            append("   - If this action triggers payments, money transfer, deleting files/contacts, factory reset, or sending messages, set safetyRisk=true and provide safetyQuestion.\n")
            append("5. OUTPUT FORMAT: Respond ONLY with a valid JSON object (no markdown surrounding, or ```json codeblock):\n")
            append("""
            {
              "action": "CLICK" | "TYPE" | "SCROLL" | "NAVIGATE_APP" | "BACK" | "HOME" | "COMPLETE",
              "targetId": 12,
              "targetDescription": "Tap Wireless Debugging switch",
              "clickX": 540.0,
              "clickY": 880.0,
              "inputText": null,
              "scrollDirection": "down",
              "appName": null,
              "safetyRisk": false,
              "safetyQuestion": null,
              "reasoning": "Found the Wireless Debugging switch row. It is currently unchecked.",
              "verificationExpectation": "Wireless Debugging switch checked",
              "isCompleted": false,
              "spokenMessage": "Enabling Wireless Debugging now."
            }
            """.trimIndent())
        }

        val requestBodyJson = buildJsonObject {
            putJsonArray("contents") {
                add(buildJsonObject {
                    putJsonArray("parts") {
                        // Image part if screenshot was captured
                        if (screenContext.screenshotBase64 != null) {
                            add(buildJsonObject {
                                putJsonObject("inline_data") {
                                    put("mime_type", "image/jpeg")
                                    put("data", screenContext.screenshotBase64)
                                }
                            })
                        }
                        // Prompt text part
                        add(buildJsonObject {
                            put("text", promptText)
                        })
                    }
                })
            }
            putJsonObject("generationConfig") {
                put("temperature", 0.1)
                put("responseMimeType", "application/json")
            }
        }

        return try {
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val request = Request.Builder()
                .url(url)
                .post(requestBodyJson.toString().toRequestBody(mediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "Gemini Vision error: ${response.code} $responseBody")
                return null
            }

            parseVisionDecision(responseBody)
        } catch (e: Exception) {
            Log.e(TAG, "Exception querying Gemini Vision", e)
            null
        }
    }

    private fun parseVisionDecision(rawResponse: String): ScreenVisionDecision? {
        return try {
            val root = json.parseToJsonElement(rawResponse).jsonObject
            val candidates = root["candidates"]?.jsonArray
            val first = candidates?.firstOrNull()?.jsonObject
            val content = first?.get("content")?.jsonObject
            val parts = content?.get("parts")?.jsonArray
            val text = parts?.firstNotNullOfOrNull { it.jsonObject["text"]?.jsonPrimitive?.content } ?: return null

            // Clean codeblock markers if present
            val cleaned = text.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val obj = json.parseToJsonElement(cleaned).jsonObject

            val action = obj["action"]?.jsonPrimitive?.contentOrNull ?: "WAIT"
            val targetId = obj["targetId"]?.jsonPrimitive?.intOrNull
            val targetDesc = obj["targetDescription"]?.jsonPrimitive?.contentOrNull ?: ""
            val clickX = obj["clickX"]?.jsonPrimitive?.floatOrNull
            val clickY = obj["clickY"]?.jsonPrimitive?.floatOrNull
            val inputText = obj["inputText"]?.jsonPrimitive?.contentOrNull
            val scrollDirection = obj["scrollDirection"]?.jsonPrimitive?.contentOrNull
            val appName = obj["appName"]?.jsonPrimitive?.contentOrNull
            val safetyRisk = obj["safetyRisk"]?.jsonPrimitive?.booleanOrNull ?: false
            val safetyQuestion = obj["safetyQuestion"]?.jsonPrimitive?.contentOrNull
            val reasoning = obj["reasoning"]?.jsonPrimitive?.contentOrNull ?: ""
            val verification = obj["verificationExpectation"]?.jsonPrimitive?.contentOrNull
            val isCompleted = obj["isCompleted"]?.jsonPrimitive?.booleanOrNull ?: (action == "COMPLETE")
            val spokenMessage = obj["spokenMessage"]?.jsonPrimitive?.contentOrNull ?: ""

            ScreenVisionDecision(
                action = action.uppercase(),
                targetId = targetId,
                targetDescription = targetDesc,
                clickX = clickX,
                clickY = clickY,
                inputText = inputText,
                scrollDirection = scrollDirection,
                appName = appName,
                safetyRisk = safetyRisk,
                safetyQuestion = safetyQuestion,
                reasoning = reasoning,
                verificationExpectation = verification,
                isCompleted = isCompleted,
                spokenMessage = spokenMessage
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed parsing vision decision JSON", e)
            null
        }
    }

    private suspend fun executeAction(decision: ScreenVisionDecision): String {
        return withContext(Dispatchers.Main) {
            when (decision.action) {
                "CLICK" -> {
                    var x = decision.clickX
                    var y = decision.clickY

                    // If targetId is provided, look up its center
                    if (decision.targetId != null) {
                        val elements = ZoyaAccessibilityService.extractDetailedUiTree()
                        val match = elements.find { it.id == decision.targetId }
                        if (match != null) {
                            x = match.centerX
                            y = match.centerY
                        }
                    }

                    if (x != null && y != null) {
                        val clicked = ZoyaAccessibilityService.dispatchGestureClick(x, y)
                        if (clicked) "Clicked at (${x.toInt()}, ${y.toInt()})" else "Click gesture failed"
                    } else {
                        "Click target coordinates not found"
                    }
                }

                "TYPE" -> {
                    val textToType = decision.inputText ?: ""
                    val success = ZoyaAccessibilityService.setTextOnInput(decision.clickX, decision.clickY, textToType)
                    if (success) "Typed: $textToType" else "Typing failed"
                }

                "SCROLL" -> {
                    val dir = decision.scrollDirection ?: "down"
                    val scrolled = ZoyaAccessibilityService.performScroll(dir)
                    if (scrolled) "Scrolled $dir" else "Scroll failed"
                }

                "NAVIGATE_APP" -> {
                    val app = decision.appName ?: "Settings"
                    toolEngine.execute("openApp", buildJsonObject { put("packageName", app) })
                    "Launched app $app"
                }

                "BACK" -> {
                    ZoyaAccessibilityService.performBack()
                    "Navigated Back"
                }

                "HOME" -> {
                    ZoyaAccessibilityService.performHome()
                    "Navigated Home"
                }

                "WAIT" -> {
                    delay(1000)
                    "Waited 1s"
                }

                else -> "No action performed"
            }
        }
    }

    private fun verifyScreenState(expectation: String, screenContext: ScreenUiContext): Boolean {
        val lowerExp = expectation.lowercase()
        return screenContext.elements.any {
            val t = it.text.lowercase()
            val d = it.contentDescription.lowercase()
            t.contains(lowerExp) || d.contains(lowerExp)
        }
    }

    private fun updateStatus(status: String) {
        _currentStatus.value = status
        notifyVisionStatus(_isVisionActive.value, status)
    }

    private fun notifyVisionStatus(active: Boolean, status: String) {
        onVisionStatusUpdated?.invoke(active, status)
    }

    private fun resolveApiKey(): String {
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
}
