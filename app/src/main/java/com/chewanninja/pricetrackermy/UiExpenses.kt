package com.chewanninja.pricetrackermy

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.chewanninja.pricetrackermy.data.ExpenseEntity
import java.time.LocalDate
import java.util.UUID

@Composable fun ExpensesScreen(vm: AppVM) {
    var tab by remember { mutableStateOf(0) }
    var showNew by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ExpenseEntity?>(null) }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(tab == 0, { tab = 0 }, text = { Text("Analytics") })
            Tab(tab == 1, { tab = 1 }, text = { Text("History") })
        }
        Box(Modifier.weight(1f)) {
            if (tab == 0) Analytics(vm) else History(vm) { editing = it }
            FloatingActionButton({ showNew = true }, Modifier.align(Alignment.BottomEnd).padding(16.dp)) { Icon(Icons.Default.Add, "Add expense") }
        }
    }
    if (showNew || editing != null) ManualDialog(vm, editing) { showNew = false; editing = null }
}

@Composable fun Analytics(vm: AppVM) {
    val exp by vm.expenses.collectAsState(); val items by vm.items.collectAsState(); val cats by vm.categories.collectAsState()
    var mode by remember { mutableStateOf(2) }
    val today = LocalDate.now()
    val (labels, vals) = remember(exp, mode) { Fin.buckets(exp, mode, today) }
    val (from, to) = Fin.range(mode, today)
    val inRange = exp.filter { it.date in from.toEpochDay()..to.toEpochDay() }
    val cs = remember(exp, items, mode) { Fin.catSpend(exp, items, from.toEpochDay(), to.toEpochDay()) }
    val names = cats.associate { it.categoryId to it.name }
    val noun = listOf("day", "week", "month", "year")[mode]
    val cur = vals.last(); val prev = vals.getOrElse(vals.size - 2) { 0.0 }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Daily", "Weekly", "Monthly", "Yearly").forEachIndexed { i, n -> FilterChip(mode == i, { mode = i }, { Text(n) }) }
            }
        }
        item { Panel { BarChart(labels, vals, Modifier.fillMaxWidth().height(150.dp)) } }
        item {
            val diff = cur - prev
            Text("This $noun ${rm(cur)} vs previous $noun ${rm(prev)}: " + (if (prev > 0) "${if (diff >= 0) "+" else "-"}${rm(kotlin.math.abs(diff))} (${Math.round(diff / prev * 100)}%)" else "no earlier data to compare"), fontWeight = FontWeight.Bold)
        }
        item {
            if (inRange.isEmpty()) Text("No transactions in this $noun.") else {
                Text("Trips: ${inRange.size}   Average trip: ${rm(inRange.sumOf { it.amount } / inRange.size)}")
                Text("Highest: ${rm(inRange.maxOf { it.amount })}   Lowest: ${rm(inRange.minOf { it.amount })}")
            }
        }
        item {
            val sorted = cs.entries.sortedByDescending { it.value }
            val colorOf = cats.associate { it.categoryId to hex(it.color) }
            Panel {
                Text("By category (this $noun)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (sorted.isEmpty()) Text("No spending in this $noun.", fontSize = 12.sp)
                else Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Donut(sorted.map { (colorOf[it.key] ?: Color.Gray) to it.value }, 120.dp) { Text(rm(sorted.sumOf { it.value }), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        sorted.take(6).forEach { Legend(colorOf[it.key] ?: Color.Gray, names[it.key] ?: it.key, rm(it.value)) }
                    }
                }
            }
        }
        item { Section("By supermarket (this $noun)") }
        val stores = inRange.groupBy { it.storeName.ifBlank { "Unknown" } }.mapValues { e -> e.value.sumOf { it.amount } }.entries.sortedByDescending { it.value }
        items(stores.size) { i -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(stores[i].key); Text(rm(stores[i].value)) } }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable fun History(vm: AppVM, onEditManual: (ExpenseEntity) -> Unit) {
    val exp by vm.expenses.collectAsState(); val receipts by vm.receipts.collectAsState(); val items by vm.items.collectAsState(); val cats by vm.categories.collectAsState()
    var fStore by remember { mutableStateOf("") }; var fCat by remember { mutableStateOf("") }; var fPay by remember { mutableStateOf("") }; var fMonth by remember { mutableStateOf("") }
    var fFrom by remember { mutableStateOf("") }; var fTo by remember { mutableStateOf("") }; var fMin by remember { mutableStateOf("") }; var fMax by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<ExpenseEntity?>(null) }
    val rmap = receipts.associateBy { it.receiptId }
    val counts = items.groupingBy { it.receiptId }.eachCount()
    val from = runCatching { LocalDate.parse(fFrom).toEpochDay() }.getOrNull(); val to = runCatching { LocalDate.parse(fTo).toEpochDay() }.getOrNull()
    val list = exp.filter { e ->
        (fStore.isEmpty() || e.storeName == fStore) && (fPay.isEmpty() || e.paymentMethod == fPay) &&
            (fMonth.isEmpty() || day(e.date).toString().startsWith(fMonth)) && (from == null || e.date >= from) && (to == null || e.date <= to) &&
            (fMin.num()?.let { e.amount >= it } ?: true) && (fMax.num()?.let { e.amount <= it } ?: true) &&
            (fCat.isEmpty() || e.categoryId == fCat || items.any { it.receiptId == e.receiptId && it.categoryId == fCat })
    }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            OutlinedButton({ show = !show }, Modifier.fillMaxWidth()) { Text(if (show) "Hide filters" else "Filters") }
            if (show) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Pick("Store", fStore, listOf("" to "All") + exp.map { it.storeName }.filter { it.isNotBlank() }.distinct().sorted().map { it to it }, { fStore = it })
                Pick("Category", fCat, listOf("" to "All") + cats.map { it.categoryId to it.name }, { fCat = it })
                Pick("Payment", fPay, listOf("" to "All") + PAYMENTS.map { it to it }, { fPay = it })
                Pick("Month", fMonth, listOf("" to "All") + exp.map { day(it.date).toString().take(7) }.distinct().sortedDescending().map { it to it }, { fMonth = it })
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Field("From yyyy-MM-dd", fFrom, { fFrom = it }, Modifier.weight(1f)); Field("To yyyy-MM-dd", fTo, { fTo = it }, Modifier.weight(1f)) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Field("Min RM", fMin, { fMin = it }, Modifier.weight(1f), number = true); Field("Max RM", fMax, { fMax = it }, Modifier.weight(1f), number = true) }
            }
            Text("${list.size} transaction(s), total ${rm(list.sumOf { it.amount })}", fontWeight = FontWeight.Bold)
        }
        items(list.size) { i ->
            val e = list[i]; val r = rmap[e.receiptId]
            Card(Modifier.fillMaxWidth().clickable { open = e }) {
                Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (r != null && r.imagePath.isNotEmpty()) Thumb(r.imagePath)
                    Column(Modifier.weight(1f)) {
                        Text(e.storeName.ifBlank { "Manual expense" }, fontWeight = FontWeight.Bold)
                        Text("${day(e.date)} · ${e.paymentMethod} · ${if (e.type == "RECEIPT") "${counts[e.receiptId] ?: 0} items" else "manual"}")
                    }
                    Text(rm(e.amount), fontWeight = FontWeight.Bold)
                }
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
    open?.let { e ->
        val its = items.filter { it.receiptId == e.receiptId && e.receiptId.isNotEmpty() }
        val r = rmap[e.receiptId]
        AlertDialog(onDismissRequest = { open = null },
            title = { Text("${e.storeName.ifBlank { "Manual expense" }} · ${rm(e.amount)}") },
            text = { LazyColumn {
                item { Text("${day(e.date)} · ${e.paymentMethod}" + (r?.receiptNo?.takeIf { it.isNotBlank() }?.let { n -> " · #$n" } ?: "") + (if (e.notes.isNotBlank()) "\n${e.notes}" else "")) }
                if (r != null && r.imagePath.isNotEmpty()) item { Thumb(r.imagePath, 160.dp, 4) }
                items(its.size) { i -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("${its[i].name} x${its[i].qty}", Modifier.weight(1f)); Text(rm(its[i].lineTotal)) } }
                if (its.isNotEmpty()) item { Text("Discount ${rm(r?.discount ?: 0.0)} · Tax ${rm(r?.tax ?: 0.0)} · Paid ${rm(e.amount)}", fontWeight = FontWeight.Bold) }
            } },
            confirmButton = { TextButton({ open = null; if (e.type == "RECEIPT") vm.editReceipt(e.receiptId) else onEditManual(e) }) { Text("Edit") } },
            dismissButton = { Row {
                TextButton({ open = null; if (e.type == "RECEIPT") vm.deleteReceipt(e.receiptId) else vm.deleteExpense(e.expenseId) }) { Text("Delete") }
                TextButton({ open = null }) { Text("Close") }
            } })
    }
}

@Composable fun ManualDialog(vm: AppVM, e: ExpenseEntity?, onClose: () -> Unit) {
    val cats by vm.categories.collectAsState()
    var date by remember { mutableStateOf(e?.let { day(it.date).toString() } ?: LocalDate.now().toString()) }
    var store by remember { mutableStateOf(e?.storeName ?: "") }
    var amt by remember { mutableStateOf(e?.amount?.let { fmt2(it) } ?: "") }
    var cat by remember { mutableStateOf(e?.categoryId ?: "groceries") }
    var pay by remember { mutableStateOf(e?.paymentMethod ?: "Cash") }
    var notes by remember { mutableStateOf(e?.notes ?: "") }
    AlertDialog(onDismissRequest = onClose, title = { Text(if (e == null) "Add expense" else "Edit expense") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Field("Date (yyyy-MM-dd)", date, { date = it }); Field("Store", store, { store = it }); Field("Amount paid (RM)", amt, { amt = it }, number = true)
            Pick("Category", cat, cats.filter { it.isActive }.map { it.categoryId to it.name }, { cat = it })
            Pick("Payment", pay, PAYMENTS.map { it to it }, { pay = it }); Field("Notes", notes, { notes = it })
        } },
        confirmButton = { TextButton({
            val d = runCatching { LocalDate.parse(date) }.getOrNull(); val a = amt.num()
            if (d == null || a == null || a <= 0) vm.message = "Enter a valid date and an amount above zero." else {
                val now = System.currentTimeMillis()
                vm.saveManual(ExpenseEntity(e?.expenseId ?: "EXP-" + UUID.randomUUID(), "", d.toEpochDay(), store.trim(), a, pay, cat, notes, "MANUAL", e?.createdAt ?: now, now))
                onClose()
            }
        }) { Text("Save") } },
        dismissButton = { TextButton(onClose) { Text("Cancel") } })
}
