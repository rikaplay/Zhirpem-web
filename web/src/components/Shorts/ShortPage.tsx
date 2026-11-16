"use client";

import React, { useEffect, useRef, useState } from "react";
import { Icon } from "@/components/ui/Icon";
import { useUserAvatar } from "@/lib/avatars";
import {
  compactCount, loadSound, markShortViewed, resolveShort, ShortSound, ShortVideo, thumbUrl, playUrl,
  toggleFavoriteSound, toggleShortLike, toggleShortRepost, useShortsStore
} from "@/lib/shorts";
import { follow, isFollowing, SessionUser } from "@/lib/social";
import { useLongPress } from "@/lib/useLongPress";

interface Props {
  video: ShortVideo;
  /** Эта страница сейчас на экране. */
  active: boolean;
  /** Вкладка Shorts открыта, и поверх нет других экранов. */
  screenActive: boolean;
  me: SessionUser;
  onOpenProfile: (username: string) => void;
  onOpenSound: (soundId: string) => void;
  onComments: (v: ShortVideo) => void;
  onShare: (v: ShortVideo) => void;
  onDelete: (v: ShortVideo) => void;
}

const RailButton: React.FC<{
  icon: any; label: string; active?: boolean; color?: string; solid?: boolean; onClick: () => void;
}> = ({ icon, label, active, color, solid, onClick }) => (
  <button
    onClick={onClick}
    className="flex flex-col items-center gap-0.5 active:scale-90 transition-transform"
    style={{ color: active && color ? color : "#fff", filter: "drop-shadow(0 2px 6px rgba(0,0,0,.55))" }}
  >
    <Icon name={icon} size={36} bold={!!active} solid={!!solid && !!active} />
    <span className="text-[12px] font-bold">{label}</span>
  </button>
);

export const ShortPage: React.FC<Props> = ({
  video: raw, active, screenActive, me, onOpenProfile, onOpenSound, onComments, onShare, onDelete
}) => {
  useShortsStore();
  const video = resolveShort(raw);
  const liked = video.likedBy.includes(me.username);
  const reposted = video.repostedBy.includes(me.username);
  const isMine = video.authorUsername === me.username;

  const vref = useRef<HTMLVideoElement>(null);
  const aref = useRef<HTMLAudioElement>(null);
  const [paused, setPaused] = useState(false);
  const [progress, setProgress] = useState(0);
  const [blocked, setBlocked] = useState(false);
  const [contain, setContain] = useState(false);
  const [burst, setBurst] = useState<{ x: number; y: number; k: number } | null>(null);
  const [pageVisible, setPageVisible] = useState(true);

  const [sound, setSound] = useState<ShortSound | null>(null);
  const [soundGone, setSoundGone] = useState(false);
  const [favorite, setFavorite] = useState(false);
  const [following, setFollowing] = useState(true);

  const avatar = useUserAvatar(video.authorUsername, video.authorAvatarUrl);
  const replaceAudio = video.muteOriginal && !!sound && !soundGone;
  const playing = active && screenActive && !paused && pageVisible;

  // Звук из библиотеки подгружаем только для текущего видео
  useEffect(() => {
    if (!active || !video.soundId || sound || soundGone) return;
    loadSound(video.soundId)
      .then((s) => { if (s) { setSound(s); setFavorite(s.favoritedBy.includes(me.username)); } else setSoundGone(true); })
      .catch(() => {});
  }, [active, video.soundId, sound, soundGone, me.username]);

  // Подписан ли я на автора
  useEffect(() => {
    if (!active || isMine) return;
    isFollowing(me.username, video.authorUsername).then(setFollowing).catch(() => {});
  }, [active, isMine, me.username, video.authorUsername]);

  useEffect(() => {
    const h = () => setPageVisible(document.visibilityState === "visible");
    document.addEventListener("visibilitychange", h);
    return () => document.removeEventListener("visibilitychange", h);
  }, []);

  // Воспроизведение: видео и (если нужно) отдельная дорожка со звуком из библиотеки
  useEffect(() => {
    const v = vref.current;
    const a = aref.current;
    if (v) {
      v.muted = replaceAudio;
      if (playing) {
        v.play().catch(() => { v.muted = true; v.play().catch(() => {}); setBlocked(true); });
      } else {
        v.pause();
      }
    }
    if (a) {
      if (playing && replaceAudio) a.play().catch(() => setBlocked(true));
      else a.pause();
    }
  }, [playing, replaceAudio, active]);

  // Ушли со страницы: начинаем сначала
  useEffect(() => {
    if (!active) {
      setPaused(false);
      setProgress(0);
      if (vref.current) vref.current.currentTime = 0;
      if (aref.current) aref.current.currentTime = 0;
    }
  }, [active]);

  // Просмотр засчитываем, если смотрели больше 2 секунд
  useEffect(() => {
    if (!playing) return;
    const t = setTimeout(() => markShortViewed(video, me.username), 2000);
    return () => clearTimeout(t);
  }, [playing, video.id, me.username]); // eslint-disable-line react-hooks/exhaustive-deps

  const enableSound = () => {
    const v = vref.current;
    const a = aref.current;
    if (v) { v.muted = replaceAudio; v.play().catch(() => {}); }
    if (a && replaceAudio) a.play().catch(() => {});
    setBlocked(false);
  };

  // ---- жесты: тап — пауза, двойной тап — лайк, удержание — удаление своего видео ----
  const lastTap = useRef(0);
  const singleTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const longPress = useLongPress(() => { if (isMine) onDelete(video); });

  const handleTap = (e: React.MouseEvent<HTMLDivElement>) => {
    if (longPress.wasLong()) return;
    const now = Date.now();
    if (now - lastTap.current < 300) {
      if (singleTimer.current) clearTimeout(singleTimer.current);
      lastTap.current = 0;
      const rect = e.currentTarget.getBoundingClientRect();
      setBurst({ x: e.clientX - rect.left, y: e.clientY - rect.top, k: now });
      if (!liked) toggleShortLike(video, me);
    } else {
      lastTap.current = now;
      singleTimer.current = setTimeout(() => setPaused((p) => !p), 250);
    }
  };

  const stop = { onPointerDown: (e: React.PointerEvent) => e.stopPropagation(), onClick: (e: React.MouseEvent) => e.stopPropagation() };
  const title = video.soundTitle || `Оригинальный звук — @${video.authorUsername}`;

  return (
    <div
      className="relative h-full w-full bg-black overflow-hidden select-none"
      {...longPress.handlers}
      onClick={handleTap}
    >
      {/* Превью, пока видео не загрузилось */}
      <img src={thumbUrl(video.videoUrl, 720)} alt="" className="absolute inset-0 w-full h-full object-cover" draggable={false} />

      {active && (
        <video
          ref={vref}
          src={playUrl(video.videoUrl)}
          poster={thumbUrl(video.videoUrl, 720)}
          className={`absolute inset-0 w-full h-full ${contain ? "object-contain" : "object-cover"}`}
          loop
          playsInline
          preload="auto"
          onLoadedMetadata={(e) => {
            const el = e.currentTarget;
            setContain(el.videoWidth / Math.max(1, el.videoHeight) > 0.8);
          }}
          onTimeUpdate={(e) => {
            const el = e.currentTarget;
            if (el.duration) setProgress(el.currentTime / el.duration);
            // Видео пошло по кругу: звук тоже начинаем сначала, чтобы не разъезжались
            const a = aref.current;
            if (replaceAudio && a && el.currentTime < 0.25 && a.currentTime > 1) a.currentTime = 0;
          }}
        />
      )}
      {active && replaceAudio && sound && <audio ref={aref} src={sound.audioUrl} loop preload="auto" />}

      {/* Затемнение сверху и снизу для читаемости */}
      <div className="absolute inset-x-0 top-0 h-40 bg-gradient-to-b from-black/45 to-transparent pointer-events-none" />
      <div className="absolute inset-x-0 bottom-0 h-96 bg-gradient-to-t from-black/70 to-transparent pointer-events-none" />

      {paused && active && (
        <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
          <Icon name="play" size={84} bold className="text-white/80" />
        </div>
      )}

      {blocked && (
        <button
          {...stop}
          onClick={(e) => { e.stopPropagation(); enableSound(); }}
          className="absolute top-24 left-1/2 -translate-x-1/2 px-4 py-2 rounded-full bg-black/60 text-white text-sm font-bold backdrop-blur"
        >
          Нажмите, чтобы включить звук
        </button>
      )}

      {burst && (
        <Icon
          key={burst.k}
          name="heart"
          size={96}
          solid
          className="heart-burst absolute text-[#ff2d55] pointer-events-none"
          style={{ left: burst.x, top: burst.y }}
        />
      )}

      {/* Колонка действий справа: лайк, комментарии, репост, «поделиться», под ними звук */}
      <div className="absolute right-2 bottom-[190px] flex flex-col items-center gap-4" {...stop}>
        <RailButton icon="heart" label={compactCount(video.likes)} active={liked} color="#ff2d55" solid onClick={() => toggleShortLike(video, me)} />
        <RailButton icon="chat" label={compactCount(video.commentsCount)} onClick={() => onComments(video)} />
        <RailButton icon="repost" label={compactCount(video.repostsCount)} active={reposted} color="#4cd964" onClick={() => toggleShortRepost(video, me)} />
        <RailButton icon="share" label="Поделиться" onClick={() => onShare(video)} />

        {video.soundId && (
          <button
            onClick={() => {
              if (!sound) return;
              const next = !favorite;
              setFavorite(next);
              toggleFavoriteSound(sound, me.username, next);
            }}
            disabled={!sound}
            title="Добавить звук в избранное"
            className="w-10 h-10 rounded-full bg-black/30 flex items-center justify-center active:scale-90 transition-transform"
            style={{ color: favorite ? "#ffc83d" : "#fff" }}
          >
            <Icon name="star" size={22} bold={favorite} />
          </button>
        )}
        <button
          onClick={() => video.soundId && onOpenSound(video.soundId)}
          className="w-11 h-11 rounded-full border border-white/40 flex items-center justify-center text-white spin-slow"
          style={{
            background: "conic-gradient(#2b2b2b, #6a6a6a, #2b2b2b)",
            animationPlayState: playing ? "running" : "paused"
          }}
        >
          <Icon name="music" size={20} />
        </button>
      </div>

      {/* Автор, подпись и название звука — слева внизу */}
      <div className="absolute left-3 right-[84px] bottom-[120px] space-y-2.5" {...stop}>
        <div className="flex items-center gap-2.5">
          <button
            onClick={() => onOpenProfile(video.authorUsername)}
            className="w-10 h-10 rounded-full overflow-hidden border-[1.5px] border-white bg-white/20 flex items-center justify-center text-white font-bold flex-shrink-0"
          >
            {avatar ? <img src={avatar} alt="" className="w-full h-full object-cover" /> : video.authorName.charAt(0).toUpperCase()}
          </button>
          <button onClick={() => onOpenProfile(video.authorUsername)} className="text-white font-black text-[15px] truncate drop-shadow">
            @{video.authorUsername}
          </button>
          {!following && !isMine && (
            <button
              onClick={() => { setFollowing(true); follow(me, video.authorUsername); }}
              className="px-2.5 py-1 rounded-lg border border-white text-white text-xs font-bold"
            >
              Подписаться
            </button>
          )}
        </div>
        {video.caption && <p className="text-white text-[14px] leading-snug line-clamp-3 drop-shadow">{video.caption}</p>}
        <button
          onClick={() => video.soundId && onOpenSound(video.soundId)}
          className="flex items-center gap-1.5 bg-black/30 rounded-2xl px-2.5 py-1.5 max-w-[220px] text-white"
        >
          <Icon name="music" size={14} />
          <span className="overflow-hidden w-[170px] text-xs text-left">
            <span className="marquee">{title}&nbsp;&nbsp;&nbsp;&nbsp;{title}&nbsp;&nbsp;&nbsp;&nbsp;</span>
          </span>
        </button>
      </div>

      {/* Полоска прогресса над нижней панелью */}
      <div className="absolute inset-x-0 bottom-[104px] h-[2px] bg-white/20 pointer-events-none">
        <div className="h-full bg-white" style={{ width: `${Math.min(100, progress * 100)}%` }} />
      </div>
    </div>
  );
};
