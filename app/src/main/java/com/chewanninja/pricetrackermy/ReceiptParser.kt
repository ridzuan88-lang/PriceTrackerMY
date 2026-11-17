package com.chewanninja.pricetrackermy

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round

/** Layout-tolerant parser for Malaysian supermarket / grocery receipts (rows of OCR text -> structured Draft). */
object ReceiptParser {
    private val MONEY = Regex("""-?\(?\d{1,3}(?:,\d{3})+\.\d{2}\)?-?|-?\(?\d+\.\d{2}\)?-?""")
    private val QTYPRICE = Regex("""(\d+(?:\.\d{1,3})?)\s*(kg|g|pcs|pc|ea|l|ml|pkt|units?)?\s*[xX@*]\s*(\d+\.\d{2})""", RegexOption.IGNORE_CASE)
    private val NOTITEM = Regex("""\b(?:cash|tunai|change|baki|tender(?:ed)?|visa|mastercard|master|debit|credit|card|tng|touch|grabpay|boost|shopeepay|duitnow|ewallet|e-wallet|points?|mata|tel|phone|fax|reg|invoice|receipt|cashier|kasyier|member|terminal|thank|terima|operator|staff|slip|qty|trans(?:action)?|counter|date|time)\b|www\.|http|@""", RegexOption.IGNORE_CASE)
    private val TAXK = Regex("""\b(?:gst|sst|tax|cukai)\b|service charge|service tax""", RegexOption.IGNORE_CASE)
    private val DISCK = Regex("""disc|diskaun|potongan|promo|voucher|coupon|rebate""", RegexOption.IGNORE_CASE)
    private val ITEMDISC = Regex("""disc|diskaun|promo|potongan|offer""", RegexOption.IGNORE_CASE)
    private val RECDISC = Regex("""voucher|coupon|member|bill|total|trans""", RegexOption.IGNORE_CASE)
    private val ROUNDK = Regex("""\brounding\b|\bround(?:ing)? adj|rnd adj|pembundaran""", RegexOption.IGNORE_CASE)
    private val SUBK = Regex("""sub[ -]?total|jumlah kecil""", RegexOption.IGNORE_CASE)
    private val TOTAL0 = Regex("""grand total|net total|nett total|amount due|total payable|amount payable|jumlah bayaran|balance due|total incl|total \(incl|rounded total|net amount|total amount""", RegexOption.IGNORE_CASE)
    private val TOTAL1 = Regex("""\btotal\b|\bjumlah\b""", RegexOption.IGNORE_CASE)
    private val TOTAL_EXCL = Regex("""qty|item|saving|disc|point|gst|sst|tax|excl|before""", RegexOption.IGNORE_CASE)
    private val RECNO = Regex("""(?:receipt|rcpt|rcp|invoice|inv|bill|trans(?:action)?|txn|doc(?:ument)?|slip|resit)\s*(?:no|num|number|#|id)?\.?\s*[:#]?\s*([A-Z0-9][A-Z0-9\-/]{3,})""", RegexOption.IGNORE_CASE)
    private val D1 = Regex("""\b(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{4}|\d{2})\b""")
    private val D2 = Regex("""\b(\d{4})[/\-.](\d{1,2})[/\-.](\d{1,2})\b""")
    private val D3 = Regex("""\b(\d{1,2})[ \-]([A-Za-z]{3,9})[ \-,]*(\d{4}|\d{2})\b""")
    private val MONTHS = mapOf("jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "mei" to 5, "jun" to 6, "jul" to 7,
        "aug" to 8, "ogo" to 8, "sep" to 9, "oct" to 10, "okt" to 10, "nov" to 11, "dec" to 12, "dis" to 12)

    private val KNOWN = listOf(
        "aeon" to "AEON", "lotus" to "Lotus's", "tesco" to "Tesco", "giant" to "Giant", "mydin" to "Mydin",
        "99 speed|99speed|speedmart" to "99 Speedmart", "kk super|kk mart|kkmart" to "KK Mart", "econsave" to "Econsave",
        "nsk" to "NSK", "the store" to "The Store", "jaya grocer" to "Jaya Grocer", "village grocer" to "Village Grocer",
        "cold storage" to "Cold Storage", "family ?mart" to "FamilyMart", "7-?eleven" to "7-Eleven", "watsons" to "Watsons",
        "guardian" to "Guardian", "mr\\.? ?diy" to "MR DIY", "sunshine" to "Sunshine", "mercato" to "Mercato",
        "hero market" to "Hero Market", "tf value" to "TF Value-Mart", "billion" to "Billion", "sam'?s groceria" to "Sam's Groceria",
        "ranch market" to "Ranch Market", "pasaraya" to "Pasaraya", "mega" to "Mega", "pacific" to "Pacific"
    ).map { Regex(it.first, RegexOption.IGNORE_CASE) to it.second }

    private fun amt(s: String): Double {
        val neg = s.contains("(") || s.startsWith("-") || s.endsWith("-")
        val v = s.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0
        return if (neg) -v else v
    }

    private fun letters(s: String) = s.count { it.isLetter() }

    private fun cleanName(s: String): String =
        s.replace(Regex("^\\s*\\d{1,3}[.)]\\s+"), "").replace(Regex("^\\d{6,}\\s+"), "")
            .replace(Regex("\\s{2,}"), " ").trim(' ', '-', ':', '*', '.')

    fun findDate(rows: List<Row>, today: LocalDate): Pair<LocalDate?, Float> {
        fun ok(y: Int, m: Int, d: Int): LocalDate? = try {
            val yy = if (y < 100) 2000 + y else y
            LocalDate.of(yy, m, d).takeIf { !it.isAfter(today.plusDays(1)) && it.isAfter(today.minusYears(5)) }
        } catch (e: Exception) { null }
        for (r in rows) {
            D2.find(r.text)?.let { m -> ok(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())?.let { return it to r.conf } }
            D1.find(r.text)?.let { m ->
                val a = m.groupValues[1].toInt(); val b = m.groupValues[2].toInt(); val y = m.groupValues[3].toInt()
                (ok(y, b, a) ?: ok(y, a, b))?.let { return it to r.conf }
            }
            D3.find(r.text)?.let { m ->
                val mo = MONTHS[m.groupValues[2].lowercase().take(3)]
                if (mo != null) ok(m.groupValues[3].toInt(), mo, m.groupValues[1].toInt())?.let { return it to r.conf }
            }
        }
        return null to 0f
    }

    fun parse(lines: List<OcrLine>, imagePath: String, today: LocalDate, learned: (String) -> String?): Draft {
        val rows = ReceiptOcr.rows(lines)
        val items = mutableListOf<DItem>()
        var pending: String? = null
        var itemsOpen = true
        var ocrSub: Double? = null
        var tax = 0.0; var rdisc = 0.0; var rounding = 0.0
        var bestPrio = 99; var total = 0.0; var totalConf = 0f; var payment = ""

        fun addItem(name: String, qty: Double, unit: String, up: Double, disc: Double, total: Double, conf: Float, forced: Boolean) {
            val key = Categories.productKey(name)
            val cat = learned("cat:$key") ?: Categories.guess(name)
            val mismatch = up > 0 && abs(qty * up - disc - total) > 0.06 + 0.01 * qty
            items.add(DItem(name = name, qty = qty, unit = unit, unitPrice = up, discount = disc, lineTotal = total,
                categoryId = cat, uncertain = conf < 0.75f || mismatch || forced))
        }

        for ((idx, row) in rows.withIndex()) {
            val t = row.text.trim(); val lo = t.lowercase()
            val ms = MONEY.findAll(t).toList()
            if (payment.isEmpty()) payment = when {
                Regex("""visa|master ?card|credit""").containsMatchIn(lo) -> "Credit card"
                Regex("""debit|mydebit""").containsMatchIn(lo) -> "Debit card"
                Regex("""tng|touch ?n ?go|grab ?pay|boost|shopee ?pay|duitnow|qr pay|e-?wallet""").containsMatchIn(lo) -> "E-wallet"
                Regex("""\bcash\b|tunai""").containsMatchIn(lo) && ms.isNotEmpty() -> "Cash"
                else -> ""
            }
            if (ms.isEmpty()) {
                pending = if (itemsOpen && idx > 1 && letters(t) >= 3 && !NOTITEM.containsMatchIn(lo) && !SUBK.containsMatchIn(lo) && t.length <= 45) cleanName(t) else null
                continue
            }
            val last = ms.last(); val v = amt(last.value)
            when {
                SUBK.containsMatchIn(lo) || (lo.contains("total") && (lo.contains("excl") || lo.contains("before"))) -> { ocrSub = abs(v); itemsOpen = false; pending = null }
                ROUNDK.containsMatchIn(lo) -> { rounding = v; pending = null }
                TAXK.containsMatchIn(lo) && !lo.contains("invoice") -> { tax = max(tax, abs(v)); itemsOpen = false; pending = null }
                DISCK.containsMatchIn(lo) -> {
                    val d = abs(v)
                    val itemDiscSum = items.sumOf { it.discount }
                    when {
                        lo.contains("total") && itemDiscSum > 0 && abs(itemDiscSum - d) < 0.02 -> {}
                        itemsOpen && items.isNotEmpty() && ITEMDISC.containsMatchIn(lo) && !RECDISC.containsMatchIn(lo) -> {
                            val li = items.last()
                            items[items.size - 1] = li.copy(discount = li.discount + d, lineTotal = max(0.0, li.lineTotal - d))
                        }
                        else -> rdisc += d
                    }
                    pending = null
                }
                TOTAL0.containsMatchIn(lo) || (TOTAL1.containsMatchIn(lo) && !TOTAL_EXCL.containsMatchIn(lo)) -> {
                    val prio = if (TOTAL0.containsMatchIn(lo)) 0 else 1
                    if (prio <= bestPrio) { bestPrio = prio; total = abs(v); totalConf = row.conf }
                    itemsOpen = false; pending = null
                }
                NOTITEM.containsMatchIn(lo) -> { pending = null }
                !itemsOpen -> {}
                else -> {
                    val tail = t.substring(last.range.last + 1).trim()
                    if (tail.length > 4) { pending = null; continue }
                    var left = t.substring(0, last.range.first).trim()
                    var lineTotal = v
                    if (lineTotal < 0) { // bare negative amount = discount on previous item
                        if (items.isNotEmpty()) { val li = items.last(); items[items.size - 1] = li.copy(discount = li.discount + abs(v), lineTotal = max(0.0, li.lineTotal - abs(v))) }
                        pending = null; continue
                    }
                    var qty = 1.0; var unit = ""; var up = 0.0
                    val qp = QTYPRICE.find(left)
                    if (qp != null) {
                        qty = qp.groupValues[1].toDouble(); unit = qp.groupValues[2].lowercase(); up = qp.groupValues[3].toDouble()
                        left = (left.removeRange(qp.range)).trim()
                    } else if (ms.size >= 2) {
                        val prev = ms[ms.size - 2]; val pu = amt(prev.value)
                        if (pu > 0) {
                            val q = lineTotal / pu
                            val before = t.substring(0, prev.range.first).trim()
                            val qm = Regex("""\s(\d{1,3})\s*$""").find(" $before")
                            if (abs(q - round(q)) < 0.01 && round(q) >= 1) {
                                qty = round(q); up = pu; left = if (qm != null) before.substring(0, max(0, before.length - qm.groupValues[1].length)).trim() else before
                            } else if (lineTotal > 0 && q in 0.001..99.0) { qty = round(q * 1000) / 1000; up = pu; left = before }
                            else left = before
                        } else left = t.substring(0, prev.range.first).trim()
                    }
                    var name = cleanName(left)
                    val p = pending
                    if (letters(name) < 3) {
                        if (p != null) name = (p + " " + name).trim() else { pending = null; continue }
                    }
                    if (up <= 0 && qty > 0) up = lineTotal / qty
                    addItem(name, qty, unit, up, 0.0, lineTotal, row.conf, false)
                    pending = null
                }
            }
        }

        // header fields
        var store = ""; var storeKnown = false
        val head = rows.take(15).joinToString("\n") { it.text }
        for ((rx, nm) in KNOWN) if (rx.containsMatchIn(head)) { store = nm; storeKnown = true; break }
        if (store.isEmpty()) {
            val r = rows.take(8).firstOrNull { letters(it.text) >= 3 && !NOTITEM.containsMatchIn(it.text) && !Regex("""tax invoice|sdn|bhd""", RegexOption.IGNORE_CASE).matches(it.text.trim()) }
            store = r?.text?.trim().orEmpty()
        }
        val (date, dconf) = findDate(rows, today)
        var recNo = ""; var recConf = 0f
        for (r in rows) { val m = RECNO.find(r.text); if (m != null && m.groupValues[1].any { it.isDigit() }) { recNo = m.groupValues[1]; recConf = r.conf; break } }
        val net = items.sumOf { it.lineTotal }
        val foundTotal = bestPrio < 99
        val paid = if (foundTotal) total else max(0.0, net - rdisc + rounding)
        val draft = Draft(
            store = store, storeUncertain = !storeKnown, date = date ?: today, dateUncertain = date == null || dconf < 0.75f,
            receiptNo = recNo, receiptNoUncertain = recNo.isEmpty() || recConf < 0.75f, ocrSubtotal = ocrSub,
            tax = tax, discount = rdisc, rounding = rounding, totalPaid = paid,
            totalUncertain = !foundTotal || totalConf < 0.75f, paymentMethod = payment.ifEmpty { "Cash" },
            items = items, imagePath = imagePath
        )
        return draft.copy(totalUncertain = draft.totalUncertain || !draft.validate().ok)
    }
}
