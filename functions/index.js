const { onSchedule } = require("firebase-functions/v2/scheduler");
const { logger } = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();
const db = admin.firestore();

const REMINDER_LEAD_MINUTES = 30;
const RUN_EVERY_MINUTES = 5;

/**
 * Runs every 5 minutes. Finds events - across every user - whose reminder time (30 minutes
 * before the event starts) has arrived but hasn't already had a reminder sent, and pushes a
 * data-only FCM message to every device token that user's app has registered (see
 * FcmTokenRepository.kt on the Android side for how tokens get here).
 *
 * This is the server-side half of "Push Notifications via Firebase Cloud Messaging for event
 * reminders": the on-device WorkManager reminder (EventReminderWorker.kt) already reminds a
 * user on the same device/app install that created the event; this is what lets a reminder
 * reach any signed-in device for that user (a different phone, a reinstall, or if the
 * WorkManager job never fired), and it's a genuine push delivered by a server, not the client
 * scheduling a notification to itself.
 */
exports.sendEventReminders = onSchedule(`every ${RUN_EVERY_MINUTES} minutes`, async () => {
  const now = Date.now();
  const reminderCutoff = now + REMINDER_LEAD_MINUTES * 60 * 1000;

  const dueEvents = await db
    .collectionGroup("events")
    .where("reminderSent", "==", false)
    .where("dateTimeMillis", ">", now)
    .where("dateTimeMillis", "<=", reminderCutoff)
    .get();

  if (dueEvents.empty) {
    logger.info("No event reminders due this run.");
    return;
  }

  for (const eventDoc of dueEvents.docs) {
    await sendReminderFor(eventDoc);
  }
});

async function sendReminderFor(eventDoc) {
  const event = eventDoc.data();
  const uid = eventDoc.ref.parent.parent ? eventDoc.ref.parent.parent.id : null;
  if (!uid) return;

  const tokensSnapshot = await db.collection("users").doc(uid).collection("fcmTokens").get();
  const tokens = tokensSnapshot.docs.map((doc) => doc.id);

  // Mark it sent either way - a user with no registered device shouldn't be retried forever.
  await eventDoc.ref.set({ reminderSent: true }, { merge: true });

  if (tokens.length === 0) {
    logger.info(`No FCM tokens for user ${uid}; skipping event ${eventDoc.id}.`);
    return;
  }

  const message = {
    tokens,
    data: {
      eventId: eventDoc.id,
      title: event.title || "Upcoming event",
      body: event.location ? `Starting soon at ${event.location}` : "Starting soon",
    },
  };

  const response = await admin.messaging().sendEachForMulticast(message);
  await cleanUpInvalidTokens(uid, tokens, response);
  logger.info(`Sent reminder for event ${eventDoc.id} to ${response.successCount}/${tokens.length} device(s).`);
}

/** Firestore doesn't clean up a token on its own when a device is uninstalled/deregistered -
 *  do it here so the fcmTokens collection doesn't grow forever with dead tokens. */
async function cleanUpInvalidTokens(uid, tokens, response) {
  const deletions = [];
  response.responses.forEach((result, index) => {
    const code = result.error && result.error.code;
    if (code === "messaging/invalid-registration-token" || code === "messaging/registration-token-not-registered") {
      deletions.push(db.collection("users").doc(uid).collection("fcmTokens").doc(tokens[index]).delete());
    }
  });
  await Promise.all(deletions);
}
