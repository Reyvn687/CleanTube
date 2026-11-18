package com.cleantube

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Bir kanalın ekran vəziyyəti. ViewModel-də saxlanılır ki, geri qayıdanda yenidən yüklənməsin. */
class ChannelUi(
    val id: String,
    val fallbackName: String,
    private val scope: CoroutineScope,
    private val minSeconds: () -> Int
) {
    var info by mutableStateOf<ChannelInfo?>(null)
        private set
    var videos by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var popular by mutableStateOf(false)
        private set

    val listState = LazyListState()

    private var token: String? = null
    private var job: Job? = null
    private var started = false

    fun start() {
        if (started) return
        started = true
        reload()
    }

    fun retry() = reload()

    fun changePopular(p: Boolean) {
        if (popular == p) return
        popular = p
        reload()
    }

    fun loadMore() {
        if (loading || token == null || videos.isEmpty()) return
        load(append = true)
    }

    private fun reload() {
        job?.cancel()
        videos = emptyList()
        token = null
        error = null
        load(append = false)
    }

    private fun load(append: Boolean) {
        val byViews = popular
        job = scope.launch {
            loading = true
            try {
                val inf = info ?: YouTubeApi.channelInfo(id).also { info = it }
                val collected = ArrayList<VideoItem>()
                var t: String? = if (append) token else null
                var attempts = 0
                do {
                    val page = if (byViews) {
                        YouTubeApi.searchVideos("", t, SortOrder.VIEWS, minSeconds(), id)
                    } else {
                        YouTubeApi.channelUploads(inf.uploadsId, t, minSeconds())
                    }
                    for (v in page.videos) collected.add(v.copy(channel = inf.title, channelId = inf.id))
                    t = page.nextToken
                    attempts++
                } while (collected.size < 8 && t != null && attempts < 4 && !byViews)

                token = t
                if (append) {
                    val known = videos.map { it.id }.toSet()
                    videos = videos + collected.filter { it.id !in known }
                } else {
                    videos = collected
                }
                error = if (videos.isEmpty()) "Video tapılmadı" else null
                loading = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Naməlum xəta"
                loading = false
            }
        }
    }
}
