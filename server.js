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

app.listen(PORT, "127.0.0.1", () => {
  console.log(`KitaLDR API running at http://127.0.0.1:${PORT}`);
});