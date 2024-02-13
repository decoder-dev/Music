package it.decoder.music.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.decoder.compose.persist.persist
import it.decoder.innertube.Innertube
import it.decoder.innertube.models.NavigationEndpoint
import it.decoder.innertube.models.bodies.NextBody
import it.decoder.innertube.requests.relatedPage
import it.decoder.music.Database
import it.decoder.music.LocalPlayerAwareWindowInsets
import it.decoder.music.LocalPlayerServiceBinder
import it.decoder.music.R
import it.decoder.music.models.Song
import it.decoder.music.preferences.DataPreferences
import it.decoder.music.query
import it.decoder.music.ui.components.LocalMenuState
import it.decoder.music.ui.components.ShimmerHost
import it.decoder.music.ui.components.themed.FloatingActionsContainerWithScrollToTop
import it.decoder.music.ui.components.themed.Header
import it.decoder.music.ui.components.themed.NonQueuedMediaItemMenu
import it.decoder.music.ui.components.themed.TextPlaceholder
import it.decoder.music.ui.items.AlbumItem
import it.decoder.music.ui.items.AlbumItemPlaceholder
import it.decoder.music.ui.items.ArtistItem
import it.decoder.music.ui.items.ArtistItemPlaceholder
import it.decoder.music.ui.items.PlaylistItem
import it.decoder.music.ui.items.PlaylistItemPlaceholder
import it.decoder.music.ui.items.SongItem
import it.decoder.music.ui.items.SongItemPlaceholder
import it.decoder.music.ui.screens.Route
import it.decoder.music.ui.styling.Dimensions
import it.decoder.music.ui.styling.LocalAppearance
import it.decoder.music.utils.asMediaItem
import it.decoder.music.utils.center
import it.decoder.music.utils.forcePlay
import it.decoder.music.utils.isLandscape
import it.decoder.music.utils.rememberSnapLayoutInfoProvider
import it.decoder.music.utils.secondary
import it.decoder.music.utils.semiBold
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalFoundationApi::class)
@Route
@Composable
fun QuickPicks(
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onSearchClick: () -> Unit
) {
    val (colorPalette, typography) = LocalAppearance.current
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current
    val windowInsets = LocalPlayerAwareWindowInsets.current

    var trending by persist<Song?>("home/trending")

    var relatedPageResult by persist<Result<Innertube.RelatedPage?>?>(tag = "home/relatedPageResult")

    LaunchedEffect(DataPreferences.quickPicksSource) {
        suspend fun handleSong(song: Song?) {
            if (relatedPageResult == null || trending?.id != song?.id) relatedPageResult =
                Innertube.relatedPage(
                    body = NextBody(videoId = (song?.id ?: "J7p4bzqLvCw"))
                )
            trending = song
        }

        when (DataPreferences.quickPicksSource) {
            DataPreferences.QuickPicksSource.Trending ->
                Database
                    .trending()
                    .distinctUntilChanged()
                    .collect { handleSong(it.firstOrNull()) }

            DataPreferences.QuickPicksSource.LastInteraction ->
                Database
                    .events()
                    .distinctUntilChanged()
                    .collect { handleSong(it.firstOrNull()?.song) }
        }
    }

    val scrollState = rememberScrollState()
    val quickPicksLazyGridState = rememberLazyGridState()

    val endPaddingValues = windowInsets.only(WindowInsetsSides.End).asPaddingValues()

    val sectionTextModifier = Modifier
        .padding(horizontal = 16.dp)
        .padding(top = 24.dp, bottom = 8.dp)
        .padding(endPaddingValues)

    BoxWithConstraints {
        val quickPicksLazyGridItemWidthFactor =
            if (isLandscape && maxWidth * 0.475f >= 320.dp) 0.475f else 0.75f

        val snapLayoutInfoProvider = rememberSnapLayoutInfoProvider(
            lazyGridState = quickPicksLazyGridState,
            positionInLayout = { layoutSize, itemSize ->
                (layoutSize * quickPicksLazyGridItemWidthFactor / 2f - itemSize / 2f)
            }
        )

        val itemInHorizontalGridWidth = maxWidth * quickPicksLazyGridItemWidthFactor

        Column(
            modifier = Modifier
                .background(colorPalette.background0)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    windowInsets
                        .only(WindowInsetsSides.Vertical)
                        .asPaddingValues()
                )
        ) {
            Header(
                title = stringResource(R.string.quick_picks),
                modifier = Modifier.padding(endPaddingValues)
            )

            relatedPageResult?.getOrNull()?.let { related ->
                LazyHorizontalGrid(
                    state = quickPicksLazyGridState,
                    rows = GridCells.Fixed(4),
                    flingBehavior = rememberSnapFlingBehavior(snapLayoutInfoProvider),
                    contentPadding = endPaddingValues,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((Dimensions.thumbnails.song + Dimensions.items.verticalPadding * 2) * 4)
                ) {
                    trending?.let { song ->
                        item {
                            SongItem(
                                modifier = Modifier
                                    .combinedClickable(
                                        onLongClick = {
                                            menuState.display {
                                                NonQueuedMediaItemMenu(
                                                    onDismiss = menuState::hide,
                                                    mediaItem = song.asMediaItem,
                                                    onRemoveFromQuickPicks = {
                                                        query {
                                                            Database.clearEventsFor(song.id)
                                                        }
                                                    }
                                                )
                                            }
                                        },
                                        onClick = {
                                            val mediaItem = song.asMediaItem
                                            binder?.stopRadio()
                                            binder?.player?.forcePlay(mediaItem)
                                            binder?.setupRadio(
                                                NavigationEndpoint.Endpoint.Watch(videoId = mediaItem.mediaId)
                                            )
                                        }
                                    )
                                    .animateItemPlacement()
                                    .width(itemInHorizontalGridWidth),
                                song = song,
                                thumbnailSize = Dimensions.thumbnails.song
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.star),
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(colorPalette.accent),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    items(
                        items = related.songs?.dropLast(if (trending == null) 0 else 1)
                            ?: emptyList(),
                        key = Innertube.SongItem::key
                    ) { song ->
                        SongItem(
                            song = song,
                            thumbnailSize = Dimensions.thumbnails.song,
                            modifier = Modifier
                                .combinedClickable(
                                    onLongClick = {
                                        menuState.display {
                                            NonQueuedMediaItemMenu(
                                                onDismiss = menuState::hide,
                                                mediaItem = song.asMediaItem
                                            )
                                        }
                                    },
                                    onClick = {
                                        val mediaItem = song.asMediaItem
                                        binder?.stopRadio()
                                        binder?.player?.forcePlay(mediaItem)
                                        binder?.setupRadio(
                                            NavigationEndpoint.Endpoint.Watch(videoId = mediaItem.mediaId)
                                        )
                                    }
                                )
                                .animateItemPlacement()
                                .width(itemInHorizontalGridWidth)
                        )
                    }
                }

                related.albums?.let { albums ->
                    BasicText(
                        text = stringResource(R.string.related_albums),
                        style = typography.m.semiBold,
                        modifier = sectionTextModifier
                    )

                    LazyRow(contentPadding = endPaddingValues) {
                        items(
                            items = albums,
                            key = Innertube.AlbumItem::key
                        ) { album ->
                            AlbumItem(
                                album = album,
                                thumbnailSize = Dimensions.thumbnails.album,
                                alternative = true,
                                modifier = Modifier.clickable(onClick = { onAlbumClick(album.key) })
                            )
                        }
                    }
                }

                related.artists?.let { artists ->
                    BasicText(
                        text = stringResource(R.string.similar_artists),
                        style = typography.m.semiBold,
                        modifier = sectionTextModifier
                    )

                    LazyRow(contentPadding = endPaddingValues) {
                        items(
                            items = artists,
                            key = Innertube.ArtistItem::key
                        ) { artist ->
                            ArtistItem(
                                artist = artist,
                                thumbnailSize = Dimensions.thumbnails.artist,
                                alternative = true,
                                modifier = Modifier.clickable(onClick = { onArtistClick(artist.key) })
                            )
                        }
                    }
                }

                related.playlists?.let { playlists ->
                    BasicText(
                        text = stringResource(R.string.recommended_playlists),
                        style = typography.m.semiBold,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(top = 24.dp, bottom = 8.dp)
                    )

                    LazyRow(contentPadding = endPaddingValues) {
                        items(
                            items = playlists,
                            key = Innertube.PlaylistItem::key
                        ) { playlist ->
                            PlaylistItem(
                                playlist = playlist,
                                thumbnailSize = Dimensions.thumbnails.playlist,
                                alternative = true,
                                modifier = Modifier.clickable(onClick = { onPlaylistClick(playlist.key) })
                            )
                        }
                    }
                }

                Unit
            } ?: relatedPageResult?.exceptionOrNull()?.let {
                BasicText(
                    text = stringResource(R.string.error_message),
                    style = typography.s.secondary.center,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(all = 16.dp)
                )
            } ?: ShimmerHost {
                repeat(4) {
                    SongItemPlaceholder(thumbnailSize = Dimensions.thumbnails.song)
                }

                TextPlaceholder(modifier = sectionTextModifier)

                Row {
                    repeat(2) {
                        AlbumItemPlaceholder(
                            thumbnailSize = Dimensions.thumbnails.album,
                            alternative = true
                        )
                    }
                }

                TextPlaceholder(modifier = sectionTextModifier)

                Row {
                    repeat(2) {
                        ArtistItemPlaceholder(
                            thumbnailSize = Dimensions.thumbnails.album,
                            alternative = true
                        )
                    }
                }

                TextPlaceholder(modifier = sectionTextModifier)

                Row {
                    repeat(2) {
                        PlaylistItemPlaceholder(
                            thumbnailSize = Dimensions.thumbnails.album,
                            alternative = true
                        )
                    }
                }
            }
        }

        FloatingActionsContainerWithScrollToTop(
            scrollState = scrollState,
            iconId = R.drawable.search,
            onClick = onSearchClick
        )
    }
}
