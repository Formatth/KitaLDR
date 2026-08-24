const express = require("express");
const admin = require("firebase-admin");

const PORT = Number(process.env.PORT || 3000);
const SERVICE_ACCOUNT_JSON = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;

if (!SERVICE_ACCOUNT_JSON) {
  console.error("FIREBASE_SERVICE_ACCOUNT_JSON is required.");
  process.exit(1);
}

let serviceAccount;
try {
  serviceAccount = JSON.parse(SERVICE_ACCOUNT_JSON);
} catch (error) {
  console.error("FIREBASE_SERVICE_ACCOUNT_JSON is not valid JSON.", error);
  process.exit(1);
}

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
});

const db = admin.firestore();
const messaging = admin.messaging();
const app = express();
app.use(express.json());

const seenActions = new Set();

async function sendActionNotification(action, actionId, coupleId) {
  const { type, senderUid, recipientUid } = action;
  if (!type || !senderUid || !recipientUid || !coupleId || senderUid === recipientUid) return;

  const coupleSnap = await db.collection("couples").doc(coupleId).get();
  if (!coupleSnap.exists || coupleSnap.data()?.status !== "active") return;

  const couple = coupleSnap.data();
  const isMember = couple.memberA === senderUid || couple.memberB === senderUid;
  const recipientIsMember = couple.memberA === recipientUid || couple.memberB === recipientUid;
  if (!isMember || !recipientIsMember) return;

  const [tokenSnap, senderSnap] = await Promise.all([
    db.collection("deviceTokens").doc(recipientUid).get(),
    db.collection("users").doc(senderUid).get(),
  ]);

  const token = tokenSnap.data()?.token;
  if (!token) {
    console.warn("FCM token not found", recipientUid);
    return;
  }

  const senderName = senderSnap.data()?.displayName || "My Love";
  const title = type === "POKE" ? `${senderName} poked you! ❤️` : "KitaLDR";
  const body = type === "POKE" ? "Your person sent you a poke." : "You have a new action.";

  try {
    const messageId = await messaging.send({
      token,
      notification: { title, body },
      data: {
        type: String(type),
        senderUid: String(senderUid),
        senderName: String(senderName),
        recipientUid: String(recipientUid),
        coupleId: String(coupleId),
        actionId: String(actionId),
      },
      android: {
        priority: "high",
        notification: {
          channelId: "kitaldr_actions",
          sound: "default",
          priority: "high",
        },
      },
    });

    console.log(`POKE notification sent: ${messageId}`);
  } catch (error) {
    const code = error?.code || "";
    if (code.includes("registration-token-not-registered") || code.includes("invalid-argument")) {
      await db.collection("deviceTokens").doc(recipientUid).delete();
      console.warn("Removed invalid FCM token", recipientUid);
    }
    console.error("FCM send failed", error);
  }
}

function startActionListener() {
  console.log("Starting Firestore action listener...");

  return db.collectionGroup("actions").onSnapshot(
    (snapshot) => {
      for (const change of snapshot.docChanges()) {
        if (change.type !== "added") continue;

        const actionId = change.doc.id;
        if (seenActions.has(actionId)) continue;
        seenActions.add(actionId);

        const pathParts = change.doc.ref.path.split("/");
        const coupleId = pathParts[1];
        const action = change.doc.data();

        console.log(`ACTION RECEIVED: ${action.type || "UNKNOWN"} ${actionId}`);
        sendActionNotification(action, actionId, coupleId).catch((error) => {
          console.error("Action processing failed", error);
        });
      }

      // Prevent unbounded memory growth while keeping a small recent-action cache.
      if (seenActions.size > 5000) {
        const first = seenActions.values().next().value;
        seenActions.delete(first);
      }
    },
    (error) => {
      console.error("Firestore listener error", error);
    }
  );
}

app.get("/health", (_req, res) => {
  res.json({ ok: true, service: "kitaldr-local-fcm" });
});

app.post("/test/push", async (req, res) => {
  try {
    const { uid } = req.body || {};
    if (!uid) return res.status(400).json({ success: false, error: "uid is required" });

    const tokenSnap = await db.collection("deviceTokens").doc(uid).get();
    const token = tokenSnap.data()?.token;
    if (!token) return res.status(404).json({ success: false, error: "FCM token not found for this uid" });

    const messageId = await messaging.send({
      token,
      notification: { title: "KitaLDR Test", body: "FCM test from local server." },
      android: {
        priority: "high",
        notification: { channelId: "kitaldr_actions", sound: "default", priority: "high" },
      },
    });

    res.json({ success: true, messageId });
  } catch (error) {
    console.error("/test/push failed", error);
    res.status(500).json({ success: false, error: error?.code || error?.message || String(error) });
  }
});

app.listen(PORT, () => {
  console.log(`KitaLDR local FCM server listening on http://127.0.0.1:${PORT}`);
  startActionListener();
});
