const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_user_1";
const STAFF_UID = "staff_user_2";
const STRANGER_UID = "stranger_3";

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
    // Seed admin record
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().collection("admins").doc(ADMIN_UID).set({
        userId: ADMIN_UID,
        email: "admin@shop.com",
        createdAt: new Date(),
      });
    });
  }
});

test("Unauthenticated user: cannot read products or orders", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("products").get());
  await assertFails(unauthDb.collection("orders").get());
});

test("Authenticated staff: can read products and create order", async () => {
  const staffDb = testEnv.authenticatedContext(STAFF_UID, { email: "staff@shop.com", email_verified: true }).firestore();

  // Create valid product
  await assertSucceeds(
    staffDb.collection("products").doc("prod_1").set({
      name: "Milk 1L",
      sku: "8901234567890",
      category: "Dairy",
      costPrice: 2.0,
      sellingPrice: 3.5,
      currentStock: 10,
      minStockAlert: 2,
      unit: "bottle",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Staff can create order
  await assertSucceeds(
    staffDb.collection("orders").doc("ord_1").set({
      orderNumber: "ORD-001",
      customerName: "Alice",
      subtotal: 3.5,
      totalAmount: 3.5,
      paymentMethod: "CASH",
      paymentStatus: "PAID",
      cashierId: STAFF_UID,
      timestamp: new Date(),
    })
  );
});

test("Staff user: cannot delete product (only admin can)", async () => {
  // Seed product
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("products").doc("prod_2").set({
      name: "Bread",
      sku: "8909999999999",
      category: "Bakery",
      costPrice: 1.0,
      sellingPrice: 2.0,
      currentStock: 5,
      minStockAlert: 1,
      unit: "pcs",
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const staffDb = testEnv.authenticatedContext(STAFF_UID, { email: "staff@shop.com", email_verified: true }).firestore();
  await assertFails(staffDb.collection("products").doc("prod_2").delete());

  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { email: "admin@shop.com", email_verified: true }).firestore();
  await assertSucceeds(adminDb.collection("products").doc("prod_2").delete());
});
