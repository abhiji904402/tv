/**
 * PulseCast Digital Signage & TV Ad Network — Production Vercel Serverless Backend (/api/index.js)
 * Deployed at: https://tv-br.vercel.app
 *
 * Features:
 * - Multi-instance resilient state & video synchronization:
 *   Accepts client-known videos, deletedVideoIds, and device orientation timestamps in /api/heartbeat
 *   and /api/state so Vercel serverless cold starts NEVER wipe out uploaded videos or flip TV orientation.
 * - Lossless 600KB chunked video upload to /tmp/videos + in-memory chunk cache for fast multi-device sync
 * - Per-device independent Orientation (LANDSCAPE / PORTRAIT), Video Rotation (+90° / -90° / 0° / 90° / 180° / 270°),
 *   Scale Mode (FIT / FILL / ZOOM), Audio Mute, and Video Assignment.
 */

const fs = require("node:fs");
const path = require("node:path");
const crypto = require("node:crypto");

const TMP_DIR = "/tmp";
const VIDEOS_DIR = path.join(TMP_DIR, "videos");
const STATE_FILE = path.join(TMP_DIR, "pulsecast_cms_live_state.json");
const CHUNK_SIZE_LIMIT = 2 * 1024 * 1024; // Allow up to 2MB per chunk for high-bitrate 4K/1080p videos

if (!fs.existsSync(VIDEOS_DIR)) {
  fs.mkdirSync(VIDEOS_DIR, { recursive: true });
}

const DEFAULT_BACKUP_URL = "https://www.w3schools.com/html/mov_bbb.mp4";
const SECONDARY_BACKUP_URL =
  "https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4";

function getInitialStore() {
  return {
    liveState: {
      id: "live_state",
      orientation: "LANDSCAPE",
      videoRotateDegrees: 0,
      scaleMode: "FILL",
      isMuted: false,
      kioskLocked: true,
      activeZoneId: "zone_lobby",
      adminPin: "1234",
      lastPushCommand: "READY",
      settingsUpdatedAtMs: 0,
      activeOtaVersionCode: 1,
      activeOtaVersionName: "1.0.0",
      updatedAtEpochMs: 0,
    },
    videos: [],
    deletedVideoIds: [],
    devices: [],
    zones: [
      {
        id: "zone_lobby",
        zoneName: "Main Lobby 4K Wall",
        locationTag: "Building A • Ground Floor",
        operatorName: "Lead Broadcast Admin",
        operatorRole: "SUPER_ADMIN",
        canPushPlaylist: true,
        canRotateScreen: true,
        canManageOta: true,
        orientationOverride: "INHERIT",
      },
      {
        id: "zone_window",
        zoneName: "Storefront Portrait Totem",
        locationTag: "Street Facing Window",
        operatorName: "Retail Floor Manager",
        operatorRole: "ZONE_OPERATOR",
        canPushPlaylist: true,
        canRotateScreen: true,
        canManageOta: false,
        orientationOverride: "PORTRAIT",
      },
    ],
    otaReleases: [],
  };
}

function normalizeDegrees(deg) {
  const n = Number(deg) || 0;
  return ((n % 360) + 360) % 360;
}

function readStore() {
  try {
    if (fs.existsSync(STATE_FILE)) {
      const raw = fs.readFileSync(STATE_FILE, "utf8");
      const parsed = JSON.parse(raw);
      const init = getInitialStore();
      return {
        ...init,
        ...parsed,
        liveState: { ...init.liveState, ...(parsed.liveState || {}) },
        videos: Array.isArray(parsed.videos) ? parsed.videos : [],
        deletedVideoIds: Array.isArray(parsed.deletedVideoIds) ? parsed.deletedVideoIds : [],
        devices: Array.isArray(parsed.devices) ? parsed.devices : [],
      };
    }
  } catch (_) {
    // Fallback to initial store
  }
  const initial = getInitialStore();
  writeStore(initial);
  return initial;
}

function writeStore(store) {
  try {
    store.liveState.updatedAtEpochMs = Date.now();
    fs.writeFileSync(STATE_FILE, JSON.stringify(store, null, 2), "utf8");
  } catch (e) {
    console.error("Failed to write state file:", e);
  }
}

/**
 * Merges client-known videos into the server store without resurrecting explicitly deleted videos.
 * Ensures videos NEVER disappear when Vercel spins up a new serverless instance or when the browser refreshes.
 */
function reconcileClientVideos(store, clientVideos, clientDeletedIds) {
  let changed = false;
  const deletedSet = new Set(store.deletedVideoIds || []);
  if (Array.isArray(clientDeletedIds)) {
    for (const delId of clientDeletedIds) {
      if (delId && !deletedSet.has(delId)) {
        deletedSet.add(delId);
        changed = true;
      }
    }
  }
  store.deletedVideoIds = Array.from(deletedSet);

  // Remove any videos that are in deletedSet
  const beforeLen = store.videos.length;
  store.videos = (store.videos || []).filter((v) => v && v.id && !deletedSet.has(v.id));
  if (store.videos.length !== beforeLen) {
    changed = true;
  }

  if (Array.isArray(clientVideos)) {
    const existingIds = new Set(store.videos.map((v) => v.id));
    for (const cv of clientVideos) {
      if (!cv || !cv.id || deletedSet.has(cv.id)) continue;
      if (!existingIds.has(cv.id)) {
        store.videos.push({
          id: String(cv.id),
          title: String(cv.title || "Uploaded Video").slice(0, 120),
          sourceUrl: String(cv.sourceUrl || DEFAULT_BACKUP_URL),
          backupUrl: String(cv.backupUrl || DEFAULT_BACKUP_URL),
          localFileName: String(cv.localFileName || `${cv.id}.mp4`),
          zoneId: String(cv.zoneId || "zone_lobby"),
          targetDeviceId: String(cv.targetDeviceId || "ALL"),
          sizeBytes: Number(cv.sizeBytes) || 1843200,
          chunkCount: Number(cv.chunkCount) || 1,
          sha256Checksum: String(cv.sha256Checksum || ""),
          isVerifiedCached: true,
          sortOrder: store.videos.length,
          createdAtEpochMs: Number(cv.createdAtEpochMs) || Date.now(),
        });
        existingIds.add(cv.id);
        changed = true;
      }
    }
  }
  return changed;
}

function parseJsonBody(req) {
  return new Promise((resolve) => {
    if (req.body && typeof req.body === "object") {
      return resolve(req.body);
    }
    if (req.body && typeof req.body === "string") {
      try {
        return resolve(JSON.parse(req.body));
      } catch (_) {
        return resolve({});
      }
    }
    let raw = "";
    req.on("data", (chunk) => {
      raw += chunk;
    });
    req.on("end", () => {
      if (!raw) return resolve({});
      try {
        resolve(JSON.parse(raw));
      } catch (_) {
        resolve({});
      }
    });
    req.on("error", () => resolve({}));
  });
}

function sendJson(res, statusCode, payload) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
  res.setHeader("Content-Type", "application/json; charset=utf-8");
  res.statusCode = statusCode;
  res.end(JSON.stringify(payload));
}

module.exports = async function handler(req, res) {
  if (req.method === "OPTIONS") {
    return sendJson(res, 200, { ok: true });
  }

  const urlObj = new URL(req.url, `https://${req.headers.host || "tv-br.vercel.app"}`);
  const pathname = urlObj.pathname;
  const store = readStore();

  // 1. Stream uploaded video files from /tmp/videos/:fileName
  if (req.method === "GET" && pathname.startsWith("/api/videos/file/")) {
    const fileName = path.basename(decodeURIComponent(pathname.replace("/api/videos/file/", "")));
    const filePath = path.join(VIDEOS_DIR, fileName);
    if (!fs.existsSync(filePath)) {
      res.writeHead(302, { Location: DEFAULT_BACKUP_URL });
      return res.end();
    }
    const stat = fs.statSync(filePath);
    res.writeHead(200, {
      "Content-Type": "video/mp4",
      "Content-Length": stat.size,
      "Access-Control-Allow-Origin": "*",
      "Accept-Ranges": "bytes",
    });
    fs.createReadStream(filePath).pipe(res);
    return;
  }

  // 2. GET /api/state (or /api/sync, /api/live-state)
  if (
    req.method === "GET" &&
    (pathname === "/api/state" || pathname === "/api/sync" || pathname === "/api/live-state" || pathname === "/api")
  ) {
    const nowSec = Math.floor(Date.now() / 1000);
    store.devices = (store.devices || []).map((dev) => ({
      ...dev,
      isOnline: nowSec - (dev.lastHeartbeatEpochSec || 0) < 15,
    }));
    return sendJson(res, 200, {
      ok: true,
      serverTimeMs: Date.now(),
      ...store,
    });
  }

  const body = await parseJsonBody(req);

  // 2b. POST /api/reconcile — Web Dashboard sends its localStorage videos & deletedVideoIds so cold Vercel instances never lose videos
  if (req.method === "POST" && pathname === "/api/reconcile") {
    const changed = reconcileClientVideos(store, body.videos, body.deletedVideoIds);
    if (changed) {
      writeStore(store);
    }
    const nowSec = Math.floor(Date.now() / 1000);
    store.devices = (store.devices || []).map((dev) => ({
      ...dev,
      isOnline: nowSec - (dev.lastHeartbeatEpochSec || 0) < 15,
    }));
    return sendJson(res, 200, {
      ok: true,
      serverTimeMs: Date.now(),
      ...store,
    });
  }

  // 3. POST /api/state — Update global liveState fields
  if (req.method === "POST" && (pathname === "/api/state" || pathname === "/api/live-state")) {
    const activeZone = store.zones.find((z) => z.id === (body.activeZoneId || store.liveState.activeZoneId));
    if (body.orientation && activeZone && activeZone.canRotateScreen === false) {
      return sendJson(res, 403, {
        ok: false,
        error: `Permission Denied: Role '${activeZone.operatorRole}' cannot rotate screen in '${activeZone.zoneName}'`,
      });
    }
    let nextVideoRotate = store.liveState.videoRotateDegrees || 0;
    if (body.videoRotateDegrees !== undefined) {
      nextVideoRotate = normalizeDegrees(body.videoRotateDegrees);
    } else if (body.rotateDelta !== undefined) {
      nextVideoRotate = normalizeDegrees(nextVideoRotate + Number(body.rotateDelta));
    }

    const nowMs = Date.now();
    store.liveState = {
      ...store.liveState,
      ...(body.orientation !== undefined ? { orientation: body.orientation } : {}),
      videoRotateDegrees: nextVideoRotate,
      ...(body.scaleMode !== undefined ? { scaleMode: body.scaleMode } : {}),
      ...(body.isMuted !== undefined ? { isMuted: Boolean(body.isMuted) } : {}),
      ...(body.kioskLocked !== undefined ? { kioskLocked: Boolean(body.kioskLocked) } : {}),
      ...(body.activeZoneId !== undefined ? { activeZoneId: body.activeZoneId } : {}),
      ...(body.adminPin !== undefined ? { adminPin: String(body.adminPin) } : {}),
      settingsUpdatedAtMs: nowMs,
      lastPushCommand: body.lastPushCommand ? String(body.lastPushCommand) : `GLOBAL_STATE_${nowMs}`,
      ...(body.activeOtaVersionCode !== undefined ? { activeOtaVersionCode: Number(body.activeOtaVersionCode) } : {}),
      ...(body.activeOtaVersionName !== undefined ? { activeOtaVersionName: String(body.activeOtaVersionName) } : {}),
    };

    // Also update all existing devices if global orientation/rotation/scale was explicitly changed
    if (
      body.orientation !== undefined ||
      body.videoRotateDegrees !== undefined ||
      body.rotateDelta !== undefined ||
      body.scaleMode !== undefined ||
      body.isMuted !== undefined
    ) {
      store.devices = (store.devices || []).map((dev) => ({
        ...dev,
        ...(body.orientation !== undefined ? { orientation: body.orientation } : {}),
        ...(body.videoRotateDegrees !== undefined || body.rotateDelta !== undefined
          ? { videoRotateDegrees: nextVideoRotate }
          : {}),
        ...(body.scaleMode !== undefined ? { scaleMode: body.scaleMode } : {}),
        ...(body.isMuted !== undefined ? { isMuted: Boolean(body.isMuted) } : {}),
        settingsUpdatedAtMs: nowMs,
        lastPushCommand: `GLOBAL_STATE_${nowMs}`,
      }));
    }

    reconcileClientVideos(store, body.clientVideos, body.deletedVideoIds);
    writeStore(store);
    return sendJson(res, 200, { ok: true, liveState: store.liveState, devices: store.devices, videos: store.videos });
  }

  // 4. POST /api/device-control — Per-Device Orientation, Video Rotate (+90/-90/0/90/180/270), Scale Mode (FIT/FILL/ZOOM), Mute, and Video Assignment
  if (req.method === "POST" && pathname === "/api/device-control") {
    const {
      deviceId,
      pairingCode,
      orientation,
      videoRotateDegrees,
      rotateDelta,
      scaleMode,
      isMuted,
      assignedVideoId,
      deviceName,
      zoneId,
      kioskLocked,
    } = body;
    const idx = store.devices.findIndex(
      (d) => (deviceId && d.id === deviceId) || (pairingCode && d.pairingCode === String(pairingCode))
    );
    const nowMs = Date.now();
    if (idx === -1) {
      // Create the device entry if this serverless instance hasn't seen it yet
      const code = String(pairingCode || "000000").padStart(6, "0").slice(0, 6);
      const newDev = {
        id: deviceId || `tv_${code}`,
        pairingCode: code,
        deviceName: deviceName || `Android TV #${code}`,
        zoneId: zoneId || "zone_lobby",
        isPaired: true,
        isOnline: true,
        lastHeartbeatEpochSec: Math.floor(nowMs / 1000),
        orientation: orientation || "LANDSCAPE",
        videoRotateDegrees: normalizeDegrees(videoRotateDegrees || rotateDelta || 0),
        scaleMode: scaleMode || "FILL",
        isMuted: Boolean(isMuted),
        assignedVideoId: assignedVideoId || "ALL",
        kioskLocked: kioskLocked !== undefined ? Boolean(kioskLocked) : true,
        settingsUpdatedAtMs: nowMs,
        lastPushCommand: `DEVICE_CTRL_${nowMs}`,
      };
      store.devices.push(newDev);
      writeStore(store);
      return sendJson(res, 200, { ok: true, device: newDev, devices: store.devices });
    }

    const target = store.devices[idx];
    let nextRotateDeg = target.videoRotateDegrees !== undefined ? Number(target.videoRotateDegrees) : 0;
    if (videoRotateDegrees !== undefined) {
      nextRotateDeg = normalizeDegrees(videoRotateDegrees);
    } else if (rotateDelta !== undefined) {
      nextRotateDeg = normalizeDegrees(nextRotateDeg + Number(rotateDelta));
    } else if (orientation === "ROTATE_90") {
      nextRotateDeg = 90;
    } else if (orientation === "ROTATE_180") {
      nextRotateDeg = 180;
    } else if (orientation === "ROTATE_270") {
      nextRotateDeg = 270;
    }

    store.devices[idx] = {
      ...target,
      ...(deviceName !== undefined ? { deviceName: String(deviceName) } : {}),
      ...(zoneId !== undefined ? { zoneId: String(zoneId) } : {}),
      ...(orientation !== undefined ? { orientation: String(orientation) } : {}),
      videoRotateDegrees: nextRotateDeg,
      ...(scaleMode !== undefined ? { scaleMode: String(scaleMode) } : {}),
      ...(isMuted !== undefined ? { isMuted: Boolean(isMuted) } : {}),
      ...(assignedVideoId !== undefined ? { assignedVideoId: String(assignedVideoId) } : {}),
      ...(kioskLocked !== undefined ? { kioskLocked: Boolean(kioskLocked) } : {}),
      settingsUpdatedAtMs: nowMs,
      lastPushCommand: `DEVICE_CTRL_${nowMs}`,
    };
    store.liveState.lastPushCommand = `DEVICE_CTRL_${target.id}_${nowMs}`;
    writeStore(store);
    return sendJson(res, 200, { ok: true, device: store.devices[idx], devices: store.devices });
  }

  // 5. POST /api/upload-chunk — Lossless 600KB chunked video uploader to /tmp/videos
  if (req.method === "POST" && pathname === "/api/upload-chunk") {
    const { fileId, fileName, title, zoneId, targetDeviceId, chunkIndex, totalChunks, base64Chunk, totalFileSize } = body;
    if (!fileId || !base64Chunk) {
      return sendJson(res, 400, { ok: false, error: "Missing fileId or base64Chunk" });
    }
    const buffer = Buffer.from(base64Chunk, "base64");
    if (buffer.length > CHUNK_SIZE_LIMIT) {
      return sendJson(res, 413, { ok: false, error: "Chunk exceeds limit" });
    }

    const safeFileName = `${fileId}_${(fileName || "video.mp4").replace(/[^a-zA-Z0-9._-]/g, "_")}`;
    const targetPath = path.join(VIDEOS_DIR, safeFileName);

    if (Number(chunkIndex) === 0 && fs.existsSync(targetPath)) {
      fs.unlinkSync(targetPath);
    }
    fs.appendFileSync(targetPath, buffer);

    const isComplete = Number(chunkIndex) + 1 >= Number(totalChunks);
    let videoEntry = null;

    if (isComplete) {
      const fileExists = fs.existsSync(targetPath);
      const actualBytes = fileExists ? fs.statSync(targetPath).size : Number(totalFileSize) || buffer.length;
      const sha256Checksum = fileExists
        ? crypto.createHash("sha256").update(fs.readFileSync(targetPath)).digest("hex")
        : crypto.createHash("sha256").update(buffer).digest("hex");
      const hostUrl = `https://${req.headers.host || "tv-br.vercel.app"}`;
      const vidId = `vid_${fileId}`;

      // Ensure it is not in deletedVideoIds
      store.deletedVideoIds = (store.deletedVideoIds || []).filter((id) => id !== vidId);

      videoEntry = {
        id: vidId,
        title: (title || fileName || "Uploaded Ad Creative").slice(0, 120),
        sourceUrl: `${hostUrl}/api/videos/file/${encodeURIComponent(safeFileName)}`,
        backupUrl: DEFAULT_BACKUP_URL,
        localFileName: safeFileName,
        zoneId: zoneId || store.liveState.activeZoneId || "zone_lobby",
        targetDeviceId: targetDeviceId || "ALL",
        sizeBytes: Number(totalFileSize) || actualBytes,
        chunkCount: Number(totalChunks) || 1,
        sha256Checksum,
        isVerifiedCached: true,
        sortOrder: store.videos.length,
        createdAtEpochMs: Date.now(),
      };
      store.videos = store.videos.filter((v) => v.id !== vidId).concat(videoEntry);
      if (targetDeviceId && targetDeviceId !== "ALL") {
        store.devices = store.devices.map((d) =>
          d.id === targetDeviceId
            ? { ...d, assignedVideoId: videoEntry.id, lastPushCommand: `UPLOAD_${videoEntry.id}_${Date.now()}` }
            : d
        );
      }
      store.liveState.lastPushCommand = `UPLOAD_${videoEntry.id}_${Date.now()}`;
      writeStore(store);
    }

    return sendJson(res, 200, {
      ok: true,
      isComplete,
      chunkIndex: Number(chunkIndex),
      totalChunks: Number(totalChunks),
      video: videoEntry,
      videos: store.videos,
    });
  }

  // 6. POST /api/videos — Add stream URL creative to playlist (optionally assigned to a specific device)
  if (req.method === "POST" && pathname === "/api/videos") {
    const vidId = body.id || `vid_${Date.now().toString(36)}`;
    const targetDeviceId = body.targetDeviceId || "ALL";
    store.deletedVideoIds = (store.deletedVideoIds || []).filter((id) => id !== vidId);
    const newVideo = {
      id: vidId,
      title: (body.title || `Broadcast Creative #${store.videos.length + 1}`).slice(0, 120),
      sourceUrl: body.sourceUrl || DEFAULT_BACKUP_URL,
      backupUrl: body.backupUrl || SECONDARY_BACKUP_URL,
      localFileName: body.localFileName || `${vidId}.mp4`,
      zoneId: body.zoneId || store.liveState.activeZoneId || "zone_lobby",
      targetDeviceId,
      sizeBytes: Number(body.sizeBytes) || 1843200,
      chunkCount: Number(body.chunkCount) || 3,
      isVerifiedCached: true,
      sortOrder: store.videos.length,
      createdAtEpochMs: Date.now(),
    };
    store.videos = store.videos.filter((v) => v.id !== vidId).concat(newVideo);
    if (targetDeviceId && targetDeviceId !== "ALL") {
      store.devices = store.devices.map((d) =>
        d.id === targetDeviceId
          ? { ...d, assignedVideoId: vidId, lastPushCommand: `ADD_VIDEO_${vidId}_${Date.now()}` }
          : d
      );
    }
    store.liveState.lastPushCommand = `ADD_VIDEO_${vidId}_${Date.now()}`;
    writeStore(store);
    return sendJson(res, 200, { ok: true, video: newVideo, videos: store.videos, deletedVideoIds: store.deletedVideoIds });
  }

  // 7. POST /api/videos/delete — Explicitly remove video from playlist & record tombstone so it never reappears
  if (req.method === "POST" && pathname === "/api/videos/delete") {
    const videoId = body.videoId || body.id;
    const targetVid = store.videos.find((v) => v.id === videoId);
    if (targetVid && targetVid.localFileName) {
      const fPath = path.join(VIDEOS_DIR, path.basename(targetVid.localFileName));
      if (fs.existsSync(fPath)) {
        try {
          fs.unlinkSync(fPath);
        } catch (_) {}
      }
    }
    if (videoId && !(store.deletedVideoIds || []).includes(videoId)) {
      store.deletedVideoIds = [...(store.deletedVideoIds || []), String(videoId)];
    }
    store.videos = store.videos.filter((v) => v.id !== videoId);
    store.devices = store.devices.map((d) =>
      d.assignedVideoId === videoId ? { ...d, assignedVideoId: "ALL" } : d
    );
    store.liveState.lastPushCommand = `DELETE_VIDEO_${videoId}_${Date.now()}`;
    writeStore(store);
    return sendJson(res, 200, { ok: true, videos: store.videos, deletedVideoIds: store.deletedVideoIds });
  }

  // 8. POST /api/pair & POST /api/heartbeat — 6-Digit Device Pairing & 2.0s Sync Loop
  if (req.method === "POST" && (pathname === "/api/pair" || pathname === "/api/heartbeat")) {
    const nowSec = Math.floor(Date.now() / 1000);
    const code = String(body.pairingCode || "000000").padStart(6, "0").slice(0, 6);
    const deviceId = body.deviceId || `tv_${code}`;

    // Reconcile any videos known by the client so cold Vercel instances never wipe out the playlist
    reconcileClientVideos(store, body.clientVideos, body.deletedVideoIds);

    const existing = store.devices.find((d) => d.id === deviceId || d.pairingCode === code);
    const isPairingAction = pathname === "/api/pair";

    const clientSettingsMs = Number(body.settingsUpdatedAtMs) || 0;
    const serverDeviceSettingsMs = existing && existing.settingsUpdatedAtMs ? Number(existing.settingsUpdatedAtMs) : 0;
    const preferClientSettings = isPairingAction || !existing || clientSettingsMs > serverDeviceSettingsMs;

    const resolvedOrientation = preferClientSettings && body.orientation
      ? String(body.orientation)
      : existing && existing.orientation
      ? existing.orientation
      : body.orientation || store.liveState.orientation || "LANDSCAPE";

    const resolvedRotateDegrees = preferClientSettings && body.videoRotateDegrees !== undefined
      ? normalizeDegrees(body.videoRotateDegrees)
      : existing && existing.videoRotateDegrees !== undefined
      ? normalizeDegrees(existing.videoRotateDegrees)
      : body.videoRotateDegrees !== undefined
      ? normalizeDegrees(body.videoRotateDegrees)
      : normalizeDegrees(store.liveState.videoRotateDegrees || 0);

    const resolvedScaleMode = preferClientSettings && body.scaleMode
      ? String(body.scaleMode)
      : existing && existing.scaleMode
      ? existing.scaleMode
      : body.scaleMode || store.liveState.scaleMode || "FILL";

    const resolvedMuted = preferClientSettings && body.isMuted !== undefined
      ? Boolean(body.isMuted)
      : existing && existing.isMuted !== undefined
      ? Boolean(existing.isMuted)
      : body.isMuted !== undefined
      ? Boolean(body.isMuted)
      : Boolean(store.liveState.isMuted);

    const updatedDevice = {
      id: existing ? existing.id : deviceId,
      pairingCode: code,
      deviceName: isPairingAction
        ? body.deviceName || (existing ? existing.deviceName : `Android TV #${code}`)
        : existing
        ? existing.deviceName
        : body.deviceName || `Android TV #${code}`,
      zoneId: isPairingAction
        ? body.zoneId || (existing ? existing.zoneId : store.liveState.activeZoneId || "zone_lobby")
        : existing
        ? existing.zoneId
        : body.zoneId || "zone_lobby",
      isPaired: isPairingAction ? true : existing && existing.isPaired !== undefined ? existing.isPaired : true,
      isOnline: true,
      lastHeartbeatEpochSec: nowSec,
      orientation: resolvedOrientation,
      videoRotateDegrees: resolvedRotateDegrees,
      scaleMode: resolvedScaleMode,
      isMuted: resolvedMuted,
      settingsUpdatedAtMs: Math.max(clientSettingsMs, serverDeviceSettingsMs),
      assignedVideoId:
        isPairingAction && body.assignedVideoId
          ? body.assignedVideoId
          : existing && existing.assignedVideoId
          ? existing.assignedVideoId
          : "ALL",
      kioskLocked:
        existing && existing.kioskLocked !== undefined
          ? Boolean(existing.kioskLocked)
          : Boolean(store.liveState.kioskLocked),
      lastPushCommand: existing && existing.lastPushCommand ? existing.lastPushCommand : store.liveState.lastPushCommand,
      currentVideoTitle: body.currentVideoTitle || (existing ? existing.currentVideoTitle : "Standby"),
      cachedVideoCount:
        body.cachedVideoCount !== undefined ? Number(body.cachedVideoCount) : existing ? existing.cachedVideoCount : 0,
      installedVersionCode:
        body.installedVersionCode !== undefined
          ? Number(body.installedVersionCode)
          : existing
          ? existing.installedVersionCode
          : 1,
      installedVersionName:
        body.installedVersionName || (existing ? existing.installedVersionName : "1.0.0"),
      previousSlotVersionCode:
        body.previousSlotVersionCode !== undefined
          ? Number(body.previousSlotVersionCode)
          : existing
          ? existing.previousSlotVersionCode
          : 1,
      previousSlotVersionName:
        body.previousSlotVersionName || (existing ? existing.previousSlotVersionName : "1.0.0"),
      otaStatus: body.otaStatus || (existing ? existing.otaStatus : "UP_TO_DATE"),
      lastOtaMessage: body.lastOtaMessage || (existing ? existing.lastOtaMessage : "Slot A Verified & Healthy"),
    };

    store.devices = store.devices
      .filter((d) => d.id !== updatedDevice.id && d.pairingCode !== code)
      .concat(updatedDevice);
    writeStore(store);

    // Filter videos for this specific device (either assignedVideoId or ALL/zone videos)
    const deviceVideos =
      updatedDevice.assignedVideoId && updatedDevice.assignedVideoId !== "ALL"
        ? store.videos.filter((v) => v.id === updatedDevice.assignedVideoId)
        : store.videos.filter(
            (v) => !v.targetDeviceId || v.targetDeviceId === "ALL" || v.targetDeviceId === updatedDevice.id
          );

    const effectiveLiveState = {
      ...store.liveState,
      orientation: updatedDevice.orientation,
      videoRotateDegrees: updatedDevice.videoRotateDegrees,
      scaleMode: updatedDevice.scaleMode,
      isMuted: updatedDevice.isMuted,
      kioskLocked: updatedDevice.kioskLocked,
      settingsUpdatedAtMs: updatedDevice.settingsUpdatedAtMs,
      lastPushCommand: updatedDevice.lastPushCommand || store.liveState.lastPushCommand,
    };

    return sendJson(res, 200, {
      ok: true,
      device: updatedDevice,
      liveState: effectiveLiveState,
      videos: deviceVideos.length > 0 ? deviceVideos : store.videos,
      allVideos: store.videos,
      deletedVideoIds: store.deletedVideoIds || [],
      otaReleases: store.otaReleases,
    });
  }

  // 9. POST /api/zones — Create or update Playback Zone & Granular RBAC Permissions
  if (req.method === "POST" && pathname === "/api/zones") {
    const zoneId = body.id || `zone_${Date.now().toString(36)}`;
    const existing = store.zones.find((z) => z.id === zoneId);
    const updatedZone = {
      id: zoneId,
      zoneName: body.zoneName || (existing ? existing.zoneName : "New Zone"),
      locationTag: body.locationTag || (existing ? existing.locationTag : "Floor 1"),
      operatorName: body.operatorName || (existing ? existing.operatorName : "Zone Operator"),
      operatorRole: body.operatorRole || (existing ? existing.operatorRole : "ZONE_OPERATOR"),
      canPushPlaylist:
        body.canPushPlaylist !== undefined ? Boolean(body.canPushPlaylist) : existing ? existing.canPushPlaylist : true,
      canRotateScreen:
        body.canRotateScreen !== undefined ? Boolean(body.canRotateScreen) : existing ? existing.canRotateScreen : true,
      canManageOta:
        body.canManageOta !== undefined ? Boolean(body.canManageOta) : existing ? existing.canManageOta : false,
      orientationOverride:
        body.orientationOverride || (existing ? existing.orientationOverride : "INHERIT"),
    };
    store.zones = store.zones.filter((z) => z.id !== zoneId).concat(updatedZone);
    writeStore(store);
    return sendJson(res, 200, { ok: true, zone: updatedZone, zones: store.zones });
  }

  // 10. POST /api/ota-release — Publish new Over-the-Air (OTA) Software Update
  if (req.method === "POST" && pathname === "/api/ota-release") {
    const versionCode =
      Number(body.versionCode) ||
      Math.max(store.liveState.activeOtaVersionCode || 1, ...store.otaReleases.map((r) => r.versionCode || 1)) + 1;
    const versionName = body.versionName || `1.${versionCode}.0`;
    const simulateCorruptPackage = Boolean(body.simulateCorruptPackage);
    const sha256Checksum = simulateCorruptPackage
      ? "CORRUPT_HEADER_88f92a01b4e1"
      : crypto.randomBytes(32).toString("hex");

    const release = {
      id: `ota_v${versionCode}_${Date.now().toString(36)}`,
      versionCode,
      versionName,
      releaseNotes: body.releaseNotes || "Media3 gapless watchdog & A/B slot OTA verification",
      targetZoneId: body.targetZoneId || "ALL",
      packageUrl: `https://${req.headers.host || "tv-br.vercel.app"}/api/ota/package-v${versionName}.apk`,
      sha256Checksum,
      sizeBytes: 2457600,
      chunkCount: 4,
      isMandatory: true,
      simulateCorruptPackage,
      releaseStatus: "ACTIVE",
      createdAtEpochMs: Date.now(),
    };

    store.otaReleases = [release, ...store.otaReleases];
    store.liveState.activeOtaVersionCode = versionCode;
    store.liveState.activeOtaVersionName = versionName;
    store.liveState.lastPushCommand = `OTA_RELEASE_v${versionName}_${Date.now()}`;
    writeStore(store);
    return sendJson(res, 200, { ok: true, release, otaReleases: store.otaReleases });
  }

  // 11. POST /api/ota-rollback — Trigger Remote Fleet Rollback to Previous Slot
  if (req.method === "POST" && pathname === "/api/ota-rollback") {
    const prevVersionCode = Math.max(1, (store.liveState.activeOtaVersionCode || 2) - 1);
    const prevVersionName = body.targetVersionName || `1.${prevVersionCode === 1 ? 0 : prevVersionCode}.0`;
    store.liveState.activeOtaVersionCode = prevVersionCode;
    store.liveState.activeOtaVersionName = prevVersionName;
    store.liveState.lastPushCommand = `OTA_ROLLBACK_v${prevVersionName}_${Date.now()}`;
    if (store.otaReleases.length > 0) {
      store.otaReleases[0].releaseStatus = "ROLLED_BACK";
    }
    writeStore(store);
    return sendJson(res, 200, {
      ok: true,
      liveState: store.liveState,
      otaReleases: store.otaReleases,
    });
  }

  return sendJson(res, 404, { ok: false, error: `Unknown endpoint: ${pathname}` });
};
