const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldPath } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

const db = getFirestore();

exports.sendPartnerActionNotification = onDocumentCreated(
  "couples/{coupleId}/actions/{actionId}",
  async (event) => {
    const action = event.data?.data();
    if (!action) return;

    const type = action.type;
    const senderUid = action.senderUid;
    const recipientUid = action.recipientUid;
    const coupleId = event.params.coupleId;

    if (!type || !senderUid || !recipientUid || !coupleId) return;

    const coupleSnap = await db.collection("couples").doc(coupleId).get();
    if (!coupleSnap.exists || coupleSnap.data()?.status !== "active") return;

    const couple = coupleSnap.data();
    const isMember = couple.memberA === senderUid || couple.memberB === senderUid;
    const recipientIsMember = couple.memberA === recipientUid || couple.memberB === recipientUid;
    if (!isMember || !recipientIsMember || senderUid === recipientUid) return;

    const [tokenSnap, senderSnap] = await Promise.all([
      db.collection("deviceTokens").doc(recipientUid).get(),
      db.collection("users").doc(senderUid).get(),
    ]);

    const token = tokenSnap.data()?.token;
    if (!token) return;

    const senderName = senderSnap.data()?.displayName || "My Love";

    const title = type === "POKE" ? `${senderName} poked you! ❤️` : "KitaLDR";
    const body = type === "POKE" ? "Your person sent you a poke." : "You have a new action.";

    try {
      await getMessaging().send({
        token,
        data: {
          type: String(type),
          senderUid: String(senderUid),
          senderName: String(senderName),
          recipientUid: String(recipientUid),
          coupleId: String(coupleId),
          actionId: String(event.params.actionId),
        },
        android: {
          priority: "high",
        },
      });
    } catch (error) {
      // Remove invalid tokens so a later pairing can register a fresh one.
      const code = error?.code || "";
      if (code.includes("registration-token-not-registered") || code.includes("invalid-argument")) {
        await db.collection("deviceTokens").doc(recipientUid).delete();
      }
      console.error("Failed to send partner action notification", error);
    }
  }
);
