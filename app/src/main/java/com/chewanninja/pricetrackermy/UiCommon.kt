package com.chewanninja.pricetrackermy

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

val Brand = Color(0xFF2F66E8)
val Bg = Color(0xFFF3F5FA)
val Orange = Color(0xFFF59E0B)
val Red = Color(0xFFE53935)
val Green = Color(0xFF2E7D32)
val LevelColors = listOf(Color(0xFF2E7D32), Color(0xFF9E9D24), Color(0xFFF57C00), Color(0xFFE64A19), Color(0xFFC62828))
val HeaderBrush = Brush.verticalGradient(listOf(Color(0xFF2557D6), Color(0xFF3F7DFF)))

fun hex(s: String): Color = try { Color(android.graphics.Color.parseColor(s)) } catch (e: Exception) { Color.Gray }

fun catEmoji(id: String): String = when (id) {
    "chicken_meat" -> "🍗"; "seafood" -> "🐟"; "vegetables" -> "🥬"; "fruits" -> "🍎"; "eggs" -> "🥚"; "rice" -> "🍚"
    "cooking_oil" -> "🛢️"; "groceries" -> "🛒"; "dairy_milk" -> "🥛"; "beverages" -> "🥤"; "snacks" -> "🍪"
    "household" -> "🧽"; "personal_care" -> "🧴"; "baby" -> "🍼"; "others" -> "📦"; else -> "🏷️"
}

// ---------- layout building blocks ----------
@Composable fun GradientHeader(title: String, onBack: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit = {}) {
    Column(Modifier.fillMaxWidth().background(HeaderBrush, RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp)).padding(horizontal = 8.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) } else Spacer(Modifier.width(8.dp))
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        content()
    }
}

@Composable fun Pad(content: @Composable ColumnScope.() -> Unit) { Column(Modifier.padding(horizontal = 12.dp), content = content) }

@Composable fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

@Composable fun Segmented(options: List<String>, selected: Int, modifier: Modifier = Modifier, equal: Boolean = false, onSelect: (Int) -> Unit) {
    Row(modifier.background(Color(0xFFE6ECFA), RoundedCornerShape(20.dp)).padding(3.dp)) {
        options.forEachIndexed { i, o ->
            val sel = i == selected
            Box((if (equal) Modifier.weight(1f) else Modifier).background(if (sel) Brand else Color.Transparent, RoundedCornerShape(17.dp))
                .clickable { onSelect(i) }.padding(horizontal = 14.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                Text(o, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (sel) Color.White else Color(0xFF55607A))
            }
        }
    }
}

@Composable fun Pill(text: String, onClick: () -> Unit) {
    Box(Modifier.background(Color.White, RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Brand)
    }
}

@Composable fun IconTile(emoji: String, color: Color, dia: Dp = 40.dp) {
    Box(Modifier.size(dia).background(color.copy(alpha = 0.16f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        Text(emoji, fontSize = (dia.value * 0.48f).sp)
    }
}

@Composable fun MiniStat(label: String, value: String, modifier: Modifier = Modifier, color: Color = Brand) {
    Column(modifier.background(color.copy(alpha = 0.08f), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Text(label, fontSize = 10.sp, color = Color.Gray, maxLines = 1)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

// ---------- inputs ----------
@Composable fun Pick(label: String, value: String, options: List<Pair<String, String>>, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: ${options.firstOrNull { it.first == value }?.second ?: value}", maxLines = 1)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { open = false; onPick(id) }) }
        }
    }
}

@Composable fun Field(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier,
                      uncertain: Boolean = false, number: Boolean = false, single: Boolean = true, secret: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, modifier = modifier.fillMaxWidth(), singleLine = single,
        colors = if (uncertain) OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Orange, unfocusedBorderColor = Orange, focusedLabelColor = Orange, unfocusedLabelColor = Orange,
            focusedContainerColor = Color(0x22F59E0B), unfocusedContainerColor = Color(0x22F59E0B)) else OutlinedTextFieldDefaults.colors(),
        supportingText = if (uncertain) { { Text("Low confidence - please check", color = Orange) } } else null,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text)
    )
}

/** Numeric field that keeps its own text while typing; re-initialises only when [key] changes. */
@Composable fun NumField(label: String, init: Double, key: Any, modifier: Modifier = Modifier, uncertain: Boolean = false, onValue: (Double) -> Unit) {
    var t by remember(key) { mutableStateOf(if (init == 0.0) "" else fmt2(init)) }
    Field(label, t, { t = it; onValue(it.num() ?: 0.0) }, modifier, uncertain, number = true)
}

@Composable fun Bar(frac: Float, color: Color, modifier: Modifier = Modifier) {
    LinearProgressIndicator(progress = { frac.coerceIn(0f, 1f) }, color = color, trackColor = Color(0xFFE6ECFA), modifier = modifier.fillMaxWidth().height(8.dp))
}

@Composable fun Thumb(path: String, size: Dp = 48.dp, sample: Int = 8) {
    val bmp = remember(path) { runCatching { BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap() }.getOrNull() }
    if (bmp != null) Image(bmp, null, Modifier.size(size), contentScale = ContentScale.Crop)
    else Box(Modifier.size(size))
}

// ---------- charts ----------
@Composable fun Ring(frac: Float, color: Color, dia: Dp = 110.dp, stroke: Dp = 12.dp, content: @Composable () -> Unit) {
    Box(Modifier.size(dia), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx(); val s = Size(this.size.width - w, this.size.height - w); val tl = Offset(w / 2, w / 2)
            drawArc(Color(0xFFE3EAF6), 0f, 360f, false, tl, s, style = Stroke(w))
            drawArc(color, -90f, 360f * frac.coerceIn(0f, 1f), false, tl, s, style = Stroke(w, cap = StrokeCap.Round))
        }
        content()
    }
}

@Composable fun Donut(parts: List<Pair<Color, Double>>, dia: Dp = 140.dp, content: @Composable () -> Unit = {}) {
    val total = parts.sumOf { it.second }
    Box(Modifier.size(dia), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 22.dp.toPx(); val s = Size(this.size.width - w, this.size.height - w); val tl = Offset(w / 2, w / 2)
            if (total <= 0.0) drawArc(Color(0xFFE3EAF6), 0f, 360f, false, tl, s, style = Stroke(w))
            else {
                var start = -90f
                parts.forEach { (c, v) ->
                    val sw = (v / total * 360).toFloat()
                    drawArc(c, start, (sw - 2f).coerceAtLeast(0.5f), false, tl, s, style = Stroke(w))
                    start += sw
                }
            }
        }
        content()
    }
}

@Composable fun Legend(color: Color, name: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(9.dp).background(color, CircleShape))
        Text(name, Modifier.weight(1f), fontSize = 12.sp, maxLines = 1)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable fun BarChart(labels: List<String>, values: List<Double>, modifier: Modifier = Modifier.fillMaxWidth().height(160.dp), showValues: Boolean = true) {
    val lc = Color(0xFF8A94A6).toArgb(); val vc = Color(0xFF3A4252).toArgb()
    Canvas(modifier) {
        val mx = (values.maxOrNull() ?: 0.0).coerceAtLeast(1.0); val n = values.size.coerceAtLeast(1)
        val bw = size.width / n; val top = 16.dp.toPx(); val ph = size.height - 26.dp.toPx() - top; val base = top + ph
        val lp = android.graphics.Paint().apply { textSize = 10.sp.toPx(); color = lc; textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true }
        val vp = android.graphics.Paint().apply { textSize = 9.sp.toPx(); color = vc; textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true }
        values.forEachIndexed { i, v ->
            val h = (v / mx * ph).toFloat().coerceAtLeast(if (v > 0) 3.dp.toPx() else 0f)
            val c = if (i == values.lastIndex) Brand else Brand.copy(alpha = 0.35f)
            drawRoundRect(c, Offset(i * bw + bw * 0.18f, base - h), Size(bw * 0.64f, h), CornerRadius(6.dp.toPx()))
            if (showValues && v > 0 && n <= 14) {
                val t = if (v >= 1000) String.format(Locale.US, "%.1fk", v / 1000) else String.format(Locale.US, "%.0f", v)
                drawContext.canvas.nativeCanvas.drawText(t, i * bw + bw / 2, base - h - 4.dp.toPx(), vp)
            }
            drawContext.canvas.nativeCanvas.drawText(labels.getOrElse(i) { "" }, i * bw + bw / 2, size.height - 6.dp.toPx(), lp)
        }
    }
}

@Composable fun AreaChart(values: List<Double>, labels: List<String>, modifier: Modifier = Modifier.fillMaxWidth().height(160.dp)) {
    val lc = Color(0xFF8A94A6).toArgb()
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val mx = values.max().coerceAtLeast(1.0); val left = 6.dp.toPx(); val w = size.width - left * 2
        val top = 14.dp.toPx(); val ph = size.height - 26.dp.toPx() - top; val base = top + ph
        val pts = values.mapIndexed { i, v -> Offset(left + w * i / (values.size - 1), base - (v / mx * ph).toFloat()) }
        val line = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 0 until pts.size - 1) { val cx = (pts[i].x + pts[i + 1].x) / 2; cubicTo(cx, pts[i].y, cx, pts[i + 1].y, pts[i + 1].x, pts[i + 1].y) }
        }
        val fill = Path().apply { addPath(line); lineTo(pts.last().x, base); lineTo(pts[0].x, base); close() }
        drawPath(fill, Brush.verticalGradient(listOf(Brand.copy(alpha = 0.30f), Brand.copy(alpha = 0f)), startY = top, endY = base))
        drawPath(line, Brand, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        val mi = values.indexOf(values.max())
        drawCircle(Color.White, 7.dp.toPx(), pts[mi]); drawCircle(Brand, 4.5.dp.toPx(), pts[mi])
        val lp = android.graphics.Paint().apply { textSize = 10.sp.toPx(); color = lc; textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true }
        labels.forEachIndexed { i, l -> if (l.isNotBlank() && i < pts.size) drawContext.canvas.nativeCanvas.drawText(l, pts[i].x, size.height - 6.dp.toPx(), lp) }
    }
}

@Composable fun LineChart(values: List<Double>, modifier: Modifier = Modifier.fillMaxWidth().height(160.dp)) {
    AreaChart(values, emptyList(), modifier)
}
