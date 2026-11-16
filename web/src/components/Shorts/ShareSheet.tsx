"use client";

import React, { useEffect, useState } from "react";
import { db } from "@/lib/firebase";
import { collection, doc, getDoc, getDocs, query, setDoc, addDoc, where } from "firebase/firestore";
import { Icon } from "@/components/ui/Icon";
import { useUserAvatar } from "@/lib/avatars";

export interface SharePayload {
  /** Для видео — "short:ID", для обычных постов — просто ID поста. */
  forwardedPostId: string;
  text: string;
}

interface Props {
  me: string;
  payload: SharePayload;
  onClose: () => void;
}

const Avatar: React.FC<{ username: string }> = ({ username }) => {
  const url = useUserAvatar(username);
  return (
    <div className="w-11 h-11 rounded-full bg-primary/10 overflow-hidden flex items-center justify-center font-black text-primary flex-shrink-0">
      {url ? <img src={url} alt="" className="w-full h-full object-cover" /> : username.charAt(0).toUpperCase()}
    </div>
  );
};

/** Отправка видео или поста в личные сообщения: выбор из чатов или поиск по нику. */
export const ShareSheet: React.FC<Props> = ({ me, payload, onClose }) => {
  const [peers, setPeers] = useState<string[]>([]);
  const [search, setSearch] = useState("");
  const [found, setFound] = useState<string | null>(null);
  const [sent, setSent] = useState<Record<string, boolean>>({});

  useEffect(() => {
    (async () => {
      const snap = await getDocs(query(collection(db, "chats"), where("participants", "array-contains", me)));
      const list = snap.docs
        .map((d) => ({ d: d.data() as any }))
        .sort((a, b) => (b.d.lastMessageTimestamp || 0) - (a.d.lastMessageTimestamp || 0))
        .map((x) => (x.d.participants as string[]).find((p) => p !== me))
        .filter((x): x is string => !!x);
      setPeers(Array.from(new Set(list)));
    })().catch(() => {});
  }, [me]);

  useEffect(() => {
    const name = search.replace("@", "").trim();
    if (!name) { setFound(null); return; }
    const t = setTimeout(async () => {
      try {
        const d = await getDoc(doc(db, "users", name));
        setFound(d.exists() && name !== me ? name : null);
      } catch { setFound(null); }
    }, 300);
    return () => clearTimeout(t);
  }, [search, me]);

  const send = async (peer: string) => {
    if (sent[peer]) return;
    setSent((s) => ({ ...s, [peer]: true }));
    const chatId = [me, peer].sort().join("_");
    const now = Date.now();
    await setDoc(doc(db, "chats", chatId), {
      id: chatId,
      participants: [me, peer].sort(),
      lastMessage: "Пересланный пост",
      lastMessageTimestamp: now
    }, { merge: true });
    await addDoc(collection(db, "chats", chatId, "messages"), {
      senderId: me,
      text: payload.text,
      timestamp: now,
      mediaType: "NONE",
      mediaUrl: "",
      forwardedPostId: payload.forwardedPostId
    });
  };

  const rows = found && !peers.includes(found) ? [found, ...peers] : peers;

  return (
    <div className="fixed inset-0 z-[90] flex items-end justify-center" onClick={onClose}>
      <div className="absolute inset-0 bg-black/50 backdrop-blur-sm" />
      <div
        className="relative w-full max-w-[500px] max-h-[75vh] bg-white dark:bg-zinc-900 rounded-t-[32px] p-5 flex flex-col animate-in slide-in-from-bottom duration-300"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between mb-3">
          <h3 className="font-black text-lg">Отправить в сообщения</h3>
          <button onClick={onClose} className="p-2 rounded-full hover:bg-zinc-100 dark:hover:bg-zinc-800"><Icon name="close" size={22} /></button>
        </div>
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Найти по нику…"
          className="w-full bg-zinc-100 dark:bg-zinc-800 rounded-2xl px-4 py-3 text-sm outline-none mb-3"
        />
        <div className="overflow-y-auto flex-1 space-y-1">
          {rows.length === 0 && <p className="text-center text-zinc-400 py-8 text-sm">Чатов пока нет. Найдите пользователя по нику.</p>}
          {rows.map((peer) => (
            <div key={peer} className="flex items-center gap-3 p-2 rounded-2xl">
              <Avatar username={peer} />
              <span className="flex-1 font-bold truncate">@{peer}</span>
              <button
                onClick={() => send(peer)}
                disabled={sent[peer]}
                className={`px-4 py-2 rounded-full text-sm font-black transition-all active:scale-95 ${sent[peer] ? "bg-zinc-200 dark:bg-zinc-800 text-zinc-500" : "bg-primary text-white"}`}
              >
                {sent[peer] ? "Отправлено" : "Отправить"}
              </button>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};
