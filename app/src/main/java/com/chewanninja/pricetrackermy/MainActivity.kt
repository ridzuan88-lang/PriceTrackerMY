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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF1B7F5C))) { Root(vm) } }
    }
}

private fun icon(s: Screen): ImageVector = when (s) {
    Screen.Dashboard -> Icons.Default.Home; Screen.Scan -> Icons.Default.Add; Screen.Expenses -> Icons.Default.List
    Screen.Prices -> Icons.Default.Search; Screen.Shopping -> Icons.Default.ShoppingCart
    Screen.Reports -> Icons.Default.DateRange; Screen.Settings -> Icons.Default.Settings
}

@Composable fun Root(vm: AppVM) {
    val d = vm.draft
    BackHandler(enabled = d != null) { vm.draft = null }
    Scaffold(bottomBar = {
        if (d == null) Surface(tonalElevation = 3.dp) {
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                Screen.values().forEach { s ->
                    val sel = vm.screen == s
                    Column(Modifier.width(84.dp).clickable { vm.screen = s; vm.openList = null }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(icon(s), s.label, tint = if (sel) MaterialTheme.colorScheme.primary else Color.Gray)
                        Text(s.label, fontSize = 11.sp, color = if (sel) MaterialTheme.colorScheme.primary else Color.Gray)
                    }
                }
            }
        }
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (d != null) ReviewScreen(vm, d) else when (vm.screen) {
                Screen.Dashboard -> DashboardScreen(vm); Screen.Scan -> ScanScreen(vm); Screen.Expenses -> ExpensesScreen(vm)
                Screen.Prices -> PricesScreen(vm); Screen.Shopping -> ShoppingScreen(vm); Screen.Reports -> ReportsScreen(vm)
                Screen.Settings -> SettingsScreen(vm)
            }
        }
    }
    vm.message?.let { m -> AlertDialog(onDismissRequest = { vm.message = null }, confirmButton = { TextButton({ vm.message = null }) { Text("OK") } }, text = { Text(m) }) }
    if (vm.busy.isNotEmpty()) AlertDialog(onDismissRequest = {}, confirmButton = {}, text = {
        Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(28.dp)); Spacer(Modifier.width(16.dp)); Text(vm.busy) }
    })
}
