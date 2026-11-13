package com.music.bitchord.ui.components

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UserPlaylist
import com.music.bitchord.download.DownloadState
import com.music.bitchord.download.Downloads
import expo.modules.glassmenuandroid.records.GlassMenuItemRecord

data class BrowseTarget(
    val browseId: String?,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String? = null,
    val type: BrowseType = BrowseType.OTHER,
    val songs: List<Song> = emptyList(),
    val playlist: UserPlaylist? = null,
    val fromCard: Boolean = true,
    val downloadId: String? = null,
)

@Composable
fun buildBrowseMenuItems(
    target: BrowseTarget,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onPlay: (() -> Unit)? = null,
    onShuffle: (() -> Unit)? = null,
    onOpen: (() -> Unit)? = null,
    onDownloadAll: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    isPinned: Boolean = false,
    onTogglePin: (() -> Unit)? = null,
    onRename: ((String) -> Unit)? = null,
    onReorder: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onDeleteDownload: (() -> Unit)? = null,
    onRenameInSheet: (() -> Unit)? = null,
): List<GlassMenuItemRecord> {
    var confirmingDelete by remember(target) { mutableStateOf(false) }
    var confirmingDeleteDownload by remember(target) { mutableStateOf(false) }

    val list = ArrayList<GlassMenuItemRecord>(12)

    onPlay?.let {
        list.add(
            GlassMenuItemRecord(
                id = "play",
                title = stringResource(R.string.play),
                icon = "play",
                onClick = it,
            ),
        )
    }
    onShuffle?.let {
        list.add(
            GlassMenuItemRecord(
                id = "shuffle",
                title = stringResource(R.string.shuffle),
                icon = "shuffle",
                onClick = it,
            ),
        )
    }
    list.add(
        GlassMenuItemRecord(
            id = "play_next",
            title = stringResource(R.string.play_next),
            icon = "play",
            onClick = onPlayNext,
        ),
    )
    list.add(
        GlassMenuItemRecord(
            id = "add_to_queue",
            title = stringResource(R.string.add_to_queue),
            icon = "list",
            onClick = onAddToQueue,
        ),
    )

    onDownloadAll?.let { download ->
        val active by Downloads.active.collectAsStateWithLifecycle()
        val requested by Downloads.requested.collectAsStateWithLifecycle()
        val saved by Downloads.saved.collectAsStateWithLifecycle()
        val waiting = target.browseId?.let { requested[it] }.orEmpty().any { id ->
            when (active[id]) {
                is DownloadState.Queued, is DownloadState.Running -> true
                else -> false
            }
        }
        val ids = remember(target.songs) { target.songs.mapTo(HashSet()) { it.videoId } }
        val downloaded = !waiting && ids.isNotEmpty() && ids.all { it in saved }
        list.add(
            GlassMenuItemRecord(
                id = "download_all",
                title = stringResource(R.string.download_all),
                subtitle = when {
                    waiting -> stringResource(R.string.downloading)
                    downloaded -> stringResource(R.string.downloaded)
                    else -> null
                },
                icon = when {
                    waiting -> "clock"
                    downloaded -> "downloaded"
                    else -> "download"
                },
                onClick = download,
            ),
        )
    }

    onOpen?.let {
        list.add(
            GlassMenuItemRecord(
                id = "open",
                title = target.type.localizedOpenLabel(),
                icon = when (target.type) {
                    BrowseType.ALBUM -> "album"
                    BrowseType.ARTIST -> "artist"
                    else -> "folder"
                },
                onClick = it,
            ),
        )
    }
    onShare?.let {
        list.add(
            GlassMenuItemRecord(
                id = "share",
                title = stringResource(R.string.share),
                icon = "share",
                onClick = it,
            ),
        )
    }
    onTogglePin?.let {
        list.add(
            GlassMenuItemRecord(
                id = "pin",
                title = stringResource(if (isPinned) R.string.unpin else R.string.pin),
                icon = "pin",
                checked = if (isPinned) true else null,
                onClick = it,
            ),
        )
    }
    if (onRename != null && onRenameInSheet != null) {
        list.add(
            GlassMenuItemRecord(
                id = "rename",
                title = stringResource(R.string.rename),
                icon = "pencil",
                onClick = onRenameInSheet,
            ),
        )
    }
    onReorder?.let {
        list.add(
            GlassMenuItemRecord(
                id = "reorder",
                title = stringResource(R.string.reorder_songs),
                icon = "sort",
                onClick = it,
            ),
        )
    }
    if (onDelete != null) {
        if (confirmingDelete) {
            list.add(
                GlassMenuItemRecord(
                    id = "delete_confirm",
                    title = stringResource(R.string.delete_playlist_confirmation, target.title),
                    icon = "trash",
                    destructive = true,
                    onClick = onDelete,
                ),
            )
        } else {
            list.add(
                GlassMenuItemRecord(
                    id = "delete",
                    title = stringResource(R.string.delete_playlist),
                    icon = "trash",
                    destructive = true,
                    keepOpen = true,
                    onClick = { confirmingDelete = true },
                ),
            )
        }
    }
    if (onDeleteDownload != null) {
        if (confirmingDeleteDownload) {
            list.add(
                GlassMenuItemRecord(
                    id = "delete_download_confirm",
                    title = stringResource(R.string.delete_download_confirmation, target.title),
                    icon = "trash",
                    destructive = true,
                    onClick = onDeleteDownload,
                ),
            )
        } else {
            list.add(
                GlassMenuItemRecord(
                    id = "delete_download",
                    title = stringResource(R.string.delete_download),
                    icon = "trash",
                    destructive = true,
                    keepOpen = true,
                    onClick = { confirmingDeleteDownload = true },
                ),
            )
        }
    }

    return list
}

@Composable
fun RenamePlaylistSheetContent(
    playlist: UserPlaylist,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
) {
    RenamePlaylistForm(
        playlist = playlist,
        onBack = onBack,
        onRename = onRename,
        modifier = Modifier.navigationBarsPadding(),
    )
}

@Composable
private fun BrowseType.localizedOpenLabel(): String = when (this) {
    BrowseType.ALBUM -> stringResource(R.string.open_album)
    BrowseType.ARTIST -> stringResource(R.string.open_artist)
    BrowseType.PLAYLIST -> stringResource(R.string.open_playlist)
    BrowseType.OTHER -> stringResource(R.string.open)
}
