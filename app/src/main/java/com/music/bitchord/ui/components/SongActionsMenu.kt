package com.music.bitchord.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.webdav.WebDavUploads
import com.music.bitchord.download.DownloadState
import com.music.bitchord.download.Downloads
import com.music.bitchord.playback.SleepTimer
import com.music.bitchord.ui.icons.SfIcons
import com.music.bitchord.ui.player.ThinSlider
import expo.modules.glassmenuandroid.records.GlassMenuItemRecord
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun buildSongMenuItems(
    song: Song,
    signedIn: Boolean,
    likeStatus: LikeStatus,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onStartRadio: () -> Unit,
    onDownload: () -> Unit,
    onUploadToWebDav: (() -> Unit)? = null,
    onToggleLike: () -> Unit,
    onToggleDislike: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onShowSongInfo: () -> Unit,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onRemoveFromCache: (() -> Unit)? = null,
    onRemoveFromHistory: (() -> Unit)? = null,
    showSleepTimer: Boolean = false,
    onOpenSleepTimer: () -> Unit = {},
    onRollbackToOriginal: (() -> Unit)? = null,
    onUpgradeQuality: (() -> Unit)? = null,
    upgradeQualityInProgress: Boolean = false,
    onToggleAudioVersion: (() -> Unit)? = null,
    isAudioVersion: Boolean = false,
    audioVersionMissing: Boolean = false,
    onShare: (() -> Unit)? = null,
    onCopyLog: (() -> Unit)? = null,
    onLyricsOffset: (() -> Unit)? = null,
    onAutomixOverview: (() -> Unit)? = null,
    resolvingLinks: Boolean = false,
): List<GlassMenuItemRecord> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activeDownloads by Downloads.active.collectAsStateWithLifecycle()
    val savedDownloads by Downloads.saved.collectAsStateWithLifecycle()
    val recorded = savedDownloads[song.videoId]
    val savedFile by produceState(recorded?.let(Uri::parse), song.videoId, recorded) {
        value = Downloads.savedUri(context, song.videoId)
    }
    DisposableEffect(song.videoId) {
        onDispose { Downloads.dismissFailure(song.videoId) }
    }
    val webdavActive by WebDavUploads.active.collectAsStateWithLifecycle()

    val liked = likeStatus == LikeStatus.LIKE
    val disliked = likeStatus == LikeStatus.DISLIKE
    val isOffline = song.localUri != null
    val canRate = isOffline || signedIn
    val shareAction = onShare?.takeIf { !isOffline }

    val list = ArrayList<GlassMenuItemRecord>(20)
    var hasGroup = false
    fun addSeparatorIfNeeded() {
        if (hasGroup) {
            list.add(GlassMenuItemRecord(separator = true))
        }
        hasGroup = true
    }

    if (canRate || shareAction != null) {
        addSeparatorIfNeeded()
        if (canRate) {
            list.add(
                GlassMenuItemRecord(
                    id = "like",
                    title = stringResource(R.string.like),
                    icon = "heart",
                    checked = if (liked) true else null,
                    keepOpen = true,
                    onClick = onToggleLike,
                ),
            )
        }
        if (signedIn && !isOffline) {
            list.add(
                GlassMenuItemRecord(
                    id = "dislike",
                    title = stringResource(R.string.dislike),
                    icon = "dislike",
                    checked = if (disliked) true else null,
                    keepOpen = true,
                    onClick = onToggleDislike,
                ),
            )
        }
        shareAction?.let {
            list.add(
                GlassMenuItemRecord(
                    id = "share",
                    title = stringResource(R.string.share),
                    icon = "share",
                    onClick = it,
                ),
            )
        }
    }

    if (onRollbackToOriginal != null || onUpgradeQuality != null || onToggleAudioVersion != null) {
        addSeparatorIfNeeded()
        (onRollbackToOriginal ?: onUpgradeQuality)?.let { action ->
            list.add(
                GlassMenuItemRecord(
                    id = "quality",
                    title = stringResource(
                        if (onRollbackToOriginal != null) R.string.revert_to_original else R.string.upgrade_quality,
                    ),
                    icon = "sliders",
                    disabled = onRollbackToOriginal == null && upgradeQualityInProgress,
                    onClick = action,
                ),
            )
        }
        onToggleAudioVersion?.let { action ->
            list.add(
                GlassMenuItemRecord(
                    id = "version",
                    title = stringResource(
                        when {
                            audioVersionMissing && isAudioVersion -> R.string.no_video_version
                            audioVersionMissing -> R.string.no_audio_version
                            isAudioVersion -> R.string.convert_to_video
                            else -> R.string.convert_to_audio
                        },
                    ),
                    icon = if (isAudioVersion) "video" else "music",
                    disabled = audioVersionMissing,
                    onClick = action,
                ),
            )
        }
    }

    if (canRate) {
        addSeparatorIfNeeded()
        list.add(
            GlassMenuItemRecord(
                id = "add_to_playlist",
                title = stringResource(R.string.add_to_playlist),
                icon = "plus",
                onClick = onAddToPlaylist,
            ),
        )
        onRemoveFromPlaylist?.let {
            list.add(
                GlassMenuItemRecord(
                    id = "remove_from_playlist",
                    title = stringResource(R.string.remove_from_playlist),
                    icon = "trash",
                    destructive = true,
                    onClick = it,
                ),
            )
        }
    }

    addSeparatorIfNeeded()
    list.add(
        GlassMenuItemRecord(
            id = "radio",
            title = stringResource(R.string.start_radio),
            icon = "radio",
            onClick = onStartRadio,
        ),
    )
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

    when (val state = activeDownloads[song.videoId]) {
        is DownloadState.Queued -> list.add(
            GlassMenuItemRecord(
                id = "download_queued",
                title = stringResource(R.string.queued),
                subtitle = stringResource(R.string.cancel),
                icon = "download",
                keepOpen = true,
                onClick = { Downloads.cancel(song.videoId) },
            ),
        )
        is DownloadState.Running -> list.add(
            GlassMenuItemRecord(
                id = "download_running",
                title = stringResource(R.string.download_notification_title),
                subtitle = if (state.fraction > 0f) "${(state.fraction * 100).toInt()}%" else null,
                icon = "download",
                keepOpen = true,
                onClick = { Downloads.cancel(song.videoId) },
            ),
        )
        is DownloadState.Failed -> list.add(
            GlassMenuItemRecord(
                id = "download_failed",
                title = state.reason,
                subtitle = stringResource(R.string.retry),
                icon = "download",
                destructive = true,
                keepOpen = true,
                onClick = onDownload,
            ),
        )
        null -> if (savedFile != null) {
            list.add(
                GlassMenuItemRecord(
                    id = "download_saved",
                    title = stringResource(R.string.saved_to_downloads),
                    subtitle = stringResource(R.string.delete),
                    icon = "downloaded",
                    keepOpen = true,
                    onClick = { scope.launch { Downloads.delete(context, song.videoId) } },
                ),
            )
        } else if (!isOffline) {
            list.add(
                GlassMenuItemRecord(
                    id = "download",
                    title = stringResource(R.string.download),
                    icon = "download",
                    keepOpen = true,
                    onClick = onDownload,
                ),
            )
        }
    }

    onRemoveFromCache?.let {
        list.add(
            GlassMenuItemRecord(
                id = "remove_cache",
                title = stringResource(R.string.remove_from_cache),
                icon = "trash",
                destructive = true,
                onClick = it,
            ),
        )
    }

    if (onUploadToWebDav != null && isOffline && WebDavUploads.isUploadable(song)) {
        when (val state = webdavActive[song.videoId]) {
            is WebDavUploads.TrackState.Running -> list.add(
                GlassMenuItemRecord(
                    id = "webdav_running",
                    title = stringResource(R.string.upload_to_webdav),
                    subtitle = "${(state.fraction * 100).toInt()}%",
                    icon = "upload",
                    disabled = true,
                ),
            )
            is WebDavUploads.TrackState.Failed -> list.add(
                GlassMenuItemRecord(
                    id = "webdav_failed",
                    title = state.reason,
                    subtitle = stringResource(R.string.try_again),
                    icon = "upload",
                    destructive = true,
                    onClick = onUploadToWebDav,
                ),
            )
            else -> list.add(
                GlassMenuItemRecord(
                    id = "webdav",
                    title = stringResource(R.string.upload_to_webdav),
                    icon = "upload",
                    onClick = onUploadToWebDav,
                ),
            )
        }
    }

    onRemoveFromHistory?.let {
        list.add(
            GlassMenuItemRecord(
                id = "remove_history",
                title = stringResource(R.string.remove_from_history),
                icon = "history",
                destructive = true,
                onClick = it,
            ),
        )
    }

    addSeparatorIfNeeded()
    when (val id = song.albumId) {
        null -> if (resolvingLinks) {
            list.add(
                GlassMenuItemRecord(
                    id = "album_loading",
                    title = stringResource(R.string.open_album),
                    icon = "album",
                    disabled = true,
                ),
            )
        }
        else -> list.add(
            GlassMenuItemRecord(
                id = "open_album",
                title = stringResource(R.string.open_album),
                subtitle = song.albumName,
                icon = "album",
                onClick = { onOpenAlbum(id) },
            ),
        )
    }
    when (val id = song.artistId) {
        null -> if (resolvingLinks) {
            list.add(
                GlassMenuItemRecord(
                    id = "artist_loading",
                    title = stringResource(R.string.open_artist),
                    icon = "artist",
                    disabled = true,
                ),
            )
        }
        else -> list.add(
            GlassMenuItemRecord(
                id = "open_artist",
                title = stringResource(R.string.open_artist),
                subtitle = song.artist.takeIf { it.isNotBlank() },
                icon = "artist",
                onClick = { onOpenArtist(id) },
            ),
        )
    }
    list.add(
        GlassMenuItemRecord(
            id = "song_info",
            title = stringResource(R.string.song_info),
            icon = "info",
            onClick = onShowSongInfo,
        ),
    )

    if (showSleepTimer || onLyricsOffset != null || onAutomixOverview != null || onCopyLog != null) {
        addSeparatorIfNeeded()
        if (showSleepTimer) {
            list.add(
                GlassMenuItemRecord(
                    id = "sleep_timer",
                    title = stringResource(R.string.sleep_timer),
                    subtitle = sleepTimerStatus(),
                    icon = "clock",
                    onClick = onOpenSleepTimer,
                ),
            )
        }
        onAutomixOverview?.let {
            list.add(
                GlassMenuItemRecord(
                    id = "automix",
                    title = stringResource(R.string.automix_overview),
                    icon = "sliders",
                    onClick = it,
                ),
            )
        }
        onLyricsOffset?.let {
            list.add(
                GlassMenuItemRecord(
                    id = "lyrics_offset",
                    title = stringResource(R.string.lyrics_offset),
                    icon = "sliders",
                    onClick = it,
                ),
            )
        }
        onCopyLog?.let {
            list.add(
                GlassMenuItemRecord(
                    id = "copy_log",
                    title = stringResource(R.string.copy_log),
                    icon = "bug",
                    onClick = it,
                ),
            )
        }
    }

    return list
}

@Composable
fun SleepTimerSheetContent(onDismiss: () -> Unit) {
    val chosen by SleepTimer.minutes.collectAsStateWithLifecycle()
    val afterTrack by SleepTimer.afterTrack.collectAsStateWithLifecycle()
    val countdown = sleepTimerCountdown()
    val custom = chosen?.takeIf { it !in SleepTimer.PRESETS }
    var pickingCustom by remember { mutableStateOf(false) }

    if (pickingCustom) {
        CustomSleepTimerPage(
            initialMinutes = custom ?: 90,
            onBack = { pickingCustom = false },
            onStart = { minutes ->
                SleepTimer.start(minutes)
                onDismiss()
            },
        )
        return
    }

    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
    ) {
        SleepTimerHeader(
            title = stringResource(R.string.sleep_timer),
            subtitle = when {
                countdown != null -> stringResource(R.string.pause_countdown, countdown)
                afterTrack -> stringResource(R.string.pause_after_song)
                else -> stringResource(R.string.pause_after_a_while)
            },
            onBack = onDismiss,
        )
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        SleepOption(
            label = stringResource(R.string.after_this_song),
            selected = afterTrack,
        ) {
            SleepTimer.startAfterTrack()
            onDismiss()
        }
        SleepTimer.PRESETS.forEach { minutes ->
            SleepOption(
                label = if (minutes == 60) {
                    stringResource(R.string.one_hour)
                } else {
                    pluralStringResource(R.plurals.minute_count, minutes, minutes)
                },
                selected = minutes == chosen,
            ) {
                SleepTimer.start(minutes)
                onDismiss()
            }
        }
        SleepOption(
            label = custom?.let {
                stringResource(R.string.sleep_timer_custom) + " · " + sleepDuration(it)
            } ?: stringResource(R.string.sleep_timer_custom),
            selected = custom != null,
        ) { pickingCustom = true }
        if (chosen != null || afterTrack) {
            ActionRow(SfIcons.Close, stringResource(R.string.turn_off_timer)) {
                SleepTimer.cancel()
                onDismiss()
            }
        }
    }
}

@Composable
private fun SleepTimerHeader(title: String, subtitle: String, onBack: () -> Unit) {
    val labelColor = MaterialTheme.colorScheme.onSurface
    val secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 22.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                SfIcons.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = labelColor,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 17.sp,
                    lineHeight = 21.sp,
                    letterSpacing = (-0.3).sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = labelColor,
            )
            Text(
                text = subtitle,
                fontSize = 14.sp,
                lineHeight = 17.sp,
                color = secondaryColor,
            )
        }
    }
}

@Composable
private fun CustomSleepTimerPage(
    initialMinutes: Int,
    onBack: () -> Unit,
    onStart: (Int) -> Unit,
) {
    var minutes by remember { mutableIntStateOf(initialMinutes) }
    val foreground = MaterialTheme.colorScheme.onBackground

    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
    ) {
        SleepTimerHeader(
            title = stringResource(R.string.sleep_timer_custom),
            subtitle = stringResource(R.string.pause_after_a_while),
            onBack = onBack,
        )
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        Text(
            text = sleepDuration(minutes),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
            color = foreground,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 8.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StepButton(
                label = "−",
                contentDescription = stringResource(R.string.less),
                enabled = minutes > SleepTimer.STEP_MINUTES,
            ) { minutes -= SleepTimer.STEP_MINUTES }
            ThinSlider(
                value = SleepTimer.fractionOf(minutes),
                onValueChange = { minutes = SleepTimer.minutesAt(it) },
                idleHeight = 6.dp,
                activeHeight = 10.dp,
                activeColor = foreground.copy(alpha = 0.92f),
                inactiveColor = foreground.copy(alpha = 0.26f),
                modifier = Modifier.weight(1f),
            )
            StepButton(
                label = "+",
                contentDescription = stringResource(R.string.more),
                enabled = minutes < SleepTimer.MAX_MINUTES,
            ) { minutes += SleepTimer.STEP_MINUTES }
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ActionRow(SfIcons.SleepTimer, stringResource(R.string.start_sleep_timer)) {
            onStart(minutes)
        }
    }
}

@Composable
private fun StepButton(
    label: String,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.semantics { this.contentDescription = contentDescription },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            color = if (enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f),
        )
    }
}

@Composable
private fun sleepDuration(minutes: Int): String = if (minutes >= 60) {
    stringResource(R.string.hours_minutes_short, minutes / 60, minutes % 60)
} else {
    stringResource(R.string.minutes_short, minutes)
}

@Composable
private fun SleepOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun sleepTimerStatus(): String? {
    val afterTrack by SleepTimer.afterTrack.collectAsStateWithLifecycle()
    return sleepTimerCountdown() ?: stringResource(R.string.after_this_song).takeIf { afterTrack }
}

@Composable
private fun sleepTimerCountdown(): String? {
    val deadline by SleepTimer.deadline.collectAsStateWithLifecycle()
    val remaining by produceState<Long?>(initialValue = SleepTimer.remainingMs(), deadline) {
        while (deadline != null) {
            value = SleepTimer.remainingMs()
            delay(1_000)
        }
        value = null
    }
    return remaining?.let {
        val seconds = it / 1000
        "%d:%02d".format(Locale.ROOT, seconds / 60, seconds % 60)
    }
}

@Composable
internal fun ActionRow(
    icon: ImageVector,
    label: String,
    value: String? = null,
    tint: Color? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val foreground = MaterialTheme.colorScheme.onBackground
    val alpha = if (enabled) 1f else 0.4f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = (tint ?: foreground).copy(alpha = alpha),
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(18.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = foreground.copy(alpha = alpha),
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = accent.copy(alpha = alpha),
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun SheetTrackHeader(
    song: Song,
    modifier: Modifier = Modifier,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = song.artworkAt(ROW_ART_PX),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .trackArtSize(52.dp, song.isVideo)
                .clip(RoundedCornerShape(8.dp))
                .thumbnailBorder(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            ExplicitSongTitle(
                song = song,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = subtitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun SheetHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 4.dp),
    )
}
