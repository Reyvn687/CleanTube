package com.cleantube

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ChannelScreen(vm: MainViewModel, id: String, name: String) {
    val ui = remember(id) { vm.channelUi(id, name) }
    LaunchedEffect(ui) { ui.start() }

    val count = ui.videos.size
    val nearEnd by remember(count) {
        derivedStateOf {
            val last = ui.listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            count > 0 && last >= count - 1
        }
    }
    LaunchedEffect(nearEnd, count) { if (nearEnd) ui.loadMore() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
            }
            Text(
                text = ui.info?.title ?: name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        LazyColumn(state = ui.listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
            item { ChannelHeader(ui, name, vm) }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !ui.popular,
                        onClick = { ui.changePopular(false) },
                        label = { Text("Ən yeni") }
                    )
                    FilterChip(
                        selected = ui.popular,
                        onClick = { ui.changePopular(true) },
                        label = { Text("Ən çox baxılan") }
                    )
                }
            }
            items(ui.videos, key = { it.id }) { v -> VideoCard(v, vm) }
            if (ui.loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    }
                }
            } else if (ui.videos.isEmpty() && ui.error != null) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            ui.error ?: "",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Button(onClick = { ui.retry() }, modifier = Modifier.padding(top = 12.dp)) {
                            Text("Yenidən yoxla")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelHeader(ui: ChannelUi, fallbackName: String, vm: MainViewModel) {
    val info = ui.info
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RemoteAvatar(info?.avatar, info?.title ?: fallbackName, ui.id, 76.dp)
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(
                    text = info?.title ?: fallbackName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (info != null) {
                    val parts = ArrayList<String>()
                    if (info.subscribers >= 0) parts.add("${compact(info.subscribers)} abunəçi")
                    if (info.videoCount >= 0) parts.add("${compact(info.videoCount)} video")
                    if (parts.isNotEmpty()) {
                        Text(
                            parts.joinToString(" • "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
        if (info != null) {
            val following = vm.follows.any { it.id == info.id }
            if (following) {
                FilledTonalButton(
                    onClick = { vm.toggleFollow(info) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) { Text("Abunəsən") }
            } else {
                Button(
                    onClick = { vm.toggleFollow(info) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) { Text("Abunə ol") }
            }
        }
        if (info != null && info.description.isNotBlank()) {
            Text(
                text = info.description,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .clickable { expanded = !expanded }
            )
        }
    }
}
