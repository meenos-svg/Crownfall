package com.example.ui

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * A generated realm landscape: a shaded terrain image covering the whole realm
 * (world units 0..100 on each axis) plus tree and mountain-peak positions for
 * decorations drawn on top when zoomed in.
 *
 * The same realm id always produces the same map, so every player in a realm
 * sees identical terrain without downloading anything.
 */
class RealmTerrain(
    val image: ImageBitmap,
    val trees: List<Offset>,
    val peaks: List<Offset>
)

object RealmTerrainGenerator {
    const val WORLD_SIZE = 100f

    private const val WATER_LEVEL = 0.34f

    fun generate(realmId: String, resolution: Int = 512): RealmTerrain {
        val seed = realmId.hashCode()
        val r = resolution
        val elevation = FloatArray(r * r)
        val moisture = FloatArray(r * r)

        for (py in 0 until r) {
            for (px in 0 until r) {
                val u = (px + 0.5f) * WORLD_SIZE / r
                val v = (py + 0.5f) * WORLD_SIZE / r
                elevation[py * r + px] = elevationAt(u, v, seed)
                moisture[py * r + px] = moistureAt(u, v, seed)
            }
        }

        val pixels = IntArray(r * r)
        for (py in 0 until r) {
            for (px in 0 until r) {
                val i = py * r + px
                val e = elevation[i]
                val m = moisture[i]
                var rgb = baseColor(e, m)

                if (e >= WATER_LEVEL) {
                    // Hill shading: light from the top-left gives the land relief.
                    val left = elevation[py * r + max(px - 1, 0)]
                    val right = elevation[py * r + min(px + 1, r - 1)]
                    val up = elevation[max(py - 1, 0) * r + px]
                    val down = elevation[min(py + 1, r - 1) * r + px]
                    val shade = (1f - ((right - left) + (down - up)) * 9f).coerceIn(0.55f, 1.35f) * 0.92f
                    rgb = scale(rgb, shade)
                }
                pixels[i] = rgb or (0xFF shl 24)
            }
        }

        val bitmap = Bitmap.createBitmap(pixels, r, r, Bitmap.Config.ARGB_8888)

        // Decorations on a jittered grid so they look natural but stay stable.
        val trees = ArrayList<Offset>()
        val peaks = ArrayList<Offset>()
        val step = 1.4f
        var gy = step / 2
        var cellY = 0
        while (gy < WORLD_SIZE) {
            var gx = step / 2
            var cellX = 0
            while (gx < WORLD_SIZE) {
                val jx = gx + (hash(cellX, cellY, seed + 91) - 0.5f) * step * 0.8f
                val jy = gy + (hash(cellX, cellY, seed + 92) - 0.5f) * step * 0.8f
                val e = elevationAt(jx, jy, seed)
                val m = moistureAt(jx, jy, seed)
                val roll = hash(cellX, cellY, seed + 93)
                when {
                    e > 0.78f && roll < 0.22f -> peaks += Offset(jx, jy)
                    e > WATER_LEVEL + 0.03f && e < 0.64f && m > 0.56f && roll < 0.45f -> trees += Offset(jx, jy)
                }
                gx += step
                cellX++
            }
            gy += step
            cellY++
        }

        return RealmTerrain(bitmap.asImageBitmap(), trees, peaks)
    }

    fun elevationAt(u: Float, v: Float, seed: Int): Float {
        var e = fbm(u / 20f, v / 20f, seed)
        e = (e - 0.5f) * 1.3f + 0.5f
        // Coastline: sink the land near the realm's edges, with a ragged shore.
        val edge = min(min(u, v), min(WORLD_SIZE - u, WORLD_SIZE - v)) / WORLD_SIZE +
            (fbm(u / 9f + 50f, v / 9f + 50f, seed + 3) - 0.5f) * 0.10f
        val fall = smoothstep(0.01f, 0.11f, edge)
        return e * (0.6f + 0.4f * fall) + 0.12f * fall - 0.30f * (1f - fall)
    }

    private fun moistureAt(u: Float, v: Float, seed: Int): Float =
        fbm(u / 16f + 100f, v / 16f + 100f, seed + 7)

    // ------------------------------------------------------------------ colours

    private val DEEP = 0x0D1B2A
    private val SHALLOW = 0x1B3A4B
    private val SAND = 0x6E6248
    private val ASH = 0x47463A
    private val GRASS = 0x34472C
    private val FOREST = 0x1F3A24
    private val MARSH = 0x2E3D33
    private val HILL = 0x55503F
    private val MOUNTAIN = 0x6B6762
    private val SNOW = 0xC9C7C2

    private fun baseColor(e: Float, m: Float): Int {
        if (e < WATER_LEVEL) {
            return lerp(DEEP, SHALLOW, smoothstep(0.15f, WATER_LEVEL, e))
        }
        var c = when {
            e < WATER_LEVEL + 0.025f -> SAND
            m > 0.62f && e < 0.42f -> MARSH
            m > 0.56f -> FOREST
            m < 0.40f -> ASH
            else -> GRASS
        }
        c = lerp(c, HILL, smoothstep(0.64f, 0.72f, e))
        c = lerp(c, MOUNTAIN, smoothstep(0.75f, 0.83f, e))
        c = lerp(c, SNOW, smoothstep(0.88f, 0.94f, e))
        return c
    }

    private fun lerp(a: Int, b: Int, t: Float): Int {
        val r = ((a shr 16) and 0xFF) + ((((b shr 16) and 0xFF) - ((a shr 16) and 0xFF)) * t)
        val g = ((a shr 8) and 0xFF) + ((((b shr 8) and 0xFF) - ((a shr 8) and 0xFF)) * t)
        val bl = (a and 0xFF) + (((b and 0xFF) - (a and 0xFF)) * t)
        return (r.toInt() shl 16) or (g.toInt() shl 8) or bl.toInt()
    }

    private fun scale(rgb: Int, f: Float): Int {
        val r = (((rgb shr 16) and 0xFF) * f).toInt().coerceIn(0, 255)
        val g = (((rgb shr 8) and 0xFF) * f).toInt().coerceIn(0, 255)
        val b = ((rgb and 0xFF) * f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    // ------------------------------------------------------------------ noise

    private fun smoothstep(a: Float, b: Float, x: Float): Float {
        val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun hash(ix: Int, iy: Int, seed: Int): Float {
        var h = ix * 374761393 + iy * 668265263 + seed * 1442695041
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0x7fffffff) / 2147483648f
    }

    private fun valueNoise(x: Float, y: Float, seed: Int): Float {
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val fx = x - x0
        val fy = y - y0
        val sx = fx * fx * (3f - 2f * fx)
        val sy = fy * fy * (3f - 2f * fy)
        val a = hash(x0, y0, seed)
        val b = hash(x0 + 1, y0, seed)
        val c = hash(x0, y0 + 1, seed)
        val d = hash(x0 + 1, y0 + 1, seed)
        return (a + (b - a) * sx) * (1f - sy) + (c + (d - c) * sx) * sy
    }

    private fun fbm(x: Float, y: Float, seed: Int, octaves: Int = 5): Float {
        var sum = 0f
        var amp = 1f
        var freq = 1f
        var norm = 0f
        for (o in 0 until octaves) {
            sum += amp * valueNoise(x * freq, y * freq, seed + o * 31)
            norm += amp
            amp *= 0.5f
            freq *= 2.03f
        }
        return sum / norm
    }
}
