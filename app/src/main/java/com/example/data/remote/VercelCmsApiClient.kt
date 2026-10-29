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
    val videos: List<PlaylistVideo>,
    val otaReleases: List<OtaRelease>,
    val assignedVideoId: String,
    val deviceName: String
)

/**
 * Connects the Android TV & Digital Display Client directly to the Web CMS Dashboard
 * hosted on Vercel (https://tv-br.vercel.app) via real-time 2.5s HTTP polling & heartbeat.
 */
class VercelCmsApiClient {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(3500, TimeUnit.MILLISECONDS)
        .readTimeout(3500, TimeUnit.MILLISECONDS)
        .writeTimeout(3500, TimeUnit.MILLISECONDS)
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

            val request = Request.Builder()
                .url("$cleanBase/api/heartbeat")
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("HTTP ${response.code} from $cleanBase/api/heartbeat")
                    )
                }
                val rawBody = response.body?.string().orEmpty()
                val root = JSONObject(rawBody)
                if (!root.optBoolean("ok", false)) {
                    return@withContext Result.failure(
                        IllegalStateException("Invalid response from Web CMS")
                    )
                }

                val lsObj = root.optJSONObject("liveState") ?: JSONObject()
                val deviceObj = root.optJSONObject("device") ?: JSONObject()

                val effectiveOrientation = deviceObj.optString(
                    "orientation",
                    lsObj.optString("orientation", "LANDSCAPE")
                ).ifBlank { "LANDSCAPE" }

                val effectiveScaleMode = deviceObj.optString(
                    "scaleMode",
                    lsObj.optString("scaleMode", "FILL")
                ).ifBlank { "FILL" }

                val effectiveMuted = if (deviceObj.has("isMuted")) {
                    deviceObj.optBoolean("isMuted", false)
                } else {
                    lsObj.optBoolean("isMuted", false)
                }

                val liveState = LiveState(
                    userId = "vercel_web_cms",
                    orientation = effectiveOrientation,
                    scaleMode = effectiveScaleMode,
                    isMuted = effectiveMuted,
                    kioskLocked = lsObj.optBoolean("kioskLocked", true),
                    adminPin = lsObj.optString("adminPin", "1234").ifBlank { "1234" },
                    activeZoneId = deviceObj.optString("zoneId", lsObj.optString("activeZoneId", "zone_lobby")),
                    lastPushCommand = lsObj.optString("lastPushCommand", "SYNC_OK"),
                    activeOtaVersionCode = lsObj.optLong("activeOtaVersionCode", 1L),
                    activeOtaVersionName = lsObj.optString("activeOtaVersionName", "1.0.0")
                )

                val videosArr = root.optJSONArray("videos") ?: JSONArray()
                val parsedVideos = mutableListOf<PlaylistVideo>()
                for (i in 0 until videosArr.length()) {
                    val v = videosArr.optJSONObject(i) ?: continue
                    val rawSourceUrl = v.optString("sourceUrl", "")
                    val resolvedSourceUrl = if (rawSourceUrl.startsWith("/")) {
                        "$cleanBase$rawSourceUrl"
                    } else {
                        rawSourceUrl
                    }
                    parsedVideos.add(
                        PlaylistVideo(
                            id = v.optString("id", "vid_$i"),
                            userId = "vercel_web_cms",
                            title = v.optString("title", "Broadcast Spot #${i + 1}"),
                            sourceUrl = resolvedSourceUrl,
                            backupUrl = v.optString("backupUrl", ""),
                            localFileName = v.optString("localFileName", "${v.optString("id", "vid_$i")}.mp4"),
                            zoneId = v.optString("zoneId", "ALL"),
                            sizeBytes = v.optLong("sizeBytes", 1843200L),
                            chunkCount = v.optLong("chunkCount", 3L),
                            isVerifiedCached = v.optBoolean("isVerifiedCached", true),
                            sortOrder = v.optLong("sortOrder", i.toLong())
                        )
                    )
                }

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
                        videos = parsedVideos,
                        otaReleases = parsedReleases,
                        assignedVideoId = deviceObj.optString("assignedVideoId", "ALL").ifBlank { "ALL" },
                        deviceName = deviceObj.optString("deviceName", deviceName).ifBlank { deviceName }
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pushDeviceControlUpdate(
        baseUrl: String,
        deviceId: String,
        fields: Map<String, Any>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val cleanBase = baseUrl.trim().trimEnd('/')
            val bodyJson = JSONObject().apply {
                put("deviceId", deviceId)
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
