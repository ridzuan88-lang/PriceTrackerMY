package com.chewanninja.pricetrackermy

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable fun PricesScreen(vm: AppVM) {
    val receipts by vm.receipts.collectAsState(); val items by vm.items.collectAsState()
    var q by remember { mutableStateOf("") }
    var sel by remember { mutableStateOf<String?>(null) }
    val recs = remember(receipts, items) { Fin.priceRecs(receipts, items) }
    val groups = remember(recs) { recs.groupBy { it.key }.values.sortedByDescending { it.last().date } }
    val shown = groups.filter { q.isBlank() || it.first().name.contains(q, true) }
    if (sel != null) {
        val l = groups.firstOrNull { it.first().key == sel }
        BackHandlerLite { sel = null }
        LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item { TextButton({ sel = null }) { Text("< Back") } }
            if (l != null) {
                item { Text(l.first().name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Lowest ${rm(l.minOf { it.price })} · Highest ${rm(l.maxOf { it.price })} · ${l.size} purchase(s)") }
                item { LineChart(l.map { it.price }) }
                items(l.reversed().size) { i -> val r = l.reversed()[i]
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("${day(r.date)} · ${r.store}"); Text(rm(r.price)) } }
            }
        }
    } else LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Field("Search product", q, { q = it }) }
        if (shown.isEmpty()) item { Text("No price history yet. Prices are recorded from every confirmed receipt.") }
        items(shown.size) { i ->
            val l = shown[i]; val last = l.last(); val prev = l.getOrNull(l.size - 2)
            val pct = if (prev != null && prev.price > 0) (last.price - prev.price) / prev.price * 100 else null
            Card(Modifier.fillMaxWidth().clickable { sel = last.key }) { Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) { Text(last.name, fontWeight = FontWeight.Bold); Text("${last.store} · ${day(last.date)}") }
                Column { Text(rm(last.price), fontWeight = FontWeight.Bold)
                    if (pct != null && kotlin.math.abs(pct) >= 0.5) Text("${if (pct > 0) "▲" else "▼"} ${Math.round(kotlin.math.abs(pct))}%", color = if (pct > 0) Color(0xFFC62828) else Color(0xFF2E7D32)) }
            } }
        }
    }
}

@Composable fun BackHandlerLite(onBack: () -> Unit) = androidx.activity.compose.BackHandler(true, onBack)
