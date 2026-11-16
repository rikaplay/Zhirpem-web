"use client";

import React, { useEffect, useMemo, useState } from "react";
import { db } from "@/lib/firebase";
import { collection, deleteDoc, doc, onSnapshot, query, where, Timestamp } from "firebase/firestore";
import { Icon } from "@/components/ui/Icon";
import { useUserAvatar } from "@/lib/avatars";
import { follow, isFollowing, readSession } from "@/lib/social";
import { useLongPress } from "@/lib/useLongPress";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";

interface Notif {
  id: string;
  senderId: string;
  senderName: string;
  senderAvatarUrl: string;
  receiverId: string;
  type: string;
  text: string;
  targetText: string;
  postId: string | null;
  timestampMs: number;
}

const parse = (d: any): Notif => {
  const x = d.data();
  const ts: Timestamp | null = x.timestamp || null;
  return {
    id: d.id,
    senderId: x.senderId || "",
    senderName: x.senderName || "Пользователь",
    senderAvatarUrl: x.senderAvatarUrl || "",
    receiverId: x.receiverId || "",
    type: x.type === "MESSAGE" ? "CHAT_MESSAGE" : x.type || "LIKE",
    text: x.text || x.htmlBody || "",
    targetText: x.targetText || x.title || "",
    postId: x.postId || null,
    // Только что созданное уведомление ещё без серверного времени: показываем его первым
    timestampMs: ts ? ts.toMillis() : Number.MAX_SAFE_INTEGER
  };
};

const HIDDEN_KEY = "hidden_notifications";
const readHidden = (): string[] => {
  try { return JSON.parse(localStorage.getItem(HIDDEN_KEY) || "[]"); } catch { return []; }
};

const ICON: Record<string, { name: any; color: string }> = {
  LIKE: { name: "heart", color: "text-pink-500" },
  COMMENT: { name: "chat", color: "text-blue-500" },
  FOLLOW: { name: "user", color: "text-primary" },
  CHAT_MESSAGE: { name: "send", color: "text-primary" },
  ADMIN: { name: "bell", color: "text-amber-500" }
};

const ACTION: Record<string, string> = {
  LIKE: "лайкнул ваше",
  COMMENT: "прокомментировал ваше",
  FOLLOW: "подписался на вас",
  CHAT_MESSAGE: "прислал сообщение",
  ADMIN: ""
};

const Item: React.FC<{ n: Notif; me: string; onDelete: () => void }> = ({ n, me, onDelete }) => {
  // Актуальная аватарка отправителя (в самом уведомлении она «застывает» на момент отправки)
  const avatar = useUserAvatar(n.senderId, n.senderAvatarUrl);
  const [followed, setFollowed] = useState(false);
  const [checked, setChecked] = useState(false);
  const lp = useLongPress(onDelete);
  const icon = ICON[n.type] || ICON.LIKE;
  const what = n.postId?.startsWith("short:") ? "видео" : "";

  useEffect(() => {
    if (n.type !== "FOLLOW" || !n.senderId) return;
    isFollowing(me, n.senderId).then((f) => { setFollowed(f); setChecked(true); }).catch(() => setChecked(true));
  }, [n.type, n.senderId, me]);

  const followBack = () => {
    const s = readSession();
    if (!s || followed) return;
    setFollowed(true);
    follow(s, n.senderId);
  };

  return (
    <div className="flex gap-4 p-4 glass rounded-[28px] shadow-sm select-none" {...lp.handlers}>
      <div className="relative flex-shrink-0">
        <div className="w-14 h-14 rounded-[20px] overflow-hidden bg-zinc-100 dark:bg-zinc-800 flex items-center justify-center text-primary font-black text-xl">
          {n.type === "ADMIN" ? <img src="/logo.png" alt="" className="w-full h-full object-cover" /> :
            avatar ? <img src={avatar} alt="" className="w-full h-full object-cover" /> : (n.senderName.charAt(0).toUpperCase() || "?")}
        </div>
        <div className={`absolute -bottom-1 -right-1 bg-white dark:bg-zinc-900 p-1.5 rounded-full shadow-lg border border-zinc-500/10 ${icon.color}`}>
          <Icon name={icon.name} size={14} solid={n.type === "LIKE"} />
        </div>
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-[15px] leading-snug">
          <span className="font-black">{n.type === "ADMIN" ? "Жирпем" : n.senderName}</span>
          <span className="text-zinc-500 font-medium"> {ACTION[n.type] ?? ""}{(n.type === "LIKE" || n.type === "COMMENT") ? ` ${what || "сообщение"}` : ""}</span>
        </p>
        {n.type === "ADMIN" ? (
          <p className="text-sm mt-1 whitespace-pre-wrap" dangerouslySetInnerHTML={{ __html: n.text }} />
        ) : n.text ? (
          <p className="text-sm text-zinc-500 mt-1 line-clamp-2 italic">«{n.text}»</p>
        ) : n.targetText ? (
          <p className="text-sm text-zinc-400 mt-1 line-clamp-1">{n.targetText}</p>
        ) : null}
        <p className="text-[10px] text-zinc-500 font-black uppercase mt-2 opacity-60">
          {n.timestampMs !== Number.MAX_SAFE_INTEGER
            ? new Date(n.timestampMs).toLocaleDateString("ru-RU", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" })
            : "только что"}
        </p>
      </div>
      {n.type === "FOLLOW" && checked && (
        <button
          onClick={followBack}
          disabled={followed}
          className={`self-center px-4 py-2 rounded-full text-[13px] font-black ${followed ? "bg-zinc-200 dark:bg-zinc-800 text-zinc-500" : "bg-primary text-white"}`}
        >
          {followed ? "Вы читаете" : "Подписаться"}
        </button>
      )}
    </div>
  );
};

export const NotificationsScreen = ({ myUsername }: { myUsername: string }) => {
  const [personal, setPersonal] = useState<Notif[]>([]);
  const [broadcast, setBroadcast] = useState<Notif[]>([]);
  const [failed, setFailed] = useState(false);
  const [hidden, setHidden] = useState<string[]>([]);
  const [deleteTarget, setDeleteTarget] = useState<Notif | null>(null);

  useEffect(() => { setHidden(readHidden()); }, []);

  // Два простых запроса вместо whereIn + orderBy: тот требовал составной индекс Firestore,
  // а без него запрос падал, и вкладка показывала пустой список. Сортировка на устройстве.
  useEffect(() => {
    if (!myUsername) return;
    const u1 = onSnapshot(
      query(collection(db, "notifications"), where("receiverId", "==", myUsername)),
      (snap) => { setFailed(false); setPersonal(snap.docs.map(parse)); },
      () => setFailed(true)
    );
    const u2 = onSnapshot(
      query(collection(db, "notifications"), where("receiverId", "==", "ALL")),
      (snap) => setBroadcast(snap.docs.map(parse)),
      () => {}
    );
    return () => { u1(); u2(); };
  }, [myUsername]);

  const list = useMemo(
    () => [...personal, ...broadcast].filter((n) => !hidden.includes(n.id)).sort((a, b) => b.timestampMs - a.timestampMs),
    [personal, broadcast, hidden]
  );

  const remove = (n: Notif) => {
    // Общие рассылки (ALL) нельзя удалять из базы: они общие для всех, поэтому только скрываем у себя
    if (n.receiverId === "ALL") {
      const next = [...hidden, n.id];
      localStorage.setItem(HIDDEN_KEY, JSON.stringify(next));
      setHidden(next);
    } else {
      deleteDoc(doc(db, "notifications", n.id)).catch(() => {});
    }
    setDeleteTarget(null);
  };

  return (
    <div className="min-h-screen px-4 pt-4 space-y-3 pb-32">
      <h2 className="text-2xl font-black uppercase tracking-tighter text-zinc-400 mb-4 ml-2">Уведомления</h2>
      {list.map((n) => <Item key={n.id} n={n} me={myUsername} onDelete={() => setDeleteTarget(n)} />)}
      {list.length === 0 && (
        <div className="text-center py-40">
          <div className="text-zinc-300 dark:text-zinc-700 flex justify-center mb-6"><Icon name="bell" size={72} /></div>
          <p className="text-zinc-400 font-black text-lg uppercase tracking-tighter italic">
            {failed ? "Не удалось загрузить уведомления" : "Пока что тихо..."}
          </p>
        </div>
      )}
      {list.length > 0 && <p className="text-center text-xs text-zinc-400 pt-2">Удерживайте уведомление, чтобы удалить его</p>}
      {deleteTarget && (
        <ConfirmDialog
          title="Удалить уведомление?"
          text={deleteTarget.receiverId === "ALL" ? "Рассылка будет скрыта только на этом устройстве." : "Уведомление будет удалено."}
          onCancel={() => setDeleteTarget(null)}
          onConfirm={() => remove(deleteTarget)}
        />
      )}
    </div>
  );
};
