package com.chewanninja.pricetrackermy

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable private fun HeaderChip(label: String, value: String, modifier: Modifier) {
    Column(modifier.background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 7.dp)) {
        Text(label, fontSize = 10.sp, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
    }
}

@Composable private fun SummaryRow(sym: String, label: String, value: String, tile: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(26.dp).background(tile, CircleShape), contentAlignment = Alignment.Center) { Text(sym, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        Text(label, Modifier.weight(1f), fontSize = 13.sp)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable fun DashboardScreen(vm: AppVM) {
    val exp by vm.expenses.collectAsState(); val items by vm.items.collectAsState(); val budgets by vm.budgets.collectAsState()
    val cats by vm.categories.collectAsState(); val st by vm.settings.collectAsState(); val jobs by vm.pendingJobs.collectAsState()
    val today = LocalDate.now()
    val dash = remember(exp, items, budgets, cats, st) { Fin.dash(exp, items, budgets, cats, st, today) }
    var tab by remember { mutableStateOf(1) }
    var allCats by remember { mutableStateOf(false) }
    val hour = LocalTime.now().hour
    val greet = if (hour < 12) "Good morning" else if (hour < 18) "Good afternoon" else "Good evening"
    val ms = today.withDayOfMonth(1); val me = ms.plusMonths(1).minusDays(1); val pms = ms.minusMonths(1)
    val monthName = today.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    val prevEnd = minOf(pms.plusDays(today.dayOfMonth - 1L).toEpochDay(), ms.minusDays(1).toEpochDay())
    val prevSame = Fin.spentIn(exp, pms.toEpochDay(), prevEnd)
    val diff = dash.spent - prevSame
    val week7 = remember(exp) { (6 downTo 0).map { i -> val d = today.minusDays(i.toLong()); "${d.dayOfMonth}/${d.monthValue}" to Fin.spentIn(exp, d.toEpochDay(), d.toEpochDay()) } }
    val daily = remember(exp) { (1..today.dayOfMonth).map { d -> val dd = today.withDayOfMonth(d); Fin.spentIn(exp, dd.toEpochDay(), dd.toEpochDay()) } }
    val dailyLabels = (1..today.dayOfMonth).map { if (it == 1 || it % 5 == 0) "$it" else "" }
    val yearly = remember(exp) { Fin.buckets(exp, 2, today) }
    val catRows = dash.cats.filter { it.spent > 0 }
    val monthExp = exp.filter { it.date in ms.toEpochDay()..me.toEpochDay() }
    val perCat = remember(items, monthExp) {
        val ids = monthExp.map { it.receiptId }.filter { it.isNotEmpty() }.toSet()
        items.filter { it.receiptId in ids }.groupingBy { it.categoryId }.eachCount()
    }
    val col = LevelColors[dash.level]
    val fill = when (dash.level) { 0, 1 -> Color.White; 2 -> Color(0xFFFFC107); 3 -> Color(0xFFFF8A65); else -> Color(0xFFFF5252) }
    val frac = if (dash.budget > 0) (dash.pct / 100).toFloat().coerceIn(0f, 1f) else 0f

    LazyColumn(Modifier.fillMaxSize().background(Bg), contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.fillMaxWidth().background(HeaderBrush, RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("$greet 👋", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                        Text("Grocery budget", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("$monthName ${today.year}", fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
                        if (jobs > 0) Text("⟳ $jobs to sync", fontSize = 11.sp, color = Color(0xFFFFE082))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(18.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Spent this month", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(rm(dash.spent), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        if (dash.budget > 0) Text("  / ${rm(dash.budget)}", fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(bottom = 4.dp))
                    }
                    if (dash.budget > 0) {
                        Box(Modifier.fillMaxWidth().height(8.dp).background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(4.dp))) {
                            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(fill, RoundedCornerShape(4.dp)))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${dash.pct.toInt()}% used · ${Fin.LABELS[dash.level]}", fontSize = 12.sp, color = Color.White)
                            Text(if (dash.remaining >= 0) "${rm(dash.remaining)} left" else "${rm(-dash.remaining)} over", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    } else {
                        TextButton({ vm.screen = Screen.Settings }, contentPadding = PaddingValues(0.dp)) { Text("Set a monthly budget →", color = Color.White) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeaderChip("Daily average", rm(dash.dailyAvg), Modifier.weight(1f))
                    HeaderChip("Safe per day", rm(dash.recDaily), Modifier.weight(1f))
                    HeaderChip("Projected", rm(dash.projected), Modifier.weight(1f))
                }
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("＋ Add expense") { vm.addManual = true; vm.screen = Screen.Expenses }
                Pill("📷 Scan receipt") { vm.screen = Screen.Scan }
                Pill("📊 Reports") { vm.screen = Screen.Reports }
                Pill("🛒 Shopping list") { vm.screen = Screen.Shopping }
            }
        }
        item {
            Pad { Panel {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Spending", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Segmented(listOf("Week", "Month", "Year"), tab) { tab = it }
                }
                when (tab) {
                    0 -> BarChart(week7.map { it.first }, week7.map { it.second }, Modifier.fillMaxWidth().height(160.dp))
                    1 -> AreaChart(daily, dailyLabels, Modifier.fillMaxWidth().height(160.dp))
                    else -> BarChart(yearly.first, yearly.second, Modifier.fillMaxWidth().height(160.dp))
                }
            } }
        }
        item {
            Pad { Panel {
                Text("Spending summary", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                SummaryRow("↓", "This month", rm(dash.spent), Red)
                SummaryRow("↺", "Same days last month", rm(prevSame), Color(0xFF90A4AE))
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Difference", fontSize = 13.sp)
                    Text((if (diff > 0) "+" else if (diff < 0) "-" else "") + rm(abs(diff)), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (diff > 0) Red else Green)
                }
                Text("${dash.trips} trip(s) this month", fontSize = 11.sp, color = Color.Gray)
            } }
        }
        item {
            Pad { Panel {
                Text("Budgets", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (dash.budget > 0) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Ring(frac, col, 72.dp, 8.dp) { Text("${dash.pct.toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = col) }
                    Column {
                        Text("My budget", fontSize = 11.sp, color = Color.Gray)
                        Text("${rm(dash.spent)} from ${rm(dash.budget)}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(if (dash.remaining >= 0) "${rm(dash.remaining)} remaining" else "${rm(-dash.remaining)} over budget", fontSize = 12.sp, color = if (dash.remaining >= 0) Green else Red)
                    }
                } else Text("No monthly budget set yet.", fontSize = 12.sp, color = Color.Gray)
                if (dash.weeklyBudget > 0) Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("This week", fontSize = 12.sp); Text("${rm(dash.weekSpent)} / ${rm(dash.weeklyBudget)}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Bar((dash.weekSpent / dash.weeklyBudget).toFloat(), if (dash.weekSpent > dash.weeklyBudget) Red else Brand)
                }
                if (dash.savings > 0) Text("Savings target ${rm(dash.savings)} · spend at most ${rm(dash.budget - dash.savings)} to reach it.", fontSize = 11.sp, color = Color.Gray)
                dash.cats.filter { it.budget > 0 }.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        IconTile(catEmoji(c.id), hex(c.color), 34.dp)
                        Column(Modifier.weight(1f)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(c.name, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Text(if (c.spent <= c.budget) "${rm(c.budget - c.spent)} left" else "${rm(c.spent - c.budget)} over", fontSize = 12.sp, color = if (c.spent <= c.budget) Green else Red)
                            }
                            Bar((c.spent / c.budget).toFloat(), if (c.spent > c.budget) Red else hex(c.color))
                        }
                    }
                }
            } }
        }
        item {
            Pad { Panel {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Spending breakdown", fontWeight = FontWeight.Bold, fontSize = 14.sp); Text(monthName, fontSize = 11.sp, color = Color.Gray)
                }
                if (catRows.isEmpty()) Text("No spending recorded yet. Scan your first receipt.", fontSize = 12.sp, color = Color.Gray)
                else {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Donut(catRows.map { hex(it.color) to it.spent }, 150.dp) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("Amount", fontSize = 10.sp, color = Color.Gray); Text(rm(dash.spent), fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                    val tot = catRows.sumOf { it.spent }.coerceAtLeast(0.01)
                    catRows.take(6).forEach { Legend(hex(it.color), it.name, "${(it.spent / tot * 100).roundToInt()}%") }
                }
            } }
        }
        item {
            val rows = dash.cats.filter { it.spent > 0 }
            Pad { Panel {
                Text("Categories", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (rows.isEmpty()) Text("Nothing yet.", fontSize = 12.sp, color = Color.Gray)
                (if (allCats) rows else rows.take(5)).forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        IconTile(catEmoji(c.id), hex(c.color))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("${perCat[c.id] ?: 0} items", fontSize = 11.sp, color = Color.Gray)
                        }
                        Text(rm(c.spent), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (rows.size > 5) TextButton({ allCats = !allCats }, contentPadding = PaddingValues(0.dp)) { Text(if (allCats) "Show less" else "Show all (${rows.size})") }
            } }
        }
        item {
            Pad {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent expenses", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    TextButton({ vm.screen = Screen.Expenses }) { Text("View all") }
                }
                if (exp.isEmpty()) Text("No expenses yet.", fontSize = 12.sp, color = Color.Gray)
                exp.take(5).forEach { e ->
                    ExpenseRow(e, cats, null, "${day(e.date)} · ${e.paymentMethod}") { vm.screen = Screen.Expenses }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
        item {
            Pad { Panel {
                Text("Insights", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                dash.insights.take(3).forEach { Text("• $it", fontSize = 12.sp) }
            } }
        }
    }
}
