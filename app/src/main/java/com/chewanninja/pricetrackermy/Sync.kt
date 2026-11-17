package com.chewanninja.pricetrackermy

import android.content.Context
import android.util.Base64
import androidx.work.*
import com.chewanninja.pricetrackermy.data.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.TimeUnit

object Api {
    fun post(url: String, body: JSONObject): JSONObject {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.connectTimeout = 20000; c.readTimeout = 90000; c.doOutput = true
        c.setRequestProperty("Content-Type", "text/plain;charset=utf-8")
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        return read(c)
    }

    fun ping(url: String, token: String): JSONObject {
        val sep = if (url.contains("?")) "&" else "?"
        val c = URL(url + sep + "token=" + URLEncoder.encode(token, "UTF-8")).openConnection() as HttpURLConnection
        c.connectTimeout = 20000; c.readTimeout = 30000
        return read(c)
    }

    private fun read(c: HttpURLConnection): JSONObject {
        val code = c.responseCode
        val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
        if (code !in 200..299) throw IOException("HTTP $code")
        return try { JSONObject(txt) } catch (e: Exception) { throw IOException("Unexpected response - deploy the Apps Script web app with access 'Anyone'") }
    }
}

private fun obj(vararg p: Pair<String, Any?>) = JSONObject().apply { p.forEach { put(it.first, it.second) } }
private fun iso(ms: Long) = Instant.ofEpochMilli(ms).toString()
private fun dstr(d: Long) = LocalDate.ofEpochDay(d).toString()
private fun upsert(sheet: String, key: String, row: JSONObject) = obj("op" to "upsert", "sheet" to sheet, "key" to key, "row" to row)
private fun replaceWhere(sheet: String, col: String, value: String, rows: JSONArray) = obj("op" to "replaceWhere", "sheet" to sheet, "col" to col, "value" to value, "rows" to rows)
private fun deleteWhere(sheet: String, col: String, value: String) = obj("op" to "deleteWhere", "sheet" to sheet, "col" to col, "value" to value)

class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    companion object {
        private const val MAX_ATTEMPTS = 8
        private val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun kick(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(net)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
            WorkManager.getInstance(ctx).enqueueUniqueWork("sync", ExistingWorkPolicy.APPEND_OR_REPLACE, req)
        }

        fun schedulePeriodic(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS).setConstraints(net).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("sync-periodic", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }

    override suspend fun doWork(): Result {
        val dao = (applicationContext as PriceApp).db.dao()
        val url = dao.setting("backend_url")?.trim().orEmpty()
        val token = dao.setting("backend_token")?.trim().orEmpty()
        if (url.isEmpty()) return Result.success() // jobs stay queued until a backend is configured
        var retry = false
        for (job in dao.pendingJobs()) {
            try {
                val ok = process(dao, url, token, job)
                if (ok) dao.deleteJob(job.jobId) else throw IllegalStateException("Server rejected the request")
            } catch (e: Exception) {
                val n = job.attempts + 1
                dao.updateJob(job.copy(attempts = n, lastError = (e.message ?: e.javaClass.simpleName).take(200)))
                if (e is IOException) { retry = true; break }
                if (n < MAX_ATTEMPTS) retry = true
            }
        }
        return if (retry) Result.retry() else Result.success()
    }

    private fun batch(url: String, token: String, ops: List<JSONObject>): Boolean {
        val r = Api.post(url, obj("token" to token, "action" to "batch", "ops" to JSONArray(ops)))
        if (!r.optBoolean("ok")) throw IllegalStateException(r.optString("error", "server error"))
        return true
    }

    private suspend fun process(dao: AppDao, url: String, token: String, job: SyncJobEntity): Boolean {
        val id = job.refId
        when (job.type) {
            "RECEIPT" -> {
                var r = dao.receipt(id) ?: return true
                if (r.deleted) return true
                if (r.driveUrl.isEmpty() && r.imagePath.isNotEmpty() && File(r.imagePath).exists()) {
                    val b64 = Base64.encodeToString(File(r.imagePath).readBytes(), Base64.NO_WRAP)
                    val up = Api.post(url, obj("token" to token, "action" to "uploadImage", "receiptId" to id,
                        "fileName" to "receipt_${dstr(r.date)}_$id.jpg", "mime" to "image/jpeg", "data" to b64))
                    if (!up.optBoolean("ok")) throw IllegalStateException(up.optString("error", "upload failed"))
                    r = r.copy(driveUrl = up.optString("url")); dao.putReceipt(r)
                }
                val items = dao.itemsOf(id)
                val ex = dao.expense("EXP-$id")
                val itemRows = JSONArray(); val priceRows = JSONArray()
                items.forEach { i ->
                    itemRows.put(obj("item_id" to i.itemId, "receipt_id" to id, "product_name" to i.name, "product_key" to i.productKey,
                        "quantity" to i.qty, "unit" to i.unit, "unit_price" to i.unitPrice, "discount" to i.discount,
                        "line_total" to i.lineTotal, "category_id" to i.categoryId))
                    priceRows.put(obj("price_id" to i.itemId, "receipt_id" to id, "product_key" to i.productKey, "product_name" to i.name,
                        "store_name" to r.storeName, "purchase_date" to dstr(r.date),
                        "unit_price" to (if (i.unitPrice > 0) i.unitPrice else if (i.qty > 0) i.lineTotal / i.qty else i.lineTotal)))
                }
                val ops = mutableListOf(
                    upsert("Receipts", "receipt_id", obj("receipt_id" to id, "store_name" to r.storeName, "receipt_number" to r.receiptNo,
                        "purchase_date" to dstr(r.date), "subtotal" to r.subtotal, "tax" to r.tax, "discount" to r.discount,
                        "rounding" to r.rounding, "total_paid" to r.totalPaid, "payment_method" to r.paymentMethod, "notes" to r.notes,
                        "image_drive_url" to r.driveUrl, "created_at" to iso(r.createdAt), "updated_at" to iso(r.updatedAt))),
                    replaceWhere("Receipt_Items", "receipt_id", id, itemRows),
                    replaceWhere("Price_History", "receipt_id", id, priceRows)
                )
                if (ex != null && !ex.deleted) ops.add(expenseOp(ex))
                return batch(url, token, ops)
            }
            "RECEIPT_DEL" -> return batch(url, token, listOf(
                deleteWhere("Receipts", "receipt_id", id), deleteWhere("Receipt_Items", "receipt_id", id),
                deleteWhere("Price_History", "receipt_id", id), deleteWhere("Expenses", "receipt_id", id)))
            "EXPENSE" -> { val e = dao.expense(id) ?: return true; if (e.deleted) return true; return batch(url, token, listOf(expenseOp(e))) }
            "EXPENSE_DEL" -> return batch(url, token, listOf(deleteWhere("Expenses", "expense_id", id)))
            "BUDGET" -> { val b = dao.budget(id) ?: return true
                return batch(url, token, listOf(upsert("Budgets", "budget_id", obj("budget_id" to b.budgetId, "period_type" to b.periodType,
                    "period_start" to dstr(b.periodStart), "period_end" to dstr(b.periodEnd), "category_id" to b.categoryId,
                    "budget_amount" to b.amount, "created_at" to iso(b.createdAt), "updated_at" to iso(b.updatedAt))))) }
            "CATEGORY" -> { val c = dao.categoriesOnce().firstOrNull { it.categoryId == id } ?: return true
                return batch(url, token, listOf(upsert("Expense_Categories", "category_id", obj("category_id" to c.categoryId,
                    "category_name" to c.name, "monthly_budget" to c.monthlyBudget, "color" to c.color, "is_active" to c.isActive)))) }
            "SHOP_LIST" -> { val l = dao.list(id) ?: return true
                val rows = JSONArray()
                dao.shopItemsOf(id).forEach { i -> rows.put(obj("item_id" to i.itemId, "list_id" to id, "product_name" to i.productName,
                    "quantity" to i.quantity, "estimated_price" to i.estimatedPrice, "actual_price" to i.actualPrice,
                    "is_purchased" to i.isPurchased, "linked_receipt_id" to i.linkedReceiptId)) }
                return batch(url, token, listOf(upsert("Shopping_Lists", "list_id", obj("list_id" to id, "list_name" to l.listName,
                    "created_at" to iso(l.createdAt), "status" to l.status)), replaceWhere("Shopping_List_Items", "list_id", id, rows))) }
            "SHOP_LIST_DEL" -> return batch(url, token, listOf(deleteWhere("Shopping_Lists", "list_id", id), deleteWhere("Shopping_List_Items", "list_id", id)))
            "SETTING" -> { val v = dao.setting(id) ?: return true
                return batch(url, token, listOf(upsert("App_Settings", "setting_name", obj("setting_name" to id, "setting_value" to v)))) }
        }
        return true
    }

    private fun expenseOp(e: ExpenseEntity) = upsert("Expenses", "expense_id", obj("expense_id" to e.expenseId,
        "transaction_date" to dstr(e.date), "store_name" to e.storeName, "receipt_id" to e.receiptId, "expense_type" to e.type,
        "category_id" to e.categoryId, "amount" to e.amount, "payment_method" to e.paymentMethod, "notes" to e.notes,
        "created_at" to iso(e.createdAt)))
}
