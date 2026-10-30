/**
 * PulseCast Digital Signage & TV Ad Network — Production Vercel Serverless Backend (/api/index.js)
 * Deployed at: https://tv-br.vercel.app
 *
 * Features:
 * - Zero Demo Videos: Completely free of any sample/demo/fallback videos. Only user-uploaded videos exist.
 * - Multi-instance resilient video & device synchronization:
 *   Reconciles client-known videos, paired devices, deletedVideoIds, and orientation timestamps in
 *   /api/reconcile, /api/heartbeat, and /api/state so Vercel cold starts NEVER lose videos or unpair TVs.
 * - Lossless chunked video assembly + HTTP 206 Partial Content byte-range streaming for instant
 *   Web Live Preview & Android ExoPlayer playback.
 */

const fs = require("node:fs");
const path = require("node:path");
const crypto = require("node:crypto");

const TMP_DIR = "/tmp";
const VIDEOS_DIR = path.join(TMP_DIR, "videos");
const CHUNKS_DIR = path.join(TMP_DIR, "videos_chunks");
const STATE_FILE = path.join(TMP_DIR, "pulsecast_cms_live_state_v3.json");
const CHUNK_SIZE_LIMIT = 4 * 1024 * 1024; // Allow up to 4MB per chunk for lossless original-quality uploads

if (!fs.existsSync(VIDEOS_DIR)) {
  fs.mkdirSync(VIDEOS_DIR, { recursive: true });
}
if (!fs.existsSync(CHUNKS_DIR)) {
  fs.mkdirSync(CHUNKS_DIR, { recursive: true });
}

// In-memory cache of assembled video buffers per warm Node process
global.__pulsecastVideoMemoryCache = global.__pulsecastVideoMemoryCache || new Map();

function isDemoOrLegacyUrl(urlStr) {
  const s = String(urlStr || "").toLowerCase();
  return (
    s.includes("mov_bbb") ||
    s.includes("w3schools") ||
    s.includes("flower.mp4") ||
    s.includes("mdn.mozilla.net") ||
    s.includes("gtv-videos-bucket") ||
    s.includes("commondatastorage.googleapis.com") ||
    s.includes("forbigger") ||
    s.includes("bigbuckbunny")
  );
}

function isDemoVideoEntry(v) {
  if (!v || !v.id) return true;
  const idStr = String(v.id).toLowerCase();
  if (idStr.startsWith("seed_") || idStr.startsWith("demo_")) return true;
  if (isDemoOrLegacyUrl(v.sourceUrl)) return true;
  return false;
}

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
    deletedDeviceIds: [],
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
      const deletedSet = new Set(Array.isArray(parsed.deletedVideoIds) ? parsed.deletedVideoIds : []);
      const deletedDevSet = new Set(Array.isArray(parsed.deletedDeviceIds) ? parsed.deletedDeviceIds : []);
      const cleanVideos = (Array.isArray(parsed.videos) ? parsed.videos : []).filter(
        (v) => v && v.id && !deletedSet.has(v.id) && !isDemoVideoEntry(v)
      );
      const cleanDevices = (Array.isArray(parsed.devices) ? parsed.devices : []).filter(
        (d) => d && !deletedDevSet.has(d.id) && !deletedDevSet.has(d.pairingCode)
      );
      return {
        ...init,
        ...parsed,
        liveState: { ...init.liveState, ...(parsed.liveState || {}) },
        videos: cleanVideos,
        deletedVideoIds: Array.from(deletedSet),
        devices: cleanDevices,
        deletedDeviceIds: Array.from(deletedDevSet),
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

global.__pulsecastPendingReseedIds = global.__pulsecastPendingReseedIds || new Set();

function hasServerVideoBinary(localFileName) {
  if (!localFileName) return false;
  const fName = path.basename(String(localFileName));
  if (global.__pulsecastVideoMemoryCache.has(fName)) return true;
  const fPath = path.join(VIDEOS_DIR, fName);
  return fs.existsSync(fPath) && fs.statSync(fPath).size > 256;
}

/**
 * Merges client-known videos and paired devices into the server store without resurrecting deleted videos,
 * demo videos, or broken 404 server-file links.
 */
function reconcileClientState(store, clientVideos, clientDeletedIds, clientDevices, clientDeletedDeviceIds) {
  let changed = false;
  const deletedSet = new Set(store.deletedVideoIds || []);
  if (Array.isArray(clientDeletedIds)) {
    for (const delId of clientDeletedIds) {
      if (delId && !deletedSet.has(delId)) {
        deletedSet.add(String(delId));
        global.__pulsecastPendingReseedIds.delete(String(delId));
        changed = true;
      }
    }
  }
  store.deletedVideoIds = Array.from(deletedSet);

  const deletedDevSet = new Set(store.deletedDeviceIds || []);
  if (Array.isArray(clientDeletedDeviceIds)) {
    for (const delDevId of clientDeletedDeviceIds) {
      if (delDevId && !deletedDevSet.has(delDevId)) {
        deletedDevSet.add(String(delDevId));
        changed = true;
      }
    }
  }
  store.deletedDeviceIds = Array.from(deletedDevSet);

  // Strip deleted or demo videos, while keeping all uploaded videos in store.videos so EVERY browser/device sees them!
  const beforeLen = store.videos.length;
  store.videos = (store.videos || []).filter((v) => {
    if (!v || !v.id || deletedSet.has(v.id) || isDemoVideoEntry(v)) return false;
    const isServerFile = String(v.sourceUrl || "").includes("/api/videos/file/");
    if (isServerFile && !hasServerVideoBinary(v.localFileName || `${v.id}.mp4`)) {
      global.__pulsecastPendingReseedIds.add(String(v.id));
    }
    return true;
  });
  if (store.videos.length !== beforeLen) {
    changed = true;
  }

  if (Array.isArray(clientVideos)) {
    const existingIds = new Set(store.videos.map((v) => v.id));
    for (const cv of clientVideos) {
      if (!cv || !cv.id || deletedSet.has(cv.id) || isDemoVideoEntry(cv)) continue;
      const safeLocal = String(cv.localFileName || `${cv.id}.mp4`);
      const isServerFile = String(cv.sourceUrl || "").includes("/api/videos/file/");
      if (isServerFile && !hasServerVideoBinary(safeLocal)) {
        global.__pulsecastPendingReseedIds.add(String(cv.id));
      } else {
        global.__pulsecastPendingReseedIds.delete(String(cv.id));
      }
      if (!existingIds.has(cv.id)) {
        store.videos.push({
          id: String(cv.id),
          title: String(cv.title || "Uploaded Video").slice(0, 120),
          sourceUrl: String(cv.sourceUrl || ""),
          backupUrl: "",
          localFileName: safeLocal,
          zoneId: String(cv.zoneId || "zone_lobby"),
          targetDeviceId: String(cv.targetDeviceId || "ALL"),
          sizeBytes: Number(cv.sizeBytes) || 0,
          chunkCount: Number(cv.chunkCount) || 1,
          sha256Checksum: String(cv.sha256Checksum || ""),
          isVerifiedCached: true,
          sortOrder: Number(cv.sortOrder) || 0,
          createdAtEpochMs: Number(cv.createdAtEpochMs) || Date.now(),
        });
        existingIds.add(cv.id);
        changed = true;
      }
    }
    // Sort newest first so the most recently uploaded video is always primary
    store.videos.sort((a, b) => (Number(b.createdAtEpochMs) || 0) - (Number(a.createdAtEpochMs) || 0));
  }

  // Filter out any explicitly removed devices
  const beforeDevLen = (store.devices || []).length;
  store.devices = (store.devices || []).filter(
    (d) => d && !deletedDevSet.has(d.id) && !deletedDevSet.has(d.pairingCode)
  );
  if (store.devices.length !== beforeDevLen) {
    changed = true;
  }

  // Reconcile paired devices & propagate newer orientation/rotation/scale settings across containers
  if (Array.isArray(clientDevices)) {
    const existingByCode = new Map();
    for (const d of store.devices || []) {
      if (d && (d.id || d.pairingCode)) {
        existingByCode.set(d.pairingCode || d.id, d);
      }
    }
    for (const cd of clientDevices) {
      if (!cd || (!cd.id && !cd.pairingCode)) continue;
      if (deletedDevSet.has(cd.id) || deletedDevSet.has(cd.pairingCode)) continue;
      const key = cd.pairingCode || cd.id;
      if (!existingByCode.has(key)) {
        const restoredDevice = {
          id: String(cd.id || `tv_${cd.pairingCode}`),
          pairingCode: String(cd.pairingCode || "000000").padStart(6, "0").slice(0, 6),
          deviceName: String(cd.deviceName || `Android TV #${cd.pairingCode}`),
          zoneId: String(cd.zoneId || "zone_lobby"),
          isPaired: true,
          isOnline: Boolean(cd.isOnline),
          lastHeartbeatEpochSec: Number(cd.lastHeartbeatEpochSec) || 0,
          orientation: String(cd.orientation || "LANDSCAPE"),
          videoRotateDegrees: normalizeDegrees(cd.videoRotateDegrees || 0),
          scaleMode: String(cd.scaleMode || "FILL"),
          isMuted: Boolean(cd.isMuted),
          assignedVideoId: String(cd.assignedVideoId || "ALL"),
          kioskLocked: cd.kioskLocked !== undefined ? Boolean(cd.kioskLocked) : true,
          settingsUpdatedAtMs: Number(cd.settingsUpdatedAtMs) || 0,
          lastPushCommand: String(cd.lastPushCommand || store.liveState.lastPushCommand || "READY"),
          currentVideoTitle: String(cd.currentVideoTitle || "Ready"),
          cachedVideoCount: Number(cd.cachedVideoCount) || 0,
          installedVersionCode: Number(cd.installedVersionCode) || 1,
          installedVersionName: String(cd.installedVersionName || "1.0.0"),
          previousSlotVersionCode: Number(cd.previousSlotVersionCode) || 1,
          previousSlotVersionName: String(cd.previousSlotVersionName || "1.0.0"),
          otaStatus: String(cd.otaStatus || "UP_TO_DATE"),
          lastOtaMessage: String(cd.lastOtaMessage || "Slot A Verified & Healthy"),
        };
        store.devices.push(restoredDevice);
        existingByCode.set(key, restoredDevice);
        changed = true;
      } else {
        const existingDev = existingByCode.get(key);
        const clientMs = Number(cd.settingsUpdatedAtMs) || 0;
        const serverMs = Number(existingDev.settingsUpdatedAtMs) || 0;
        if (clientMs > serverMs) {
          if (cd.orientation) existingDev.orientation = String(cd.orientation);
          if (cd.videoRotateDegrees !== undefined) {
            existingDev.videoRotateDegrees = normalizeDegrees(cd.videoRotateDegrees);
          }
          if (cd.scaleMode) existingDev.scaleMode = String(cd.scaleMode);
          if (cd.isMuted !== undefined) existingDev.isMuted = Boolean(cd.isMuted);
          if (cd.assignedVideoId !== undefined) existingDev.assignedVideoId = String(cd.assignedVideoId);
          if (cd.deviceName) existingDev.deviceName = String(cd.deviceName);
          existingDev.settingsUpdatedAtMs = clientMs;
          existingDev.lastPushCommand = String(cd.lastPushCommand || `DEVICE_CTRL_${clientMs}`);
          store.liveState.orientation = existingDev.orientation;
          store.liveState.videoRotateDegrees = existingDev.videoRotateDegrees;
          store.liveState.scaleMode = existingDev.scaleMode;
          store.liveState.isMuted = existingDev.isMuted;
          store.liveState.settingsUpdatedAtMs = Math.max(Number(store.liveState.settingsUpdatedAtMs) || 0, clientMs);
          store.liveState.lastPushCommand = existingDev.lastPushCommand;
          changed = true;
        }
      }
    }
  }

  return changed;
}

function computeMissingServerVideoIds(store) {
  const missingSet = new Set(global.__pulsecastPendingReseedIds || []);
  for (const v of store.videos || []) {
    if (!v || !v.id || !v.localFileName) continue;
    const isServerHostedFile = String(v.sourceUrl || "").includes("/api/videos/file/");
    if (!isServerHostedFile) continue;
    if (!hasServerVideoBinary(v.localFileName)) {
      missingSet.add(v.id);
    } else {
      missingSet.delete(v.id);
    }
  }
  return Array.from(missingSet);
}

function detectMimeType(fileName) {
  const ext = path.extname(fileName || "").toLowerCase();
  if (ext === ".webm") return "video/webm";
  if (ext === ".mov" || ext === ".qt") return "video/quicktime";
  if (ext === ".mkv") return "video/x-matroska";
  return "video/mp4";
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
  res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, Range");
  res.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
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

  // 1. Stream uploaded video files from memory or /tmp/videos/:fileName with full HTTP 206 Range support
  if (req.method === "GET" && pathname.startsWith("/api/videos/file/")) {
    const fileName = path.basename(decodeURIComponent(pathname.replace("/api/videos/file/", "")));
    const filePath = path.join(VIDEOS_DIR, fileName);
    const mimeType = detectMimeType(fileName);

    let fileBuffer = global.__pulsecastVideoMemoryCache.get(fileName) || null;
    if (!fileBuffer && fs.existsSync(filePath)) {
      try {
        fileBuffer = fs.readFileSync(filePath);
        if (fileBuffer.length <= 25 * 1024 * 1024) {
          global.__pulsecastVideoMemoryCache.set(fileName, fileBuffer);
        }
      } catch (_) {}
    }

    if (!fileBuffer || fileBuffer.length === 0) {
      // NEVER redirect to a demo video! Return 404 so the client retries or Web Dashboard re-seeds.
      return sendJson(res, 404, {
        ok: false,
        error: "Video binary syncing to this instance — retry in 1s",
        fileName,
      });
    }

    const totalSize = fileBuffer.length;
    const rangeHeader = req.headers.range;

    if (rangeHeader) {
      const parts = rangeHeader.replace(/bytes=/, "").split("-");
      const start = parseInt(parts[0], 10) || 0;
      const end = parts[1] ? Math.min(totalSize - 1, parseInt(parts[1], 10)) : totalSize - 1;
      if (start >= totalSize || start > end) {
        res.writeHead(416, {
          "Content-Range": `bytes */${totalSize}`,
          "Access-Control-Allow-Origin": "*",
        });
        return res.end();
      }
      const chunkSize = end - start + 1;
      res.writeHead(206, {
        "Content-Range": `bytes ${start}-${end}/${totalSize}`,
        "Accept-Ranges": "bytes",
        "Content-Length": chunkSize,
        "Content-Type": mimeType,
        "Access-Control-Allow-Origin": "*",
        "Cache-Control": "public, max-age=31536000, immutable",
      });
      return res.end(fileBuffer.subarray(start, end + 1));
    }

    res.writeHead(200, {
      "Content-Type": mimeType,
      "Content-Length": totalSize,
      "Access-Control-Allow-Origin": "*",
      "Accept-Ranges": "bytes",
      "Cache-Control": "public, max-age=31536000, immutable",
    });
    return res.end(fileBuffer);
  }

  // 2. GET /api/state (or /api/sync, /api/live-state)
  if (
    req.method === "GET" &&
    (pathname === "/api/state" || pathname === "/api/sync" || pathname === "/api/live-state" || pathname === "/api")
  ) {
    const nowSec = Math.floor(Date.now() / 1000);
    store.devices = (store.devices || []).map((dev) => ({
      ...dev,
      isPaired: true,
      isOnline: nowSec - (dev.lastHeartbeatEpochSec || 0) < 15,
    }));
    return sendJson(res, 200, {
      ok: true,
      serverTimeMs: Date.now(),
      missingServerVideoIds: computeMissingServerVideoIds(store),
      ...store,
    });
  }

  const body = await parseJsonBody(req);

  // 2b. POST /api/reconcile — Web Dashboard sends its localStorage videos, deletedVideoIds & paired devices
  if (req.method === "POST" && pathname === "/api/reconcile") {
    const changed = reconcileClientState(
      store,
      body.videos,
      body.deletedVideoIds,
      body.devices,
      body.deletedDeviceIds
    );
    if (changed) {
      writeStore(store);
    }
    const nowSec = Math.floor(Date.now() / 1000);
    store.devices = (store.devices || []).map((dev) => ({
      ...dev,
      isPaired: true,
      isOnline: nowSec - (dev.lastHeartbeatEpochSec || 0) < 15,
    }));
    return sendJson(res, 200, {
      ok: true,
      serverTimeMs: Date.now(),
      missingServerVideoIds: computeMissingServerVideoIds(store),
      ...store,
    });
  }

  // 3. POST /api/state — Update global liveState fields
  if (req.method === "POST" && (pathname === "/api/state" || pathname === "/api/live-state")) {
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

    if (
      body.orientation !== undefined ||
      body.videoRotateDegrees !== undefined ||
      body.rotateDelta !== undefined ||
      body.scaleMode !== undefined ||
      body.isMuted !== undefined ||
      body.assignedVideoId !== undefined ||
      body.lastPushCommand !== undefined
    ) {
      store.devices = (store.devices || []).map((dev) => ({
        ...dev,
        ...(body.orientation !== undefined ? { orientation: body.orientation } : {}),
        ...(body.videoRotateDegrees !== undefined || body.rotateDelta !== undefined
          ? { videoRotateDegrees: nextVideoRotate }
          : {}),
        ...(body.scaleMode !== undefined ? { scaleMode: body.scaleMode } : {}),
        ...(body.isMuted !== undefined ? { isMuted: Boolean(body.isMuted) } : {}),
        ...(body.assignedVideoId !== undefined ? { assignedVideoId: String(body.assignedVideoId) } : {}),
        settingsUpdatedAtMs: nowMs,
        lastPushCommand: store.liveState.lastPushCommand,
      }));
    }

    reconcileClientState(store, body.clientVideos, body.deletedVideoIds, body.clientDevices);
    writeStore(store);
    return sendJson(res, 200, {
      ok: true,
      liveState: store.liveState,
      devices: store.devices,
      videos: store.videos,
      missingServerVideoIds: computeMissingServerVideoIds(store),
    });
  }

  // 4. POST /api/device-control & POST /api/devices/delete — Per-Device Orientation, Video Rotate, Scale, Mute & Remove Device
  if (req.method === "POST" && (pathname === "/api/device-control" || pathname === "/api/devices/delete")) {
    const {
      action,
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

    if (pathname === "/api/devices/delete" || action === "delete") {
      const deletedDevSet = new Set(store.deletedDeviceIds || []);
      if (deviceId) deletedDevSet.add(String(deviceId));
      if (pairingCode) deletedDevSet.add(String(pairingCode));
      store.devices = (store.devices || []).filter((d) => {
        const matchId = deviceId && d.id === deviceId;
        const matchCode = pairingCode && d.pairingCode === String(pairingCode);
        if (matchId || matchCode) {
          if (d.id) deletedDevSet.add(String(d.id));
          if (d.pairingCode) deletedDevSet.add(String(d.pairingCode));
          return false;
        }
        return true;
      });
      store.deletedDeviceIds = Array.from(deletedDevSet);
      writeStore(store);
      return sendJson(res, 200, {
        ok: true,
        devices: store.devices,
        deletedDeviceIds: store.deletedDeviceIds,
      });
    }

    reconcileClientState(
      store,
      body.clientVideos,
      body.deletedVideoIds,
      body.clientDevices,
      body.deletedDeviceIds
    );

    const idx = store.devices.findIndex(
      (d) => (deviceId && d.id === deviceId) || (pairingCode && d.pairingCode === String(pairingCode))
    );
    const prevMs =
      idx !== -1
        ? Math.max(Number(store.devices[idx].settingsUpdatedAtMs) || 0, Number(store.liveState.settingsUpdatedAtMs) || 0)
        : Number(store.liveState.settingsUpdatedAtMs) || 0;
    const nowMs = Math.max(Date.now(), prevMs + 1);

    if (idx === -1) {
      const code = String(pairingCode || String(deviceId || "").replace(/^tv_/, "") || "000000")
        .padStart(6, "0")
        .slice(0, 6);
      const nextDeg = normalizeDegrees(videoRotateDegrees !== undefined ? videoRotateDegrees : rotateDelta || 0);
      const newDev = {
        id: deviceId || `tv_${code}`,
        pairingCode: code,
        deviceName: deviceName || `Android TV #${code}`,
        zoneId: zoneId || "zone_lobby",
        isPaired: true,
        isOnline: true,
        lastHeartbeatEpochSec: Math.floor(Date.now() / 1000),
        orientation: orientation || store.liveState.orientation || "LANDSCAPE",
        videoRotateDegrees: nextDeg,
        scaleMode: scaleMode || store.liveState.scaleMode || "FILL",
        isMuted: isMuted !== undefined ? Boolean(isMuted) : Boolean(store.liveState.isMuted),
        assignedVideoId: assignedVideoId || "ALL",
        kioskLocked: kioskLocked !== undefined ? Boolean(kioskLocked) : true,
        settingsUpdatedAtMs: nowMs,
        lastPushCommand: `DEVICE_CTRL_${nowMs}`,
      };
      store.devices.push(newDev);
      store.liveState = {
        ...store.liveState,
        orientation: newDev.orientation,
        videoRotateDegrees: newDev.videoRotateDegrees,
        scaleMode: newDev.scaleMode,
        isMuted: newDev.isMuted,
        settingsUpdatedAtMs: nowMs,
        lastPushCommand: newDev.lastPushCommand,
      };
      writeStore(store);
      return sendJson(res, 200, { ok: true, device: newDev, devices: store.devices, liveState: store.liveState });
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

    const pushCmd = `DEVICE_CTRL_${target.id}_${nowMs}`;
    store.devices[idx] = {
      ...target,
      isPaired: true,
      ...(deviceName !== undefined ? { deviceName: String(deviceName) } : {}),
      ...(zoneId !== undefined ? { zoneId: String(zoneId) } : {}),
      ...(orientation !== undefined ? { orientation: String(orientation) } : {}),
      videoRotateDegrees: nextRotateDeg,
      ...(scaleMode !== undefined ? { scaleMode: String(scaleMode) } : {}),
      ...(isMuted !== undefined ? { isMuted: Boolean(isMuted) } : {}),
      ...(assignedVideoId !== undefined ? { assignedVideoId: String(assignedVideoId) } : {}),
      ...(kioskLocked !== undefined ? { kioskLocked: Boolean(kioskLocked) } : {}),
      settingsUpdatedAtMs: nowMs,
      lastPushCommand: pushCmd,
    };
    store.liveState = {
      ...store.liveState,
      orientation: store.devices[idx].orientation,
      videoRotateDegrees: store.devices[idx].videoRotateDegrees,
      scaleMode: store.devices[idx].scaleMode,
      isMuted: store.devices[idx].isMuted,
      settingsUpdatedAtMs: nowMs,
      lastPushCommand: pushCmd,
    };
    writeStore(store);
    return sendJson(res, 200, {
      ok: true,
      device: store.devices[idx],
      devices: store.devices,
      liveState: store.liveState,
    });
  }

  // 5. POST /api/upload-chunk — Lossless Original-Quality Video Uploader with Multi-Chunk Verification
  if (req.method === "POST" && pathname === "/api/upload-chunk") {
    const {
      fileId,
      fileName,
      title,
      zoneId,
      targetDeviceId,
      chunkIndex,
      totalChunks,
      base64Chunk,
      totalFileSize,
      isReseed,
    } = body;
    if (!fileId || !base64Chunk) {
      return sendJson(res, 400, { ok: false, error: "Missing fileId or base64Chunk" });
    }
    const buffer = Buffer.from(base64Chunk, "base64");
    if (buffer.length > CHUNK_SIZE_LIMIT) {
      return sendJson(res, 413, { ok: false, error: "Chunk exceeds limit" });
    }

    const cleanFileId = String(fileId).replace(/[^a-zA-Z0-9._-]/g, "");
    const safeFileName = body.localFileName
      ? path.basename(String(body.localFileName))
      : `${cleanFileId}_${(fileName || "video.mp4").replace(/[^a-zA-Z0-9._-]/g, "_")}`;
    const targetPath = path.join(VIDEOS_DIR, safeFileName);
    const totalChunksNum = Math.max(1, Number(totalChunks) || 1);
    const chunkIdxNum = Math.max(0, Number(chunkIndex) || 0);

    // Save individual chunk part file so chunks can be verified and assembled without corruption
    const partPath = path.join(CHUNKS_DIR, `${safeFileName}.part_${chunkIdxNum}`);
    fs.writeFileSync(partPath, buffer);

    // Check which chunks we have on this instance
    const missingChunks = [];
    for (let i = 0; i <= chunkIdxNum; i++) {
      const p = path.join(CHUNKS_DIR, `${safeFileName}.part_${i}`);
      if (!fs.existsSync(p)) {
        missingChunks.push(i);
      }
    }

    const isLastChunk = chunkIdxNum + 1 >= totalChunksNum;
    let isComplete = false;
    let videoEntry = null;

    if (isLastChunk && missingChunks.length === 0) {
      // Verify all chunks 0 .. totalChunksNum - 1 exist
      for (let i = 0; i < totalChunksNum; i++) {
        const p = path.join(CHUNKS_DIR, `${safeFileName}.part_${i}`);
        if (!fs.existsSync(p)) {
          missingChunks.push(i);
        }
      }
    }

    if (isLastChunk && missingChunks.length === 0) {
      // Assemble all chunks in exact order into targetPath and memory cache
      const buffers = [];
      for (let i = 0; i < totalChunksNum; i++) {
        const p = path.join(CHUNKS_DIR, `${safeFileName}.part_${i}`);
        buffers.push(fs.readFileSync(p));
      }
      const fullBuffer = Buffer.concat(buffers);
      fs.writeFileSync(targetPath, fullBuffer);
      if (fullBuffer.length <= 25 * 1024 * 1024) {
        global.__pulsecastVideoMemoryCache.set(safeFileName, fullBuffer);
      }
      // Clean up temporary part files
      for (let i = 0; i < totalChunksNum; i++) {
        const p = path.join(CHUNKS_DIR, `${safeFileName}.part_${i}`);
        try {
          fs.unlinkSync(p);
        } catch (_) {}
      }

      isComplete = true;
      const actualBytes = fullBuffer.length;
      const sha256Checksum = crypto.createHash("sha256").update(fullBuffer).digest("hex");
      const hostUrl = `https://${req.headers.host || "tv-br.vercel.app"}`;
      const vidId = body.videoId ? String(body.videoId) : `vid_${cleanFileId}`;
      const nowMs = Date.now();

      store.deletedVideoIds = (store.deletedVideoIds || []).filter((id) => id !== vidId);

      const existingEntry = (store.videos || []).find((v) => v.id === vidId);
      videoEntry = {
        id: vidId,
        title: (title || (existingEntry && existingEntry.title) || fileName || "Uploaded Video").slice(0, 120),
        sourceUrl: `${hostUrl}/api/videos/file/${encodeURIComponent(safeFileName)}`,
        backupUrl: "",
        localFileName: safeFileName,
        zoneId: zoneId || store.liveState.activeZoneId || "zone_lobby",
        targetDeviceId: targetDeviceId || (existingEntry && existingEntry.targetDeviceId) || "ALL",
        sizeBytes: Number(totalFileSize) || actualBytes,
        chunkCount: totalChunksNum,
        sha256Checksum,
        isVerifiedCached: true,
        sortOrder: 0,
        createdAtEpochMs: isReseed && existingEntry ? existingEntry.createdAtEpochMs : nowMs,
      };

      // Place newest uploaded video FIRST in playlist so all TVs & Live Preview play it immediately!
      const remaining = (store.videos || []).filter((v) => v.id !== vidId && !isDemoVideoEntry(v));
      store.videos = [videoEntry, ...remaining].map((v, idx) => ({ ...v, sortOrder: idx }));

      if (!isReseed) {
        if (targetDeviceId && targetDeviceId !== "ALL") {
          store.devices = (store.devices || []).map((d) =>
            d.id === targetDeviceId
              ? { ...d, assignedVideoId: videoEntry.id, lastPushCommand: `UPLOAD_PLAY_${videoEntry.id}_${nowMs}` }
              : d
          );
        } else {
          // Ensure all devices play the newly uploaded playlist immediately!
          store.devices = (store.devices || []).map((d) => ({
            ...d,
            assignedVideoId: "ALL",
            lastPushCommand: `UPLOAD_PLAY_${videoEntry.id}_${nowMs}`,
          }));
        }
        store.liveState.lastPushCommand = `UPLOAD_PLAY_${videoEntry.id}_${nowMs}`;
      }
      writeStore(store);
    }

    return sendJson(res, 200, {
      ok: true,
      isComplete,
      missingChunks,
      chunkIndex: chunkIdxNum,
      totalChunks: totalChunksNum,
      video: videoEntry,
      videos: store.videos,
    });
  }

  // 6. POST /api/videos — Add direct stream URL creative to playlist (placed first so it plays immediately)
  if (req.method === "POST" && pathname === "/api/videos") {
    const rawSourceUrl = String(body.sourceUrl || "").trim();
    if (!rawSourceUrl || isDemoOrLegacyUrl(rawSourceUrl)) {
      return sendJson(res, 400, {
        ok: false,
        error: "Please enter a valid video stream URL or upload a video file above.",
      });
    }
    const vidId = body.id || `vid_${Date.now().toString(36)}`;
    const targetDeviceId = body.targetDeviceId || "ALL";
    const nowMs = Date.now();
    store.deletedVideoIds = (store.deletedVideoIds || []).filter((id) => id !== vidId);
    const newVideo = {
      id: vidId,
      title: (body.title || `Broadcast Video #${store.videos.length + 1}`).slice(0, 120),
      sourceUrl: rawSourceUrl,
      backupUrl: "",
      localFileName: body.localFileName || `${vidId}.mp4`,
      zoneId: body.zoneId || store.liveState.activeZoneId || "zone_lobby",
      targetDeviceId,
      sizeBytes: Number(body.sizeBytes) || 0,
      chunkCount: Number(body.chunkCount) || 1,
      isVerifiedCached: true,
      sortOrder: 0,
      createdAtEpochMs: nowMs,
    };
    const remaining = (store.videos || []).filter((v) => v.id !== vidId && !isDemoVideoEntry(v));
    store.videos = [newVideo, ...remaining].map((v, idx) => ({ ...v, sortOrder: idx }));

    if (targetDeviceId && targetDeviceId !== "ALL") {
      store.devices = store.devices.map((d) =>
        d.id === targetDeviceId
          ? { ...d, assignedVideoId: vidId, lastPushCommand: `ADD_VIDEO_${vidId}_${nowMs}` }
          : d
      );
    } else {
      store.devices = store.devices.map((d) => ({
        ...d,
        assignedVideoId: "ALL",
        lastPushCommand: `ADD_VIDEO_${vidId}_${nowMs}`,
      }));
    }
    store.liveState.lastPushCommand = `ADD_VIDEO_${vidId}_${nowMs}`;
    writeStore(store);
    return sendJson(res, 200, {
      ok: true,
      video: newVideo,
      videos: store.videos,
      deletedVideoIds: store.deletedVideoIds,
    });
  }

  // 7. POST /api/videos/delete — Explicitly remove video from playlist & record tombstone
  if (req.method === "POST" && pathname === "/api/videos/delete") {
    const videoId = body.videoId || body.id;
    const targetVid = store.videos.find((v) => v.id === videoId);
    if (targetVid && targetVid.localFileName) {
      const baseName = path.basename(targetVid.localFileName);
      global.__pulsecastVideoMemoryCache.delete(baseName);
      const fPath = path.join(VIDEOS_DIR, baseName);
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

  // 8. POST /api/pair & POST /api/heartbeat — Automatic Permanent Pairing & 2.0s Sync Loop
  if (req.method === "POST" && (pathname === "/api/pair" || pathname === "/api/heartbeat")) {
    const nowSec = Math.floor(Date.now() / 1000);
    const code = String(body.pairingCode || "000000").padStart(6, "0").slice(0, 6);
    const deviceId = body.deviceId || `tv_${code}`;
    const isPairingAction = pathname === "/api/pair";

    if (isPairingAction) {
      store.deletedDeviceIds = (store.deletedDeviceIds || []).filter(
        (id) => id !== deviceId && id !== code
      );
    }

    reconcileClientState(
      store,
      body.clientVideos,
      body.deletedVideoIds,
      body.clientDevices,
      body.deletedDeviceIds
    );

    const existing = store.devices.find((d) => d.id === deviceId || d.pairingCode === code);

    const clientSettingsMs = Number(body.settingsUpdatedAtMs) || 0;
    const serverDeviceSettingsMs = existing && existing.settingsUpdatedAtMs ? Number(existing.settingsUpdatedAtMs) : 0;
    const preferClientSettings = isPairingAction || (!existing && clientSettingsMs > (Number(store.liveState.settingsUpdatedAtMs) || 0)) || (existing && clientSettingsMs > serverDeviceSettingsMs);

    const resolvedOrientation =
      preferClientSettings && body.orientation
        ? String(body.orientation)
        : existing && existing.orientation
        ? existing.orientation
        : store.liveState.orientation || body.orientation || "LANDSCAPE";

    const resolvedRotateDegrees =
      preferClientSettings && body.videoRotateDegrees !== undefined
        ? normalizeDegrees(body.videoRotateDegrees)
        : existing && existing.videoRotateDegrees !== undefined
        ? normalizeDegrees(existing.videoRotateDegrees)
        : store.liveState.videoRotateDegrees !== undefined
        ? normalizeDegrees(store.liveState.videoRotateDegrees)
        : normalizeDegrees(body.videoRotateDegrees || 0);

    const resolvedScaleMode =
      preferClientSettings && body.scaleMode
        ? String(body.scaleMode)
        : existing && existing.scaleMode
        ? existing.scaleMode
        : store.liveState.scaleMode || body.scaleMode || "FILL";

    const resolvedMuted =
      preferClientSettings && body.isMuted !== undefined
        ? Boolean(body.isMuted)
        : existing && existing.isMuted !== undefined
        ? Boolean(existing.isMuted)
        : store.liveState.isMuted !== undefined
        ? Boolean(store.liveState.isMuted)
        : Boolean(body.isMuted);

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
      isPaired: true,
      isOnline: true,
      lastHeartbeatEpochSec: nowSec,
      orientation: resolvedOrientation,
      videoRotateDegrees: resolvedRotateDegrees,
      scaleMode: resolvedScaleMode,
      isMuted: resolvedMuted,
      settingsUpdatedAtMs: Math.max(clientSettingsMs, serverDeviceSettingsMs, Number(store.liveState.settingsUpdatedAtMs) || 0),
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
      currentVideoTitle: body.currentVideoTitle || (existing ? existing.currentVideoTitle : "Ready"),
      cachedVideoCount:
        body.cachedVideoCount !== undefined ? Number(body.cachedVideoCount) : existing ? existing.cachedVideoCount : 0,
      installedVersionCode:
        body.installedVersionCode !== undefined
          ? Number(body.installedVersionCode)
          : existing
          ? existing.installedVersionCode
          : 1,
      installedVersionName: body.installedVersionName || (existing ? existing.installedVersionName : "1.0.0"),
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

    const isRemovedDevice =
      !isPairingAction &&
      ((store.deletedDeviceIds || []).includes(deviceId) || (store.deletedDeviceIds || []).includes(code));

    if (!isRemovedDevice) {
      store.devices = store.devices
        .filter((d) => d.id !== updatedDevice.id && d.pairingCode !== code)
        .concat(updatedDevice);
    }
    writeStore(store);

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
      deletedDeviceIds: store.deletedDeviceIds || [],
      missingServerVideoIds: computeMissingServerVideoIds(store),
      otaReleases: store.otaReleases,
    });
  }

  // 9. POST /api/zones — Create or update Playback Zone
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
      orientationOverride: body.orientationOverride || (existing ? existing.orientationOverride : "INHERIT"),
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
