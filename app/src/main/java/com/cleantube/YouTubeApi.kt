package com.cleantube

import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object YouTubeApi {
    private const val BASE = "https://www.googleapis.com/youtube/v3"

    /** Shorts-u gizlətmək açıq olanda bundan qısa videolar çıxarılır (saniyə). */
    const val MIN_SECONDS = 181

    /** İstifadə olunan API açarları (1 və ya 2). Birincinin limiti bitəndə növbətiyə keçilir. */
    @Volatile
    var keys: List<String> = emptyList()

    @Volatile
    private var active = 0

    private const val KEY_PLACEHOLDER = "@@KEY@@"
    private val KEY_REASONS = setOf(
        "quotaExceeded", "dailyLimitExceeded", "rateLimitExceeded", "userRateLimitExceeded", "keyInvalid"
    )

    private class KeyProblem(message: String) : Exception(message)

    /** Bu andan n gün əvvəlin tarixi (RFC 3339, UTC). */
    fun daysAgo(n: Int): String {
        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC")
        return f.format(Date(System.currentTimeMillis() - n * 86_400_000L))
    }

    private val DURATION = Regex("PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?")

    private fun requireKey(): String {
        if (keys.isEmpty()) throw ApiException("API açarı yoxdur.")
        return KEY_PLACEHOLDER
    }

    suspend fun searchVideos(
        query: String,
        pageToken: String?,
        order: SortOrder,
        minSeconds: Int,
        channelId: String? = null,
        videoDuration: String? = null,
        categoryId: String? = null,
        publishedAfter: String? = null,
        regionCode: String? = null,
        language: String? = null
    ): SearchPage = withContext(Dispatchers.IO) {
        val key = requireKey()
        val sb = StringBuilder("$BASE/search?part=snippet&type=video&videoEmbeddable=true&maxResults=30")
        sb.append("&order=").append(order.api)
        if (query.isNotBlank()) sb.append("&q=").append(URLEncoder.encode(query, "UTF-8"))
        if (channelId != null) sb.append("&channelId=").append(channelId)
        if (videoDuration != null) sb.append("&videoDuration=").append(videoDuration)
        if (categoryId != null) sb.append("&videoCategoryId=").append(categoryId)
        if (publishedAfter != null) sb.append("&publishedAfter=").append(URLEncoder.encode(publishedAfter, "UTF-8"))
        if (regionCode != null) sb.append("&regionCode=").append(regionCode)
        if (language != null) sb.append("&relevanceLanguage=").append(language)
        if (pageToken != null) sb.append("&pageToken=").append(pageToken)
        sb.append("&key=").append(key)

        val json = JSONObject(get(sb.toString()))
        val arr = json.optJSONArray("items") ?: JSONArray()
        val base = ArrayList<VideoItem>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val id = o.optJSONObject("id")?.optString("videoId").orEmpty()
            if (id.isEmpty()) continue
            val sn = o.getJSONObject("snippet")
            base.add(
                VideoItem(
                    id = id,
                    title = clean(sn.optString("title")),
                    channel = clean(sn.optString("channelTitle")),
                    channelId = sn.optString("channelId"),
                    thumb = thumbUrl(sn).ifEmpty { "https://i.ytimg.com/vi/$id/mqdefault.jpg" },
                    published = sn.optString("publishedAt")
                )
            )
        }
        SearchPage(enrich(base, minSeconds, key), emptyList(), json.optString("nextPageToken").ifEmpty { null })
    }

    suspend fun searchChannels(query: String, pageToken: String?): SearchPage =
        withContext(Dispatchers.IO) {
            val key = requireKey()
            val sb = StringBuilder("$BASE/search?part=snippet&type=channel&maxResults=20")
            sb.append("&q=").append(URLEncoder.encode(query, "UTF-8"))
            if (pageToken != null) sb.append("&pageToken=").append(pageToken)
            sb.append("&key=").append(key)

            val json = JSONObject(get(sb.toString()))
            val arr = json.optJSONArray("items") ?: JSONArray()
            val list = ArrayList<ChannelItem>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.optJSONObject("id")?.optString("channelId").orEmpty()
                if (id.isEmpty()) continue
                val sn = o.getJSONObject("snippet")
                list.add(
                    ChannelItem(
                        id = id,
                        title = clean(sn.optString("title")),
                        avatar = fixUrl(thumbUrl(sn)),
                        description = clean(sn.optString("description"))
                    )
                )
            }
            SearchPage(emptyList(), list, json.optString("nextPageToken").ifEmpty { null })
        }

    suspend fun channelInfo(channelId: String): ChannelInfo = withContext(Dispatchers.IO) {
        val key = requireKey()
        val json = JSONObject(
            get("$BASE/channels?part=snippet,statistics,contentDetails&id=$channelId&key=$key")
        )
        val arr = json.optJSONArray("items")
        if (arr == null || arr.length() == 0) throw ApiException("Kanal tapılmadı")
        val o = arr.getJSONObject(0)
        val sn = o.getJSONObject("snippet")
        val st = o.optJSONObject("statistics")
        val hidden = st?.optBoolean("hiddenSubscriberCount", false) ?: false
        val subs = if (hidden) -1L else (st?.optString("subscriberCount")?.toLongOrNull() ?: -1L)
        val count = st?.optString("videoCount")?.toLongOrNull() ?: -1L
        val uploads = o.optJSONObject("contentDetails")
            ?.optJSONObject("relatedPlaylists")?.optString("uploads").orEmpty()
        if (uploads.isEmpty()) throw ApiException("Kanalın videoları tapılmadı")
        ChannelInfo(
            id = channelId,
            title = clean(sn.optString("title")),
            description = clean(sn.optString("description")),
            avatar = fixUrl(thumbUrl(sn)),
            subscribers = subs,
            videoCount = count,
            uploadsId = uploads
        )
    }

    /** Kanalın yüklədiyi videolar (ən yenidən köhnəyə). Çox ucuz sorğudur. */
    suspend fun channelUploads(
        uploadsId: String,
        pageToken: String?,
        minSeconds: Int,
        maxResults: Int = 50
    ): SearchPage =
        withContext(Dispatchers.IO) {
            val key = requireKey()
            val sb = StringBuilder("$BASE/playlistItems?part=snippet,contentDetails&maxResults=$maxResults")
            sb.append("&playlistId=").append(uploadsId)
            if (pageToken != null) sb.append("&pageToken=").append(pageToken)
            sb.append("&key=").append(key)

            val json = JSONObject(get(sb.toString()))
            val arr = json.optJSONArray("items") ?: JSONArray()
            val base = ArrayList<VideoItem>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val sn = o.optJSONObject("snippet") ?: continue
                val id = sn.optJSONObject("resourceId")?.optString("videoId").orEmpty()
                if (id.isEmpty()) continue
                val pub = o.optJSONObject("contentDetails")?.optString("videoPublishedAt").orEmpty()
                base.add(
                    VideoItem(
                        id = id,
                        title = clean(sn.optString("title")),
                        channel = clean(sn.optString("videoOwnerChannelTitle")),
                        channelId = sn.optString("videoOwnerChannelId"),
                        thumb = thumbUrl(sn).ifEmpty { "https://i.ytimg.com/vi/$id/mqdefault.jpg" },
                        published = pub.ifEmpty { sn.optString("publishedAt") }
                    )
                )
            }
            SearchPage(enrich(base, minSeconds, key), emptyList(), json.optString("nextPageToken").ifEmpty { null })
        }


    /** Bölgədə trend olan videolar (çox ucuz sorğu). */
    suspend fun trending(
        region: String,
        categoryId: String?,
        pageToken: String?,
        minSeconds: Int
    ): SearchPage = withContext(Dispatchers.IO) {
        val key = requireKey()
        val sb = StringBuilder("$BASE/videos?part=snippet,contentDetails,statistics,status&chart=mostPopular&maxResults=50")
        sb.append("&regionCode=").append(region)
        if (categoryId != null) sb.append("&videoCategoryId=").append(categoryId)
        if (pageToken != null) sb.append("&pageToken=").append(pageToken)
        sb.append("&key=").append(key)

        val json = JSONObject(get(sb.toString()))
        val arr = json.optJSONArray("items") ?: JSONArray()
        val out = ArrayList<VideoItem>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val id = o.optString("id")
            if (id.isEmpty()) continue
            val embeddable = o.optJSONObject("status")?.optBoolean("embeddable", true) ?: true
            if (!embeddable) continue
            val sn = o.optJSONObject("snippet") ?: continue
            val d = parseDuration(o.optJSONObject("contentDetails")?.optString("duration").orEmpty())
            if (d <= 0 || d < minSeconds) continue
            out.add(
                VideoItem(
                    id = id,
                    title = clean(sn.optString("title")),
                    channel = clean(sn.optString("channelTitle")),
                    channelId = sn.optString("channelId"),
                    thumb = thumbUrl(sn).ifEmpty { "https://i.ytimg.com/vi/$id/mqdefault.jpg" },
                    durationSec = d,
                    published = sn.optString("publishedAt"),
                    views = o.optJSONObject("statistics")?.optString("viewCount")?.toLongOrNull() ?: -1L
                )
            )
        }
        SearchPage(out, emptyList(), json.optString("nextPageToken").ifEmpty { null })
    }

    suspend fun videoDescription(id: String): String = withContext(Dispatchers.IO) {
        val key = requireKey()
        val arr = JSONObject(get("$BASE/videos?part=snippet&id=$id&key=$key")).optJSONArray("items")
        if (arr == null || arr.length() == 0) {
            ""
        } else {
            arr.getJSONObject(0).getJSONObject("snippet").optString("description")
        }
    }

    /** Axtarış təklifləri (açar və kvota tələb etmir). */
    suspend fun suggestions(q: String): List<String> = withContext(Dispatchers.IO) {
        val body = get(
            "https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&q=" +
                URLEncoder.encode(q, "UTF-8")
        )
        val out = ArrayList<String>()
        val arr = JSONArray(body).optJSONArray(1)
        if (arr != null) {
            for (i in 0 until minOf(arr.length(), 8)) out.add(arr.getString(i))
        }
        out
    }


    /** API açarı ilə bir videonun tam məlumatı. */
    suspend fun videoById(id: String): VideoItem = withContext(Dispatchers.IO) {
        val key = requireKey()
        val arr = JSONObject(get("$BASE/videos?part=snippet,contentDetails,statistics&id=$id&key=$key"))
            .optJSONArray("items")
        if (arr == null || arr.length() == 0) throw ApiException("Video tapılmadı")
        val o = arr.getJSONObject(0)
        val sn = o.getJSONObject("snippet")
        VideoItem(
            id = id,
            title = clean(sn.optString("title")),
            channel = clean(sn.optString("channelTitle")),
            channelId = sn.optString("channelId"),
            thumb = thumbUrl(sn).ifEmpty { "https://i.ytimg.com/vi/$id/mqdefault.jpg" },
            durationSec = parseDuration(o.optJSONObject("contentDetails")?.optString("duration").orEmpty()),
            published = sn.optString("publishedAt"),
            views = o.optJSONObject("statistics")?.optString("viewCount")?.toLongOrNull() ?: -1L
        )
    }

    /** API açarı olmadan: YouTube-un açıq oEmbed ünvanından başlıq və kanal adı. */
    suspend fun videoFromOEmbed(id: String): VideoItem = withContext(Dispatchers.IO) {
        val watch = URLEncoder.encode("https://www.youtube.com/watch?v=$id", "UTF-8")
        val body = try {
            get("https://www.youtube.com/oembed?format=json&url=$watch")
        } catch (e: ApiException) {
            val m = e.message.orEmpty()
            throw ApiException(
                when {
                    m.contains("401") || m.contains("403") -> "Bu video yerləşdirmə üçün açıq deyil"
                    m.contains("404") -> "Video tapılmadı"
                    else -> m.ifEmpty { "Video açıla bilmədi" }
                }
            )
        }
        val o = JSONObject(body)
        val authorUrl = o.optString("author_url")
        val channelId = Regex("/channel/(UC[A-Za-z0-9_-]{22})").find(authorUrl)?.groupValues?.get(1).orEmpty()
        VideoItem(
            id = id,
            title = o.optString("title").ifEmpty { "YouTube video" },
            channel = o.optString("author_name"),
            channelId = channelId,
            thumb = "https://i.ytimg.com/vi/$id/mqdefault.jpg"
        )
    }

    /** Müddət və baxış sayını əlavə edir; Shorts, canlı yayım və silinmiş videoları süzür. */
    private fun enrich(list: List<VideoItem>, minSeconds: Int, key: String): List<VideoItem> {
        if (list.isEmpty()) return list
        val ids = list.joinToString(",") { it.id }
        val arr = JSONObject(get("$BASE/videos?part=contentDetails,statistics&id=$ids&key=$key"))
            .optJSONArray("items") ?: JSONArray()
        val durations = HashMap<String, Int>()
        val views = HashMap<String, Long>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val id = o.getString("id")
            durations[id] = parseDuration(o.getJSONObject("contentDetails").optString("duration"))
            views[id] = o.optJSONObject("statistics")?.optString("viewCount")?.toLongOrNull() ?: -1L
        }
        val result = ArrayList<VideoItem>()
        for (v in list) {
            val d = durations[v.id] ?: continue
            if (d <= 0 || d < minSeconds) continue
            result.add(v.copy(durationSec = d, views = views[v.id] ?: -1L))
        }
        return result
    }

    private fun parseDuration(s: String): Int {
        val m = DURATION.matchEntire(s) ?: return 0
        val (h, mi, se) = m.destructured
        return (h.toIntOrNull() ?: 0) * 3600 + (mi.toIntOrNull() ?: 0) * 60 + (se.toIntOrNull() ?: 0)
    }

    private fun thumbUrl(sn: JSONObject): String {
        val t = sn.optJSONObject("thumbnails") ?: return ""
        for (k in listOf("medium", "high", "default")) {
            val u = t.optJSONObject(k)?.optString("url").orEmpty()
            if (u.isNotEmpty()) return u
        }
        return ""
    }

    private fun fixUrl(u: String): String = if (u.startsWith("//")) "https:$u" else u

    private fun clean(s: String): String =
        Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString()

    /** Açar tələb edən sorğularda limit bitəndə növbəti açara keçir. */
    private fun get(url: String): String {
        if (!url.contains(KEY_PLACEHOLDER)) return rawGet(url)
        val ks = keys
        if (ks.isEmpty()) throw ApiException("API açarı yoxdur.")
        var lastMsg = ""
        for (n in ks.indices) {
            val idx = (active + n) % ks.size
            try {
                val body = rawGet(url.replace(KEY_PLACEHOLDER, ks[idx]))
                active = idx
                return body
            } catch (e: KeyProblem) {
                lastMsg = e.message.orEmpty()
            }
        }
        throw ApiException(
            if (ks.size > 1) {
                "Bütün açarların limiti bitib. Limit Pasifik vaxtı ilə gecə yarısı (Bakı vaxtı ilə təxminən günorta) yenilənir."
            } else {
                "API limiti bitib və ya açar etibarsızdır. " + lastMsg
            }
        )
    }

    private fun rawGet(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        try {
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val err = runCatching { JSONObject(body).getJSONObject("error") }.getOrNull()
                val reason = err?.optJSONArray("errors")?.optJSONObject(0)?.optString("reason").orEmpty()
                val msg = err?.optString("message").orEmpty().ifEmpty { "HTTP xətası: $code" }
                if (reason in KEY_REASONS || msg.contains("API key not valid", ignoreCase = true)) {
                    throw KeyProblem(msg)
                }
                throw ApiException(msg)
            }
            return body
        } finally {
            c.disconnect()
        }
    }
}
