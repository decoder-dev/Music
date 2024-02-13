package it.decoder.music

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.ripple.LocalRippleTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material.ripple.RippleTheme
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.valentinilk.shimmer.LocalShimmerTheme
import com.valentinilk.shimmer.defaultShimmerTheme
import it.decoder.compose.persist.LocalPersistMap
import it.decoder.innertube.Innertube
import it.decoder.innertube.models.bodies.BrowseBody
import it.decoder.innertube.requests.playlistPage
import it.decoder.innertube.requests.song
import it.decoder.music.enums.ColorPaletteMode
import it.decoder.music.enums.ColorPaletteName
import it.decoder.music.preferences.AppearancePreferences
import it.decoder.music.service.PlayerService
import it.decoder.music.service.downloadState
import it.decoder.music.ui.components.BottomSheetMenu
import it.decoder.music.ui.components.LocalMenuState
import it.decoder.music.ui.components.rememberBottomSheetState
import it.decoder.music.ui.components.themed.LinearProgressIndicator
import it.decoder.music.ui.screens.albumRoute
import it.decoder.music.ui.screens.artistRoute
import it.decoder.music.ui.screens.home.HomeScreen
import it.decoder.music.ui.screens.player.Player
import it.decoder.music.ui.screens.playlistRoute
import it.decoder.music.ui.styling.Appearance
import it.decoder.music.ui.styling.Dimensions
import it.decoder.music.ui.styling.LocalAppearance
import it.decoder.music.ui.styling.colorPaletteOf
import it.decoder.music.ui.styling.dynamicColorPaletteOf
import it.decoder.music.ui.styling.typographyOf
import it.decoder.music.utils.asMediaItem
import it.decoder.music.utils.forcePlay
import it.decoder.music.utils.intent
import it.decoder.music.utils.isAtLeastAndroid6
import it.decoder.music.utils.isAtLeastAndroid8
import it.decoder.music.utils.isCompositionLaunched
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service is PlayerService.Binder) this@MainActivity.binder = service
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            binder = null
            // Try to rebind, otherwise fail
            unbindService(this)
            bindService(intent<PlayerService>(), this, Context.BIND_AUTO_CREATE)
        }
    }

    private var binder by mutableStateOf<PlayerService.Binder?>(null)

    override fun onStart() {
        super.onStart()
        bindService(intent<PlayerService>(), serviceConnection, Context.BIND_AUTO_CREATE)
    }

    @Suppress("CyclomaticComplexMethod")
    @OptIn(ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val launchedFromNotification = intent?.extras?.getBoolean("expandPlayerBottomSheet") == true

        setContent {
            val coroutineScope = rememberCoroutineScope()
            val isSystemInDarkTheme = isSystemInDarkTheme()

            var appearance by rememberSaveable(
                isSystemInDarkTheme,
                isCompositionLaunched(),
                stateSaver = Appearance.AppearanceSaver
            ) {
                with(AppearancePreferences) {
                    val colorPalette = colorPaletteOf(
                        name = colorPaletteName,
                        mode = colorPaletteMode,
                        isSystemInDarkMode = isSystemInDarkTheme
                    )

                    setSystemBarAppearance(colorPalette.isDark)

                    mutableStateOf(
                        Appearance(
                            colorPalette = colorPalette,
                            typography = typographyOf(
                                color = colorPalette.text,
                                useSystemFont = useSystemFont,
                                applyFontPadding = applyFontPadding
                            ),
                            thumbnailShapeCorners = thumbnailRoundness.dp
                        )
                    )
                }
            }

            DisposableEffect(binder, isSystemInDarkTheme) {
                var bitmapListenerJob: Job? = null
                var appearanceUpdaterJob: Job? = null

                fun setDynamicPalette(colorPaletteMode: ColorPaletteMode) {
                    val isDark = colorPaletteMode == ColorPaletteMode.Dark ||
                            colorPaletteMode == ColorPaletteMode.System && isSystemInDarkTheme

                    binder?.setBitmapListener { bitmap: Bitmap? ->
                        if (bitmap == null) {
                            val colorPalette = colorPaletteOf(
                                name = ColorPaletteName.Dynamic,
                                mode = colorPaletteMode,
                                isSystemInDarkMode = isSystemInDarkTheme
                            )

                            setSystemBarAppearance(colorPalette.isDark)

                            appearance = appearance.copy(
                                colorPalette = colorPalette,
                                typography = appearance.typography.copy(colorPalette.text)
                            )

                            return@setBitmapListener
                        }

                        bitmapListenerJob = coroutineScope.launch(Dispatchers.IO) {
                            dynamicColorPaletteOf(bitmap, isDark)?.let {
                                withContext(Dispatchers.Main) {
                                    setSystemBarAppearance(it.isDark)
                                }
                                appearance = appearance.copy(
                                    colorPalette = it,
                                    typography = appearance.typography.copy(it.text)
                                )
                            }
                        }
                    }
                }

                with(AppearancePreferences) {
                    if (colorPaletteName == ColorPaletteName.Dynamic)
                        setDynamicPalette(colorPaletteMode)
                    appearanceUpdaterJob = coroutineScope.launch {
                        launch {
                            snapshotFlow { colorPaletteName to colorPaletteMode }.collectLatest { (name, mode) ->
                                if (name == ColorPaletteName.Dynamic) setDynamicPalette(mode) else {
                                    bitmapListenerJob?.cancel()
                                    binder?.setBitmapListener(null)

                                    val colorPalette = colorPaletteOf(
                                        name = name,
                                        mode = mode,
                                        isSystemInDarkMode = isSystemInDarkTheme
                                    )

                                    setSystemBarAppearance(colorPalette.isDark)

                                    appearance = appearance.copy(
                                        colorPalette = colorPalette,
                                        typography = appearance.typography.copy(colorPalette.text)
                                    )
                                }
                            }
                        }
                        launch {
                            snapshotFlow { thumbnailRoundness }.collectLatest {
                                appearance = appearance.copy(thumbnailShapeCorners = it.dp)
                            }
                        }
                        launch {
                            snapshotFlow { useSystemFont to applyFontPadding }.collectLatest { (system, padding) ->
                                appearance = appearance.copy(
                                    typography = typographyOf(
                                        appearance.colorPalette.text,
                                        system,
                                        padding
                                    )
                                )
                            }
                        }
                    }
                }

                onDispose {
                    bitmapListenerJob?.cancel()
                    appearanceUpdaterJob?.cancel()
                    binder?.setBitmapListener(null)
                }
            }

            val rippleTheme = remember(
                appearance.colorPalette.text,
                appearance.colorPalette.isDark
            ) {
                object : RippleTheme {
                    @Composable
                    override fun defaultColor(): Color = RippleTheme.defaultRippleColor(
                        contentColor = appearance.colorPalette.text,
                        lightTheme = !appearance.colorPalette.isDark
                    )

                    @Composable
                    override fun rippleAlpha(): RippleAlpha = RippleTheme.defaultRippleAlpha(
                        contentColor = appearance.colorPalette.text,
                        lightTheme = !appearance.colorPalette.isDark
                    )
                }
            }

            val shimmerTheme = remember {
                defaultShimmerTheme.copy(
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = 800,
                            easing = LinearEasing,
                            delayMillis = 250
                        ),
                        repeatMode = RepeatMode.Restart
                    ),
                    shaderColors = listOf(
                        Color.Unspecified.copy(alpha = 0.25f),
                        Color.White.copy(alpha = 0.50f),
                        Color.Unspecified.copy(alpha = 0.25f)
                    )
                )
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(appearance.colorPalette.background0)
            ) {
                val density = LocalDensity.current
                val windowsInsets = WindowInsets.systemBars
                val bottomDp = with(density) { windowsInsets.getBottom(density).toDp() }

                val imeVisible = WindowInsets.isImeVisible
                val imeBottomDp = with(density) { WindowInsets.ime.getBottom(density).toDp() }
                val animatedBottomDp by animateDpAsState(
                    targetValue = if (imeVisible) 0.dp else bottomDp,
                    label = ""
                )

                val playerBottomSheetState = rememberBottomSheetState(
                    dismissedBound = 0.dp,
                    collapsedBound = Dimensions.items.collapsedPlayerHeight + bottomDp,
                    expandedBound = maxHeight
                )

                val playerAwareWindowInsets by remember(
                    bottomDp,
                    animatedBottomDp,
                    playerBottomSheetState.value,
                    imeVisible,
                    imeBottomDp
                ) {
                    derivedStateOf {
                        val bottom =
                            if (imeVisible) imeBottomDp.coerceAtLeast(playerBottomSheetState.value)
                            else playerBottomSheetState.value.coerceIn(
                                animatedBottomDp..playerBottomSheetState.collapsedBound
                            )

                        windowsInsets
                            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
                            .add(WindowInsets(bottom = bottom))
                    }
                }

                CompositionLocalProvider(
                    LocalAppearance provides appearance,
                    LocalIndication provides rememberRipple(bounded = true),
                    LocalRippleTheme provides rippleTheme,
                    LocalShimmerTheme provides shimmerTheme,
                    LocalPlayerServiceBinder provides binder,
                    LocalPlayerAwareWindowInsets provides playerAwareWindowInsets,
                    LocalLayoutDirection provides LayoutDirection.Ltr,
                    LocalPersistMap provides Dependencies.application.persistMap
                ) {
                    val isDownloading by downloadState.collectAsState()

                    Box {
                        HomeScreen(
                            onPlaylistUrl = { url ->
                                onNewIntent(Intent.parseUri(url, 0))
                            }
                        )
                    }

                    AnimatedVisibility(
                        visible = isDownloading,
                        modifier = Modifier.padding(playerAwareWindowInsets.asPaddingValues())
                    ) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                        )
                    }

                    Player(
                        layoutState = playerBottomSheetState,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )

                    BottomSheetMenu(
                        state = LocalMenuState.current,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .imePadding()
                    )
                }

                DisposableEffect(binder?.player) {
                    val player = binder?.player ?: return@DisposableEffect onDispose { }

                    if (player.currentMediaItem == null) {
                        if (!playerBottomSheetState.isDismissed) playerBottomSheetState.dismiss()
                    } else {
                        if (playerBottomSheetState.isDismissed) {
                            if (launchedFromNotification) {
                                intent.replaceExtras(Bundle())
                                playerBottomSheetState.expand(tween(700))
                            } else playerBottomSheetState.collapse(tween(700))
                        }
                    }

                    val listener = object : Player.Listener {
                        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED && mediaItem != null) {
                                if (mediaItem.mediaMetadata.extras?.getBoolean("isFromPersistentQueue") != true) {
                                    playerBottomSheetState.expand(tween(500))
                                } else playerBottomSheetState.collapse(tween(700))
                            }
                        }
                    }

                    player.addListener(listener)

                    onDispose { player.removeListener(listener) }
                }
            }
        }

        onNewIntent(intent)
    }

    @Suppress("CyclomaticComplexMethod")
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        val uri = intent?.data ?: intent?.getStringExtra(Intent.EXTRA_TEXT)?.toUri() ?: return

        intent?.data = null
        this.intent = null

        Log.d("MainActivity", "Opening url: $uri")

        lifecycleScope.launch(Dispatchers.IO) {
            when (val path = uri.pathSegments.firstOrNull()) {
                "playlist" -> uri.getQueryParameter("list")?.let { playlistId ->
                    val browseId = "VL$playlistId"

                    if (playlistId.startsWith("OLAK5uy_")) {
                        Innertube.playlistPage(BrowseBody(browseId = browseId))?.getOrNull()?.let {
                            it.songsPage?.items?.firstOrNull()?.album?.endpoint?.browseId?.let { browseId ->
                                albumRoute.ensureGlobal(browseId)
                            }
                        }
                    } else {
                        playlistRoute.ensureGlobal(browseId, uri.getQueryParameter("params"), null)
                    }
                }

                "channel", "c" -> uri.lastPathSegment?.let { channelId ->
                    artistRoute.ensureGlobal(channelId)
                }

                else -> when {
                    path == "watch" -> uri.getQueryParameter("v")
                    uri.host == "youtu.be" -> path
                    else -> {
                        Toast.makeText(this@MainActivity, "Can't open url $uri", Toast.LENGTH_SHORT)
                            .show()
                        null
                    }
                }?.let { videoId ->
                    Innertube.song(videoId)?.getOrNull()?.let { song ->
                        val binder = snapshotFlow { binder }.filterNotNull().first()
                        withContext(Dispatchers.Main) {
                            binder.player.forcePlay(song.asMediaItem)
                        }
                    }
                }
            }
        }
    }

    override fun onStop() {
        unbindService(serviceConnection)
        super.onStop()
    }

    private fun setSystemBarAppearance(isDark: Boolean) {
        with(WindowCompat.getInsetsController(window, window.decorView.rootView)) {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }

        if (!isAtLeastAndroid6) window.statusBarColor =
            (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()

        if (!isAtLeastAndroid8) window.navigationBarColor =
            (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
    }
}

val LocalPlayerServiceBinder = staticCompositionLocalOf<PlayerService.Binder?> { null }

val LocalPlayerAwareWindowInsets =
    staticCompositionLocalOf<WindowInsets> { error("No player insets provided") }
