package it.decoder.innertube.requests

import io.ktor.client.call.body
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import it.decoder.extensions.runCatchingCancellable
import it.decoder.innertube.Innertube
import it.decoder.innertube.models.BrowseResponse
import it.decoder.innertube.models.ContinuationResponse
import it.decoder.innertube.models.MusicCarouselShelfRenderer
import it.decoder.innertube.models.MusicShelfRenderer
import it.decoder.innertube.models.bodies.BrowseBody
import it.decoder.innertube.models.bodies.ContinuationBody
import it.decoder.innertube.utils.from

suspend fun Innertube.playlistPage(body: BrowseBody) = runCatchingCancellable {
    val response = client.post(BROWSE) {
        setBody(body)
        body.context.apply()
    }.body<BrowseResponse>()

    val musicDetailHeaderRenderer = response
        .header
        ?.musicDetailHeaderRenderer

    val sectionListRendererContents = response
        .contents
        ?.singleColumnBrowseResultsRenderer
        ?.tabs
        ?.firstOrNull()
        ?.tabRenderer
        ?.content
        ?.sectionListRenderer
        ?.contents

    val musicShelfRenderer = sectionListRendererContents
        ?.firstOrNull()
        ?.musicShelfRenderer

    val musicCarouselShelfRenderer = sectionListRendererContents
        ?.getOrNull(1)
        ?.musicCarouselShelfRenderer

    Innertube.PlaylistOrAlbumPage(
        title = musicDetailHeaderRenderer
            ?.title
            ?.text,
        thumbnail = musicDetailHeaderRenderer
            ?.thumbnail
            ?.musicThumbnailRenderer
            ?.thumbnail
            ?.thumbnails
            ?.firstOrNull(),
        authors = musicDetailHeaderRenderer
            ?.subtitle
            ?.splitBySeparator()
            ?.getOrNull(1)
            ?.map(Innertube::Info),
        year = musicDetailHeaderRenderer
            ?.subtitle
            ?.splitBySeparator()
            ?.getOrNull(2)
            ?.firstOrNull()
            ?.text,
        url = response
            .microformat
            ?.microformatDataRenderer
            ?.urlCanonical,
        songsPage = musicShelfRenderer
            ?.toSongsPage(),
        otherVersions = musicCarouselShelfRenderer
            ?.contents
            ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
            ?.mapNotNull(Innertube.AlbumItem::from)
    )
}

suspend fun Innertube.playlistPage(body: ContinuationBody) = runCatchingCancellable {
    val response = client.post(BROWSE) {
        setBody(body)
        parameter("continuation", body.continuation)
        parameter("ctoken", body.continuation)
        parameter("type", "next")
        body.context.apply()
    }.body<ContinuationResponse>()

    response
        .continuationContents
        ?.musicShelfContinuation
        ?.toSongsPage()
}

private fun MusicShelfRenderer?.toSongsPage() = Innertube.ItemsPage(
    items = this
        ?.contents
        ?.mapNotNull(MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
        ?.mapNotNull(Innertube.SongItem::from),
    continuation = this
        ?.continuations
        ?.firstOrNull()
        ?.nextContinuationData
        ?.continuation
)
