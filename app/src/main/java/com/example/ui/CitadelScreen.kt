package com.example.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.data.Alliance
import com.example.data.BuildingType
import com.example.data.GameCatalog
import com.example.data.PlayerEmpire
import com.example.data.ResourceCost
import com.example.data.ResourceType
import com.example.data.TroopType
import com.example.data.formatCompact
import com.example.data.formatCountdown
import com.example.ui.theme.BasaltSlate
import com.example.ui.theme.CrownGold
import com.example.ui.theme.DarkInkOnGold
import com.example.ui.theme.EmberCrimson
import com.example.ui.theme.EtchedSteel
import com.example.ui.theme.ForgedIron
import com.example.ui.theme.GildedHighlight
import com.example.ui.theme.IvoryOnCrimson
import com.example.ui.theme.MonoStatStyle
import com.example.ui.theme.ParchmentIvory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.ByteArrayInputStream

private const val DIORAMA_HOST = "citadel.crownfall.local"
private const val DIORAMA_ENTRY_URL = "https://$DIORAMA_HOST/assets/index.html"
private const val BRIDGE_NAME = "CrownfallBridge"

private val ALLOWED_LOCAL_ASSETS = mapOf(
    "index.html" to "text/html",
    "textures.js" to "application/javascript",
    "world.js" to "application/javascript",
    "buildings.js" to "application/javascript",
    "ancient.js" to "application/javascript",
    "three.min.js" to "application/javascript",
    "app.js" to "application/javascript"
)

private val ALLOWED_CDN_HOSTS = setOf(
    "cdn.jsdelivr.net",
    "unpkg.com"
)

private class CitadelDioramaBridge(
    private val onBuildingTappedCallback: (BuildingType) -> Unit,
    private val onHarvestTappedCallback: (ResourceType) -> Unit,
    private val onDioramaReadyCallback: () -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onBuildingTapped(rawKey: String?) {
        val building = rawKey?.let { BuildingType.fromRaw(it) } ?: return
        mainHandler.post { onBuildingTappedCallback(building) }
    }

    @JavascriptInterface
    fun onHarvestTapped(rawResource: String?) {
        val resource = ResourceType.entries.firstOrNull { it.name == rawResource } ?: return
        mainHandler.post { onHarvestTappedCallback(resource) }
    }

    @JavascriptInterface
    fun onDioramaReady() {
        mainHandler.post { onDioramaReadyCallback() }
    }
}

private class SandboxedDioramaWebViewClient(
    private val appContext: Context
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        // Block all navigation away from the local 3D diorama entry page
        return true
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val uri = request?.url ?: return forbiddenResponse()
        if (!uri.scheme.equals("https", ignoreCase = true)) {
            return forbiddenResponse()
        }
        val host = uri.host?.lowercase() ?: return forbiddenResponse()

        if (host == DIORAMA_HOST) {
            val path = uri.path ?: return forbiddenResponse()
            val prefix = "/assets/"
            if (!path.startsWith(prefix)) return forbiddenResponse()
            val fileName = path.removePrefix(prefix)
            val mime = ALLOWED_LOCAL_ASSETS[fileName] ?: return forbiddenResponse()
            return try {
                val stream = appContext.assets.open("citadel/$fileName")
                WebResourceResponse(mime, "UTF-8", stream)
            } catch (_: Exception) {
                forbiddenResponse()
            }
        }

        // Allow only Three.js UMD script fetches from approved HTTPS CDNs
        if (host in ALLOWED_CDN_HOSTS) {
            return null
        }

        return forbiddenResponse()
    }

    private fun forbiddenResponse(): WebResourceResponse = WebResourceResponse(
        "text/plain",
        "UTF-8",
        403,
        "Forbidden",
        emptyMap(),
        ByteArrayInputStream(ByteArray(0))
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CitadelScreen(
    empire: PlayerEmpire,
    alliance: Alliance?,
    nowEpochMs: Long,
    perResourceLastHarvestMs: Map<ResourceType, Long>,
    onCollectTribute: ((ResourceCost) -> Unit) -> Unit,
    onCollectSingleResource: (ResourceType, (Long) -> Unit) -> Unit,
    onUpgradeBuilding: (BuildingType) -> Unit,
    onSpeedupBuilding: () -> Unit,
    onTrainTroops: (TroopType, Long) -> Unit,
    onSpeedupTraining: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var selectedBuilding by remember { mutableStateOf<BuildingType?>(null) }
    var dioramaReady by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val latestCollectSingle by rememberUpdatedState(onCollectSingleResource)

    fun pushHarvestPopup(resourceType: ResourceType, amount: Long) {
        if (amount <= 0L) return
        val wv = webViewRef ?: return
        val resJson = JSONObject.quote(resourceType.name)
        val txtJson = JSONObject.quote("+${formatCompact(amount)}")
        wv.evaluateJavascript(
            "window.CrownfallDiorama && window.CrownfallDiorama.showHarvestPopup($resJson, $txtJson);",
            null
        )
    }

    val bridge = remember {
        CitadelDioramaBridge(
            onBuildingTappedCallback = { bType ->
                selectedBuilding = bType
            },
            onHarvestTappedCallback = { resType ->
                latestCollectSingle(resType) { harvested ->
                    pushHarvestPopup(resType, harvested)
                }
            },
            onDioramaReadyCallback = {
                dioramaReady = true
            }
        )
    }

    val pendingAll = GameCatalog.accruedPendingResources(
        empire = empire,
        alliance = alliance,
        nowEpochMs = nowEpochMs,
        perResourceLastHarvestMs = perResourceLastHarvestMs
    )
    val totalPending = pendingAll.food + pendingAll.timber + pendingAll.stone + pendingAll.gold
    val courtyardDesc = stringResource(R.string.cd_courtyard_canvas)

    // Synchronize live empire building levels, timers, upgrade indicators & pending harvest amounts to 3D diorama
    LaunchedEffect(
        dioramaReady,
        empire,
        alliance,
        nowEpochMs,
        perResourceLastHarvestMs,
        selectedBuilding
    ) {
        val wv = webViewRef
        if (!dioramaReady || wv == null) return@LaunchedEffect
        val stateJson = buildDioramaStateJson(
            empire = empire,
            pendingAll = pendingAll,
            nowEpochMs = nowEpochMs,
            selectedBuilding = selectedBuilding
        )
        wv.evaluateJavascript(
            "window.CrownfallDiorama && window.CrownfallDiorama.updateState($stateJson);",
            null
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            val wv = webViewRef
            webViewRef = null
            if (wv != null) {
                wv.removeJavascriptInterface(BRIDGE_NAME)
                wv.stopLoading()
                wv.loadUrl("about:blank")
                wv.destroy()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF9FC2DE))
    ) {
        // 1. Hardware-Accelerated Three.js 3D WebGL Alpine Castle Diorama
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = courtyardDesc }
                .testTag("citadel_courtyard_canvas"),
            factory = { ctx ->
                WebView(ctx).apply {
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
                    setBackgroundColor(android.graphics.Color.parseColor("#9FC2DE"))
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    overScrollMode = View.OVER_SCROLL_NEVER

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = false
                        allowFileAccess = false
                        allowContentAccess = false
                        @Suppress("DEPRECATION")
                        allowFileAccessFromFileURLs = false
                        @Suppress("DEPRECATION")
                        allowUniversalAccessFromFileURLs = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        setSupportZoom(false)
                        builtInZoomControls = false
                        displayZoomControls = false
                        mediaPlaybackRequiresUserGesture = true
                    }

                    webViewClient = SandboxedDioramaWebViewClient(ctx.applicationContext)
                    addJavascriptInterface(bridge, BRIDGE_NAME)
                    loadUrl(DIORAMA_ENTRY_URL)
                    webViewRef = this
                }
            }
        )

        // 2. Top-Left Floating Queue Timer Pills (Construction & Training)
        val buildRemaining = (empire.activeBuildEndsAtMs - nowEpochMs).coerceAtLeast(0L)
        val trainRemaining = (empire.activeTrainEndsAtMs - nowEpochMs).coerceAtLeast(0L)
        if ((empire.activeBuildKey.isNotBlank() && buildRemaining > 0L) ||
            (empire.activeTrainKey.isNotBlank() && trainRemaining > 0L)
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (empire.activeBuildKey.isNotBlank() && buildRemaining > 0L) {
                    val bType = BuildingType.fromRaw(empire.activeBuildKey)
                    FloatingQueuePill(
                        icon = Icons.Filled.Construction,
                        title = bType?.displayName ?: "Building",
                        countdown = formatCountdown(buildRemaining),
                        accentColor = CrownGold,
                        onBodyClick = { if (bType != null) selectedBuilding = bType },
                        onSpeedupClick = onSpeedupBuilding,
                        speedupTag = "courtyard_queue_build_speedup"
                    )
                }
                if (empire.activeTrainKey.isNotBlank() && trainRemaining > 0L) {
                    val tType = TroopType.fromRaw(empire.activeTrainKey)
                    FloatingQueuePill(
                        icon = Icons.Filled.SportsMartialArts,
                        title = "${empire.activeTrainQty} ${tType?.displayName?.substringBefore(" ") ?: "Troops"}",
                        countdown = formatCountdown(trainRemaining),
                        accentColor = EmberCrimson,
                        onBodyClick = { if (tType != null) selectedBuilding = tType.building },
                        onSpeedupClick = onSpeedupTraining,
                        speedupTag = "courtyard_queue_train_speedup"
                    )
                }
            }
        }

        // 3. Bottom-Left Floating "Collect All" Tribute Pill
        Surface(
            onClick = {
                onCollectTribute { gain ->
                    scope.launch {
                        if (gain.food > 0) pushHarvestPopup(ResourceType.FOOD, gain.food)
                        delay(85L)
                        if (gain.timber > 0) pushHarvestPopup(ResourceType.TIMBER, gain.timber)
                        delay(85L)
                        if (gain.stone > 0) pushHarvestPopup(ResourceType.STONE, gain.stone)
                        delay(85L)
                        if (gain.gold > 0) pushHarvestPopup(ResourceType.GOLD, gain.gold)
                    }
                }
            },
            color = Color.Transparent,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.2.dp, GildedHighlight),
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, bottom = 14.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.horizontalGradient(listOf(CrownGold, GildedHighlight)))
                .testTag("courtyard_collect_all_button")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.TrendingUp,
                    contentDescription = null,
                    tint = DarkInkOnGold,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (totalPending >= 10L) {
                        "${stringResource(R.string.collect_all)} • +${formatCompact(totalPending)}"
                    } else {
                        stringResource(R.string.collect_all)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = DarkInkOnGold,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 4. Bottom-Right Camera Controls (Zoom +, Zoom -, Recenter)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CourtyardCameraButton(
                icon = Icons.Filled.Add,
                contentDesc = stringResource(R.string.zoom_in),
                tag = "courtyard_zoom_in",
                onClick = {
                    webViewRef?.evaluateJavascript(
                        "window.CrownfallDiorama && window.CrownfallDiorama.zoomIn();",
                        null
                    )
                }
            )
            CourtyardCameraButton(
                icon = Icons.Filled.Remove,
                contentDesc = stringResource(R.string.zoom_out),
                tag = "courtyard_zoom_out",
                onClick = {
                    webViewRef?.evaluateJavascript(
                        "window.CrownfallDiorama && window.CrownfallDiorama.zoomOut();",
                        null
                    )
                }
            )
            CourtyardCameraButton(
                icon = Icons.Filled.CenterFocusStrong,
                contentDesc = stringResource(R.string.recenter_keep),
                tag = "courtyard_recenter",
                accentGold = true,
                onClick = {
                    webViewRef?.evaluateJavascript(
                        "window.CrownfallDiorama && window.CrownfallDiorama.recenter();",
                        null
                    )
                }
            )
        }
    }

    // 5. Contextual Native Material 3 Slide-Up Building Drawer
    val openBuilding = selectedBuilding
    if (openBuilding != null) {
        CitadelBuildingDrawer(
            building = openBuilding,
            empire = empire,
            alliance = alliance,
            nowEpochMs = nowEpochMs,
            perResourceLastHarvestMs = perResourceLastHarvestMs,
            onDismiss = { selectedBuilding = null },
            onUpgradeBuilding = onUpgradeBuilding,
            onSpeedupBuilding = onSpeedupBuilding,
            onTrainTroops = onTrainTroops,
            onSpeedupTraining = onSpeedupTraining,
            onCollectSingleResource = { resType ->
                onCollectSingleResource(resType) { harvested ->
                    pushHarvestPopup(resType, harvested)
                }
            }
        )
    }
}

private fun buildDioramaStateJson(
    empire: PlayerEmpire,
    pendingAll: ResourceCost,
    nowEpochMs: Long,
    selectedBuilding: BuildingType?
): String {
    val activeBuild = BuildingType.fromRaw(empire.activeBuildKey)
    val buildingsObj = JSONObject()

    for (bType in BuildingType.entries) {
        val level = empire.buildingLevel(bType)
        val isUpgrading = activeBuild == bType && empire.activeBuildEndsAtMs > nowEpochMs
        val remainingMs = (empire.activeBuildEndsAtMs - nowEpochMs).coerceAtLeast(0L)
        val canUpgrade = GameCatalog.canUpgradeBuilding(empire, bType)
        val resType = GameCatalog.resourceTypeFor(bType)
        val pending = when (resType) {
            ResourceType.FOOD -> pendingAll.food
            ResourceType.TIMBER -> pendingAll.timber
            ResourceType.STONE -> pendingAll.stone
            ResourceType.GOLD -> pendingAll.gold
            null -> 0L
        }

        val item = JSONObject().apply {
            put("level", level)
            put("isUpgrading", isUpgrading)
            put("countdown", if (isUpgrading) formatCountdown(remainingMs) else "")
            put("canUpgrade", canUpgrade)
            put("pendingAmount", pending)
            put("pendingFormatted", formatCompact(pending))
        }
        buildingsObj.put(bType.name, item)
    }

    return JSONObject().apply {
        put("selectedBuilding", selectedBuilding?.name ?: "")
        put("buildings", buildingsObj)
    }.toString()
}

@Composable
private fun FloatingQueuePill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    countdown: String,
    accentColor: Color,
    onBodyClick: () -> Unit,
    onSpeedupClick: () -> Unit,
    speedupTag: String
) {
    Surface(
        color = BasaltSlate.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.78f)),
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 5.dp
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onBodyClick)
                .padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(16.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = ParchmentIvory,
                    maxLines = 1
                )
                Text(
                    text = countdown,
                    style = MonoStatStyle.copy(fontSize = 11.sp),
                    color = accentColor
                )
            }
            Surface(
                onClick = onSpeedupClick,
                color = accentColor,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .height(36.dp)
                    .testTag(speedupTag)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bolt,
                        contentDescription = stringResource(R.string.speedup_action),
                        tint = if (accentColor == CrownGold) DarkInkOnGold else IvoryOnCrimson,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = stringResource(R.string.speedup_action),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (accentColor == CrownGold) DarkInkOnGold else IvoryOnCrimson,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun CourtyardCameraButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDesc: String,
    tag: String,
    accentGold: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        color = ForgedIron.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, if (accentGold) CrownGold else EtchedSteel),
        shape = CircleShape,
        shadowElevation = 4.dp,
        modifier = Modifier.size(48.dp)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .fillMaxSize()
                .testTag(tag)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDesc,
                tint = if (accentGold) GildedHighlight else ParchmentIvory,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
