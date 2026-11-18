package com.cleantube

import org.json.JSONObject

data class VideoItem(
    val id: String,
    val title: String,
    val channel: String,
    val channelId: String,
    val thumb: String,
    val durationSec: Int = 0,
    val published: String = "",
    val views: Long = -1L
)

fun VideoItem.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("title", title)
    .put("channel", channel)
    .put("channelId", channelId)
    .put("thumb", thumb)
    .put("dur", durationSec)
    .put("pub", published)
    .put("views", views)

fun JSONObject.toVideo(): VideoItem = VideoItem(
    id = getString("id"),
    title = optString("title"),
    channel = optString("channel"),
    channelId = optString("channelId"),
    thumb = optString("thumb"),
    durationSec = optInt("dur"),
    published = optString("pub"),
    views = optLong("views", -1L)
)

data class ChannelItem(
    val id: String,
    val title: String,
    val avatar: String,
    val description: String = ""
)

data class ChannelInfo(
    val id: String,
    val title: String,
    val description: String,
    val avatar: String,
    val subscribers: Long,
    val videoCount: Long,
    val uploadsId: String
)

data class SearchPage(
    val videos: List<VideoItem>,
    val channels: List<ChannelItem>,
    val nextToken: String?
)

class ApiException(message: String) : Exception(message)

enum class SortOrder(val api: String, val label: String) {
    RELEVANCE("relevance", "Uyğunluq"),
    DATE("date", "Ən yeni"),
    VIEWS("viewCount", "Ən çox baxılan")
}

enum class SearchType { VIDEO, CHANNEL }

data class FollowedChannel(
    val id: String,
    val title: String,
    val avatar: String,
    val uploadsId: String
)

fun FollowedChannel.toJson(): JSONObject = JSONObject()
    .put("id", id).put("title", title).put("avatar", avatar).put("uploads", uploadsId)

fun JSONObject.toFollowed(): FollowedChannel = FollowedChannel(
    id = getString("id"),
    title = optString("title"),
    avatar = optString("avatar"),
    uploadsId = optString("uploads")
)
