# Minimal Status Dot, Green Star Trigger, Built-In PIN Keypad, Bottom-Nav Admin Hub & Web Video Rotation Controls

This update replaces the large top status banner on the Android TV player with a clean, ultra-minimal **Status Dot (Green when live synced, White/Red when offline)** + **Small Green Star (`★`) button** in the top-right corner. Tapping the star opens a **Built-In On-Screen Password Keypad** (no OS keyboard needed), which unlocks a **Full-Screen Admin Hub** featuring a top **Back button** and a **Bottom Navigation Bar** to switch effortlessly between pages, while upgrading both the Android client and the Web CMS Dashboard with per-device **Video Rotation (`+90°`, `-90°`, `0°`, `180°`, `270°`)** and **Scale Modes (`Scale to Fit`, `Full Screen`, `Zoom`)**.

## User Review & Critical Decisions

> [!IMPORTANT]
> **Confirmed Design & UX Decisions:**
> - **Confirmed Decision 1 (Minimal Top-Right Indicator)**: Replaced the wide text pill and old Admin button with a compact top-right indicator containing a live connection dot (**Green** when connected & syncing every 2s with Web CMS, **White** with subtle red ring when disconnected) and a **Small Green Star (`★`)** trigger.
> - **Confirmed Decision 2 (Built-In Password Keypad)**: Tapping the green star opens an in-app password dialog with a built-in numeric/alphanumeric keypad (`0–9`, `Clear`, `⌫ Backspace`, `Unlock`) so you can enter the password (`1234` default) directly inside the app without relying on an external or system keyboard.
> - **Confirmed Decision 3 (Full-Screen Admin Hub with Top Back Button & Bottom Navigation Bar)**: Unlocking opens a clean, user-friendly full-screen Admin Hub with a prominent **`← Back to Player`** button in the top header and a **Material 3 Bottom Navigation Bar** (`NavigationBar` with `windowInsetsPadding(WindowInsets.navigationBars)`) to switch between **5 dedicated pages**:
>   1. **Pair & Sync** (6-digit pairing code, 2s live sync status, server URL)
>   2. **Videos** (Cloud videos with **Download Offline View** & **Play Offline** buttons)
>   3. **Display & Rotate** (`LANDSCAPE` / `PORTRAIT`, **Rotate Video `+90°` / `-90°` / `0°` / `180°` / `270°`**, **Scale to Fit** / **Full Screen** / **Zoom**, Audio Mute, and **Save Settings**)
>   4. **24x7 Boot** (Default HOME Launcher, Device Admin 24x7 Lock, Accessibility 24x7 Guard, System Overlay, 24x7 Battery)
>   5. **OTA & Logs** (A/B Partition OTA status, rollback, and live logs)
> - **Confirmed Decision 4 (Web Dashboard Per-Device Video Rotation & Scale Controls)**: Updated the Web CMS Dashboard (`index.html` & `/api/index.js`) so each connected TV card in the **Connected Android TV Screens** section includes dedicated controls for **Rotate Video (`-90°`, `+90°`, `0°`, `90°`, `180°`, `270°`)**, **Scale to Fit (`FIT`)**, **Full Screen (`FILL`)**, and **Screen Orientation (`LANDSCAPE` / `PORTRAIT`)**, syncing in real time within 2 seconds.

---

## 1. Overview & Core Concept

- **What It Does**: Keeps the live video playback screen 100% unobstructed except for a tiny top-right status dot and green star icon (`★`), provides a self-contained in-app keypad for password entry, presents all device settings in a clean bottom-navigated Admin Hub with a top Back button, and adds full video rotation (`+90°` / `-90°`) and scale (`Scale to Fit` / `Full Screen`) controls to every device card on the Web CMS Dashboard.
- **Target Audience / Persona**: Digital signage operators managing Android TV screens in portrait or landscape who want an unobtrusive player screen, effortless remote/touch password entry, and complete video rotation/scaling control from both the TV and Web Dashboard.
- **Key Value**: Eliminates visual clutter on the signage display, removes OS keyboard popups during password entry, makes Android settings navigation simple and frustration-free in both Portrait and Landscape, and lets operators fix sideways or stretched videos remotely with a single click (`+90°`, `-90°`, `Scale to Fit`, `Full Screen`).

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Unobtrusive Playback Screen**:
     - While playing video or in standby, only a tiny pill in the top-right corner is visible: a **Status Dot** (**Green `#10B981`** when connected and syncing live with Web CMS; **White `#F8FAFC`** when disconnected) next to a **Small Green Star (`★`)** icon button.
  2. **Built-In Password Keypad Modal**:
     - Tapping the green star (`★`) opens a sleek modal with masked PIN dots (`● ● ● ●`) and an **in-built 3x4 keypad** (`1–9`, `Clear`, `0`, `⌫`) plus an `Unlock Admin` button and `Cancel` button. Typing the 4-digit PIN (`1234` default) auto-unlocks or unlocks on tap without opening the Android system keyboard.
  3. **Full-Screen Admin Hub with Top Back Button & Bottom Navigation Bar**:
     - Once unlocked, the Admin Hub opens with:
       - **Top Bar**: Prominent **`← Back`** button to return immediately to video playback, live connection dot (Green/White), device pairing code badge, and a quick `Sync Now` action.
       - **Center Content Area**: Clean, spacious cards for the selected page.
       - **Bottom Navigation Bar**: 5 clear bottom tabs (`Pair`, `Videos`, `Rotate`, `24x7 Perms`, `OTA`) so switching pages takes a single tap.
  4. **Web CMS Dashboard Device Controls**:
     - In each device card on the Web CMS Dashboard, operators can click **`Rotate -90°`**, **`Rotate +90°`**, **`0°` / `90°` / `180°` / `270°`**, **`Scale to Fit`**, or **`Full Screen`**, and the Android TV applies the video rotation and scaling within 2 seconds.

- **Visual Identity & Theme**:
  - *Aesthetic Direction*: Broadcast Obsidian Dark (`#090D16` background, `#111827` cards) with crisp `#10B981` Signal Green and `#00E5FF` Electric Cyan accents.
  - *Component Styling & Layout*: Material 3 `Scaffold` with top header bar and bottom `NavigationBar` respecting `WindowInsets.safeDrawing` and `WindowInsets.navigationBars`.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Minimal Top-Right Status Dot + Green Star (`★`) vs. Wide Status Banner**
  - *Chosen Approach*: A compact 36dp-tall pill in the top-right corner containing only the status dot (Green = live connected, White = offline) and the green star icon (`★`), removing the left-edge tab and wide text banner.
  - *Why*: Digital signage screens should display content cleanly without large HUD banners covering the video or wrapping awkwardly in Portrait mode.
- **Decision 2: Built-In On-Screen Keypad vs. System `OutlinedTextField` Keyboard**
  - *Chosen Approach*: Custom Compose keypad grid inside the password modal (also supporting physical TV remote number keys `0–9`).
  - *Why*: System keyboards on Android TV / kiosk displays often cover the screen or fail to appear in locked launcher mode; a built-in keypad works reliably everywhere with touch, mouse, or TV remote.
- **Decision 3: Separate `videoRotateDegrees` (`0° / 90° / 180° / 270°` + `+90° / -90°`) from `screenOrientation` (`LANDSCAPE / PORTRAIT`)**
  - *Chosen Approach*: Store and sync `videoRotateDegrees` independently alongside `orientation` and `scaleMode` (`FIT` = Scale to Fit, `FILL` = Full Screen, `ZOOM` = Zoom Crop) in both the Android client and the Web CMS backend (`/api/device-control` & `/api/heartbeat`).
  - *Why*: Allows rotating the video stream itself (`+90°` or `-90°`) inside any screen orientation and scaling it to fit or fill full-screen without distortion.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                 ANDROID TV DISPLAY CLIENT (Full-Screen Video)                │
│                                                                              │
│  Top-Right Corner:  [ ● Status Dot (Green=Live / White=Offline) | ★ Star ]   │
└──────────────────────────────────────┬───────────────────────────────────────┘
                                       │ Tap ★ Star
                                       ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                 BUILT-IN PASSWORD KEYPAD MODAL (No OS Keyboard)              │
│         [ ● ● ● ● ]  +  Built-In 1-9 / 0 / Clear / ⌫ Keypad + [Back]         │
└──────────────────────────────────────┬───────────────────────────────────────┘
                                       │ PIN Verified (1234)
                                       ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│               FULL-SCREEN ADMIN HUB (Top Back Button + Bottom Nav)           │
│  Top Header:  [ ← Back to Player ]   ● Live Sync (Code: 127489)  [Sync Now]  │
│  --------------------------------------------------------------------------  │
│  Page Content:                                                               │
│   • Page 1 (Pair & Sync): 6-Digit Code, TV Name, Server URL, 2s Live Sync    │
│   • Page 2 (Videos): Cloud Videos + [Download Offline View] + [Play Offline] │
│   • Page 3 (Rotate & Scale): Landscape/Portrait, Video Rotate (-90° / +90° / │
│     0° / 90° / 180° / 270°), Scale to Fit / Full Screen / Zoom, [Save]       │
│   • Page 4 (24x7 Boot): Default HOME Launcher, Device Admin, Accessibility,  │
│     System Overlay, Unrestricted Battery                                     │
│   • Page 5 (OTA & Logs): A/B Slot Status, Rollback, Live Diagnostic Logs     │
│  --------------------------------------------------------------------------  │
│  Bottom Nav Bar: [ 🔗 Pair ] [ 🎬 Videos ] [ 🔄 Rotate ] [ 🛡 24x7 ] [ ⚡ OTA ] │
└──────────────────────────────────────────────────────────────────────────────┘
```

- **Interactive Component & State Mapping**:
  - **Top-Right Minimal Indicator**: Renders a `10.dp` circle (`SignalGreen` `#10B981` when `isWebCmsConnected == true`, `Color.White` `#F8FAFC` when `false`) paired with a `SignalGreen` Star icon (`Icons.Default.Star`). Tapping opens the Built-In Keypad PIN dialog (`viewModel.openStarAdminPrompt()`).
  - **Built-In Keypad Dialog**: Maintains `enteredPin` state in Compose; tapping `0–9` appends digits (auto-verifying at 4 digits or when tapping `Unlock`), `⌫` removes the last digit, `Clear` resets, and `Back / Cancel` closes the dialog.
  - **Admin Hub (`AdminFullScreenHub`)**: Uses a `Scaffold` with a top bar containing a clear `← Back` button (`onCloseAdmin`) and a bottom `NavigationBar` with 5 `NavigationBarItem` entries.
  - **Video Rotation (`+90°` / `-90°`) & Scale (`Scale to Fit` / `Full Screen`)**:
    - Added `rotateVideoByDelta(-90)` and `rotateVideoByDelta(+90)` helpers in `SignageViewModel` and on each device card in the Web CMS Dashboard (`index.html` & `/api/index.js`), syncing `videoRotateDegrees` (`0`, `90`, `180`, `270`), `orientation` (`LANDSCAPE`, `PORTRAIT`), and `scaleMode` (`FIT` = Scale to Fit, `FILL` = Full Screen, `ZOOM` = Zoom).
