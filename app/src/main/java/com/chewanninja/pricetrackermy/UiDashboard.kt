package com.chewanninja.pricetrackermy

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable fun DashboardScreen(vm: AppVM) {
    val exp by vm.expenses.collectAsState(); val items by vm.items.collectAsState(); val budgets by vm.budgets.collectAsState()
    val cats by vm.categories.collectAsState(); val st by vm.settings.collectAsState(); val jobs by vm.pendingJobs.collectAsState()
    val today = LocalDate.now()
    val dash = remember(exp, items, budgets, cats, st) { Fin.dash(exp, items, budgets, cats, st, today) }
    val col = LevelColors[dash.level]
    var chart by remember { mutableStateOf(0) }
    var allCats by remember { mutableStateOf(false) }
    val daily = remember(exp) { (1..today.dayOfMonth).map { d -> val dd = today.withDayOfMonth(d); Fin.spentIn(exp, dd.toEpochDay(), dd.toEpochDay()) } }
    val dailyLabels = (1..today.dayOfMonth).map { if (it == 1 || it % 5 == 0) "$it" else "" }
    val weekly = remember(exp) { Fin.buckets(exp, 1, today) }
    val monthly = remember(exp) { Fin.buckets(exp, 2, today) }
    val catRows = dash.cats.filter { it.spent > 0 }
    val chartTitle = listOf("Daily spending this month", "Last 8 weeks", "Last 12 months", "Spending by category this month")[chart]

    LazyColumn(Modifier.fillMaxSize().background(Bg), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Grocery budget", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("${today.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${today.year}", fontSize = 12.sp, color = Color.Gray)
                }
                if (jobs > 0) Text("⟳ $jobs to sync", fontSize = 11.sp, color = Orange)
            }
        }
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Ring(if (dash.budget > 0) (dash.pct / 100).toFloat() else 0f, col, 112.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (dash.budget > 0) "${dash.pct.toInt()}%" else "--", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = col)
                            Text("used", fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(Fin.LABELS[dash.level], color = col, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(rm(dash.spent), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text(if (dash.budget > 0) "of ${rm(dash.budget)}" else "no budget set", fontSize = 12.sp, color = Color.Gray)
                        if (dash.budget <= 0) TextButton({ vm.screen = Screen.Settings }, contentPadding = PaddingValues(0.dp)) { Text("Set budget") }
                    }
                }
                if (dash.budget > 0) Text(dash.message, fontSize = 12.sp)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniStat("Remaining", rm(dash.remaining), Modifier.weight(1f), if (dash.remaining < 0) LevelColors[4] else Brand)
                    MiniStat("Daily average", rm(dash.dailyAvg), Modifier.weight(1f))
                    MiniStat("Safe per day", rm(dash.recDaily), Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniStat("Projected", rm(dash.projected), Modifier.weight(1f), if (dash.budget > 0 && dash.projected > dash.budget) LevelColors[3] else Brand)
                    MiniStat("Trips", "${dash.trips}", Modifier.weight(1f))
                    MiniStat(if (dash.weeklyBudget > 0) "Week / ${rm(dash.weeklyBudget)}" else "This week", rm(dash.weekSpent), Modifier.weight(1f),
                        if (dash.weeklyBudget > 0 && dash.weekSpent > dash.weeklyBudget) LevelColors[4] else Brand)
                }
                if (dash.savings > 0) Text("Savings target ${rm(dash.savings)} - spend at most ${rm(dash.budget - dash.savings)} to reach it.", fontSize = 11.sp, color = Color.Gray)
            }
        }
        item {
            Panel {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Days", "Weeks", "Months", "Categories").forEachIndexed { i, n -> FilterChip(chart == i, { chart = i }, { Text(n, fontSize = 12.sp) }) }
                }
                Text(chartTitle, fontSize = 12.sp, color = Color.Gray)
                when (chart) {
                    0 -> BarChart(dailyLabels, daily, Modifier.fillMaxWidth().height(140.dp))
                    1 -> BarChart(weekly.first, weekly.second, Modifier.fillMaxWidth().height(140.dp))
                    2 -> BarChart(monthly.first, monthly.second, Modifier.fillMaxWidth().height(140.dp))
                    else -> if (catRows.isEmpty()) Text("No spending recorded yet.", fontSize = 12.sp) else
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Donut(catRows.map { hex(it.color) to it.spent }, 120.dp) { Text(rm(dash.spent), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                catRows.take(6).forEach { Legend(hex(it.color), it.name, rm(it.spent)) }
                            }
                        }
                }
            }
        }
        item {
            val rows = dash.cats.filter { it.spent > 0 || it.budget > 0 }
            Panel {
                Text("Categories", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (rows.isEmpty()) Text("Nothing yet. Scan a receipt to get started.", fontSize = 12.sp)
                (if (allCats) rows else rows.take(5)).forEach { c ->
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(c.name, fontSize = 12.sp); Text(if (c.budget > 0) "${rm(c.spent)} / ${rm(c.budget)}" else rm(c.spent), fontSize = 12.sp)
                        }
                        Bar(if (c.budget > 0) (c.spent / c.budget).toFloat() else if (dash.spent > 0) (c.spent / dash.spent).toFloat() else 0f,
                            if (c.budget > 0 && c.spent > c.budget) LevelColors[4] else hex(c.color), Modifier.height(5.dp))
                    }
                }
                if (rows.size > 5) TextButton({ allCats = !allCats }, contentPadding = PaddingValues(0.dp)) { Text(if (allCats) "Show less" else "Show all (${rows.size})") }
            }
        }
        item {
            Panel {
                Text("Insights", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                dash.insights.take(3).forEach { Text("• $it", fontSize = 12.sp) }
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}
