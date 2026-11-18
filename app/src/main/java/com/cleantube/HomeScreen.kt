package com.cleantube

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

private val Categories = listOf(
    Pair<String?, String>(null, "Hamısı"),
    Pair<String?, String>("english", "İngilis dili"),
    Pair<String?, String>("islam", "Din"),
    Pair<String?, String>("ai", "Aİ"),
    Pair<String?, String>("17", "İdman"),
    Pair<String?, String>("27", "Təhsil"),
    Pair<String?, String>("28", "Elm və texnika")
)

private fun greeting(): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        h < 5 -> "Gecən xeyir"
        h < 12 -> "Sabahın xeyir"
        h < 18 -> "Günün xeyir"
        else -> "Axşamın xeyir"
    }
}

@Composable
fun HomeScreen(vm: MainViewModel, listState: LazyListState) {
    val context = LocalContext.current
    LaunchedEffect(vm.hasKey, vm.showTrending) { vm.ensureHome() }

    val count = vm.homeVideos.size
    val nearEnd by remember(count) {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            count > 0 && last >= count - 1
        }
    }
    LaunchedEffect(nearEnd, count) { if (nearEnd && vm.showTrending) vm.homeMore() }

    val resume = if (vm.showResume && vm.watchHistoryOn) {
        vm.history.filter { vm.positionOf(it.id) > 8f }.take(10)
    } else {
        emptyList()
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                }
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text("CleanTube", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    val limit = vm.limitReached
                    Text(
                        text = greeting() + " • Bu gün " + (vm.watchToday / 60) + " dəq" +
                            (if (limit) " (limit dolub)" else ""),
                        fontSize = 12.sp,
                        color = if (limit) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (vm.hasKey && vm.showTrending) {
                    IconButton(onClick = { vm.refreshHome(true) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Yenilə")
                    }
                }
                IconButton(onClick = { vm.tab = Tab.SETTINGS }) {
                    Icon(Icons.Default.Settings, contentDescription = "Ayarlar")
                }
            }
        }

        if (resume.isNotEmpty()) {
            item {
                Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                    Text(
                        "İzləməyə davam et",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(resume, key = { it.id }) { v -> MiniCard(v, vm) }
                    }
                }
            }
        }

        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = false,
                    onClick = { vm.openLink(readClipboardText(context).orEmpty()) },
                    label = { Text("Link aç") }
                )
                if (vm.hasKey) {
                    DurationChip(vm)
                    if (vm.showTrending) {
                        for (c in Categories) {
                            FilterChip(
                                selected = vm.homeCategory == c.first,
                                onClick = { vm.changeCategory(c.first) },
                                label = { Text(c.second) }
                            )
                        }
                    }
                }
            }
        }

        if (!vm.hasKey) {
            item {
                InfoCard(
                    "API açarı olmadan YouTube-da gəzə və linklə video aça bilərsən. Axtarış, trend və kanal siyahıları üçün pulsuz API açarı lazımdır.",
                    "YouTube-da gəz", { vm.tab = Tab.WEB },
                    "Açar əlavə et", { vm.tab = Tab.SETTINGS }
                )
            }
        } else if (!vm.showTrending) {
            item {
                InfoCard(
                    "Trend videoları söndürülüb. Axtarış bölməsindən istədiyin videonu tapa bilərsən.",
                    "Axtar", { vm.tab = Tab.SEARCH },
                    "Trendi yandır", { vm.updateShowTrending(true) }
                )
            }
        } else {
            if (vm.homeCategory == null) {
                item {
                    Text(
                        "Hamısı: İngilis dili, Din, Aİ, İdman, Təhsil və Elm kateqoriyalarının qarışığı.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                    )
                }
            }

            items(vm.homeVideos, key = { it.id }) { v -> VideoCard(v, vm) }

            if (vm.homeLoading && vm.homeVideos.isEmpty()) {
                item {
                    Column {
                        repeat(3) { SkeletonCard() }
                    }
                }
            } else if (vm.homeLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    }
                }
            } else if (vm.homeVideos.isEmpty() && vm.homeError != null) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            vm.homeError ?: "",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Button(onClick = { vm.refreshHome() }, modifier = Modifier.padding(top = 12.dp)) {
                            Text("Yenidən yoxla")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(
    text: String,
    firstLabel: String,
    onFirst: () -> Unit,
    secondLabel: String,
    onSecond: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp)
    ) {
        Text(text, fontSize = 14.sp)
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onFirst) { Text(firstLabel) }
            OutlinedButton(onClick = onSecond) { Text(secondLabel) }
        }
    }
}
