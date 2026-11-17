package com.chewanninja.pricetrackermy

import com.chewanninja.pricetrackermy.data.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

fun rm(v: Double): String = (if (v < -0.004) "-RM" else "RM") + String.format(Locale.US, "%,.2f", abs(v))
fun fmt2(v: Double): String = String.format(Locale.US, "%.2f", v)
fun String.num(): Double? = trim().replace(",", "").toDoubleOrNull()
fun day(l: Long): LocalDate = LocalDate.ofEpochDay(l)
val PAYMENTS = listOf("Cash", "Debit card", "Credit card", "E-wallet", "Bank transfer", "Other")

data class CatRow(val id: String, val name: String, val color: String, val spent: Double, val budget: Double, val prev: Double)
data class Dash(
    val budget: Double, val weeklyBudget: Double, val savings: Double, val spent: Double, val remaining: Double,
    val pct: Double, val dailyAvg: Double, val recDaily: Double, val projected: Double, val level: Int,
    val message: String, val weekSpent: Double, val weekPct: Double, val trips: Int,
    val cats: List<CatRow>, val insights: List<String>
)
data class PriceRec(val key: String, val name: String, val date: Long, val store: String, val price: Double, val receiptId: String)
data class PriceChange(val name: String, val old: Double, val new: Double) { val pct get() = if (old > 0) (new - old) / old * 100 else 0.0 }
data class Report(
    val month: YearMonth, val budget: Double, val spent: Double, val remaining: Double, val trips: Int, val avgTrip: Double,
    val prevSpent: Double, val cats: List<Pair<String, Double>>, val top: List<Triple<String, String, Double>>,
    val inc: List<PriceChange>, val dec: List<PriceChange>, val rows: List<ExpenseEntity>
)

object Fin {
    val LABELS = listOf("Normal", "Monitor spending", "Approaching budget", "Almost reaching limit", "Budget exceeded")

    fun thresholds(s: Map<String, String>): List<Int> {
        val t = s["alert_thresholds"]?.split(",")?.mapNotNull { it.trim().toIntOrNull() }
        return if (t != null && t.size == 4 && t == t.sorted()) t else listOf(50, 75, 90, 100)
    }

    fun level(pct: Double, th: List<Int>): Int = when {
        pct > th[3] -> 4; pct >= th[2] -> 3; pct >= th[1] -> 2; pct >= th[0] -> 1; else -> 0
    }

    /** kind = MONTH or WEEK. Exact-period override wins, otherwise the latest *_DEFAULT effective on/before the start. */
    fun budgetFor(b: List<BudgetEntity>, kind: String, start: LocalDate, cat: String = ""): Double {
        val s = start.toEpochDay()
        b.filter { it.periodType == kind && it.categoryId == cat && it.periodStart == s }.maxByOrNull { it.updatedAt }?.let { return it.amount }
        return b.filter { it.periodType == "${kind}_DEFAULT" && it.categoryId == cat && it.periodStart <= s }
            .maxWithOrNull(compareBy({ it.periodStart }, { it.updatedAt }))?.amount ?: 0.0
    }

    fun spentIn(exp: List<ExpenseEntity>, from: Long, to: Long): Double =
        exp.filter { it.date in from..to }.sumOf { it.amount }

    /** Spreads each receipt's ACTUAL amount paid across its items, so category totals add up to real spending. */
    fun catSpend(exp: List<ExpenseEntity>, items: List<ReceiptItemEntity>, from: Long, to: Long): Map<String, Double> {
        val byReceipt = items.groupBy { it.receiptId }
        val out = HashMap<String, Double>()
        for (e in exp) {
            if (e.date !in from..to) continue
            val its = if (e.receiptId.isNotEmpty()) byReceipt[e.receiptId].orEmpty() else emptyList()
            val net = its.sumOf { it.lineTotal }
            if (its.isEmpty() || net <= 0.0) out.merge(e.categoryId, e.amount, Double::plus)
            else { val f = e.amount / net; its.forEach { out.merge(it.categoryId, it.lineTotal * f, Double::plus) } }
        }
        return out
    }

    fun dash(exp: List<ExpenseEntity>, items: List<ReceiptItemEntity>, budgets: List<BudgetEntity>,
             cats: List<CategoryEntity>, st: Map<String, String>, today: LocalDate): Dash {
        val ms = today.withDayOfMonth(1); val me = ms.plusMonths(1).minusDays(1); val pms = ms.minusMonths(1)
        val budget = budgetFor(budgets, "MONTH", ms)
        val wk = today.with(DayOfWeek.MONDAY); val wb = budgetFor(budgets, "WEEK", wk)
        val savings = st["savings_target"]?.num() ?: 0.0
        val spent = spentIn(exp, ms.toEpochDay(), me.toEpochDay())
        val weekSpent = spentIn(exp, wk.toEpochDay(), wk.plusDays(6).toEpochDay())
        val elapsed = today.dayOfMonth; val left = me.dayOfMonth - today.dayOfMonth + 1
        val prevEnd = min(pms.plusDays(elapsed - 1L).toEpochDay(), ms.minusDays(1).toEpochDay())
        val prevSame = spentIn(exp, pms.toEpochDay(), prevEnd)
        val pct = if (budget > 0) spent / budget * 100 else 0.0
        val lvl = if (budget > 0) level(pct, thresholds(st)) else 0
        val remaining = budget - spent
        val dailyAvg = spent / elapsed
        val rec = if (remaining > 0) remaining / left else 0.0
        val projected = dailyAvg * me.dayOfMonth
        val cur = catSpend(exp, items, ms.toEpochDay(), me.toEpochDay())
        val prv = catSpend(exp, items, pms.toEpochDay(), prevEnd)
        val rows = cats.filter { it.isActive || (cur[it.categoryId] ?: 0.0) > 0 }.map {
            val cb = budgetFor(budgets, "MONTH", ms, it.categoryId)
            CatRow(it.categoryId, it.name, it.color, cur[it.categoryId] ?: 0.0, if (cb > 0) cb else it.monthlyBudget, prv[it.categoryId] ?: 0.0)
        }.sortedByDescending { it.spent }
        val monthExp = exp.filter { it.date in ms.toEpochDay()..me.toEpochDay() }
        val msg = when {
            budget <= 0 -> "Set a monthly grocery budget in Settings to track your spending."
            lvl == 4 -> "Budget exceeded: you have spent ${rm(spent)} of ${rm(budget)} (${pct.roundToInt()}%)."
            else -> "You have used ${pct.roundToInt()}% of your monthly grocery budget. Remaining budget: ${rm(remaining)}."
        }
        val ins = mutableListOf<String>()
        if (monthExp.isEmpty() && prevSame == 0.0) ins.add("No spending recorded yet. Scan your first receipt to see insights.")
        else {
            if (prevSame > 0) {
                val diff = spent - prevSame
                ins.add(if (diff >= 0) "You spent ${rm(diff)} more than in the same days of last month." else "You spent ${rm(-diff)} less than in the same days of last month.")
            }
            rows.firstOrNull { it.spent > 0 }?.let { ins.add("Your highest spending category this month is ${it.name}.") }
            if (monthExp.isNotEmpty()) ins.add("Your average shopping trip costs ${rm(spent / monthExp.size)}.")
            rows.filter { it.prev > 0 && it.spent > 0 && (it.spent - it.prev) / it.prev >= 0.10 }
                .sortedByDescending { (it.spent - it.prev) / it.prev }.take(2)
                .forEach { ins.add("Your spending on ${it.name} has increased by ${(((it.spent - it.prev) / it.prev) * 100).roundToInt()}% versus the same days last month.") }
            if (budget > 0 && elapsed >= 3 && projected > budget) ins.add("At your current spending rate, you may exceed your monthly budget by ${rm(projected - budget)}.")
            if (savings > 0 && budget > 0 && spent + savings > budget) ins.add("Your spending is cutting into your ${rm(savings)} savings target.")
        }
        return Dash(budget, wb, savings, spent, remaining, pct, dailyAvg, rec, projected, lvl, msg, weekSpent,
            if (wb > 0) weekSpent / wb * 100 else 0.0, monthExp.size, rows, ins)
    }

    /** mode 0 daily(14) 1 weekly(8) 2 monthly(12) 3 yearly(5): labels + totals, oldest first. */
    fun buckets(exp: List<ExpenseEntity>, mode: Int, today: LocalDate): Pair<List<String>, List<Double>> {
        val labels = mutableListOf<String>(); val vals = mutableListOf<Double>()
        when (mode) {
            0 -> for (i in 13 downTo 0) { val d = today.minusDays(i.toLong()); labels.add("${d.dayOfMonth}/${d.monthValue}"); vals.add(spentIn(exp, d.toEpochDay(), d.toEpochDay())) }
            1 -> { val w0 = today.with(DayOfWeek.MONDAY); for (i in 7 downTo 0) { val s = w0.minusWeeks(i.toLong()); labels.add("${s.dayOfMonth}/${s.monthValue}"); vals.add(spentIn(exp, s.toEpochDay(), s.plusDays(6).toEpochDay())) } }
            2 -> { val m0 = today.withDayOfMonth(1); for (i in 11 downTo 0) { val s = m0.minusMonths(i.toLong()); labels.add(s.format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH))); vals.add(spentIn(exp, s.toEpochDay(), s.plusMonths(1).minusDays(1).toEpochDay())) } }
            else -> for (i in 4 downTo 0) { val y = today.year - i; labels.add("$y"); vals.add(spentIn(exp, LocalDate.of(y, 1, 1).toEpochDay(), LocalDate.of(y, 12, 31).toEpochDay())) }
        }
        return labels to vals
    }

    fun range(mode: Int, today: LocalDate): Pair<LocalDate, LocalDate> = when (mode) {
        0 -> today to today
        1 -> today.with(DayOfWeek.MONDAY).let { it to it.plusDays(6) }
        2 -> today.withDayOfMonth(1).let { it to it.plusMonths(1).minusDays(1) }
        else -> LocalDate.of(today.year, 1, 1) to LocalDate.of(today.year, 12, 31)
    }

    fun priceRecs(receipts: List<ReceiptEntity>, items: List<ReceiptItemEntity>): List<PriceRec> {
        val rm = receipts.associateBy { it.receiptId }
        return items.mapNotNull { i ->
            val r = rm[i.receiptId] ?: return@mapNotNull null
            val p = if (i.unitPrice > 0) i.unitPrice else if (i.qty > 0) i.lineTotal / i.qty else i.lineTotal
            PriceRec(i.productKey, i.name, r.date, r.storeName, p, r.receiptId)
        }.sortedBy { it.date }
    }

    fun priceChanges(recs: List<PriceRec>, from: Long, to: Long): List<PriceChange> =
        recs.groupBy { it.key }.mapNotNull { (_, l) ->
            val upTo = l.filter { it.date <= to }
            val last = upTo.lastOrNull() ?: return@mapNotNull null
            if (last.date < from || upTo.size < 2) return@mapNotNull null
            val prev = upTo[upTo.size - 2]
            if (prev.price <= 0 || abs(prev.price - last.price) < 0.005) null else PriceChange(last.name, prev.price, last.price)
        }

    fun report(ym: YearMonth, exp: List<ExpenseEntity>, items: List<ReceiptItemEntity>, receipts: List<ReceiptEntity>,
               budgets: List<BudgetEntity>, cats: List<CategoryEntity>): Report {
        val s = ym.atDay(1); val e = ym.atEndOfMonth()
        val rows = exp.filter { it.date in s.toEpochDay()..e.toEpochDay() }.sortedBy { it.date }
        val spent = rows.sumOf { it.amount }; val budget = budgetFor(budgets, "MONTH", s)
        val names = cats.associate { it.categoryId to it.name }
        val cs = catSpend(exp, items, s.toEpochDay(), e.toEpochDay()).entries.sortedByDescending { it.value }.map { (names[it.key] ?: it.key) to it.value }
        val rmap = receipts.associateBy { it.receiptId }
        val ids = rows.map { it.receiptId }.toSet()
        val top = items.filter { it.receiptId in ids }.sortedByDescending { it.lineTotal }.distinctBy { it.productKey }.take(10)
            .map { Triple(it.name, rmap[it.receiptId]?.storeName ?: "", it.lineTotal) }
        val ch = priceChanges(priceRecs(receipts, items), s.toEpochDay(), e.toEpochDay())
        return Report(ym, budget, spent, budget - spent, rows.size, if (rows.isEmpty()) 0.0 else spent / rows.size,
            spentIn(exp, ym.minusMonths(1).atDay(1).toEpochDay(), ym.minusMonths(1).atEndOfMonth().toEpochDay()), cs, top,
            ch.filter { it.pct > 0 }.sortedByDescending { it.pct }.take(5), ch.filter { it.pct < 0 }.sortedBy { it.pct }.take(5), rows)
    }
}
