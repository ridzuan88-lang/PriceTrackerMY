package com.chewanninja.pricetrackermy

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.YearMonth

@Composable fun ReportsScreen(vm: AppVM) {
    val ctx = LocalContext.current
    val exp by vm.expenses.collectAsState(); val items by vm.items.collectAsState(); val receipts by vm.receipts.collectAsState()
    val budgets by vm.budgets.collectAsState(); val cats by vm.categories.collectAsState()
    var ym by remember { mutableStateOf(YearMonth.now()) }
    val names = cats.associate { it.categoryId to it.name }
    val rep = remember(exp, items, receipts, budgets, cats, ym) { Fin.report(ym, exp, items, receipts, budgets, cats) }
    val lines = remember(rep) { Export.lines(rep, names) }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton({ ym = ym.minusMonths(1) }) { Text("<") }; Text(ym.toString(), fontWeight = FontWeight.Bold); TextButton({ ym = ym.plusMonths(1) }) { Text(">") }
                Spacer(Modifier.weight(1f))
                OutlinedButton({ Export.share(ctx, Export.csv(ctx, rep, names), "text/csv") }) { Text("CSV") }
                Spacer(Modifier.width(6.dp))
                Button({ Export.share(ctx, Export.pdf(ctx, rep, names), "application/pdf") }) { Text("PDF") }
            }
            Spacer(Modifier.height(8.dp))
        }
        items(lines.size) { i -> val l = lines[i]
            Text(l, fontWeight = if (l.isNotEmpty() && l == l.uppercase() && l.any { it.isLetter() }) FontWeight.Bold else FontWeight.Normal) }
    }
}
