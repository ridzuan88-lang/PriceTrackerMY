package com.chewanninja.pricetrackermy

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

@Composable fun DashboardScreen(vm: AppVM) {
    val exp by vm.expenses.collectAsState(); val items by vm.items.collectAsState(); val budgets by vm.budgets.collectAsState()
    val cats by vm.categories.collectAsState(); val st by vm.settings.collectAsState(); val jobs by vm.pendingJobs.collectAsState()
    val dash = remember(exp, items, budgets, cats, st) { Fin.dash(exp, items, budgets, cats, st, LocalDate.now()) }
    val col = LevelColors[dash.level]
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Grocery budget", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(Fin.LABELS[dash.level], color = col, fontWeight = FontWeight.Bold)
                Text("${rm(dash.spent)} of ${rm(dash.budget)}", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Bar(if (dash.budget > 0) (dash.pct / 100).toFloat() else 0f, col)
                Text(dash.message)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat("Remaining", rm(dash.remaining)); Stat("Used", "${dash.pct.toInt()}%"); Stat("Trips", "${dash.trips}")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat("Daily average", rm(dash.dailyAvg)); Stat("Safe daily spend", rm(dash.recDaily)); Stat("Projected", rm(dash.projected))
                }
            } }
        }
        if (dash.weeklyBudget > 0 || dash.savings > 0) item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (dash.weeklyBudget > 0) {
                    Text("This week: ${rm(dash.weekSpent)} of ${rm(dash.weeklyBudget)}", fontWeight = FontWeight.Bold)
                    Bar((dash.weekPct / 100).toFloat(), LevelColors[Fin.level(dash.weekPct, Fin.thresholds(st))])
                }
                if (dash.savings > 0) Text("Savings target ${rm(dash.savings)} - spend at most ${rm(dash.budget - dash.savings)} this month to reach it.")
            } }
        }
        item { Section("Spending by category (this month)") }
        items(dash.cats.filter { it.spent > 0 || it.budget > 0 }.size) { i ->
            val c = dash.cats.filter { it.spent > 0 || it.budget > 0 }[i]
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(c.name); Text(if (c.budget > 0) "${rm(c.spent)} / ${rm(c.budget)}" else rm(c.spent))
                }
                Bar(if (c.budget > 0) (c.spent / c.budget).toFloat() else if (dash.spent > 0) (c.spent / dash.spent).toFloat() else 0f,
                    if (c.budget > 0 && c.spent > c.budget) LevelColors[4] else hex(c.color))
            }
        }
        item { Section("Smart insights") }
        items(dash.insights.size) { i -> Text("• ${dash.insights[i]}") }
        item {
            if (jobs > 0) Text("$jobs item(s) waiting to sync" + (vm.lastError.value?.let { " - last error: $it" } ?: ""), color = Orange)
            Spacer(Modifier.height(8.dp))
            Button({ vm.screen = Screen.Scan }, Modifier.fillMaxWidth()) { Text("Scan a receipt") }
        }
    }
}

@Composable fun Stat(label: String, value: String) {
    Column { Text(label, fontSize = 11.sp, color = androidx.compose.ui.graphics.Color.Gray); Text(value, fontWeight = FontWeight.Bold) }
}
