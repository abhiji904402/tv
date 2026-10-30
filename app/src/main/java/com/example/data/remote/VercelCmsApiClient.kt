package com.example.data.remote

import com.example.data.local.OtaSlotState
import com.example.data.model.LiveState
import com.example.data.model.OtaRelease
import com.example.data.model.PlaylistVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class RemoteHeartbeatSyncPayload(
    val liveState: LiveState,
    val videoRotateDegrees: Int?,
    val remoteSettingsUpdatedAtMs: Long,
    val videos: List<PlaylistVideo>,
    val allCloudVideos: List<PlaylistVideo>,
    val deletedVideoIds: Set<String>,
    val otaReleases: List<OtaRelease>,
    val assignedVideoId: String,
    val deviceName: String,
    val isPairedWithCms: Boolean
)

/**
 * Connects the Android TV & Digital Display Client directly to the Web CMS Dashboard
 * hosted on Vercel (https://tv-br.vercel.app) via real-time 2.0s HTTP polling, pairing & heartbeat.
 */
class VercelCmsApiClient {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(3000, TimeUnit.MILLISECONDS)
        .readTimeout(3000, TimeUnit.MILLISECONDS)
        .writeTimeout(3000, TimeUnit.MILLISECONDS)
        .build()

    private fun isDemoOrForbiddenVideo(id: String, title: String, sourceUrl: String): Boolean {
        val u = sourceUrl.lowercase()
        val t = title.lowercase()
        val i = id.lowercase()
        return u.contains("w3schools.com/html/mov_bbb.mp4") ||
            u.contains("mov_bbb.mp4") ||
            u.contains("mdn.mozilla.net/media/cc0-videos/flower.mp4") ||
            u.contains("flower.mp4") ||
            u.contains("commondatastorage.googleapis.com") ||
            u.contains("forbiggerblazes") ||
            u.contains("forbiggerescapes") ||
            u.contains("bigbuckbunny") ||
            i == "vid_lobby_1" ||
            i == "vid_retail_2" ||
            t.contains("welcome lobby loop") ||
            t.contains("retail promo reel")
    }

    suspend fun syncHeartbeatWithWebCms(
        baseUrl: String,
        deviceId: String,
        pairingCode: String,
        deviceName: String,
        currentOrientation: String,
        currentVideoRotateDegrees: Int,
        currentScaleMode: String,
        currentIsMuted: Boolean,
        settingsUpdatedAtMs: Long,
        currentVideoTitle: String,
        cachedVideoCount: Long,
        otaSlotState: OtaSlotState,
        knownClientVideos: List<PlaylistVideo> = emptyList(),
        deletedVideoIds: Set<String> = emptySet()
    ): Result<RemoteHeartbeatSyncPayload> = withContext(Dispatchers.IO) {
        try {
            val cleanBase = baseUrl.trim().trimEnd('/')

            val clientVideosArr = JSONArray()
            knownClientVideos
                .filter {
                    !it.id.startsWith("local_") &&
                        !deletedVideoIds.contains(it.id) &&
                        !isDemoOrForbiddenVideo(it.id, it.title, it.sourceUrl) &&
                        !it.sourceUrl.contains("/api/videos/file/")
                }
                .forEach { v ->
                    clientVideosArr.put(
                        JSONObject().apply {
                            put("id", v.id)
                            put("title", v.title)
                            put("sourceUrl", v.sourceUrl)
                            put("backupUrl", "")
                            put("localFileName", v.localFileName)
                            put("zoneId", v.zoneId)
                            put("sizeBytes", v.sizeBytes)
                            put("chunkCount", v.chunkCount)
                        }
                    )
                }

            val deletedIdsArr = JSONArray()
            deletedVideoIds.forEach { deletedIdsArr.put(it) }

            val bodyJson = JSONObject().apply {
                put("deviceId", deviceId)
                put("pairingCode", pairingCode)
                put("code", pairingCode)
                put("deviceName", deviceName)
                put("isPaired", true)
                put("paired", true)
                put("zoneId", "zone_lobby")
                put("orientation", currentOrientation)
                put("videoRotateDegrees", currentVideoRotateDegrees)
                put("scaleMode", currentScaleMode)
                put("isMuted", currentIsMuted)
                put("settingsUpdatedAtMs", settingsUpdatedAtMs)
                put("currentVideoTitle", currentVideoTitle)
                put("cachedVideoCount", cachedVideoCount)
                put("installedVersionCode", otaSlotState.installedVersionCode)
                put("installedVersionName", otaSlotState.installedVersionName)
                put("previousSlotVersionCode", otaSlotState.previousSlotVersionCode)
                put("previousSlotVersionName", otaSlotState.previousSlotVersionName)
                put("otaStatus", otaSlotState.otaStatus)
                put("lastOtaMessage", otaSlotState.lastOtaMessage)
                put("clientVideos", clientVideosArr)
                put("deletedVideoIds", deletedIdsArr)
            }

            val heartbeatReq = Request.Builder()
                .url("$cleanBase/api/heartbeat")
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            val responseBodyString = httpClient.newCall(heartbeatReq).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string().orEmpty()
                } else {
                    null
                }
            } ?: run {
                val fallbackReq = Request.Builder()
                    .url("$cleanBase/api/state")
                    .get()
                    .build()
                httpClient.newCall(fallbackReq).execute().use { fallbackResp ->
                    if (!fallbackResp.isSuccessful) {
                        return@withContext Result.failure(
                            IllegalStateException("HTTP ${fallbackResp.code} from $cleanBase")
                        )
                    }
                    fallbackResp.body?.string().orEmpty()
                }
            }

            if (responseBodyString.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Empty response from Web CMS"))
            }

            val root = JSONObject(responseBodyString)
            val lsObj = root.optJSONObject("liveState")
                ?: root.optJSONObject("state")
                ?: root
            val deviceObj = root.optJSONObject("device") ?: JSONObject()

            val effectiveOrientation = deviceObj.optString(
                "orientation",
                lsObj.optString("orientation", lsObj.optString("rotation", currentOrientation))
            ).ifBlank { currentOrientation }

            val remoteVideoRotateDegrees: Int? = when {
                deviceObj.has("videoRotateDegrees") -> deviceObj.optInt("videoRotateDegrees", currentVideoRotateDegrees)
                lsObj.has("videoRotateDegrees") -> lsObj.optInt("videoRotateDegrees", currentVideoRotateDegrees)
                else -> null
            }

            val remoteSettingsUpdatedAtMs = maxOf(
                deviceObj.optLong("settingsUpdatedAtMs", 0L),
                lsObj.optLong("settingsUpdatedAtMs", 0L)
            )

            val effectiveScaleMode = deviceObj.optString(
                "scaleMode",
                lsObj.optString("scaleMode", currentScaleMode)
            ).ifBlank { currentScaleMode }

            val effectiveMuted = when {
                deviceObj.has("isMuted") -> deviceObj.optBoolean("isMuted", currentIsMuted)
                lsObj.has("isMuted") -> lsObj.optBoolean("isMuted", currentIsMuted)
                lsObj.has("muted") -> lsObj.optBoolean("muted", currentIsMuted)
                else -> currentIsMuted
            }

            val isPaired = true

            val effectivePushCmd = deviceObj.optString(
                "lastPushCommand",
                lsObj.optString("lastPushCommand", "SYNC_OK")
            )

            val liveState = LiveState(
                userId = "vercel_web_cms",
                orientation = effectiveOrientation,
                scaleMode = effectiveScaleMode,
                isMuted = effectiveMuted,
                kioskLocked = lsObj.optBoolean("kioskLocked", false),
                adminPin = lsObj.optString("adminPin", "1234").ifBlank { "1234" },
                activeZoneId = deviceObj.optString("zoneId", lsObj.optString("activeZoneId", "zone_lobby")),
                lastPushCommand = effectivePushCmd,
                activeOtaVersionCode = lsObj.optLong("activeOtaVersionCode", 1L),
                activeOtaVersionName = lsObj.optString("activeOtaVersionName", "1.0.0")
            )

            val remoteDeletedSet = mutableSetOf<String>()
            val delArr = root.optJSONArray("deletedVideoIds")
            if (delArr != null) {
                for (i in 0 until delArr.length()) {
                    val dId = delArr.optString(i)
                    if (dId.isNotBlank()) remoteDeletedSet.add(dId)
                }
            }

            fun parseVideoArray(arr: JSONArray?): List<PlaylistVideo> {
                if (arr == null) return emptyList()
                val list = mutableListOf<PlaylistVideo>()
                for (i in 0 until arr.length()) {
                    val v = arr.optJSONObject(i) ?: continue
                    val rawId = v.optString("id", v.optString("_id", "vid_$i")).ifBlank { "vid_$i" }
                    if (remoteDeletedSet.contains(rawId)) continue
                    val title = v.optString("title", v.optString("name", "Uploaded Video #${i + 1}"))
                    val rawSourceUrl = v.optString(
                        "sourceUrl",
                        v.optString("url", v.optString("videoUrl", v.optString("src", "")))
                    )
                    if (isDemoOrForbiddenVideo(rawId, title, rawSourceUrl)) continue
                    val resolvedSourceUrl = if (rawSourceUrl.startsWith("/")) {
                        "$cleanBase$rawSourceUrl"
                    } else {
                        rawSourceUrl
                    }
                    val safeLocalName = v.optString("localFileName", "${rawId}.mp4")
                        .replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
                        .ifBlank { "${rawId}.mp4" }

                    list.add(
                        PlaylistVideo(
                            id = rawId,
                            userId = "vercel_web_cms",
                            title = title,
                            sourceUrl = resolvedSourceUrl,
                            backupUrl = "",
                            localFileName = safeLocalName,
                            zoneId = v.optString("zoneId", "ALL"),
                            sizeBytes = v.optLong("sizeBytes", v.optLong("size", 1843200L)),
                            chunkCount = v.optLong("chunkCount", 3L),
                            isVerifiedCached = v.optBoolean("isVerifiedCached", false),
                            sortOrder = v.optLong("sortOrder", i.toLong())
                        )
                    )
                }
                return list
            }

            val deviceVideos = parseVideoArray(
                root.optJSONArray("videos") ?: root.optJSONArray("playlist")
            )
            val allCloudVideos = parseVideoArray(
                root.optJSONArray("allVideos") ?: root.optJSONArray("videos") ?: root.optJSONArray("playlist")
            )

            val otaArr = root.optJSONArray("otaReleases") ?: JSONArray()
            val parsedReleases = mutableListOf<OtaRelease>()
            for (i in 0 until otaArr.length()) {
                val r = otaArr.optJSONObject(i) ?: continue
                parsedReleases.add(
                    OtaRelease(
                        id = r.optString("id", "ota_$i"),
                        userId = "vercel_web_cms",
                        versionCode = r.optLong("versionCode", 1L),
                        versionName = r.optString("versionName", "1.0.0"),
                        releaseNotes = r.optString("releaseNotes", ""),
                        targetZoneId = r.optString("targetZoneId", "ALL"),
                        packageUrl = r.optString("packageUrl", ""),
                        sha256Checksum = r.optString("sha256Checksum", ""),
                        sizeBytes = r.optLong("sizeBytes", 2457600L),
                        chunkCount = r.optLong("chunkCount", 4L),
                        isMandatory = r.optBoolean("isMandatory", true),
                        simulateCorruptPackage = r.optBoolean("simulateCorruptPackage", false),
                        releaseStatus = r.optString("releaseStatus", "ACTIVE")
                    )
                )
            }

            Result.success(
                RemoteHeartbeatSyncPayload(
                    liveState = liveState,
                    videoRotateDegrees = remoteVideoRotateDegrees,
                    remoteSettingsUpdatedAtMs = remoteSettingsUpdatedAtMs,
                    videos = deviceVideos,
                    allCloudVideos = if (allCloudVideos.isNotEmpty()) allCloudVideos else deviceVideos,
                    deletedVideoIds = remoteDeletedSet,
                    otaReleases = parsedReleases,
                    assignedVideoId = deviceObj.optString("assignedVideoId", "ALL").ifBlank { "ALL" },
                    deviceName = deviceObj.optString("deviceName", deviceName).ifBlank { deviceName },
                    isPairedWithCms = isPaired
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Explicitly pairs this Android TV client with the Web CMS Dashboard using its 6-digit code.
     */
    suspend fun pairDeviceWithWebCms(
        baseUrl: String,
        deviceId: String,
        pairingCode: String,
        deviceName: String,
        orientation: String,
        videoRotateDegrees: Int,
        scaleMode: String,
        isMuted: Boolean,
        settingsUpdatedAtMs: Long = System.currentTimeMillis()
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val cleanBase = baseUrl.trim().trimEnd('/')
            val bodyJson = JSONObject().apply {
                put("deviceId", deviceId)
                put("pairingCode", pairingCode)
                put("code", pairingCode)
                put("deviceName", deviceName)
                put("isPaired", true)
                put("orientation", orientation)
                put("videoRotateDegrees", videoRotateDegrees)
                put("scaleMode", scaleMode)
                put("isMuted", isMuted)
                put("settingsUpdatedAtMs", settingsUpdatedAtMs)
            }
            val request = Request.Builder()
                .url("$cleanBase/api/pair")
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()
            httpClient.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) Result.success(Unit)
                else Result.failure(IllegalStateException("HTTP ${resp.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pushDeviceControlUpdate(
        baseUrl: String,
        deviceId: String,
        pairingCode: String,
        fields: Map<String, Any>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val cleanBase = baseUrl.trim().trimEnd('/')
            val bodyJson = JSONObject().apply {
                put("deviceId", deviceId)
                put("pairingCode", pairingCode)
                fields.forEach { (k, v) -> put(k, v) }
            }
            val request = Request.Builder()
                .url("$cleanBase/api/device-control")
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()
            httpClient.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) Result.success(Unit)
                else Result.failure(IllegalStateException("HTTP ${resp.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateGlobalStateFields(
        baseUrl: String,
        fields: Map<String, Any>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val cleanBase = baseUrl.trim().trimEnd('/')
            val bodyJson = JSONObject()
            fields.forEach { (k, v) -> bodyJson.put(k, v) }
            val request = Request.Builder()
                .url("$cleanBase/api/state")
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()
            httpClient.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) Result.success(Unit)
                else Result.failure(IllegalStateException("HTTP ${resp.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteVideoOnWebCms(
        baseUrl: String,
        videoId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val cleanBase = baseUrl.trim().trimEnd('/')
            val bodyJson = JSONObject().apply {
                put("videoId", videoId)
            }
            val request = Request.Builder()
                .url("$cleanBase/api/videos/delete")
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()
            httpClient.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) Result.success(Unit)
                else Result.failure(IllegalStateException("HTTP ${resp.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
