package com.chewanninja.pricetrackermy

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chewanninja.pricetrackermy.data.CategoryEntity
import java.time.YearMonth
import java.util.UUID

@Composable fun SettingsScreen(vm: AppVM) {
    val st by vm.settings.collectAsState(); val budgets by vm.budgets.collectAsState(); val cats by vm.categories.collectAsState()
    val jobs by vm.pendingJobs.collectAsState(); val err by vm.lastError.collectAsState()
    var url by remember(st["backend_url"]) { mutableStateOf(st["backend_url"] ?: "") }
    var tok by remember(st["backend_token"]) { mutableStateOf(st["backend_token"] ?: "") }
    val now = java.time.LocalDate.now()
    var mAmt by remember { mutableStateOf("") }; var month by remember { mutableStateOf(YearMonth.now().toString()) }; var only by remember { mutableStateOf(false) }
    var wAmt by remember { mutableStateOf("") }
    var sav by remember(st["savings_target"]) { mutableStateOf(st["savings_target"] ?: "") }
    var th by remember(st["alert_thresholds"]) { mutableStateOf(st["alert_thresholds"] ?: "50,75,90,100") }
    var newCat by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("App version ${vm.versionText}", Modifier.weight(1f))
                OutlinedButton({ vm.checkUpdate(true) }) { Text("Check for updates") }
            }
        }
        item { Section("Google Sheets & Drive backend") }
        item {
            Field("Apps Script web app URL", url, { url = it }); Field("Access token", tok, { tok = it }, secret = true)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button({ vm.setSetting("backend_url", url.trim()); vm.setSetting("backend_token", tok.trim()); vm.message = "Backend saved." }) { Text("Save") }
                OutlinedButton({ vm.setSetting("backend_url", url.trim()); vm.setSetting("backend_token", tok.trim()); vm.testConnection() }) { Text("Test") }
                OutlinedButton({ vm.syncNow() }) { Text("Sync now") }
            }
            Text("$jobs item(s) waiting to sync" + (err?.let { " · last error: $it" } ?: ""), color = if (jobs > 0) Orange else androidx.compose.ui.graphics.Color.Gray)
        }
        item { Section("Budgets") }
        item {
            val curM = Fin.budgetFor(budgets, "MONTH", now.withDayOfMonth(1)); val curW = Fin.budgetFor(budgets, "WEEK", now.with(java.time.DayOfWeek.MONDAY))
            Text("Current monthly budget: ${rm(curM)} · weekly: ${rm(curW)}")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Field("Monthly RM", mAmt, { mAmt = it }, Modifier.weight(1f), number = true); Field("From month", month, { month = it }, Modifier.weight(1f)) }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(only, { only = it }); Text("Only this month (special month)") }
            Button({ val a = mAmt.num(); val m = runCatching { YearMonth.parse(month) }.getOrNull()
                if (a == null || a < 0 || m == null) vm.message = "Enter a valid amount and month (yyyy-MM)." else { vm.setMonthlyBudget(a, m, only); mAmt = "" } }) { Text("Save monthly budget") }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Field("Weekly RM", wAmt, { wAmt = it }, Modifier.weight(1f), number = true)
                Button({ wAmt.num()?.let { vm.setWeeklyBudget(it); wAmt = "" } ?: run { vm.message = "Enter a valid amount." } }) { Text("Save") } }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Field("Savings target RM", sav, { sav = it }, Modifier.weight(1f), number = true)
                Button({ vm.setSetting("savings_target", fmt2(sav.num() ?: 0.0), true) }) { Text("Save") } }
        }
        item { Section("Budget alerts") }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) { Switch(st["notify"] != "0", { vm.setSetting("notify", if (it) "1" else "0", true) }); Spacer(Modifier.width(8.dp)); Text("Notify when spending nears the budget") }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Field("Thresholds % (4 ascending numbers)", th, { th = it }, Modifier.weight(1f))
                Button({ val p = th.split(",").mapNotNull { it.trim().toIntOrNull() }
                    if (p.size == 4 && p == p.sorted()) vm.setSetting("alert_thresholds", p.joinToString(","), true) else vm.message = "Use 4 ascending numbers, e.g. 50,75,90,100." }) { Text("Save") } }
        }
        item { Section("Categories & recurring allocations") }
        item { Text("Give a category a monthly target (e.g. Drinking Water, Baby essentials) and spending is tracked against it on the dashboard.") }
        items(cats.size) { i -> val c = cats[i]
            var b by remember(c.categoryId, c.monthlyBudget) { mutableStateOf(if (c.monthlyBudget > 0) fmt2(c.monthlyBudget) else "") }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Switch(c.isActive, { vm.saveCategory(c.copy(isActive = it)) })
                Text(c.name, Modifier.weight(1f))
                Field("RM/mo", b, { b = it }, Modifier.width(110.dp), number = true)
                TextButton({ vm.setCategoryBudget(c, b.num() ?: 0.0) }) { Text("Set") } } }
        item { OutlinedButton({ newCat = true }, Modifier.fillMaxWidth()) { Text("+ Add custom category / allocation") }; Spacer(Modifier.height(24.dp)) }
    }
    if (newCat) {
        var n by remember { mutableStateOf("") }; var b by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { newCat = false }, title = { Text("New category") },
            text = { Column { Field("Name", n, { n = it }); Field("Monthly target RM (optional)", b, { b = it }, number = true) } },
            confirmButton = { TextButton({
                if (n.isBlank()) vm.message = "Enter a name." else {
                    val palette = listOf("#26A69A", "#7E57C2", "#EF6C00", "#5D4037", "#00897B", "#C2185B")
                    val c = CategoryEntity("cat_" + UUID.randomUUID().toString().take(8), n.trim(), 0.0, palette[cats.size % palette.size], true)
                    vm.saveCategory(c); b.num()?.takeIf { it > 0 }?.let { vm.setCategoryBudget(c, it) }; newCat = false } }) { Text("Add") } },
            dismissButton = { TextButton({ newCat = false }) { Text("Cancel") } })
    }
}
