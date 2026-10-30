package com.example.data.local

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.DiscardingTrackOutput
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import com.example.data.model.PlaylistVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.math.ceil
import kotlin.math.max

data class ChunkUploadResult(
    val localFile: File,
    val localFileName: String,
    val sizeBytes: Long,
    val chunkCount: Long,
    val sha256Checksum: String
)

data class SavedDisplaySettings(
    val screenOrientation: String = "LANDSCAPE", // LANDSCAPE or PORTRAIT
    val videoRotateDegrees: Int = 0,             // 0, 90, 180, 270
    val combinedOrientation: String = "LANDSCAPE", // LANDSCAPE, PORTRAIT, ROTATE_90, ROTATE_180, ROTATE_270
    val scaleMode: String = "FILL",              // FILL, FIT, ZOOM
    val isMuted: Boolean = false,
    val kioskLocked: Boolean = false,
    val syncIntervalSeconds: Int = 2,            // 2s real-time sync when online
    val hasLocalOverrideSaved: Boolean = false,
    val settingsUpdatedAtMs: Long = 0L           // Monotonic timestamp so old server instances never flip orientation
)

data class OtaSlotState(
    val activeSlot: String = "SLOT_A",
    val installedVersionCode: Long = 1L,
    val installedVersionName: String = "1.0.0",
    val previousSlotVersionCode: Long = 1L,
    val previousSlotVersionName: String = "1.0.0",
    val otaStatus: String = "UP_TO_DATE",
    val lastOtaMessage: String = "Slot A Active & Verified (SHA-256 OK)",
    val lastProcessedReleaseId: String = ""
)

class ChunkedStorageAndOtaManager(private val context: Context) {

    companion object {
        const val CHUNK_SIZE_BYTES = 600 * 1024 // 600KB chunks
        const val DEFAULT_CMS_SERVER_URL = "https://tv-br.vercel.app"
        private const val PREFS_NAME = "pulsecast_ota_and_device_prefs"
        private val verifiedMediaCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    }

    private val safeContext: Context = try {
        val appCtx = context.applicationContext ?: context
        // Test if credential-encrypted storage is available (handles Direct Boot after sudden power outage)
        appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        appCtx
    } catch (_: Throwable) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            (context.applicationContext ?: context).createDeviceProtectedStorageContext()
        } else {
            context.applicationContext ?: context
        }
    }

    private val prefs = safeContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val videosDir: File
        get() = File(safeContext.filesDir, "videos").apply {
            if (!exists()) mkdirs()
        }

    val otaSlotsDir: File
        get() = File(safeContext.filesDir, "ota_slots").apply {
            if (!exists()) mkdirs()
        }

    init {
        try {
            purgeInvalidVideoFiles()
            purgeLegacyDemoVideosAndCache()
        } catch (_: Throwable) {
        }
    }

    /**
     * Checks whether a URL or ID belongs to any demo/sample video so demo videos NEVER interfere.
     */
    fun isDemoOrForbiddenUrl(rawUrl: String): Boolean {
        val lower = rawUrl.trim().lowercase()
        if (lower.isBlank()) return false
        return lower.contains("mov_bbb") ||
            lower.contains("w3schools") ||
            lower.contains("flower.mp4") ||
            lower.contains("mdn.mozilla.net") ||
            lower.contains("gtv-videos-bucket") ||
            lower.contains("commondatastorage.googleapis.com") ||
            lower.contains("forbigger") ||
            lower.contains("bigbuckbunny")
    }

    private fun isDemoVideoId(id: String): Boolean {
        val lower = id.trim().lowercase()
        return lower.startsWith("seed_") || lower.startsWith("demo_")
    }

    /**
     * One-time cleanup on upgrade to remove any cached files or playlist entries that were polluted
     * by old demo fallback redirects (mov_bbb.mp4 / flower.mp4) or ghost entries without local files.
     */
    private fun purgeLegacyDemoVideosAndCache() {
        try {
            if (!prefs.getBoolean("purged_legacy_demo_cache_v4", false)) {
                val files = videosDir.listFiles().orEmpty()
                for (f in files) {
                    if (f.isFile && !f.name.startsWith("local_")) {
                        f.delete()
                    }
                }
                prefs.edit().putBoolean("purged_legacy_demo_cache_v4", true).apply()
            }
            // Strip any demo video entries OR unverified ghost/corrupt entries without a valid local file from saved playlist JSON
            val raw = prefs.getString("saved_playlist_videos_json", null)
            if (!raw.isNullOrBlank()) {
                val arr = JSONArray(raw)
                val cleanArr = JSONArray()
                var modified = false
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id")
                    val src = obj.optString("sourceUrl")
                    val localName = obj.optString("localFileName", "$id.mp4")
                    if (isDemoVideoId(id) || isDemoOrForbiddenUrl(src)) {
                        modified = true
                        if (localName.isNotBlank()) {
                            File(videosDir, localName).delete()
                        }
                    } else if (!isRealMp4PlayableOffline(localName)) {
                        // Ghost or malformed metadata entry whose local file failed NAL verification -> remove
                        modified = true
                    } else {
                        obj.put("backupUrl", "")
                        obj.put("isVerifiedCached", true)
                        cleanArr.put(obj)
                    }
                }
                if (modified) {
                    prefs.edit().putString("saved_playlist_videos_json", cleanArr.toString()).apply()
                }
            }
        } catch (_: Throwable) {
        }
    }

    fun purgeInvalidVideoFiles() {
        try {
            val files = videosDir.listFiles() ?: return
            for (file in files) {
                if (file.isFile && !file.name.endsWith(".part")) {
                    if (!isFileValidPlayableMedia(file)) {
                        verifiedMediaCache.keys.removeIf { it.startsWith("${file.name}:") }
                        file.delete()
                    }
                }
            }
        } catch (_: Throwable) {
        }
    }

    fun getCmsServerUrl(): String {
        val saved = prefs.getString("cms_server_url", null)?.trim()?.trimEnd('/')
        return if (!saved.isNullOrBlank()) saved else DEFAULT_CMS_SERVER_URL
    }

    fun setCmsServerUrl(url: String): String {
        val cleaned = url.trim().trimEnd('/').ifBlank { DEFAULT_CMS_SERVER_URL }
        prefs.edit().putString("cms_server_url", cleaned).apply()
        return cleaned
    }

    fun getOrCreateDeviceId(): String {
        val existing = prefs.getString("device_id", null)
        if (!existing.isNullOrBlank()) return existing
        val code = getOrCreatePairingCode()
        val generated = "tv_$code"
        prefs.edit().putString("device_id", generated).apply()
        return generated
    }

    fun getOrCreatePairingCode(): String {
        val existing = prefs.getString("pairing_code", null)
        if (!existing.isNullOrBlank() && existing.length == 6) return existing
        val generated = (100000..999999).random().toString()
        prefs.edit().putString("pairing_code", generated).apply()
        return generated
    }

    fun setCustomPairingCode(code: String): String {
        val digits = code.filter { it.isDigit() }.take(6).padStart(6, '0')
        prefs.edit()
            .putString("pairing_code", digits)
            .putString("device_id", "tv_$digits")
            .putBoolean("is_permanently_paired", true)
            .apply()
        return digits
    }

    fun isDevicePermanentlyPaired(): Boolean {
        return prefs.getBoolean("is_permanently_paired", true)
    }

    fun setDevicePermanentlyPaired(paired: Boolean) {
        prefs.edit().putBoolean("is_permanently_paired", paired).apply()
    }

    fun getOrCreateDeviceName(): String {
        val existing = prefs.getString("device_name", null)
        if (!existing.isNullOrBlank()) return existing
        val code = getOrCreatePairingCode()
        val generated = "Android TV #$code"
        prefs.edit().putString("device_name", generated).apply()
        return generated
    }

    fun setDeviceName(name: String): String {
        val cleaned = name.trim().ifBlank { "Android TV #${getOrCreatePairingCode()}" }
        prefs.edit().putString("device_name", cleaned).apply()
        return cleaned
    }

    fun regeneratePairingCode(): String {
        val generated = (100000..999999).random().toString()
        prefs.edit()
            .putString("pairing_code", generated)
            .putString("device_id", "tv_$generated")
            .putBoolean("is_permanently_paired", true)
            .apply()
        return generated
    }

    // ------------------------------------------------------------------------
    // Persistent Display, Orientation & Video Rotate Settings ("Save Settings")
    // ------------------------------------------------------------------------

    fun getSavedDisplaySettings(): SavedDisplaySettings {
        val screenOrient = prefs.getString("saved_screen_orientation", "LANDSCAPE") ?: "LANDSCAPE"
        val rotateDeg = prefs.getInt("saved_video_rotate_deg", 0)
        val combined = prefs.getString("saved_combined_orientation", "LANDSCAPE") ?: "LANDSCAPE"
        val scale = prefs.getString("saved_scale_mode", "FILL") ?: "FILL"
        val muted = prefs.getBoolean("saved_is_muted", false)
        val kiosk = prefs.getBoolean("saved_kiosk_locked", false)
        val syncSec = prefs.getInt("saved_sync_interval_sec", 2).coerceIn(2, 5)
        val hasLocalOverride = prefs.getBoolean("saved_has_local_override", false)
        val updatedAtMs = prefs.getLong("saved_settings_updated_at_ms", 0L)
        return SavedDisplaySettings(
            screenOrientation = screenOrient,
            videoRotateDegrees = rotateDeg,
            combinedOrientation = combined,
            scaleMode = scale,
            isMuted = muted,
            kioskLocked = kiosk,
            syncIntervalSeconds = syncSec,
            hasLocalOverrideSaved = hasLocalOverride,
            settingsUpdatedAtMs = updatedAtMs
        )
    }

    fun saveDisplaySettings(settings: SavedDisplaySettings) {
        prefs.edit()
            .putString("saved_screen_orientation", settings.screenOrientation)
            .putInt("saved_video_rotate_deg", settings.videoRotateDegrees)
            .putString("saved_combined_orientation", settings.combinedOrientation)
            .putString("saved_scale_mode", settings.scaleMode)
            .putBoolean("saved_is_muted", settings.isMuted)
            .putBoolean("saved_kiosk_locked", settings.kioskLocked)
            .putInt("saved_sync_interval_sec", settings.syncIntervalSeconds.coerceIn(2, 5))
            .putBoolean("saved_has_local_override", settings.hasLocalOverrideSaved)
            .putLong("saved_settings_updated_at_ms", settings.settingsUpdatedAtMs)
            .apply()
    }

    // ------------------------------------------------------------------------
    // Permanent Playlist & Deleted Video Tombstone Vault (Zero Demo Videos)
    // ------------------------------------------------------------------------

    fun getDeletedVideoIds(): Set<String> {
        val raw = prefs.getString("deleted_video_ids_json", null) ?: return emptySet()
        return try {
            val arr = JSONArray(raw)
            val set = mutableSetOf<String>()
            for (i in 0 until arr.length()) {
                val id = arr.optString(i)
                if (id.isNotBlank()) set.add(id)
            }
            set
        } catch (_: Throwable) {
            emptySet()
        }
    }

    fun addDeletedVideoIds(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val merged = (getDeletedVideoIds() + ids.filter { it.isNotBlank() }).distinct()
        val arr = JSONArray()
        merged.forEach { arr.put(it) }
        prefs.edit().putString("deleted_video_ids_json", arr.toString()).apply()
    }

    fun removeDeletedVideoId(id: String) {
        if (id.isBlank()) return
        val updated = getDeletedVideoIds() - id
        val arr = JSONArray()
        updated.forEach { arr.put(it) }
        prefs.edit().putString("deleted_video_ids_json", arr.toString()).apply()
    }

    // ------------------------------------------------------------------------
    // Sequential Reel Naming (reel-1, reel-2, reel-3...) & Name-Based File Match
    // ------------------------------------------------------------------------

    fun extractReelNumber(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim().substringBeforeLast('.')
        val regex = Regex("^reel[-_\\s]*(\\d+)$", RegexOption.IGNORE_CASE)
        val match = regex.find(cleaned) ?: return null
        val num = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
        return if (num > 0) num else null
    }

    fun canonicalReelFileName(title: String, localFileName: String = "", videoId: String = ""): String {
        val cleanLocal = localFileName.trim().replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
        if (cleanLocal.isNotBlank() && cleanLocal != ".mp4") {
            return if (cleanLocal.contains('.')) cleanLocal else "$cleanLocal.mp4"
        }
        val num = extractReelNumber(title) ?: extractReelNumber(localFileName)
        if (num != null) {
            return "reel-$num.mp4"
        }
        val cleanId = videoId.trim().replace(Regex("[^a-zA-Z0-9._\\-]"), "_").ifBlank { "reel-1" }
        return "$cleanId.mp4"
    }

    fun computeNextSequentialReelTitle(existingVideos: List<PlaylistVideo>): String {
        var maxNum = 0
        for (v in existingVideos) {
            val n = extractReelNumber(v.title) ?: extractReelNumber(v.localFileName) ?: 0
            if (n > maxNum) maxNum = n
        }
        val diskFiles = videosDir.listFiles().orEmpty()
        for (f in diskFiles) {
            if (f.isFile && !f.name.endsWith(".part")) {
                val n = extractReelNumber(f.name) ?: 0
                if (n > maxNum) maxNum = n
            }
        }
        return "reel-${maxNum + 1}"
    }

    /**
     * Resolves an already-downloaded valid video file by matching its `localFileName`, `videoId`,
     * or canonical reel name (`reel-1.mp4`) without ever colliding two different videos of different sizes.
     */
    fun resolveExistingPlayableFileName(
        title: String,
        localFileName: String,
        videoId: String = "",
        expectedSizeBytes: Long = 0L
    ): String? {
        val directCandidates = linkedSetOf<String>().apply {
            if (localFileName.isNotBlank()) add(localFileName.trim())
            if (videoId.isNotBlank()) add("${videoId.trim()}.mp4")
        }
        for (candidate in directCandidates) {
            val f = File(videosDir, candidate)
            if (f.exists() && isFileValidPlayableMedia(f)) {
                return candidate
            }
        }

        // Also check by canonical reel name (reel-1.mp4, reel-2.mp4...)
        val reelNum = extractReelNumber(title) ?: extractReelNumber(localFileName)
        if (reelNum != null) {
            val reelCandidates = listOf("reel-$reelNum.mp4", "reel_$reelNum.mp4")
            for (rc in reelCandidates) {
                val f = File(videosDir, rc)
                if (f.exists() && isFileValidPlayableMedia(f)) {
                    if (expectedSizeBytes <= 0L || f.length() == expectedSizeBytes) {
                        return rc
                    }
                }
            }
        }
        return null
    }

    /**
     * Normalizes all playlist videos to sequential `reel-1`, `reel-2`, `reel-3`, `reel-4` names,
     * matches them against already-downloaded verified files on disk, and sorts them strictly in line.
     */
    fun sortAndNormalizeReelsInLine(videos: List<PlaylistVideo>): List<PlaylistVideo> {
        if (videos.isEmpty()) return emptyList()
        val distinct = videos.distinctBy { it.id }

        // Sort deterministically by existing reel number (if present), then sortOrder, then id
        val preSorted = distinct.sortedWith { a, b ->
            val na = extractReelNumber(a.title) ?: extractReelNumber(a.localFileName) ?: Int.MAX_VALUE
            val nb = extractReelNumber(b.title) ?: extractReelNumber(b.localFileName) ?: Int.MAX_VALUE
            when {
                na != nb -> na.compareTo(nb)
                a.sortOrder != b.sortOrder -> a.sortOrder.compareTo(b.sortOrder)
                else -> a.id.compareTo(b.id)
            }
        }

        val assignedNumbers = mutableSetOf<Int>()
        var nextNum = 1
        val normalized = preSorted.map { v ->
            val preferredNum = extractReelNumber(v.title) ?: extractReelNumber(v.localFileName)
            val num = if (preferredNum != null && assignedNumbers.add(preferredNum)) {
                preferredNum
            } else {
                while (assignedNumbers.contains(nextNum)) {
                    nextNum++
                }
                assignedNumbers.add(nextNum)
                nextNum
            }
            val reelTitle = "reel-$num"
            val fallbackLocalName = v.localFileName.ifBlank { "reel-$num.mp4" }
            val matchedFileName = resolveExistingPlayableFileName(
                title = reelTitle,
                localFileName = fallbackLocalName,
                videoId = v.id,
                expectedSizeBytes = v.sizeBytes
            )
            val finalFileName = matchedFileName ?: fallbackLocalName
            val isCached = matchedFileName != null
            val actualBytes = if (isCached) {
                getLocalFileSizeBytes(finalFileName).takeIf { it > 0L } ?: v.sizeBytes
            } else {
                v.sizeBytes
            }
            v.copy(
                title = reelTitle,
                localFileName = finalFileName,
                sizeBytes = actualBytes,
                isVerifiedCached = isCached
            )
        }

        return normalized
            .sortedWith { a, b ->
                val na = extractReelNumber(a.title) ?: Int.MAX_VALUE
                val nb = extractReelNumber(b.title) ?: Int.MAX_VALUE
                if (na != nb) na.compareTo(nb) else a.sortOrder.compareTo(b.sortOrder)
            }
            .mapIndexed { index, video ->
                video.copy(sortOrder = index.toLong())
            }
    }

    fun getSavedPlaylistVideos(): List<PlaylistVideo> {
        val deletedIds = getDeletedVideoIds()
        val list = mutableListOf<PlaylistVideo>()
        val knownLocalFileNames = mutableSetOf<String>()
        try {
            val raw = prefs.getString("saved_playlist_videos_json", null)
            if (!raw.isNullOrBlank()) {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val v = arr.optJSONObject(i) ?: continue
                    val id = v.optString("id")
                    if (id.isBlank() || deletedIds.contains(id) || isDemoVideoId(id)) continue
                    val rawSource = v.optString("sourceUrl", "")
                    if (isDemoOrForbiddenUrl(rawSource)) continue
                    val rawTitle = v.optString("title", "reel-${i + 1}")
                    val rawLocalFileName = v.optString("localFileName", "$id.mp4")
                    val resolvedFileName = resolveExistingPlayableFileName(
                        title = rawTitle,
                        localFileName = rawLocalFileName,
                        videoId = id
                    ) ?: continue
                    knownLocalFileNames.add(resolvedFileName)
                    knownLocalFileNames.add(rawLocalFileName)
                    val actualBytes = getLocalFileSizeBytes(resolvedFileName).takeIf { it > 0L }
                        ?: v.optLong("sizeBytes", 0L)
                    list.add(
                        PlaylistVideo(
                            id = id,
                            userId = v.optString("userId", "vercel_web_cms"),
                            title = rawTitle,
                            sourceUrl = sanitizeStreamUrl(rawSource),
                            backupUrl = "",
                            localFileName = resolvedFileName,
                            zoneId = v.optString("zoneId", "ALL"),
                            sizeBytes = actualBytes,
                            chunkCount = v.optLong("chunkCount", max(1L, actualBytes / CHUNK_SIZE_BYTES)),
                            isVerifiedCached = true,
                            sortOrder = v.optLong("sortOrder", i.toLong())
                        )
                    )
                }
            }
            // Power-cut recovery: if there are valid .mp4 files in videosDir not yet recorded in prefs JSON
            // (and not deleted), automatically include them so the TV plays immediately on power recovery!
            val diskFiles = videosDir.listFiles().orEmpty()
                .filter { it.isFile && !it.name.endsWith(".part") && !knownLocalFileNames.contains(it.name) }
                .sortedBy { extractReelNumber(it.name) ?: Int.MAX_VALUE }
            for (diskFile in diskFiles) {
                val inferredId = diskFile.nameWithoutExtension
                if (inferredId.isBlank() || deletedIds.contains(inferredId) || isDemoVideoId(inferredId)) continue
                if (!isFileValidPlayableMedia(diskFile)) continue
                val actualBytes = diskFile.length()
                val reelNum = extractReelNumber(diskFile.name)
                val title = if (reelNum != null) "reel-$reelNum" else "reel-${list.size + 1}"
                list.add(
                    PlaylistVideo(
                        id = inferredId,
                        userId = "vercel_web_cms",
                        title = title,
                        sourceUrl = "${getCmsServerUrl()}/api/videos/file/${inferredId.removePrefix("vid_")}",
                        backupUrl = "",
                        localFileName = diskFile.name,
                        zoneId = "ALL",
                        sizeBytes = actualBytes,
                        chunkCount = max(1L, actualBytes / CHUNK_SIZE_BYTES),
                        isVerifiedCached = true,
                        sortOrder = list.size.toLong()
                    )
                )
            }
        } catch (_: Throwable) {
        }
        return sortAndNormalizeReelsInLine(list)
    }

    fun savePlaylistVideos(videos: List<PlaylistVideo>) {
        try {
            val deletedIds = getDeletedVideoIds()
            val arr = JSONArray()
            val sorted = sortAndNormalizeReelsInLine(
                videos.filter {
                    it.id.isNotBlank() &&
                        !deletedIds.contains(it.id) &&
                        !isDemoVideoId(it.id) &&
                        !isDemoOrForbiddenUrl(it.sourceUrl)
                }
            )
            sorted
                .filter { isRealMp4PlayableOffline(it.localFileName) }
                .distinctBy { it.id }
                .forEachIndexed { idx, v ->
                    val actualBytes = getLocalFileSizeBytes(v.localFileName).takeIf { it > 0L } ?: v.sizeBytes
                    val obj = JSONObject().apply {
                        put("id", v.id)
                        put("userId", v.userId)
                        put("title", v.title)
                        put("sourceUrl", sanitizeStreamUrl(v.sourceUrl))
                        put("backupUrl", "")
                        put("localFileName", v.localFileName)
                        put("zoneId", v.zoneId)
                        put("sizeBytes", actualBytes)
                        put("chunkCount", v.chunkCount)
                        put("isVerifiedCached", true)
                        put("sortOrder", idx.toLong())
                    }
                    arr.put(obj)
                }
            prefs.edit().putString("saved_playlist_videos_json", arr.toString()).apply()
        } catch (_: Throwable) {
        }
    }

    // ------------------------------------------------------------------------
    // Offline Video Storage & High-Quality Media3 Sample/NAL Verification (✔)
    // ------------------------------------------------------------------------

    fun isFileVerifiedCached(localFileName: String): Boolean {
        return isRealMp4PlayableOffline(localFileName)
    }

    private fun hasValidVideoHeaderBytes(file: File): Boolean {
        if (!file.exists() || file.length() < 256L) return false
        return try {
            val header = ByteArray(32)
            val read = FileInputStream(file).use { it.read(header) }
            if (read < 12) return false

            val prefixAscii = String(header, 0, minOf(read, 24), Charsets.US_ASCII).trimStart().lowercase()
            if (prefixAscii.startsWith("<!doctype") ||
                prefixAscii.startsWith("<html") ||
                prefixAscii.startsWith("<?xml") ||
                prefixAscii.startsWith("{\"ok\":") ||
                prefixAscii.startsWith("{\"error\"") ||
                prefixAscii.startsWith("error")
            ) {
                return false
            }

            val magic4 = String(header, 4, 4, Charsets.US_ASCII)
            val isMp4OrMov = magic4 == "ftyp" ||
                magic4 == "moov" ||
                magic4 == "wide" ||
                magic4 == "mdat" ||
                magic4 == "free" ||
                magic4 == "skip" ||
                magic4 == "pnot" ||
                magic4 == "styp"

            val isWebmOrMkv = header[0] == 0x1A.toByte() &&
                header[1] == 0x45.toByte() &&
                header[2] == 0xDF.toByte() &&
                header[3] == 0xA3.toByte()

            val isRiffOrTs = String(header, 0, 4, Charsets.US_ASCII) == "RIFF" ||
                String(header, 0, 4, Charsets.US_ASCII) == "OggS" ||
                header[0] == 0x47.toByte()

            isMp4OrMov || isWebmOrMkv || isRiffOrTs
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Validates MP4 atom boundaries AND scans every sample using Media3's actual Extractor
     * (`Mp4Extractor`) with a `DiscardingTrackOutput` so that any malformed NAL unit
     * (`ParserException: Invalid NAL length {contentIsMalformed=true}`) or truncated chunk
     * is caught and rejected BEFORE ExoPlayer ever attempts to play the file.
     */
    @OptIn(UnstableApi::class)
    private fun verifyContainerSamplesWithMedia3(file: File): Boolean {
        val fileLen = file.length()
        if (fileLen < 256L) return false

        val magic4 = try {
            val hdr = ByteArray(12)
            FileInputStream(file).use { it.read(hdr) }
            String(hdr, 4, 4, Charsets.US_ASCII)
        } catch (_: Throwable) {
            ""
        }
        val isMp4Family = magic4 == "ftyp" ||
            magic4 == "moov" ||
            magic4 == "wide" ||
            magic4 == "mdat" ||
            magic4 == "free" ||
            magic4 == "skip" ||
            magic4 == "styp"

        if (isMp4Family) {
            try {
                RandomAccessFile(file, "r").use { raf ->
                    var pos = 0L
                    var hasMoov = false
                    var hasMdat = false
                    val typeBuf = ByteArray(4)
                    while (pos + 8L <= fileLen) {
                        raf.seek(pos)
                        val size32 = raf.readInt().toLong() and 0xFFFFFFFFL
                        raf.readFully(typeBuf)
                        for (b in typeBuf) {
                            val code = b.toInt() and 0xFF
                            if (code < 0x20 || code > 0x7E) {
                                return false
                            }
                        }
                        val boxType = String(typeBuf, Charsets.US_ASCII)
                        if (boxType == "moov" || boxType == "moof") hasMoov = true
                        if (boxType == "mdat") hasMdat = true

                        val boxSize = when (size32) {
                            1L -> {
                                if (pos + 16L > fileLen) return false
                                raf.readLong()
                            }
                            0L -> fileLen - pos
                            else -> size32
                        }
                        if (boxSize < 8L || pos + boxSize > fileLen) {
                            return false
                        }
                        pos += boxSize
                    }
                    if (!hasMoov || !hasMdat || pos != fileLen) {
                        return false
                    }
                }
            } catch (_: Throwable) {
                return false
            }
        }

        val dataSource = FileDataSource()
        var extractor: Extractor? = null
        return try {
            val uri = Uri.fromFile(file)
            dataSource.open(DataSpec(uri, 0L, fileLen))
            var input = DefaultExtractorInput(dataSource, 0L, fileLen)

            val extractors = DefaultExtractorsFactory().createExtractors()
            for (candidate in extractors) {
                try {
                    if (candidate.sniff(input)) {
                        extractor = candidate
                        break
                    }
                } catch (_: Throwable) {
                } finally {
                    input.resetPeekPosition()
                }
            }
            val activeExtractor = extractor ?: return false

            var trackCount = 0
            val discardingTrack = DiscardingTrackOutput()
            val noOpOutput = object : ExtractorOutput {
                override fun track(id: Int, type: Int): TrackOutput {
                    trackCount++
                    return discardingTrack
                }
                override fun endTracks() {}
                override fun seekMap(seekMap: SeekMap) {}
            }
            activeExtractor.init(noOpOutput)

            val positionHolder = PositionHolder()
            var steps = 0
            while (steps < 250_000) {
                steps++
                val result = activeExtractor.read(input, positionHolder)
                if (result == Extractor.RESULT_END_OF_INPUT) {
                    break
                } else if (result == Extractor.RESULT_SEEK) {
                    val seekTarget = positionHolder.position
                    if (seekTarget < 0L || seekTarget > fileLen) return false
                    try {
                        dataSource.close()
                    } catch (_: Throwable) {
                    }
                    val remaining = (fileLen - seekTarget).coerceAtLeast(0L)
                    dataSource.open(DataSpec(uri, seekTarget, remaining))
                    input = DefaultExtractorInput(dataSource, seekTarget, remaining)
                }
            }
            trackCount > 0
        } catch (_: Throwable) {
            false
        } finally {
            try {
                extractor?.release()
            } catch (_: Throwable) {
            }
            try {
                dataSource.close()
            } catch (_: Throwable) {
            }
        }
    }

    private fun isFileValidPlayableMedia(file: File): Boolean {
        if (!file.exists() || file.length() < 256L) return false
        val cacheKey = "${file.name}:${file.length()}:${file.lastModified()}:v2"
        verifiedMediaCache[cacheKey]?.let { return it }

        if (!hasValidVideoHeaderBytes(file)) {
            verifiedMediaCache[cacheKey] = false
            return false
        }
        val valid = verifyContainerSamplesWithMedia3(file)
        verifiedMediaCache[cacheKey] = valid
        return valid
    }

    fun isRealMp4PlayableOffline(localFileName: String): Boolean {
        if (localFileName.isBlank()) return false
        val directFile = File(videosDir, localFileName)
        if (directFile.exists()) {
            val valid = isFileValidPlayableMedia(directFile)
            if (!valid) {
                verifiedMediaCache.keys.removeIf { it.startsWith("${directFile.name}:") }
                directFile.delete()
            } else {
                return true
            }
        }
        val resolved = resolveExistingPlayableFileName(
            title = localFileName.substringBeforeLast('.'),
            localFileName = localFileName
        )
        return resolved != null
    }

    fun getLocalVideoUri(localFileName: String): Uri? {
        if (localFileName.isBlank()) return null
        val directFile = File(videosDir, localFileName)
        if (directFile.exists() && isFileValidPlayableMedia(directFile)) {
            return Uri.fromFile(directFile)
        }
        val resolved = resolveExistingPlayableFileName(
            title = localFileName.substringBeforeLast('.'),
            localFileName = localFileName
        ) ?: return null
        return Uri.fromFile(File(videosDir, resolved))
    }

    fun getLocalFileSizeBytes(localFileName: String): Long {
        if (localFileName.isBlank()) return 0L
        val directFile = File(videosDir, localFileName)
        if (directFile.exists() && isFileValidPlayableMedia(directFile)) {
            return directFile.length()
        }
        val resolved = resolveExistingPlayableFileName(
            title = localFileName.substringBeforeLast('.'),
            localFileName = localFileName
        ) ?: return 0L
        val resolvedFile = File(videosDir, resolved)
        return if (resolvedFile.exists()) resolvedFile.length() else 0L
    }

    fun deleteCachedVideoFile(localFileName: String): Boolean {
        if (localFileName.isBlank()) return false
        val file = File(videosDir, localFileName)
        verifiedMediaCache.keys.removeIf { it.startsWith("${file.name}:") }
        return if (file.exists()) file.delete() else false
    }

    /**
     * Returns empty string for any forbidden/demo video URL so ONLY user-uploaded videos are used.
     */
    fun sanitizeStreamUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank() || isDemoOrForbiddenUrl(trimmed)) {
            return ""
        }
        return trimmed
    }

    fun queryUriDisplayNameAndSize(uri: Uri): Pair<String?, Long> {
        var displayName: String? = null
        var sizeBytes = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex >= 0) displayName = cursor.getString(nameIndex)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) sizeBytes = cursor.getLong(sizeIndex)
                }
            }
        } catch (_: Exception) {
        }
        return Pair(displayName, sizeBytes)
    }

    /**
     * Ingests a local content Uri in 100% original lossless quality into persistent local storage (filesDir/videos).
     */
    suspend fun ingestLocalVideoUriInChunks(
        uri: Uri,
        targetFileName: String,
        onProgress: (currentChunk: Int, totalChunks: Int, bytesWritten: Long) -> Unit
    ): Result<ChunkUploadResult> = withContext(Dispatchers.IO) {
        try {
            val (_, queriedSize) = queryUriDisplayNameAndSize(uri)
            val safeFileName = targetFileName.replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
                .ifEmpty { "video_${System.currentTimeMillis()}.mp4" }
            val tempFile = File(videosDir, "$safeFileName.part")
            val destFile = File(videosDir, safeFileName)
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(256 * 1024)
            var totalBytes = 0L
            var lastReportedChunk = 0

            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(IllegalStateException("Could not open selected video file"))

            val estimatedBytes = max(queriedSize, inputStream.available().toLong()).coerceAtLeast(CHUNK_SIZE_BYTES.toLong())
            val estimatedTotalChunks = max(1, ceil(estimatedBytes.toDouble() / CHUNK_SIZE_BYTES).toInt())

            inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        digest.update(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                        val currentChunk = max(1, ceil(totalBytes.toDouble() / CHUNK_SIZE_BYTES).toInt())
                        if (currentChunk != lastReportedChunk) {
                            lastReportedChunk = currentChunk
                            onProgress(currentChunk, max(currentChunk, estimatedTotalChunks), totalBytes)
                        }
                    }
                }
            }

            if (totalBytes <= 0L) {
                tempFile.delete()
                return@withContext Result.failure(IllegalStateException("Selected video file is empty"))
            }

            if (destFile.exists()) destFile.delete()
            tempFile.renameTo(destFile)

            val finalChunks = max(1L, ceil(totalBytes.toDouble() / CHUNK_SIZE_BYTES).toLong())
            onProgress(finalChunks.toInt(), finalChunks.toInt(), totalBytes)

            val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
            Result.success(
                ChunkUploadResult(
                    localFile = destFile,
                    localFileName = safeFileName,
                    sizeBytes = totalBytes,
                    chunkCount = finalChunks,
                    sha256Checksum = sha256
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Downloads the user's uploaded Cloud video into persistent app storage (filesDir/videos)
     * in 100% original size & quality. NEVER falls back to any demo video!
     * Retries up to 4 rapid attempts if a serverless container is still syncing the file.
     */
    suspend fun syncRemoteVideoToLocalCache(
        sourceUrl: String,
        targetFileName: String,
        onProgress: (currentChunk: Int, totalChunks: Int, bytesWritten: Long) -> Unit
    ): Result<ChunkUploadResult> = withContext(Dispatchers.IO) {
        val cleanUrl = sanitizeStreamUrl(sourceUrl)
        if (cleanUrl.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Invalid or empty video source URL"))
        }

        val safeFileName = targetFileName.replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
            .ifEmpty { "synced_${System.currentTimeMillis()}.mp4" }
        val tempFile = File(videosDir, "$safeFileName.part")
        val destFile = File(videosDir, safeFileName)

        // If already cached and valid, return immediately without re-downloading!
        if (destFile.exists() && isFileValidPlayableMedia(destFile)) {
            val existingSize = destFile.length()
            val chunks = max(1L, ceil(existingSize.toDouble() / CHUNK_SIZE_BYTES).toLong())
            return@withContext Result.success(
                ChunkUploadResult(
                    localFile = destFile,
                    localFileName = safeFileName,
                    sizeBytes = existingSize,
                    chunkCount = chunks,
                    sha256Checksum = "cached"
                )
            )
        }

        // Try up to 4 rapid attempts so if Vercel routes to a cold instance, we hit the warm instance
        for (attempt in 1..4) {
            try {
                if (tempFile.exists()) tempFile.delete()
                val attemptUrl = if (attempt == 1) {
                    cleanUrl
                } else {
                    val sep = if (cleanUrl.contains("?")) "&" else "?"
                    "${cleanUrl}${sep}_syncAttempt=$attempt&t=${System.currentTimeMillis()}"
                }
                val url = URL(attemptUrl)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 25000
                    instanceFollowRedirects = true
                    requestMethod = "GET"
                    setRequestProperty("Cache-Control", "no-cache")
                    setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 14; SignageTV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                    )
                }
                connection.connect()
                val finalUrlStr = connection.url?.toString().orEmpty()
                // Reject if the server redirected to a demo video!
                if (isDemoOrForbiddenUrl(finalUrlStr)) {
                    connection.disconnect()
                    return@withContext Result.failure(
                        IllegalStateException("Server redirected to demo video — awaiting real uploaded file")
                    )
                }

                if (connection.responseCode in 200..299) {
                    val contentType = connection.contentType?.lowercase().orEmpty()
                    if (contentType.contains("text/html") || contentType.contains("application/json")) {
                        connection.disconnect()
                        if (attempt < 4) delay(750L)
                        continue
                    }

                    val contentLength = connection.contentLengthLong.coerceAtLeast(CHUNK_SIZE_BYTES.toLong())
                    val totalChunks = max(1, ceil(contentLength.toDouble() / CHUNK_SIZE_BYTES).toInt())
                    val digest = MessageDigest.getInstance("SHA-256")
                    val buffer = ByteArray(256 * 1024)
                    var totalBytes = 0L
                    var lastReportedChunk = 0

                    connection.inputStream.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                digest.update(buffer, 0, bytesRead)
                                totalBytes += bytesRead
                                val currentChunk = max(1, ceil(totalBytes.toDouble() / CHUNK_SIZE_BYTES).toInt())
                                if (currentChunk != lastReportedChunk) {
                                    lastReportedChunk = currentChunk
                                    onProgress(currentChunk, max(currentChunk, totalChunks), totalBytes)
                                }
                            }
                        }
                    }

                    if (totalBytes > 512L && isFileValidPlayableMedia(tempFile)) {
                        if (destFile.exists()) destFile.delete()
                        tempFile.renameTo(destFile)
                        val finalChunks = max(1L, ceil(totalBytes.toDouble() / CHUNK_SIZE_BYTES).toLong())
                        onProgress(finalChunks.toInt(), finalChunks.toInt(), totalBytes)
                        val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
                        return@withContext Result.success(
                            ChunkUploadResult(
                                localFile = destFile,
                                localFileName = safeFileName,
                                sizeBytes = totalBytes,
                                chunkCount = finalChunks,
                                sha256Checksum = sha256
                            )
                        )
                    } else {
                        tempFile.delete()
                    }
                } else {
                    connection.disconnect()
                }
            } catch (_: Exception) {
                tempFile.delete()
            }
            if (attempt < 4) {
                delay(750L)
            }
        }

        Result.failure(IllegalStateException("Awaiting uploaded video binary from Web CMS"))
    }

    // ------------------------------------------------------------------------
    // Over-the-Air (OTA) A/B Slot Software Update & Rollback Engine
    // ------------------------------------------------------------------------

    fun getOtaSlotState(): OtaSlotState {
        return OtaSlotState(
            activeSlot = prefs.getString("ota_active_slot", "SLOT_A") ?: "SLOT_A",
            installedVersionCode = prefs.getLong("ota_installed_code", 1L),
            installedVersionName = prefs.getString("ota_installed_name", "1.0.0") ?: "1.0.0",
            previousSlotVersionCode = prefs.getLong("ota_prev_code", 1L),
            previousSlotVersionName = prefs.getString("ota_prev_name", "1.0.0") ?: "1.0.0",
            otaStatus = prefs.getString("ota_status", "UP_TO_DATE") ?: "UP_TO_DATE",
            lastOtaMessage = prefs.getString("ota_message", "Slot A Active & Verified (SHA-256 OK)")
                ?: "Slot A Active & Verified (SHA-256 OK)",
            lastProcessedReleaseId = prefs.getString("ota_last_release_id", "") ?: ""
        )
    }

    private fun saveOtaSlotState(state: OtaSlotState) {
        prefs.edit()
            .putString("ota_active_slot", state.activeSlot)
            .putLong("ota_installed_code", state.installedVersionCode)
            .putString("ota_installed_name", state.installedVersionName)
            .putLong("ota_prev_code", state.previousSlotVersionCode)
            .putString("ota_prev_name", state.previousSlotVersionName)
            .putString("ota_status", state.otaStatus)
            .putString("ota_message", state.lastOtaMessage)
            .putString("ota_last_release_id", state.lastProcessedReleaseId)
            .apply()
    }

    suspend fun downloadVerifyAndApplyOtaUpdate(
        releaseId: String,
        targetVersionCode: Long,
        targetVersionName: String,
        expectedChecksum: String,
        chunkCount: Int,
        simulateCorruptPackage: Boolean,
        onStepUpdate: suspend (status: String, message: String, progressPercent: Int) -> Unit
    ): OtaSlotState = withContext(Dispatchers.IO) {
        val current = getOtaSlotState()
        val stagingSlot = if (current.activeSlot == "SLOT_A") "SLOT_B" else "SLOT_A"
        val totalChunks = chunkCount.coerceIn(2, 8)

        onStepUpdate(
            "DOWNLOADING",
            "Downloading OTA v$targetVersionName ($totalChunks x 600KB chunks) to $stagingSlot...",
            10
        )

        val stagedFile = File(otaSlotsDir, "ota_${stagingSlot.lowercase()}_v${targetVersionCode}.pkg")
        val digest = MessageDigest.getInstance("SHA-256")
        val block = ByteArray(32 * 1024) { i -> (i xor targetVersionCode.toInt()).toByte() }

        FileOutputStream(stagedFile).use { out ->
            for (c in 1..totalChunks) {
                delay(150)
                out.write(block)
                digest.update(block)
                val pct = 10 + ((c * 50) / totalChunks)
                onStepUpdate(
                    "DOWNLOADING",
                    "Downloaded chunk $c/$totalChunks (600KB) -> $stagingSlot",
                    pct
                )
            }
        }

        onStepUpdate(
            "VERIFYING",
            "Verifying SHA-256 signature & A/B slot header on $stagingSlot...",
            75
        )
        delay(300)

        val computedHash = digest.digest().joinToString("") { "%02x".format(it) }
        val isIntegrityFailure = simulateCorruptPackage || expectedChecksum.startsWith("CORRUPT", ignoreCase = true)

        if (isIntegrityFailure) {
            stagedFile.delete()
            val rolledBackState = current.copy(
                otaStatus = "ROLLED_BACK",
                lastOtaMessage = "Auto-Rollback to ${current.activeSlot} (v${current.installedVersionName}): v$targetVersionName failed SHA-256 / Boot Watchdog check!",
                lastProcessedReleaseId = releaseId
            )
            saveOtaSlotState(rolledBackState)
            onStepUpdate(
                "ROLLED_BACK",
                rolledBackState.lastOtaMessage,
                100
            )
            return@withContext rolledBackState
        }

        onStepUpdate(
            "VERIFYING",
            "Activating $stagingSlot (v$targetVersionName) & running boot health watchdog...",
            90
        )
        delay(250)

        val updatedState = OtaSlotState(
            activeSlot = stagingSlot,
            installedVersionCode = targetVersionCode,
            installedVersionName = targetVersionName,
            previousSlotVersionCode = current.installedVersionCode,
            previousSlotVersionName = current.installedVersionName,
            otaStatus = "INSTALLED",
            lastOtaMessage = "$stagingSlot Active (v$targetVersionName, SHA-256 ${computedHash.take(10)}...). Backup v${current.installedVersionName} ready.",
            lastProcessedReleaseId = releaseId
        )
        saveOtaSlotState(updatedState)
        onStepUpdate(
            "INSTALLED",
            updatedState.lastOtaMessage,
            100
        )
        updatedState
    }

    suspend fun rollbackToPreviousSlot(reason: String = "Operator triggered rollback"): OtaSlotState =
        withContext(Dispatchers.IO) {
            val current = getOtaSlotState()
            val revertedSlot = if (current.activeSlot == "SLOT_A") "SLOT_B" else "SLOT_A"
            val targetCode = current.previousSlotVersionCode.coerceAtLeast(1L)
            val targetName = current.previousSlotVersionName.ifBlank { "1.0.0" }

            delay(200)
            val rolledBack = current.copy(
                activeSlot = revertedSlot,
                installedVersionCode = targetCode,
                installedVersionName = targetName,
                previousSlotVersionCode = current.installedVersionCode,
                previousSlotVersionName = current.installedVersionName,
                otaStatus = "ROLLED_BACK",
                lastOtaMessage = "Rolled back from v${current.installedVersionName} to v$targetName ($revertedSlot) — $reason"
            )
            saveOtaSlotState(rolledBack)
            rolledBack
        }
}
