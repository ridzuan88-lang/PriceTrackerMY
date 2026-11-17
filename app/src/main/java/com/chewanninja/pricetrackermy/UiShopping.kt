package com.chewanninja.pricetrackermy

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chewanninja.pricetrackermy.data.ShoppingItemEntity
import java.time.LocalDate
import java.util.UUID

@Composable fun ShoppingScreen(vm: AppVM) {
    val lists by vm.lists.collectAsState(); val all by vm.shopItems.collectAsState()
    val exp by vm.expenses.collectAsState(); val budgets by vm.budgets.collectAsState(); val receipts by vm.receipts.collectAsState()
    var newName by remember { mutableStateOf("") }
    val id = vm.openList
    val list = lists.firstOrNull { it.listId == id }
    if (list == null) {
        LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("Shopping lists", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Field("New list name", newName, { newName = it }, Modifier.weight(1f))
                    Button({ if (newName.isNotBlank()) { vm.addList(newName.trim()); newName = "" } }) { Text("Add") } } }
            items(lists.size) { i -> val l = lists[i]; val its = all.filter { it.listId == l.listId }
                Card(Modifier.fillMaxWidth().clickable { vm.openList = l.listId }) { Column(Modifier.padding(12.dp)) {
                    Text(l.listName, fontWeight = FontWeight.Bold); Text("${its.count { it.isPurchased }}/${its.size} purchased · est. ${rm(its.sumOf { it.quantity * it.estimatedPrice })}") } } }
        }
        return
    }
    val its = all.filter { it.listId == list.listId }
    val ms = LocalDate.now().withDayOfMonth(1)
    val remaining = Fin.budgetFor(budgets, "MONTH", ms) - Fin.spentIn(exp, ms.toEpochDay(), ms.plusMonths(1).minusDays(1).toEpochDay())
    val est = its.filter { !it.isPurchased }.sumOf { it.quantity * it.estimatedPrice }
    var name by remember { mutableStateOf("") }; var qty by remember { mutableStateOf("1") }; var price by remember { mutableStateOf("") }
    var link by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) { TextButton({ vm.openList = null }) { Text("< Lists") }
                Spacer(Modifier.weight(1f)); TextButton({ vm.deleteList(list.listId); vm.openList = null }) { Text("Delete list") } }
            Text(list.listName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Estimated to buy: ${rm(est)} · Remaining budget: ${rm(remaining)}")
            Text(if (remaining <= 0) "No budget left this month." else if (est > remaining) "Over remaining budget by ${rm(est - remaining)}." else "Fits within your remaining budget (${rm(remaining - est)} spare).",
                color = if (est > remaining) LevelColors[4] else LevelColors[0], fontWeight = FontWeight.Bold)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Field("Product", name, { name = it }, Modifier.weight(2f)); Field("Qty", qty, { qty = it }, Modifier.weight(1f), number = true)
                Field("RM each", price, { price = it }, Modifier.weight(1f), number = true) }
            Button({ if (name.isNotBlank()) { vm.saveShopItem(ShoppingItemEntity(UUID.randomUUID().toString(), list.listId, name.trim(), qty.num() ?: 1.0, price.num() ?: 0.0, 0.0, false)); name = ""; price = ""; qty = "1" } },
                Modifier.fillMaxWidth()) { Text("Add product") }
        }
        items(its.size) { i -> val it = its[i]
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(it.isPurchased, { c -> vm.saveShopItem(it.copy(isPurchased = c)) })
                Column(Modifier.weight(1f)) { Text("${it.productName} x${it.quantity}"); Text(if (it.isPurchased && it.actualPrice > 0) "Paid ${rm(it.actualPrice)}" else "Est. ${rm(it.quantity * it.estimatedPrice)}", color = androidx.compose.ui.graphics.Color.Gray) }
                TextButton({ vm.deleteShopItem(it) }) { Text("Remove") } } }
        item { OutlinedButton({ link = true }, Modifier.fillMaxWidth()) { Text("Link a scanned receipt") } }
    }
    if (link) AlertDialog(onDismissRequest = { link = false }, title = { Text("Choose receipt") },
        text = { LazyColumn { items(receipts.take(15).size) { i -> val r = receipts[i]
            TextButton({ link = false; vm.linkReceipt(list.listId, r.receiptId) }) { Text("${day(r.date)} · ${r.storeName} · ${rm(r.totalPaid)}") } } } },
        confirmButton = { TextButton({ link = false }) { Text("Cancel") } })
}
