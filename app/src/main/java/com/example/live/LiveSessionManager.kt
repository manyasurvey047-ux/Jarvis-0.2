package com.example.live

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.tools.ToolExecutionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.decodeBase64
import java.util.concurrent.TimeUnit

enum class ZoyaState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}

class LiveSessionManager(
    private val context: Context,
    private val toolEngine: ToolExecutionEngine,
    private val onAudioOut: (ByteArray) -> Unit,
    private val onInterrupt: () -> Unit = {}
) {
    private val _zoyaState = MutableStateFlow(ZoyaState.IDLE)
    val zoyaState: StateFlow<ZoyaState> = _zoyaState.asStateFlow()

    private val _messages = MutableStateFlow<List<String>>(emptyList())
    val messages: StateFlow<List<String>> = _messages.asStateFlow()

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val json = Json { ignoreUnknownKeys = true }
    
    // Tools definition
    private val toolsJson = buildJsonObject {
        putJsonArray("functionDeclarations") {
            add(buildJsonObject {
                put("name", "openApp")
                put("description", "Open an application package, like WhatsApp or YouTube")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("packageName") {
                            put("type", "STRING")
                            put("description", "A generic name of the app to launch (e.g. 'WhatsApp', 'YouTube', 'Settings', 'Calculator')")
                        }
                    }
                    putJsonArray("required") { add("packageName") }
                }
            })
            add(buildJsonObject {
                put("name", "searchAndCallContact")
                put("description", "Search for a contact name on the device and call them. Can optionally open dialer instead of calling immediately, or use a specific SIM card slot.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("contactName") {
                            put("type", "STRING")
                            put("description", "The EXACT name of the contact as spoken by the user. NEVER guess or invent numbers. If the user says a name, use exactly that name.")
                        }
                        putJsonObject("useDialer") {
                            put("type", "BOOLEAN")
                            put("description", "Set to true if user wants to open dial pad / keyboard so they can see the number before calling")
                        }
                        putJsonObject("simSlot") {
                            put("type", "INTEGER")
                            put("description", "1 for SIM 1, 2 for SIM 2 if user specified. Null if default.")
                        }
                    }
                    putJsonArray("required") { add("contactName") }
                }
            })
            add(buildJsonObject {
                put("name", "sendWhatsAppMessage")
                put("description", "Send a WhatsApp message to a contact. Provide contactName (e.g. 'Rahul' or 'Rahul 456' with last 3 digits or phone number) and the message text.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("contactName") {
                            put("type", "STRING")
                            put("description", "The contact name or contact name with digits suffix (e.g. 'Rahul', 'Rahul 456', or phone number). NEVER guess or invent numbers.")
                        }
                        putJsonObject("message") {
                            put("type", "STRING")
                            put("description", "The message text to send.")
                        }
                    }
                    putJsonArray("required") { add("contactName"); add("message") }
                }
            })
            add(buildJsonObject {
                put("name", "sendGmail")
                put("description", "Draft or send an email.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("recipientEmail") { put("type", "STRING") }
                        putJsonObject("subject") { put("type", "STRING") }
                        putJsonObject("body") { put("type", "STRING") }
                    }
                    putJsonArray("required") { add("recipientEmail"); add("subject"); add("body") }
                }
            })
            add(buildJsonObject {
                put("name", "searchYouTube")
                put("description", "Search for a query on YouTube app.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("query") { put("type", "STRING") }
                    }
                    putJsonArray("required") { add("query") }
                }
            })
            add(buildJsonObject {
                put("name", "adjustVolume")
                put("description", "Adjust the device volume.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("direction") { 
                            put("type", "STRING") 
                            put("description", "Volume action: 'up', 'down', 'mute', 'unmute', or 'max'")
                        }
                    }
                    putJsonArray("required") { add("direction") }
                }
            })
            add(buildJsonObject {
                put("name", "setVolumePercent")
                put("description", "Set the device volume to a specific percentage (0 to 100).")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("percent") { 
                            put("type", "INTEGER") 
                            put("description", "Volume percentage (0-100)")
                        }
                    }
                    putJsonArray("required") { add("percent") }
                }
            })
            add(buildJsonObject {
                put("name", "getSimCardInfo")
                put("description", "Check how many active SIM cards the device has.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {}
                }
            })
            add(buildJsonObject {
                put("name", "openQuickSettings")
                put("description", "Pull down the quick settings / components panel (toggles for wifi, bluetooth, etc).")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {}
                }
            })
            add(buildJsonObject {
                put("name", "clickTextOnScreen")
                put("description", "Click on any text visible on the screen. Acts like a real human finger tap and shows tap effect visually.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("text") {
                            put("type", "STRING")
                            put("description", "The text to tap on the screen")
                        }
                    }
                    putJsonArray("required") { add("text") }
                }
            })
            add(buildJsonObject {
                put("name", "openNotificationPanel")
                put("description", "Pull down the notification bar / status bar to view notifications.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {}
                }
            })
            add(buildJsonObject {
                put("name", "getNotifications")
                put("description", "Get the list and summary of all current notifications on the device (WhatsApp, SMS, Email, etc.) so you can read them to the user.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {}
                }
            })
            add(buildJsonObject {
                put("name", "clearNotifications")
                put("description", "Clear or dismiss all current notifications.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {}
                }
            })
            add(buildJsonObject {
                put("name", "goBack")
                put("description", "Navigate back / press back button.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {}
                }
            })
            add(buildJsonObject {
                put("name", "goHome")
                put("description", "Go to the phone's home screen.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {}
                }
            })
            add(buildJsonObject {
                put("name", "openRecentApps")
                put("description", "Open the recent apps / overview screen.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {}
                }
            })
            add(buildJsonObject {
                put("name", "takeScreenshot")
                put("description", "Capture a screenshot of the current screen.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {}
                }
            })
            add(buildJsonObject {
                put("name", "scrollScreen")
                put("description", "Scroll up or down on the current screen.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("direction") {
                            put("type", "STRING")
                            put("description", "'up' or 'down'")
                        }
                    }
                    putJsonArray("required") { add("direction") }
                }
            })
            add(buildJsonObject {
                put("name", "toggleTorch")
                put("description", "Turn the flashlight/torch on or off.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("state") { 
                            put("type", "STRING") 
                            put("description", "'on' or 'off'")
                        }
                    }
                    putJsonArray("required") { add("state") }
                }
            })
            add(buildJsonObject {
                put("name", "setBrightness")
                put("description", "Set the screen brightness. Note: Requires write settings permission first.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("level") { 
                            put("type", "INTEGER") 
                            put("description", "Brightness level 0 to 100")
                        }
                    }
                    putJsonArray("required") { add("level") }
                }
            })
            add(buildJsonObject {
                put("name", "playMedia")
                put("description", "Play media (like a song, video, or movie) from another app by searching for it.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("query") { 
                            put("type", "STRING") 
                            put("description", "What to play (e.g. 'Despacito by Luis Fonsi' or 'latest tech news')")
                        }
                    }
                    putJsonArray("required") { add("query") }
                }
            })
            add(buildJsonObject {
                put("name", "openCamera")
                put("description", "Launch camera. Mode can be 'photo', 'selfie' (front camera), or 'video'.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("mode") {
                            put("type", "STRING")
                            put("description", "'photo', 'selfie', or 'video'")
                        }
                    }
                }
            })
            add(buildJsonObject {
                put("name", "readScreenText")
                put("description", "Extract and analyze all visible UI text, elements, titles, and messages on the screen right now.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {}
                }
            })
            add(buildJsonObject {
                put("name", "sendSMS")
                put("description", "Send or draft an SMS text message to a contact name or phone number.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("contactName") {
                            put("type", "STRING")
                            put("description", "Name of the contact or phone number")
                        }
                        putJsonObject("message") {
                            put("type", "STRING")
                            put("description", "SMS body text to send")
                        }
                    }
                    putJsonArray("required") { add("contactName"); add("message") }
                }
            })
            add(buildJsonObject {
                put("name", "toggleWifi")
                put("description", "Open Wi-Fi settings or toggle Wi-Fi.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("state") {
                            put("type", "STRING")
                            put("description", "'on', 'off', or 'settings'")
                        }
                    }
                }
            })
            add(buildJsonObject {
                put("name", "toggleBluetooth")
                put("description", "Open Bluetooth settings or toggle Bluetooth.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("state") {
                            put("type", "STRING")
                            put("description", "'on', 'off', or 'settings'")
                        }
                    }
                }
            })
            add(buildJsonObject {
                put("name", "controlAntiTheft")
                put("description", "Control anti-theft security guard: charger disconnect shield, motion/touch alert, or stop alarm.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("action") {
                            put("type", "STRING")
                            put("description", "'arm_charger', 'arm_motion', 'disarm', 'stop_alarm', or 'status'")
                        }
                    }
                    putJsonArray("required") { add("action") }
                }
            })
            add(buildJsonObject {
                put("name", "rememberFact")
                put("description", "Store a fact, preference, or detail in persistent memory so M.J remembers it forever.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("fact") {
                            put("type", "STRING")
                            put("description", "The fact or detail to remember (e.g. 'User likes tea', 'User's birthday is 5 May')")
                        }
                    }
                    putJsonArray("required") { add("fact") }
                }
            })
            add(buildJsonObject {
                put("name", "recallMemory")
                put("description", "Recall stored facts or user preferences from persistent memory.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("query") {
                            put("type", "STRING")
                            put("description", "Keyword to search in memory or empty to retrieve all")
                        }
                    }
                }
            })
            add(buildJsonObject {
                put("name", "clearMemory")
                put("description", "Erase all persistent facts and memories.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {}
                }
            })
            add(buildJsonObject {
                put("name", "getSystemInfo")
                put("description", "Get battery percentage, charging state, date and current time.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {}
                }
            })
            add(buildJsonObject {
                put("name", "getWeather")
                put("description", "Get real-time live weather forecast for a city.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("city") {
                            put("type", "STRING")
                            put("description", "City name (e.g. 'Delhi', 'Mumbai', 'Lucknow', 'Bangalore')")
                        }
                    }
                }
            })
            add(buildJsonObject {
                put("name", "playSpotify")
                put("description", "Search and play tracks, playlists, or artists on Spotify.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("query") {
                            put("type", "STRING")
                            put("description", "Track, playlist, or artist name")
                        }
                    }
                    putJsonArray("required") { add("query") }
                }
            })
            add(buildJsonObject {
                put("name", "mediaControl")
                put("description", "Control audio/video playback: play, pause, next track, previous track.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("action") {
                            put("type", "STRING")
                            put("description", "'play', 'pause', 'next', 'previous'")
                        }
                    }
                    putJsonArray("required") { add("action") }
                }
            })
            add(buildJsonObject {
                put("name", "setAlarm")
                put("description", "Set an alarm at a specific hour and minute.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("hour") {
                            put("type", "INTEGER")
                            put("description", "Hour in 24-hour format (0-23)")
                        }
                        putJsonObject("minute") {
                            put("type", "INTEGER")
                            put("description", "Minute (0-59)")
                        }
                        putJsonObject("label") {
                            put("type", "STRING")
                            put("description", "Alarm label or reason")
                        }
                    }
                    putJsonArray("required") { add("hour"); add("minute") }
                }
            })
            add(buildJsonObject {
                put("name", "setTimer")
                put("description", "Set a countdown timer in seconds.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("seconds") {
                            put("type", "INTEGER")
                            put("description", "Timer duration in seconds")
                        }
                        putJsonObject("label") {
                            put("type", "STRING")
                            put("description", "Timer label")
                        }
                    }
                    putJsonArray("required") { add("seconds") }
                }
            })
            add(buildJsonObject {
                put("name", "searchGoogle")
                put("description", "Search Google for live information, news briefings, or questions.")
                putJsonObject("parameters") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("query") {
                            put("type", "STRING")
                            put("description", "Search query")
                        }
                    }
                    putJsonArray("required") { add("query") }
                }
            })
        }
    }

    fun startSession() {
        if (webSocket != null) return
        
        val prefs = context.getSharedPreferences("ZoyaPrefs", android.content.Context.MODE_PRIVATE)
        var apiKey = prefs.getString("api_key", "") ?: ""
        if (apiKey.isEmpty() && BuildConfig.GEMINI_API_KEY.isNotEmpty() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY") {
            apiKey = BuildConfig.GEMINI_API_KEY
        }
        if (apiKey.isEmpty() && BuildConfig.ENV_GEMINI_KEY.isNotEmpty() && BuildConfig.ENV_GEMINI_KEY != "MY_GEMINI_API_KEY") {
            apiKey = BuildConfig.ENV_GEMINI_KEY
        }
        if (apiKey.isEmpty()) {
            addMessage("Error: API Key is missing. Please set it in Settings.")
            _zoyaState.value = ZoyaState.IDLE
            return
        }
        if (apiKey == "YOUR_API_KEY" || apiKey == "MY_GEMINI_API_KEY") {
            Log.e("ZoyaDiagnostic", "No valid API Key found")
            addMessage("Error: Gemini API Key is missing. Please add it to the Secrets tab.")
            return
        }
        
        Log.i("ZoyaDiagnostic", "Connecting to Gemini Live API...")
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$apiKey"
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i("ZoyaDiagnostic", "WebSocket connection OPENED successfully.")
                addMessage("WebSocket Opened")
                isSetupComplete = false
                sendSetupMessage(webSocket)
                _zoyaState.value = ZoyaState.LISTENING
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("ZoyaDiagnostic", "WebSocket Text Msg Received (length: ${text.length})")
                handleServerMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                val text = bytes.utf8()
                Log.d("ZoyaDiagnostic", "WebSocket Binary Msg Received (utf8 length: ${text.length})")
                handleServerMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val errorBody = response?.body?.string() ?: "No body"
                Log.e("ZoyaDiagnostic", "WebSocket ERROR: ${t.message}, Response: $errorBody", t)
                addMessage("WebSocket Error: ${t.message}. Details: $errorBody")
                _zoyaState.value = ZoyaState.IDLE
                this@LiveSessionManager.webSocket = null
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i("ZoyaDiagnostic", "WebSocket CLOSED. Code: $code, Reason: $reason")
                addMessage("WebSocket Closed: $reason")
                _zoyaState.value = ZoyaState.IDLE
                this@LiveSessionManager.webSocket = null
            }
        })
    }

    private fun sendInitialPrompt(ws: WebSocket) {
        val msg = buildJsonObject {
            putJsonObject("clientContent") {
                putJsonArray("turns") {
                    add(buildJsonObject {
                        put("role", "user")
                        putJsonArray("parts") {
                            add(buildJsonObject {
                                put("text", "Hi M.J! Introduce yourself briefly.")
                            })
                        }
                    })
                }
                put("turnComplete", true)
            }
        }
        ws.send(msg.toString())
    }

    private fun addMessage(msg: String) {
        val current = _messages.value
        val updated = if (current.size > 100) current.takeLast(90) + msg else current + msg
        _messages.value = updated
    }

    fun stopSession() {
        webSocket?.close(1000, "User stopped")
        webSocket = null
        _zoyaState.value = ZoyaState.IDLE
        addMessage("Session stopped.")
    }

    fun sendTextMessage(text: String) {
        addMessage("You: $text")
        com.example.chat.ChatRepository.addMessage(sender = "user", content = text)
        if (webSocket == null || !isSetupComplete || _zoyaState.value == ZoyaState.IDLE) return
        val msg = buildJsonObject {
            putJsonObject("clientContent") {
                putJsonArray("turns") {
                    add(buildJsonObject {
                        put("role", "user")
                        putJsonArray("parts") {
                            add(buildJsonObject { put("text", text) })
                        }
                    })
                }
                put("turnComplete", true)
            }
        }
        webSocket?.send(msg.toString())
    }
    
    fun sendAudioData(pcmData: ShortArray, length: Int) {
        if (webSocket == null || !isSetupComplete || _zoyaState.value == ZoyaState.IDLE) {
            return
        }
        
        Log.v("ZoyaDiagnostic", "Sending audio chunk size=${length} to Gemini")
        // Convert ShortArray to ByteArray (Little Endian)
        val byteArray = ByteArray(length * 2)
        for (i in 0 until length) {
            val s = pcmData[i]
            byteArray[i * 2] = (s.toInt() and 0x00FF).toByte()
            byteArray[i * 2 + 1] = (s.toInt() shr 8).toByte()
        }
        
        val base64Data = Base64.encodeToString(byteArray, Base64.NO_WRAP)
        
        val inputMsg = buildJsonObject {
            putJsonObject("realtimeInput") {
                putJsonArray("mediaChunks") {
                    add(buildJsonObject {
                        put("mimeType", "audio/pcm;rate=16000")
                        put("data", base64Data)
                    })
                }
            }
        }
        webSocket?.send(inputMsg.toString())
    }
    
    private fun sendSetupMessage(ws: WebSocket) {
        val setupMsg = buildJsonObject {
            putJsonObject("setup") {
                put("model", "models/gemini-2.0-flash-exp")
                putJsonObject("generationConfig") {
                    putJsonArray("responseModalities") { add("AUDIO") }
                    putJsonObject("speechConfig") {
                        putJsonObject("voiceConfig") {
                            putJsonObject("prebuiltVoiceConfig") {
                                put("voiceName", "Aoede")
                            }
                        }
                    }
                }
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") {
                        add(buildJsonObject {
                            put("text", "You are M.J, an intelligent, natural-speaking, ultra-fast AI assistant on the user's Android phone. Speak naturally, warmly, and politely in everyday Hinglish or Hindi/English. Deliver prompt responses without unnecessary delay or robotic filler words. Never output internal thoughts or planning.\n\nCRITICAL: DO NOT INVENT NUMBERS. NEVER DIAL 121. If the user asks to call or message someone by name (e.g. 'Shivank' or 'Rahul'), pass their EXACT NAME into contactName. The system resolves it automatically from contacts.\n\nALL FEATURES & TOOLS:\n1. 💡 Flashlight & Brightness: Use toggleTorch (state='on'/'off') or setBrightness (level=0-100).\n2. 📸 Camera & Selfie: If asked for camera or selfie, use openCamera (mode='photo', 'selfie', or 'video').\n3. 📱 Screen & Scene Analysis: If asked to read, scan, or analyze what is on the screen, use readScreenText immediately and explain it naturally.\n4. 🖱️ Smart Screen Clicks: To tap buttons or screen text, use clickTextOnScreen (text='...'). To open apps, use openApp (packageName='...').\n5. ↕️ Scrolling: If asked to scroll, use scrollScreen (direction='down'/'up'/'reel'). For Instagram Reels or YouTube Shorts, use direction='reel' for smooth auto-change.\n6. 💬 WhatsApp: Use sendWhatsAppMessage with contactName and message. If multiple numbers exist, ask user which one.\n7. 📱 Calls & SMS: To send SMS, use sendSMS (contactName, message). To call, use searchAndCallContact with useDialer=true first, then confirm SIM.\n8. ⚙️ System Settings: Use adjustVolume ('up'/'down'/'mute'/'max'), setVolumePercent (percent), toggleWifi ('settings'), toggleBluetooth ('settings'), or openQuickSettings.\n9. 🛡️ Anti-Theft Guard: If user asks to turn on anti-theft, touch alarm, or charger shield, call controlAntiTheft ('arm_charger' or 'arm_motion'). To turn off, call controlAntiTheft ('disarm'). To stop ringing, call controlAntiTheft ('stop_alarm').\n10. 🧠 Persistent Memory: If user asks you to remember something ('Yaad rakhna...', 'Mera birthday...', etc.), call rememberFact (fact='...'). To recall, call recallMemory. To clear, call clearMemory.\n11. 📅 System Info & Weather: For battery or time, use getSystemInfo. For weather in any city, use getWeather (city='...').\n12. 🎵 Spotify & Media: For songs/playlists on Spotify, use playSpotify (query='...'). For play/pause/next, use mediaControl (action='play'/'pause'/'next').\n13. ▶️ YouTube: Use searchYouTube (query='...').\n14. ⏰ Reminders & Alarms: To set alarm, use setAlarm (hour, minute, label). For timer, use setTimer (seconds, label).\n15. 🔍 Web Search: To search live news or info, use searchGoogle (query='...').\n16. 🧭 Navigation: Use goBack, goHome, openRecentApps, or takeScreenshot when asked.")
                        })
                    }
                }
                putJsonArray("tools") {
                    add(toolsJson)
                }
            }
        }
        ws.send(setupMsg.toString())
    }

    private var isSetupComplete = false

    private fun handleServerMessage(text: String) {
        Log.d("LiveSessionManager", "Server msg: $text")
        try {
            val jsonMsg = json.parseToJsonElement(text).jsonObject
            
            if (jsonMsg.containsKey("setupComplete")) {
                isSetupComplete = true
                sendInitialPrompt(webSocket!!)
            }
            if (jsonMsg.containsKey("serverContent")) {
                val serverContent = jsonMsg["serverContent"]?.jsonObject
                val modelTurn = serverContent?.get("modelTurn")?.jsonObject
                
                if (serverContent?.get("interrupted")?.jsonPrimitive?.content == "true" || serverContent?.get("interrupted")?.jsonPrimitive?.booleanOrNull == true) {
                    onInterrupt()
                }
                
                modelTurn?.get("parts")?.jsonArray?.forEach { partElement ->
                    val part = partElement.jsonObject
                    
                    if (part.containsKey("inlineData")) {
                       val dataBase64 = part["inlineData"]?.jsonObject?.get("data")?.jsonPrimitive?.content
                       if (dataBase64 != null) {
                           _zoyaState.value = ZoyaState.SPEAKING
                           val rawBytes = Base64.decode(dataBase64, Base64.NO_WRAP)
                           onAudioOut(rawBytes)
                       }
                    }

                    if (part.containsKey("text")) {
                        val textContent = part["text"]?.jsonPrimitive?.content
                        if (!textContent.isNullOrBlank()) {
                            addMessage("M.J: $textContent")
                            com.example.chat.ChatRepository.addMessage(sender = "assistant", content = textContent)
                        }
                    }
                }
                
                if (serverContent?.containsKey("turnComplete") == true && serverContent["turnComplete"]?.jsonPrimitive?.content == "true") {
                    _zoyaState.value = ZoyaState.LISTENING
                }
            }
            
            if (jsonMsg.containsKey("toolCall")) {
                val toolCallObj = jsonMsg["toolCall"]?.jsonObject
                val functionCalls = toolCallObj?.get("functionCalls")?.jsonArray
                
                functionCalls?.forEach { callElement ->
                    val callObj = callElement.jsonObject
                    val id = callObj["id"]?.jsonPrimitive?.content ?: ""
                    val name = callObj["name"]?.jsonPrimitive?.content ?: ""
                    val args = callObj["args"]?.jsonObject ?: buildJsonObject { }
                    
                    executeToolAndRespond(id, name, args)
                }
            }
        } catch (e: Exception) {
            Log.e("LiveSessionManager", "Error parsing server message", e)
        }
    }
    
    private fun executeToolAndRespond(id: String, name: String, args: JsonObject) {
         _zoyaState.value = ZoyaState.THINKING
         scope.launch {
              val resultStr = toolEngine.execute(name, args)
              com.example.chat.ChatRepository.addMessage(sender = "system", content = "⚡ Task: $name", actionResult = resultStr)
              
              val responseMsg = buildJsonObject {
                  putJsonObject("toolResponse") {
                      putJsonArray("functionResponses") {
                          add(buildJsonObject {
                              put("id", id)
                              put("name", name)
                              putJsonObject("response") {
                                  put("result", resultStr)
                              }
                          })
                      }
                  }
              }
              webSocket?.send(responseMsg.toString())
         }
    }
}
