"use client";

import React, { useEffect, useRef, useState } from "react";
import { ShortPage } from "./ShortPage";
import { CommentsSheet } from "./CommentsSheet";
import { ShareSheet } from "./ShareSheet";
import { deleteShort, isShortDeleted, ShortVideo, useShortsStore } from "@/lib/shorts";
import { SessionUser } from "@/lib/social";

interface Props {
  videos: ShortVideo[];
  startIndex?: number;
  me: SessionUser;
  screenActive: boolean;
  onOpenProfile: (username: string) => void;
  onOpenSound: (soundId: string) => void;
  onLoadMore?: () => void;
  emptyText?: string;
  loading?: boolean;
}

/** Вертикальная лента видео: прокрутка с «примагничиванием», как в TikTok. */
export const ShortsFeed: React.FC<Props> = ({
  videos: allVideos, startIndex = 0, me, screenActive, onOpenProfile, onOpenSound, onLoadMore,
  emptyText = "Пока нет видео", loading = false
}) => {
  useShortsStore();
  // Удалённые видео сразу исчезают из ленты
  const videos = allVideos.filter((v) => !isShortDeleted(v.id));

  const scroller = useRef<HTMLDivElement>(null);
  const [activeIndex, setActiveIndex] = useState(startIndex);
  const [commentsFor, setCommentsFor] = useState<ShortVideo | null>(null);
  const [shareFor, setShareFor] = useState<ShortVideo | null>(null);
  const [deleteFor, setDeleteFor] = useState<ShortVideo | null>(null);

  // Стартуем с нужного видео (например, открытого из сетки профиля)
  useEffect(() => {
    const el = scroller.current;
    if (el && startIndex > 0) el.scrollTo({ top: startIndex * el.clientHeight });
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // Какая страница сейчас на экране
  useEffect(() => {
    const root = scroller.current;
    if (!root) return;
    const obs = new IntersectionObserver(
      (entries) => {
        entries.forEach((en) => {
          if (en.isIntersecting && en.intersectionRatio >= 0.65) {
            setActiveIndex(Number((en.target as HTMLElement).dataset.index || 0));
          }
        });
      },
      { root, threshold: [0.65] }
    );
    root.querySelectorAll("[data-index]").forEach((n) => obs.observe(n));
    return () => obs.disconnect();
  }, [videos.length]);

  useEffect(() => {
    if (onLoadMore && videos.length > 0 && activeIndex >= videos.length - 3) onLoadMore();
  }, [activeIndex, videos.length]); // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <div className="relative h-full w-full bg-black">
      {videos.length === 0 ? (
        <div className="h-full flex items-center justify-center text-white/70 px-8 text-center">
          {loading ? "Загрузка…" : emptyText}
        </div>
      ) : (
        <div ref={scroller} className="h-full w-full overflow-y-scroll snap-feed">
          {videos.map((v, i) => (
            <div key={v.id} data-index={i} className="snap-page h-full w-full">
              <ShortPage
                video={v}
                active={i === activeIndex}
                screenActive={screenActive && !commentsFor && !shareFor && !deleteFor}
                me={me}
                onOpenProfile={onOpenProfile}
                onOpenSound={onOpenSound}
                onComments={setCommentsFor}
                onShare={setShareFor}
                onDelete={setDeleteFor}
              />
            </div>
          ))}
        </div>
      )}

      {commentsFor && (
        <CommentsSheet video={commentsFor} me={me} onOpenProfile={onOpenProfile} onClose={() => setCommentsFor(null)} />
      )}
      {shareFor && (
        <ShareSheet
          me={me.username}
          payload={{ forwardedPostId: `short:${shareFor.id}`, text: `🎬 ${shareFor.caption.slice(0, 80)}` }}
          onClose={() => setShareFor(null)}
        />
      )}
      {deleteFor && (
        <div className="fixed inset-0 z-[95] flex items-center justify-center p-6" onClick={() => setDeleteFor(null)}>
          <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" />
          <div className="relative bg-white dark:bg-zinc-900 rounded-3xl p-6 max-w-sm w-full" onClick={(e) => e.stopPropagation()}>
            <h3 className="font-black text-lg mb-2">Удалить видео?</h3>
            <p className="text-sm text-zinc-500 mb-5">Видео и все комментарии к нему будут удалены. Это действие нельзя отменить.</p>
            <div className="flex gap-3">
              <button onClick={() => setDeleteFor(null)} className="flex-1 py-3 rounded-2xl bg-zinc-100 dark:bg-zinc-800 font-bold">Отмена</button>
              <button
                onClick={() => { deleteShort(deleteFor); setDeleteFor(null); }}
                className="flex-1 py-3 rounded-2xl bg-red-500 text-white font-black"
              >
                Удалить
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
