package com.example.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.data.ResourceType

/*
 * Hand-drawn map markers for the realm map. Every shape is built from simple
 * paths so it stays crisp at any zoom level. `s` is the marker's half-width in pixels.
 */

private val StoneDark = Color(0xFF2A2830)
private val StoneMid = Color(0xFF4A4652)
private val StoneLight = Color(0xFF6E6878)
private val Outline = Color(0xFF0B0B0F)
private val GateDark = Color(0xFF120E0A)

private fun Path.rect(l: Float, t: Float, r: Float, b: Float) {
    moveTo(l, t); lineTo(r, t); lineTo(r, b); lineTo(l, b); close()
}

private fun DrawScope.groundShadow(c: Offset, s: Float) {
    drawOval(
        color = Color.Black.copy(alpha = 0.45f),
        topLeft = Offset(c.x - s * 1.25f, c.y + s * 0.35f),
        size = Size(s * 2.5f, s * 0.55f)
    )
}

/** A walled keep: two towers, a central hall with a banner, and a gate. */
fun DrawScope.drawCastle(
    c: Offset,
    s: Float,
    wallColor: Color,
    bannerColor: Color,
    shielded: Boolean
) {
    groundShadow(c, s)
    if (shielded) {
        drawCircle(Color(0xFF2E9E6B).copy(alpha = 0.18f), radius = s * 1.65f, center = c)
        drawCircle(Color(0xFF52D69A).copy(alpha = 0.55f), radius = s * 1.65f, center = c, style = Stroke(s * 0.09f))
    }

    val x = c.x
    val y = c.y
    val body = Path().apply {
        // curtain wall
        rect(x - 0.75f * s, y - 0.30f * s, x + 0.75f * s, y + 0.55f * s)
        // side towers
        rect(x - 1.05f * s, y - 0.90f * s, x - 0.55f * s, y + 0.55f * s)
        rect(x + 0.55f * s, y - 0.90f * s, x + 1.05f * s, y + 0.55f * s)
        // central hall
        rect(x - 0.32f * s, y - 0.75f * s, x + 0.32f * s, y - 0.25f * s)
        // battlements on the towers and the wall
        val m = 0.15f * s
        for (tx in floatArrayOf(-1.05f, -0.875f, -0.70f, 0.55f, 0.725f, 0.90f)) {
            rect(x + tx * s, y - 0.90f * s - m, x + (tx + 0.15f) * s, y - 0.90f * s)
        }
        var wx = -0.50f
        while (wx < 0.50f) {
            rect(x + wx * s, y - 0.30f * s - m, x + (wx + 0.13f) * s, y - 0.30f * s)
            wx += 0.26f
        }
    }
    drawPath(body, wallColor)
    drawPath(body, Outline, style = Stroke(width = (s * 0.08f).coerceAtLeast(1.2f)))

    // tower shading for depth
    drawRect(Color.Black.copy(alpha = 0.22f), Offset(x + 0.80f * s, y - 0.90f * s), Size(0.25f * s, 1.45f * s))
    drawRect(Color.Black.copy(alpha = 0.22f), Offset(x - 0.80f * s, y - 0.90f * s), Size(0.25f * s, 1.45f * s))

    // arched gate
    drawRect(GateDark, Offset(x - 0.18f * s, y + 0.12f * s), Size(0.36f * s, 0.43f * s))
    drawCircle(GateDark, radius = 0.18f * s, center = Offset(x, y + 0.12f * s))

    // tower windows
    drawRect(GateDark, Offset(x - 0.86f * s, y - 0.55f * s), Size(0.12f * s, 0.22f * s))
    drawRect(GateDark, Offset(x + 0.74f * s, y - 0.55f * s), Size(0.12f * s, 0.22f * s))

    // banner
    drawLine(Outline, Offset(x, y - 0.75f * s), Offset(x, y - 1.35f * s), strokeWidth = (s * 0.07f).coerceAtLeast(1.2f))
    val flag = Path().apply {
        moveTo(x, y - 1.35f * s)
        lineTo(x + 0.55f * s, y - 1.20f * s)
        lineTo(x, y - 1.05f * s)
        close()
    }
    drawPath(flag, bannerColor)
}

/** A dark watchtower with burning windows: an NPC Shadow Bastion. */
fun DrawScope.drawBastion(c: Offset, s: Float) {
    groundShadow(c, s * 0.85f)
    drawCircle(Color(0xFFE03A4A).copy(alpha = 0.16f), radius = s * 1.5f, center = c)

    val x = c.x
    val y = c.y
    val tower = Path().apply {
        moveTo(x - 0.45f * s, y + 0.55f * s)
        lineTo(x - 0.38f * s, y - 0.85f * s)
        lineTo(x - 0.60f * s, y - 0.95f * s)
        lineTo(x - 0.60f * s, y - 1.25f * s)
        lineTo(x + 0.60f * s, y - 1.25f * s)
        lineTo(x + 0.60f * s, y - 0.95f * s)
        lineTo(x + 0.38f * s, y - 0.85f * s)
        lineTo(x + 0.45f * s, y + 0.55f * s)
        close()
        val m = 0.17f * s
        rect(x - 0.60f * s, y - 1.25f * s - m, x - 0.42f * s, y - 1.25f * s)
        rect(x - 0.09f * s, y - 1.25f * s - m, x + 0.09f * s, y - 1.25f * s)
        rect(x + 0.42f * s, y - 1.25f * s - m, x + 0.60f * s, y - 1.25f * s)
    }
    drawPath(tower, StoneDark)
    drawRect(StoneMid, Offset(x - 0.38f * s, y - 0.85f * s), Size(0.30f * s, 1.38f * s))
    drawPath(tower, Outline, style = Stroke(width = (s * 0.08f).coerceAtLeast(1.2f)))

    val ember = Color(0xFFFF6A3D)
    drawRect(ember, Offset(x - 0.10f * s, y - 0.70f * s), Size(0.20f * s, 0.28f * s))
    drawRect(ember, Offset(x - 0.10f * s, y - 0.15f * s), Size(0.20f * s, 0.28f * s))
    drawCircle(ember.copy(alpha = 0.35f), radius = 0.3f * s, center = Offset(x, y - 0.56f * s))

    // tattered crimson banner
    drawLine(Outline, Offset(x + 0.45f * s, y - 1.42f * s), Offset(x + 0.45f * s, y - 2.0f * s), strokeWidth = (s * 0.07f).coerceAtLeast(1.2f))
    val flag = Path().apply {
        moveTo(x + 0.45f * s, y - 2.0f * s)
        lineTo(x + 1.0f * s, y - 1.88f * s)
        lineTo(x + 0.80f * s, y - 1.78f * s)
        lineTo(x + 1.0f * s, y - 1.66f * s)
        lineTo(x + 0.45f * s, y - 1.62f * s)
        close()
    }
    drawPath(flag, Color(0xFFB82534))
}

/**
 * An alliance stronghold: a star-shaped fortress with a great keep and the
 * alliance's banner. A red ring pulses while it is under siege, and a golden
 * beacon burns above it while it calls for aid. `walls` is 0..1 durability.
 */
fun DrawScope.drawStronghold(
    c: Offset,
    s: Float,
    wallColor: Color,
    bannerColor: Color,
    walls: Float,
    underSiege: Boolean,
    callForAid: Boolean,
    pulse: Float
) {
    groundShadow(c, s * 1.2f)
    val x = c.x
    val y = c.y
    if (underSiege) {
        val r = s * (1.7f + 0.35f * pulse)
        drawCircle(Color(0xFFE03A4A).copy(alpha = 0.22f * (1f - pulse)), radius = r, center = c)
        drawCircle(Color(0xFFE03A4A).copy(alpha = 0.8f * (1f - pulse)), radius = r, center = c, style = Stroke(s * 0.08f))
    }

    // star bastions: an eight-point wall around the keep
    val star = Path()
    for (i in 0 until 16) {
        val a = Math.PI * 2 * i / 16 - Math.PI / 2
        val rr = if (i % 2 == 0) 1.25f * s else 0.92f * s
        val px = x + (Math.cos(a) * rr).toFloat()
        val py = y + (Math.sin(a) * rr * 0.62f).toFloat() + 0.12f * s
        if (i == 0) star.moveTo(px, py) else star.lineTo(px, py)
    }
    star.close()
    drawPath(star, StoneMid)
    drawPath(star, Color.Black.copy(alpha = 0.25f))
    drawPath(star, Outline, style = Stroke(width = (s * 0.07f).coerceAtLeast(1.2f)))

    // great keep
    val keep = Path().apply {
        rect(x - 0.42f * s, y - 0.95f * s, x + 0.42f * s, y + 0.30f * s)
        rect(x - 0.62f * s, y - 0.55f * s, x - 0.36f * s, y + 0.30f * s)
        rect(x + 0.36f * s, y - 0.55f * s, x + 0.62f * s, y + 0.30f * s)
        val m = 0.14f * s
        var bx = -0.42f
        while (bx < 0.40f) {
            rect(x + bx * s, y - 0.95f * s - m, x + (bx + 0.14f) * s, y - 0.95f * s)
            bx += 0.28f
        }
    }
    drawPath(keep, wallColor)
    drawRect(Color.Black.copy(alpha = 0.22f), Offset(x + 0.18f * s, y - 0.95f * s), Size(0.24f * s, 1.25f * s))
    drawPath(keep, Outline, style = Stroke(width = (s * 0.08f).coerceAtLeast(1.2f)))
    drawRect(GateDark, Offset(x - 0.14f * s, y - 0.05f * s), Size(0.28f * s, 0.35f * s))
    drawCircle(GateDark, radius = 0.14f * s, center = Offset(x, y - 0.05f * s))

    // tall banner
    drawLine(Outline, Offset(x, y - 1.05f * s), Offset(x, y - 1.85f * s), strokeWidth = (s * 0.07f).coerceAtLeast(1.2f))
    val flag = Path().apply {
        moveTo(x, y - 1.85f * s)
        lineTo(x + 0.70f * s, y - 1.70f * s)
        lineTo(x + 0.50f * s, y - 1.58f * s)
        lineTo(x + 0.70f * s, y - 1.45f * s)
        lineTo(x, y - 1.40f * s)
        close()
    }
    drawPath(flag, bannerColor)
    drawPath(flag, Outline, style = Stroke(width = (s * 0.05f).coerceAtLeast(1f)))

    if (callForAid) {
        val b = Offset(x - 0.55f * s, y - 1.75f * s)
        drawCircle(Color(0xFFFFC94A).copy(alpha = 0.25f + 0.25f * pulse), radius = s * (0.45f + 0.15f * pulse), center = b)
        drawCircle(Color(0xFFFFE08A), radius = s * 0.16f, center = b)
    }

    // wall strength bar
    val barW = 1.9f * s
    val barTop = y + 0.95f * s
    drawRoundRect(Color.Black.copy(alpha = 0.6f), Offset(x - barW / 2, barTop), Size(barW, 0.16f * s), CornerRadius(0.08f * s))
    drawRoundRect(
        if (walls > 0.5f) Color(0xFF52D69A) else if (walls > 0.25f) Color(0xFFFFC94A) else Color(0xFFE03A4A),
        Offset(x - barW / 2, barTop),
        Size(barW * walls.coerceIn(0f, 1f), 0.16f * s),
        CornerRadius(0.08f * s)
    )
}

/** A visitor camp: two canvas tents and a campfire. Faded while it is still travelling. */
fun DrawScope.drawCamp(c: Offset, s: Float, canvas: Color, flag: Color, arriving: Boolean, pulse: Float) {
    val alpha = if (arriving) 0.45f else 1f
    groundShadow(c, s * 0.9f)
    if (arriving) {
        drawCircle(Color(0xFF9B7BFF).copy(alpha = 0.35f * (1f - pulse)), radius = s * (1.2f + pulse), center = c,
            style = Stroke(s * 0.1f))
    }
    val x = c.x
    val y = c.y
    fun tent(cx: Float, w: Float, h: Float, color: Color) {
        val p = Path().apply {
            moveTo(cx, y - h)
            lineTo(cx + w, y + 0.45f * s)
            lineTo(cx - w, y + 0.45f * s)
            close()
        }
        drawPath(p, color.copy(alpha = alpha))
        drawPath(p, Outline.copy(alpha = alpha), style = Stroke((s * 0.07f).coerceAtLeast(1.1f)))
        val door = Path().apply {
            moveTo(cx, y - h * 0.15f)
            lineTo(cx + w * 0.3f, y + 0.45f * s)
            lineTo(cx - w * 0.3f, y + 0.45f * s)
            close()
        }
        drawPath(door, GateDark.copy(alpha = alpha))
    }
    tent(x + 0.42f * s, 0.55f * s, 0.55f * s, canvas.copy(red = canvas.red * 0.85f, green = canvas.green * 0.85f, blue = canvas.blue * 0.85f))
    tent(x - 0.25f * s, 0.75f * s, 0.95f * s, canvas)
    drawLine(Outline.copy(alpha = alpha), Offset(x - 0.25f * s, y - 0.95f * s), Offset(x - 0.25f * s, y - 1.5f * s),
        strokeWidth = (s * 0.06f).coerceAtLeast(1.1f))
    val f = Path().apply {
        moveTo(x - 0.25f * s, y - 1.5f * s)
        lineTo(x + 0.25f * s, y - 1.38f * s)
        lineTo(x - 0.25f * s, y - 1.26f * s)
        close()
    }
    drawPath(f, flag.copy(alpha = alpha))
    if (!arriving) {
        drawCircle(Color(0xFFFF8A3D).copy(alpha = 0.3f + 0.2f * pulse), radius = s * 0.28f, center = Offset(x + 0.95f * s, y + 0.35f * s))
        drawCircle(Color(0xFFFFC94A), radius = s * 0.1f, center = Offset(x + 0.95f * s, y + 0.35f * s))
    }
}

/** A resource site: a round badge with a small picture of what it produces. */
fun DrawScope.drawResourceSite(c: Offset, s: Float, type: ResourceType?, accent: Color) {
    groundShadow(c, s * 0.8f)
    drawCircle(Color(0xFF14141B), radius = s, center = c)
    drawCircle(accent, radius = s, center = c, style = Stroke(width = (s * 0.14f).coerceAtLeast(1.5f)))

    val x = c.x
    val y = c.y
    when (type) {
        ResourceType.FOOD -> {
            // wheat sheaf
            val stalk = Color(0xFFE8C26A)
            for (dx in floatArrayOf(-0.30f, 0f, 0.30f)) {
                drawLine(stalk, Offset(x + dx * 0.4f * s, y + 0.55f * s), Offset(x + dx * s, y - 0.25f * s), strokeWidth = 0.09f * s)
                drawOval(stalk, Offset(x + dx * s - 0.12f * s, y - 0.62f * s), Size(0.24f * s, 0.42f * s))
            }
            drawLine(Color(0xFF8A6D23), Offset(x - 0.25f * s, y + 0.25f * s), Offset(x + 0.25f * s, y + 0.25f * s), strokeWidth = 0.1f * s)
        }
        ResourceType.TIMBER -> {
            // stacked logs
            val bark = Color(0xFF7A5534)
            val ring = Color(0xFFD9B27C)
            val logs = listOf(Offset(-0.30f, 0.25f), Offset(0.30f, 0.25f), Offset(0f, -0.22f))
            logs.forEach { o ->
                val p = Offset(x + o.x * s, y + o.y * s)
                drawCircle(bark, radius = 0.30f * s, center = p)
                drawCircle(ring, radius = 0.20f * s, center = p)
                drawCircle(bark, radius = 0.07f * s, center = p)
            }
        }
        ResourceType.STONE -> {
            // boulders
            val rock = Path().apply {
                moveTo(x - 0.60f * s, y + 0.45f * s)
                lineTo(x - 0.45f * s, y - 0.10f * s)
                lineTo(x - 0.05f * s, y - 0.45f * s)
                lineTo(x + 0.40f * s, y - 0.25f * s)
                lineTo(x + 0.62f * s, y + 0.45f * s)
                close()
            }
            drawPath(rock, StoneLight)
            drawLine(StoneDark, Offset(x - 0.05f * s, y - 0.45f * s), Offset(x + 0.05f * s, y + 0.45f * s), strokeWidth = 0.08f * s)
            drawPath(rock, Outline, style = Stroke(0.07f * s))
        }
        ResourceType.GOLD -> {
            // coin stack
            val coin = Color(0xFFF4C95D)
            val edge = Color(0xFF8A6D23)
            for (i in 0 until 3) {
                val cy = y + (0.35f - i * 0.25f) * s
                drawOval(edge, Offset(x - 0.48f * s, cy - 0.12f * s), Size(0.96f * s, 0.34f * s))
                drawOval(coin, Offset(x - 0.48f * s, cy - 0.20f * s), Size(0.96f * s, 0.34f * s))
            }
        }
        null -> drawCircle(accent, radius = 0.35f * s, center = c)
    }
}

/** A small name tag under a marker, only drawn when zoomed in enough to read. */
fun DrawScope.drawMapLabel(
    measurer: TextMeasurer,
    text: String,
    anchor: Offset,
    color: Color
) {
    val layout = measurer.measure(
        AnnotatedString(text),
        style = TextStyle(color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    )
    val w = layout.size.width.toFloat()
    val h = layout.size.height.toFloat()
    val padX = 6f
    val padY = 2f
    val box = Rect(anchor.x - w / 2 - padX, anchor.y, anchor.x + w / 2 + padX, anchor.y + h + padY * 2)
    drawRoundRect(
        color = Color(0xCC0B0B0F),
        topLeft = box.topLeft,
        size = box.size,
        cornerRadius = CornerRadius(h / 2, h / 2)
    )
    drawText(layout, topLeft = Offset(anchor.x - w / 2, anchor.y + padY))
}
