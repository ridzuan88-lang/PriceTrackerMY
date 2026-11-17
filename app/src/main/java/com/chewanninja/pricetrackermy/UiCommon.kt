package com.chewanninja.pricetrackermy

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions

val Orange = Color(0xFFF59E0B)
val LevelColors = listOf(Color(0xFF2E7D32), Color(0xFF9E9D24), Color(0xFFF57C00), Color(0xFFE64A19), Color(0xFFC62828))
fun hex(s: String): Color = try { Color(android.graphics.Color.parseColor(s)) } catch (e: Exception) { Color.Gray }

@Composable fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

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
    LinearProgressIndicator(progress = { frac.coerceIn(0f, 1f) }, color = color, modifier = modifier.fillMaxWidth().height(8.dp))
}

@Composable fun Thumb(path: String, size: Dp = 48.dp, sample: Int = 8) {
    val bmp = remember(path) { runCatching { BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap() }.getOrNull() }
    if (bmp != null) Image(bmp, null, Modifier.size(size), contentScale = ContentScale.Crop)
    else Box(Modifier.size(size))
}

@Composable fun BarChart(labels: List<String>, values: List<Double>, modifier: Modifier = Modifier.fillMaxWidth().height(180.dp)) {
    val txt = MaterialTheme.colorScheme.onSurface.toArgb(); val col = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val mx = (values.maxOrNull() ?: 0.0).coerceAtLeast(1.0); val n = values.size.coerceAtLeast(1)
        val bw = size.width / n; val ph = size.height - 28.dp.toPx()
        val paint = android.graphics.Paint().apply { textSize = 10.sp.toPx(); this.color = txt; textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true }
        values.forEachIndexed { i, v ->
            val h = (v / mx * ph).toFloat()
            drawRect(col, Offset(i * bw + bw * 0.15f, ph - h), Size(bw * 0.7f, h))
            drawContext.canvas.nativeCanvas.drawText(labels.getOrElse(i) { "" }, i * bw + bw / 2, size.height - 8.dp.toPx(), paint)
        }
    }
}

@Composable fun LineChart(values: List<Double>, modifier: Modifier = Modifier.fillMaxWidth().height(160.dp)) {
    val col = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val mn = values.min(); val mx = values.max(); val span = (mx - mn).coerceAtLeast(0.01)
        val pts = values.mapIndexed { i, v -> Offset(size.width * i / (values.size - 1), (size.height - 12.dp.toPx() * 2) * (1 - ((v - mn) / span)).toFloat() + 12.dp.toPx()) }
        for (i in 0 until pts.size - 1) drawLine(col, pts[i], pts[i + 1], strokeWidth = 3.dp.toPx())
        pts.forEach { drawCircle(col, 4.dp.toPx(), it) }
    }
}
