const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_lord_1";
const BOB_UID = "bob_lord_2";
const CHARLIE_UID = "charlie_lord_3";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(offsetMs = 2000) {
  return new Date(Date.now() - offsetMs);
}

function makeEmpire(uid, overrides = {}) {
  const ts = pastDate(5000);
  return {
    userId: uid,
    lordName: "Lord Valerius",
    food: 1200,
    timber: 1200,
    stone: 900,
    gold: 600,
    speedups: 3,
    lastHarvestEpochMs: Date.now() - 5000,
    coordX: 42,
    coordY: 58,
    power: 1450,
    keepLevel: 1,
    barracksLevel: 1,
    stablesLevel: 1,
    archeryLevel: 1,
    siegeLevel: 1,
    allianceHallLevel: 1,
    farmLevel: 1,
    lumberLevel: 1,
    quarryLevel: 1,
    mintLevel: 1,
    infantry: 60,
    cavalry: 20,
    archers: 40,
    siege: 5,
    activeBuildKey: "",
    activeBuildEndsAtMs: 0,
    activeTrainKey: "",
    activeTrainQty: 0,
    activeTrainEndsAtMs: 0,
    allianceId: "",
    allianceTag: "",
    allianceRole: "NONE",
    honor: 0,
    shieldUntilEpochMs: 0,
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

function makeKingdom(uid, overrides = {}) {
  const ts = pastDate(5000);
  return {
    ownerUid: uid,
    realmId: "realm_1",
    publicVisible: true,
    lordName: "Lord Valerius",
    keepLevel: 1,
    power: 1450,
    garrisonPower: 650,
    coordX: 42,
    coordY: 58,
    allianceId: "",
    allianceTag: "",
    shieldUntilEpochMs: 0,
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

function makeAlliance(leaderUid, overrides = {}) {
  const ts = pastDate(5000);
  return {
    realmId: "realm_1",
    name: "Obsidian Vanguard",
    tag: "OBV",
    motto: "Iron and Gold Eternal",
    crestColor: "CRIMSON",
    leaderUid,
    officerUids: [],
    memberUids: [leaderUid],
    memberCount: 1,
    totalPower: 1450,
    openJoin: true,
    techMarchLevel: 0,
    techYieldLevel: 0,
    techAttackLevel: 0,
    techDefenseLevel: 0,
    techXp: 0,
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

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

test("Unauthenticated user: denied across all Crownfall collections", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("users").doc(ALICE_UID).get());
  await assertFails(unauth.collection("kingdoms").where("realmId", "==", "realm_1").where("publicVisible", "==", true).get());
  await assertFails(unauth.collection("alliances").where("realmId", "==", "realm_1").get());
  await assertFails(unauth.collection("marches").where("realmId", "==", "realm_1").get());
  await assertFails(unauth.collection("battleLogs").where("realmId", "==", "realm_1").get());
});

test("Private PlayerEmpire (/users/{uid}): owner create/update works; cross-user & ghost fields denied", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(alice.collection("users").doc(ALICE_UID).set(makeEmpire(ALICE_UID)));

  // Partial update of economy & building timer
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).update({
      food: 1500,
      activeBuildKey: "KEEP",
      activeBuildEndsAtMs: Date.now() + 30000,
      updatedAt: pastDate(1000),
    })
  );

  // Reject ghost field ("Shadow Update")
  await assertFails(
    alice.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: pastDate(500),
    })
  );

  // Reject immutable createdAt tamper
  await assertFails(
    alice.collection("users").doc(ALICE_UID).update({
      createdAt: pastDate(500),
      updatedAt: pastDate(500),
    })
  );

  // Reject future timestamp
  await assertFails(
    alice.collection("users").doc(ALICE_UID).update({
      food: 2000,
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Reject cross-user read and write
  await assertFails(bob.collection("users").doc(ALICE_UID).get());
  await assertFails(bob.collection("users").doc(ALICE_UID).update({ food: 0, updatedAt: pastDate(500) }));
});

test("RealmKingdom (/kingdoms/{uid}): realm-scoped query succeeds; unfiltered query & cross-user write fail", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(alice.collection("kingdoms").doc(ALICE_UID).set(makeKingdom(ALICE_UID)));

  // Filtered realm query succeeds for any signed-in player
  await assertSucceeds(
    bob.collection("kingdoms")
      .where("realmId", "==", "realm_1")
      .where("publicVisible", "==", true)
      .get()
  );

  // Unfiltered list query fails
  await assertFails(bob.collection("kingdoms").get());

  // Cross-user update fails
  await assertFails(
    bob.collection("kingdoms").doc(ALICE_UID).update({
      power: 0,
      updatedAt: pastDate(500),
    })
  );

  // Owner stats sync succeeds
  await assertSucceeds(
    alice.collection("kingdoms").doc(ALICE_UID).update({
      power: 2100,
      keepLevel: 2,
      updatedAt: pastDate(500),
    })
  );
});

test("WorldMarch (/marches/{id}): create, resolve terminal lock, and cross-user protection", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate(5000);

  await assertSucceeds(
    alice.collection("marches").doc("march_1").set({
      ownerUid: ALICE_UID,
      realmId: "realm_1",
      ownerName: "Lord Valerius",
      allianceId: "",
      allianceTag: "",
      marchType: "RAID",
      targetType: "BASTION",
      targetId: "bastion_1",
      targetName: "Obsidian Watch",
      fromX: 42,
      fromY: 58,
      toX: 48,
      toY: 60,
      troopPower: 850,
      infantrySent: 50,
      cavalrySent: 20,
      archersSent: 20,
      siegeSent: 5,
      departedAtEpochMs: Date.now() - 10000,
      arrivesAtEpochMs: Date.now() + 10000,
      status: "MARCHING",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Realm list query succeeds
  await assertSucceeds(bob.collection("marches").where("realmId", "==", "realm_1").get());
  await assertFails(bob.collection("marches").get());

  // Cross-user cannot recall Alice's march
  await assertFails(
    bob.collection("marches").doc("march_1").update({
      status: "RECALLED",
      updatedAt: pastDate(500),
    })
  );

  // Owner resolves march
  await assertSucceeds(
    alice.collection("marches").doc("march_1").update({
      status: "RESOLVED",
      updatedAt: pastDate(500),
    })
  );

  // Terminal state lock: cannot update after RESOLVED
  await assertFails(
    alice.collection("marches").doc("march_1").update({
      status: "RECALLED",
      updatedAt: pastDate(100),
    })
  );
});

test("Alliance lifecycle: create, join, chat, rally, aid, tech upgrade, hierarchy & non-member denial", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlie = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const allianceId = "alliance_obv";
  const ts = pastDate(5000);

  // Alice creates alliance
  await assertSucceeds(
    alice.collection("alliances").doc(allianceId).set(makeAlliance(ALICE_UID))
  );

  // Bob joins open alliance
  await assertSucceeds(
    bob.collection("alliances").doc(allianceId).update({
      memberUids: [ALICE_UID, BOB_UID],
      memberCount: 2,
      totalPower: 2900,
      updatedAt: pastDate(1000),
    })
  );

  // Alice promotes Bob to Officer
  await assertSucceeds(
    alice.collection("alliances").doc(allianceId).update({
      officerUids: [BOB_UID],
      updatedAt: pastDate(900),
    })
  );

  // Member chat message succeeds; non-member Charlie cannot read or post
  await assertSucceeds(
    alice.collection("alliances").doc(allianceId).collection("messages").doc("msg_1").set({
      allianceId,
      senderUid: ALICE_UID,
      senderName: "Lord Valerius",
      senderRole: "LEADER",
      body: "Rally on the northern bastion!",
      messageType: "CHAT",
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: ts,
    })
  );
  await assertSucceeds(
    bob.collection("alliances").doc(allianceId).collection("messages")
      .where("memberUids", "array-contains", BOB_UID)
      .get()
  );
  await assertFails(
    charlie.collection("alliances").doc(allianceId).collection("messages").doc("msg_1").get()
  );
  await assertFails(
    charlie.collection("alliances").doc(allianceId).collection("messages").doc("msg_2").set({
      allianceId,
      senderUid: CHARLIE_UID,
      senderName: "Intruder",
      senderRole: "MEMBER",
      body: "Spying!",
      messageType: "CHAT",
      memberUids: [ALICE_UID, BOB_UID, CHARLIE_UID],
      createdAt: ts,
    })
  );

  // Coordinated Rally: Alice creates, Bob joins, Alice resolves (then terminal lock!)
  await assertSucceeds(
    alice.collection("alliances").doc(allianceId).collection("rallies").doc("rally_1").set({
      allianceId,
      leaderUid: ALICE_UID,
      leaderName: "Lord Valerius",
      targetType: "BASTION",
      targetId: "bastion_3",
      targetName: "Dread Citadel",
      targetX: 60,
      targetY: 30,
      targetPower: 3500,
      totalPower: 1800,
      participantUids: [ALICE_UID],
      participantSummary: "Lord Valerius (1.8k)",
      launchAtEpochMs: Date.now() + 60000,
      status: "GATHERING",
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    bob.collection("alliances").doc(allianceId).collection("rallies").doc("rally_1").update({
      participantUids: [ALICE_UID, BOB_UID],
      participantSummary: "Lord Valerius (1.8k), Bob (2.0k)",
      totalPower: 3800,
      updatedAt: pastDate(500),
    })
  );
  await assertSucceeds(
    alice.collection("alliances").doc(allianceId).collection("rallies").doc("rally_1").update({
      status: "RESOLVED",
      updatedAt: pastDate(400),
    })
  );
  await assertFails(
    alice.collection("alliances").doc(allianceId).collection("rallies").doc("rally_1").update({
      status: "CANCELLED",
      updatedAt: pastDate(200),
    })
  );

  // Aid Request: Alice requests, Bob donates; Alice cannot self-donate
  await assertSucceeds(
    alice.collection("alliances").doc(allianceId).collection("aidRequests").doc("aid_1").set({
      allianceId,
      requesterUid: ALICE_UID,
      requesterName: "Lord Valerius",
      resourceType: "GOLD",
      requestedAmount: 500,
      fulfilledAmount: 0,
      status: "OPEN",
      donorUids: [],
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(
    alice.collection("alliances").doc(allianceId).collection("aidRequests").doc("aid_1").update({
      fulfilledAmount: 500,
      status: "FULFILLED",
      donorUids: [ALICE_UID],
      updatedAt: pastDate(300),
    })
  );
  await assertSucceeds(
    bob.collection("alliances").doc(allianceId).collection("aidRequests").doc("aid_1").update({
      fulfilledAmount: 500,
      status: "FULFILLED",
      donorUids: [BOB_UID],
      updatedAt: pastDate(300),
    })
  );

  // Officer Bob invites Charlie; Charlie accepts
  const inviteId = `${allianceId}_${CHARLIE_UID}`;
  await assertSucceeds(
    bob.collection("allianceInvites").doc(inviteId).set({
      allianceId,
      allianceName: "Obsidian Vanguard",
      allianceTag: "OBV",
      inviterUid: BOB_UID,
      inviterName: "Lord Bob",
      targetUid: CHARLIE_UID,
      targetName: "Lord Charlie",
      status: "PENDING",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    charlie.collection("allianceInvites").where("targetUid", "==", CHARLIE_UID).get()
  );
  await assertSucceeds(
    charlie.collection("allianceInvites").doc(inviteId).update({
      status: "ACCEPTED",
      updatedAt: pastDate(200),
    })
  );
});

test("BattleLog (/battleLogs/{id}): create & realm query succeed; immutable after creation", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate(2000);

  await assertSucceeds(
    alice.collection("battleLogs").doc("log_1").set({
      realmId: "realm_1",
      attackerUid: ALICE_UID,
      attackerName: "Lord Valerius",
      attackerTag: "OBV",
      defenderUid: "bastion_1",
      defenderName: "Obsidian Watch",
      defenderTag: "NPC",
      battleType: "RAID",
      victory: true,
      attackerPower: 1450,
      defenderPower: 900,
      troopsLost: 12,
      lootFood: 450,
      lootTimber: 320,
      lootStone: 210,
      lootGold: 180,
      allianceId: "alliance_obv",
      createdAt: ts,
    })
  );

  await assertSucceeds(bob.collection("battleLogs").where("realmId", "==", "realm_1").get());
  await assertFails(alice.collection("battleLogs").doc("log_1").update({ victory: false }));
  await assertFails(alice.collection("battleLogs").doc("log_1").delete());
});
