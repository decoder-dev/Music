package it.decoder.innertube.requests

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import it.decoder.extensions.runCatchingCancellable
import it.decoder.innertube.Innertube
import it.decoder.innertube.models.ContinuationResponse
import it.decoder.innertube.models.NextResponse
import it.decoder.innertube.models.bodies.ContinuationBody
import it.decoder.innertube.models.bodies.NextBody
import it.decoder.innertube.utils.from

suspend fun Innertube.nextPage(body: NextBody): Result<Innertube.NextPage>? =
    runCatchingCancellable {
        val response = client.post(NEXT) {
            setBody(body)
            @Suppress("all")
            mask("contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer.watchNextTabbedResultsRenderer.tabs.tabRenderer.content.musicQueueRenderer.content.playlistPanelRenderer(continuations,contents(automixPreviewVideoRenderer,$PLAYLIST_PANEL_VIDEO_RENDERER_MASK))")
        }.body<NextResponse>()

        val tabs = response
            .contents
            ?.singleColumnMusicWatchNextResultsRenderer
            ?.tabbedRenderer
            ?.watchNextTabbedResultsRenderer
            ?.tabs

        val playlistPanelRenderer = tabs
            ?.getOrNull(0)
            ?.tabRenderer
            ?.content
            ?.musicQueueRenderer
            ?.content
            ?.playlistPanelRenderer

        if (body.playlistId == null) {
            val endpoint = playlistPanelRenderer
                ?.contents
                ?.lastOrNull()
                ?.automixPreviewVideoRenderer
                ?.content
                ?.automixPlaylistVideoRenderer
                ?.navigationEndpoint
                ?.watchPlaylistEndpoint

            if (endpoint != null) return nextPage(
                body.copy(
                    playlistId = endpoint.playlistId,
                    params = endpoint.params
                )
            )
        }

        Innertube.NextPage(
            playlistId = body.playlistId,
            playlistSetVideoId = body.playlistSetVideoId,
            params = body.params,
            itemsPage = playlistPanelRenderer
                ?.toSongsPage()
        )
    }

suspend fun Innertube.nextPage(body: ContinuationBody) = runCatchingCancellable {
    val response = client.post(NEXT) {
        setBody(body)
        @Suppress("all")
        mask("continuationContents.playlistPanelContinuation(continuations,contents.$PLAYLIST_PANEL_VIDEO_RENDERER_MASK)")
    }.body<ContinuationResponse>()

    response
        .continuationContents
        ?.playlistPanelContinuation
        ?.toSongsPage()
}

private fun NextResponse.MusicQueueRenderer.Content.PlaylistPanelRenderer?.toSongsPage() =
    Innertube.ItemsPage(
        items = this
            ?.contents
            ?.mapNotNull(
                NextResponse.MusicQueueRenderer.Content.PlaylistPanelRenderer.Content
                ::playlistPanelVideoRenderer
            )?.mapNotNull(Innertube.SongItem::from),
        continuation = this
            ?.continuations
            ?.firstOrNull()
            ?.nextContinuationData
            ?.continuation
    )
