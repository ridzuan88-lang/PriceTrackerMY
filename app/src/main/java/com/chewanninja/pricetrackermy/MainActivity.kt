package com.chewanninja.pricetrackermy

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val vm: AppVM by viewModels()
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onStart() { super.onStart(); vm.checkUpdate(false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Brand, onPrimary = Color.White, primaryContainer = Color(0xFFD6F0E5),
                background = Bg, surface = Color.White, surfaceVariant = Color(0xFFEAF0EC))) { Root(vm) }
        }
    }
}

fun icon(s: Screen): ImageVector = when (s) {
    Screen.Dashboard -> Icons.Default.Home; Screen.Scan -> Icons.Default.Add; Screen.Expenses -> Icons.Default.List
    Screen.Prices -> Icons.Default.Search; Screen.Shopping -> Icons.Default.ShoppingCart
    Screen.Reports -> Icons.Default.DateRange; Screen.Settings -> Icons.Default.Settings; Screen.More -> Icons.Default.Menu
}

@Composable fun Root(vm: AppVM) {
    val d = vm.draft
    BackHandler(enabled = d != null) { vm.draft = null }
    BackHandler(enabled = d == null && vm.openList == null && vm.screen in setOf(Screen.Shopping, Screen.Reports, Screen.Settings)) { vm.screen = Screen.More }
    val tabs = listOf(Screen.Dashboard, Screen.Expenses, Screen.Scan, Screen.Prices, Screen.More)
    Scaffold(containerColor = Bg, bottomBar = {
        if (d == null) NavigationBar(containerColor = Color.White) {
            tabs.forEach { s ->
                val sel = vm.screen == s || (s == Screen.More && vm.screen in setOf(Screen.Shopping, Screen.Reports, Screen.Settings))
                NavigationBarItem(selected = sel, onClick = { vm.screen = s; vm.openList = null },
                    icon = { Icon(icon(s), s.label) }, label = { Text(s.label, fontSize = 11.sp) })
            }
        }
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (d != null) ReviewScreen(vm, d) else when (vm.screen) {
                Screen.Dashboard -> DashboardScreen(vm); Screen.Scan -> ScanScreen(vm); Screen.Expenses -> ExpensesScreen(vm)
                Screen.Prices -> PricesScreen(vm); Screen.Shopping -> ShoppingScreen(vm); Screen.Reports -> ReportsScreen(vm)
                Screen.Settings -> SettingsScreen(vm); Screen.More -> MoreScreen(vm)
            }
        }
    }
    vm.message?.let { m -> AlertDialog(onDismissRequest = { vm.message = null }, confirmButton = { TextButton({ vm.message = null }) { Text("OK") } }, text = { Text(m) }) }
    vm.update?.let { u ->
        if (vm.updateProgress == null) AlertDialog(
            onDismissRequest = { vm.dismissUpdate() }, title = { Text("Update available") },
            text = { Text("New version: ${u.name}\nYou have: ${vm.versionText}" + (u.notes.takeIf { it.isNotBlank() }?.let { "\n\n$it" } ?: "")) },
            confirmButton = { TextButton({ vm.installUpdate() }) { Text("Update now") } },
            dismissButton = { TextButton({ vm.dismissUpdate() }) { Text("Later") } })
    }
    vm.updateProgress?.let { p ->
        AlertDialog(onDismissRequest = {}, confirmButton = {}, title = { Text("Downloading update…") },
            text = { LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth()) })
    }
    if (vm.busy.isNotEmpty()) AlertDialog(onDismissRequest = {}, confirmButton = {}, text = {
        Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(28.dp)); Spacer(Modifier.width(16.dp)); Text(vm.busy) }
    })
}
