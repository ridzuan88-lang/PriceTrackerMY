package com.chewanninja.pricetrackermy

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.io.File
import java.time.LocalDate

@Composable fun ScanScreen(vm: AppVM) {
    val ctx = LocalContext.current
    val tmp = remember { File(ctx.cacheDir, "capture.jpg") }
    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val uri = GmsDocumentScanningResult.fromActivityResultIntent(res.data)?.pages?.firstOrNull()?.imageUri
            if (uri != null) vm.processUri(uri, true) else vm.message = "No image was returned by the scanner."
        }
    }
    val cam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) vm.processFile(tmp, false) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> if (uri != null) vm.processUri(uri, false) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Lay the receipt flat in good light with the whole receipt in view. Text is read on your phone - no internet needed for the OCR.")
        Button(onClick = {
            val opts = GmsDocumentScannerOptions.Builder().setGalleryImportAllowed(true).setPageLimit(1)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG).setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL).build()
            GmsDocumentScanning.getClient(opts).getStartScanIntent(ctx as Activity)
                .addOnSuccessListener { scanLauncher.launch(IntentSenderRequest.Builder(it).build()) }
                .addOnFailureListener { vm.message = "Auto-scanner unavailable (${it.message}). Use 'Take photo' or 'Choose from gallery' instead." }
        }, Modifier.fillMaxWidth()) { Text("Scan with auto-crop (recommended)") }
        OutlinedButton(onClick = { tmp.delete(); cam.launch(FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", tmp)) }, Modifier.fillMaxWidth()) { Text("Take photo") }
        OutlinedButton(onClick = { gallery.launch("image/*") }, Modifier.fillMaxWidth()) { Text("Choose from gallery") }
        Text("After scanning you review and correct every field before anything is saved.", color = androidx.compose.ui.graphics.Color.Gray)
    }
}

@Composable fun ReviewScreen(vm: AppVM, d: Draft) {
    val cats by vm.categories.collectAsState()
    val catOpts = cats.filter { it.isActive }.map { it.categoryId to it.name }
    val v = d.validate()
    var dt by remember(d.receiptId) { mutableStateOf(d.date?.toString() ?: "") }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Orange fields had low OCR confidence. Check them against the receipt.")
            if (d.imagePath.isNotEmpty()) Thumb(d.imagePath, 140.dp, 4)
        }
        item { Field("Store", d.store, { s -> vm.updateDraft { copy(store = s, storeUncertain = false) } }, uncertain = d.storeUncertain) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Date (yyyy-MM-dd)", dt, { s ->
                    dt = s
                    runCatching { LocalDate.parse(s) }.getOrNull()?.let { ld -> vm.updateDraft { copy(date = ld, dateUncertain = false) } }
                }, Modifier.weight(1f), uncertain = d.dateUncertain)
                Field("Receipt no.", d.receiptNo, { s -> vm.updateDraft { copy(receiptNo = s, receiptNoUncertain = false) } }, Modifier.weight(1f), uncertain = d.receiptNoUncertain)
            }
        }
        item { Pick("Payment", d.paymentMethod, PAYMENTS.map { it to it }, { p -> vm.updateDraft { copy(paymentMethod = p) } }) }
        item { Section("Items (${d.items.size})") }
        items(d.items.size) { idx ->
            val it = d.items[idx]
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Field("Product", it.name, { s -> vm.updateItem(it.id) { copy(name = s, uncertain = false) } }, uncertain = it.uncertain)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NumField("Qty", it.qty, it.id, Modifier.weight(1f)) { q -> vm.updateItem(it.id) { copy(qty = q, lineTotal = recalc(q, unitPrice, discount, lineTotal), rev = rev + 1, uncertain = false) } }
                    Field("Unit", it.unit, { s -> vm.updateItem(it.id) { copy(unit = s) } }, Modifier.weight(1f))
                    NumField("Unit price", it.unitPrice, it.id, Modifier.weight(1f)) { p -> vm.updateItem(it.id) { copy(unitPrice = p, lineTotal = recalc(qty, p, discount, lineTotal), rev = rev + 1, uncertain = false) } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NumField("Discount", it.discount, it.id, Modifier.weight(1f)) { x -> vm.updateItem(it.id) { copy(discount = x, lineTotal = recalc(qty, unitPrice, x, lineTotal), rev = rev + 1) } }
                    NumField("Line total", it.lineTotal, it.id to it.rev, Modifier.weight(1f), uncertain = it.uncertain) { x -> vm.updateItem(it.id) { copy(lineTotal = x, uncertain = false) } }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pick("Category", it.categoryId, catOpts, { c -> vm.updateItem(it.id) { copy(categoryId = c) } }, Modifier.weight(1f))
                    TextButton({ vm.removeItem(it.id) }) { Text("Remove") }
                }
            } }
        }
        item { OutlinedButton({ vm.addItem() }, Modifier.fillMaxWidth()) { Text("+ Add item") } }
        item { Section("Totals") }
        item { Text("Items total: ${rm(d.itemsNet())}", fontWeight = FontWeight.Bold) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField("Receipt discount", d.discount, d.receiptId, Modifier.weight(1f)) { x -> vm.updateDraft { copy(discount = x) } }
                NumField("Tax (GST/SST)", d.tax, d.receiptId, Modifier.weight(1f)) { x -> vm.updateDraft { copy(tax = x) } }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Rounding (+/-)", if (d.rounding == 0.0) "" else fmt2(d.rounding), { s -> vm.updateDraft { copy(rounding = s.num() ?: 0.0) } }, Modifier.weight(1f), number = true)
                NumField("Amount paid", d.totalPaid, d.receiptId, Modifier.weight(1f), uncertain = d.totalUncertain) { x -> vm.updateDraft { copy(totalPaid = x, totalUncertain = false) } }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = if (v.ok) androidx.compose.ui.graphics.Color(0xFFE8F5E9) else androidx.compose.ui.graphics.Color(0xFFFFF3E0))) {
                Text(v.message + (d.ocrSubtotal?.let { s -> if (kotlin.math.abs(s - d.itemsNet()) > 0.1) "\nPrinted subtotal ${rm(s)} differs from items total ${rm(d.itemsNet())}." else "" } ?: ""), Modifier.padding(12.dp))
            }
        }
        item { Field("Notes", d.notes, { s -> vm.updateDraft { copy(notes = s) } }, single = false) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ vm.draft = null }, Modifier.weight(1f)) { Text("Cancel") }
                Button({ vm.confirm() }, Modifier.weight(1f)) { Text(if (v.ok) "Confirm & save" else "Save anyway") }
            }
            if (!v.ok) Text("Totals do not match yet - you can still save after checking the receipt.", color = Orange)
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun recalc(q: Double, p: Double, disc: Double, current: Double): Double =
    if (p > 0 && q > 0) kotlin.math.max(0.0, Math.round((q * p - disc) * 100) / 100.0) else current
