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
import android.util.Base64
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.max

data class RemoteHeartbeatSyncPayload(
    val liveState: LiveState,
    val videoRotateDegrees: Int?,
    val remoteSettingsUpdatedAtMs: Long,
    val videos: List<PlaylistVideo>,
    val allCloudVideos: List<PlaylistVideo>,
    val deletedVideoIds: Set<String>,
    val missingServerVideoIds: Set<String> = emptySet(),
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
        .connectTimeout(6000, TimeUnit.MILLISECONDS)
        .readTimeout(8000, TimeUnit.MILLISECONDS)
        .writeTimeout(8000, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
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
        currentKioskLocked: Boolean = false,
        currentAssignedVideoId: String = "ALL",
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
                        (it.isVerifiedCached || !it.sourceUrl.contains("/api/videos/file/"))
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
                            put("targetDeviceId", "ALL")
                            put("sizeBytes", v.sizeBytes)
                            put("chunkCount", v.chunkCount)
                            put("isVerifiedCached", true)
                            put("sortOrder", v.sortOrder)
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
                put("isOnline", true)
                put("autoPair", true)
                put("zoneId", "zone_lobby")
                put("orientation", currentOrientation)
                put("videoRotateDegrees", currentVideoRotateDegrees)
                put("scaleMode", currentScaleMode)
                put("isMuted", currentIsMuted)
                put("kioskLocked", currentKioskLocked)
                put("assignedVideoId", currentAssignedVideoId)
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
                .url("$cleanBase/api/heartbeat?t=${System.currentTimeMillis()}")
                .header("Cache-Control", "no-cache, no-store")
                .header("Connection", "close")
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            var usedStateFallback = false
            val responseBodyString = httpClient.newCall(heartbeatReq).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string().orEmpty()
                } else {
                    null
                }
            } ?: run {
                usedStateFallback = true
                val fallbackReq = Request.Builder()
                    .url("$cleanBase/api/state?t=${System.currentTimeMillis()}")
                    .header("Cache-Control", "no-cache, no-store")
                    .header("Connection", "close")
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

            val effectivePushCmd = deviceObj.optString(
                "lastPushCommand",
                lsObj.optString("lastPushCommand", "SYNC_OK")
            )

            // Only trust device-specific settingsUpdatedAtMs (or global state timestamp if an explicit GLOBAL_STATE command was sent).
            // Never allow a fallback /api/state response or a cold-started server with timestamp 0 to overwrite this device's settings!
            val deviceSettingsUpdatedAtMs = deviceObj.optLong("settingsUpdatedAtMs", 0L)
            val remoteSettingsUpdatedAtMs = when {
                usedStateFallback -> 0L
                effectivePushCmd.startsWith("GLOBAL_STATE") -> maxOf(
                    deviceSettingsUpdatedAtMs,
                    lsObj.optLong("settingsUpdatedAtMs", 0L)
                )
                else -> deviceSettingsUpdatedAtMs
            }

            val effectiveOrientation = if (!usedStateFallback && deviceObj.has("orientation")) {
                deviceObj.optString("orientation", currentOrientation).ifBlank { currentOrientation }
            } else if (effectivePushCmd.startsWith("GLOBAL_STATE")) {
                lsObj.optString("orientation", lsObj.optString("rotation", currentOrientation)).ifBlank { currentOrientation }
            } else {
                currentOrientation
            }

            val remoteVideoRotateDegrees: Int? = when {
                !usedStateFallback && deviceObj.has("videoRotateDegrees") ->
                    deviceObj.optInt("videoRotateDegrees", currentVideoRotateDegrees)
                effectivePushCmd.startsWith("GLOBAL_STATE") && lsObj.has("videoRotateDegrees") ->
                    lsObj.optInt("videoRotateDegrees", currentVideoRotateDegrees)
                else -> null
            }

            val effectiveScaleMode = if (!usedStateFallback && deviceObj.has("scaleMode")) {
                deviceObj.optString("scaleMode", currentScaleMode).ifBlank { currentScaleMode }
            } else if (effectivePushCmd.startsWith("GLOBAL_STATE")) {
                lsObj.optString("scaleMode", currentScaleMode).ifBlank { currentScaleMode }
            } else {
                currentScaleMode
            }

            val effectiveMuted = when {
                !usedStateFallback && deviceObj.has("isMuted") -> deviceObj.optBoolean("isMuted", currentIsMuted)
                effectivePushCmd.startsWith("GLOBAL_STATE") && lsObj.has("isMuted") -> lsObj.optBoolean("isMuted", currentIsMuted)
                effectivePushCmd.startsWith("GLOBAL_STATE") && lsObj.has("muted") -> lsObj.optBoolean("muted", currentIsMuted)
                else -> currentIsMuted
            }

            val effectiveKioskLocked = when {
                !usedStateFallback && deviceObj.has("kioskLocked") -> deviceObj.optBoolean("kioskLocked", currentKioskLocked)
                effectivePushCmd.startsWith("GLOBAL_STATE") && lsObj.has("kioskLocked") -> lsObj.optBoolean("kioskLocked", currentKioskLocked)
                else -> currentKioskLocked
            }

            val isPaired = true

            val liveState = LiveState(
                userId = "vercel_web_cms",
                orientation = effectiveOrientation,
                scaleMode = effectiveScaleMode,
                isMuted = effectiveMuted,
                kioskLocked = effectiveKioskLocked,
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

            val missingServerSet = mutableSetOf<String>()
            val missingArr = root.optJSONArray("missingServerVideoIds")
            if (missingArr != null) {
                for (i in 0 until missingArr.length()) {
                    val mId = missingArr.optString(i)
                    if (mId.isNotBlank()) missingServerSet.add(mId)
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

            val effectiveAssignedVideoId = if (!usedStateFallback && deviceObj.has("assignedVideoId") && deviceSettingsUpdatedAtMs > 0L) {
                deviceObj.optString("assignedVideoId", currentAssignedVideoId).ifBlank { currentAssignedVideoId }
            } else {
                currentAssignedVideoId
            }

            Result.success(
                RemoteHeartbeatSyncPayload(
                    liveState = liveState,
                    videoRotateDegrees = remoteVideoRotateDegrees,
                    remoteSettingsUpdatedAtMs = remoteSettingsUpdatedAtMs,
                    videos = deviceVideos,
                    allCloudVideos = if (allCloudVideos.isNotEmpty()) allCloudVideos else deviceVideos,
                    deletedVideoIds = remoteDeletedSet,
                    missingServerVideoIds = missingServerSet,
                    otaReleases = parsedReleases,
                    assignedVideoId = effectiveAssignedVideoId,
                    deviceName = if (!usedStateFallback && deviceSettingsUpdatedAtMs > 0L) {
                        deviceObj.optString("deviceName", deviceName).ifBlank { deviceName }
                    } else {
                        deviceName
                    },
                    isPairedWithCms = isPaired
                )
            )
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /**
     * Explicitly pairs & connects this Android TV client with the Web CMS Dashboard using its 6-digit code.
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
        kioskLocked: Boolean = false,
        assignedVideoId: String = "ALL",
        currentVideoTitle: String = "Playing",
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
                put("isOnline", true)
                put("autoPair", true)
                put("currentVideoTitle", currentVideoTitle)
                put("orientation", orientation)
                put("videoRotateDegrees", videoRotateDegrees)
                put("scaleMode", scaleMode)
                put("isMuted", isMuted)
                put("kioskLocked", kioskLocked)
                put("assignedVideoId", assignedVideoId)
                put("settingsUpdatedAtMs", settingsUpdatedAtMs)
            }
            val request = Request.Builder()
                .url("$cleanBase/api/pair?t=${System.currentTimeMillis()}")
                .header("Cache-Control", "no-cache, no-store")
                .header("Connection", "close")
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()
            httpClient.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) Result.success(Unit)
                else Result.failure(IllegalStateException("HTTP ${resp.code}"))
            }
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /**
     * Explicitly notifies the Web CMS Dashboard that this Android TV client disconnected.
     */
    suspend fun disconnectDeviceFromWebCms(
        baseUrl: String,
        deviceId: String,
        pairingCode: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val cleanBase = baseUrl.trim().trimEnd('/')
            val bodyJson = JSONObject().apply {
                put("deviceId", deviceId)
                put("pairingCode", pairingCode)
            }
            val request = Request.Builder()
                .url("$cleanBase/api/disconnect?t=${System.currentTimeMillis()}")
                .header("Cache-Control", "no-cache, no-store")
                .header("Connection", "close")
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()
            httpClient.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) Result.success(Unit)
                else Result.failure(IllegalStateException("HTTP ${resp.code}"))
            }
        } catch (e: Throwable) {
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
                .header("Cache-Control", "no-cache, no-store")
                .header("Connection", "close")
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()
            httpClient.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) Result.success(Unit)
                else Result.failure(IllegalStateException("HTTP ${resp.code}"))
            }
        } catch (e: Throwable) {
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

    /**
     * Re-uploads a locally cached & verified MP4 file from the Android TV back to the Web CMS
     * (/api/upload-chunk with isReseed=true) when a new Vercel container instance or a second
     * device's browser opens the Web CMS Dashboard and needs the video metadata + binary.
     */
    suspend fun reseedLocalVideoToWebCms(
        baseUrl: String,
        video: PlaylistVideo,
        localFile: File
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!localFile.exists() || localFile.length() < 256L) {
                return@withContext Result.failure(IllegalStateException("Local file missing"))
            }
            val cleanBase = baseUrl.trim().trimEnd('/')
            val chunkSize = 3200 * 1024 // 3.2MB atomic chunks (fits within 4MB server limit; avoids multi-chunk splitting)
            val totalBytes = localFile.length()
            val totalChunks = max(1, ceil(totalBytes.toDouble() / chunkSize.toDouble()).toInt())
            val cleanFileId = video.id.removePrefix("vid_").ifBlank { video.id }

            FileInputStream(localFile).use { fis ->
                val buffer = ByteArray(chunkSize)
                for (chunkIdx in 0 until totalChunks) {
                    var bytesInChunk = 0
                    while (bytesInChunk < chunkSize) {
                        val r = fis.read(buffer, bytesInChunk, chunkSize - bytesInChunk)
                        if (r <= 0) break
                        bytesInChunk += r
                    }
                    if (bytesInChunk <= 0) break

                    val b64 = Base64.encodeToString(buffer, 0, bytesInChunk, Base64.NO_WRAP)
                    val bodyJson = JSONObject().apply {
                        put("fileId", cleanFileId)
                        put("videoId", video.id)
                        put("localFileName", video.localFileName)
                        put("fileName", video.localFileName.ifBlank { "${video.id}.mp4" })
                        put("title", video.title)
                        put("zoneId", video.zoneId.ifBlank { "zone_lobby" })
                        put("targetDeviceId", "ALL")
                        put("chunkIndex", chunkIdx)
                        put("totalChunks", totalChunks)
                        put("totalFileSize", totalBytes)
                        put("base64Chunk", b64)
                        put("isReseed", true)
                    }
                    val req = Request.Builder()
                        .url("$cleanBase/api/upload-chunk")
                        .post(bodyJson.toString().toRequestBody(jsonMediaType))
                        .build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            return@withContext Result.failure(IllegalStateException("HTTP ${resp.code}"))
                        }
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
