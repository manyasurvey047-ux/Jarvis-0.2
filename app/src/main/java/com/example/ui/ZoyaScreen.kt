package com.example.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Payment
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.ZoyaForegroundService
import com.example.live.ZoyaState
import com.example.notification.AppNotification
import com.example.notification.NotificationStore
import com.example.tools.ToolExecutionEngine
import com.example.ui.theme.CrimsonDark
import com.example.ui.theme.CrimsonGlow
import com.example.ui.theme.CrimsonOrbCore
import com.example.ui.theme.CrimsonPrimary
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianBgDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoyaScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    val prefs = remember { context.getSharedPreferences("ZoyaPrefs", Context.MODE_PRIVATE) }
    var apiKey by remember {
        val saved = prefs.getString("api_key", "") ?: ""
        mutableStateOf(
            if (saved.isNotEmpty()) saved
            else if (BuildConfig.GEMINI_API_KEY.isNotEmpty() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY") BuildConfig.GEMINI_API_KEY
            else ""
        )
    }
    var userName by remember {
        val saved = prefs.getString("user_name", "Manjesh Kushwaha") ?: "Manjesh Kushwaha"
        mutableStateOf(saved)
    }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Home, 1: Chat, 2: Discover, 3: Settings
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showNotificationSheet by remember { mutableStateOf(false) }
    var zoyaState by remember { mutableStateOf(ZoyaForegroundService.currentState) }
    var serviceStarted by remember { mutableStateOf(ZoyaForegroundService.activeService != null) }
    var inputText by remember { mutableStateOf("") }
    var torchState by remember { mutableStateOf(false) }
    var isReadingNotificationsAloud by remember { mutableStateOf(false) }

    val messages by ZoyaForegroundService.messages.collectAsState(initial = emptyList())
    val notifications by NotificationStore.notifications.collectAsState(initial = emptyList())
    val isChargerArmed by com.example.security.AntiTheftManager.isChargerShieldArmed.collectAsState()
    val isMotionArmed by com.example.security.AntiTheftManager.isMotionShieldArmed.collectAsState()
    val isAlarmSounding by com.example.security.AntiTheftManager.isAlarmSounding.collectAsState()
    val memories by com.example.memory.MemoryStore.memories.collectAsState()
    val toolEngine = remember { ToolExecutionEngine(context) }

    // Initialize TTS and Notification Listener check
    LaunchedEffect(Unit) {
        NotificationStore.initTts(context)
        ZoyaForegroundService.onStateChange = { newState ->
            zoyaState = newState
            serviceStarted = (ZoyaForegroundService.activeService != null)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val hasMic = permissions[Manifest.permission.RECORD_AUDIO] == true
        if (hasMic) {
            val intent = Intent(context, ZoyaForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
            serviceStarted = true
            Toast.makeText(context, "Voice Core Activated", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Microphone permission is required", Toast.LENGTH_LONG).show()
        }
    }

    fun toggleVoiceAssistant() {
        if (serviceStarted) {
            val intent = Intent(context, ZoyaForegroundService::class.java)
            context.stopService(intent)
            serviceStarted = false
            zoyaState = ZoyaState.IDLE
        } else {
            if (apiKey.isEmpty()) {
                showSettingsDialog = true
                return
            }
            val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            val hasContacts = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
            val hasPhone = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

            if (hasMic && hasContacts && hasPhone) {
                val intent = Intent(context, ZoyaForegroundService::class.java)
                ContextCompat.startForegroundService(context, intent)
                serviceStarted = true
            } else {
                val permissionsList = mutableListOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.CALL_PHONE
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionsList.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                permissionLauncher.launch(permissionsList.toTypedArray())
            }
        }
    }

    fun sendUserQuery(text: String) {
        if (text.isBlank()) return
        val activeService = ZoyaForegroundService.activeService
        if (activeService != null) {
            activeService.sendTextMessage(text.trim())
        } else {
            toggleVoiceAssistant()
            scope.launch {
                delay(1000)
                ZoyaForegroundService.activeService?.sendTextMessage(text.trim())
            }
        }
        inputText = ""
        keyboardController?.hide()
    }

    fun readNotificationItem(notif: AppNotification) {
        isReadingNotificationsAloud = true
        NotificationStore.readNotificationAloud(notif) {
            isReadingNotificationsAloud = false
        }
        Toast.makeText(context, "Reading notification: ${notif.appName}", Toast.LENGTH_SHORT).show()
    }

    fun readAllNotifications() {
        isReadingNotificationsAloud = true
        NotificationStore.readAllNotificationsAloud {
            isReadingNotificationsAloud = false
        }
        Toast.makeText(context, "Reading all notifications...", Toast.LENGTH_SHORT).show()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBgDark)
    ) {
        // Deep Space Crimson Constellation Background Canvas
        CrimsonConstellationCanvas()

        // Main Screen View
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(bottom = 76.dp) // Leave room for custom bottom navigation
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (selectedTab) {
                0 -> {
                    // -------------------------------------------------------------
                    // TAB 0: HOME SCREEN (WITH IN-APP NOTIFICATION TICKER & PANEL)
                    // -------------------------------------------------------------
                    Spacer(modifier = Modifier.height(10.dp))

                    // 1. Header with Name & Notification Bell Icon
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Hello, $userName",
                                color = Color.White,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Serif,
                                fontStyle = FontStyle.Italic,
                                letterSpacing = 0.5.sp,
                                modifier = Modifier.testTag("greeting_title")
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "How can I assist you today?",
                                color = Color(0xFFC0C4D6),
                                fontSize = 16.sp,
                                fontFamily = FontFamily.Serif,
                                fontStyle = FontStyle.Italic,
                                letterSpacing = 0.2.sp
                            )
                        }

                        // Notification Badge Trigger Button
                        Surface(
                            onClick = { showNotificationSheet = true },
                            shape = CircleShape,
                            color = Color(0xFF141524),
                            border = BorderStroke(1.dp, Color(0x44FF2442)),
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("notification_bell_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (notifications.isNotEmpty()) Icons.Rounded.NotificationsActive else Icons.Rounded.Notifications,
                                    contentDescription = "Notifications",
                                    tint = if (notifications.isNotEmpty()) CrimsonPrimary else Color(0xFF94A3B8),
                                    modifier = Modifier.size(22.dp)
                                )
                                if (notifications.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = (-4).dp, y = 4.dp)
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(CrimsonPrimary)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 2. IN-APP TOP NOTIFICATION BAR (LIVE TICKER & QUICK READ)
                    TopInAppNotificationBar(
                        notifications = notifications,
                        onReadNotif = { notif -> readNotificationItem(notif) },
                        onReadAll = { readAllNotifications() },
                        onOpenHub = { showNotificationSheet = true },
                        onDismiss = { notifId -> NotificationStore.removeNotification(notifId) }
                    )

                    Spacer(modifier = Modifier.weight(0.12f))

                    // 3. Center Hero Glowing Red Voice Core with Orbital Gyroscope Arcs
                    CrimsonGyroOrb(
                        state = zoyaState,
                        isActive = serviceStarted,
                        onOrbClick = { toggleVoiceAssistant() }
                    )

                    Spacer(modifier = Modifier.weight(0.12f))

                    // 4. Search / Input Prompt Bar ("Ask MYRA anything...")
                    AskPromptBar(
                        inputText = inputText,
                        onInputChange = { inputText = it },
                        onSend = { sendUserQuery(inputText) }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 5. Two-Column Feature Cards (Voice Mode & Neural Lens)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Card 1: Voice Mode / Holographic Orb
                        FeatureActionCard(
                            title = "Voice Mode",
                            subtitle = "Holographic Orb",
                            icon = Icons.Rounded.GraphicEq,
                            iconTint = CrimsonPrimary,
                            isActive = serviceStarted,
                            modifier = Modifier.weight(1f),
                            onClick = { toggleVoiceAssistant() }
                        )

                        // Card 2: Neural Lens / Object Scan
                        FeatureActionCard(
                            title = "Neural Lens",
                            subtitle = "Object Scan",
                            icon = Icons.Rounded.PhotoCamera,
                            iconTint = CrimsonPrimary,
                            isActive = false,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                Toast.makeText(context, "Neural Lens initialized", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                }
                1 -> {
                    // -------------------------------------------------------------
                    // TAB 1: LIVE CHAT & CONVERSATION TRANSCRIPT
                    // -------------------------------------------------------------
                    ChatTranscriptTab(
                        messages = messages,
                        inputText = inputText,
                        onInputChange = { inputText = it },
                        onSend = { sendUserQuery(inputText) },
                        onClear = {
                            ZoyaForegroundService.clearMessages()
                        }
                    )
                }
                2 -> {
                    // -------------------------------------------------------------
                    // TAB 2: DISCOVER / SYSTEM QUICK TOOLS & NOTIFICATION HUB
                    // -------------------------------------------------------------
                    DiscoverToolsTab(
                        torchState = torchState,
                        notificationCount = notifications.size,
                        isChargerArmed = isChargerArmed,
                        isMotionArmed = isMotionArmed,
                        isAlarmSounding = isAlarmSounding,
                        memoryCount = memories.size,
                        onOpenNotificationHub = { showNotificationSheet = true },
                        onReadAllNotifications = { readAllNotifications() },
                        onToggleTorch = {
                            torchState = !torchState
                            scope.launch {
                                toolEngine.execute("toggleTorch", buildJsonObject {
                                    put("state", if (torchState) "on" else "off")
                                })
                            }
                        },
                        onToggleChargerShield = {
                            val msg = com.example.security.AntiTheftManager.toggleChargerShield()
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        },
                        onToggleMotionShield = {
                            val msg = com.example.security.AntiTheftManager.toggleMotionShield()
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        },
                        onStopAlarm = {
                            val msg = com.example.security.AntiTheftManager.stopAlarm()
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        },
                        onOpenApp = { pkg ->
                            scope.launch {
                                toolEngine.execute("openApp", buildJsonObject {
                                    put("packageName", pkg)
                                })
                            }
                        },
                        onMakeCall = {
                            scope.launch {
                                toolEngine.execute("searchAndCallContact", buildJsonObject {
                                    put("contactName", "")
                                    put("useDialer", true)
                                })
                            }
                        },
                        onExecuteTool = { toolName, argsMap ->
                            scope.launch {
                                val jsonArgs = buildJsonObject {
                                    argsMap.forEach { (k, v) -> put(k, v) }
                                }
                                val res = toolEngine.execute(toolName, jsonArgs)
                                Toast.makeText(context, res, Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                }
                3 -> {
                    // -------------------------------------------------------------
                    // TAB 3: SETTINGS & CONFIGURATION
                    // -------------------------------------------------------------
                    SettingsTab(
                        apiKey = apiKey,
                        userName = userName,
                        onSave = { newKey, newName ->
                            prefs.edit().putString("api_key", newKey).putString("user_name", newName).apply()
                            apiKey = newKey
                            userName = newName
                            Toast.makeText(context, "Configuration Saved", Toast.LENGTH_SHORT).show()
                        },
                        onOpenAccessibility = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            context.startActivity(intent)
                        },
                        onOpenNotificationListener = {
                            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            context.startActivity(intent)
                        }
                    )
                }
            }
        }

        // Custom Bottom Navigation Bar with Floating Elevated Center Orb
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            CustomBottomNavigationBar(
                selectedTab = selectedTab,
                onTabSelect = { tabIndex ->
                    if (tabIndex == 3 && showSettingsDialog) {
                        showSettingsDialog = false
                    }
                    selectedTab = tabIndex
                },
                isServiceActive = serviceStarted,
                onCenterOrbClick = {
                    toggleVoiceAssistant()
                }
            )
        }
    }

    // In-App Notification Center Bottom Sheet Modal
    if (showNotificationSheet) {
        ModalBottomSheet(
            onDismissRequest = { showNotificationSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xFF0F101A),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .width(42.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF334155))
                )
            }
        ) {
            InAppNotificationCenterContent(
                notifications = notifications,
                isReadingAloud = isReadingNotificationsAloud,
                onReadNotif = { notif -> readNotificationItem(notif) },
                onReadAll = { readAllNotifications() },
                onDismissNotif = { id -> NotificationStore.removeNotification(id) },
                onClearAll = { NotificationStore.clearAllNotifications() },
                onAddDemo = {
                    val now = System.currentTimeMillis()
                    val sample = AppNotification(
                        id = "demo_$now",
                        packageName = "com.whatsapp",
                        appName = "WhatsApp",
                        title = "Pooja Verma",
                        content = "Haan Manjesh, project presentation ready hai. Check kar lo!",
                        timestamp = now,
                        category = "chat"
                    )
                    NotificationStore.addNotification(sample)
                    Toast.makeText(context, "New Test Notification Added!", Toast.LENGTH_SHORT).show()
                },
                onOpenListenerSettings = {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                }
            )
        }
    }

    // Modal Settings Dialog if opened directly
    if (showSettingsDialog) {
        CyberSettingsModal(
            currentKey = apiKey,
            currentName = userName,
            onDismiss = { showSettingsDialog = false },
            onSave = { newKey, newName ->
                prefs.edit().putString("api_key", newKey).putString("user_name", newName).apply()
                apiKey = newKey
                userName = newName
                showSettingsDialog = false
                Toast.makeText(context, "API Key Saved", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

// -------------------------------------------------------------
// TOP IN-APP NOTIFICATION BAR / TICKER COMPONENT
// -------------------------------------------------------------
@Composable
fun TopInAppNotificationBar(
    notifications: List<AppNotification>,
    onReadNotif: (AppNotification) -> Unit,
    onReadAll: () -> Unit,
    onOpenHub: () -> Unit,
    onDismiss: (String) -> Unit
) {
    if (notifications.isEmpty()) {
        return
    }

    val latest = notifications.first()
    val count = notifications.size

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF101220),
        border = BorderStroke(1.dp, Color(0x44FF2442)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("top_notification_bar")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header Row: App Name, Count Badge, Read All, and Dismiss
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Category/App Color Dot
                    val badgeColor = when (latest.category) {
                        "chat" -> NeonEmerald
                        "payment" -> NeonAmber
                        "email" -> Color(0xFFEA4335)
                        "call" -> Color(0xFF38BDF8)
                        else -> CrimsonPrimary
                    }
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(badgeColor)
                    )
                    Text(
                        text = latest.appName,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF23253B))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "$count New",
                            color = CrimsonPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Quick "Sunao" (Read) Icon Button
                    Surface(
                        onClick = { onReadNotif(latest) },
                        shape = RoundedCornerShape(8.dp),
                        color = CrimsonPrimary.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, CrimsonPrimary.copy(alpha = 0.5f)),
                        modifier = Modifier.testTag("read_notification_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VolumeUp,
                                contentDescription = "Read Aloud",
                                tint = CrimsonPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Sunao",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Dismiss Button
                    IconButton(
                        onClick = { onDismiss(latest.id) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Body: Title & Content Preview (Clickable to open Hub)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenHub() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = latest.title,
                        color = Color(0xFFF1F5F9),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = latest.content,
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "View All ›",
                    color = CrimsonPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// -------------------------------------------------------------
// IN-APP NOTIFICATION CENTER BOTTOM SHEET CONTENT
// -------------------------------------------------------------
@Composable
fun InAppNotificationCenterContent(
    notifications: List<AppNotification>,
    isReadingAloud: Boolean,
    onReadNotif: (AppNotification) -> Unit,
    onReadAll: () -> Unit,
    onDismissNotif: (String) -> Unit,
    onClearAll: () -> Unit,
    onAddDemo: () -> Unit,
    onOpenListenerSettings: () -> Unit
) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf("all") }
    val isAccessGranted = remember { NotificationStore.isNotificationAccessGranted(context) }

    val filteredList = remember(notifications, selectedCategory) {
        if (selectedCategory == "all") notifications
        else notifications.filter { it.category == selectedCategory }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "In-App Notification Hub",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic
                )
                Text(
                    text = "${notifications.size} active alert(s) on device",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp
                )
            }

            if (notifications.isNotEmpty()) {
                TextButton(onClick = onClearAll) {
                    Text("Clear All", color = CrimsonPrimary, fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Big "Sabhi Notifications Padhkar Sunao" Action Bar
        Surface(
            onClick = onReadAll,
            shape = RoundedCornerShape(16.dp),
            color = CrimsonPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("read_all_notifications_button")
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.VolumeUp,
                    contentDescription = "Read All",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isReadingAloud) "Speaking Notifications..." else "Sabhi Notifications Sunao (Read All)",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Notification Listener Permission Status Alert Banner
        if (!isAccessGranted) {
            Surface(
                onClick = onOpenListenerSettings,
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF201815),
                border = BorderStroke(1.dp, NeonAmber.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "⚡ Real Notification Listener Disabled",
                            color = NeonAmber,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tap to grant notification access so assistant can read real alerts.",
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        text = "ENABLE",
                        color = NeonAmber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Category Filter Chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                NotificationFilterChip(
                    label = "All (${notifications.size})",
                    isSelected = selectedCategory == "all",
                    onClick = { selectedCategory = "all" }
                )
            }
            item {
                NotificationFilterChip(
                    label = "WhatsApp 💬",
                    isSelected = selectedCategory == "chat",
                    onClick = { selectedCategory = "chat" }
                )
            }
            item {
                NotificationFilterChip(
                    label = "Gmail ✉️",
                    isSelected = selectedCategory == "email",
                    onClick = { selectedCategory = "email" }
                )
            }
            item {
                NotificationFilterChip(
                    label = "Payments 💳",
                    isSelected = selectedCategory == "payment",
                    onClick = { selectedCategory = "payment" }
                )
            }
            item {
                NotificationFilterChip(
                    label = "Calls 📞",
                    isSelected = selectedCategory == "call",
                    onClick = { selectedCategory = "call" }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Notifications List
        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "No notifications in this category",
                        color = Color(0xFF64748B),
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Serif
                    )
                    TextButton(onClick = onAddDemo) {
                        Text("+ Add Test Notification", color = CrimsonPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredList, key = { it.id }) { notif ->
                    NotificationCardItem(
                        notification = notif,
                        onRead = { onReadNotif(notif) },
                        onDismiss = { onDismissNotif(notif.id) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(
                        onClick = onAddDemo,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("+ Add Test Alert For Assistant", color = Color(0xFF94A3B8), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationFilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) CrimsonPrimary else Color(0xFF141524),
        border = BorderStroke(1.dp, if (isSelected) CrimsonPrimary else Color(0x33475569))
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.White else Color(0xFF94A3B8),
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun NotificationCardItem(
    notification: AppNotification,
    onRead: () -> Unit,
    onDismiss: () -> Unit
) {
    val categoryIcon = when (notification.category) {
        "chat" -> Icons.Rounded.Chat
        "email" -> Icons.Rounded.Security
        "payment" -> Icons.Rounded.Payment
        "call" -> Icons.Rounded.Call
        else -> Icons.Rounded.Notifications
    }

    val categoryColor = when (notification.category) {
        "chat" -> NeonEmerald
        "email" -> Color(0xFFEA4335)
        "payment" -> NeonAmber
        "call" -> Color(0xFF38BDF8)
        else -> CrimsonPrimary
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141524)),
        border = BorderStroke(1.dp, Color(0x22FFFFFF)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(categoryColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = categoryIcon,
                            contentDescription = null,
                            tint = categoryColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = notification.appName,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Read Aloud "Sunao" Button
                    Surface(
                        onClick = onRead,
                        shape = RoundedCornerShape(8.dp),
                        color = CrimsonPrimary,
                        modifier = Modifier.testTag("read_card_${notification.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VolumeUp,
                                contentDescription = "Sunao",
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Sunao",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Dismiss Button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Dismiss",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = notification.title,
                color = Color(0xFFF1F5F9),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = notification.content,
                color = Color(0xFFCBD5E1),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}

// -------------------------------------------------------------
// CRIMSON CONSTELLATION & RED PARTICLES BACKGROUND CANVAS
// -------------------------------------------------------------
@Composable
fun CrimsonConstellationCanvas() {
    val infiniteTransition = rememberInfiniteTransition(label = "ConstellationPulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Deep obsidian background with subtle top-center and bottom-center red nebula glow
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF090A12),
                    Color(0xFF07070C),
                    Color(0xFF0C0911)
                )
            )
        )

        // Center Ambient Crimson Glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0x33FF1E44).copy(alpha = 0.25f * pulse),
                    Color(0x11FF1E44).copy(alpha = 0.10f * pulse),
                    Color.Transparent
                ),
                center = Offset(width / 2f, height * 0.42f),
                radius = width * 0.75f
            ),
            radius = width * 0.75f,
            center = Offset(width / 2f, height * 0.42f)
        )

        // Floating Embers / Star Particles matching reference design
        val particlePositions = listOf(
            Offset(width * 0.12f, height * 0.18f),
            Offset(width * 0.88f, height * 0.16f),
            Offset(width * 0.78f, height * 0.24f),
            Offset(width * 0.35f, height * 0.28f),
            Offset(width * 0.22f, height * 0.46f),
            Offset(width * 0.77f, height * 0.36f),
            Offset(width * 0.84f, height * 0.48f),
            Offset(width * 0.48f, height * 0.52f),
            Offset(width * 0.18f, height * 0.62f),
            Offset(width * 0.82f, height * 0.60f),
            Offset(width * 0.34f, height * 0.82f),
            Offset(width * 0.75f, height * 0.74f),
            Offset(width * 0.95f, height * 0.69f)
        )

        particlePositions.forEachIndexed { index, pos ->
            val particleAlpha = if (index % 2 == 0) pulse * 0.85f else (1.3f - pulse) * 0.75f
            val particleRadius = if (index % 3 == 0) 3.5f else 2.2f

            drawCircle(
                color = CrimsonPrimary.copy(alpha = particleAlpha),
                radius = particleRadius,
                center = pos
            )
            // Soft glow aura
            drawCircle(
                color = CrimsonGlow.copy(alpha = particleAlpha * 0.3f),
                radius = particleRadius * 2.8f,
                center = pos
            )
        }

        // Faint Constellation Network Lines
        val lineAlpha = 0.08f * pulse
        drawLine(
            color = CrimsonPrimary.copy(alpha = lineAlpha),
            start = Offset(width * 0.18f, height * 0.46f),
            end = Offset(width * 0.35f, height * 0.54f),
            strokeWidth = 1f
        )
        drawLine(
            color = CrimsonPrimary.copy(alpha = lineAlpha),
            start = Offset(width * 0.35f, height * 0.54f),
            end = Offset(width * 0.48f, height * 0.52f),
            strokeWidth = 1f
        )
        drawLine(
            color = CrimsonPrimary.copy(alpha = lineAlpha),
            start = Offset(width * 0.48f, height * 0.52f),
            end = Offset(width * 0.65f, height * 0.53f),
            strokeWidth = 1f
        )
        drawLine(
            color = CrimsonPrimary.copy(alpha = lineAlpha),
            start = Offset(width * 0.65f, height * 0.53f),
            end = Offset(width * 0.84f, height * 0.48f),
            strokeWidth = 1f
        )
    }
}

// -------------------------------------------------------------
// HERO CRIMSON GYROSCOPE VOICE ORB
// -------------------------------------------------------------
@Composable
fun CrimsonGyroOrb(
    state: ZoyaState,
    isActive: Boolean,
    onOrbClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "GyroSpin")
    val angle1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle1"
    )
    val angle2 by infiniteTransition.animateFloat(
        initialValue = 45f,
        targetValue = -315f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle2"
    )
    val angle3 by infiniteTransition.animateFloat(
        initialValue = 90f,
        targetValue = 450f,
        animationSpec = infiniteRepeatable(
            animation = tween(7500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle3"
    )
    val corePulse by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = if (state == ZoyaState.SPEAKING) 1.14f else if (state == ZoyaState.LISTENING) 1.08f else 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (state == ZoyaState.SPEAKING) 300 else if (state == ZoyaState.LISTENING) 600 else 1800,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "corePulse"
    )

    Box(
        modifier = Modifier
            .size(240.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOrbClick
            )
            .testTag("hero_voice_orb"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerOffset = center
            val sphereRadius = 48.dp.toPx() * (if (isActive) corePulse else 0.95f)
            val ringRadiusX = sphereRadius * 1.85f
            val ringRadiusY = sphereRadius * 0.72f

            // 1. Orbital Gyroscopic Rings (Broken Arcs in Crimson Red)
            fun drawBrokenArcRing(angle: Float, arcColor: Color, strokeWidth: Float) {
                rotate(angle, centerOffset) {
                    // Segment 1
                    drawArc(
                        color = arcColor,
                        startAngle = 10f,
                        sweepAngle = 100f,
                        useCenter = false,
                        topLeft = Offset(centerOffset.x - ringRadiusX, centerOffset.y - ringRadiusY),
                        size = Size(ringRadiusX * 2, ringRadiusY * 2),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    // Segment 2
                    drawArc(
                        color = arcColor.copy(alpha = 0.75f),
                        startAngle = 135f,
                        sweepAngle = 65f,
                        useCenter = false,
                        topLeft = Offset(centerOffset.x - ringRadiusX, centerOffset.y - ringRadiusY),
                        size = Size(ringRadiusX * 2, ringRadiusY * 2),
                        style = Stroke(width = strokeWidth * 0.9f, cap = StrokeCap.Round)
                    )
                    // Segment 3
                    drawArc(
                        color = arcColor,
                        startAngle = 230f,
                        sweepAngle = 90f,
                        useCenter = false,
                        topLeft = Offset(centerOffset.x - ringRadiusX, centerOffset.y - ringRadiusY),
                        size = Size(ringRadiusX * 2, ringRadiusY * 2),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Orbiting Red Bead Points
                    val rad1 = Math.toRadians((angle + 45).toDouble())
                    val p1x = centerOffset.x + (ringRadiusX * cos(rad1)).toFloat()
                    val p1y = centerOffset.y + (ringRadiusY * sin(rad1)).toFloat()
                    drawCircle(color = CrimsonPrimary, radius = strokeWidth * 1.2f, center = Offset(p1x, p1y))

                    val rad2 = Math.toRadians((angle + 210).toDouble())
                    val p2x = centerOffset.x + (ringRadiusX * cos(rad2)).toFloat()
                    val p2y = centerOffset.y + (ringRadiusY * sin(rad2)).toFloat()
                    drawCircle(color = Color.White, radius = strokeWidth * 0.9f, center = Offset(p2x, p2y))
                }
            }

            // Draw multi-axis Gyroscope Rings
            drawBrokenArcRing(angle1, CrimsonPrimary, 3.2f)
            drawBrokenArcRing(angle2, Color(0xFFFF2E54), 2.6f)
            drawBrokenArcRing(angle3, Color(0xFFCC1133), 3.0f)

            // 2. Concentric Outer Circle Arc
            drawArc(
                color = CrimsonPrimary.copy(alpha = 0.35f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(centerOffset.x - sphereRadius * 1.55f, centerOffset.y - sphereRadius * 1.55f),
                size = Size(sphereRadius * 3.1f, sphereRadius * 3.1f),
                style = Stroke(width = 1.2f)
            )

            // 3. Central Glowing Crimson 3D Sphere Core
            // Outer Glow Aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        CrimsonPrimary.copy(alpha = if (isActive) 0.65f else 0.35f),
                        CrimsonDark.copy(alpha = if (isActive) 0.35f else 0.15f),
                        Color.Transparent
                    ),
                    center = centerOffset,
                    radius = sphereRadius * 1.8f
                ),
                radius = sphereRadius * 1.8f,
                center = centerOffset
            )

            // Solid 3D Red Sphere
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFF5C77), // Specular light reflection
                        CrimsonOrbCore,     // Rich scarlet body
                        CrimsonDark,        // Shadow depth
                        Color(0xFF5A000D)   // Obsidian rim
                    ),
                    center = Offset(centerOffset.x - sphereRadius * 0.25f, centerOffset.y - sphereRadius * 0.25f),
                    radius = sphereRadius * 1.2f
                ),
                radius = sphereRadius,
                center = centerOffset
            )
        }

        // Center White Microphone Icon inside the Red Sphere
        Box(
            modifier = Modifier.size(44.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isActive) Icons.Rounded.Mic else Icons.Rounded.MicOff,
                contentDescription = "Voice Assistant State",
                tint = Color.White,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

// -------------------------------------------------------------
// ASK PROMPT INPUT BAR ("Ask MYRA anything...")
// -------------------------------------------------------------
@Composable
fun AskPromptBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF0F1018))
            .border(1.dp, Color(0x33FF2442), RoundedCornerShape(20.dp))
            .padding(start = 18.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = inputText,
            onValueChange = onInputChange,
            placeholder = {
                Text(
                    text = "Ask M.J anything...",
                    color = Color(0xFF6B7280),
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic
                )
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = CrimsonPrimary
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            modifier = Modifier
                .weight(1f)
                .testTag("ask_input_field")
        )

        // Red Send Arrow Button (➤)
        IconButton(
            onClick = onSend,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Transparent)
                .testTag("send_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
                tint = CrimsonPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// -------------------------------------------------------------
// TWO-COLUMN FEATURE ACTION CARDS (Voice Mode & Neural Lens)
// -------------------------------------------------------------
@Composable
fun FeatureActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0E0F17)
        ),
        border = BorderStroke(
            1.dp,
            if (isActive) CrimsonPrimary else Color(0x33FF2442)
        ),
        modifier = modifier
            .height(120.dp)
            .testTag("feature_card_${title.lowercase().replace(" ", "_")}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Icon
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(28.dp)
            )

            // Titles
            Column {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = Color(0xFF888E9E),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}

// -------------------------------------------------------------
// CUSTOM BOTTOM NAVIGATION BAR WITH ELEVATED CELESTIAL ORB
// -------------------------------------------------------------
@Composable
fun CustomBottomNavigationBar(
    selectedTab: Int,
    onTabSelect: (Int) -> Unit,
    isServiceActive: Boolean,
    onCenterOrbClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Dark Base Navigation Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            color = Color(0xFF0A0B12),
            border = BorderStroke(1.dp, Color(0x22334155))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. Home Item
                BottomNavItem(
                    label = "Home",
                    icon = Icons.Rounded.Home,
                    isSelected = selectedTab == 0,
                    activeColor = CrimsonPrimary,
                    inactiveColor = Color(0xFF64748B),
                    onClick = { onTabSelect(0) }
                )

                // 2. Chat Item
                BottomNavItem(
                    label = "Chat",
                    icon = Icons.Rounded.ChatBubbleOutline,
                    isSelected = selectedTab == 1,
                    activeColor = Color(0xFFA855F7),
                    inactiveColor = Color(0xFF64748B),
                    onClick = { onTabSelect(1) }
                )

                // Empty space for center floating orb
                Spacer(modifier = Modifier.width(64.dp))

                // 3. Discover Item
                BottomNavItem(
                    label = "Discover",
                    icon = Icons.Rounded.Explore,
                    isSelected = selectedTab == 2,
                    activeColor = Color(0xFF38BDF8),
                    inactiveColor = Color(0xFF64748B),
                    onClick = { onTabSelect(2) }
                )

                // 4. Settings Item
                BottomNavItem(
                    label = "Settings",
                    icon = Icons.Rounded.Settings,
                    isSelected = selectedTab == 3,
                    activeColor = Color(0xFF60A5FA),
                    inactiveColor = Color(0xFF64748B),
                    onClick = { onTabSelect(3) }
                )
            }
        }

        // Center Floating Glowing Nebula Orb Button
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-14).dp)
                .size(66.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onCenterOrbClick
                )
                .testTag("floating_center_orb"),
            contentAlignment = Alignment.Center
        ) {
            // Glowing Red Outer Ring Border
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0F1018))
                    .border(
                        BorderStroke(
                            2.5.dp,
                            if (isServiceActive) CrimsonPrimary else Color(0xFFFF1E44)
                        ),
                        CircleShape
                    )
            )

            // Inner Colorful Nebula / Fire Orb Canvas
            Canvas(modifier = Modifier.size(50.dp)) {
                val centerOffset = center
                val radius = size.minDimension / 2f

                // Swirling Nebula Radial Gradient
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFFD59E), // Core warmth
                            Color(0xFFFF7A45), // Solar orange
                            Color(0xFFE0245E), // Crimson magenta
                            Color(0xFF5E27CD), // Deep galaxy violet
                            Color(0xFF1B1464)  // Outer dark abyss
                        ),
                        center = Offset(centerOffset.x - radius * 0.2f, centerOffset.y - radius * 0.2f),
                        radius = radius * 1.15f
                    ),
                    radius = radius,
                    center = centerOffset
                )

                // Specular Light Sheen
                drawCircle(
                    color = Color.White.copy(alpha = 0.45f),
                    center = Offset(centerOffset.x - radius * 0.35f, centerOffset.y - radius * 0.35f),
                    radius = radius * 0.25f
                )
            }
        }
    }
}

@Composable
fun BottomNavItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("nav_tab_${label.lowercase()}")
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) activeColor else inactiveColor,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = if (isSelected) activeColor else inactiveColor,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic
        )
    }
}

// -------------------------------------------------------------
// TAB 1: CHAT & TRANSCRIPT TAB
// -------------------------------------------------------------
@Composable
fun ChatTranscriptTab(
    messages: List<String>,
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onClear: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Chat Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Conversation Feed",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic
                )
                Text(
                    text = "Real-time dialogue & actions",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Serif
                )
            }

            IconButton(
                onClick = onClear,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF171826))
            ) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Clear Chat",
                    tint = CrimsonPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Messages Feed
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF0D0E16))
                .border(1.dp, Color(0x22FF2442), RoundedCornerShape(18.dp))
                .padding(12.dp)
        ) {
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No dialogue yet.\nSpeak into the core or type a message below.",
                        color = Color(0xFF64748B),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic
                    )
                }
            } else {
                val cleanMessages = messages.filter { msg ->
                    !msg.startsWith("Server says: [") && !msg.startsWith("Server says: setupComplete") && !msg.startsWith("Server says: Setup") && !msg.startsWith("WebSocket")
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(cleanMessages) { msg ->
                        val isUser = msg.startsWith("You:")

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 16.dp,
                                            topEnd = 16.dp,
                                            bottomStart = if (isUser) 16.dp else 4.dp,
                                            bottomEnd = if (isUser) 4.dp else 16.dp
                                        )
                                    )
                                    .background(
                                        if (isUser) CrimsonPrimary.copy(alpha = 0.25f)
                                        else Color(0xFF1A1B2A)
                                    )
                                    .border(
                                        1.dp,
                                        if (isUser) CrimsonPrimary.copy(alpha = 0.5f) else Color(0x33475569),
                                        RoundedCornerShape(16.dp)
                                    )
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = msg,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Text prompt bar in chat
        AskPromptBar(
            inputText = inputText,
            onInputChange = onInputChange,
            onSend = onSend
        )
    }
}

// -------------------------------------------------------------
// TAB 2: DISCOVER / SYSTEM QUICK TOOLS TAB
// -------------------------------------------------------------
@Composable
fun DiscoverToolsTab(
    torchState: Boolean,
    notificationCount: Int,
    isChargerArmed: Boolean,
    isMotionArmed: Boolean,
    isAlarmSounding: Boolean,
    memoryCount: Int,
    onOpenNotificationHub: () -> Unit,
    onReadAllNotifications: () -> Unit,
    onToggleTorch: () -> Unit,
    onToggleChargerShield: () -> Unit,
    onToggleMotionShield: () -> Unit,
    onStopAlarm: () -> Unit,
    onOpenApp: (String) -> Unit,
    onMakeCall: () -> Unit,
    onExecuteTool: (String, Map<String, String>) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(modifier = Modifier.padding(top = 8.dp)) {
            Text(
                text = "System Discover",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic
            )
            Text(
                text = "Smart Automation, Security & Hardware Controls",
                color = Color(0xFF94A3B8),
                fontSize = 14.sp,
                fontFamily = FontFamily.Serif
            )
        }

        // Active Alarm Warning Banner (if alarm is ringing)
        if (isAlarmSounding) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF7F1D1D),
                border = BorderStroke(2.dp, Color(0xFFFF2442)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("🚨 SECURITY ALARM ACTIVE!", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Touch or charger disconnect detected!", color = Color(0xFFFCA5A5), fontSize = 12.sp)
                    }
                    Surface(
                        onClick = onStopAlarm,
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White
                    ) {
                        Text(
                            text = "STOP 🔕",
                            color = Color(0xFF991B1B),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // Notification Action Banner (only when notifications are present)
        if (notificationCount > 0) {
            Surface(
                onClick = onOpenNotificationHub,
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF141524),
                border = BorderStroke(1.dp, Color(0x44FF2442)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(CrimsonPrimary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.NotificationsActive,
                                contentDescription = "Notifications",
                                tint = CrimsonPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text("Notification Hub", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("$notificationCount new alerts", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        }
                    }

                    Surface(
                        onClick = onReadAllNotifications,
                        shape = RoundedCornerShape(10.dp),
                        color = CrimsonPrimary
                    ) {
                        Text(
                            text = "Sunao 🔊",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // SECTION 1: 🛡️ ANTI-THEFT GUARD
        Text("🛡️ Anti-Theft Guard", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickToolCard(
                title = if (isChargerArmed) "Charger: ARMED" else "Charger Shield",
                subtitle = if (isChargerArmed) "Unplug = Alarm 🔔" else "Tap to arm cable guard",
                icon = Icons.Rounded.Security,
                accentColor = if (isChargerArmed) NeonEmerald else Color(0xFF94A3B8),
                modifier = Modifier.weight(1f),
                onClick = onToggleChargerShield
            )
            QuickToolCard(
                title = if (isMotionArmed) "Motion: ARMED" else "Motion Guard",
                subtitle = if (isMotionArmed) "Touch = Alarm 🚨" else "Tap to arm surface alert",
                icon = Icons.Rounded.Security,
                accentColor = if (isMotionArmed) NeonAmber else Color(0xFF94A3B8),
                modifier = Modifier.weight(1f),
                onClick = onToggleMotionShield
            )
        }

        // SECTION 2: 📸 CAMERA & SCREEN ANALYSIS
        Text("📸 Camera & Vision", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickToolCard(
                title = "Front Selfie 🤳",
                subtitle = "Front camera",
                icon = Icons.Rounded.PhotoCamera,
                accentColor = CrimsonPrimary,
                modifier = Modifier.weight(1f),
                onClick = { onExecuteTool("openCamera", mapOf("mode" to "selfie")) }
            )
            QuickToolCard(
                title = "Screen Scan 📱",
                subtitle = "Extract visible text",
                icon = Icons.Rounded.GraphicEq,
                accentColor = Color(0xFF38BDF8),
                modifier = Modifier.weight(1f),
                onClick = { onExecuteTool("readScreenText", emptyMap()) }
            )
        }

        // SECTION 3: 🎵 SPOTIFY, YOUTUBE & REELS
        Text("🎵 Media & Streaming", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickToolCard(
                title = "Spotify 🎵",
                subtitle = "Play music",
                icon = Icons.Rounded.PlayArrow,
                accentColor = NeonEmerald,
                modifier = Modifier.weight(1f),
                onClick = { onExecuteTool("playSpotify", mapOf("query" to "Top Hindi Hits")) }
            )
            QuickToolCard(
                title = "YouTube ▶️",
                subtitle = "Search videos",
                icon = Icons.Rounded.PlayArrow,
                accentColor = Color(0xFFFF0033),
                modifier = Modifier.weight(1f),
                onClick = { onOpenApp("YouTube") }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickToolCard(
                title = "Next Reel ↕️",
                subtitle = "Auto scroll Shorts",
                icon = Icons.Rounded.GraphicEq,
                accentColor = NeonAmber,
                modifier = Modifier.weight(1f),
                onClick = { onExecuteTool("scrollScreen", mapOf("direction" to "reel")) }
            )
            QuickToolCard(
                title = "Media Toggle ⏯️",
                subtitle = "Play / Pause",
                icon = Icons.Rounded.PlayArrow,
                accentColor = Color.White,
                modifier = Modifier.weight(1f),
                onClick = { onExecuteTool("mediaControl", mapOf("action" to "play_pause")) }
            )
        }

        // SECTION 4: 🧠 PERSISTENT MEMORY
        Text("🧠 Persistent Memory", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF101220),
            border = BorderStroke(1.dp, Color(0x33475569)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("$memoryCount Stored Facts", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("M.J remembers your facts & preferences across sessions", color = Color(0xFF94A3B8), fontSize = 11.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        onClick = { onExecuteTool("recallMemory", emptyMap()) },
                        shape = RoundedCornerShape(8.dp),
                        color = CrimsonPrimary.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, CrimsonPrimary.copy(alpha = 0.5f))
                    ) {
                        Text("View", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                    Surface(
                        onClick = { onExecuteTool("clearMemory", emptyMap()) },
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1E293B)
                    ) {
                        Text("Clear", color = Color(0xFF94A3B8), fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
                    }
                }
            }
        }

        // SECTION 5: 📅 SYSTEM, WEATHER & ALARM
        Text("📅 System & Hardware", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickToolCard(
                title = if (torchState) "Torch ON" else "Torch 💡",
                subtitle = "Toggle flashlight",
                icon = Icons.Rounded.FlashlightOn,
                accentColor = if (torchState) NeonAmber else CrimsonPrimary,
                modifier = Modifier.weight(1f),
                onClick = onToggleTorch
            )
            QuickToolCard(
                title = "Battery & Time ⚡",
                subtitle = "Device health",
                icon = Icons.Rounded.Settings,
                accentColor = Color(0xFF38BDF8),
                modifier = Modifier.weight(1f),
                onClick = { onExecuteTool("getSystemInfo", emptyMap()) }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickToolCard(
                title = "Weather 🌤️",
                subtitle = "Live city weather",
                icon = Icons.Rounded.GraphicEq,
                accentColor = NeonAmber,
                modifier = Modifier.weight(1f),
                onClick = { onExecuteTool("getWeather", mapOf("city" to "Delhi")) }
            )
            QuickToolCard(
                title = "5m Timer ⏰",
                subtitle = "Countdown timer",
                icon = Icons.Rounded.Notifications,
                accentColor = CrimsonPrimary,
                modifier = Modifier.weight(1f),
                onClick = { onExecuteTool("setTimer", mapOf("seconds" to "300", "label" to "Quick Timer")) }
            )
        }

        // SECTION 6: 📱 CALLS & WHATSAPP
        Text("💬 Calls & WhatsApp", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickToolCard(
                title = "WhatsApp 💬",
                subtitle = "Message hub",
                icon = Icons.Rounded.Chat,
                accentColor = NeonEmerald,
                modifier = Modifier.weight(1f),
                onClick = { onOpenApp("WhatsApp") }
            )
            QuickToolCard(
                title = "Phone Call 📞",
                subtitle = "Contact dialer",
                icon = Icons.Rounded.Call,
                accentColor = Color(0xFF38BDF8),
                modifier = Modifier.weight(1f),
                onClick = onMakeCall
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun QuickToolCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10111D)),
        border = BorderStroke(1.dp, Color(0x33475569)),
        modifier = modifier.height(100.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = accentColor,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(text = title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(text = subtitle, color = Color(0xFF94A3B8), fontSize = 11.sp)
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 3: SETTINGS TAB
// -------------------------------------------------------------
@Composable
fun SettingsTab(
    apiKey: String,
    userName: String,
    onSave: (String, String) -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenNotificationListener: () -> Unit
) {
    var keyInput by remember { mutableStateOf(apiKey) }
    var nameInput by remember { mutableStateOf(userName) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(modifier = Modifier.padding(top = 8.dp)) {
            Text(
                text = "Preferences",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic
            )
            Text(
                text = "Assistant intelligence & listener access",
                color = Color(0xFF94A3B8),
                fontSize = 14.sp,
                fontFamily = FontFamily.Serif
            )
        }

        // Name input
        Column {
            Text("Your Name", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CrimsonPrimary,
                    unfocusedBorderColor = Color(0x33FF2442),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Gemini API Key
        Column {
            Text("Gemini API Key", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CrimsonPrimary,
                    unfocusedBorderColor = Color(0x33FF2442),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Notification Listener Access Button
        Surface(
            onClick = onOpenNotificationListener,
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF141524),
            border = BorderStroke(1.dp, Color(0x44FF2442)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Notification Listener Service", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Read device notifications automatically in app", color = Color(0xFF94A3B8), fontSize = 11.sp)
                }
                Text("OPEN", color = CrimsonPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        // Accessibility Service Button
        Surface(
            onClick = onOpenAccessibility,
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF141524),
            border = BorderStroke(1.dp, Color(0x33475569)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Accessibility Service", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Enable automatic WhatsApp sending & clicks", color = Color(0xFF94A3B8), fontSize = 11.sp)
                }
                Text("OPEN", color = CrimsonPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Save Button
        Surface(
            onClick = { onSave(keyInput, nameInput) },
            shape = RoundedCornerShape(16.dp),
            color = CrimsonPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "Save Configuration",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// -------------------------------------------------------------
// SETTINGS MODAL DIALOG
// -------------------------------------------------------------
@Composable
fun CyberSettingsModal(
    currentKey: String,
    currentName: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var key by remember { mutableStateOf(currentKey) }
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F101A),
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = "Assistant Setup",
                color = Color.White,
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                fontSize = 20.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display Name", color = Color(0xFF94A3B8)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CrimsonPrimary,
                        unfocusedBorderColor = Color(0x33FF2442),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Gemini API Key", color = Color(0xFF94A3B8)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CrimsonPrimary,
                        unfocusedBorderColor = Color(0x33FF2442),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(key, name) }) {
                Text("Save", color = CrimsonPrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF94A3B8))
            }
        }
    )
}
