package com.tripmate.ai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.tripmate.ai.domain.model.ArtMotif
import com.tripmate.ai.ui.theme.LocalExtendedColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Original, code-drawn destination illustrations ("atmospheric" gradients + silhouettes).
 * Used instead of stock photography so the app is fully offline and has no licensing issues.
 */
@Composable
fun DestinationArt(
    motif: ArtMotif,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    contentDescription: String? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val dark = LocalExtendedColors.current.isDark
    Box(modifier.clip(shape).then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier)) {
        Canvas(Modifier.matchParentSize()) { drawScene(motif, dark) }
        content()
    }
}

private data class Palette(val skyTop: Color, val skyBottom: Color, val sun: Color, val far: Color, val near: Color, val accent: Color)

private fun palette(motif: ArtMotif, dark: Boolean): Palette = if (dark) when (motif) {
    ArtMotif.SEA, ArtMotif.NILE, ArtMotif.FORT, ArtMotif.LIBRARY -> Palette(Color(0xFF071425), Color(0xFF14365A), Color(0xFFE9E3C9), Color(0xFF1E4A6E), Color(0xFF0A2238), Color(0xFF5FD8CC))
    else -> Palette(Color(0xFF0A1424), Color(0xFF3A2A4A), Color(0xFFF1E6C8), Color(0xFF4A3550), Color(0xFF1A1A2E), Color(0xFFE1B867))
} else when (motif) {
    ArtMotif.PYRAMIDS, ArtMotif.SPHINX -> Palette(Color(0xFFFF9E7A), Color(0xFFFFE2B0), Color(0xFFFFF4D6), Color(0xFFD9A066), Color(0xFFB9763F), Color(0xFF8C4E25))
    ArtMotif.NILE, ArtMotif.SEA -> Palette(Color(0xFF5CC6C0), Color(0xFFFFE1C4), Color(0xFFFFF6E0), Color(0xFF2E8C8A), Color(0xFF0C6F6A), Color(0xFFFF795B))
    ArtMotif.GARDEN -> Palette(Color(0xFF9ED9C8), Color(0xFFFFF0D4), Color(0xFFFFF8E6), Color(0xFF5FAE7E), Color(0xFF2F7D57), Color(0xFFE1B867))
    ArtMotif.MOSQUE, ArtMotif.BAZAAR, ArtMotif.CHURCH -> Palette(Color(0xFFFF8E6E), Color(0xFFFFD9A8), Color(0xFFFFF2D2), Color(0xFFC77A55), Color(0xFF7A3E2A), Color(0xFFE1B867))
    ArtMotif.MUSEUM, ArtMotif.LIBRARY, ArtMotif.TOWER, ArtMotif.CITY, ArtMotif.FORT -> Palette(Color(0xFF7CC8D8), Color(0xFFFFE6C2), Color(0xFFFFF7E3), Color(0xFF8AA6B5), Color(0xFF2E4A5F), Color(0xFFFF795B))
    ArtMotif.MEAL, ArtMotif.BREAK -> Palette(Color(0xFFFFC7A8), Color(0xFFFFF0DC), Color(0xFFFFFBF0), Color(0xFFE9A37E), Color(0xFFB0573A), Color(0xFF0C9E92))
}

private fun DrawScope.drawScene(motif: ArtMotif, dark: Boolean) {
    val p = palette(motif, dark)
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(p.skyTop, p.skyBottom)))
    // Sun / moon with soft halo
    val sunCenter = Offset(w * 0.78f, h * 0.30f)
    drawCircle(Brush.radialGradient(listOf(p.sun.copy(alpha = 0.55f), Color.Transparent), sunCenter, h * 0.45f), h * 0.45f, sunCenter)
    drawCircle(p.sun, h * 0.10f, sunCenter)
    if (dark) repeat(18) { i ->
        val x = ((i * 97) % 100) / 100f * w
        val y = ((i * 53) % 45) / 100f * h
        drawCircle(Color.White.copy(alpha = 0.55f), 1.6f + (i % 3), Offset(x, y))
    }
    val ground = h * 0.78f
    when (motif) {
        ArtMotif.PYRAMIDS -> {
            pyramid(w * 0.30f, ground, w * 0.42f, h * 0.48f, p.far, p.accent)
            pyramid(w * 0.62f, ground, w * 0.34f, h * 0.38f, p.near, p.accent)
            pyramid(w * 0.84f, ground, w * 0.18f, h * 0.20f, p.far, p.accent)
            dunes(ground, p.near)
        }
        ArtMotif.SPHINX -> {
            pyramid(w * 0.72f, ground, w * 0.40f, h * 0.45f, p.far, p.accent)
            val body = Path().apply {
                moveTo(w * 0.08f, ground); lineTo(w * 0.08f, ground - h * 0.10f); lineTo(w * 0.30f, ground - h * 0.12f)
                lineTo(w * 0.34f, ground - h * 0.26f); lineTo(w * 0.42f, ground - h * 0.27f); lineTo(w * 0.45f, ground - h * 0.14f)
                lineTo(w * 0.58f, ground - h * 0.08f); lineTo(w * 0.58f, ground); close()
            }
            drawPath(body, p.near)
            dunes(ground, p.near)
        }
        ArtMotif.MUSEUM -> {
            val left = w * 0.16f; val right = w * 0.84f; val top = ground - h * 0.32f
            drawPath(Path().apply { moveTo(left - 10, top); lineTo(w / 2, top - h * 0.14f); lineTo(right + 10, top); close() }, p.near)
            drawRect(p.near, Offset(left, top), Size(right - left, h * 0.04f))
            repeat(6) { i -> drawRect(p.near, Offset(left + 8 + i * (right - left - 30) / 5f, top + h * 0.05f), Size(w * 0.03f, h * 0.24f)) }
            drawRect(p.near, Offset(left - 16, ground - h * 0.04f), Size(right - left + 32, h * 0.04f))
            drawRect(p.far, Offset(0f, ground), Size(w, h - ground))
        }
        ArtMotif.MOSQUE -> {
            drawRect(p.far, Offset(0f, ground - h * 0.10f), Size(w, h * 0.10f))
            val cx = w * 0.5f
            drawRect(p.near, Offset(cx - w * 0.22f, ground - h * 0.22f), Size(w * 0.44f, h * 0.22f))
            drawArc(p.near, 180f, 180f, true, Offset(cx - w * 0.16f, ground - h * 0.40f), Size(w * 0.32f, h * 0.36f))
            minaret(cx - w * 0.30f, ground, h * 0.55f, w * 0.03f, p.near)
            minaret(cx + w * 0.30f, ground, h * 0.55f, w * 0.03f, p.near)
            drawRect(p.near, Offset(0f, ground), Size(w, h - ground))
        }
        ArtMotif.BAZAAR -> {
            drawRect(p.near, Offset(0f, ground - h * 0.38f), Size(w, h * 0.38f + (h - ground)))
            repeat(4) { i ->
                val x = w * (0.08f + i * 0.23f)
                drawArc(p.skyBottom.copy(alpha = 0.85f), 180f, 180f, true, Offset(x, ground - h * 0.30f), Size(w * 0.16f, h * 0.16f))
                drawRect(p.skyBottom.copy(alpha = 0.85f), Offset(x, ground - h * 0.22f), Size(w * 0.16f, h * 0.22f))
                drawLine(p.accent, Offset(x + w * 0.08f, ground - h * 0.38f), Offset(x + w * 0.08f, ground - h * 0.30f), 3f)
                drawCircle(p.accent, h * 0.035f, Offset(x + w * 0.08f, ground - h * 0.27f))
            }
        }
        ArtMotif.GARDEN -> {
            drawOval(p.far, Offset(-w * 0.2f, ground - h * 0.12f), Size(w * 0.9f, h * 0.4f))
            drawOval(p.near, Offset(w * 0.3f, ground - h * 0.06f), Size(w * 0.9f, h * 0.4f))
            palm(w * 0.22f, ground, h * 0.42f, p.near)
            palm(w * 0.70f, ground + h * 0.02f, h * 0.50f, p.near)
        }
        ArtMotif.NILE, ArtMotif.SEA -> {
            val waterTop = h * 0.62f
            if (motif == ArtMotif.NILE) {
                drawRect(p.far, Offset(0f, waterTop - h * 0.08f), Size(w, h * 0.08f))
                repeat(5) { i -> drawRect(p.far, Offset(w * (0.05f + i * 0.2f), waterTop - h * (0.14f + (i % 3) * 0.06f)), Size(w * 0.08f, h * 0.2f)) }
            }
            drawRect(Brush.verticalGradient(listOf(p.near, p.far)), Offset(0f, waterTop), Size(w, h - waterTop))
            repeat(4) { i -> drawLine(p.sun.copy(alpha = 0.5f), Offset(w * 0.55f, waterTop + h * (0.06f + i * 0.07f)), Offset(w * 0.95f, waterTop + h * (0.06f + i * 0.07f)), 2.5f) }
            if (motif == ArtMotif.NILE) {
                val bx = w * 0.32f; val by = waterTop + h * 0.12f
                drawPath(Path().apply { moveTo(bx - w * 0.12f, by); lineTo(bx + w * 0.12f, by); lineTo(bx + w * 0.08f, by + h * 0.05f); lineTo(bx - w * 0.08f, by + h * 0.05f); close() }, Color(0xFF3B2A20))
                drawPath(Path().apply { moveTo(bx, by - 2); lineTo(bx, by - h * 0.42f); lineTo(bx + w * 0.16f, by - h * 0.06f); close() }, Color(0xFFFFF7EA))
            } else {
                drawPath(Path().apply { moveTo(w * 0.1f, waterTop + h * 0.2f); lineTo(w * 0.16f, waterTop + h * 0.08f); lineTo(w * 0.22f, waterTop + h * 0.2f); close() }, Color(0xFFFFF7EA))
            }
        }
        ArtMotif.CHURCH -> {
            drawRect(p.near, Offset(w * 0.22f, ground - h * 0.26f), Size(w * 0.46f, h * 0.26f))
            drawRect(p.near, Offset(w * 0.62f, ground - h * 0.48f), Size(w * 0.12f, h * 0.48f))
            drawArc(p.near, 180f, 180f, true, Offset(w * 0.62f, ground - h * 0.54f), Size(w * 0.12f, h * 0.12f))
            repeat(3) { i -> drawArc(p.skyBottom.copy(alpha = 0.8f), 180f, 180f, true, Offset(w * (0.27f + i * 0.13f), ground - h * 0.18f), Size(w * 0.07f, h * 0.08f)) }
            drawRect(p.far, Offset(0f, ground), Size(w, h - ground))
        }
        ArtMotif.TOWER -> {
            skyline(ground, p.far, w, h)
            val cx = w * 0.5f
            drawPath(Path().apply { moveTo(cx - w * 0.05f, ground); lineTo(cx - w * 0.025f, ground - h * 0.6f); lineTo(cx + w * 0.025f, ground - h * 0.6f); lineTo(cx + w * 0.05f, ground); close() }, p.near)
            drawOval(p.near, Offset(cx - w * 0.06f, ground - h * 0.68f), Size(w * 0.12f, h * 0.10f))
            repeat(8) { i -> drawLine(p.accent.copy(alpha = 0.5f), Offset(cx - w * 0.03f, ground - h * (0.06f + i * 0.07f)), Offset(cx + w * 0.03f, ground - h * (0.1f + i * 0.07f)), 1.5f) }
        }
        ArtMotif.FORT -> {
            val waterTop = h * 0.74f
            drawRect(p.far, Offset(0f, waterTop), Size(w, h - waterTop))
            val left = w * 0.18f; val right = w * 0.82f; val top = waterTop - h * 0.28f
            drawRect(p.near, Offset(left, top), Size(right - left, h * 0.28f))
            var x = left
            while (x < right) { drawRect(p.near, Offset(x, top - h * 0.04f), Size(w * 0.03f, h * 0.04f)); x += w * 0.06f }
            drawRect(p.near, Offset(w * 0.42f, top - h * 0.16f), Size(w * 0.16f, h * 0.16f))
        }
        ArtMotif.LIBRARY -> {
            drawRect(p.far, Offset(0f, ground), Size(w, h - ground))
            val path = Path().apply {
                moveTo(w * 0.1f, ground); lineTo(w * 0.9f, ground - h * 0.30f); lineTo(w * 0.92f, ground); close()
            }
            drawPath(path, p.near)
            repeat(6) { i -> drawLine(p.accent.copy(alpha = 0.6f), Offset(w * (0.2f + i * 0.12f), ground - h * (0.035f + i * 0.045f)), Offset(w * (0.2f + i * 0.12f), ground), 2f) }
        }
        ArtMotif.MEAL, ArtMotif.BREAK -> {
            drawRect(p.far, Offset(0f, ground), Size(w, h - ground))
            val c = Offset(w * 0.45f, ground - h * 0.02f)
            drawOval(Color.White.copy(alpha = 0.9f), Offset(c.x - w * 0.2f, c.y - h * 0.06f), Size(w * 0.4f, h * 0.12f))
            drawRect(p.near, Offset(w * 0.68f, ground - h * 0.22f), Size(w * 0.12f, h * 0.2f))
            repeat(3) { i ->
                val sx = w * (0.70f + i * 0.04f)
                drawPath(Path().apply { moveTo(sx, ground - h * 0.26f); quadraticTo(sx + 8, ground - h * 0.34f, sx, ground - h * 0.42f) }, p.accent, style = Stroke(3f))
            }
        }
        ArtMotif.CITY -> {
            skyline(ground, p.near, w, h)
            drawRect(p.far, Offset(0f, ground), Size(w, h - ground))
        }
    }
    // Subtle bottom scrim for text legibility
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)), startY = h * 0.45f, endY = h))
}

private fun DrawScope.pyramid(cx: Float, base: Float, width: Float, height: Float, color: Color, shade: Color) {
    drawPath(Path().apply { moveTo(cx - width / 2, base); lineTo(cx, base - height); lineTo(cx + width / 2, base); close() }, color)
    drawPath(Path().apply { moveTo(cx, base - height); lineTo(cx + width / 2, base); lineTo(cx + width * 0.12f, base); close() }, shade.copy(alpha = 0.35f))
}

private fun DrawScope.dunes(ground: Float, color: Color) {
    drawPath(Path().apply {
        moveTo(0f, ground); quadraticTo(size.width * 0.3f, ground - 18, size.width * 0.6f, ground); quadraticTo(size.width * 0.8f, ground + 12, size.width, ground - 6)
        lineTo(size.width, size.height); lineTo(0f, size.height); close()
    }, color)
}

private fun DrawScope.minaret(x: Float, ground: Float, height: Float, width: Float, color: Color) {
    drawRect(color, Offset(x - width / 2, ground - height), Size(width, height))
    drawPath(Path().apply { moveTo(x - width / 2, ground - height); lineTo(x, ground - height - width * 2.5f); lineTo(x + width / 2, ground - height); close() }, color)
    drawRect(color, Offset(x - width, ground - height * 0.7f), Size(width * 2, width * 0.6f))
}

private fun DrawScope.palm(x: Float, ground: Float, height: Float, color: Color) {
    drawPath(Path().apply { moveTo(x, ground); quadraticTo(x + 14, ground - height * 0.5f, x + 6, ground - height) }, color, style = Stroke(7f))
    val top = Offset(x + 6, ground - height)
    for (k in 0 until 6) {
        val a = PI.toFloat() * (1.05f + k * 0.18f)
        val end = Offset(top.x + cos(a) * height * 0.32f, top.y + sin(a) * height * 0.10f + height * 0.12f)
        drawPath(Path().apply { moveTo(top.x, top.y); quadraticTo((top.x + end.x) / 2, top.y - height * 0.1f, end.x, end.y) }, color, style = Stroke(5f))
    }
}

private fun DrawScope.skyline(ground: Float, color: Color, w: Float, h: Float) {
    val heights = listOf(0.18f, 0.28f, 0.22f, 0.34f, 0.2f, 0.26f, 0.16f, 0.3f)
    heights.forEachIndexed { i, f -> drawRect(color, Offset(w * i / heights.size, ground - h * f), Size(w / heights.size - 4, h * f)) }
}
