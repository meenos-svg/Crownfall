package com.example.ui

import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.asImageBitmap

import android.graphics.BitmapFactory

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.data.Alliance
import com.example.data.GameCatalog
import com.example.data.PlayerEmpire
import com.example.data.RealmKingdom
import com.example.data.ResourceType
import com.example.data.TroopType
import com.example.data.WorldMarch
import com.example.data.Stronghold
import com.example.data.Realm
import com.example.data.Landmark
import com.example.data.VisitorCamp
import com.example.data.WorldNpcNode
import com.example.data.formatCompact
import com.example.data.formatCountdown
import com.example.ui.theme.ArcaneEmerald
import com.example.ui.theme.BasaltSlate
import com.example.ui.theme.BurnishedCharcoal
import com.example.ui.theme.CrownGold
import com.example.ui.theme.DarkInkOnGold
import com.example.ui.theme.EmberCrimson
import com.example.ui.theme.EtchedSteel
import com.example.ui.theme.FoodAmber
import com.example.ui.theme.ForgedIron
import com.example.ui.theme.GildedHighlight
import com.example.ui.theme.GoldCoin
import com.example.ui.theme.MonoStatStyle
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.ParchmentIvory
import com.example.ui.theme.RoyalAzure
import com.example.ui.theme.StoneSilver
import com.example.ui.theme.TimberMoss
import com.example.ui.theme.WarCrimson
import com.example.ui.theme.WeatheredAsh
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToLong

sealed interface MapTarget {
    val id: String
    val name: String
    val coordX: Long
    val coordY: Long
    val power: Long

    data class PlayerCastle(val kingdom: RealmKingdom) : MapTarget {
        override val id: String = kingdom.ownerUid
        override val name: String = kingdom.lordName
        override val coordX: Long = kingdom.coordX
        override val coordY: Long = kingdom.coordY
        override val power: Long = kingdom.garrisonPower
    }

    data class StrongholdSite(val stronghold: Stronghold) : MapTarget {
        override val id: String = stronghold.id
        override val name: String = stronghold.displayName
        override val coordX: Long = stronghold.coordX
        override val coordY: Long = stronghold.coordY
        override val power: Long = stronghold.defensePower(0L)
    }

    data class LandmarkSite(val landmark: Landmark) : MapTarget {
        override val id: String = landmark.id
        override val name: String = landmark.name
        override val coordX: Long = landmark.coordX
        override val coordY: Long = landmark.coordY
        override val power: Long = landmark.defenseEstimate
    }

    data class CampSite(val camp: VisitorCamp) : MapTarget {
        override val id: String = camp.id
        override val name: String = "${camp.ownerName}'s camp"
        override val coordX: Long = camp.coordX
        override val coordY: Long = camp.coordY
        override val power: Long = camp.troopPower
    }

    data class NpcSite(val node: WorldNpcNode) : MapTarget {
        override val id: String = node.id
        override val name: String = node.name
        override val coordX: Long = node.coordX
        override val coordY: Long = node.coordY
        override val power: Long = node.garrisonPower
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RealmMapScreen(
    empire: PlayerEmpire,
    alliance: Alliance?,
    kingdoms: List<RealmKingdom>,
    activeMarches: List<WorldMarch>,
    nowEpochMs: Long,
    onDispatchMarch: (
        marchType: String,
        targetType: String,
        targetId: String,
        targetName: String,
        toX: Long,
        toY: Long,
        infantry: Long,
        cavalry: Long,
        archers: Long,
        siege: Long
    ) -> Unit,
    onRecallMarch: (WorldMarch) -> Unit,
    onResolveMarch: (WorldMarch) -> Unit,
    onLaunchRally: (
        targetType: String,
        targetId: String,
        targetName: String,
        targetX: Long,
        targetY: Long,
        targetPower: Long
    ) -> Unit,
    strongholds: List<Stronghold> = emptyList(),
    pactAllianceIds: Set<String> = emptySet(),
    onFoundStronghold: (x: Long, y: Long) -> Unit = { _, _ -> },
    onReinforceStronghold: (Stronghold, Long, Long, Long, Long) -> Unit = { _, _, _, _, _ -> },
    onWithdrawGarrison: (Stronghold) -> Unit = {},
    onCallForAid: (String) -> Unit = {},
    onEndCallForAid: () -> Unit = {},
    realms: List<Realm> = emptyList(),
    viewedRealmId: String = "",
    homeEmpire: PlayerEmpire = empire,
    camps: List<VisitorCamp> = emptyList(),
    onSelectRealm: (String) -> Unit = {},
    onSendExpedition: (Long, Long, Long, Long, Long, Long) -> Unit = { _, _, _, _, _, _ -> },
    onRecallCamp: () -> Unit = {},
    landmarks: List<Landmark> = emptyList(),
    onAttackLandmark: (Landmark, Long, Long, Long, Long) -> Unit = { _, _, _, _, _ -> },
    onReinforceLandmark: (Landmark, Long, Long, Long, Long) -> Unit = { _, _, _, _, _ -> },
    onWithdrawLandmark: (Landmark) -> Unit = {}
) {
    val homeRealmId = homeEmpire.homeRealmId
    val shownRealmId = viewedRealmId.ifBlank { homeRealmId }
    val visiting = shownRealmId.isNotBlank() && shownRealmId != homeRealmId
    val myCamp = camps.firstOrNull { it.ownerUid == empire.userId }
    val campHere = myCamp?.takeIf { it.realmId == shownRealmId }
    val campsHere = camps.filter { it.realmId == shownRealmId }
    val landmarksHere = landmarks.filter { it.realmId == shownRealmId }
    val strongholdsHere = strongholds.filter { it.realmId.isBlank() || it.realmId == shownRealmId }
    var expeditionSpot by remember { mutableStateOf<Pair<Long, Long>?>(null) }

    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var zoomScale by remember { mutableFloatStateOf(1.15f) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var selectedTarget by remember { mutableStateOf<MapTarget?>(null) }
    val textMeasurer = rememberTextMeasurer()

    var foundSpot by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    val ownStronghold = strongholds.firstOrNull { it.allianceId == empire.allianceId && empire.allianceId.isNotBlank() }
    val canFound = empire.allianceRole == "LEADER" && ownStronghold == null &&
        (!visiting || campHere?.status == "CAMPED")

    val allTargets = remember(kingdoms, strongholdsHere, campsHere, landmarksHere) {
        strongholdsHere.map { MapTarget.StrongholdSite(it) } +
            landmarksHere.map { MapTarget.LandmarkSite(it) } +
            campsHere.map { MapTarget.CampSite(it) } +
            kingdoms.map { MapTarget.PlayerCastle(it) } +
            GameCatalog.worldNpcNodes.map { MapTarget.NpcSite(it) }
    }

    fun worldToScreenF(wx: Float, wy: Float, size: IntSize): Offset {
        val base = minOf(size.width, size.height).toFloat().coerceAtLeast(1f) * zoomScale
        val cx = size.width / 2f + panOffset.x
        val cy = size.height / 2f + panOffset.y
        val nx = (wx - empire.coordX) / 60f
        val ny = (wy - empire.coordY) / 60f
        return Offset(cx + nx * base, cy + ny * base)
    }

    fun worldToScreen(wx: Long, wy: Long, size: IntSize): Offset =
        worldToScreenF(wx.toFloat(), wy.toFloat(), size)

    fun screenToWorld(p: Offset, size: IntSize): Pair<Long, Long> {
        val base = minOf(size.width, size.height).toFloat().coerceAtLeast(1f) * zoomScale
        val cx = size.width / 2f + panOffset.x
        val cy = size.height / 2f + panOffset.y
        val wx = empire.coordX + (p.x - cx) / base * 60f
        val wy = empire.coordY + (p.y - cy) / base * 60f
        return wx.roundToLong() to wy.roundToLong()
    }

    // The realm's hand-painted map (assets/realms/<realm>.jpg); generated landscape if there isn't one.
    val realmId = shownRealmId.ifBlank { "realm-1" }
    val appContext = LocalContext.current.applicationContext
    val terrain by produceState<RealmTerrain?>(initialValue = null, realmId) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                appContext.assets.open("realms/$realmId.jpg").use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.getOrNull()?.let { RealmTerrain(it, emptyList(), emptyList(), painted = true) }
                ?: RealmTerrainGenerator.generate(realmId)
        }
    }

    val myMarches = remember(activeMarches, empire.userId) {
        activeMarches.filter { it.ownerUid == empire.userId }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid)
    ) {
        // Interactive 2D Live Tactical Kingdom Map Canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        zoomScale = (zoomScale * zoom).coerceIn(0.35f, 3.2f)
                        // Allow panning across the whole realm from wherever your keep is.
                        val limit = minOf(canvasSize.width, canvasSize.height) * zoomScale * 1.8f
                        panOffset = Offset(
                            (panOffset.x + pan.x).coerceIn(-limit, limit),
                            (panOffset.y + pan.y).coerceIn(-limit, limit)
                        )
                    }
                }
                .pointerInput(allTargets, panOffset, zoomScale, canvasSize, canFound, visiting, myCamp?.id) {
                    detectTapGestures(
                        onLongPress = { press ->
                            val spot = screenToWorld(press, canvasSize)
                            if (visiting && myCamp == null) expeditionSpot = spot
                            else if (canFound) foundSpot = spot
                        }
                    ) { tap ->
                        val hit = allTargets.minByOrNull { target ->
                            val pos = worldToScreen(target.coordX, target.coordY, canvasSize)
                            hypot((pos.x - tap.x).toDouble(), (pos.y - tap.y).toDouble())
                        }
                        if (hit != null) {
                            val pos = worldToScreen(hit.coordX, hit.coordY, canvasSize)
                            if (hypot((pos.x - tap.x).toDouble(), (pos.y - tap.y).toDouble()) <= 68.0) {
                                selectedTarget = hit
                            }
                        }
                    }
                }
                .testTag("realm_map_canvas")
        ) {
            // Atmospheric obsidian radial backdrop
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF1A1622), ObsidianVoid),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = maxOf(size.width, size.height) * 0.75f
                )
            )

            // Realm landscape
            terrain?.let { t ->
                val topLeft = worldToScreenF(0f, 0f, canvasSize)
                val bottomRight = worldToScreenF(
                    RealmTerrainGenerator.WORLD_SIZE, RealmTerrainGenerator.WORLD_SIZE, canvasSize
                )
                drawImage(
                    image = t.image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(t.image.width, t.image.height),
                    dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
                    dstSize = IntSize(
                        (bottomRight.x - topLeft.x).roundToInt().coerceAtLeast(1),
                        (bottomRight.y - topLeft.y).roundToInt().coerceAtLeast(1)
                    ),
                    filterQuality = if (t.painted) FilterQuality.Medium else FilterQuality.Low
                )

                // Trees and mountain peaks appear once you zoom in far enough to see them.
                val unitPx = minOf(canvasSize.width, canvasSize.height) * zoomScale / 60f
                if (unitPx >= 9f) {
                    val margin = unitPx * 2
                    fun visible(p: Offset) =
                        p.x > -margin && p.y > -margin && p.x < size.width + margin && p.y < size.height + margin

                    val treePath = Path()
                    val treeH = unitPx * 0.9f
                    val treeW = unitPx * 0.42f
                    t.trees.forEach { w ->
                        val p = worldToScreenF(w.x, w.y, canvasSize)
                        if (visible(p)) {
                            treePath.moveTo(p.x, p.y - treeH * 0.6f)
                            treePath.lineTo(p.x + treeW, p.y + treeH * 0.4f)
                            treePath.lineTo(p.x - treeW, p.y + treeH * 0.4f)
                            treePath.close()
                        }
                    }
                    drawPath(treePath, Color(0xFF13281A))

                    val peakPath = Path()
                    val snowPath = Path()
                    val peakH = unitPx * 1.3f
                    val peakW = unitPx * 0.9f
                    t.peaks.forEach { w ->
                        val p = worldToScreenF(w.x, w.y, canvasSize)
                        if (visible(p)) {
                            peakPath.moveTo(p.x, p.y - peakH * 0.6f)
                            peakPath.lineTo(p.x + peakW, p.y + peakH * 0.4f)
                            peakPath.lineTo(p.x - peakW, p.y + peakH * 0.4f)
                            peakPath.close()
                            snowPath.moveTo(p.x, p.y - peakH * 0.6f)
                            snowPath.lineTo(p.x + peakW * 0.32f, p.y - peakH * 0.28f)
                            snowPath.lineTo(p.x - peakW * 0.32f, p.y - peakH * 0.28f)
                            snowPath.close()
                        }
                    }
                    drawPath(peakPath, Color(0xFF3E3B37))
                    drawPath(snowPath, Color(0xFFD9D6CF))
                }
            }

            // Old roads from the realm's heart to each Shadow Bastion (painted maps have their own roads)
            val roadEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
            val heart = worldToScreen(50L, 50L, canvasSize)
            if (terrain?.painted != true) GameCatalog.worldNpcNodes.filter { it.targetType == "BASTION" }.forEach { node ->
                drawLine(
                    color = Color(0xFF8A7650).copy(alpha = 0.55f),
                    start = heart,
                    end = worldToScreen(node.coordX, node.coordY, canvasSize),
                    strokeWidth = 2.5f,
                    pathEffect = roadEffect
                )
            }

            // Tactical grid lines
            val step = 10L
            for (g in 0L..100L step step) {
                val pStartV = worldToScreen(g, 0L, canvasSize)
                val pEndV = worldToScreen(g, 100L, canvasSize)
                drawLine(EtchedSteel.copy(alpha = 0.10f), pStartV, pEndV, strokeWidth = 1f)

                val pStartH = worldToScreen(0L, g, canvasSize)
                val pEndH = worldToScreen(100L, g, canvasSize)
                drawLine(EtchedSteel.copy(alpha = 0.10f), pStartH, pEndH, strokeWidth = 1f)
            }

            // Realm Center Citadel Territory Ring around (50, 50)
            val centerCrown = worldToScreen(50L, 50L, canvasSize)
            drawCircle(
                color = WarCrimson.copy(alpha = 0.14f),
                radius = 120f * zoomScale,
                center = centerCrown
            )
            drawCircle(
                color = CrownGold.copy(alpha = 0.35f),
                radius = 120f * zoomScale,
                center = centerCrown,
                style = Stroke(width = 2f)
            )

            // Territory zones: each castle, stronghold and held landmark claims the land around it
            val tilePx = minOf(canvasSize.width, canvasSize.height) * zoomScale / 60f
            fun zoneColor(allianceId: String, ownerUid: String? = null): Color = when {
                ownerUid != null && ownerUid == empire.userId -> CrownGold
                allianceId.isNotBlank() && allianceId == empire.allianceId -> RoyalAzure
                allianceId.isNotBlank() && allianceId in pactAllianceIds -> Color(0xFF6FB7E0)
                allianceId.isNotBlank() -> EmberCrimson
                else -> WeatheredAsh
            }
            fun zone(center: Offset, tiles: Float, color: Color) {
                val r = tiles * tilePx
                drawCircle(color.copy(alpha = 0.10f), radius = r, center = center)
                drawCircle(color.copy(alpha = 0.32f), radius = r, center = center, style = Stroke(width = 2f))
            }
            kingdoms.forEach { k ->
                zone(worldToScreen(k.coordX, k.coordY, canvasSize), 3f + k.keepLevel / 3f, zoneColor(k.allianceId, k.ownerUid))
            }
            strongholdsHere.forEach { sh ->
                zone(worldToScreen(sh.coordX, sh.coordY, canvasSize), 7f, zoneColor(sh.allianceId))
            }
            landmarksHere.filter { it.isHeld }.forEach { lm ->
                zone(worldToScreen(lm.coordX, lm.coordY, canvasSize), 4f, zoneColor(lm.holderAllianceId))
            }

            // Animated Real-Time Marching Vectors
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
            activeMarches.forEach { march ->
                val start = worldToScreen(march.fromX, march.fromY, canvasSize)
                val end = worldToScreen(march.toX, march.toY, canvasSize)
                val totalMs = (march.arrivesAtEpochMs - march.departedAtEpochMs).coerceAtLeast(1000L)
                val progress = ((nowEpochMs - march.departedAtEpochMs).toFloat() / totalMs.toFloat())
                    .coerceIn(0f, 1f)
                val currentPos = Offset(
                    start.x + (end.x - start.x) * progress,
                    start.y + (end.y - start.y) * progress
                )
                val lineColor = when {
                    march.marchType == "GATHER" -> ArcaneEmerald
                    march.ownerUid == empire.userId -> CrownGold
                    else -> EmberCrimson
                }
                drawLine(
                    color = lineColor.copy(alpha = 0.7f),
                    start = start,
                    end = end,
                    strokeWidth = 3.5f,
                    pathEffect = dashEffect
                )
                drawCircle(color = lineColor.copy(alpha = 0.35f), radius = 15f, center = currentPos)
                drawCircle(color = lineColor, radius = 7f, center = currentPos)
            }

            // Map markers scale with zoom but stay readable
            val unitPxMarkers = minOf(canvasSize.width, canvasSize.height) * zoomScale / 60f
            val markerS = (unitPxMarkers * 0.95f).coerceIn(9f, 30f)
            val showLabels = unitPxMarkers >= 11f

            // NPC resource sites & Shadow Bastions
            GameCatalog.worldNpcNodes.forEach { node ->
                val pos = worldToScreen(node.coordX, node.coordY, canvasSize)
                if (node.targetType == "BASTION") {
                    drawBastion(pos, markerS * 0.9f)
                    if (showLabels) {
                        drawMapLabel(textMeasurer, node.name, Offset(pos.x, pos.y + markerS * 0.75f), EmberCrimson)
                    }
                } else {
                    val resColor = when (node.resourceType) {
                        ResourceType.FOOD -> FoodAmber
                        ResourceType.TIMBER -> TimberMoss
                        ResourceType.STONE -> StoneSilver
                        ResourceType.GOLD -> GoldCoin
                        null -> ArcaneEmerald
                    }
                    drawResourceSite(pos, markerS * 0.62f, node.resourceType, resColor)
                }
            }

            // Landmarks
            landmarksHere.forEach { lm ->
                val pos = worldToScreen(lm.coordX, lm.coordY, canvasSize)
                val holder = if (lm.isHeld) zoneColor(lm.holderAllianceId) else null
                drawLandmark(pos, markerS * 0.95f, lm.kind, holder)
                if (showLabels) {
                    val label = if (lm.isHeld) "${lm.name} [${lm.holderTag}]" else lm.name
                    drawMapLabel(textMeasurer, label, Offset(pos.x, pos.y + markerS * 0.9f), holder ?: ParchmentIvory)
                }
            }

            // Alliance strongholds
            val pulse = (nowEpochMs % 1600L) / 1600f
            strongholdsHere.forEach { sh ->
                val pos = worldToScreen(sh.coordX, sh.coordY, canvasSize)
                val isOurs = sh.allianceId == empire.allianceId && empire.allianceId.isNotBlank()
                val isPact = sh.allianceId in pactAllianceIds
                val wall = when {
                    isOurs -> CrownGold
                    isPact -> RoyalAzure
                    else -> EmberCrimson
                }
                val shS = markerS * 1.35f
                drawStronghold(
                    c = pos,
                    s = shS,
                    wallColor = wall,
                    bannerColor = if (isOurs || isPact) ParchmentIvory else ObsidianVoid,
                    walls = sh.durability.toFloat() / sh.maxDurability.toFloat(),
                    underSiege = sh.underSiege,
                    callForAid = sh.callForAid,
                    pulse = pulse
                )
                drawMapLabel(
                    textMeasurer,
                    sh.displayName,
                    Offset(pos.x, pos.y + shS * 1.25f),
                    if (isOurs) GildedHighlight else if (sh.callForAid) CrownGold else ParchmentIvory
                )
            }

            // Visitor camps
            campsHere.forEach { camp ->
                val pos = worldToScreen(camp.coordX, camp.coordY, canvasSize)
                val isMineCamp = camp.ownerUid == empire.userId
                val isAllyCamp = empire.allianceId.isNotBlank() && camp.ownerAllianceId == empire.allianceId
                val flag = when {
                    isMineCamp -> CrownGold
                    isAllyCamp -> RoyalAzure
                    else -> EmberCrimson
                }
                drawCamp(pos, markerS * 0.9f, Color(0xFFD8C9A8), flag, camp.status != "CAMPED", pulse)
                if (showLabels || isMineCamp) {
                    drawMapLabel(textMeasurer, "${camp.ownerName}'s camp", Offset(pos.x, pos.y + markerS * 0.85f),
                        if (isMineCamp) GildedHighlight else ParchmentIvory)
                }
            }

            // Player castles (yours on top)
            kingdoms.sortedBy { it.ownerUid == empire.userId }.forEach { k ->
                val pos = worldToScreen(k.coordX, k.coordY, canvasSize)
                val isMine = k.ownerUid == empire.userId
                val isAlly = !empire.allianceId.isBlank() && k.allianceId == empire.allianceId
                val wall = when {
                    isMine -> CrownGold
                    isAlly -> RoyalAzure
                    else -> EmberCrimson
                }
                val banner = when {
                    isMine -> ParchmentIvory
                    isAlly -> ParchmentIvory
                    else -> ObsidianVoid
                }
                val castleS = if (isMine) markerS * 1.2f else markerS
                drawCastle(
                    c = pos,
                    s = castleS,
                    wallColor = wall,
                    bannerColor = banner,
                    shielded = k.shieldUntilEpochMs > nowEpochMs
                )
                if (showLabels || isMine) {
                    drawMapLabel(
                        textMeasurer,
                        k.lordName.ifBlank { "Lord" } + if (k.isBot) " (AI)" else "",
                        Offset(pos.x, pos.y + castleS * 0.8f),
                        if (isMine) GildedHighlight else ParchmentIvory
                    )
                }
            }
        }

        // Top Map Legend + Quick Target Chips
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (realms.size > 1) {
                RealmSwitcher(
                    realms = realms,
                    shownRealmId = shownRealmId,
                    homeRealmId = homeRealmId,
                    campRealmId = myCamp?.realmId,
                    onSelectRealm = onSelectRealm
                )
            }
            if (visiting || myCamp != null) {
                ExpeditionBanner(
                    realms = realms,
                    shownRealmId = shownRealmId,
                    visiting = visiting,
                    myCamp = myCamp,
                    nowEpochMs = nowEpochMs,
                    onRecallCamp = onRecallCamp
                )
            }
            Surface(
                color = BasaltSlate.copy(alpha = 0.9f),
                border = BorderStroke(1.dp, EtchedSteel),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MapLegendDot(CrownGold, "You")
                    MapLegendDot(RoyalAzure, "Ally")
                    MapLegendDot(WarCrimson, "Bastion/Rival")
                    MapLegendDot(TimberMoss, "Nodes")
                }
            }
            if (canFound && empire.allianceId.isNotBlank()) {
                Surface(
                    color = BasaltSlate.copy(alpha = 0.9f),
                    border = BorderStroke(1.dp, CrownGold.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "Leader: long-press an open spot to raise your alliance stronghold",
                        style = MaterialTheme.typography.labelMedium,
                        color = CrownGold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            // Quick Target Selector Strip so players can also tap targets effortlessly
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(
                    items = allTargets.filter { it.id != empire.userId }.take(12),
                    key = { it.id }
                ) { target ->
                    val badgeColor = when (target) {
                        is MapTarget.LandmarkSite -> if (target.landmark.isHeld) CrownGold else WeatheredAsh
                        is MapTarget.CampSite -> EmberCrimson
                        is MapTarget.StrongholdSite -> if (target.stronghold.callForAid) CrownGold else RoyalAzure
                        is MapTarget.PlayerCastle -> EmberCrimson
                        is MapTarget.NpcSite -> if (target.node.targetType == "BASTION") WarCrimson else ArcaneEmerald
                    }
                    Surface(
                        color = ForgedIron.copy(alpha = 0.92f),
                        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(10.dp),
                        onClick = { selectedTarget = target },
                        modifier = Modifier.testTag("map_target_chip_${target.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(badgeColor, CircleShape)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = target.name,
                                style = MaterialTheme.typography.labelLarge,
                                color = ParchmentIvory,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Bottom Controls: Active Marches Strip + Center-on-Home Button
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                FilledIconButton(
                    onClick = {
                        panOffset = Offset.Zero
                        zoomScale = 1.15f
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = CrownGold,
                        contentColor = DarkInkOnGold
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("map_recenter_button")
                ) {
                    Icon(Icons.Filled.Home, contentDescription = "Center on Citadel")
                }
            }

            if (myMarches.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(myMarches, key = { it.id }) { march ->
                        val remaining = (march.arrivesAtEpochMs - nowEpochMs).coerceAtLeast(0L)
                        Surface(
                            color = BasaltSlate.copy(alpha = 0.95f),
                            border = BorderStroke(1.dp, CrownGold),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.width(275.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${march.marchType}: ${march.targetName}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = ParchmentIvory,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = formatCountdown(remaining),
                                        style = MonoStatStyle,
                                        color = CrownGold
                                    )
                                }
                                Text(
                                    text = "PWR ${formatCompact(march.troopPower)} -> (${march.toX}, ${march.toY})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = WeatheredAsh
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { onRecallMarch(march) },
                                        border = BorderStroke(1.dp, EtchedSteel),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(40.dp)
                                    ) {
                                        Icon(Icons.Filled.Undo, null, Modifier.size(15.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Recall", style = MaterialTheme.typography.labelSmall)
                                    }
                                    if (remaining <= 0L) {
                                        GildedButton(
                                            text = "Resolve",
                                            onClick = { onResolveMarch(march) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(40.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    (selectedTarget as? MapTarget.StrongholdSite)?.let { site ->
        val live = strongholds.firstOrNull { it.id == site.stronghold.id } ?: site.stronghold
        StrongholdSheet(
            stronghold = live,
            empire = empire,
            alliance = alliance,
            isPactAlly = live.allianceId in pactAllianceIds,
            onDismiss = { selectedTarget = null },
            onReinforce = { i, c, a, sg -> onReinforceStronghold(live, i, c, a, sg); selectedTarget = null },
            onWithdraw = { onWithdrawGarrison(live); selectedTarget = null },
            onCallForAid = { msg -> onCallForAid(msg); selectedTarget = null },
            onEndCallForAid = { onEndCallForAid(); selectedTarget = null },
            onSiege = { i, c, a, sg ->
                onDispatchMarch("RAID", "STRONGHOLD", live.id, live.displayName, live.coordX, live.coordY, i, c, a, sg)
                selectedTarget = null
            },
            onRally = {
                onLaunchRally("STRONGHOLD", live.id, live.displayName, live.coordX, live.coordY, live.defensePower(0L))
                selectedTarget = null
            }
        )
    }

    (selectedTarget as? MapTarget.LandmarkSite)?.let { site ->
        val live = landmarks.firstOrNull { it.id == site.landmark.id } ?: site.landmark
        val friendly = live.isHeld && empire.allianceId.isNotBlank() &&
            (live.holderAllianceId == empire.allianceId || live.holderAllianceId in pactAllianceIds)
        LandmarkSheet(
            landmark = live,
            empire = empire,
            alliance = alliance,
            isFriendly = friendly,
            onDismiss = { selectedTarget = null },
            onAttack = { i, c, a, sg -> onAttackLandmark(live, i, c, a, sg); selectedTarget = null },
            onReinforce = { i, c, a, sg -> onReinforceLandmark(live, i, c, a, sg); selectedTarget = null },
            onWithdraw = { onWithdrawLandmark(live); selectedTarget = null }
        )
    }

    expeditionSpot?.let { (ex, ey) ->
        ExpeditionDialog(
            x = ex,
            y = ey,
            realmName = realms.firstOrNull { it.id == shownRealmId }?.name ?: "this realm",
            homeEmpire = homeEmpire,
            onConfirm = { i, c, a, sg -> onSendExpedition(ex, ey, i, c, a, sg); expeditionSpot = null },
            onDismiss = { expeditionSpot = null }
        )
    }

    foundSpot?.let { (fx, fy) ->
        FoundStrongholdDialog(
            x = fx,
            y = fy,
            empire = empire,
            onConfirm = { onFoundStronghold(fx, fy); foundSpot = null },
            onDismiss = { foundSpot = null }
        )
    }

    // Tactical Target Command Bottom Sheet
    selectedTarget?.takeIf { it !is MapTarget.StrongholdSite && it !is MapTarget.LandmarkSite }?.let { target ->
        val isOwnCastle = target.id == empire.userId ||
            (target is MapTarget.CampSite && target.camp.ownerUid == empire.userId)
        val isResource = target is MapTarget.NpcSite && target.node.targetType == "RESOURCE"
        val isShielded = target is MapTarget.PlayerCastle && target.kingdom.shieldUntilEpochMs > nowEpochMs
        val isAllyCastle = (target is MapTarget.CampSite && empire.allianceId.isNotBlank() &&
            target.camp.ownerAllianceId == empire.allianceId) || target is MapTarget.PlayerCastle &&
            empire.allianceId.isNotBlank() &&
            target.kingdom.allianceId == empire.allianceId
        val travelMs = GameCatalog.marchDurationMs(
            empire.coordX, empire.coordY, target.coordX, target.coordY, alliance
        )

        var troopShare by remember(target) { mutableFloatStateOf(0.65f) }
        val sendInf = (empire.infantry * troopShare).roundToLong()
        val sendCav = (empire.cavalry * troopShare).roundToLong()
        val sendArc = (empire.archers * troopShare).roundToLong()
        val sendSiege = (empire.siege * troopShare).roundToLong()
        val sentPower = sendInf * TroopType.INFANTRY.power +
            sendCav * TroopType.CAVALRY.power +
            sendArc * TroopType.ARCHERS.power +
            sendSiege * TroopType.SIEGE.power

        ModalBottomSheet(
            onDismissRequest = { selectedTarget = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = ForgedIron
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = target.name,
                            style = MaterialTheme.typography.headlineSmall,
                            color = ParchmentIvory
                        )
                        Text(
                            text = "Coordinates (${target.coordX}, ${target.coordY}) • March ETA ${formatCountdown(travelMs)}",
                            style = MonoStatStyle,
                            color = CrownGold
                        )
                    }
                    Surface(
                        color = BurnishedCharcoal,
                        border = BorderStroke(1.dp, EtchedSteel),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isResource) "RESOURCE" else "DEF ${formatCompact(target.power)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isResource) ArcaneEmerald else EmberCrimson,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                if (target is MapTarget.NpcSite && target.node.targetType == "RESOURCE") {
                    Text(
                        text = "Lv.${target.node.level} Resource Deposit — Dispatch troops to harvest ${target.node.resourceType?.displayName ?: "Resources"} safely.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WeatheredAsh
                    )
                } else if (target is MapTarget.NpcSite) {
                    Text(
                        text = "Lv.${target.node.level} Shadow Bastion — Victory grants plunder (${formatCompact(target.node.rewardFood)}F, ${formatCompact(target.node.rewardGold)}G) + 1 Speedup Hourglass.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WeatheredAsh
                    )
                } else if (target is MapTarget.CampSite) {
                    Text(
                        text = "Visitor camp from another realm • ${formatCompact(target.camp.troopPower)} troop power. " +
                            "Rout it and the survivors flee home through the Waygate.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WeatheredAsh
                    )
                } else if (target is MapTarget.PlayerCastle) {
                    val tagText = if (target.kingdom.allianceTag.isNotBlank()) "[${target.kingdom.allianceTag}] " else ""
                    val aiText = if (target.kingdom.isBot) "AI Lord • " else ""
                    Text(
                        text = "${aiText}${tagText}Keep Lv.${target.kingdom.keepLevel} • Total Power ${formatCompact(target.kingdom.power)}" +
                            if (isShielded) " • Protected by Peace Shield" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isShielded) ArcaneEmerald else WeatheredAsh
                    )
                }

                if (!isOwnCastle && !isAllyCastle && !isShielded) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Commit Garrison", style = MaterialTheme.typography.titleSmall, color = ParchmentIvory)
                        Text(
                            "${(troopShare * 100).toInt()}% (PWR ${formatCompact(sentPower)})",
                            style = MonoStatStyle,
                            color = GildedHighlight
                        )
                    }
                    Slider(
                        value = troopShare,
                        onValueChange = { troopShare = it },
                        valueRange = 0.1f..1.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = CrownGold,
                            activeTrackColor = if (isResource) ArcaneEmerald else WarCrimson,
                            inactiveTrackColor = BurnishedCharcoal
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (isResource) {
                            GildedButton(
                                text = "March & Gather",
                                icon = Icons.Filled.Flag,
                                enabled = sentPower > 0L,
                                onClick = {
                                    onDispatchMarch(
                                        "GATHER", "RESOURCE", target.id, target.name,
                                        target.coordX, target.coordY,
                                        sendInf, sendCav, sendArc, sendSiege
                                    )
                                    selectedTarget = null
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("dispatch_gather_button")
                            )
                        } else {
                            CrimsonButton(
                                text = "March & Raid",
                                icon = Icons.Filled.Whatshot,
                                enabled = sentPower > 0L,
                                onClick = {
                                    val tType = when (target) {
                                        is MapTarget.PlayerCastle -> "KINGDOM"
                                        is MapTarget.CampSite -> "CAMP"
                                        else -> "BASTION"
                                    }
                                    onDispatchMarch(
                                        "RAID", tType, target.id, target.name,
                                        target.coordX, target.coordY,
                                        sendInf, sendCav, sendArc, sendSiege
                                    )
                                    selectedTarget = null
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("dispatch_raid_button")
                            )
                            if (target !is MapTarget.CampSite && !visiting) GildedButton(
                                text = "Rally",
                                icon = Icons.Filled.Groups,
                                enabled = alliance != null,
                                onClick = {
                                    val tType = when (target) {
                                        is MapTarget.PlayerCastle -> "KINGDOM"
                                        is MapTarget.CampSite -> "CAMP"
                                        else -> "BASTION"
                                    }
                                    onLaunchRally(
                                        tType, target.id, target.name,
                                        target.coordX, target.coordY, target.power
                                    )
                                    selectedTarget = null
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("launch_rally_button")
                            )
                        }
                    }
                    if (!isResource && alliance == null) {
                        Text(
                            text = "Join or found an Alliance to coordinate War Rallies.",
                            style = MaterialTheme.typography.bodySmall,
                            color = WeatheredAsh
                        )
                    }
                } else {
                    Surface(
                        color = BurnishedCharcoal,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isShielded) Icons.Filled.Shield else Icons.Filled.MyLocation,
                                contentDescription = null,
                                tint = CrownGold
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = when {
                                    isOwnCastle && target is MapTarget.CampSite -> "Your camp. Its troops raid targets in this realm."
                                    isOwnCastle -> "This is your Obsidian Citadel."
                                    isAllyCastle && target is MapTarget.CampSite -> "An allied camp."
                                    isAllyCastle -> "Allied Citadel — friendly territory."
                                    else -> "Protected by an active Obsidian Peace Shield."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = ParchmentIvory
                            )
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun MapLegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .background(color, CircleShape)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = ParchmentIvory,
            fontWeight = FontWeight.Medium
        )
    }
}
