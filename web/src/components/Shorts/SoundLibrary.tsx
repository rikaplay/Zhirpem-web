"use client";

import React, { useEffect, useRef, useState } from "react";
import { db } from "@/lib/firebase";
import { collection, getDocs, limit, onSnapshot, orderBy, query, where, doc } from "firebase/firestore";
import { Icon } from "@/components/ui/Icon";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { ShortThumb } from "./ShortThumb";
import {
  compactCount, deleteSound, formatDuration, parseShort, parseSound, resolveShort, ShortSound, ShortVideo,
  toggleFavoriteSound, uploadSound, isShortDeleted, useShortsStore
} from "@/lib/shorts";
import { SessionUser } from "@/lib/social";
import { useLongPress } from "@/lib/useLongPress";

/** Один общий плеер для прослушивания звуков в списке. */
function useSoundPreview() {
  const ref = useRef<HTMLAudioElement | null>(null);
  const [playingId, setPlayingId] = useState<string | null>(null);

  useEffect(() => {
    const a = new Audio();
    a.onended = () => setPlayingId(null);
    ref.current = a;
    return () => { a.pause(); ref.current = null; };
  }, []);

  const toggle = (s: ShortSound) => {
    const a = ref.current;
    if (!a) return;
    if (playingId === s.id) { a.pause(); setPlayingId(null); return; }
    a.src = s.audioUrl;
    a.play().catch(() => {});
    setPlayingId(s.id);
  };
  const stop = () => { ref.current?.pause(); setPlayingId(null); };
  return { playingId, toggle, stop };
}

export const SoundDisc: React.FC<{ size: number }> = ({ size }) => (
  <div
    className="rounded-full border border-white/30 flex items-center justify-center text-white flex-shrink-0"
    style={{ width: size, height: size, background: "conic-gradient(#2b2b2b, #707070, #2b2b2b)" }}
  >
    <Icon name="music" size={Math.round(size * 0.45)} />
  </div>
);

const DeleteSoundDialog: React.FC<{ sound: ShortSound; onConfirm: () => void; onCancel: () => void }> = ({ sound, onConfirm, onCancel }) => (
  <ConfirmDialog
    title="Удалить звук?"
    text={`«${sound.title}» исчезнет из библиотеки и избранного у всех.` +
      (sound.uses > 0 ? ` Под него снято видео: ${sound.uses}. У видео со звуком из библиотеки будет играть исходная дорожка.` : "")}
    onConfirm={onConfirm}
    onCancel={onCancel}
  />
);

const SoundRow: React.FC<{
  sound: ShortSound; me: string; playing: boolean; pickMode: boolean;
  onPreview: () => void; onFavorite: (f: boolean) => void; onDelete: () => void; onClick: () => void;
}> = ({ sound, me, playing, pickMode, onPreview, onFavorite, onDelete, onClick }) => {
  const favorite = sound.favoritedBy.includes(me);
  const own = sound.authorUsername === me;
  const lp = useLongPress(() => own && onDelete());
  return (
    <div
      className="flex items-center gap-3 px-4 py-2 cursor-pointer hover:bg-zinc-100/60 dark:hover:bg-zinc-800/40"
      {...(own ? lp.handlers : {})}
      onClick={() => { if (own && lp.wasLong()) return; onClick(); }}
    >
      <button
        onClick={(e) => { e.stopPropagation(); onPreview(); }}
        className="relative flex-shrink-0"
        title={playing ? "Остановить" : "Прослушать"}
      >
        <SoundDisc size={52} />
        <span className="absolute inset-0 flex items-center justify-center text-white"><Icon name={playing ? "pause" : "play"} size={22} solid /></span>
      </button>
      <div className="flex-1 min-w-0">
        <p className="font-bold truncate">{sound.title}</p>
        <p className="text-xs text-zinc-500 truncate">@{sound.authorUsername} · {formatDuration(sound.durationMs)} · {compactCount(sound.uses)} видео</p>
      </div>
      <button
        onClick={(e) => { e.stopPropagation(); onFavorite(!favorite); }}
        className="p-2 rounded-full"
        style={{ color: favorite ? "#ffc83d" : undefined }}
        title="В избранное"
      >
        <Icon name="star" size={24} bold={favorite} />
      </button>
      {own && (
        <button onClick={(e) => { e.stopPropagation(); onDelete(); }} className="p-2 rounded-full text-red-500" title="Удалить звук">
          <Icon name="trash" size={20} />
        </button>
      )}
      {pickMode && (
        <button onClick={(e) => { e.stopPropagation(); onClick(); }} className="px-3 py-1.5 rounded-full bg-primary text-white text-xs font-black">
          Выбрать
        </button>
      )}
    </div>
  );
};

const TABS = ["Популярные", "Новые", "Избранные", "Мои"];

/**
 * Площадка со звуками. Любой пользователь может загрузить свой звук, и он станет доступен всем.
 * В режиме pickMode нажатие на звук выбирает его для съёмки. Свои звуки удаляются удержанием.
 */
export const SoundLibrary: React.FC<{
  me: SessionUser;
  pickMode: boolean;
  onPick: (s: ShortSound) => void;
  onOpenSound: (id: string) => void;
  onBack: () => void;
}> = ({ me, pickMode, onPick, onOpenSound, onBack }) => {
  const preview = useSoundPreview();
  const [tab, setTab] = useState(0);
  const [search, setSearch] = useState("");
  const [sounds, setSounds] = useState<ShortSound[]>([]);
  const [loading, setLoading] = useState(true);
  const [refresh, setRefresh] = useState(0);
  const [deleteTarget, setDeleteTarget] = useState<ShortSound | null>(null);

  const fileRef = useRef<HTMLInputElement>(null);
  const [pendingFile, setPendingFile] = useState<File | null>(null);
  const [title, setTitle] = useState("");
  const [uploading, setUploading] = useState(false);
  const [progress, setProgress] = useState(0);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    preview.stop();
    const base = collection(db, "sounds");
    // Только простые запросы: составные индексы Firestore не нужны, сортировка на устройстве
    const q =
      tab === 0 ? query(base, orderBy("uses", "desc"), limit(60))
      : tab === 1 ? query(base, orderBy("timestamp", "desc"), limit(60))
      : tab === 2 ? query(base, where("favoritedBy", "array-contains", me.username))
      : query(base, where("authorUsername", "==", me.username));
    getDocs(q)
      .then((snap) => {
        if (cancelled) return;
        let list = snap.docs.map(parseSound);
        if (tab === 2) list = list.sort((a, b) => a.title.toLowerCase().localeCompare(b.title.toLowerCase()));
        if (tab === 3) list = list.sort((a, b) => b.timestampMs - a.timestampMs);
        setSounds(list);
      })
      .catch(() => !cancelled && setSounds([]))
      .finally(() => !cancelled && setLoading(false));
    return () => { cancelled = true; };
  }, [tab, refresh, me.username]); // eslint-disable-line react-hooks/exhaustive-deps

  const q = search.trim().toLowerCase();
  const shown = q ? sounds.filter((s) => s.title.toLowerCase().includes(q) || s.authorUsername.toLowerCase().includes(q)) : sounds;

  const doUpload = async () => {
    if (!pendingFile) return;
    setUploading(true); setError(""); setProgress(0);
    try {
      await uploadSound(pendingFile, title, me, setProgress);
      setPendingFile(null); setTitle("");
      setTab(3); setRefresh((r) => r + 1);
    } catch (e: any) {
      setError(e?.message || "Не удалось загрузить звук");
    } finally {
      setUploading(false);
    }
  };

  return (
    <div className="absolute inset-0 z-30 bg-background-light dark:bg-background-dark flex flex-col">
      <div className="flex items-center gap-2 px-3 pt-4 pb-2">
        <button onClick={() => { preview.stop(); onBack(); }} className="p-2.5 rounded-full hover:bg-zinc-100 dark:hover:bg-zinc-800"><Icon name="back" size={22} /></button>
        <h2 className="flex-1 text-xl font-black">{pickMode ? "Выбрать звук" : "Звуки"}</h2>
        <button
          onClick={() => fileRef.current?.click()}
          className="flex items-center gap-1.5 px-3.5 py-2 rounded-full bg-primary text-white text-sm font-black"
        >
          <Icon name="upload" size={18} /> Загрузить
        </button>
        <input
          ref={fileRef}
          type="file"
          accept="audio/*"
          className="hidden"
          onChange={(e) => { const f = e.target.files?.[0]; if (f) { setPendingFile(f); setTitle(f.name.replace(/\.[^.]+$/, "")); setError(""); } e.target.value = ""; }}
        />
      </div>

      <div className="px-4">
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Поиск звука или автора"
          className="w-full bg-zinc-100 dark:bg-zinc-800 rounded-full px-4 py-3 text-sm outline-none"
        />
      </div>
      <div className="flex gap-2 px-3 py-3 overflow-x-auto">
        {TABS.map((name, i) => (
          <button
            key={name}
            onClick={() => setTab(i)}
            className={`px-3.5 py-1.5 rounded-full text-sm font-bold whitespace-nowrap ${tab === i ? "bg-primary text-white" : "bg-zinc-100 dark:bg-zinc-800"}`}
          >
            {name}
          </button>
        ))}
      </div>

      <div className="flex-1 overflow-y-auto pb-32">
        {loading ? (
          <div className="flex justify-center py-16"><div className="h-8 w-8 animate-spin border-4 border-primary border-t-transparent rounded-full" /></div>
        ) : shown.length === 0 ? (
          <p className="text-center text-zinc-400 px-10 py-16">
            {tab === 2 ? "Добавляйте звуки в избранное звездой, и они появятся здесь"
              : tab === 3 ? "Вы ещё не загружали звуки. Загруженный звук можно удалить удержанием"
              : "Звуков пока нет. Загрузите первый!"}
          </p>
        ) : (
          shown.map((s) => (
            <SoundRow
              key={s.id}
              sound={s}
              me={me.username}
              playing={preview.playingId === s.id}
              pickMode={pickMode}
              onPreview={() => preview.toggle(s)}
              onFavorite={(fav) => {
                toggleFavoriteSound(s, me.username, fav);
                setSounds((list) => list.map((x) => x.id === s.id
                  ? { ...x, favoritedBy: fav ? [...x.favoritedBy, me.username] : x.favoritedBy.filter((u) => u !== me.username) }
                  : x));
              }}
              onDelete={() => setDeleteTarget(s)}
              onClick={() => { preview.stop(); if (pickMode) onPick(s); else onOpenSound(s.id); }}
            />
          ))
        )}
      </div>

      {deleteTarget && (
        <DeleteSoundDialog
          sound={deleteTarget}
          onCancel={() => setDeleteTarget(null)}
          onConfirm={() => {
            preview.stop();
            deleteSound(deleteTarget);
            setSounds((list) => list.filter((x) => x.id !== deleteTarget.id));
            setDeleteTarget(null);
          }}
        />
      )}

      {pendingFile && (
        <div className="fixed inset-0 z-[110] flex items-center justify-center p-6" onClick={() => !uploading && setPendingFile(null)}>
          <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" />
          <div className="relative bg-white dark:bg-zinc-900 rounded-3xl p-6 max-w-sm w-full space-y-4" onClick={(e) => e.stopPropagation()}>
            <h3 className="font-black text-lg">Новый звук</h3>
            <input
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              disabled={uploading}
              placeholder="Название"
              className="w-full bg-zinc-100 dark:bg-zinc-800 rounded-2xl px-4 py-3 outline-none"
            />
            {uploading ? (
              <div className="h-2 rounded-full bg-zinc-200 dark:bg-zinc-800 overflow-hidden"><div className="h-full bg-primary transition-all" style={{ width: `${progress * 100}%` }} /></div>
            ) : (
              <p className="text-xs text-zinc-500">После загрузки звук появится в общей библиотеке, и под него смогут снимать все.</p>
            )}
            {error && <p className="text-sm text-red-500">{error}</p>}
            <div className="flex gap-3">
              <button disabled={uploading} onClick={() => setPendingFile(null)} className="flex-1 py-3 rounded-2xl bg-zinc-100 dark:bg-zinc-800 font-bold disabled:opacity-50">Отмена</button>
              <button disabled={uploading} onClick={doUpload} className="flex-1 py-3 rounded-2xl bg-primary text-white font-black disabled:opacity-50">Загрузить</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

/** Страница звука: видео под этот звук и кнопка «Снять видео». */
export const SoundDetail: React.FC<{
  soundId: string;
  me: SessionUser;
  onBack: () => void;
  onUse: (s: ShortSound) => void;
  onOpenViewer: (videos: ShortVideo[], index: number) => void;
  onOpenProfile: (u: string) => void;
}> = ({ soundId, me, onBack, onUse, onOpenViewer, onOpenProfile }) => {
  useShortsStore();
  const preview = useSoundPreview();
  const [sound, setSound] = useState<ShortSound | null>(null);
  const [notFound, setNotFound] = useState(false);
  const [videos, setVideos] = useState<ShortVideo[]>([]);
  const [loading, setLoading] = useState(true);
  const [showDelete, setShowDelete] = useState(false);

  useEffect(() => {
    const unsub = onSnapshot(doc(db, "sounds", soundId), (d) => {
      if (d.exists()) setSound(parseSound(d)); else setNotFound(true);
    });
    getDocs(query(collection(db, "shorts"), where("soundId", "==", soundId)))
      .then((snap) => setVideos(snap.docs.map(parseShort).sort((a, b) => b.likes - a.likes)))
      .catch(() => {})
      .finally(() => setLoading(false));
    return () => { unsub(); preview.stop(); };
  }, [soundId]); // eslint-disable-line react-hooks/exhaustive-deps

  const visible = videos.filter((v) => !isShortDeleted(v.id));
  const favorite = !!sound && sound.favoritedBy.includes(me.username);

  return (
    <div className="absolute inset-0 z-30 bg-background-light dark:bg-background-dark flex flex-col overflow-y-auto pb-32">
      <div className="flex items-center gap-2 px-3 pt-4 pb-2">
        <button onClick={() => { preview.stop(); onBack(); }} className="p-2.5 rounded-full hover:bg-zinc-100 dark:hover:bg-zinc-800"><Icon name="back" size={22} /></button>
        <h2 className="flex-1 text-xl font-black">Звук</h2>
        {sound && sound.authorUsername === me.username && (
          <button onClick={() => setShowDelete(true)} className="p-2.5 rounded-full text-red-500" title="Удалить звук"><Icon name="trash" size={22} /></button>
        )}
      </div>

      {!sound ? (
        <p className="text-center text-zinc-400 py-20">{notFound ? "Этот звук удалён" : "Загрузка…"}</p>
      ) : (
        <>
          <div className="flex flex-col items-center px-4 pt-2 pb-4">
            <SoundDisc size={96} />
            <h3 className="text-xl font-black mt-3 text-center">{sound.title}</h3>
            <button onClick={() => onOpenProfile(sound.authorUsername)} className="text-primary font-bold py-1">@{sound.authorUsername}</button>
            <p className="text-sm text-zinc-500">{formatDuration(sound.durationMs)} · {compactCount(sound.uses)} видео</p>
            <div className="flex items-center gap-3 mt-4">
              <button onClick={() => preview.toggle(sound)} className="w-12 h-12 rounded-full bg-zinc-100 dark:bg-zinc-800 flex items-center justify-center">
                <Icon name={preview.playingId === sound.id ? "pause" : "play"} size={22} solid />
              </button>
              <button onClick={() => { preview.stop(); onUse(sound); }} className="flex items-center gap-2 px-6 py-3 rounded-full bg-primary text-white font-black">
                <Icon name="plus" size={20} /> Снять видео
              </button>
              <button
                onClick={() => {
                  const now = !favorite;
                  toggleFavoriteSound(sound, me.username, now);
                  setSound({ ...sound, favoritedBy: now ? [...sound.favoritedBy, me.username] : sound.favoritedBy.filter((u) => u !== me.username) });
                }}
                className="w-12 h-12 rounded-full bg-zinc-100 dark:bg-zinc-800 flex items-center justify-center"
                style={{ color: favorite ? "#ffc83d" : undefined }}
              >
                <Icon name="star" size={22} bold={favorite} />
              </button>
            </div>
          </div>
          <div className="px-1.5">
            {loading ? (
              <div className="flex justify-center py-8"><div className="h-8 w-8 animate-spin border-4 border-primary border-t-transparent rounded-full" /></div>
            ) : visible.length === 0 ? (
              <p className="text-center text-zinc-400 px-8 py-8">Под этот звук ещё никто не снимал. Будьте первым!</p>
            ) : (
              <div className="grid grid-cols-3 gap-1">
                {visible.map((v, i) => <ShortThumb key={v.id} video={resolveShort(v)} onClick={() => onOpenViewer(visible, i)} />)}
              </div>
            )}
          </div>
        </>
      )}

      {showDelete && sound && (
        <DeleteSoundDialog
          sound={sound}
          onCancel={() => setShowDelete(false)}
          onConfirm={() => { preview.stop(); deleteSound(sound); setShowDelete(false); onBack(); }}
        />
      )}
    </div>
  );
};
