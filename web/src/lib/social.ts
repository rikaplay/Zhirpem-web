import { db } from "./firebase";
import {
  addDoc, collection, doc, getDocs, limit, query, runTransaction, serverTimestamp, where,
  deleteDoc
} from "firebase/firestore";

export interface SessionUser {
  username: string;
  name: string;
  avatarUrl?: string | null;
}

export function readSession(): SessionUser | null {
  if (typeof window === "undefined") return null;
  try {
    const raw = localStorage.getItem("user_session");
    if (!raw) return null;
    const s = JSON.parse(raw);
    return { username: s.username, name: s.name || s.username, avatarUrl: s.avatarUrl || null };
  } catch {
    return null;
  }
}

/** Запись в ленту уведомлений. Поля те же, что пишет приложение. */
export async function sendNotification(p: {
  senderId: string;
  senderName: string;
  senderAvatar?: string | null;
  receiverId: string;
  type: "LIKE" | "COMMENT" | "FOLLOW" | "CHAT_MESSAGE";
  text?: string;
  postId?: string | null;
  targetText?: string;
}) {
  if (!p.receiverId || p.receiverId === p.senderId) return;
  await addDoc(collection(db, "notifications"), {
    senderId: p.senderId,
    senderName: p.senderName,
    senderAvatarUrl: p.senderAvatar || "",
    receiverId: p.receiverId,
    type: p.type,
    text: p.text || "",
    targetText: p.targetText || "",
    postId: p.postId ?? null,
    timestamp: serverTimestamp()
  });
}

/** Лайк не дублирует уведомление: проверяем, не отправляли ли мы его раньше. */
export async function notifyLiked(me: SessionUser, receiver: string, postId: string, preview: string) {
  if (!receiver || receiver === me.username) return;
  try {
    const exists = await getDocs(query(
      collection(db, "notifications"),
      where("senderId", "==", me.username),
      where("type", "==", "LIKE"),
      where("postId", "==", postId),
      limit(1)
    ));
    if (!exists.empty) return;
  } catch { /* если проверить не вышло, лучше отправить */ }
  await sendNotification({
    senderId: me.username, senderName: me.name, senderAvatar: me.avatarUrl,
    receiverId: receiver, type: "LIKE", postId, targetText: preview
  });
}

export async function notifyFollowed(me: SessionUser, receiver: string) {
  if (!receiver || receiver === me.username) return;
  try {
    const exists = await getDocs(query(
      collection(db, "notifications"),
      where("senderId", "==", me.username),
      where("type", "==", "FOLLOW"),
      where("receiverId", "==", receiver),
      limit(1)
    ));
    if (!exists.empty) return;
  } catch { /* см. выше */ }
  await sendNotification({
    senderId: me.username, senderName: me.name, senderAvatar: me.avatarUrl,
    receiverId: receiver, type: "FOLLOW"
  });
}

/**
 * Интересы пользователя для рекомендаций: тот же алгоритм, что и в приложении
 * (user_interests.scores, топ-50 тегов). Вес: 1 просмотр, 2 лайк/комментарий, 3 репост/избранное.
 */
export async function trackInteraction(username: string, tags: string[] | undefined, weight: number) {
  if (!username || !tags || tags.length === 0) return;
  const ref = doc(db, "user_interests", username);
  try {
    await runTransaction(db, async (tx) => {
      const snap = await tx.get(ref);
      const current: Record<string, number> = (snap.exists() ? snap.data().scores : null) || {};
      const next = { ...current };
      tags.forEach((t) => { next[t] = (next[t] || 0) + weight; });
      const top = Object.entries(next).sort((a, b) => b[1] - a[1]).slice(0, 50);
      tx.set(ref, { scores: Object.fromEntries(top) });
    });
  } catch (e) {
    console.warn("trackInteraction", e);
  }
}

// ---------- Подписки ----------

export async function isFollowing(me: string, target: string): Promise<boolean> {
  const s = await getDocs(query(
    collection(db, "follows"), where("follower", "==", me), where("following", "==", target), limit(1)
  ));
  return !s.empty;
}

export async function follow(me: SessionUser, target: string) {
  await addDoc(collection(db, "follows"), {
    follower: me.username, following: target, timestamp: serverTimestamp()
  });
  await notifyFollowed(me, target);
}

export async function unfollow(me: string, target: string) {
  const s = await getDocs(query(
    collection(db, "follows"), where("follower", "==", me), where("following", "==", target)
  ));
  await Promise.all(s.docs.map((d) => deleteDoc(d.ref)));
}
