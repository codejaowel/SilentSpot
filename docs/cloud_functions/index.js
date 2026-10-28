const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

exports.onStudySpaceUpdated = functions.firestore
  .document("studySpaces/{spaceId}")
  .onWrite(async (change, context) => {
    const newData = change.after.data();

    if (!newData) {
      console.log("Document deleted, skipping notification.");
      return null;
    }

    const spaceId = context.params.spaceId;
    const spaceName = newData.name || spaceId;
    const noiseLevel = newData.noiseLevel || 0;
    const threshold = newData.threshold || 60;

    console.log(`[${spaceName}] noiseLevel=${noiseLevel} threshold=${threshold}`);

    if (noiseLevel <= threshold) {
      console.log("Noise level within threshold, no notification sent.");
      return null;
    }

    const message = {
      notification: {
        title: `${spaceName} is currently noisy`,
        body: `The ${spaceName} noise level is ${Math.round(noiseLevel)} dB, ` +
              `exceeding the ${threshold} dB threshold.`,
      },
      data: {
        spaceId: spaceId,
        spaceName: spaceName,
        noiseLevel: noiseLevel.toString(),
        threshold: threshold.toString(),
      },
      topic: "noisy_alerts",
      android: {
        priority: "high",
        notification: {
          channelId: "silentspot_noise_alerts",
          color: "#1565C0",
          clickAction: "OPEN_DASHBOARD",
        },
      },
    };

    try {
      const response = await admin.messaging().send(message);
      console.log(`Notification sent for ${spaceName}:`, response);
    } catch (error) {
      console.error(`Failed to send notification for ${spaceName}:`, error);
    }

    return null;
  });
