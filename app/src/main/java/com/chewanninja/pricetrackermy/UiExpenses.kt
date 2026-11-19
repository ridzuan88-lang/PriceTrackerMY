package com.chewanninja.pricetrackermy

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chewanninja.pricetrackermy.data.CategoryEntity
import com.chewanninja.pricetrackermy.data.ExpenseEntity
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable fun ExpenseRow(e: ExpenseEntity, cats: List<CategoryEntity>, thumb: String?, subtitle: String, onClick: () -> Unit) {
    val c = cats.firstOrNull { it.categoryId == e.categoryId }
    Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        IconTile(catEmoji(e.categoryId), hex(c?.color ?: "#78909C"))
        Column(Modifier.weight(1f)) {
            Text(e.storeName.ifBlank { "Manual expense" }, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
            Text(subtitle, fontSize = 11.sp, color = Color.Gray, maxLines = 1)
        }
        if (thumb != null && thumb.isNotEmpty()) Box(Modifier.clip(RoundedCornerShape(8.dp))) { Thumb(thumb, 36.dp) }
        Text("-" + rm(e.amount), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Red)
    }
}

@Composable fun ExpensesScreen(vm: AppVM) {
    var tab by remember { mutableStateOf(0) }
    var showNew by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ExpenseEntity?>(null) }
    Column(Modifier.fillMaxSize().background(Bg)) {
        Pad { Spacer(Modifier.height(10.dp)); Segmented(listOf("Overview", "History"), tab, Modifier.fillMaxWidth(), equal = true) { tab = it }; Spacer(Modifier.height(4.dp)) }
        Box(Modifier.weight(1f)) {
            if (tab == 0) Overview(vm) else History(vm) { editing = it }
            FloatingActionButton({ showNew = true }, Modifier.align(Alignment.BottomEnd).padding(16.dp), containerColor = Brand, contentColor = Color.White) {
                Icon(Icons.Default.Add, "Add expense")
            }
        }
    }
    if (showNew || vm.addManual || editing != null) ManualDialog(vm, editing) { showNew = false; vm.addManual = false; editing = null }
}

@Composable fun Overview(vm: AppVM) {
    val exp by vm.expenses.collectAsState(); val items by vm.items.collectAsState(); val cats by vm.categories.collectAsState()
    var mode by remember { mutableStateOf(2) }
    val today = LocalDate.now()
    val (labels, vals) = remember(exp, mode) { Fin.buckets(exp, mode, today) }
    val (from, to) = Fin.range(mode, today)
    val inRange = exp.filter { it.date in from.toEpochDay()..to.toEpochDay() }
    val cs = remember(exp, items, mode) { Fin.catSpend(exp, items, from.toEpochDay(), to.toEpochDay()) }
    val noun = listOf("day", "week", "month", "year")[mode]
    val cur = vals.last(); val prev = vals.getOrElse(vals.size - 2) { 0.0 }
    val colorOf = cats.associate { it.categoryId to hex(it.color) }; val names = cats.associate { it.categoryId to it.name }
    val sorted = cs.entries.sortedByDescending { it.value }
    val total = sorted.sumOf { it.value }.coerceAtLeast(0.01)
    val counts = remember(items, inRange) {
        val ids = inRange.map { it.receiptId }.filter { it.isNotEmpty() }.toSet()
        items.filter { it.receiptId in ids }.groupingBy { it.categoryId }.eachCount()
    }
    val stores = inRange.groupBy { it.storeName.ifBlank { "Unknown" } }.mapValues { e -> e.value.sumOf { it.amount } }.entries.sortedByDescending { it.value }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp, 6.dp, 12.dp, 88.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Segmented(listOf("Day", "Week", "Month", "Year"), mode, Modifier.fillMaxWidth(), equal = true) { mode = it } }
        item {
            Panel {
                Text("Total this $noun", fontSize = 12.sp, color = Color.Gray)
                Text(rm(cur), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                val diff = cur - prev
                Text(if (prev > 0) "${if (diff >= 0) "▲" else "▼"} ${Math.abs(Math.round(diff / prev * 100))}% (${rm(abs(diff))}) vs previous $noun" else "No earlier $noun to compare",
                    fontSize = 12.sp, color = if (prev > 0 && diff >= 0) Red else Green, fontWeight = FontWeight.Medium)
                if (inRange.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniStat("Trips", "${inRange.size}", Modifier.weight(1f))
                    MiniStat("Average", rm(inRange.sumOf { it.amount } / inRange.size), Modifier.weight(1f))
                    MiniStat("Highest", rm(inRange.maxOf { it.amount }), Modifier.weight(1f))
                }
            }
        }
        item {
            Panel {
                Text("Spending trend", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (mode == 2) AreaChart(vals, labels, Modifier.fillMaxWidth().height(170.dp)) else BarChart(labels, vals, Modifier.fillMaxWidth().height(170.dp))
            }
        }
        item {
            Panel {
                Text("Category summary", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (sorted.isEmpty()) Text("No spending in this $noun.", fontSize = 12.sp, color = Color.Gray)
                else {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Donut(sorted.map { (colorOf[it.key] ?: Color.Gray) to it.value }, 150.dp) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("Amount", fontSize = 10.sp, color = Color.Gray); Text(rm(total), fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                    sorted.take(6).forEach { Legend(colorOf[it.key] ?: Color.Gray, names[it.key] ?: it.key, "${(it.value / total * 100).roundToInt()}%") }
                }
            }
        }
        item {
            Panel {
                Text("Categories", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                sorted.forEach { e ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        IconTile(catEmoji(e.key), colorOf[e.key] ?: Color.Gray)
                        Column(Modifier.weight(1f)) {
                            Text(names[e.key] ?: e.key, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("${counts[e.key] ?: 0} items", fontSize = 11.sp, color = Color.Gray)
                        }
                        Text(rm(e.value), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (sorted.isEmpty()) Text("Nothing yet.", fontSize = 12.sp, color = Color.Gray)
            }
        }
        item {
            Panel {
                Text("By supermarket", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                val mx = (stores.firstOrNull()?.value ?: 1.0).coerceAtLeast(0.01)
                stores.forEach { s ->
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(s.key, fontSize = 13.sp); Text(rm(s.value), fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                        Bar((s.value / mx).toFloat(), Brand, Modifier.height(5.dp))
                    }
                }
                if (stores.isEmpty()) Text("Nothing yet.", fontSize = 12.sp, color = Color.Gray)
            }
        }
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
    val grouped = list.groupBy { it.date }.entries.sortedByDescending { it.key }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp, 6.dp, 12.dp, 88.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${list.size} transaction(s) · ${rm(list.sumOf { it.amount })}", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    FilterChip(show, { show = !show }, { Text("Filters", fontSize = 12.sp) })
                }
                if (show) Panel {
                    Pick("Store", fStore, listOf("" to "All") + exp.map { it.storeName }.filter { it.isNotBlank() }.distinct().sorted().map { it to it }, { fStore = it })
                    Pick("Category", fCat, listOf("" to "All") + cats.map { it.categoryId to it.name }, { fCat = it })
                    Pick("Payment", fPay, listOf("" to "All") + PAYMENTS.map { it to it }, { fPay = it })
                    Pick("Month", fMonth, listOf("" to "All") + exp.map { day(it.date).toString().take(7) }.distinct().sortedDescending().map { it to it }, { fMonth = it })
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Field("From yyyy-MM-dd", fFrom, { fFrom = it }, Modifier.weight(1f)); Field("To yyyy-MM-dd", fTo, { fTo = it }, Modifier.weight(1f)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Field("Min RM", fMin, { fMin = it }, Modifier.weight(1f), number = true); Field("Max RM", fMax, { fMax = it }, Modifier.weight(1f), number = true) }
                }
            }
        }
        if (list.isEmpty()) item { Text("No transactions match.", color = Color.Gray, fontSize = 13.sp) }
        grouped.forEach { (dayL, es) ->
            item(key = "h$dayL") {
                val d = day(dayL)
                Row(Modifier.fillMaxWidth().padding(top = 6.dp).background(Color.White, RoundedCornerShape(14.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("%02d".format(d.dayOfMonth), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Brand)
                    Column(Modifier.weight(1f)) {
                        Text(d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("${d.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${d.year}", fontSize = 12.sp, color = Color.Gray)
                    }
                    Text("-" + rm(es.sumOf { it.amount }), fontWeight = FontWeight.Bold, color = Red)
                }
            }
            items(es.size, key = { es[it].expenseId }) { i ->
                val e = es[i]; val r = rmap[e.receiptId]
                ExpenseRow(e, cats, r?.imagePath, "${e.paymentMethod} · " + (if (e.type == "RECEIPT") "${counts[e.receiptId] ?: 0} items" else "manual")) { open = e }
            }
        }
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
