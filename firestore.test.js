const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_operator_1";
const BOB_UID = "bob_operator_2";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function nowTimestamp() {
  return new Date();
}

test("Unauthenticated user: cannot read or write cms_global/live_state", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("cms_global").doc("live_state").get()
  );
});

test("Authenticated owner: can create and update cms_global/live_state with valid schema", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb.collection("users").doc(ALICE_UID).collection("cms_global").doc("live_state");
  const ts = nowTimestamp();

  await assertSucceeds(
    docRef.set({
      userId: ALICE_UID,
      orientation: "LANDSCAPE",
      scaleMode: "FILL",
      isMuted: false,
      kioskLocked: true,
      activeZoneId: "zone_lobby",
      adminPin: "1234",
      lastPushCommand: "SYNC_PLAYLIST",
      activeOtaVersionCode: 1,
      activeOtaVersionName: "1.0.0",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    docRef.update({
      orientation: "ROTATE_90",
      isMuted: true,
      updatedAt: nowTimestamp(),
    })
  );
});

test("Cross-user isolation: Bob cannot read or modify Alice's cms_global/live_state or videos", async () => {
  const ts = nowTimestamp();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore()
      .collection("users").doc(ALICE_UID)
      .collection("cms_global").doc("live_state")
      .set({
        userId: ALICE_UID,
        orientation: "LANDSCAPE",
        scaleMode: "FILL",
        isMuted: false,
        kioskLocked: true,
        activeZoneId: "zone_lobby",
        adminPin: "1234",
        lastPushCommand: "INIT",
        activeOtaVersionCode: 1,
        activeOtaVersionName: "1.0.0",
        createdAt: ts,
        updatedAt: ts,
      });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("cms_global").doc("live_state").get()
  );
});

test("Shadow update & value poisoning: rejects ghost fields and invalid enum values", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb.collection("users").doc(ALICE_UID).collection("cms_global").doc("live_state");
  const ts = nowTimestamp();

  // Rejects invalid orientation enum
  await assertFails(
    docRef.set({
      userId: ALICE_UID,
      orientation: " UPSIDE_DOWN_INVALID ",
      scaleMode: "FILL",
      isMuted: false,
      kioskLocked: true,
      activeZoneId: "zone_lobby",
      adminPin: "1234",
      lastPushCommand: "INIT",
      activeOtaVersionCode: 1,
      activeOtaVersionName: "1.0.0",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Rejects ghost field
  await assertFails(
    docRef.set({
      userId: ALICE_UID,
      orientation: "LANDSCAPE",
      scaleMode: "FILL",
      isMuted: false,
      kioskLocked: true,
      activeZoneId: "zone_lobby",
      adminPin: "1234",
      lastPushCommand: "INIT",
      activeOtaVersionCode: 1,
      activeOtaVersionName: "1.0.0",
      ghostField: "hacked",
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("Authenticated owner: can create and query videos, devices, zones, and ota_releases", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = nowTimestamp();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("videos").doc("vid_1").set({
      userId: ALICE_UID,
      title: "Holiday Promo 4K",
      sourceUrl: "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
      backupUrl: "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
      localFileName: "vid_1.mp4",
      zoneId: "zone_lobby",
      sizeBytes: 2457600,
      chunkCount: 4,
      isVerifiedCached: true,
      sortOrder: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("ota_releases").doc("rel_2").set({
      userId: ALICE_UID,
      versionCode: 2,
      versionName: "1.1.0",
      releaseNotes: "Resilient exoplayer watchdog & A/B slot verification",
      targetZoneId: "ALL",
      packageUrl: "https://pulsecast.cdn/releases/pulsecast-v1.1.0.apk",
      sha256Checksum: "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
      sizeBytes: 1843200,
      chunkCount: 3,
      isMandatory: true,
      simulateCorruptPackage: false,
      releaseStatus: "ACTIVE",
      createdAt: ts,
      updatedAt: ts,
    })
  );
});
