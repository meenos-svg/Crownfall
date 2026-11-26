package com.example.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Castle
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.example.ui.theme.BurnishedCharcoal
import com.example.ui.theme.CrownGold
import com.example.ui.theme.EtchedSteel
import com.example.ui.theme.ForgedIron
import com.example.ui.theme.ParchmentIvory
import com.example.ui.theme.WeatheredAsh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The twelve heraldic crests every lord can pick from: an emblem on a coloured field. */
private data class Crest(val icon: ImageVector, val field: Color, val charge: Color)

private val CRESTS = listOf(
    Crest(Icons.Filled.Shield, Color(0xFF8E1E24), Color(0xFFE8C46A)),       // 1 crimson & gold shield
    Crest(Icons.Filled.Whatshot, Color(0xFF3A1F14), Color(0xFFFF8A3D)),     // 2 ember flame
    Crest(Icons.Filled.Pets, Color(0xFF1F4A2E), Color(0xFFE9DFC4)),         // 3 forest beast
    Crest(Icons.Filled.AcUnit, Color(0xFF1C3D63), Color(0xFFCDE7FF)),       // 4 frost
    Crest(Icons.Filled.Bolt, Color(0xFF3B2266), Color(0xFFFFD84A)),         // 5 storm
    Crest(Icons.Filled.Castle, Color(0xFF2E2E36), Color(0xFFD9C08A)),       // 6 tower
    Crest(Icons.Filled.Anchor, Color(0xFF113A44), Color(0xFFB8E3E9)),       // 7 sea
    Crest(Icons.Filled.LocalFlorist, Color(0xFF5B1A3A), Color(0xFFF2B8D2)), // 8 rose
    Crest(Icons.Filled.Diamond, Color(0xFF14323F), Color(0xFF7FE0F0)),      // 9 jewel
    Crest(Icons.Filled.WbSunny, Color(0xFF6B4410), Color(0xFFFFE08A)),      // 10 sun
    Crest(Icons.Filled.NightsStay, Color(0xFF151A33), Color(0xFFC9C7FF)),   // 11 moon
    Crest(Icons.Filled.Star, Color(0xFF40142A), Color(0xFFFFD27A)),         // 12 star
)

private const val PORTRAIT_SLOTS = 24

@Composable
private fun rememberPortrait(index: Int): ImageBitmap? {
    val context = LocalContext.current.applicationContext
    val bitmap by produceState<ImageBitmap?>(initialValue = null, index) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("avatars/portrait$index.webp").use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            }.getOrNull()
        }
    }
    return bitmap
}

/** Which painted portraits are bundled in the app (assets/avatars/portraitN.webp). */
@Composable
private fun rememberAvailablePortraits(): List<Int> {
    val context = LocalContext.current.applicationContext
    return remember {
        val files = runCatching { context.assets.list("avatars")?.toList() }.getOrNull().orEmpty()
        (1..PORTRAIT_SLOTS).filter { "portrait$it.webp" in files }
    }
}

@Composable
private fun CrestFace(index: Int, size: Dp) {
    val crest = CRESTS[(index - 1).coerceIn(0, CRESTS.lastIndex)]
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(crest.field.copy(alpha = 1f), Color(0xFF0B0B0F)))),
        contentAlignment = Alignment.Center
    ) {
        Icon(crest.icon, contentDescription = null, tint = crest.charge, modifier = Modifier.size(size * 0.56f))
    }
}

/**
 * A lord's avatar in a gold-ringed circle. [avatar] is "crest:N", "portrait:N" or "google"
 * (which shows [avatarUrl], the Google profile photo). Anything unknown falls back to the first crest.
 */
@Composable
fun LordAvatar(
    avatar: String,
    avatarUrl: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    ringColor: Color = CrownGold
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFF0B0B0F))
            .border(if (size >= 40.dp) 2.dp else 1.5.dp, ringColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        val kind = avatar.substringBefore(':')
        val n = avatar.substringAfter(':', "1").toIntOrNull() ?: 1
        when {
            kind == "google" && !avatarUrl.isNullOrBlank() -> SubcomposeAsyncImage(
                model = avatarUrl,
                contentDescription = "Avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { CrestFace(1, size) },
                error = { CrestFace(1, size) }
            )
            kind == "portrait" -> {
                val portrait = rememberPortrait(n)
                if (portrait != null) {
                    Image(portrait, contentDescription = "Avatar", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    CrestFace(1, size)
                }
            }
            else -> CrestFace(n, size)
        }
    }
}

/** Choose a Google photo, a painted portrait (when bundled) or a heraldic crest. */
@Composable
fun AvatarPickerDialog(
    current: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val portraits = rememberAvailablePortraits()
    val options = buildList {
        add("google")
        portraits.forEach { add("portrait:$it") }
        (1..CRESTS.size).forEach { add("crest:$it") }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ForgedIron,
        title = { Text("Choose your likeness", color = CrownGold) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.heightIn(max = 420.dp)
            ) {
                items(options, key = { it }) { option ->
                    val selected = option == current
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onPick(option) }
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (option == "google") {
                                Surface(
                                    shape = CircleShape,
                                    color = BurnishedCharcoal,
                                    border = BorderStroke(if (selected) 3.dp else 1.5.dp, if (selected) CrownGold else EtchedSteel),
                                    modifier = Modifier.size(58.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = ParchmentIvory,
                                            modifier = Modifier.size(34.dp))
                                    }
                                }
                            } else {
                                LordAvatar(
                                    avatar = option,
                                    avatarUrl = null,
                                    size = 58.dp,
                                    ringColor = if (selected) CrownGold else EtchedSteel
                                )
                            }
                        }
                        if (option == "google") {
                            Text("Google", style = MaterialTheme.typography.labelSmall, color = WeatheredAsh)
                        }
                    }
                }
                if (portraits.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "Painted portraits will appear here once they're added.",
                            style = MaterialTheme.typography.bodySmall,
                            color = WeatheredAsh,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = WeatheredAsh) }
        }
    )
}
