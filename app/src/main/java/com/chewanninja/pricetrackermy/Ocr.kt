package com.chewanninja.pricetrackermy

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class DItem(
    val id: String = UUID.randomUUID().toString(), val name: String = "", val qty: Double = 1.0, val unit: String = "",
    val unitPrice: Double = 0.0, val discount: Double = 0.0, val lineTotal: Double = 0.0,
    val categoryId: String = "others", val uncertain: Boolean = false, val rev: Int = 0
)

data class Draft(
    val receiptId: String = UUID.randomUUID().toString(), val isEdit: Boolean = false,
    val store: String = "", val storeUncertain: Boolean = false,
    val date: LocalDate? = null, val dateUncertain: Boolean = false,
    val receiptNo: String = "", val receiptNoUncertain: Boolean = false,
    val ocrSubtotal: Double? = null, val tax: Double = 0.0, val discount: Double = 0.0, val rounding: Double = 0.0,
    val totalPaid: Double = 0.0, val totalUncertain: Boolean = false,
    val paymentMethod: String = "Cash", val notes: String = "", val items: List<DItem> = emptyList(),
    val imagePath: String = "", val createdAt: Long = 0L, val driveUrl: String = ""
)

data class Validation(val ok: Boolean, val diff: Double, val message: String)

fun Draft.itemsNet(): Double = items.sumOf { it.lineTotal }

/** Item totals (already net of line discounts) - receipt discounts + rounding (+ tax when prices are tax-exclusive) must equal the amount paid. */
fun Draft.validate(): Validation {
    val base = itemsNet() - discount + rounding
    val inc = base - totalPaid
    val exc = base + tax - totalPaid
    val tol = 0.10
    if (totalPaid <= 0.0) return Validation(false, 0.0, "Enter the amount paid.")
    if (abs(inc) <= tol) return Validation(true, inc, "Items match the amount paid.")
    if (tax > 0 && abs(exc) <= tol) return Validation(true, exc, "Items + tax match the amount paid.")
    val d = if (tax > 0 && abs(exc) < abs(inc)) exc else inc
    val dir = if (d > 0) "exceed" else "are short of"
    return Validation(false, d, "Items $dir the amount paid by ${rm(abs(d))}. Check item prices, discounts, tax or rounding.")
}

data class OcrLine(val text: String, val conf: Float, val box: Rect)
data class Row(val text: String, val conf: Float)

object ReceiptOcr {
    /** Fully on-device (bundled Latin model) - no network needed. */
    suspend fun read(bmp: Bitmap): List<OcrLine> {
        val rec = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val res = rec.process(InputImage.fromBitmap(bmp, 0)).await()
            return res.textBlocks.flatMap { it.lines }.mapNotNull { l ->
                val b = l.boundingBox ?: return@mapNotNull null
                if (l.text.isBlank()) null else OcrLine(l.text, l.confidence, b)
            }
        } finally { rec.close() }
    }

    /** Merge OCR fragments that sit on the same printed line (name on the left, price on the right). */
    fun rows(lines: List<OcrLine>): List<Row> {
        val sorted = lines.sortedBy { it.box.centerY() }
        val groups = mutableListOf<MutableList<OcrLine>>()
        for (l in sorted) {
            val g = groups.lastOrNull()
            if (g != null) {
                val cy = g.map { it.box.centerY() }.average()
                val h = max(l.box.height(), g.maxOf { it.box.height() })
                if (abs(l.box.centerY() - cy) < h * 0.5) { g.add(l); continue }
            }
            groups.add(mutableListOf(l))
        }
        return groups.map { g ->
            val s = g.sortedBy { it.box.left }
            Row(s.joinToString("  ") { it.text.trim() }, s.minOf { it.conf })
        }
    }
}

object ImageProcessor {
    fun load(path: String, maxEdge: Int = 2400): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, o)
        if (o.outWidth <= 0) return null
        var s = 1
        while (max(o.outWidth, o.outHeight) / s > maxEdge * 2) s *= 2
        var bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s }) ?: return null
        val rot = when (ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f; ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f; else -> 0f
        }
        if (rot != 0f) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rot) }, true)
        val m = max(bmp.width, bmp.height)
        if (m > maxEdge) { val f = maxEdge.toFloat() / m; bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * f).toInt(), (bmp.height * f).toInt(), true) }
        return bmp
    }

    fun save(b: Bitmap, f: File) { f.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 85, it) } }

    /** Fallback pipeline (camera/gallery photo): Otsu threshold -> bright paper bounding box -> crop -> contrast stretch. */
    fun autoCropEnhance(src: Bitmap): Bitmap {
        val w = src.width; val h = src.height
        val sc = 240f / max(w, h)
        val small = Bitmap.createScaledBitmap(src, max(1, (w * sc).toInt()), max(1, (h * sc).toInt()), true)
        val sw = small.width; val sh = small.height
        val px = IntArray(sw * sh); small.getPixels(px, 0, sw, 0, 0, sw, sh)
        val lum = IntArray(px.size) { val c = px[it]; (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)).toInt() }
        val hist = IntArray(256); lum.forEach { hist[it]++ }
        var sum = 0.0; for (i in 0..255) sum += i * hist[i]
        var sumB = 0.0; var wB = 0; var best = 0.0; var thr = 128
        for (t in 0..255) {
            wB += hist[t]; if (wB == 0) continue
            val wF = lum.size - wB; if (wF == 0) break
            sumB += t * hist[t]
            val mB = sumB / wB; val mF = (sum - sumB) / wF
            val v = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (v > best) { best = v; thr = t }
        }
        val colF = IntArray(sw)
        for (y in 0 until sh) for (x in 0 until sw) if (lum[y * sw + x] > thr) colF[x]++
        var x0 = sw; var x1 = -1
        for (x in 0 until sw) if (colF[x] > sh * 0.3) { if (x < x0) x0 = x; x1 = x }
        var y0 = sh; var y1 = -1
        if (x1 >= x0) {
            val span = x1 - x0 + 1
            for (y in 0 until sh) {
                var c = 0; for (x in x0..x1) if (lum[y * sw + x] > thr) c++
                if (c > span * 0.5) { if (y < y0) y0 = y; y1 = y }
            }
        }
        var crop = src
        if (x1 > x0 && y1 > y0) {
            val fx = w.toFloat() / sw; val fy = h.toFloat() / sh
            val l = max(0, ((x0 - 2) * fx).toInt()); val t = max(0, ((y0 - 2) * fy).toInt())
            val r = min(w, ((x1 + 3) * fx).toInt()); val b = min(h, ((y1 + 3) * fy).toInt())
            if ((r - l).toLong() * (b - t) > 0.2 * w * h && r > l && b > t) crop = Bitmap.createBitmap(src, l, t, r - l, b - t)
        }
        return enhance(crop)
    }

    private fun enhance(b: Bitmap): Bitmap {
        val w = b.width; val h = b.height
        val px = IntArray(w * h); b.getPixels(px, 0, w, 0, 0, w, h)
        val g = IntArray(px.size) { val c = px[it]; (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)).toInt() }
        val hist = IntArray(256); g.forEach { hist[it]++ }
        var acc = 0; var lo = 0; var hi = 255
        val total = g.size
        for (i in 0..255) { acc += hist[i]; if (acc >= total * 0.02) { lo = i; break } }
        acc = 0
        for (i in 255 downTo 0) { acc += hist[i]; if (acc >= total * 0.02) { hi = i; break } }
        if (hi - lo < 30) return b
        val out = IntArray(px.size) {
            val v = ((g[it] - lo) * 255 / (hi - lo)).coerceIn(0, 255)
            Color.rgb(v, v, v)
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }
}
