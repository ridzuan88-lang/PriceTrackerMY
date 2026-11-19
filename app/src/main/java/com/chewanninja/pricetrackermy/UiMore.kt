package com.chewanninja.pricetrackermy

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
        listOf(
            Triple(Screen.Shopping, "🛒 Shopping list" to Color(0xFFFB8C00), "Plan purchases and compare with your remaining budget"),
            Triple(Screen.Reports, "📊 Reports" to Color(0xFF8E24AA), "Monthly report with CSV and PDF export"),
            Triple(Screen.Settings, "⚙️ Settings" to Brand, "Budget, alerts, categories, Google Sheets, app updates")
        ).forEach { (s, titleColor, sub) ->
            Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).clickable { vm.screen = s }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconTile(titleColor.first.substringBefore(' '), titleColor.second, 44.dp)
                Column(Modifier.weight(1f)) {
                    Text(titleColor.first.substringAfter(' '), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(sub, fontSize = 12.sp, color = Color.Gray)
                }
            }
        }
        Text("Version ${vm.versionText}", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 6.dp))
    }
}
