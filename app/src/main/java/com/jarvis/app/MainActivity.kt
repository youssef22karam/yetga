package com.jarvis.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.jarvis.app.navigation.Screen
import com.jarvis.app.service.JarvisService
import com.jarvis.app.ui.screens.*
import com.jarvis.app.ui.theme.*
import com.jarvis.app.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()
    private val grantedPerms = mutableStateMapOf<String, Boolean>()

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> results.forEach { (p, g) -> grantedPerms[p] = g } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        refreshPermissions()
        tryStartService()

        setContent {
            JarvisTheme {
                // showMain is a boolean state that becomes true once user grants or skips
                var showMain by remember { mutableStateOf(allGranted()) }

                if (showMain) {
                    JarvisApp(vm)
                } else {
                    PermissionScreen(
                        items  = permItems(),
                        onGrant = { permLauncher.launch(neededPerms().toTypedArray()) },
                        onSkip  = { showMain = true }
                    )
                    // Auto-advance when permissions are all granted
                    LaunchedEffect(grantedPerms.toMap()) {
                        if (allGranted()) showMain = true
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
    }

    private fun refreshPermissions() {
        neededPerms().forEach { p ->
            grantedPerms[p] = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun allGranted() = neededPerms().all { grantedPerms[it] == true }

    private fun neededPerms(): List<String> = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            add(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun permItems() = neededPerms().map { p ->
        PermissionItem(p,
            label   = when (p) {
                Manifest.permission.RECORD_AUDIO       -> "Microphone"
                Manifest.permission.POST_NOTIFICATIONS -> "Notifications"
                else -> p.substringAfterLast('.')
            },
            icon    = when (p) {
                Manifest.permission.RECORD_AUDIO       -> Icons.Default.Mic
                Manifest.permission.POST_NOTIFICATIONS -> Icons.Default.Notifications
                else -> Icons.Default.Lock
            },
            reason  = when (p) {
                Manifest.permission.RECORD_AUDIO       -> "Required for voice commands to JARVIS"
                Manifest.permission.POST_NOTIFICATIONS -> "Shows JARVIS status while running in background"
                else -> "Required for app functionality"
            },
            granted = grantedPerms[p] == true
        )
    }

    private fun tryStartService() {
        try {
            val i = Intent(this, JarvisService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i)
            else startService(i)
        } catch (_: Exception) {}
    }
}

// ── Data class ────────────────────────────────────────────────────────────────
data class PermissionItem(
    val permission: String,
    val label: String,
    val icon: ImageVector,
    val reason: String,
    val granted: Boolean
)

// ── Permission Screen ─────────────────────────────────────────────────────────
@Composable
fun PermissionScreen(
    items: List<PermissionItem>,
    onGrant: () -> Unit,
    onSkip: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(JarvisBg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Box(
                modifier = Modifier.size(80.dp)
                    .background(JarvisBlue.copy(0.15f), CircleShape)
                    .border(2.dp, JarvisBlue, CircleShape),
                contentAlignment = Alignment.Center
            ) { Text("⬡", fontSize = 36.sp, color = JarvisBlue) }

            Text("J.A.R.V.I.S needs access",
                style = MaterialTheme.typography.headlineMedium,
                color = JarvisBlue, textAlign = TextAlign.Center)
            Text("Grant these permissions so JARVIS can hear you.",
                style = MaterialTheme.typography.bodyMedium,
                color = JarvisTextMuted, textAlign = TextAlign.Center)

            items.forEach { item ->
                Surface(
                    color  = JarvisSurface,
                    shape  = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp,
                        if (item.granted) JarvisBlue.copy(0.5f) else JarvisBlueDark.copy(0.3f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(44.dp)
                                .background(
                                    if (item.granted) JarvisBlue.copy(0.2f) else JarvisTextMuted.copy(0.1f),
                                    RoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(item.icon, null,
                                tint = if (item.granted) JarvisBlue else JarvisTextMuted,
                                modifier = Modifier.size(22.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.label,
                                style = MaterialTheme.typography.titleMedium, color = JarvisText)
                            Text(item.reason,
                                style = MaterialTheme.typography.bodySmall, color = JarvisTextMuted)
                        }
                        if (item.granted)
                            Icon(Icons.Default.CheckCircle, null,
                                tint = JarvisBlue, modifier = Modifier.size(22.dp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onGrant,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = JarvisBlue),
                shape  = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Security, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Grant Permissions", style = MaterialTheme.typography.titleMedium)
            }

            TextButton(onClick = onSkip) {
                Text("Continue without microphone →",
                    color = JarvisTextMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ── Main App Shell ────────────────────────────────────────────────────────────
@Composable
fun JarvisApp(vm: MainViewModel) {
    val navController = rememberNavController()
    val navItems = listOf(
        Triple(Screen.Chat,     Icons.Default.Chat,          "Chat"),
        Triple(Screen.Models,   Icons.Default.Storage,       "Models"),
        Triple(Screen.Download, Icons.Default.CloudDownload, "Download"),
        Triple(Screen.Settings, Icons.Default.Settings,      "Settings")
    )
    Scaffold(
        containerColor = JarvisBg,
        bottomBar = {
            NavigationBar(containerColor = JarvisSurface, tonalElevation = 0.dp) {
                val back by navController.currentBackStackEntryAsState()
                val cur = back?.destination
                navItems.forEach { (screen, icon, label) ->
                    val sel = cur?.hierarchy?.any { it.route == screen.route } == true
                    NavigationBarItem(
                        selected = sel,
                        onClick  = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true; restoreState = true
                            }
                        },
                        icon  = { Icon(icon, label, modifier = Modifier.size(if (sel) 26.dp else 22.dp)) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor   = JarvisBlue,
                            selectedTextColor   = JarvisBlue,
                            unselectedIconColor = JarvisTextMuted,
                            unselectedTextColor = JarvisTextMuted,
                            indicatorColor      = JarvisBlue.copy(0.15f)
                        )
                    )
                }
            }
        }
    ) { pad ->
        NavHost(navController, Screen.Chat.route, Modifier.padding(pad)) {
            composable(Screen.Chat.route)     { ChatScreen(vm) }
            composable(Screen.Models.route)   { ModelsScreen(vm) }
            composable(Screen.Download.route) { DownloadScreen(vm) }
            composable(Screen.Settings.route) { SettingsScreen(vm) }
        }
    }
}
