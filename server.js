const express = require("express");
const { initializeApp, cert, getApps } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

const app = express();
const PORT = process.env.PORT || 3000;

const serviceAccount = require("./firebase-service-account.json");

const firebaseApp =
  getApps().length === 0
    ? initializeApp({
        credential: cert(serviceAccount),
      })
    : getApps()[0];

const db = getFirestore(firebaseApp);
const messaging = getMessaging(firebaseApp);

app.use(express.json());

/* =========================================================
   BASIC API
   ========================================================= */

app.get("/", (req, res) => {
  res.json({
    app: "KitaLDR API",
    status: "online",
  });
});

app.get("/health", (req, res) => {
  res.json({
    status: "ok",
    service: "kitaldr-api",
  });
});

/* =========================================================
   FIRESTORE TEST
   ========================================================= */

app.get("/test/firestore", async (req, res) => {
  try {
    const snapshot = await db
      .collection("users")
      .limit(1)
      .get();

    res.json({
      success: true,
      message: "Firestore connection works",
      documentsFound: snapshot.size,
    });
  } catch (error) {
    console.error("Firestore error:", error);

    res.status(500).json({
      success: false,
      error: error.message,
    });
  }
});

/* =========================================================
   MANUAL FCM TEST
   ========================================================= */

app.post("/test/push", async (req, res) => {
  try {
    const { uid } = req.body;

    if (!uid) {
      return res.status(400).json({
        success: false,
        error: "uid is required",
      });
    }

    const tokenDoc = await db
      .collection("deviceTokens")
      .doc(uid)
      .get();

    if (!tokenDoc.exists) {
      return res.status(404).json({
        success: false,
        error: "FCM token not found for this uid",
      });
    }

    const token = tokenDoc.data()?.token;

    if (!token) {
      return res.status(404).json({
        success: false,
        error: "FCM token is empty",
      });
    }

    const message = {
      token,

      notification: {
        title: "KitaLDR ❤️",
        body: "Test notification berhasil!",
      },

      data: {
        type: "TEST",
      },

      android: {
        priority: "high",

        notification: {
          channelId: "kitaldr_actions",
          sound: "default",
          priority: "high",
        },
      },
    };

    const messageId = await messaging.send(message);

    console.log(`FCM sent to ${uid}: ${messageId}`);

    return res.json({
      success: true,
      messageId,
    });
  } catch (error) {
    console.error("FCM error:", error);

    return res.status(500).json({
      success: false,
      error: error.message,
    });
  }
});

/* =========================================================
   FIRESTORE ACTION → FCM
   ========================================================= */

const seenActions = new Set();

async function sendActionNotification(action, actionId, coupleId) {
  const {
    type,
    senderUid,
    recipientUid,
  } = action;

  if (
    !type ||
    !senderUid ||
    !recipientUid ||
    !coupleId
  ) {
    console.warn(
      `Invalid action ignored: ${actionId}`
    );
    return;
  }

  // Jangan kirim ke diri sendiri
  if (senderUid === recipientUid) {
    console.log(
      `Action ${actionId} ignored: sender === recipient`
    );
    return;
  }

  /* -------------------------------------------------------
     Validate couple
     ------------------------------------------------------- */

  const coupleSnap = await db
    .collection("couples")
    .doc(coupleId)
    .get();

  if (!coupleSnap.exists) {
    console.warn(
      `Couple not found: ${coupleId}`
    );
    return;
  }

  const couple = coupleSnap.data();

  if (couple?.status !== "active") {
    console.warn(
      `Couple is not active: ${coupleId}`
    );
    return;
  }

  const isSenderMember =
    couple.memberA === senderUid ||
    couple.memberB === senderUid;

  const isRecipientMember =
    couple.memberA === recipientUid ||
    couple.memberB === recipientUid;

  if (!isSenderMember || !isRecipientMember) {
    console.warn(
      `Action ${actionId} rejected: users are not members of couple ${coupleId}`
    );
    return;
  }

  /* -------------------------------------------------------
     Get recipient FCM token + sender information
     ------------------------------------------------------- */

  const [tokenSnap, senderSnap] = await Promise.all([
    db
      .collection("deviceTokens")
      .doc(recipientUid)
      .get(),

    db
      .collection("users")
      .doc(senderUid)
      .get(),
  ]);

  const token = tokenSnap.data()?.token;

  if (!token) {
    console.warn(
      `FCM token not found for recipient ${recipientUid}`
    );
    return;
  }

  const senderName =
    senderSnap.data()?.displayName ||
    "My Love";

  /* -------------------------------------------------------
     Notification content
     ------------------------------------------------------- */

  let title = "KitaLDR ❤️";
  let body = "You have a new action.";

  if (type === "POKE") {
    title = `${senderName} poked you! ❤️`;
    body = "Your person sent you a poke.";
  }

  /* -------------------------------------------------------
     FCM message
     ------------------------------------------------------- */

  const message = {
    token,

    notification: {
      title,
      body,
    },

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
  };

  try {
    const messageId = await messaging.send(message);

    console.log(
      `FCM ACTION SENT: ${type} → ${recipientUid}: ${messageId}`
    );
  } catch (error) {
    const errorCode = error?.code || "";

    /*
     * Token sudah tidak valid.
     * Hapus supaya server tidak terus mencoba
     * mengirim ke token mati.
     */

    if (
      errorCode.includes(
        "registration-token-not-registered"
      ) ||
      errorCode.includes("invalid-argument")
    ) {
      await db
        .collection("deviceTokens")
        .doc(recipientUid)
        .delete();

      console.warn(
        `Removed invalid FCM token: ${recipientUid}`
      );
    }

    console.error(
      `FCM ACTION FAILED: ${type}`,
      error
    );
  }
}

/* =========================================================
   REALTIME FIRESTORE ACTION LISTENER
   ========================================================= */

function startActionListener() {
  console.log(
    "Starting Firestore action listener..."
  );

  return db
    .collectionGroup("actions")
    .onSnapshot(
      (snapshot) => {
        for (const change of snapshot.docChanges()) {
          if (change.type !== "added") {
            continue;
          }

          const actionId = change.doc.id;

          /*
           * Hindari memproses action yang sama
           * berkali-kali selama process hidup.
           */

          if (seenActions.has(actionId)) {
            continue;
          }

          seenActions.add(actionId);

          const pathParts =
            change.doc.ref.path.split("/");

          /*
           * Expected:
           *
           * couples/{coupleId}/actions/{actionId}
           *
           * pathParts:
           * [0] couples
           * [1] coupleId
           * [2] actions
           * [3] actionId
           */

          const coupleId = pathParts[1];
          const action = change.doc.data();

          console.log(
            `ACTION RECEIVED: ${action.type || "UNKNOWN"} ${actionId}`
          );

          console.log(
            `  senderUid: ${action.senderUid}`
          );

          console.log(
            `  recipientUid: ${action.recipientUid}`
          );

          console.log(
            `  coupleId: ${coupleId}`
          );

          sendActionNotification(
            action,
            actionId,
            coupleId
          ).catch((error) => {
            console.error(
              "Action processing failed:",
              error
            );
          });
        }

        /*
         * Hindari Set tumbuh tanpa batas.
         */

        if (seenActions.size > 5000) {
          const first =
            seenActions.values().next().value;

          seenActions.delete(first);
        }
      },

      (error) => {
        console.error(
          "Firestore listener error:",
          error
        );
      }
    );
}

/* =========================================================
   START SERVER + FIRESTORE LISTENER
   ========================================================= */

app.listen(PORT, "127.0.0.1", () => {
  console.log(
    `KitaLDR local FCM server listening on http://127.0.0.1:${PORT}`
  );

  startActionListener();
});