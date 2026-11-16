"use client";

import React, { useEffect, useState } from "react";
import { db } from "@/lib/firebase";
import { collection, doc, getCountFromServer, getDocs, onSnapshot, query, where } from "firebase/firestore";
import { Icon } from "@/components/ui/Icon";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { ShortThumb } from "./ShortThumb";
import { SoundDisc } from "./SoundLibrary";
import { useUserAvatar } from "@/lib/avatars";
import {
  compactCount, deleteShort, isShortDeleted, loadLikedShorts, loadUserShorts, parseSound, resolveShort,
  ShortSound, ShortVideo, useShortsStore
} from "@/lib/shorts";
import { follow, isFollowing, SessionUser, unfollow } from "@/lib/social";

/**
 * Профиль в Shorts. Отличается от профиля в обычной ленте: баннер переходит в аватарку,
 * под ней крупная статистика (подписки, подписчики, лайки), а ниже видео человека.
 * У своего профиля есть ещё вкладки «Понравившиеся» и «Избранные звуки».
 */
export const ShortsProfile: React.FC<{
  username: string;
  me: SessionUser;
  onBack: () => void;
  onOpenViewer: (videos: ShortVideo[], index: number) => void;
  onOpenSound: (id: string) => void;
  onOpenLibrary: () => void;
  onCreate: () => void;
}> = ({ username, me, onBack, onOpenViewer, onOpenSound, onOpenLibrary, onCreate }) => {
  useShortsStore();
  const isOwn = username === me.username;
  const avatar = useUserAvatar(username);

  const [user, setUser] = useState<any>({});
  const [followers, setFollowers] = useState(0);
  const [followingCount, setFollowingCount] = useState(0);
  const [following, setFollowing] = useState(false);
  const [videos, setVideos] = useState<ShortVideo[] | null>(null);
  const [liked, setLiked] = useState<ShortVideo[] | null>(null);
  const [favSounds, setFavSounds] = useState<ShortSound[] | null>(null);
  const [tab, setTab] = useState(0);
  const [deleteTarget, setDeleteTarget] = useState<ShortVideo | null>(null);

  useEffect(() => {
    const unsub = onSnapshot(doc(db, "users", username), (d) => setUser(d.exists() ? d.data() : {}));
    getCountFromServer(query(collection(db, "follows"), where("following", "==", username))).then((s) => setFollowers(s.data().count)).catch(() => {});
    getCountFromServer(query(collection(db, "follows"), where("follower", "==", username))).then((s) => setFollowingCount(s.data().count)).catch(() => {});
    if (!isOwn) isFollowing(me.username, username).then(setFollowing).catch(() => {});
    loadUserShorts(username).then(setVideos).catch(() => setVideos([]));
    return unsub;
  }, [username, me.username, isOwn]);

  useEffect(() => {
    if (!isOwn) return;
    if (tab === 1 && liked === null) loadLikedShorts(me.username).then(setLiked).catch(() => setLiked([]));
    if (tab === 2 && favSounds === null) {
      getDocs(query(collection(db, "sounds"), where("favoritedBy", "array-contains", me.username)))
        .then((s) => setFavSounds(s.docs.map(parseSound).sort((a, b) => a.title.toLowerCase().localeCompare(b.title.toLowerCase()))))
        .catch(() => setFavSounds([]));
    }
  }, [tab, isOwn, me.username]); // eslint-disable-line react-hooks/exhaustive-deps

  const visibleVideos = (videos || []).filter((v) => !isShortDeleted(v.id));
  const visibleLiked = (liked || []).filter((v) => !isShortDeleted(v.id));
  const totalLikes = visibleVideos.reduce((s, v) => s + resolveShort(v).likes, 0);

  const bannerColor: string = user.bannerColor || "#3a3a3a";
  const name: string = user.name || username;

  const toggleFollow = () => {
    if (isOwn) return;
    if (following) { setFollowing(false); setFollowers((n) => Math.max(0, n - 1)); unfollow(me.username, username); }
    else { setFollowing(true); setFollowers((n) => n + 1); follow(me, username); }
  };

  const grid = (list: ShortVideo[], empty: string, canDelete: (v: ShortVideo) => boolean) =>
    list.length === 0 ? (
      <p className="text-center text-zinc-400 px-10 py-12">{empty}</p>
    ) : (
      <div className="grid grid-cols-3 gap-[3px] px-1">
        {list.map((v, i) => (
          <ShortThumb
            key={v.id}
            video={v}
            onClick={() => onOpenViewer(list, i)}
            onLongPress={canDelete(v) ? () => setDeleteTarget(v) : undefined}
          />
        ))}
      </div>
    );

  return (
    <div className="absolute inset-0 z-30 bg-background-light dark:bg-background-dark overflow-y-auto pb-32">
      {/* Баннер плавно переходит в аватарку */}
      <div className="relative h-[204px]">
        {user.bannerUrl ? (
          <img src={user.bannerUrl} alt="" className="absolute inset-0 w-full h-full object-cover" />
        ) : (
          <div className="absolute inset-0" style={{ background: `linear-gradient(135deg, ${bannerColor}, ${bannerColor}88)` }} />
        )}
        <div className="absolute inset-0 bg-gradient-to-b from-transparent via-transparent to-background-light dark:to-background-dark" />
        <button onClick={onBack} className="absolute top-4 left-3 w-10 h-10 rounded-full bg-black/35 text-white flex items-center justify-center backdrop-blur">
          <Icon name="back" size={22} />
        </button>
        <div className="absolute left-1/2 -translate-x-1/2 -bottom-[54px] w-[108px] h-[108px] rounded-full overflow-hidden border-[3px] border-background-light dark:border-background-dark bg-zinc-200 dark:bg-zinc-800 flex items-center justify-center">
          {avatar ? <img src={avatar} alt="" className="w-full h-full object-cover" /> : <span className="text-4xl font-black">{name.charAt(0).toUpperCase()}</span>}
        </div>
      </div>

      <div className="pt-16 px-4 flex flex-col items-center text-center">
        <h2 className="text-[22px] font-black truncate max-w-full">{name}</h2>
        <p className="text-sm text-zinc-500">@{username}</p>
        {user.status && <p className="text-sm mt-1.5 line-clamp-3">{user.status}</p>}

        <div className="flex justify-evenly w-full mt-4">
          {[["Подписки", followingCount], ["Подписчики", followers], ["Лайки", totalLikes]].map(([label, value]) => (
            <div key={label as string} className="flex flex-col items-center">
              <span className="text-xl font-black">{compactCount(value as number)}</span>
              <span className="text-xs text-zinc-500">{label}</span>
            </div>
          ))}
        </div>

        <div className="flex gap-2 w-full mt-4">
          {isOwn ? (
            <>
              <button onClick={onCreate} className="flex-1 h-11 rounded-xl bg-primary text-white font-bold">Снять видео</button>
              <button onClick={onOpenLibrary} className="flex-1 h-11 rounded-xl bg-zinc-100 dark:bg-zinc-800 font-bold">Звуки</button>
            </>
          ) : (
            <button
              onClick={toggleFollow}
              className={`flex-1 h-11 rounded-xl font-bold ${following ? "bg-zinc-100 dark:bg-zinc-800" : "bg-primary text-white"}`}
            >
              {following ? "Вы подписаны" : "Подписаться"}
            </button>
          )}
        </div>
      </div>

      {/* Вкладки */}
      <div className="flex mt-4">
        {(isOwn ? (["grid", "heart", "music"] as const) : (["grid"] as const)).map((icon, i) => (
          <button key={icon} onClick={() => setTab(i)} className="flex-1 pt-2.5 flex flex-col items-center" style={{ opacity: tab === i ? 1 : 0.4 }}>
            <Icon name={icon} size={26} bold={tab === i} />
            <div className={`mt-2 h-[2px] w-full ${tab === i ? "bg-zinc-900 dark:bg-white" : "bg-transparent"}`} />
          </button>
        ))}
      </div>

      <div className="pt-1">
        {tab === 0 && (videos === null
          ? <Spinner />
          : grid(visibleVideos, "Здесь будут видео", () => isOwn))}
        {tab === 1 && (liked === null
          ? <Spinner />
          : grid(visibleLiked, "Видео, которые вы лайкнули, появятся здесь", (v) => v.authorUsername === me.username))}
        {tab === 2 && (favSounds === null ? <Spinner /> : favSounds.length === 0 ? (
          <p className="text-center text-zinc-400 px-10 py-12">Добавляйте звуки в избранное звездой, чтобы потом снимать под них видео</p>
        ) : (
          favSounds.map((s) => (
            <button key={s.id} onClick={() => onOpenSound(s.id)} className="w-full flex items-center gap-3 px-3 py-2 text-left hover:bg-zinc-100/60 dark:hover:bg-zinc-800/40">
              <SoundDisc size={48} />
              <div className="flex-1 min-w-0">
                <p className="font-bold truncate">{s.title}</p>
                <p className="text-xs text-zinc-500">@{s.authorUsername} · {compactCount(s.uses)} видео</p>
              </div>
              <Icon name="star" size={22} bold className="text-[#ffc83d]" />
            </button>
          ))
        ))}
      </div>

      {deleteTarget && (
        <ConfirmDialog
          title="Удалить видео?"
          text="Видео и все комментарии к нему будут удалены. Это действие нельзя отменить."
          onCancel={() => setDeleteTarget(null)}
          onConfirm={() => { deleteShort(deleteTarget); setDeleteTarget(null); }}
        />
      )}
    </div>
  );
};

const Spinner = () => (
  <div className="flex justify-center py-12"><div className="h-8 w-8 animate-spin border-4 border-primary border-t-transparent rounded-full" /></div>
);
