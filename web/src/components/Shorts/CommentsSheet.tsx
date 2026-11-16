"use client";

import React, { useEffect, useState } from "react";
import { db } from "@/lib/firebase";
import { collection, onSnapshot, query, where } from "firebase/firestore";
import { Icon } from "@/components/ui/Icon";
import { useUserAvatar } from "@/lib/avatars";
import { addShortComment, ShortComment, ShortVideo, parseComment, compactCount } from "@/lib/shorts";
import { SessionUser } from "@/lib/social";

const CommentRow: React.FC<{ c: ShortComment; onOpenProfile: (u: string) => void }> = ({ c, onOpenProfile }) => {
  const username = c.authorUsername.replace("@", "");
  const avatar = useUserAvatar(username);
  return (
    <div className="flex gap-3">
      <button
        onClick={() => onOpenProfile(username)}
        className="w-9 h-9 rounded-full bg-primary/10 overflow-hidden flex items-center justify-center text-primary font-bold flex-shrink-0"
      >
        {avatar ? <img src={avatar} alt="" className="w-full h-full object-cover" /> : (c.author || username).charAt(0).toUpperCase()}
      </button>
      <div className="min-w-0">
        <p className="text-xs font-bold text-zinc-500">{c.author || c.authorUsername}</p>
        <p className="text-[15px] break-words">{c.text}</p>
      </div>
    </div>
  );
};

interface Props {
  video: ShortVideo;
  me: SessionUser;
  onOpenProfile: (username: string) => void;
  onClose: () => void;
}

export const CommentsSheet: React.FC<Props> = ({ video, me, onOpenProfile, onClose }) => {
  const [comments, setComments] = useState<ShortComment[]>([]);
  const [text, setText] = useState("");

  useEffect(() => {
    // Без orderBy, чтобы не требовался составной индекс: сортируем на устройстве
    const q = query(collection(db, "shorts_comments"), where("shortId", "==", video.id));
    return onSnapshot(q, (snap) => {
      setComments(snap.docs.map(parseComment).sort((a, b) => (b.timestampMs || Date.now()) - (a.timestampMs || Date.now())));
    });
  }, [video.id]);

  const send = async (e: React.FormEvent) => {
    e.preventDefault();
    const t = text;
    if (!t.trim()) return;
    setText("");
    await addShortComment(video, me, t);
  };

  return (
    <div className="fixed inset-0 z-[90] flex items-end justify-center" onClick={onClose}>
      <div className="absolute inset-0 bg-black/50 backdrop-blur-sm" />
      <div
        className="relative w-full max-w-[500px] h-[78vh] bg-white dark:bg-zinc-900 rounded-t-[32px] flex flex-col animate-in slide-in-from-bottom duration-300"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between px-5 pt-5 pb-3">
          <h3 className="font-black text-lg">Комментарии · {compactCount(video.commentsCount)}</h3>
          <button onClick={onClose} className="p-2 rounded-full hover:bg-zinc-100 dark:hover:bg-zinc-800"><Icon name="close" size={22} /></button>
        </div>
        <div className="flex-1 overflow-y-auto px-5 space-y-4 pb-4">
          {comments.length === 0 && <p className="text-zinc-400 pt-6">Комментариев пока нет. Напишите первым!</p>}
          {comments.map((c) => <CommentRow key={c.id} c={c} onOpenProfile={(u) => { onClose(); onOpenProfile(u); }} />)}
        </div>
        <form onSubmit={send} className="flex items-center gap-2 p-3 border-t border-zinc-500/10">
          <input
            value={text}
            onChange={(e) => setText(e.target.value)}
            placeholder="Добавить комментарий…"
            className="flex-1 bg-zinc-100 dark:bg-zinc-800 rounded-full px-4 py-3 text-sm outline-none"
          />
          <button
            type="submit"
            disabled={!text.trim()}
            className="w-11 h-11 rounded-full bg-primary text-white flex items-center justify-center disabled:opacity-40 active:scale-90 transition-all"
          >
            <Icon name="send" size={20} bold />
          </button>
        </form>
      </div>
    </div>
  );
};
