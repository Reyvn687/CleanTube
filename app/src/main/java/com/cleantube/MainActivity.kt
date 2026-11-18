package com.cleantube

import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

enum class Tab(val label: String, val icon: ImageVector, val inBar: Boolean = true) {
    HOME("Ana", Icons.Default.Home),
    SEARCH("Axtar", Icons.Default.Search),
    WEB("YouTube", Icons.Default.PlayArrow),
    SUBS("Abunə", Icons.Default.Person),
    LIBRARY("Kitabxana", Icons.Default.Favorite),
    SETTINGS("Ayarlar", Icons.Default.Settings, false)
}

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CleanTubeTheme(vm.themeMode, vm.accent) {
                App(vm)
            }
        }
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** YouTube-dan "Paylaş" və ya link vasitəsilə gələn videonu açır (API açarı lazım deyil). */
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val text = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        }
        if (!text.isNullOrBlank()) vm.openLink(text)
    }
}

@Composable
fun App(vm: MainViewModel) {
    val searchList = rememberLazyListState()
    val homeList = rememberLazyListState()
    val webHolder = remember { arrayOfNulls<WebView>(1) }

    BackHandler(enabled = vm.stack.isNotEmpty()) { vm.back() }
    BackHandler(enabled = vm.stack.isEmpty() && vm.tab != Tab.HOME) { vm.tab = Tab.HOME }
    BackHandler(enabled = vm.stack.isEmpty() && vm.tab == Tab.WEB && vm.webCanGoBack) {
        webHolder[0]?.goBack()
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            when (val top = vm.stack.lastOrNull()) {
                is Screen.Player -> key(top.video.id) { PlayerScreen(top.video, vm) }
                is Screen.Channel -> key(top.id) { ChannelScreen(vm, top.id, top.name) }
                null -> MainTabs(vm, homeList, searchList, webHolder)
            }
            if (vm.linkLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }
}

@Composable
private fun MainTabs(
    vm: MainViewModel,
    homeList: LazyListState,
    searchList: LazyListState,
    webHolder: Array<WebView?>
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                for (t in Tab.values()) {
                    if (t.inBar) {
                        NavigationBarItem(
                            selected = vm.tab == t,
                            onClick = { vm.tab = t },
                            icon = { Icon(t.icon, contentDescription = t.label) },
                            label = { Text(t.label, maxLines = 1) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (vm.tab) {
                Tab.HOME -> HomeScreen(vm, homeList)
                Tab.SEARCH -> SearchScreen(vm, searchList)
                Tab.WEB -> WebScreen(vm, webHolder)
                Tab.SUBS -> SubsScreen(vm)
                Tab.LIBRARY -> LibraryScreen(vm)
                Tab.SETTINGS -> SettingsScreen(vm)
            }
        }
    }
}
