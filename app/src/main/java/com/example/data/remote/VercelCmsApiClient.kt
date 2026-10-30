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
    val videos: List<PlaylistVideo>,
    val allCloudVideos: List<PlaylistVideo>,
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
        .connectTimeout(2500, TimeUnit.MILLISECONDS)
        .readTimeout(2500, TimeUnit.MILLISECONDS)
        .writeTimeout(2500, TimeUnit.MILLISECONDS)
        .build()

    suspend fun syncHeartbeatWithWebCms(
        baseUrl: String,
        deviceId: String,
        pairingCode: String,
        deviceName: String,
        currentVideoTitle: String,
        cachedVideoCount: Long,
        otaSlotState: OtaSlotState
    ): Result<RemoteHeartbeatSyncPayload> = withContext(Dispatchers.IO) {
        try {
            val cleanBase = baseUrl.trim().trimEnd('/')
            val bodyJson = JSONObject().apply {
                put("deviceId", deviceId)
                put("pairingCode", pairingCode)
                put("code", pairingCode)
                put("deviceName", deviceName)
                put("zoneId", "zone_lobby")
                put("currentVideoTitle", currentVideoTitle)
                put("cachedVideoCount", cachedVideoCount)
                put("installedVersionCode", otaSlotState.installedVersionCode)
                put("installedVersionName", otaSlotState.installedVersionName)
                put("previousSlotVersionCode", otaSlotState.previousSlotVersionCode)
                put("previousSlotVersionName", otaSlotState.previousSlotVersionName)
                put("otaStatus", otaSlotState.otaStatus)
                put("lastOtaMessage", otaSlotState.lastOtaMessage)
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
                // Fallback to GET /api/state if /api/heartbeat is not yet deployed on custom server
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
                lsObj.optString("orientation", lsObj.optString("rotation", "LANDSCAPE"))
            ).ifBlank { "LANDSCAPE" }

            val remoteVideoRotateDegrees: Int? = when {
                deviceObj.has("videoRotateDegrees") -> deviceObj.optInt("videoRotateDegrees", 0)
                lsObj.has("videoRotateDegrees") -> lsObj.optInt("videoRotateDegrees", 0)
                else -> null
            }

            val effectiveScaleMode = deviceObj.optString(
                "scaleMode",
                lsObj.optString("scaleMode", "FILL")
            ).ifBlank { "FILL" }

            val effectiveMuted = when {
                deviceObj.has("isMuted") -> deviceObj.optBoolean("isMuted", false)
                lsObj.has("isMuted") -> lsObj.optBoolean("isMuted", false)
                lsObj.has("muted") -> lsObj.optBoolean("muted", false)
                else -> false
            }

            val isPaired = when {
                deviceObj.has("isPaired") -> deviceObj.optBoolean("isPaired", true)
                deviceObj.has("paired") -> deviceObj.optBoolean("paired", true)
                else -> true
            }

            val liveState = LiveState(
                userId = "vercel_web_cms",
                orientation = effectiveOrientation,
                scaleMode = effectiveScaleMode,
                isMuted = effectiveMuted,
                kioskLocked = lsObj.optBoolean("kioskLocked", false),
                adminPin = lsObj.optString("adminPin", "1234").ifBlank { "1234" },
                activeZoneId = deviceObj.optString("zoneId", lsObj.optString("activeZoneId", "zone_lobby")),
                lastPushCommand = deviceObj.optString(
                    "lastPushCommand",
                    lsObj.optString("lastPushCommand", "SYNC_OK")
                ),
                activeOtaVersionCode = lsObj.optLong("activeOtaVersionCode", 1L),
                activeOtaVersionName = lsObj.optString("activeOtaVersionName", "1.0.0")
            )

            fun parseVideoArray(arr: JSONArray?): List<PlaylistVideo> {
                if (arr == null) return emptyList()
                val list = mutableListOf<PlaylistVideo>()
                for (i in 0 until arr.length()) {
                    val v = arr.optJSONObject(i) ?: continue
                    val rawId = v.optString("id", v.optString("_id", "vid_$i")).ifBlank { "vid_$i" }
                    val rawSourceUrl = v.optString(
                        "sourceUrl",
                        v.optString("url", v.optString("videoUrl", v.optString("src", "")))
                    )
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
                            title = v.optString("title", v.optString("name", "Cloud Video #${i + 1}")),
                            sourceUrl = resolvedSourceUrl,
                            backupUrl = v.optString("backupUrl", ""),
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
                    videos = deviceVideos,
                    allCloudVideos = if (allCloudVideos.isNotEmpty()) allCloudVideos else deviceVideos,
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
        isMuted: Boolean
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
