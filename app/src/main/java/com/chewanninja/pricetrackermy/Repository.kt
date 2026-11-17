package com.chewanninja.pricetrackermy

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import com.chewanninja.pricetrackermy.data.*
import java.security.MessageDigest
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

sealed class SaveResult {
    object Ok : SaveResult()
    data class Duplicate(val existing: ReceiptEntity) : SaveResult()
    data class Invalid(val msg: String) : SaveResult()
}

class Repository(private val app: Application, private val db: AppDatabase) {
    val dao = db.dao()

    suspend fun enqueue(type: String, ref: String) {
        dao.dropJobs(type, ref); dao.addJob(SyncJobEntity(type = type, refId = ref)); SyncWorker.kick(app)
    }

    suspend fun setSetting(name: String, v: String, sync: Boolean = false) {
        dao.putSetting(SettingEntity(name, v)); if (sync) enqueue("SETTING", name)
    }

    suspend fun seed() {
        if (dao.categoryCount() == 0) {
            dao.putCategories(Categories.defaults.map { CategoryEntity(it.id, it.name, 0.0, it.color, true) })
            Categories.defaults.forEach { enqueue("CATEGORY", it.id) }
        }
    }

    private fun sha1(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun fingerprint(d: Draft, date: LocalDate): String {
        val store = Categories.productKey(d.store)
        return sha1(if (d.receiptNo.isNotBlank()) "$store|${d.receiptNo.trim().uppercase()}|$date"
        else "$store|$date|${fmt2(d.totalPaid)}|${d.items.map { Categories.productKey(it.name) }.sorted().joinToString(",")}")
    }

    /** One transaction: receipt + items + exactly one expense (id EXP-<receiptId>, so edits/re-syncs never duplicate). */
    suspend fun confirmReceipt(d: Draft): SaveResult {
        if (d.items.isEmpty()) return SaveResult.Invalid("Add at least one item.")
        if (d.items.any { it.name.isBlank() }) return SaveResult.Invalid("Every item needs a name.")
        if (d.totalPaid <= 0.0) return SaveResult.Invalid("The amount paid must be more than RM0.00.")
        val date = d.date ?: LocalDate.now()
        val fp = fingerprint(d, date)
        dao.duplicate(fp, d.receiptId)?.let { return SaveResult.Duplicate(it) }
        val now = System.currentTimeMillis()
        val old = dao.receipt(d.receiptId)
        val receipt = ReceiptEntity(d.receiptId, d.store.trim(), d.receiptNo.trim(), date.toEpochDay(), d.itemsNet(), d.tax, d.discount,
            d.rounding, d.totalPaid, d.paymentMethod, d.notes, d.imagePath, old?.driveUrl ?: d.driveUrl, fp, old?.createdAt ?: now, now)
        val items = d.items.map { ReceiptItemEntity(it.id, d.receiptId, it.name.trim(), Categories.productKey(it.name), it.qty, it.unit,
            it.unitPrice, it.discount, it.lineTotal, it.categoryId) }
        val top = items.groupBy { it.categoryId }.maxByOrNull { e -> e.value.sumOf { it.lineTotal } }?.key ?: "others"
        val expense = ExpenseEntity("EXP-${d.receiptId}", d.receiptId, date.toEpochDay(), receipt.storeName, d.totalPaid,
            d.paymentMethod, top, d.notes, "RECEIPT", old?.createdAt ?: now, now)
        db.withTransaction {
            dao.putReceipt(receipt); dao.deleteItemsOf(d.receiptId); dao.putItems(items); dao.putExpense(expense)
            items.forEach { i ->
                if (i.categoryId != Categories.guess(i.name)) dao.putSetting(SettingEntity("cat:${i.productKey}", i.categoryId))
            }
        }
        enqueue("RECEIPT", d.receiptId)
        checkAlert()
        return SaveResult.Ok
    }

    suspend fun deleteReceipt(id: String) {
        val r = dao.receipt(id) ?: return
        val now = System.currentTimeMillis()
        db.withTransaction {
            dao.putReceipt(r.copy(deleted = true, updatedAt = now))
            dao.deleteItemsOf(id)
            dao.expense("EXP-$id")?.let { dao.putExpense(it.copy(deleted = true, updatedAt = now)) }
        }
        dao.dropJobs("RECEIPT", id); enqueue("RECEIPT_DEL", id)
    }

    suspend fun saveManual(e: ExpenseEntity) {
        dao.putExpense(e); enqueue("EXPENSE", e.expenseId); checkAlert()
    }

    suspend fun deleteExpense(id: String) {
        val e = dao.expense(id) ?: return
        dao.putExpense(e.copy(deleted = true, updatedAt = System.currentTimeMillis()))
        dao.dropJobs("EXPENSE", id); enqueue("EXPENSE_DEL", id)
    }

    suspend fun saveBudget(type: String, start: LocalDate, end: LocalDate, cat: String, amount: Double) {
        val now = System.currentTimeMillis()
        val id = "BUD-$type-${start.toEpochDay()}-$cat"
        val old = dao.budget(id)
        dao.putBudget(BudgetEntity(id, type, start.toEpochDay(), end.toEpochDay(), cat, amount, old?.createdAt ?: now, now))
        enqueue("BUDGET", id)
        if (cat.isNotEmpty()) dao.categoriesOnce().firstOrNull { it.categoryId == cat }?.let { saveCategory(it.copy(monthlyBudget = amount)) }
    }

    suspend fun saveCategory(c: CategoryEntity) { dao.putCategory(c); enqueue("CATEGORY", c.categoryId) }

    // shopping
    suspend fun saveList(l: ShoppingListEntity) { dao.putList(l); enqueue("SHOP_LIST", l.listId) }
    suspend fun deleteList(id: String) { dao.deleteShopItemsOf(id); dao.deleteList(id); dao.dropJobs("SHOP_LIST", id); enqueue("SHOP_LIST_DEL", id) }
    suspend fun saveShopItem(i: ShoppingItemEntity) { dao.putShopItems(listOf(i)); enqueue("SHOP_LIST", i.listId) }
    suspend fun deleteShopItem(i: ShoppingItemEntity) { dao.deleteShopItem(i.itemId); enqueue("SHOP_LIST", i.listId) }

    /** Link a scanned receipt to a list: matched items become purchased with the real price paid. */
    suspend fun linkReceipt(listId: String, receiptId: String): Int {
        val ritems = dao.itemsOf(receiptId)
        var n = 0
        val upd = dao.shopItemsOf(listId).map { si ->
            if (si.isPurchased) return@map si
            val toks = si.productName.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 3 }
            val hit = ritems.firstOrNull { ri -> val nm = ri.name.lowercase(); toks.isNotEmpty() && toks.all { nm.contains(it) } }
                ?: ritems.firstOrNull { ri -> val nm = ri.name.lowercase(); toks.any { it.length >= 4 && nm.contains(it) } }
            if (hit == null) si else { n++; si.copy(isPurchased = true, actualPrice = hit.lineTotal, linkedReceiptId = receiptId) }
        }
        dao.putShopItems(upd); enqueue("SHOP_LIST", listId)
        return n
    }

    suspend fun checkAlert() {
        try {
            val st = dao.settingsOnce().associate { it.name to it.value }
            if (st["notify"] == "0") return
            val ms = LocalDate.now().withDayOfMonth(1)
            val b = Fin.budgetFor(dao.budgetsOnce(), "MONTH", ms)
            if (b <= 0) return
            val spent = Fin.spentIn(dao.expensesOnce(), ms.toEpochDay(), ms.plusMonths(1).minusDays(1).toEpochDay())
            val pct = spent / b * 100
            val lvl = Fin.level(pct, Fin.thresholds(st))
            val key = "last_alert_$ms"
            if (lvl > 0 && lvl > (st[key]?.toIntOrNull() ?: 0)) {
                val text = if (lvl == 4) "Budget exceeded: ${rm(spent)} spent of ${rm(b)}."
                else "You have used ${pct.roundToInt()}% of your monthly grocery budget. Remaining budget: ${rm(b - spent)}."
                dao.putSetting(SettingEntity(key, lvl.toString()))
                val ok = android.os.Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                if (ok) NotificationManagerCompat.from(app).notify(1001, NotificationCompat.Builder(app, "budget")
                    .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(Fin.LABELS[lvl]).setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setAutoCancel(true).build())
            }
        } catch (_: Exception) { }
    }
}
