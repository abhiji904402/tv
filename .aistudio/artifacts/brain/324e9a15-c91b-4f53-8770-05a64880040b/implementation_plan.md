# PulseCast Signage & TV Ad Network

A production-grade Digital Signage and TV Ad Network suite delivered as a dual-mode Android application (**TV Display Client** + **Cloud CMS Dashboard**) powered by **Jetpack Compose**, **Media3 ExoPlayer**, and **Firebase Firestore**. It features gapless offline-first video looping, a 3.5-second buffering watchdog, 6-digit screen pairing, granular zone permissions, and an A/B Over-the-Air (OTA) software update engine with automatic rollback.

## User Review & Critical Decisions

> [!IMPORTANT]
> Please review the confirmed product decisions below—including the newly added **Over-the-Air (OTA) Software Update & Rollback** architecture—and click **Proceed** when ready to provision Firebase and build the application.

- **Confirmed Decision 1 (Dual-Mode Workspace Architecture)**: The Android app provides both the **TV Display Client** (Kiosk Player) and the **CMS Controller Dashboard** within a unified application, plus a companion Node.js/Vercel serverless reference API for external web deployments.
- **Confirmed Decision 2 (Video Ingestion & Chunked Pipeline)**: Videos can be ingested via the local device media picker or direct stream URLs, processed through a **600KB chunked ingestion pipeline** into local device storage (`filesDir/videos`) for verified offline playback (`✔`), and synchronized across connected screens via **Firebase Firestore** (`cms_global/live_state`).
- **Confirmed Decision 3 (Kiosk Security & Admin Unlock)**: While looping ads in Kiosk Mode, the **Protected Admin Settings Panel** is hidden from viewers and unlocked only via a **5-tap corner gesture** (top-right) or **TV Remote Menu key**, guarded by a configurable **4-digit Admin PIN** (default `1234`).
- **Decision 4 (OTA Software Update & A/B Rollback Engine)**: Administrators can publish new client versions in the CMS Dashboard; display clients automatically detect, download, verify (SHA-256), and stage updates in the background using an **A/B slot architecture**, automatically rolling back to the previous verified version if post-update health checks or installation fail.

---

## 1. Overview & Core Concept

- **What It Does**: Transforms any Android TV, tablet, or digital display into a resilient, cloud-managed advertising screen while giving network operators a real-time command center to upload ads, manage multi-zone playlists, rotate/scale screens remotely, and push OTA software updates with zero-downtime rollback safety.
- **Target Audience / Persona**: Retail store operators, DOOH (Digital Out-of-Home) ad network administrators, hospitality venues, and enterprise IT teams managing fleets of unmanned displays.
- **Key Value**: Eliminates black screens and frozen streams through local offline caching and a 3.5-second watchdog, while providing instant fleet control (orientation, mute, playlists, zone permissions, and OTA updates) from a single dark-mode dashboard.

---

## 2. User Experience & Visual Design

### Key User Flows
1. **Authentication & Role Selection**:
   - Users sign in securely with **Google Sign-In** (via Jetpack Credential Manager) and select or switch between **TV Display Client Mode** and **CMS Dashboard Mode**.
2. **6-Digit Device Pairing & Live Sync**:
   - An unpaired TV screen generates a high-contrast **6-Digit Pairing Code** with a 2.5-second heartbeat loop.
   - In the **CMS Dashboard**, the operator enters the 6-digit code, assigns the screen to a **Playback Zone** (e.g., *Main Lobby*, *Storefront Window*, *Food Court*), and pairs it instantly.
3. **Chunked Video Upload & Playlist Publishing**:
   - The playlist starts completely empty (no pre-loaded demo videos).
   - Operators import a video file from the device or enter a direct MP4/HLS stream URL. The uploader processes the media in **600KB chunks** with real-time progress telemetry, caches it locally, and updates `cms_global/live_state` in Firestore.
   - Display clients download new playlist items to persistent storage and display a **Green Checkmark (`✔`)** badge once verified for offline playback.
4. **Kiosk Playback, Orientation & Watchdog Recovery**:
   - Media3 ExoPlayer loops the playlist gaplessly (`REPEAT_MODE_ALL`).
   - Remote CMS commands immediately adjust **Orientation** (`Landscape`, `Portrait`, `90°`, `180°`, `270°`), **Scale Mode** (`Fill`, `Fit`, `Zoom`), and **Audio Mute** without restarting playback.
   - If a remote stream stalls for **>3.5 seconds** or throws an `IOException`/HTTP 404, the **Buffering Watchdog** logs a diagnostic event and seamlessly switches to the locally cached copy or verified backup stream.
5. **Hidden 5-Tap Admin Panel**:
   - Tapping the top-right corner 5 times within 3 seconds (or pressing the TV remote Menu button) opens the **Admin PIN Modal**.
   - Entering the PIN unlocks the **Admin Settings Panel**, exposing the **Live Diagnostic Log Viewer**, **Local Cache Manager**, **Kiosk Lock Toggle**, **Orientation Overrides**, **OTA Update Status & Manual Rollback**, and **Switch to CMS Dashboard**.
6. **OTA Software Update & Automatic Rollback**:
   - In the **CMS OTA Release Center**, admins publish a new release (e.g., `v1.2.0`, build `3`) via APK/bundle picker or release URL, complete with target zones, release notes, and an optional **Simulate Faulty Update** toggle to test rollback resilience.
   - The **TV Display Client** detects the update during its background sync, downloads the package in chunks, verifies the **SHA-256 checksum**, backs up the current version in **Slot A**, and activates the new release in **Slot B**.
   - A **Post-Update Boot & Playback Watchdog** verifies stability; if package verification, installation, or startup health fails, the client automatically executes an **Instant Rollback** to Slot A and reports the rollback telemetry to the CMS Dashboard.

### Visual Identity & Theme
- **Aesthetic Direction**: **Broadcast Mission Control (Industrial Dark UI)** — engineered for zero-glare control rooms and 10-foot TV legibility, combining obsidian slate surfaces, high-contrast status indicators, and monospaced telemetry readouts.
- **Color Palette & Mood**:
  - `--background`: `#090D16` (Deep Obsidian Broadcast Canvas)
  - `--surface`: `#111827` (Elevated Control Deck Card)
  - `--surface-variant`: `#1E293B` (Interactive Panel / Module Header)
  - `--primary`: `#06B6D4` (Electric Cyan — Active Sync, Primary Actions & Focus Rings)
  - `--secondary`: `#6366F1` (Indigo — Zone Badges & OTA Release Tags)
  - `--accent-success`: `#10B981` (Signal Green — Connected Dot, Offline Cached `✔`, Healthy Slot)
  - `--accent-warning`: `#F59E0B` (Amber — Buffering Watchdog / Staged OTA Download)
  - `--accent-danger`: `#EF4444` (Crimson — Offline Device Dot, Rollback Triggered, Stream Error)
- **Typography & Hierarchy**:
  - **Display & Headings**: `Space Grotesk` (bundled locally via `font-util`) for crisp geometric headers, 6-digit pairing codes, and dashboard metrics.
  - **Telemetry, Logs & Timestamps**: `JetBrains Mono` (bundled locally via `font-util`) for diagnostic logs, chunk progress, SHA-256 hashes, and 2.5s heartbeat counters.
  - **Body & Controls**: `Inter` for clean legibility across compact phones, tablets, and widescreen displays.
- **Component Styling & Layout**:
  - Adaptive Material 3 layout supporting both handheld CMS operation and widescreen/tablet split-pane views (**Live Preview Monitor** alongside **Playlist & Fleet Controls**).
  - Status pills with pulsing green/red telemetry dots, tactile rotation/scale selector chips, and non-blocking instant sync toasts.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Unified Dual-Mode Android App + Firestore Real-Time Core**
  - *Chosen Approach*: Embed both the **TV Display Client** and the **Web-style CMS Dashboard** directly inside the Android app backed by Firebase Firestore (`cms_global/live_state` plus user-scoped collections), while also generating the companion Node.js/Vercel API reference files.
  - *Why*: Lets you test and operate the entire signage network immediately inside the Android emulator—uploading videos, toggling rotation/mute, managing zone permissions, and triggering OTA updates—with live cloud persistence.
- **Decision 2: 600KB Chunked Ingestion & Local File Caching**
  - *Chosen Approach*: Read selected local videos or remote streams in 600KB buffer blocks, writing verified files to `context.filesDir/signage_videos/` and tracking chunk progress in real time.
  - *Why*: Prevents Out-Of-Memory (OOM) crashes on large video creatives and guarantees uninterrupted local playback (`✔`) even if the network drops completely.
- **Decision 3: A/B Slot OTA Update & Automatic Rollback Engine**
  - *Chosen Approach*: Implement a dual-slot OTA manager (`Slot A: Active/Known-Good`, `Slot B: Staged Update`) with SHA-256 verification, Android `PackageInstaller` session support for APK files, and an automated health-check watchdog that rolls back to the previous slot if an update fails verification or boot health checks.
  - *Why*: Unmanned digital signage screens cannot require a technician visit when a bad update is pushed; automated A/B rollback guarantees fleet self-healing.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram
```
┌────────────────────────────────────────────────────────────────────────────┐
│                   PULSECAST DIGITAL SIGNAGE APPLICATION                    │
│                  (Google Sign-In Gate via CredentialManager)               │
└───────────────────┬────────────────────────────────────┬───────────────────┘
                    │                                    │
                    ▼                                    ▼
┌───────────────────────────────────────┐ ┌──────────────────────────────────┐
│      TV DISPLAY CLIENT (KIOSK)        │ │       CMS DASHBOARD VIEW         │
│  ┌─────────────────────────────────┐  │ │  ┌────────────────────────────┐  │
│  │ Media3 ExoPlayer Engine         │  │ │  │ Live Preview Monitor       │  │
│  │ • RepeatMode.ALL (Gapless)      │  │ │  │ • Orientation / Mute Sync  │  │
│  │ • 3.5s Buffering Watchdog       │  │ │  └────────────────────────────┘  │
│  │ • Rotation (0/90/180/270) & Zoom│  │ │  ┌────────────────────────────┐  │
│  └───────────────┬─────────────────┘  │ │  │ 600KB Chunked Uploader &   │  │
│                  ▼                    │ │  │ Playlist Manager (Empty 0) │  │
│  ┌─────────────────────────────────┐  │ │  └────────────────────────────┘  │
│  │ Offline Cache & 2.5s Sync Loop  │  │ │  ┌────────────────────────────┐  │
│  │ • Local Storage (✔ Verified)    │  │ │  │ 6-Digit Pairing & Granular │  │
│  │ • 6-Digit Pairing Screen        │  │ │  │ Zone Permissions Manager   │  │
│  └───────────────┬─────────────────┘  │ │  └────────────────────────────┘  │
│                  ▼                    │ │  ┌────────────────────────────┐  │
│  ┌─────────────────────────────────┐  │ │  │ OTA Release & Rollback Hub │  │
│  │ 5-Tap PIN Admin Settings Panel  │  │ │  │ • Upload Version / SHA-256 │  │
│  │ • Diagnostic Log Viewer         │  │ │  │ • Fleet Health & Rollbacks │  │
│  │ • OTA Slot Status & Rollback    │  │ │  └────────────────────────────┘  │
│  └─────────────────────────────────┘  │ └─────────────────┬────────────────┘
└───────────────────┬───────────────────┘                   │
                    │         Real-Time Sync & Polling      │
                    └───────────────────┬───────────────────┘
                                        ▼
┌────────────────────────────────────────────────────────────────────────────┐
│             FIREBASE FIRESTORE (Custom DB: firestore_database_id)          │
│  • users/{uid}/cms_state/live_state   (Live Playlist, Rotation, Mute)      │
│  • users/{uid}/devices/{deviceId}     (6-Digit Code, Heartbeat, OTA State) │
│  • users/{uid}/zones/{zoneId}         (Playback Zones & Operator Roles)    │
│  • users/{uid}/ota_releases/{relId}   (OTA Versions, Checksums, Rollback)  │
└────────────────────────────────────────────────────────────────────────────┘
```

### Data Model & State
- **`LiveState` (`users/{uid}/cms_state/live_state`)**: Stores global/zone playback configuration including `orientation` (`LANDSCAPE`, `PORTRAIT`, `ROTATE_90`, `ROTATE_180`, `ROTATE_270`), `scaleMode` (`FILL`, `FIT`, `ZOOM`), `isMuted`, `kioskLocked`, `activeZoneId`, `playlist` (list of `VideoItem` objects with `id`, `title`, `sourceUrl`, `localFileName`, `chunkCount`, `sizeBytes`, `zoneId`, `backupUrl`), and `updatedAt`.
- **`SignageDevice` (`users/{uid}/devices/{deviceId}`)**: Tracks each display unit with `pairingCode` (6 digits), `deviceName`, `zoneId`, `isPaired`, `lastHeartbeatMs`, `currentVideoTitle`, `cachedVideoIds`, `installedVersionName`, `installedVersionCode`, `otaStatus` (`UP_TO_DATE`, `DOWNLOADING`, `VERIFYING`, `INSTALLED`, `ROLLED_BACK`), and `previousSlotVersion`.
- **`PlaybackZone` (`users/{uid}/zones/{zoneId}`)**: Defines zone name, allowed operator emails/roles (`ADMIN`, `ZONE_MANAGER`, `VIEWER`), and zone-specific overrides.
- **`OtaRelease` (`users/{uid}/ota_releases/{releaseId}`)**: Stores `versionName`, `versionCode`, `releaseNotes`, `targetZoneId`, `packageUri`, `sha256Checksum`, `chunkCount`, `simulateCorruptHeader` (for deterministic rollback verification), `isMandatory`, and `createdAt`.

### Interactive Component & State Mapping
- **600KB Chunked Video & OTA Uploader**: Reads selected URI or stream in `614,400`-byte chunks, updating a determinate progress bar (`chunksProcessed / totalChunks`), computing a SHA-256 digest, persisting to local storage, and committing metadata to Firestore with an instant sync toast.
- **3.5s Buffering Watchdog & Backup Fallback**: A coroutine monitor observes `Player.STATE_BUFFERING`. If buffering exceeds `3500ms` or `onPlayerError` fires (`IOException` / `HttpDataSource.InvalidResponseCodeException` 404), the player logs the failure to the diagnostic ring buffer and switches the media item to its locally cached file or verified backup stream without interrupting the loop.
- **5-Tap Corner Gesture & PIN Verification**: Tracks tap timestamps in the top-right 72dp hit zone; 5 taps within 3000ms opens the numeric keypad modal. Validating the PIN (`1234` default, customizable in settings) opens the tabbed Admin Settings overlay.
- **Background OTA & Rollback State Machine**: Polls `ota_releases` for `versionCode > currentVersionCode`. Automatically downloads the package in the background, snapshots the current configuration/version into `Slot A (Backup)`, verifies the package integrity and startup health check in `Slot B`, and triggers an automatic rollback to `Slot A` with a high-priority diagnostic log and CMS alert if verification fails.
