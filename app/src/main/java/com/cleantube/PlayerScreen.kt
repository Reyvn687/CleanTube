package com.cleantube

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.FullscreenListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

private fun setImmersive(activity: Activity, on: Boolean) {
    val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
    if (on) {
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    } else {
        controller.show(WindowInsetsCompat.Type.systemBars())
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}

@Composable
fun PlayerScreen(video: VideoItem, vm: MainViewModel) {
    val context = LocalContext.current
    val activity = context as Activity
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    var ended by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<YouTubePlayer?>(null) }
    var fsView by remember { mutableStateOf<View?>(null) }
    var fsExit by remember { mutableStateOf<(() -> Unit)?>(null) }
    val startAt = remember(video.id) { vm.positionOf(video.id) }
    var loop by remember { mutableStateOf(false) }
    var description by remember { mutableStateOf<String?>(null) }
    var descOpen by remember { mutableStateOf(false) }

    LaunchedEffect(video.id) {
        if (vm.hasKey) {
            description = try {
                YouTubeApi.videoDescription(video.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
    }

    val playerView = remember {
        YouTubePlayerView(context).apply {
            enableAutomaticInitialization = false

            addFullscreenListener(object : FullscreenListener {
                override fun onEnterFullscreen(view: View, exit: () -> Unit) {
                    setImmersive(activity, true)
                    fsView = view
                    fsExit = exit
                }

                override fun onExitFullscreen() {
                    setImmersive(activity, false)
                    fsView = null
                    fsExit = null
                }
            })

            // rel=0: video bitəndə yalnız eyni kanalın videoları; son ekranı biz özümüz örtürük
            val options = IFramePlayerOptions.Builder(context)
                .controls(1)
                .rel(0)
                .ivLoadPolicy(3)
                .fullscreen(1)
                .build()

            initialize(object : AbstractYouTubePlayerListener() {
                override fun onReady(youTubePlayer: YouTubePlayer) {
                    player = youTubePlayer
                    val start = if (startAt > 8f) startAt - 3f else 0f
                    youTubePlayer.loadVideo(video.id, start)
                }

                override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
                    if (!ended) vm.savePosition(video.id, second)
                }

                override fun onStateChange(
                    youTubePlayer: YouTubePlayer,
                    state: PlayerConstants.PlayerState
                ) {
                    when (state) {
                        PlayerConstants.PlayerState.ENDED -> {
                            playing = false
                            if (loop) {
                                youTubePlayer.seekTo(0f)
                                youTubePlayer.play()
                            } else {
                                vm.clearPosition(video.id)
                                if (!vm.playNext(video)) ended = true
                            }
                        }
                        PlayerConstants.PlayerState.PLAYING -> {
                            ended = false
                            playing = true
                        }
                        PlayerConstants.PlayerState.PAUSED -> playing = false
                        else -> {}
                    }
                }
            }, options)
        }
    }

    DisposableEffect(playerView) {
        lifecycle.addObserver(playerView)
        onDispose {
            lifecycle.removeObserver(playerView)
            playerView.release()
            vm.flushWatch()
            setImmersive(activity, false)
        }
    }

    // İzləmə vaxtını say (gündəlik statistika və limit üçün)
    LaunchedEffect(playing) {
        while (playing) {
            delay(1000)
            vm.addWatchSeconds(1)
        }
    }

    BackHandler(enabled = fsView != null) { fsExit?.invoke() }

    val fav = vm.favorites.any { it.id == video.id }
    val later = vm.watchLater.any { it.id == video.id }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.back() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                }
            }

            Box(Modifier.fillMaxWidth().aspectRatio(16 / 9f).background(Color.Black)) {
                AndroidView(factory = { playerView }, modifier = Modifier.fillMaxSize())
                if (ended) {
                    // Video bitəndə YouTube-un "tövsiyə olunan videolar" ekranını gizlədir
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                            .clickable(enabled = false) {},
                        contentAlignment = Alignment.Center
                    ) {
                        Button(onClick = {
                            ended = false
                            player?.seekTo(0f)
                            player?.play()
                        }) { Text("Yenidən izlə") }
                    }
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                if (vm.limitReached) {
                    Text(
                        "Gündəlik izləmə limitinə çatdın (" + vm.dailyLimitMin + " dəq). Fasilə vermək yaxşı olar.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(10.dp)
                    )
                }
                Text(video.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)

                val meta = ArrayList<String>()
                if (video.views >= 0) meta.add("${compact(video.views)} baxış")
                val ago = formatAgo(video.published)
                if (ago.isNotEmpty()) meta.add(ago)
                if (meta.isNotEmpty()) {
                    Text(
                        meta.joinToString(" • "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(onClick = { vm.toggleFavorite(video) }) {
                        Icon(
                            if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (fav) "Sevimlilərdə" else "Sevimli")
                    }
                    FilledTonalButton(onClick = { vm.toggleWatchLater(video) }) {
                        Icon(
                            if (later) Icons.Default.Check else Icons.Default.Add,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (later) "Siyahıdadır" else "Sonra izlə")
                    }
                    FilledTonalButton(onClick = { shareVideo(context, video) }) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Paylaş")
                    }
                    FilterChip(
                        selected = loop,
                        onClick = { loop = !loop },
                        label = { Text("Təkrar") }
                    )
                }

                val desc = description
                if (!desc.isNullOrBlank()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { descOpen = !descOpen }
                            .padding(12.dp)
                    ) {
                        Text("Təsvir", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(
                            desc,
                            fontSize = 13.sp,
                            maxLines = if (descOpen) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Text(
                            if (descOpen) "Daha az" else "Daha çox",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 16.dp))

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = video.channelId.isNotBlank()) {
                            vm.openChannel(video.channelId, video.channel)
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LetterAvatar(video.channel, video.channelId.ifEmpty { video.channel }, 46.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(video.channel, fontWeight = FontWeight.SemiBold)
                        if (video.channelId.isNotBlank()) {
                            Text(
                                "Kanalın bütün videolarına bax",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                    }
                    if (video.channelId.isNotBlank()) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }
        }

        fsView?.let { v ->
            AndroidView(
                factory = { v },
                modifier = Modifier.fillMaxSize().background(Color.Black)
            )
        }
    }
}
