package com.cleantube

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LibraryScreen(vm: MainViewModel) {
    val current: List<VideoItem> = when (vm.libraryTab) {
        0 -> vm.favorites
        1 -> vm.watchLater
        else -> if (vm.watchHistoryOn) vm.history else emptyList()
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Kitabxana", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (vm.libraryTab == 2 && vm.history.isNotEmpty()) {
                TextButton(onClick = { vm.clearHistory() }) { Text("Təmizlə") }
            }
            IconButton(onClick = { vm.tab = Tab.SETTINGS }) {
                Icon(Icons.Default.Settings, contentDescription = "Ayarlar")
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = vm.libraryTab == 0,
                onClick = { vm.libraryTab = 0 },
                label = { Text("Sevimlilər (${vm.favorites.size})") }
            )
            FilterChip(
                selected = vm.libraryTab == 1,
                onClick = { vm.libraryTab = 1 },
                label = { Text("Sonra izlə (${vm.watchLater.size})") }
            )
            FilterChip(
                selected = vm.libraryTab == 2,
                onClick = { vm.libraryTab = 2 },
                label = { Text("Tarixçə") }
            )
        }
        if (vm.libraryTab != 2 && current.isNotEmpty()) {
            Button(
                onClick = { vm.playAll(current) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Hamısını ardıcıl oynat")
            }
        }
        val emptyText = when (vm.libraryTab) {
            0 -> "Hələ sevimli video yoxdur.\nVideonun yanındakı ⋮ menyusundan əlavə et."
            1 -> "Sonra izləmək üçün videoları ⋮ menyusundan əlavə et."
            else -> if (vm.watchHistoryOn) "İzlədiyin videolar burada görünəcək."
            else "İzləmə tarixçəsi söndürülüb. Ayarlardan yandıra bilərsən."
        }
        VideoList(current, emptyText, vm)
    }
}

@Composable
private fun VideoList(list: List<VideoItem>, emptyText: String, vm: MainViewModel) {
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                emptyText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp)
            )
        }
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 12.dp)) {
            items(list, key = { it.id }) { v -> VideoCard(v, vm) }
        }
    }
}
