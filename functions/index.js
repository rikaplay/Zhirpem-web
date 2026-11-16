const { onDocumentCreated, onDocumentDeleted } = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");
const axios = require("axios");

admin.initializeApp();

// Используйте firebase functions:secrets:set для реальной безопасности
const ONESIGNAL_APP_ID = "e52144a6-d4ea-46a4-870f-4089ec7a6af9";
const ONESIGNAL_REST_API_KEY = "os_v2_app_4uqujjwu5jdkjbypice6y6tk7hgycpqrjz7el4eil7itrf3xlinih6ikgpfq6o5l43izejzh4wdmjtrszqsdjvzj455p7mvulqousny";

exports.onNewPostCreatedOneSignal = onDocumentCreated("zhirpem_posts/{postId}", async (event) => {
    const postData = event.data.data();
    if (!postData) return null;

    const authorName = postData.author || "Пользователь";
    const postText = postData.text || "выложил новый пост";

    console.log(`Начинаю отправку пуша OneSignal для поста: ${event.params.postId}`);

    const notificationBody = {
        app_id: ONESIGNAL_APP_ID,
        included_segments: ["Subscribed Users"], // "All" тоже работает, но "Subscribed Users" надежнее
        headings: { "ru": `Новый пост от ${authorName}` },
        contents: { "ru": postText },
        data: {
            postId: event.params.postId,
            type: "NEW_POST"
        },
        // Важно для Android 13+
        android_channel_id: "zhirpem_notifications"
    };

    try {
        const response = await axios.post("https://onesignal.com/api/v1/notifications", notificationBody, {
            headers: {
                "Content-Type": "application/json; charset=utf-8",
                "Authorization": `Basic ${ONESIGNAL_REST_API_KEY}`
            }
        });
        console.log("OneSignal API Response:", response.data);
    } catch (error) {
        if (error.response) {
            console.error("OneSignal API Error Data:", error.response.data);
            console.error("OneSignal API Error Status:", error.response.status);
        } else {
            console.error("Error message:", error.message);
        }
    }
    return null;
});


// ==========================================================================
// Фоновые push: входящие звонки и личные сообщения (FCM, без постоянных соединений в приложении)
// Приложение сохраняет токен устройства в users/{username}.fcmTokens, функции ниже по нему шлют push.
// ==========================================================================
const db = admin.firestore();
const { FieldValue } = admin.firestore;

/** Токены устройств пользователя. */
function tokensOf(userData) {
    const tokens = new Set();
    if (userData.fcmToken) tokens.add(userData.fcmToken);
    (userData.fcmTokens || []).forEach((t) => t && tokens.add(t));
    return [...tokens];
}

/** Отправляет data-push (приложение само рисует уведомление) и чистит устаревшие токены. */
async function pushToUser(username, data, ttlMs) {
    const userRef = db.collection("users").doc(username);
    const snap = await userRef.get();
    if (!snap.exists) return;
    const tokens = tokensOf(snap.data());
    if (tokens.length === 0) return;

    const response = await admin.messaging().sendEachForMulticast({
        tokens,
        data,
        android: { priority: "high", ttl: ttlMs },
    });

    const dead = [];
    response.responses.forEach((r, i) => {
        const code = r.error && r.error.code;
        if (!r.success && (code === "messaging/registration-token-not-registered" ||
            code === "messaging/invalid-registration-token")) {
            dead.push(tokens[i]);
        }
    });
    if (dead.length > 0) {
        const update = { fcmTokens: FieldValue.arrayRemove(...dead) };
        if (dead.includes(snap.data().fcmToken)) update.fcmToken = FieldValue.delete();
        await userRef.update(update);
    }
}

/** Все значения в data обязаны быть строками. */
function str(value) {
    return value === undefined || value === null ? "" : String(value);
}

// 1. Входящий звонок: приложение показывает уведомление с контактом и кнопками «Ответить» / «Сбросить»
exports.onCallCreatedPush = onDocumentCreated("calls/{callId}", async (event) => {
    const call = event.data && event.data.data();
    if (!call || !call.offer || !call.recipientId) return null;

    const fromId = call.offer.from;
    const fromSnap = await db.collection("users").doc(fromId).get();
    const from = fromSnap.exists ? fromSnap.data() : {};

    try {
        await pushToUser(call.recipientId, {
            type: "CALL",
            chatId: str(event.params.callId),
            callType: str(call.callType || "VIDEO"),
            fromId: str(fromId),
            fromName: str(from.name || fromId),
            fromAvatar: str(from.avatarUrl),
            sessionId: str(call.sessionId),
        }, 45 * 1000); // звонок живёт 45 секунд, потом push не доставляется
    } catch (e) {
        console.error("CALL push error:", e);
    }
    return null;
});

// 2. Звонок завершён или сброшен: убираем уведомление у получателя
exports.onCallDeletedPush = onDocumentDeleted("calls/{callId}", async (event) => {
    const call = event.data && event.data.data();
    if (!call || !call.recipientId) return null;
    try {
        await pushToUser(call.recipientId, {
            type: "CALL_CANCEL",
            chatId: str(event.params.callId),
        }, 60 * 1000);
    } catch (e) {
        console.error("CALL_CANCEL push error:", e);
    }
    return null;
});

/** Подпись для сообщения без текста. */
function previewOf(message) {
    if (message.text) return message.text;
    if (message.replyToId === "voice") return "🎤 Голосовое сообщение";
    if (message.mediaUrl) {
        if (message.mediaType === "VIDEO") return "🎥 Видео";
        if (message.mediaType === "GIF") return "GIF";
        return "📷 Фото";
    }
    return "Новое сообщение";
}

/** Взаимная подписка (нужна, если получатель принимает уведомления только от «своих»). */
async function areMutualFollowers(a, b) {
    const [one, two] = await Promise.all([
        db.collection("follows").where("follower", "==", a).where("following", "==", b).limit(1).get(),
        db.collection("follows").where("follower", "==", b).where("following", "==", a).limit(1).get(),
    ]);
    return !one.empty && !two.empty;
}

// 3. Новое личное сообщение
exports.onChatMessageCreatedPush = onDocumentCreated("chats/{chatId}/messages/{messageId}", async (event) => {
    const message = event.data && event.data.data();
    if (!message || !message.senderId) return null;

    const chatSnap = await db.collection("chats").doc(event.params.chatId).get();
    if (!chatSnap.exists) return null;
    const participants = chatSnap.data().participants || [];
    const recipients = participants.filter((p) => p !== message.senderId);
    if (recipients.length === 0) return null;

    const senderSnap = await db.collection("users").doc(message.senderId).get();
    const sender = senderSnap.exists ? senderSnap.data() : {};
    const senderVerified = sender.blueBadge === true || sender.yellowBadge === true;

    await Promise.all(recipients.map(async (recipientId) => {
        try {
            const recipientSnap = await db.collection("users").doc(recipientId).get();
            if (!recipientSnap.exists) return;
            const recipient = recipientSnap.data();

            // Уважаем настройки получателя
            if (recipient.notificationSetting === "none") return;
            if (recipient.isOnlyVerifiedMessages === true && !senderVerified) return;
            if (recipient.notificationSetting === "following" &&
                !(await areMutualFollowers(recipientId, message.senderId))) return;

            await pushToUser(recipientId, {
                type: "MESSAGE",
                chatId: str(event.params.chatId),
                fromId: str(message.senderId),
                fromName: str(sender.name || message.senderId),
                fromAvatar: str(sender.avatarUrl),
                text: str(previewOf(message)).slice(0, 300),
            }, 24 * 60 * 60 * 1000);
        } catch (e) {
            console.error("MESSAGE push error:", e);
        }
    }));
    return null;
});
