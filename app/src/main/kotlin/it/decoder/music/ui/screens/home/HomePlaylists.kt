package it.decoder.music.ui.screens.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.decoder.compose.persist.persist
import it.decoder.compose.persist.persistList
import it.decoder.piped.Piped
import it.decoder.piped.models.Session
import it.decoder.music.Database
import it.decoder.music.LocalPlayerAwareWindowInsets
import it.decoder.music.R
import it.decoder.music.enums.BuiltInPlaylist
import it.decoder.music.enums.PlaylistSortBy
import it.decoder.music.enums.SortOrder
import it.decoder.music.models.PipedSession
import it.decoder.music.models.Playlist
import it.decoder.music.models.PlaylistPreview
import it.decoder.music.preferences.DataPreferences
import it.decoder.music.preferences.OrderPreferences
import it.decoder.music.query
import it.decoder.music.ui.components.themed.FloatingActionsContainerWithScrollToTop
import it.decoder.music.ui.components.themed.Header
import it.decoder.music.ui.components.themed.HeaderIconButton
import it.decoder.music.ui.components.themed.SecondaryTextButton
import it.decoder.music.ui.components.themed.TextFieldDialog
import it.decoder.music.ui.items.PlaylistItem
import it.decoder.music.ui.screens.Route
import it.decoder.music.ui.screens.settings.SettingsEntryGroupText
import it.decoder.music.ui.screens.settings.SettingsGroupSpacer
import it.decoder.music.ui.styling.Dimensions
import it.decoder.music.ui.styling.LocalAppearance
import kotlinx.coroutines.async
import it.decoder.piped.models.PlaylistPreview as PipedPlaylistPreview

@OptIn(ExperimentalFoundationApi::class)
@Route
@Composable
fun HomePlaylists(
    onBuiltInPlaylist: (BuiltInPlaylist) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onPipedPlaylistClick: (Session, PipedPlaylistPreview) -> Unit,
    onSearchClick: () -> Unit
) = with(OrderPreferences) {
    val (colorPalette) = LocalAppearance.current

    var isCreatingANewPlaylist by rememberSaveable { mutableStateOf(false) }

    if (isCreatingANewPlaylist) TextFieldDialog(
        hintText = stringResource(R.string.enter_playlist_name_prompt),
        onDismiss = { isCreatingANewPlaylist = false },
        onDone = { text ->
            query {
                Database.insert(Playlist(name = text))
            }
        }
    )
    var items by persistList<PlaylistPreview>("home/playlists")
    var pipedSessions by persist<Map<PipedSession, List<PipedPlaylistPreview>?>>("home/piped")

    LaunchedEffect(playlistSortBy, playlistSortOrder) {
        Database.playlistPreviews(playlistSortBy, playlistSortOrder).collect { items = it }
    }

    LaunchedEffect(Unit) {
        Database.pipedSessions().collect { sessions ->
            pipedSessions = sessions.associateWith { session ->
                async {
                    Piped.playlist.list(session = session.toApiSession())?.getOrNull()
                }
            }.mapValues { (_, value) -> value.await() }
        }
    }

    val sortOrderIconRotation by animateFloatAsState(
        targetValue = if (playlistSortOrder == SortOrder.Ascending) 0f else 180f,
        animationSpec = tween(durationMillis = 400, easing = LinearEasing),
        label = ""
    )

    val lazyGridState = rememberLazyGridState()

    Box {
        LazyVerticalGrid(
            state = lazyGridState,
            columns = GridCells.Adaptive(Dimensions.thumbnails.song * 2 + Dimensions.items.verticalPadding * 2),
            contentPadding = LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
                .asPaddingValues(),
            verticalArrangement = Arrangement.spacedBy(Dimensions.items.verticalPadding * 2),
            horizontalArrangement = Arrangement.spacedBy(
                space = Dimensions.items.verticalPadding * 2,
                alignment = Alignment.CenterHorizontally
            ),
            modifier = Modifier
                .fillMaxSize()
                .background(colorPalette.background0)
        ) {
            item(key = "header", contentType = 0, span = { GridItemSpan(maxLineSpan) }) {
                Header(title = stringResource(R.string.playlists)) {
                    SecondaryTextButton(
                        text = stringResource(R.string.new_playlist),
                        onClick = { isCreatingANewPlaylist = true }
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    HeaderIconButton(
                        icon = R.drawable.medical,
                        color = if (playlistSortBy == PlaylistSortBy.SongCount) colorPalette.text
                        else colorPalette.textDisabled,
                        onClick = { playlistSortBy = PlaylistSortBy.SongCount }
                    )

                    HeaderIconButton(
                        icon = R.drawable.text,
                        color = if (playlistSortBy == PlaylistSortBy.Name) colorPalette.text
                        else colorPalette.textDisabled,
                        onClick = { playlistSortBy = PlaylistSortBy.Name }
                    )

                    HeaderIconButton(
                        icon = R.drawable.time,
                        color = if (playlistSortBy == PlaylistSortBy.DateAdded) colorPalette.text
                        else colorPalette.textDisabled,
                        onClick = { playlistSortBy = PlaylistSortBy.DateAdded }
                    )

                    Spacer(modifier = Modifier.width(2.dp))

                    HeaderIconButton(
                        icon = R.drawable.arrow_up,
                        color = colorPalette.text,
                        onClick = { playlistSortOrder = !playlistSortOrder },
                        modifier = Modifier.graphicsLayer { rotationZ = sortOrderIconRotation }
                    )
                }
            }

            item(key = "favorites") {
                PlaylistItem(
                    icon = R.drawable.heart,
                    colorTint = colorPalette.red,
                    name = stringResource(R.string.favorites),
                    songCount = null,
                    thumbnailSize = Dimensions.thumbnails.playlist,
                    alternative = true,
                    modifier = Modifier
                        .clickable(onClick = { onBuiltInPlaylist(BuiltInPlaylist.Favorites) })
                        .animateItemPlacement()
                )
            }

            item(key = "offline") {
                PlaylistItem(
                    icon = R.drawable.airplane,
                    colorTint = colorPalette.blue,
                    name = stringResource(R.string.offline),
                    songCount = null,
                    thumbnailSize = Dimensions.thumbnails.playlist,
                    alternative = true,
                    modifier = Modifier
                        .clickable(onClick = { onBuiltInPlaylist(BuiltInPlaylist.Offline) })
                        .animateItemPlacement()
                )
            }

            item(key = "top") {
                PlaylistItem(
                    icon = R.drawable.trending,
                    colorTint = colorPalette.red,
                    name = stringResource(
                        R.string.format_my_top_playlist,
                        DataPreferences.topListLength
                    ),
                    songCount = null,
                    thumbnailSize = Dimensions.thumbnails.playlist,
                    alternative = true,
                    modifier = Modifier
                        .clickable(onClick = { onBuiltInPlaylist(BuiltInPlaylist.Top) })
                        .animateItemPlacement()
                )
            }

            items(
                items = items,
                key = { it.playlist.id }
            ) { playlistPreview ->
                PlaylistItem(
                    playlist = playlistPreview,
                    thumbnailSize = Dimensions.thumbnails.playlist,
                    alternative = true,
                    modifier = Modifier
                        .clickable(onClick = { onPlaylistClick(playlistPreview.playlist) })
                        .animateItemPlacement()
                )
            }

            pipedSessions
                ?.ifEmpty { null }
                ?.filter { it.value?.isNotEmpty() == true }
                ?.forEach { (session, playlists) ->
                    item(
                        key = "piped-header-${session.username}",
                        contentType = 0,
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        SettingsGroupSpacer()
                        SettingsEntryGroupText(title = session.username)
                    }

                    playlists?.let {
                        items(
                            items = playlists,
                            key = { "piped-${session.username}-${it.id}" }
                        ) { playlist ->
                            PlaylistItem(
                                name = playlist.name,
                                songCount = playlist.videoCount,
                                channelName = null,
                                thumbnailUrl = playlist.thumbnailUrl.toString(),
                                thumbnailSize = Dimensions.thumbnails.playlist,
                                alternative = true,
                                modifier = Modifier
                                    .clickable(onClick = {
                                        onPipedPlaylistClick(
                                            session.toApiSession(),
                                            playlist
                                        )
                                    })
                                    .animateItemPlacement()
                            )
                        }
                    }
                }
        }

        FloatingActionsContainerWithScrollToTop(
            lazyGridState = lazyGridState,
            iconId = R.drawable.search,
            onClick = onSearchClick
        )
    }
}
