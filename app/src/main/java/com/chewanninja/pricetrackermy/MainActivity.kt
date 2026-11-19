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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
                primary = Brand, onPrimary = Color.White, primaryContainer = Color(0xFFDCE6FF),
                background = Bg, surface = Color.White, surfaceVariant = Color(0xFFE9EEFB))) { Root(vm) }
        }
    }
}

fun icon(s: Screen): ImageVector = when (s) {
    Screen.Dashboard -> Icons.Default.Home; Screen.Scan -> Icons.Default.Add; Screen.Expenses -> Icons.Default.List
    Screen.Prices -> Icons.Default.Search; Screen.Shopping -> Icons.Default.ShoppingCart
    Screen.Reports -> Icons.Default.DateRange; Screen.Settings -> Icons.Default.Settings; Screen.More -> Icons.Default.Menu
}

private val SUB = setOf(Screen.Shopping, Screen.Reports, Screen.Settings)

private fun titleOf(s: Screen) = when (s) {
    Screen.Dashboard -> "Home"; Screen.Scan -> "Scan receipt"; Screen.Expenses -> "Expenses"; Screen.Prices -> "Price tracker"
    Screen.More -> "More"; Screen.Shopping -> "Shopping list"; Screen.Reports -> "Monthly report"; Screen.Settings -> "Settings"
}

@Composable fun BottomBar(vm: AppVM) {
    Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).navigationBarsPadding().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        listOf(Screen.Dashboard, Screen.Expenses, Screen.Scan, Screen.Prices, Screen.More).forEach { s ->
            val sel = vm.screen == s || (s == Screen.More && vm.screen in SUB)
            val tint = if (sel) Brand else Color(0xFF9AA3B2)
            Box(Modifier.weight(1f).clickable { vm.screen = s; vm.openList = null }, contentAlignment = Alignment.Center) {
                if (s == Screen.Scan) Box(Modifier.size(54.dp).shadow(6.dp, CircleShape).background(Brand, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(icon(s), "Scan receipt", tint = Color.White)
                } else Column(Modifier.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(icon(s), s.label, tint = tint); Text(s.label, fontSize = 10.sp, color = tint, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable fun Root(vm: AppVM) {
    val d = vm.draft
    BackHandler(enabled = d != null) { vm.draft = null }
    BackHandler(enabled = d == null && vm.openList == null && vm.screen in SUB) { vm.screen = Screen.More }
    Scaffold(containerColor = Bg, bottomBar = { if (d == null) BottomBar(vm) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (d != null) GradientHeader(if (d.isEdit) "Edit receipt" else "Review receipt", { vm.draft = null })
            else if (vm.screen != Screen.Dashboard) GradientHeader(titleOf(vm.screen), if (vm.screen in SUB) ({ vm.screen = Screen.More }) else null)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (d != null) ReviewScreen(vm, d) else when (vm.screen) {
                    Screen.Dashboard -> DashboardScreen(vm); Screen.Scan -> ScanScreen(vm); Screen.Expenses -> ExpensesScreen(vm)
                    Screen.Prices -> PricesScreen(vm); Screen.Shopping -> ShoppingScreen(vm); Screen.Reports -> ReportsScreen(vm)
                    Screen.Settings -> SettingsScreen(vm); Screen.More -> MoreScreen(vm)
                }
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
