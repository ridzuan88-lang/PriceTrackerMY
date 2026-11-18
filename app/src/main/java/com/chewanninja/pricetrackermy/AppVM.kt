package com.chewanninja.pricetrackermy

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chewanninja.pricetrackermy.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

enum class Screen(val label: String) { Dashboard("Dashboard"), Scan("Scan"), Expenses("Expenses"), Prices("Prices"), Shopping("Lists"), Reports("Reports"), Settings("Settings") }

class AppVM(private val app: Application) : AndroidViewModel(app) {
    private val a = app as PriceApp
    val repo = a.repo
    val dao = a.db.dao()
    private fun <T> Flow<T>.st(init: T) = stateIn(viewModelScope, SharingStarted.Eagerly, init)

    val expenses = dao.expenses().st(emptyList())
    val receipts = dao.receipts().st(emptyList())
    val items = dao.allItems().st(emptyList())
    val budgets = dao.budgets().st(emptyList())
    val categories = dao.categories().st(emptyList())
    val settings = dao.settings().map { l -> l.associate { it.name to it.value } }.st(emptyMap())
    val lists = dao.lists().st(emptyList())
    val shopItems = dao.shopItems().st(emptyList())
    val pendingJobs = dao.jobCount().st(0)
    val lastError = dao.lastError().st(null)

    var screen by mutableStateOf(Screen.Dashboard)
    var draft by mutableStateOf<Draft?>(null)
    var busy by mutableStateOf("")
    var message by mutableStateOf<String?>(null)
    var openList by mutableStateOf<String?>(null)

    init { viewModelScope.launch(Dispatchers.IO) { repo.seed() } }

    // ---------- in-app updates (GitHub Releases) ----------
    var update by mutableStateOf<Updater.Info?>(null)
    var updateProgress by mutableStateOf<Float?>(null)
    private var lastCheck = 0L
    private var dismissedCode = 0L
    val versionText: String get() = Updater.current(app).let { "${it.second} (build ${it.first})" }

    fun checkUpdate(manual: Boolean = false) {
        if (!manual && System.currentTimeMillis() - lastCheck < 30 * 60 * 1000L) return
        lastCheck = System.currentTimeMillis()
        viewModelScope.launch {
            try {
                val latest = withContext(Dispatchers.IO) { Updater.fetchLatest() }
                val cur = Updater.current(app).first
                if (latest != null && latest.code > cur) {
                    if (manual || latest.code != dismissedCode) update = latest
                } else if (manual) {
                    message = if (latest == null) "No published update found yet." else "You are on the latest version ($versionText)."
                }
            } catch (e: Exception) { if (manual) message = "Could not check for updates: ${e.message}" }
        }
    }

    fun dismissUpdate() { update?.let { dismissedCode = it.code }; update = null }

    fun installUpdate() {
        val info = update ?: return
        if (!Updater.canInstall(app)) {
            Updater.openInstallPermission(app)
            message = "Allow 'Install unknown apps' for PriceTracker MY, then come back and tap Update now again."
            return
        }
        viewModelScope.launch {
            updateProgress = 0f
            try {
                val f = withContext(Dispatchers.IO) { Updater.download(app, info) { p -> updateProgress = p } }
                update = null
                Updater.install(app, f)
            } catch (e: Exception) { message = "Update failed: ${e.message}" } finally { updateProgress = null }
        }
    }

    // ---------- scanning ----------
    fun processUri(uri: Uri, enhanced: Boolean) {
        viewModelScope.launch {
            busy = "Loading image…"
            try {
                val tmp = withContext(Dispatchers.IO) {
                    File(app.cacheDir, "in_${System.currentTimeMillis()}.jpg").also { f ->
                        app.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                    }
                }
                runPipeline(tmp, enhanced)
            } catch (e: Exception) { message = "Could not read the image: ${e.message}" } finally { busy = "" }
        }
    }

    fun processFile(f: File, enhanced: Boolean) {
        viewModelScope.launch {
            try { runPipeline(f, enhanced) } catch (e: Exception) { message = "Scan failed: ${e.message}" } finally { busy = "" }
        }
    }

    private suspend fun runPipeline(src: File, enhanced: Boolean) {
        busy = "Cropping & enhancing…"
        val dir = File(app.filesDir, "receipts").apply { mkdirs() }
        val out = File(dir, "${UUID.randomUUID()}.jpg")
        val bmp = withContext(Dispatchers.Default) {
            ImageProcessor.load(src.path)?.let { if (enhanced) it else ImageProcessor.autoCropEnhance(it) }
        }
        if (bmp == null) { message = "Could not open that image."; return }
        withContext(Dispatchers.IO) { ImageProcessor.save(bmp, out) }
        busy = "Reading text on this device…"
        val lines = ReceiptOcr.read(bmp)
        if (lines.isEmpty()) { out.delete(); message = "No text was found. Retake the photo with the whole receipt in view and good light."; return }
        val learned = settings.value
        draft = withContext(Dispatchers.Default) { ReceiptParser.parse(lines, out.path, LocalDate.now()) { k -> learned[k] } }
        busy = ""
    }

    fun updateDraft(f: Draft.() -> Draft) { draft = draft?.f() }
    fun updateItem(id: String, f: DItem.() -> DItem) { draft = draft?.let { d -> d.copy(items = d.items.map { if (it.id == id) it.f() else it }) } }
    fun addItem() { draft = draft?.let { it.copy(items = it.items + DItem(uncertain = false)) } }
    fun removeItem(id: String) { draft = draft?.let { d -> d.copy(items = d.items.filter { it.id != id }) } }

    fun confirm() {
        val d = draft ?: return
        viewModelScope.launch {
            busy = "Saving…"
            try {
                when (val r = repo.confirmReceipt(d)) {
                    SaveResult.Ok -> { draft = null; screen = Screen.Dashboard
                        message = "Saved. Expense of ${rm(d.totalPaid)} recorded; it will sync to Google Sheets and Drive when online." }
                    is SaveResult.Duplicate -> message = "This receipt was already saved (${r.existing.storeName}, ${day(r.existing.date)}, ${rm(r.existing.totalPaid)}). Duplicate not added."
                    is SaveResult.Invalid -> message = r.msg
                }
            } catch (e: Exception) { message = "Could not save: ${e.message}" } finally { busy = "" }
        }
    }

    fun editReceipt(id: String) {
        viewModelScope.launch {
            val r = dao.receipt(id) ?: return@launch
            val its = dao.itemsOf(id)
            draft = Draft(receiptId = r.receiptId, isEdit = true, store = r.storeName, date = day(r.date), receiptNo = r.receiptNo,
                tax = r.tax, discount = r.discount, rounding = r.rounding, totalPaid = r.totalPaid, paymentMethod = r.paymentMethod,
                notes = r.notes, imagePath = r.imagePath, createdAt = r.createdAt, driveUrl = r.driveUrl,
                items = its.map { DItem(it.itemId, it.name, it.qty, it.unit, it.unitPrice, it.discount, it.lineTotal, it.categoryId) })
        }
    }

    fun deleteReceipt(id: String) { viewModelScope.launch { repo.deleteReceipt(id) } }
    fun saveManual(e: ExpenseEntity) { viewModelScope.launch { repo.saveManual(e) } }
    fun deleteExpense(id: String) { viewModelScope.launch { repo.deleteExpense(id) } }

    // ---------- budgets / settings / categories ----------
    fun setMonthlyBudget(amount: Double, month: YearMonth, onlyThisMonth: Boolean) = viewModelScope.launch {
        val s = month.atDay(1); val e = month.atEndOfMonth()
        repo.saveBudget(if (onlyThisMonth) "MONTH" else "MONTH_DEFAULT", s, e, "", amount)
    }
    fun setWeeklyBudget(amount: Double) = viewModelScope.launch {
        val w = LocalDate.now().with(java.time.DayOfWeek.MONDAY)
        repo.saveBudget("WEEK_DEFAULT", w, w.plusDays(6), "", amount)
    }
    fun setCategoryBudget(c: CategoryEntity, amount: Double) = viewModelScope.launch {
        repo.saveBudget("MONTH_DEFAULT", LocalDate.now().withDayOfMonth(1), LocalDate.now().withDayOfMonth(1).plusMonths(1).minusDays(1), c.categoryId, amount)
    }
    fun saveCategory(c: CategoryEntity) { viewModelScope.launch { repo.saveCategory(c) } }
    fun setSetting(name: String, v: String, sync: Boolean = false) { viewModelScope.launch { repo.setSetting(name, v, sync) } }

    // ---------- shopping ----------
    fun addList(name: String) { viewModelScope.launch { repo.saveList(ShoppingListEntity(UUID.randomUUID().toString(), name, System.currentTimeMillis(), "OPEN")) } }
    fun deleteList(id: String) { viewModelScope.launch { repo.deleteList(id) } }
    fun saveShopItem(i: ShoppingItemEntity) { viewModelScope.launch { repo.saveShopItem(i) } }
    fun deleteShopItem(i: ShoppingItemEntity) { viewModelScope.launch { repo.deleteShopItem(i) } }
    fun linkReceipt(listId: String, receiptId: String) {
        viewModelScope.launch { val n = repo.linkReceipt(listId, receiptId); message = "$n list item(s) matched and marked as purchased." }
    }

    // ---------- sync ----------
    fun syncNow() { SyncWorker.kick(app); message = "Sync started. Pending items upload when a connection is available." }
    fun testConnection() {
        viewModelScope.launch {
            busy = "Testing connection…"
            try {
                val url = settings.value["backend_url"].orEmpty(); val tok = settings.value["backend_token"].orEmpty()
                val r = withContext(Dispatchers.IO) { Api.ping(url, tok) }
                message = if (r.optBoolean("ok")) "Connected to your Google Sheets backend." else "Server replied: ${r.optString("error")}"
            } catch (e: Exception) { message = "Connection failed: ${e.message}" } finally { busy = "" }
        }
    }
}
