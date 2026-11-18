package com.cleantube

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SearchScreen(vm: MainViewModel, listState: LazyListState) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val isVideo = vm.searchType == SearchType.VIDEO
    val count = if (isVideo) vm.videos.size else vm.channels.size

    val nearEnd by remember(count) {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            count > 0 && last >= count - 3
        }
    }
    LaunchedEffect(nearEnd, count) { if (nearEnd) vm.loadMore() }
    LaunchedEffect(vm.searchVersion) { listState.scrollToItem(0) }

    Column(Modifier.fillMaxSize()) {
        val sv = MaterialTheme.colorScheme.surfaceVariant
        TextField(
            value = vm.query,
            onValueChange = { vm.onQueryChange(it) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            placeholder = { Text(if (vm.hasKey) "YouTube-da axtar" else "YouTube linkini yapışdır") },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (vm.query.isNotEmpty() || vm.searched) {
                    IconButton(onClick = { vm.clearSearch() }) {
                        Icon(Icons.Default.Close, contentDescription = "Sil")
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = sv,
                unfocusedContainerColor = sv,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                focus.clearFocus()
                keyboard?.hide()
                vm.search()
            })
        )

        if (vm.searched) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = isVideo,
                    onClick = { vm.changeType(SearchType.VIDEO) },
                    label = { Text("Videolar") }
                )
                FilterChip(
                    selected = !isVideo,
                    onClick = { vm.changeType(SearchType.CHANNEL) },
                    label = { Text("Kanallar") }
                )
                if (isVideo) {
                    DurationChip(vm)
                    for (o in SortOrder.values()) {
                        FilterChip(
                            selected = vm.order == o,
                            onClick = { vm.changeOrder(o) },
                            label = { Text(o.label) }
                        )
                    }
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (vm.typing && vm.suggestions.isNotEmpty()) {
                SuggestionList(vm)
            } else if (!vm.searched) {
                if (vm.hasKey) SearchHistory(vm) else NoKeyHint(vm)
            } else if (count == 0) {
                if (vm.loading) {
                    Column {
                        repeat(3) { SkeletonCard() }
                    }
                } else {
                    Text(
                        text = vm.error ?: "Nəticə tapılmadı",
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    if (isVideo) {
                        items(vm.videos, key = { it.id }) { v -> VideoCard(v, vm) }
                    } else {
                        items(vm.channels, key = { it.id }) { ch ->
                            ChannelResultRow(ch) { vm.openChannel(ch.id, ch.title) }
                        }
                    }
                    if (vm.loading) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchHistory(vm: MainViewModel) {
    if (!vm.searchHistoryOn || vm.searchHistory.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Axtarmaq üçün yuxarıda yaz.\nVideo və ya kanal tapa bilərsən.\nYouTube linkini də yapışdıra bilərsən.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp)
            )
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Son axtarışlar",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { vm.clearSearchHistory() }) { Text("Hamısını sil") }
            }
        }
        items(vm.searchHistory, key = { it }) { q ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { vm.searchFor(q) }
                    .padding(start = 16.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(q, modifier = Modifier.weight(1f).padding(horizontal = 14.dp), maxLines = 1)
                IconButton(onClick = { vm.removeSearchHistory(q) }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Sil",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionList(vm: MainViewModel) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(vm.suggestions, key = { it }) { q ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { vm.searchFor(q) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(q, modifier = Modifier.weight(1f).padding(start = 14.dp), maxLines = 1)
            }
        }
    }
}

@Composable
private fun NoKeyHint(vm: MainViewModel) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "API açarı olmadan axtarış işləmir, amma:\n\n• YouTube linkini yuxarıdakı xanaya yapışdırıb aça bilərsən\n• \"YouTube\" bölməsində saytda gəzib istədiyin videonu seçsən, təmiz pleyerdə açılır\n• YouTube tətbiqindən \"Paylaş\" → CleanTube seçə bilərsən",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.tab = Tab.WEB }) { Text("YouTube-da gəz") }
            OutlinedButton(onClick = { vm.openLink(readClipboardText(context).orEmpty()) }) {
                Text("Link aç")
            }
        }
        TextButton(onClick = { vm.tab = Tab.SETTINGS }) { Text("API açarı əlavə et") }
    }
}
