"use client";

import { useEffect, useReducer } from "react";
import { db } from "./firebase";
import {
  addDoc, arrayRemove, arrayUnion, collection, deleteDoc, doc, DocumentSnapshot, getDoc, getDocs,
  increment, limit, orderBy, query, serverTimestamp, setDoc, startAfter, updateDoc, where, writeBatch
} from "firebase/firestore";
import { uploadToCloudinary } from "./cloudinary";
import { SessionUser, sendNotification, trackInteraction } from "./social";

// ======================================================================
//  МОДЕЛИ
// ======================================================================

export const SHORT_MAX_MS = 180_000;
export const SHORT_MAX_UPLOAD_BYTES = 100 * 1024 * 1024;

export interface ShortVideo {
  id: string;
  authorUsername: string;
  authorName: string;
  authorAvatarUrl: string | null;
  videoUrl: string;
  caption: string;
  soundId: string | null;
  soundTitle: string;
  soundAuthor: string;
  /** true — звук из библиотеки: собственная дорожка видео не играет, вместо неё играет звук. */
  muteOriginal: boolean;
  tags: string[];
  likes: number;
  likedBy: string[];
  commentsCount: number;
  repostsCount: number;
  repostedBy: string[];
  views: number;
  durationMs: number;
  timestampMs: number;
}

export interface ShortSound {
  id: string;
  title: string;
  authorUsername: string;
  audioUrl: string;
  durationMs: number;
  uses: number;
  favoritedBy: string[];
  isOriginal: boolean;
  timestampMs: number;
}

export interface ShortComment {
  id: string;
  shortId: string;
  authorUsername: string;
  author: string;
  text: string;
  timestampMs: number;
}

const ms = (t: any): number => (t && typeof t.toMillis === "function" ? t.toMillis() : 0);

export function parseShort(d: DocumentSnapshot): ShortVideo {
  const x: any = d.data() || {};
  return {
    id: d.id,
    authorUsername: x.authorUsername || "",
    authorName: x.authorName || x.authorUsername || "",
    authorAvatarUrl: x.authorAvatarUrl || null,
    videoUrl: x.videoUrl || "",
    caption: x.caption || "",
    soundId: x.soundId || null,
    soundTitle: x.soundTitle || "",
    soundAuthor: x.soundAuthor || "",
    muteOriginal: !!x.muteOriginal,
    tags: x.tags || [],
    likes: x.likes || 0,
    likedBy: x.likedBy || [],
    commentsCount: x.commentsCount || 0,
    repostsCount: x.repostsCount || 0,
    repostedBy: x.repostedBy || [],
    views: x.views || 0,
    durationMs: x.durationMs || 0,
    timestampMs: ms(x.timestamp)
  };
}

export function parseSound(d: DocumentSnapshot): ShortSound {
  const x: any = d.data() || {};
  return {
    id: d.id,
    title: x.title || "Без названия",
    authorUsername: x.authorUsername || "",
    audioUrl: x.audioUrl || "",
    durationMs: x.durationMs || 0,
    uses: x.uses || 0,
    favoritedBy: x.favoritedBy || [],
    isOriginal: !!x.isOriginal,
    timestampMs: ms(x.timestamp)
  };
}

export function parseComment(d: DocumentSnapshot): ShortComment {
  const x: any = d.data() || {};
  return {
    id: d.id,
    shortId: x.shortId || "",
    authorUsername: x.authorUsername || "",
    author: x.author || "",
    text: x.text || "",
    timestampMs: ms(x.timestamp)
  };
}

// ======================================================================
//  ССЫЛКИ НА МЕДИА (Cloudinary умеет делать превью и аудио из видео)
// ======================================================================

export function thumbUrl(videoUrl: string, width = 360): string {
  if (!videoUrl.includes("/upload/")) return videoUrl;
  const t = videoUrl.replace("/upload/", `/upload/so_0,w_${width},c_limit,q_auto/`);
  return t.substring(0, t.lastIndexOf(".")) + ".jpg";
}

export function playUrl(videoUrl: string): string {
  return videoUrl.includes("/upload/") ? videoUrl.replace("/upload/", "/upload/q_auto:good/") : videoUrl;
}

/** Звук, извлечённый из видео: Cloudinary отдаёт аудио, если сменить расширение на .mp3. */
export function soundUrlFromVideo(videoUrl: string): string | null {
  return videoUrl.includes("/upload/") ? videoUrl.substring(0, videoUrl.lastIndexOf(".")) + ".mp3" : null;
}

export function compactCount(n: number): string {
  if (n >= 10_000_000) return `${Math.floor(n / 1_000_000)}M`;
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`.replace(".0M", "M").replace(".", ",");
  if (n >= 10_000) return `${Math.floor(n / 1000)}K`;
  if (n >= 1000) return `${(n / 1000).toFixed(1)}K`.replace(".0K", "K").replace(".", ",");
  return String(n);
}

export function formatDuration(msv: number): string {
  const total = Math.max(0, Math.floor(msv / 1000));
  return `${Math.floor(total / 60)}:${String(total % 60).padStart(2, "0")}`;
}

export function extractTags(caption: string, soundId?: string | null): string[] {
  const tags = Array.from(new Set(
    Array.from(caption.matchAll(/#([\p{L}\p{N}_]+)/gu)).map((m) => m[1].toLowerCase())
  ));
  if (soundId) tags.push(`sound_${soundId}`);
  return tags.slice(0, 10);
}

// ======================================================================
//  ЛОКАЛЬНЫЕ ПРАВКИ: лайки и удаления видны сразу во всех списках
// ======================================================================

const patches = new Map<string, ShortVideo>();
const deleted = new Set<string>();
const storeListeners = new Set<() => void>();
const bump = () => storeListeners.forEach((l) => l());

export const resolveShort = (v: ShortVideo): ShortVideo => patches.get(v.id) || v;
export const isShortDeleted = (id: string) => deleted.has(id);

/** Подписывает компонент на локальные правки (лайки, удаления). */
export function useShortsStore() {
  const [, force] = useReducer((x: number) => x + 1, 0);
  useEffect(() => {
    storeListeners.add(force);
    return () => { storeListeners.delete(force); };
  }, []);
}

// ======================================================================
//  ЛЕНТЫ
// ======================================================================

export class ForYouLoader {
  private seen = new Set<string>();
  private lastRecent: DocumentSnapshot | null = null;
  ended = false;

  reset() {
    this.seen.clear();
    this.lastRecent = null;
    this.ended = false;
  }

  /**
   * Тот же метод, что и в основной ленте: берём топ интересов (user_interests.scores),
   * ищем видео с этими тегами, ранжируем по сумме баллов, а потом добавляем свежие.
   */
  async next(me: string): Promise<ShortVideo[]> {
    if (this.ended) return [];
    let ranked: ShortVideo[] = [];
    let scores: Record<string, number> = {};
    try {
      if (me) {
        const interests = await getDoc(doc(db, "user_interests", me));
        scores = (interests.exists() ? interests.data().scores : null) || {};
        const top = Object.entries(scores).sort((a, b) => b[1] - a[1]).slice(0, 10).map(([t]) => t);
        if (top.length > 0 && this.seen.size < 30) {
          const snap = await getDocs(query(collection(db, "shorts"), where("tags", "array-contains-any", top), limit(20)));
          ranked = snap.docs.map(parseShort)
            .filter((v) => !this.seen.has(v.id))
            .sort((a, b) => {
              const sa = a.tags.reduce((s, t) => s + (scores[t] || 0), 0) + Math.random() * 3;
              const sb = b.tags.reduce((s, t) => s + (scores[t] || 0), 0) + Math.random() * 3;
              return sb - sa;
            });
        }
      }
    } catch (e) {
      console.warn("for you interests", e);
    }

    let recent: ShortVideo[] = [];
    try {
      let q = query(collection(db, "shorts"), orderBy("timestamp", "desc"), limit(15));
      if (this.lastRecent) q = query(q, startAfter(this.lastRecent));
      const snap = await getDocs(q);
      if (snap.docs.length > 0) this.lastRecent = snap.docs[snap.docs.length - 1];
      recent = snap.docs.map(parseShort);
    } catch (e) {
      console.warn("for you recent", e);
    }

    const merged = [...ranked, ...recent]
      .filter((v, i, a) => a.findIndex((t) => t.id === v.id) === i)
      .filter((v) => !this.seen.has(v.id));
    merged.forEach((v) => this.seen.add(v.id));
    if (merged.length === 0) this.ended = true;
    return merged;
  }
}

export async function loadFollowingShorts(me: string): Promise<ShortVideo[]> {
  if (!me) return [];
  const follows = await getDocs(query(collection(db, "follows"), where("follower", "==", me)));
  const authors = Array.from(new Set(follows.docs.map((d) => d.data().following as string).filter(Boolean)));
  if (authors.length === 0) return [];
  const all: ShortVideo[] = [];
  for (let i = 0; i < authors.length; i += 30) {
    const snap = await getDocs(query(collection(db, "shorts"), where("authorUsername", "in", authors.slice(i, i + 30)), limit(40)));
    all.push(...snap.docs.map(parseShort));
  }
  return all
    .filter((v, i, a) => a.findIndex((t) => t.id === v.id) === i)
    .sort((a, b) => b.timestampMs - a.timestampMs);
}

export async function loadUserShorts(username: string): Promise<ShortVideo[]> {
  const snap = await getDocs(query(collection(db, "shorts"), where("authorUsername", "==", username)));
  return snap.docs.map(parseShort).sort((a, b) => b.timestampMs - a.timestampMs);
}

export async function loadLikedShorts(me: string): Promise<ShortVideo[]> {
  const snap = await getDocs(query(collection(db, "shorts"), where("likedBy", "array-contains", me)));
  return snap.docs.map(parseShort).sort((a, b) => b.timestampMs - a.timestampMs);
}

export async function loadShort(id: string): Promise<ShortVideo | null> {
  const d = await getDoc(doc(db, "shorts", id));
  return d.exists() ? parseShort(d) : null;
}

// ======================================================================
//  ДЕЙСТВИЯ
// ======================================================================

export async function toggleShortLike(video: ShortVideo, me: SessionUser) {
  const cur = resolveShort(video);
  const liked = cur.likedBy.includes(me.username);
  patches.set(cur.id, {
    ...cur,
    likes: Math.max(0, cur.likes + (liked ? -1 : 1)),
    likedBy: liked ? cur.likedBy.filter((u) => u !== me.username) : [...cur.likedBy, me.username]
  });
  bump();
  await updateDoc(doc(db, "shorts", cur.id), {
    likedBy: liked ? arrayRemove(me.username) : arrayUnion(me.username),
    likes: increment(liked ? -1 : 1)
  }).catch((e) => console.warn("like", e));
  if (!liked) {
    trackInteraction(me.username, cur.tags, 2);
    notifyAuthor(cur, me, "LIKE", "");
  }
}

export async function toggleShortRepost(video: ShortVideo, me: SessionUser) {
  const cur = resolveShort(video);
  const reposted = cur.repostedBy.includes(me.username);
  patches.set(cur.id, {
    ...cur,
    repostsCount: Math.max(0, cur.repostsCount + (reposted ? -1 : 1)),
    repostedBy: reposted ? cur.repostedBy.filter((u) => u !== me.username) : [...cur.repostedBy, me.username]
  });
  bump();
  await updateDoc(doc(db, "shorts", cur.id), {
    repostedBy: reposted ? arrayRemove(me.username) : arrayUnion(me.username),
    repostsCount: increment(reposted ? -1 : 1)
  }).catch((e) => console.warn("repost", e));
  if (!reposted) trackInteraction(me.username, cur.tags, 3);
}

const viewed = new Set<string>();
/** Просмотр засчитывается один раз за сессию. */
export function markShortViewed(video: ShortVideo, me: string) {
  if (viewed.has(video.id)) return;
  viewed.add(video.id);
  const cur = resolveShort(video);
  patches.set(cur.id, { ...cur, views: cur.views + 1 });
  bump();
  updateDoc(doc(db, "shorts", cur.id), { views: increment(1) }).catch(() => {});
  if (me) trackInteraction(me, cur.tags, 1);
}

export async function addShortComment(video: ShortVideo, me: SessionUser, text: string) {
  const clean = text.trim();
  if (!clean) return;
  const cur = resolveShort(video);
  await addDoc(collection(db, "shorts_comments"), {
    shortId: cur.id,
    authorUsername: `@${me.username}`,
    author: me.name,
    text: clean,
    timestamp: serverTimestamp()
  });
  patches.set(cur.id, { ...cur, commentsCount: cur.commentsCount + 1 });
  bump();
  await updateDoc(doc(db, "shorts", cur.id), { commentsCount: increment(1) }).catch(() => {});
  trackInteraction(me.username, cur.tags, 2);
  notifyAuthor(cur, me, "COMMENT", clean);
}

function notifyAuthor(video: ShortVideo, me: SessionUser, type: "LIKE" | "COMMENT", text: string) {
  if (video.authorUsername === me.username) return;
  sendNotification({
    senderId: me.username, senderName: me.name, senderAvatar: me.avatarUrl,
    receiverId: video.authorUsername, type, text,
    postId: `short:${video.id}`, targetText: `🎬 ${video.caption.slice(0, 70)}`
  }).catch(() => {});
}

/** Удаляет видео вместе с комментариями. Файл в Cloudinary остаётся: из браузера его не стереть. */
export async function deleteShort(video: ShortVideo) {
  deleted.add(video.id);
  bump();
  await deleteDoc(doc(db, "shorts", video.id));
  try {
    const comments = await getDocs(query(collection(db, "shorts_comments"), where("shortId", "==", video.id)));
    for (let i = 0; i < comments.docs.length; i += 400) {
      const batch = writeBatch(db);
      comments.docs.slice(i, i + 400).forEach((d) => batch.delete(d.ref));
      await batch.commit();
    }
    if (video.soundId) await updateDoc(doc(db, "sounds", video.soundId), { uses: increment(-1) });
  } catch (e) {
    console.warn("deleteShort cleanup", e);
  }
}

// ---------------- Звуки ----------------

export async function toggleFavoriteSound(sound: ShortSound, me: string, nowFavorite: boolean) {
  await updateDoc(doc(db, "sounds", sound.id), {
    favoritedBy: nowFavorite ? arrayUnion(me) : arrayRemove(me)
  });
  if (nowFavorite) trackInteraction(me, [`sound_${sound.id}`], 3);
}

export async function deleteSound(sound: ShortSound) {
  await deleteDoc(doc(db, "sounds", sound.id));
}

export async function loadSound(id: string): Promise<ShortSound | null> {
  const d = await getDoc(doc(db, "sounds", id));
  return d.exists() ? parseSound(d) : null;
}

// ======================================================================
//  ПУБЛИКАЦИЯ
// ======================================================================

export function readMediaDuration(file: Blob, kind: "video" | "audio"): Promise<number> {
  return new Promise((resolve) => {
    const el = document.createElement(kind);
    const url = URL.createObjectURL(file);
    el.preload = "metadata";
    el.onloadedmetadata = () => {
      const d = isFinite(el.duration) ? el.duration * 1000 : 0;
      URL.revokeObjectURL(url);
      resolve(d);
    };
    el.onerror = () => { URL.revokeObjectURL(url); resolve(0); };
    el.src = url;
  });
}

/**
 * Загружает видео и создаёт документ в shorts. Без выбранного звука автоматически создаётся
 * «оригинальный звук» (аудиодорожка этого видео), под который потом смогут снимать другие.
 */
export async function publishShort(opts: {
  file: Blob;
  fileName: string;
  caption: string;
  sound: ShortSound | null;
  durationMs: number;
  me: SessionUser;
  onProgress?: (f: number) => void;
}): Promise<ShortVideo> {
  const { file, caption, sound, durationMs, me } = opts;
  if (file.size > SHORT_MAX_UPLOAD_BYTES) {
    throw new Error("Видео больше 100 МБ. Сделайте его короче или выберите файл поменьше");
  }
  const videoUrl = await uploadToCloudinary(file, "video", opts.onProgress, opts.fileName);

  let finalSound: ShortSound | null = sound;
  let muteOriginal = !!sound;
  if (!sound) {
    const audio = soundUrlFromVideo(videoUrl);
    if (audio) {
      const ref = doc(collection(db, "sounds"));
      const original: ShortSound = {
        id: ref.id, title: `Оригинальный звук — @${me.username}`, authorUsername: me.username,
        audioUrl: audio, durationMs, uses: 0, favoritedBy: [], isOriginal: true, timestampMs: Date.now()
      };
      try {
        await setDoc(ref, {
          title: original.title, authorUsername: me.username, audioUrl: audio, durationMs,
          uses: 0, favoritedBy: [], isOriginal: true, timestamp: serverTimestamp()
        });
        finalSound = original;
      } catch { finalSound = null; }
    }
    muteOriginal = false; // своя дорожка остаётся в видео: она и есть «оригинальный звук»
  }

  const ref = doc(collection(db, "shorts"));
  const data = {
    authorUsername: me.username,
    authorName: me.name,
    authorAvatarUrl: me.avatarUrl || null,
    videoUrl,
    caption: caption.trim(),
    soundId: finalSound?.id || null,
    soundTitle: finalSound?.title || "",
    soundAuthor: finalSound?.authorUsername || "",
    muteOriginal,
    tags: extractTags(caption, finalSound?.id),
    likes: 0, likedBy: [], commentsCount: 0, repostsCount: 0, repostedBy: [], views: 0,
    durationMs,
    timestamp: serverTimestamp()
  };
  await setDoc(ref, data);
  if (sound) await updateDoc(doc(db, "sounds", sound.id), { uses: increment(1) }).catch(() => {});
  return { ...data, id: ref.id, timestampMs: Date.now() } as ShortVideo;
}

/** Загрузка собственного звука в общую библиотеку. */
export async function uploadSound(file: File, title: string, me: SessionUser, onProgress?: (f: number) => void): Promise<ShortSound> {
  const duration = await readMediaDuration(file, "audio");
  if (duration > SHORT_MAX_MS) throw new Error("Звук должен быть не длиннее 3 минут");
  const url = await uploadToCloudinary(file, "audio", onProgress, file.name);
  const ref = doc(collection(db, "sounds"));
  const clean = title.trim() || `Звук @${me.username}`;
  await setDoc(ref, {
    title: clean, authorUsername: me.username, audioUrl: url, durationMs: duration,
    uses: 0, favoritedBy: [], isOriginal: false, timestamp: serverTimestamp()
  });
  return { id: ref.id, title: clean, authorUsername: me.username, audioUrl: url, durationMs: duration, uses: 0, favoritedBy: [], isOriginal: false, timestampMs: Date.now() };
}
