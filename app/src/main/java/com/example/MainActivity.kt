package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.ui.ZoyaScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
           Log.i("ZoyaDiagnostic", "All permissions granted.")
        } else {
           Log.e("ZoyaDiagnostic", "Some permissions denied.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        Log.i("ZoyaDiagnostic", "MainActivity onCreate started")
        
        try {
            val cacheDir = java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/js")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            cacheDir.setReadable(true, false)
            cacheDir.setWritable(true, false)
            cacheDir.setExecutable(true, false)
        } catch (e: Exception) {
            Log.w("ZoyaDiagnostic", "Failed to pre-create WebView cache dir", e)
        }

        checkPermissions()
        startDiagnosticLogging()
        com.example.chat.ChatRepository.init(this)
        com.example.voice.VoiceSpeaker.init(this)
        com.example.security.AntiTheftManager.init(this)
        com.example.memory.MemoryStore.init(this)

        setContent {
            MyApplicationTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    com.example.ui.JarvisHudScreen()
                }
            }
        }
    }
    
    private fun startDiagnosticLogging() {
        CoroutineScope(Dispatchers.Main).launch {
            while(true) {
                val service = ZoyaForegroundService.activeService
                if (service != null) {
                    Log.d("ZoyaDiagnostic", "STATUS REPORT: Service Running=${service != null}, State=${com.example.ZoyaForegroundService.currentState.name}")
                } else {
                    Log.d("ZoyaDiagnostic", "STATUS REPORT: Service Not Running")
                }
                delay(3000)
            }
        }
    }

    private fun checkPermissions() {

        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }
}
