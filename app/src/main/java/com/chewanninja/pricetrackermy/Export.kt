package com.chewanninja.pricetrackermy

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File

object Export {
    fun lines(r: Report, names: Map<String, String>): List<String> {
        val l = mutableListOf<String>()
        l.add("PriceTracker MY - Monthly Financial Report ${r.month}")
        l.add("")
        l.add("Monthly budget: ${rm(r.budget)}"); l.add("Total expenses: ${rm(r.spent)}"); l.add("Remaining balance: ${rm(r.remaining)}")
        l.add("Shopping trips: ${r.trips}"); l.add("Average shopping trip: ${rm(r.avgTrip)}")
        l.add("Previous month total: ${rm(r.prevSpent)} (${if (r.spent - r.prevSpent >= 0) "+" else "-"}${rm(kotlin.math.abs(r.spent - r.prevSpent))})")
        l.add(""); l.add("CATEGORY BREAKDOWN"); r.cats.forEach { l.add("  ${it.first}: ${rm(it.second)}") }
        l.add(""); l.add("TOP 10 MOST EXPENSIVE PRODUCTS"); r.top.forEachIndexed { i, t -> l.add("  ${i + 1}. ${t.first} (${t.second}) ${rm(t.third)}") }
        l.add(""); l.add("BIGGEST PRICE INCREASES"); if (r.inc.isEmpty()) l.add("  none") else r.inc.forEach { l.add("  ${it.name}: ${rm(it.old)} -> ${rm(it.new)} (+${Math.round(it.pct)}%)") }
        l.add(""); l.add("BIGGEST PRICE DECREASES"); if (r.dec.isEmpty()) l.add("  none") else r.dec.forEach { l.add("  ${it.name}: ${rm(it.old)} -> ${rm(it.new)} (${Math.round(it.pct)}%)") }
        l.add(""); l.add("TRANSACTIONS"); r.rows.forEach { l.add("  ${day(it.date)}  ${it.storeName.ifBlank { "Manual" }}  ${it.paymentMethod}  ${rm(it.amount)}") }
        return l
    }

    private fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""

    fun csv(ctx: Context, r: Report, names: Map<String, String>): File {
        val sb = StringBuilder()
        sb.appendLine("Section,Name,Value")
        sb.appendLine("Summary,Month,${r.month}"); sb.appendLine("Summary,Budget,${fmt2(r.budget)}"); sb.appendLine("Summary,Total expenses,${fmt2(r.spent)}")
        sb.appendLine("Summary,Remaining,${fmt2(r.remaining)}"); sb.appendLine("Summary,Trips,${r.trips}"); sb.appendLine("Summary,Average trip,${fmt2(r.avgTrip)}")
        sb.appendLine("Summary,Previous month,${fmt2(r.prevSpent)}")
        r.cats.forEach { sb.appendLine("Category,${q(it.first)},${fmt2(it.second)}") }
        r.top.forEach { sb.appendLine("Top product,${q(it.first + " (" + it.second + ")")},${fmt2(it.third)}") }
        r.inc.forEach { sb.appendLine("Price increase,${q(it.name)},${fmt2(it.old)} -> ${fmt2(it.new)}") }
        r.dec.forEach { sb.appendLine("Price decrease,${q(it.name)},${fmt2(it.old)} -> ${fmt2(it.new)}") }
        sb.appendLine(); sb.appendLine("Date,Store,Payment,Amount,Type,Category,Notes")
        r.rows.forEach { sb.appendLine("${day(it.date)},${q(it.storeName)},${q(it.paymentMethod)},${fmt2(it.amount)},${it.type},${q(names[it.categoryId] ?: it.categoryId)},${q(it.notes)}") }
        return File(File(ctx.cacheDir, "exports").apply { mkdirs() }, "report_${r.month}.csv").apply { writeText(sb.toString()) }
    }

    fun pdf(ctx: Context, r: Report, names: Map<String, String>): File {
        val doc = PdfDocument(); val paint = Paint().apply { textSize = 11f }
        val bold = Paint().apply { textSize = 14f; typeface = Typeface.DEFAULT_BOLD }
        var page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create()); var y = 50f; var n = 1
        for ((i, line) in lines(r, names).withIndex()) {
            if (y > 800f) { doc.finishPage(page); n++; page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, n).create()); y = 50f }
            page.canvas.drawText(line.take(95), 40f, y, if (i == 0) bold else paint); y += 16f
        }
        doc.finishPage(page)
        val f = File(File(ctx.cacheDir, "exports").apply { mkdirs() }, "report_${r.month}.pdf")
        f.outputStream().use { doc.writeTo(it) }; doc.close()
        return f
    }

    fun share(ctx: Context, f: File, mime: String) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = mime; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Share report"))
    }
}
