package com.chewanninja.pricetrackermy

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun MoreScreen(vm: AppVM) {
    Column(Modifier.fillMaxSize().background(Bg).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("More", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        listOf(
            Triple(Screen.Shopping, "Shopping list", "Plan purchases and compare with your remaining budget"),
            Triple(Screen.Reports, "Reports", "Monthly report with CSV and PDF export"),
            Triple(Screen.Settings, "Settings", "Budget, alerts, categories, Google Sheets, app updates")
        ).forEach { (s, title, sub) ->
            Card(Modifier.fillMaxWidth().clickable { vm.screen = s }, colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(icon(s), title, tint = Brand)
                    Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(sub, fontSize = 12.sp, color = Color.Gray) }
                }
            }
        }
        Text("Version ${vm.versionText}", fontSize = 11.sp, color = Color.Gray)
    }
}
