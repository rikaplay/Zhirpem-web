"use client";

import { useEffect, useReducer } from "react";
import { db } from "./firebase";
import { collection, doc, getDoc, getDocs, query, where, writeBatch } from "firebase/firestore";

/**
 * Общий кэш аватарок. Показывает актуальную аватарку там, где её нельзя хранить в документе
 * (комментарии, уведомления), и сразу обновляется, когда пользователь меняет аватарку здесь.
 */
const TTL = 10 * 60 * 1000;
const cache = new Map<string, { url: string; at: number }>();
const loading = new Set<string>();
const listeners = new Set<() => void>();

const clean = (u: string) => (u || "").replace("@", "").trim();
const notify = () => listeners.forEach((l) => l());

export function putAvatar(username: string, url: string | null | undefined) {
  const u = clean(username);
  if (!u) return;
  cache.set(u, { url: url || "", at: Date.now() });
  notify();
}

function requestAvatar(username: string) {
  const u = clean(username);
  if (!u) return;
  const c = cache.get(u);
  if (c && Date.now() - c.at < TTL) return;
  if (loading.has(u)) return;
  loading.add(u);
  getDoc(doc(db, "users", u))
    .then((d) => putAvatar(u, d.exists() ? (d.data().avatarUrl as string) : ""))
    .catch(() => {})
    .finally(() => loading.delete(u));
}

/** Актуальная аватарка. Пока грузится — fallback; null, если аватарки нет. */
export function useUserAvatar(username: string, fallback?: string | null): string | null {
  const [, force] = useReducer((x: number) => x + 1, 0);
  useEffect(() => {
    listeners.add(force);
    requestAvatar(username);
    return () => { listeners.delete(force); };
  }, [username]);
  const c = cache.get(clean(username));
  if (!c) return fallback || null;
  return c.url || null;
}

/**
 * После смены аватарки: обновляет кэш и сохранённую сессию, а во всех старых постах
 * и видео автора переписывает authorAvatarUrl (в документах она хранится копией).
 */
export async function syncAvatarEverywhere(username: string, newUrl: string) {
  const u = clean(username);
  putAvatar(u, newUrl);
  try {
    const raw = localStorage.getItem("user_session");
    if (raw) {
      const s = JSON.parse(raw);
      if (s.username === u) localStorage.setItem("user_session", JSON.stringify({ ...s, avatarUrl: newUrl }));
    }
  } catch { /* ignore */ }

  const jobs: Array<Promise<void>> = [];
  const patchAll = async (col: string, field: string, value: string) => {
    const snap = await getDocs(query(collection(db, col), where(field, "==", value)));
    for (let i = 0; i < snap.docs.length; i += 400) {
      const batch = writeBatch(db);
      snap.docs.slice(i, i + 400).forEach((d) => batch.update(d.ref, { authorAvatarUrl: newUrl }));
      await batch.commit();
    }
  };
  jobs.push(patchAll("zhirpem_posts", "handle", `@${u}`).catch((e) => console.warn("avatar sync posts", e)));
  jobs.push(patchAll("shorts", "authorUsername", u).catch((e) => console.warn("avatar sync shorts", e)));
  await Promise.all(jobs);
}
