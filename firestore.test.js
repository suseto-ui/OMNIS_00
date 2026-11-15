const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const ROOT_ADMIN_UID = "root_admin_001";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      host: emulatorHost,
      port: emulatorPort,
      rules: fs.readFileSync("firestore.rules", "utf8"),
    },
  });
});

beforeEach(async () => {
  await testEnv.clearFirestore();
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

test("Unauthenticated user cannot read user profile or protected collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("system").doc("metrics").get());
  await assertFails(unauthDb.collection("audit_logs").doc("log_1").get());
  await assertFails(unauthDb.collection("unknown_collection").doc("doc_1").get());
});

test("Alice can create her own profile with valid data", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@example.com",
      displayName: "Alice",
      createdAt: new Date(),
    })
  );
});

test("Alice cannot create Bob's profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      email: "bob@example.com",
      createdAt: new Date(),
    })
  );
});

test("Standard user cannot self-escalate role or spoof admin email without verification", async () => {
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true }).firestore();
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      email: "bob@example.com",
      role: "ADMIN_OPERATOR",
      createdAt: new Date(),
    })
  );

  // Unverified email spoofing susetofu@gmail.com must be rejected
  const spoofDb = testEnv.authenticatedContext("spoof_uid", {
    email: "susetofu@gmail.com",
    email_verified: false,
  }).firestore();
  await assertFails(
    spoofDb.collection("users").doc("spoof_uid").set({
      userId: "spoof_uid",
      email: "susetofu@gmail.com",
      role: "ADMIN_OPERATOR",
      createdAt: new Date(),
    })
  );
});

test("Verified root admin (susetofu@gmail.com) can create ADMIN_OPERATOR profile and update system metrics", async () => {
  const adminDb = testEnv.authenticatedContext(ROOT_ADMIN_UID, {
    email: "susetofu@gmail.com",
    email_verified: true,
  }).firestore();

  await assertSucceeds(
    adminDb.collection("users").doc(ROOT_ADMIN_UID).set({
      userId: ROOT_ADMIN_UID,
      email: "susetofu@gmail.com",
      role: "ADMIN_OPERATOR",
      createdAt: new Date(),
    })
  );

  await assertSucceeds(
    adminDb.collection("system").doc("metrics").set({
      tokens: 1024,
      syncStatus: "ONLINE",
      threats: 0,
      updatedAt: new Date(),
    })
  );

  // Standard user can read system metrics but cannot write them
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("system").doc("metrics").get());
  await assertFails(
    aliceDb.collection("system").doc("metrics").set({
      tokens: 0,
      syncStatus: "COMPROMISED",
      threats: 99,
    })
  );
});

test("Alice can create and read her own record and goal; Bob is denied cross-user access", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const recordDoc = aliceDb.collection("users").doc(ALICE_UID).collection("records").doc("rec_1");
  await assertSucceeds(
    recordDoc.set({
      id: "rec_1",
      userId: ALICE_UID,
      query: "Analyze systemic stability",
      response: "System stable with 98% confidence",
      domain: "SYSTEMS",
      confidenceScore: 0.98,
      createdAt: new Date(),
    })
  );
  await assertSucceeds(recordDoc.get());

  const goalDoc = aliceDb.collection("users").doc(ALICE_UID).collection("goals").doc("goal_1");
  await assertSucceeds(
    goalDoc.set({
      id: "goal_1",
      userId: ALICE_UID,
      title: "NIS2 Resilience Compliance",
      status: "active",
      progress: 85.0,
      createdAt: new Date(),
    })
  );

  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("records").doc("rec_1").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("goals").doc("goal_1").get()
  );
});

test("Alice cannot inject invalid fields, oversized IDs, or corrupted schema into records", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("records").doc("rec_invalid").set({
      id: "rec_invalid",
      userId: ALICE_UID,
      query: "Invalid payload",
      createdAt: new Date(),
      ghostField: "malicious_injection",
    })
  );

  const oversizedId = "a".repeat(130);
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("records").doc(oversizedId).set({
      id: oversizedId,
      userId: ALICE_UID,
      query: "Valid query",
      response: "Valid response",
      createdAt: new Date(),
    })
  );
});

test("Audit logs and Proof of Necessity ledgers allow owner create and forbid update/delete", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const auditRef = aliceDb.collection("audit_logs").doc("audit_1");
  await assertSucceeds(
    auditRef.set({
      userId: ALICE_UID,
      actionType: "AKGE_EVAL",
      commitmentHash: "a54e10894014d3b99c28dd23255ea82a89d49de005ab779f41998cf94988a4c8",
      timestamp: Date.now(),
      overrideActive: false,
    })
  );

  await assertFails(auditRef.update({ overrideActive: true }));
  await assertFails(auditRef.delete());
  await assertFails(bobDb.collection("audit_logs").doc("audit_1").get());

  // Spoofed userId on audit_logs create must fail
  await assertFails(
    bobDb.collection("audit_logs").doc("audit_spoof").set({
      userId: ALICE_UID,
      actionType: "SPOOF",
      commitmentHash: "0123456789abcdef0123456789abcdef",
      timestamp: Date.now(),
    })
  );

  const ponRef = aliceDb.collection("proof_of_necessity").doc("pon_1");
  await assertSucceeds(
    ponRef.set({
      userId: ALICE_UID,
      trigger_vector: "NIS2_BUDGET_REDUCTION",
      exclusion_proof: "CSR causality prevents bypass",
      minimization_check: "Zero extra schema surfaces added",
      timestamp: Date.now(),
      system_tension_sigma: 0.42,
    })
  );

  await assertFails(ponRef.update({ system_tension_sigma: 0.0 }));
  await assertFails(ponRef.delete());
});

test("O.M.N.I.S. sessions and agent deliberations enforce ownership isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const sessionRef = aliceDb.collection("omnis_sessions").doc("sess_1");
  await assertSucceeds(
    sessionRef.set({
      sessionId: "sess_1",
      userId: ALICE_UID,
      status: "ACTIVE",
      focusedDomain: "SEC",
      createdAt: new Date(),
    })
  );

  // Bob cannot create a session claiming Alice's userId or read Alice's session
  await assertFails(
    bobDb.collection("omnis_sessions").doc("sess_hijack").set({
      sessionId: "sess_hijack",
      userId: ALICE_UID,
      status: "ACTIVE",
      createdAt: new Date(),
    })
  );
  await assertFails(bobDb.collection("omnis_sessions").doc("sess_1").get());

  // Agent deliberation create & list scoped by userId
  await assertSucceeds(
    aliceDb.collection("agent_deliberations").doc("delib_1").set({
      userId: ALICE_UID,
      debateId: "debate_1",
      timestamp: Date.now(),
      verdict: "APPROVED",
      overrideActive: false,
    })
  );
  await assertSucceeds(
    aliceDb.collection("agent_deliberations").where("userId", "==", ALICE_UID).get()
  );
  await assertFails(bobDb.collection("agent_deliberations").doc("delib_1").get());
  await assertFails(bobDb.collection("agent_deliberations").get());
});

test("Override history allows owner create with hash-chain seal and blocks update or delete", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const histRef = aliceDb.collection("users").doc(ALICE_UID).collection("override_history").doc("ov_1");
  await assertSucceeds(
    histRef.set({
      id: "ov_1",
      timestamp: Date.now(),
      reason: "Emergency incident containment",
      auditCommitmentSeal: "a54e10894014d3b99c28dd23255ea82a89d49de005ab779f41998cf94988a4c8",
      prevCommitmentHash: "GENESIS",
      adminUser: "susetofu@gmail.com",
    })
  );
  await assertFails(histRef.update({ reason: "Tampered" }));
  await assertFails(histRef.delete());

  const chainedAuditRef = aliceDb.collection("audit_logs").doc("audit_chained_1");
  await assertSucceeds(
    chainedAuditRef.set({
      userId: ALICE_UID,
      actionType: "AKGE_CHAINED_EVAL",
      commitmentHash: "b64e10894014d3b99c28dd23255ea82a89d49de005ab779f41998cf94988a4c9",
      prevCommitmentHash: "a54e10894014d3b99c28dd23255ea82a89d49de005ab779f41998cf94988a4c8",
      commitmentAlgorithm: "HMAC-SHA256-HASH-CHAIN-v4.5",
      timestamp: Date.now(),
      overrideActive: false,
    })
  );
  await assertFails(chainedAuditRef.update({ prevCommitmentHash: "TAMPERED" }));
});
