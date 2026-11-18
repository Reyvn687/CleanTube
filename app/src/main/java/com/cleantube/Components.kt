package com.cleantube

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest

private val AvatarColors = listOf(
    0xFFE57373L, 0xFF64B5F6L, 0xFF81C784L, 0xFFFFB74DL, 0xFFBA68C8L, 0xFF4DB6ACL, 0xFFF06292L
)

fun shareVideo(context: Context, v: VideoItem) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, v.title + "\nhttps://youtu.be/" + v.id)
    }
    context.startActivity(Intent.createChooser(send, null))
}

@Composable
fun thumbRequest(url: String): ImageRequest {
    val ctx = LocalContext.current
    return remember(url) { ImageRequest.Builder(ctx).data(url).crossfade(true).build() }
}

/** Yüklənərkən göstərilən yanıb-sönən boş kart. */
@Composable
fun SkeletonCard() {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "alpha"
    )
    val color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)
    Column(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
        Box(
            Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .aspectRatio(16 / 9f)
                .clip(RoundedCornerShape(14.dp))
                .background(color)
        )
        Row(Modifier.padding(start = 12.dp, top = 10.dp)) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(color))
            Column(Modifier.padding(start = 10.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth(0.85f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(color)
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth(0.5f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(color)
                )
            }
        }
    }
}

fun copyLink(context: Context, v: VideoItem) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("YouTube", "https://youtu.be/" + v.id))
    Toast.makeText(context, "Link kopyalandı", Toast.LENGTH_SHORT).show()
}

fun readClipboardText(context: Context): String? {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = cm.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()
}

val DurationOptions = listOf(0, 1, 3, 5, 10, 20, 30, 60)

fun durationLabel(m: Int): String = if (m == 0) "Hamısı" else "$m dəq+"

/** Video uzunluğuna görə süzgəc: seçilən dəqiqədən qısa videolar göstərilmir. */
@Composable
fun DurationChip(vm: MainViewModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = vm.minMinutes > 0,
            onClick = { open = true },
            label = { Text("Uzunluq: " + durationLabel(vm.minMinutes)) }
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (m in DurationOptions) {
                DropdownMenuItem(
                    text = { Text(if (m == 0) "Hamısı (Shorts daxil)" else "$m dəqiqədən uzun") },
                    onClick = {
                        open = false
                        vm.updateMinMinutes(m)
                    }
                )
            }
        }
    }
}

@Composable
fun LetterAvatar(name: String, id: String, size: Dp, modifier: Modifier = Modifier) {
    val color = Color(AvatarColors[(id.hashCode() and 0x7fffffff) % AvatarColors.size])
    Box(
        modifier.size(size).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.firstOrNull()?.uppercase() ?: "?",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.45f).sp
        )
    }
}

@Composable
fun RemoteAvatar(url: String?, name: String, id: String, size: Dp) {
    if (url.isNullOrBlank()) {
        LetterAvatar(name, id, size)
    } else {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    }
}

@Composable
fun VideoCard(v: VideoItem, vm: MainViewModel) {
    val context = LocalContext.current
    val fav = vm.favorites.any { it.id == v.id }
    val later = vm.watchLater.any { it.id == v.id }
    val progress = if (v.durationSec > 0) (vm.positionOf(v.id) / v.durationSec).coerceIn(0f, 1f) else 0f
    var menu by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .clickable { vm.openVideo(v) }
            .padding(bottom = 18.dp)
    ) {
        Box(
            Modifier
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(14.dp))
        ) {
            AsyncImage(
                model = thumbRequest(v.thumb),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16 / 9f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            if (v.durationSec > 0) {
                Text(
                    text = formatDuration(v.durationSec),
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            if (progress > 0.01f) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(progress)
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
        Row(
            Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            LetterAvatar(
                name = v.channel,
                id = v.channelId.ifEmpty { v.channel },
                size = 36.dp,
                modifier = Modifier.clickable { vm.openChannel(v.channelId, v.channel) }
            )
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(
                    text = v.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp
                )
                Text(
                    text = v.metaLine(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clickable { vm.openChannel(v.channelId, v.channel) }
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Menyu",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (fav) "Sevimlilərdən çıxar" else "Sevimlilərə əlavə et") },
                        onClick = {
                            menu = false
                            vm.toggleFavorite(v)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (later) "Sonra izləmədən çıxar" else "Sonra izlə") },
                        onClick = {
                            menu = false
                            vm.toggleWatchLater(v)
                        }
                    )
                    if (v.channelId.isNotBlank()) {
                        DropdownMenuItem(
                            text = { Text("Kanala keç") },
                            onClick = {
                                menu = false
                                vm.openChannel(v.channelId, v.channel)
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Linki kopyala") },
                        onClick = {
                            menu = false
                            copyLink(context, v)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Paylaş") },
                        onClick = {
                            menu = false
                            shareVideo(context, v)
                        }
                    )
                }
            }
        }
    }
}

/** "İzləməyə davam et" cərgəsi üçün kiçik kart. */
@Composable
fun MiniCard(v: VideoItem, vm: MainViewModel) {
    val progress = if (v.durationSec > 0) (vm.positionOf(v.id) / v.durationSec).coerceIn(0f, 1f) else 0f
    Column(
        Modifier
            .width(172.dp)
            .clickable { vm.openVideo(v) }
    ) {
        Box(Modifier.clip(RoundedCornerShape(10.dp))) {
            AsyncImage(
                model = thumbRequest(v.thumb),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16 / 9f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            if (progress > 0.01f) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(progress)
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
        Text(
            text = v.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
fun ChannelResultRow(ch: ChannelItem, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        RemoteAvatar(ch.avatar, ch.title, ch.id, 60.dp)
        Column(Modifier.weight(1f)) {
            Text(ch.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (ch.description.isNotBlank()) {
                Text(
                    ch.description,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
        }
    }
}
