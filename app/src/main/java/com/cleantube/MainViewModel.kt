package com.cleantube

import android.app.Application
import android.content.Context
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

sealed class Screen {
    data class Player(val video: VideoItem) : Screen()
    data class Channel(val id: String, val name: String) : Screen()
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("cleantube", Context.MODE_PRIVATE)

    // ---------- Naviqasiya ----------
    var tab by mutableStateOf(if (prefs.getBoolean("show_trending", true)) Tab.HOME else Tab.SEARCH)
    val stack = mutableStateListOf<Screen>()

    fun openVideo(v: VideoItem) {
        addHistory(v)
        stack.add(Screen.Player(v))
    }

    fun openChannel(id: String, name: String) {
        if (id.isBlank()) return
        val top = stack.lastOrNull()
        if (top is Screen.Channel && top.id == id) return
        stack.add(Screen.Channel(id, name))
    }

    fun back() {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
    }

    // ---------- Ayarlar ----------
    var themeMode by mutableStateOf(prefs.getInt("theme", 0)) // 0 sistem, 1 qaranlıq, 2 işıq
        private set
    /** Bundan qısa videolar göstərilmir (dəqiqə). 0 = hamısı. */
    var minMinutes by mutableStateOf(
        prefs.getInt("min_minutes", if (prefs.getBoolean("hide_shorts", true)) 3 else 0)
    )
        private set
    var accent by mutableStateOf(prefs.getInt("accent", 0))
        private set
    var webCanGoBack by mutableStateOf(false)
    var webCanGoForward by mutableStateOf(false)
    var webProgress by mutableStateOf(100)
    var region by mutableStateOf(prefs.getString("region", null) ?: "AZ")
        private set
    var hasKey by mutableStateOf(false)
        private set

    var showTrending by mutableStateOf(prefs.getBoolean("show_trending", true))
        private set
    var showResume by mutableStateOf(prefs.getBoolean("show_resume", true))
        private set
    var searchHistoryOn by mutableStateOf(prefs.getBoolean("search_history_on", true))
        private set
    var watchHistoryOn by mutableStateOf(prefs.getBoolean("watch_history_on", true))
        private set
    var linkLoading by mutableStateOf(false)
        private set

    fun updateShowTrending(value: Boolean) {
        showTrending = value
        prefs.edit().putBoolean("show_trending", value).apply()
        if (value) {
            refreshHome()
        } else {
            homeJob?.cancel()
            homeVideos = emptyList()
            homeToken = null
            homeError = null
            homeLoading = false
        }
    }

    fun updateShowResume(value: Boolean) {
        showResume = value
        prefs.edit().putBoolean("show_resume", value).apply()
    }

    fun updateSearchHistoryOn(value: Boolean) {
        searchHistoryOn = value
        prefs.edit().putBoolean("search_history_on", value).apply()
    }

    fun updateWatchHistoryOn(value: Boolean) {
        watchHistoryOn = value
        prefs.edit().putBoolean("watch_history_on", value).apply()
    }

    private fun toast(msg: String) {
        Toast.makeText(getApplication<Application>(), msg, Toast.LENGTH_LONG).show()
    }

    /** YouTube linkini (və ya paylaşılan mətni) açır. API açarı tələb etmir. */
    fun openLink(text: String) {
        val id = extractVideoId(text)
        if (id == null) {
            toast("YouTube linki tanınmadı")
            return
        }
        viewModelScope.launch {
            linkLoading = true
            try {
                val video = if (hasKey) {
                    try {
                        YouTubeApi.videoById(id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        YouTubeApi.videoFromOEmbed(id)
                    }
                } else {
                    YouTubeApi.videoFromOEmbed(id)
                }
                openVideo(video)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast(e.message ?: "Video açıla bilmədi")
            } finally {
                linkLoading = false
            }
        }
    }

    private fun minSeconds(): Int = if (minMinutes <= 0) 1 else minMinutes * 60 + 1

    /** 20 dəqiqədən uzun videolar üçün YouTube özü süzsün (daha az API sərfi). */
    private fun durationParam(): String? = if (minMinutes >= 20) "long" else null

    fun updateTheme(mode: Int) {
        themeMode = mode
        prefs.edit().putInt("theme", mode).apply()
    }

    fun updateAccent(i: Int) {
        accent = i
        prefs.edit().putInt("accent", i).apply()
    }

    fun updateMinMinutes(m: Int) {
        if (minMinutes == m) return
        minMinutes = m
        prefs.edit().putInt("min_minutes", m).apply()
        channelCache.clear()
        resetHomeAndFeed()
        if (tab == Tab.HOME) ensureHome()
        if (searched) runSearch(lastQuery)
    }

    fun updateRegion(r: String) {
        if (region == r) return
        region = r
        prefs.edit().putString("region", r).apply()
        refreshHome()
    }

    var key1Set by mutableStateOf(false)
        private set
    var key2Set by mutableStateOf(false)
        private set

    /** Tətbiqdə daxil edilən açar build açarından üstündür. */
    private fun rebuildKeys() {
        val k1 = (prefs.getString("api_key", null) ?: "").ifBlank { BuildConfig.YOUTUBE_API_KEY }
        val k2 = (prefs.getString("api_key_2", null) ?: "").ifBlank { BuildConfig.YOUTUBE_API_KEY_2 }
        YouTubeApi.keys = listOf(k1, k2).filter { it.isNotBlank() }.distinct()
        key1Set = k1.isNotBlank()
        key2Set = k2.isNotBlank()
        hasKey = YouTubeApi.keys.isNotEmpty()
    }

    private fun keyPref(slot: Int): String = if (slot == 2) "api_key_2" else "api_key"

    fun saveKey(slot: Int, key: String) {
        val k = key.trim().trim('"')
        if (k.isEmpty()) return
        prefs.edit().putString(keyPref(slot), k).apply()
        rebuildKeys()
        error = null
        feedKey = ""
        if (showTrending) refreshHome()
    }

    fun clearKey(slot: Int) {
        prefs.edit().remove(keyPref(slot)).apply()
        rebuildKeys()
        if (!hasKey) {
            clearSearch()
            channelCache.clear()
            resetHomeAndFeed()
        }
    }

    /** YouTube sayt (veb) məlumatlarını (cookie, yerli yaddaş) silir. */
    fun clearWebData() {
        android.webkit.CookieManager.getInstance().removeAllCookies(null)
        android.webkit.CookieManager.getInstance().flush()
        android.webkit.WebStorage.getInstance().deleteAllData()
        toast("Veb məlumatları silindi")
    }

    private fun resetHomeAndFeed() {
        homeJob?.cancel()
        homeVideos = emptyList()
        homeToken = null
        homeError = null
        homeLoading = false
        feedJob?.cancel()
        feedKey = ""
        feed = emptyList()
        feedLoading = false
        feedError = null
    }

    // ---------- Ana səhifə ----------
    var homeVideos by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var homeLoading by mutableStateOf(false)
        private set
    var homeError by mutableStateOf<String?>(null)
        private set

    /** null = "Hamısı" (yalnız mövcud kateqoriyaların qarışığı). */
    var homeCategory by mutableStateOf<String?>(null)
        private set

    private var homeToken: String? = null
    private var homeJob: Job? = null

    private val homeCats = listOf("english", "islam", "ai", "17", "27", "28")

    fun ensureHome() {
        if (hasKey && showTrending && homeVideos.isEmpty() && !homeLoading && homeError == null) {
            loadHome(append = false, force = false)
        }
    }

    fun refreshHome(force: Boolean = false) {
        homeJob?.cancel()
        homeToken = null
        homeVideos = emptyList()
        homeError = null
        loadHome(append = false, force = force)
    }

    fun homeMore() {
        if (homeLoading || homeToken == null || homeVideos.isEmpty()) return
        loadHome(append = true, force = false)
    }

    fun changeCategory(id: String?) {
        if (homeCategory == id) return
        homeCategory = id
        refreshHome()
    }

    private fun cacheKey(cat: String) = "cache_" + cat + "_" + region + "_" + minMinutes

    private fun cacheGet(key: String): SearchPage? {
        return try {
            val s = prefs.getString(key, null) ?: return null
            val o = JSONObject(s)
            if (System.currentTimeMillis() - o.getLong("t") > 3 * 3600_000L) {
                null
            } else {
                val a = o.getJSONArray("items")
                val list = ArrayList<VideoItem>()
                for (i in 0 until a.length()) list.add(a.getJSONObject(i).toVideo())
                SearchPage(list, emptyList(), o.optString("next").ifEmpty { null })
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun cachePut(key: String, page: SearchPage) {
        val a = JSONArray()
        page.videos.forEach { a.put(it.toJson()) }
        val o = JSONObject()
            .put("t", System.currentTimeMillis())
            .put("next", page.nextToken ?: "")
            .put("items", a)
        val suffix = "_" + region + "_" + minMinutes
        val ed = prefs.edit()
        for (k in prefs.all.keys) {
            if (k.startsWith("cache_") && !k.endsWith(suffix)) ed.remove(k)
        }
        ed.putString(key, o.toString()).apply()
    }

    /** Bir kateqoriyanın bir səhifəsi. İngilis dili, Din, Aİ axtarışla; digərləri trend (olmasa axtarış) ilə. */
    private suspend fun fetchCategory(cat: String, tok: String?): SearchPage {
        return when (cat) {
            "english" -> YouTubeApi.searchVideos(
                "learn English", tok, SortOrder.RELEVANCE, minSeconds(), null, durationParam()
            )
            "islam" -> YouTubeApi.searchVideos(
                "İslam dini", tok, SortOrder.RELEVANCE, minSeconds(), null, durationParam(),
                regionCode = region, language = "az"
            )
            "ai" -> YouTubeApi.searchVideos(
                "artificial intelligence news", tok, SortOrder.DATE, minSeconds(), null, durationParam(),
                publishedAfter = YouTubeApi.daysAgo(14)
            )
            else -> {
                val viaSearch = tok != null && tok.startsWith("S|")
                val real = if (viaSearch) tok?.substring(2) else tok
                var result: SearchPage? = null
                if (!viaSearch) {
                    val trend = try {
                        YouTubeApi.trending(region, cat, real, minSeconds())
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: ApiException) {
                        null
                    }
                    if (trend != null && (trend.videos.isNotEmpty() || real != null)) result = trend
                }
                if (result != null) {
                    result
                } else {
                    // Trend bu kateqoriya/bölgə üçün boşdursa (məs. Təhsil) axtarışla doldur
                    val sp = YouTubeApi.searchVideos(
                        "", real, SortOrder.VIEWS, minSeconds(), null, durationParam(),
                        categoryId = cat, publishedAfter = YouTubeApi.daysAgo(30), regionCode = region
                    )
                    SearchPage(sp.videos, sp.channels, sp.nextToken?.let { "S|$it" })
                }
            }
        }
    }

    private suspend fun categoryFirstPage(cat: String, force: Boolean): SearchPage {
        val key = cacheKey(cat)
        if (!force) {
            val cached = cacheGet(key)
            if (cached != null) return cached
        }
        val page = fetchCategory(cat, null)
        if (page.videos.isNotEmpty()) cachePut(key, page)
        return page
    }

    /** "Hamısı": mövcud kateqoriyaların videoları növbə ilə qarışdırılır. */
    private suspend fun loadAll(force: Boolean): SearchPage {
        val errors = ArrayList<String>()
        val lists = coroutineScope {
            homeCats.map { c ->
                async {
                    try {
                        categoryFirstPage(c, force).videos
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        errors.add(e.message ?: "Xəta")
                        emptyList<VideoItem>()
                    }
                }
            }.awaitAll()
        }
        val out = ArrayList<VideoItem>()
        val seen = HashSet<String>()
        var i = 0
        while (true) {
            var any = false
            for (l in lists) {
                if (i < l.size) {
                    any = true
                    if (seen.add(l[i].id)) out.add(l[i])
                }
            }
            if (!any) break
            i++
        }
        if (out.isEmpty() && errors.isNotEmpty()) throw ApiException(errors.first())
        return SearchPage(out, emptyList(), null)
    }

    private fun loadHome(append: Boolean, force: Boolean) {
        val cat = homeCategory
        val tok = if (append) homeToken else null
        homeJob = viewModelScope.launch {
            homeLoading = true
            try {
                val page = when {
                    cat == null -> loadAll(force)
                    append -> fetchCategory(cat, tok)
                    else -> categoryFirstPage(cat, force)
                }
                homeVideos = if (append) {
                    val known = homeVideos.map { it.id }.toSet()
                    homeVideos + page.videos.filter { it.id !in known }
                } else page.videos
                homeToken = page.nextToken
                homeError = if (homeVideos.isEmpty()) "Video tapılmadı" else null
                homeLoading = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                homeError = e.message ?: "Naməlum xəta"
                homeLoading = false
            }
        }
    }

    // ---------- Axtarış ----------
    var query by mutableStateOf("")
    var searched by mutableStateOf(false)
        private set
    var searchType by mutableStateOf(SearchType.VIDEO)
        private set
    var order by mutableStateOf(SortOrder.RELEVANCE)
        private set
    var videos by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var channels by mutableStateOf<List<ChannelItem>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var searchVersion by mutableStateOf(0)
        private set
    var searchHistory by mutableStateOf<List<String>>(emptyList())
        private set
    var typing by mutableStateOf(false)
        private set
    var suggestions by mutableStateOf<List<String>>(emptyList())
        private set

    private var nextToken: String? = null
    private var lastQuery = ""
    private var job: Job? = null
    private var suggestJob: Job? = null

    fun onQueryChange(text: String) {
        query = text
        suggestJob?.cancel()
        val t = text.trim()
        if (t.length < 2 || !hasKey || extractVideoId(t) != null) {
            suggestions = emptyList()
            typing = false
            return
        }
        typing = true
        suggestJob = viewModelScope.launch {
            delay(300)
            suggestions = try {
                YouTubeApi.suggestions(t)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    fun searchFor(q: String) {
        query = q
        search()
    }

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        suggestJob?.cancel()
        suggestions = emptyList()
        typing = false
        if (extractVideoId(q) != null) {
            query = ""
            openLink(q)
            return
        }
        if (!hasKey) {
            toast("Axtarış üçün API açarı lazımdır. Ayarlardan əlavə et və ya YouTube linki yapışdır.")
            return
        }
        addSearchHistory(q)
        runSearch(q)
    }

    fun changeType(t: SearchType) {
        if (searchType == t) return
        searchType = t
        if (searched) runSearch(lastQuery)
    }

    fun changeOrder(o: SortOrder) {
        if (order == o) return
        order = o
        if (searched && searchType == SearchType.VIDEO) runSearch(lastQuery)
    }

    private fun runSearch(q: String) {
        job?.cancel()
        lastQuery = q
        nextToken = null
        videos = emptyList()
        channels = emptyList()
        error = null
        searched = true
        searchVersion++
        load(append = false)
    }

    fun clearSearch() {
        job?.cancel()
        suggestJob?.cancel()
        suggestions = emptyList()
        typing = false
        query = ""
        searched = false
        videos = emptyList()
        channels = emptyList()
        error = null
        loading = false
        nextToken = null
    }

    fun loadMore() {
        if (loading || nextToken == null || !searched) return
        load(append = true)
    }

    private fun load(append: Boolean) {
        val type = searchType
        val ord = order
        val q = lastQuery
        val token = if (append) nextToken else null
        job = viewModelScope.launch {
            loading = true
            try {
                val page = if (type == SearchType.VIDEO) {
                    YouTubeApi.searchVideos(q, token, ord, minSeconds(), null, durationParam())
                } else {
                    YouTubeApi.searchChannels(q, token)
                }
                if (append) {
                    val kv = videos.map { it.id }.toSet()
                    videos = videos + page.videos.filter { it.id !in kv }
                    val kc = channels.map { it.id }.toSet()
                    channels = channels + page.channels.filter { it.id !in kc }
                } else {
                    videos = page.videos
                    channels = page.channels
                }
                nextToken = page.nextToken
                error = if (videos.isEmpty() && channels.isEmpty()) "Nəticə tapılmadı" else null
                loading = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Naməlum xəta"
                loading = false
            }
        }
    }

    private fun addSearchHistory(q: String) {
        if (!searchHistoryOn) return
        searchHistory = (listOf(q) + searchHistory.filter { !it.equals(q, ignoreCase = true) }).take(20)
        saveSearchHistory()
    }

    fun removeSearchHistory(q: String) {
        searchHistory = searchHistory.filter { it != q }
        saveSearchHistory()
    }

    fun clearSearchHistory() {
        searchHistory = emptyList()
        saveSearchHistory()
    }

    private fun saveSearchHistory() {
        val a = JSONArray()
        searchHistory.forEach { a.put(it) }
        prefs.edit().putString("search_history", a.toString()).apply()
    }

    // ---------- Kitabxana: sevimlilər, sonra izlə, tarixçə ----------
    var libraryTab by mutableStateOf(0)
    var favorites by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var watchLater by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var history by mutableStateOf<List<VideoItem>>(emptyList())
        private set

    fun toggleFavorite(v: VideoItem) {
        favorites = if (favorites.any { it.id == v.id }) {
            favorites.filter { it.id != v.id }
        } else {
            listOf(v) + favorites
        }
        saveVideos("favorites", favorites)
    }

    fun toggleWatchLater(v: VideoItem) {
        watchLater = if (watchLater.any { it.id == v.id }) {
            watchLater.filter { it.id != v.id }
        } else {
            listOf(v) + watchLater
        }
        saveVideos("watch_later", watchLater)
    }

    private fun addHistory(v: VideoItem) {
        if (!watchHistoryOn) return
        history = (listOf(v) + history.filter { it.id != v.id }).take(100)
        saveVideos("history", history)
    }

    fun clearHistory() {
        history = emptyList()
        saveVideos("history", history)
        positions.clear()
        persistPositions()
    }

    private fun saveVideos(key: String, list: List<VideoItem>) {
        val a = JSONArray()
        list.forEach { a.put(it.toJson()) }
        prefs.edit().putString(key, a.toString()).apply()
    }

    private fun loadVideos(key: String): List<VideoItem> = try {
        val a = JSONArray(prefs.getString(key, null) ?: "[]")
        val out = ArrayList<VideoItem>()
        for (i in 0 until a.length()) out.add(a.getJSONObject(i).toVideo())
        out
    } catch (e: Exception) {
        emptyList()
    }

    // ---------- İzləmə mövqeyi (davam etmək üçün) ----------
    private val positions = LinkedHashMap<String, Float>()

    fun positionOf(id: String): Float = positions[id] ?: 0f

    fun savePosition(id: String, sec: Float) {
        if (sec < 3f || !watchHistoryOn) return
        val old = positions[id]
        if (old != null && kotlin.math.abs(sec - old) < 5f) return
        positions.remove(id)
        positions[id] = sec
        while (positions.size > 200) positions.remove(positions.keys.first())
        persistPositions()
    }

    fun clearPosition(id: String) {
        if (positions.remove(id) != null) persistPositions()
    }

    private fun persistPositions() {
        val o = JSONObject()
        positions.forEach { (k, v) -> o.put(k, v.toDouble()) }
        prefs.edit().putString("positions", o.toString()).apply()
    }

    // ---------- Abunəliklər ----------
    var follows by mutableStateOf<List<FollowedChannel>>(emptyList())
        private set
    var feed by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var feedLoading by mutableStateOf(false)
        private set
    var feedError by mutableStateOf<String?>(null)
        private set

    private var feedKey = ""
    private var feedTime = 0L
    private var feedJob: Job? = null

    fun isFollowing(id: String): Boolean = follows.any { it.id == id }

    fun toggleFollow(info: ChannelInfo) {
        follows = if (isFollowing(info.id)) {
            follows.filter { it.id != info.id }
        } else {
            listOf(FollowedChannel(info.id, info.title, info.avatar, info.uploadsId)) + follows
        }
        val a = JSONArray()
        follows.forEach { a.put(it.toJson()) }
        prefs.edit().putString("follows", a.toString()).apply()
    }

    fun refreshFeed(force: Boolean) {
        if (!hasKey) return
        val list = follows.take(25)
        val key = list.joinToString(",") { it.id }
        if (list.isEmpty()) {
            feed = emptyList()
            feedKey = ""
            feedError = null
            return
        }
        val fresh = System.currentTimeMillis() - feedTime < 10 * 60_000L
        if (!force && key == feedKey && fresh) return

        feedJob?.cancel()
        feedJob = viewModelScope.launch {
            feedLoading = true
            feedError = null
            try {
                val minS = minSeconds()
                val errors = ArrayList<String>()
                val results = coroutineScope {
                    list.map { ch ->
                        async {
                            try {
                                YouTubeApi.channelUploads(ch.uploadsId, null, minS, 10).videos
                                    .map { it.copy(channel = ch.title, channelId = ch.id) }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                errors.add(e.message ?: "Xəta")
                                emptyList<VideoItem>()
                            }
                        }
                    }.awaitAll()
                }
                feed = results.flatten().sortedByDescending { it.published }.take(100)
                feedKey = key
                feedTime = System.currentTimeMillis()
                if (feed.isEmpty()) {
                    feedError = if (errors.isNotEmpty()) errors.first() else "Yeni video tapılmadı"
                }
                feedLoading = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                feedError = e.message ?: "Naməlum xəta"
                feedLoading = false
            }
        }
    }

    // ---------- İzləmə vaxtı ----------
    var watchToday by mutableStateOf(0)
        private set
    var dailyLimitMin by mutableStateOf(prefs.getInt("daily_limit", 0))
        private set
    private var watchDay = ""

    private fun dayKey(cal: Calendar = Calendar.getInstance()): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)

    private fun ensureDay() {
        val d = dayKey()
        if (d != watchDay) {
            watchDay = d
            watchToday = prefs.getInt("watch_$d", 0)
        }
    }

    fun addWatchSeconds(sec: Int) {
        ensureDay()
        watchToday += sec
        if (watchToday % 10 == 0) prefs.edit().putInt("watch_$watchDay", watchToday).apply()
    }

    fun flushWatch() {
        ensureDay()
        prefs.edit().putInt("watch_$watchDay", watchToday).apply()
    }

    val limitReached: Boolean
        get() = dailyLimitMin > 0 && watchToday >= dailyLimitMin * 60

    fun updateDailyLimit(min: Int) {
        dailyLimitMin = min
        prefs.edit().putInt("daily_limit", min).apply()
    }

    /** Son 7 günün izləmə vaxtı: (gün adı, saniyə). */
    fun weekStats(): List<Pair<String, Int>> {
        ensureDay()
        val out = ArrayList<Pair<String, Int>>()
        val label = SimpleDateFormat("EEE", Locale.getDefault())
        for (i in 6 downTo 0) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            val secs = if (i == 0) watchToday else prefs.getInt("watch_" + dayKey(cal), 0)
            out.add(Pair(label.format(cal.time), secs))
        }
        return out
    }

    // ---------- Növbə (ardıcıl oynatma) ----------
    private var queue: List<VideoItem> = emptyList()

    fun playAll(list: List<VideoItem>) {
        if (list.isEmpty()) return
        queue = list
        openVideo(list.first())
    }

    /** Növbədə növbəti video varsa onu açır (cari ekranı əvəz edir). */
    fun playNext(current: VideoItem): Boolean {
        val i = queue.indexOfFirst { it.id == current.id }
        if (i < 0 || i >= queue.size - 1) return false
        val next = queue[i + 1]
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
        openVideo(next)
        return true
    }

    // ---------- Kanallar ----------
    private val channelCache = HashMap<String, ChannelUi>()

    fun channelUi(id: String, name: String): ChannelUi =
        channelCache.getOrPut(id) { ChannelUi(id, name, viewModelScope) { minSeconds() } }

    // ---------- Başlanğıc ----------
    init {
        rebuildKeys()
        ensureDay()

        favorites = loadVideos("favorites")
        watchLater = loadVideos("watch_later")
        history = loadVideos("history")
        try {
            val a = JSONArray(prefs.getString("follows", null) ?: "[]")
            val out = ArrayList<FollowedChannel>()
            for (i in 0 until a.length()) out.add(a.getJSONObject(i).toFollowed())
            follows = out
        } catch (e: Exception) {
        }
        try {
            val a = JSONArray(prefs.getString("search_history", null) ?: "[]")
            val out = ArrayList<String>()
            for (i in 0 until a.length()) out.add(a.getString(i))
            searchHistory = out
        } catch (e: Exception) {
        }
        try {
            val o = JSONObject(prefs.getString("positions", null) ?: "{}")
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                positions[k] = o.getDouble(k).toFloat()
            }
        } catch (e: Exception) {
        }
    }
}
