"use client";

import React, { useCallback, useEffect, useRef, useState } from "react";
import { Icon } from "@/components/ui/Icon";
import { ShortsFeed } from "./ShortsFeed";
import { ShortsProfile } from "./ShortsProfile";
import { SoundDetail, SoundLibrary } from "./SoundLibrary";
import { ShortsCreate } from "./ShortsCreate";
import { ForYouLoader, loadFollowingShorts, loadShort, ShortSound, ShortVideo } from "@/lib/shorts";
import { SessionUser } from "@/lib/social";
import { useUserAvatar } from "@/lib/avatars";

type Route =
  | { t: "profile"; username: string }
  | { t: "sound"; id: string }
  | { t: "viewer"; videos: ShortVideo[]; index: number }
  | { t: "create"; sound: ShortSound | null }
  | { t: "library" };

const FeedTab: React.FC<{ title: string; selected: boolean; onClick: () => void }> = ({ title, selected, onClick }) => (
  <button onClick={onClick} className="flex flex-col items-center py-2" style={{ color: selected ? "#fff" : "rgba(255,255,255,.6)" }}>
    <span className={`text-[17px] ${selected ? "font-black" : "font-semibold"}`}>{title}</span>
    <span className="mt-1 h-[3px] w-6 rounded-full" style={{ background: selected ? "#fff" : "transparent" }} />
  </button>
);

const MyAvatarButton: React.FC<{ me: string; onClick: () => void }> = ({ me, onClick }) => {
  const avatar = useUserAvatar(me);
  return (
    <button onClick={onClick} title="Мой профиль"
      className="w-[42px] h-[42px] rounded-full overflow-hidden bg-white/20 flex items-center justify-center text-white">
      {avatar ? <img src={avatar} alt="" className="w-full h-full object-cover" /> : <Icon name="user" size={24} />}
    </button>
  );
};

/**
 * Вкладка ZHIRPEM Shorts. Нижняя панель приложения здесь всегда на экране: видео идёт под ней.
 * Прячется панель только на съёмке (onFullscreenChange).
 */
export const ShortsHost: React.FC<{
  me: SessionUser;
  openShortId?: string | null;
  onShortOpened?: () => void;
  createRequest: number;
  onFullscreenChange: (fullscreen: boolean) => void;
}> = ({ me, openShortId, onShortOpened, createRequest, onFullscreenChange }) => {
  const [stack, setStack] = useState<Route[]>([]);
  const [feedTab, setFeedTab] = useState(0); // 0 — рекомендации, 1 — подписки
  const [forYou, setForYou] = useState<ShortVideo[]>([]);
  const [following, setFollowing] = useState<ShortVideo[]>([]);
  const [loadingForYou, setLoadingForYou] = useState(false);
  const [loadingFollowing, setLoadingFollowing] = useState(false);
  const loader = useRef(new ForYouLoader());
  const busy = useRef(false);

  const push = useCallback((r: Route) => setStack((s) => [...s, r]), []);
  const pop = useCallback(() => setStack((s) => s.slice(0, -1)), []);
  const top = stack[stack.length - 1];

  useEffect(() => { onFullscreenChange(top?.t === "create"); }, [top?.t]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => () => onFullscreenChange(false), []); // eslint-disable-line react-hooks/exhaustive-deps

  const loadForYou = useCallback(async (refresh: boolean) => {
    if (busy.current) return;
    busy.current = true;
    setLoadingForYou(true);
    try {
      if (refresh) loader.current.reset();
      const batch = await loader.current.next(me.username);
      setForYou((cur) => (refresh ? batch : [...cur, ...batch].filter((v, i, a) => a.findIndex((t) => t.id === v.id) === i)));
    } finally {
      busy.current = false;
      setLoadingForYou(false);
    }
  }, [me.username]);

  useEffect(() => { loadForYou(true); }, [loadForYou]);

  useEffect(() => {
    if (feedTab !== 1) return;
    setLoadingFollowing(true);
    loadFollowingShorts(me.username).then(setFollowing).catch(() => setFollowing([])).finally(() => setLoadingFollowing(false));
  }, [feedTab, me.username]);

  // «+» на вкладке Shorts открывает съёмку
  const lastCreate = useRef(createRequest);
  useEffect(() => {
    if (createRequest !== lastCreate.current) {
      lastCreate.current = createRequest;
      if (top?.t !== "create") push({ t: "create", sound: null });
    }
  }, [createRequest]); // eslint-disable-line react-hooks/exhaustive-deps

  // Ссылка на видео (например, из личных сообщений)
  useEffect(() => {
    if (!openShortId) return;
    loadShort(openShortId).then((v) => { if (v) push({ t: "viewer", videos: [v], index: 0 }); }).finally(() => onShortOpened && onShortOpened());
  }, [openShortId]); // eslint-disable-line react-hooks/exhaustive-deps

  const openProfile = (u: string) => push({ t: "profile", username: u });
  const openSound = (id: string) => push({ t: "sound", id });

  return (
    <div className="relative h-full w-full bg-black overflow-hidden">
      <ShortsFeed
        key={feedTab}
        videos={feedTab === 0 ? forYou : following}
        me={me}
        screenActive={stack.length === 0}
        onOpenProfile={openProfile}
        onOpenSound={openSound}
        onLoadMore={feedTab === 0 ? () => loadForYou(false) : undefined}
        loading={feedTab === 0 ? loadingForYou : loadingFollowing}
        emptyText={feedTab === 0 ? "Видео пока нет. Нажмите «+» и снимите первое!" : "Здесь появятся видео авторов, на которых вы подписаны"}
      />

      {/* Верхняя строка: звуки · Рекомендации | Подписки · мой профиль */}
      <div className="absolute top-0 inset-x-0 flex items-center px-3 pt-3 z-10 pointer-events-none">
        <button onClick={() => push({ t: "library" })} title="Звуки"
          className="pointer-events-auto w-[42px] h-[42px] rounded-full flex items-center justify-center text-white">
          <Icon name="music" size={24} />
        </button>
        <div className="flex-1 flex items-center justify-center gap-5 pointer-events-auto">
          <FeedTab title="Рекомендации" selected={feedTab === 0} onClick={() => setFeedTab(0)} />
          <FeedTab title="Подписки" selected={feedTab === 1} onClick={() => setFeedTab(1)} />
        </div>
        <div className="pointer-events-auto"><MyAvatarButton me={me.username} onClick={() => openProfile(me.username)} /></div>
      </div>

      {/* Экраны поверх ленты */}
      {top?.t === "profile" && (
        <ShortsProfile
          key={`p-${stack.length}-${top.username}`}
          username={top.username}
          me={me}
          onBack={pop}
          onOpenViewer={(videos, index) => push({ t: "viewer", videos, index })}
          onOpenSound={openSound}
          onOpenLibrary={() => push({ t: "library" })}
          onCreate={() => push({ t: "create", sound: null })}
        />
      )}
      {top?.t === "sound" && (
        <SoundDetail
          key={`s-${top.id}`}
          soundId={top.id}
          me={me}
          onBack={pop}
          onUse={(s) => push({ t: "create", sound: s })}
          onOpenViewer={(videos, index) => push({ t: "viewer", videos, index })}
          onOpenProfile={openProfile}
        />
      )}
      {top?.t === "viewer" && (
        <div className="absolute inset-0 z-30 bg-black">
          <ShortsFeed videos={top.videos} startIndex={top.index} me={me} screenActive onOpenProfile={openProfile} onOpenSound={openSound} />
          <button onClick={pop} className="absolute top-4 left-3 w-10 h-10 rounded-full bg-black/35 text-white flex items-center justify-center backdrop-blur z-10">
            <Icon name="back" size={22} />
          </button>
        </div>
      )}
      {top?.t === "library" && (
        <SoundLibrary me={me} pickMode={false} onPick={() => {}} onOpenSound={openSound} onBack={pop} />
      )}
      {top?.t === "create" && (
        <ShortsCreate
          me={me}
          initialSound={top.sound}
          onClose={pop}
          onPublished={() => { pop(); loadForYou(true); }}
        />
      )}
    </div>
  );
};
