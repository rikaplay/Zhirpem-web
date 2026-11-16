"use client";

import React, { useCallback, useEffect, useRef, useState } from "react";
import { Icon } from "@/components/ui/Icon";
import { SoundLibrary } from "./SoundLibrary";
import {
  formatDuration, publishShort, readMediaDuration, SHORT_MAX_MS, SHORT_MAX_UPLOAD_BYTES, ShortSound, ShortVideo
} from "@/lib/shorts";
import { SessionUser } from "@/lib/social";

const ACCENT = "#ff2d55";

function pickMime(): string {
  if (typeof MediaRecorder === "undefined") return "";
  const options = [
    "video/webm;codecs=vp9,opus", "video/webm;codecs=vp8,opus", "video/webm", "video/mp4"
  ];
  return options.find((m) => MediaRecorder.isTypeSupported(m)) || "";
}

/** Кнопка записи с кольцом прогресса (до 3 минут). */
const RecordButton: React.FC<{ recording: boolean; progress: number; onClick: () => void }> = ({ recording, progress, onClick }) => {
  const r = 42;
  const c = 2 * Math.PI * r;
  return (
    <button onClick={onClick} className="relative w-[92px] h-[92px] flex items-center justify-center active:scale-95 transition-transform">
      <svg className="absolute inset-0 -rotate-90" viewBox="0 0 92 92">
        <circle cx="46" cy="46" r={r} fill="none" stroke="rgba(255,255,255,.4)" strokeWidth="6" />
        <circle cx="46" cy="46" r={r} fill="none" stroke={ACCENT} strokeWidth="6" strokeLinecap="round"
          strokeDasharray={c} strokeDashoffset={c * (1 - Math.min(1, progress))} />
      </svg>
      <span
        className="block transition-all"
        style={{
          width: recording ? 34 : 68, height: recording ? 34 : 68,
          borderRadius: recording ? 9 : 999, background: recording ? ACCENT : "#fff"
        }}
      />
    </button>
  );
};

const RoundBtn: React.FC<{ icon: any; title: string; onClick: () => void; disabled?: boolean }> = ({ icon, title, onClick, disabled }) => (
  <button
    onClick={onClick}
    disabled={disabled}
    title={title}
    className="w-11 h-11 rounded-full bg-black/35 text-white flex items-center justify-center backdrop-blur disabled:opacity-40 active:scale-90 transition-transform"
  >
    <Icon name={icon} size={22} />
  </button>
);

const SoundChooser: React.FC<{ sound: ShortSound | null; enabled: boolean; onChoose: () => void; onClear: () => void }> = ({ sound, enabled, onChoose, onClear }) => (
  <div className="flex items-center gap-1.5 bg-black/40 text-white rounded-full pl-3.5 pr-2 py-2 backdrop-blur max-w-[220px]">
    <button onClick={onChoose} disabled={!enabled} className="flex items-center gap-2 min-w-0 disabled:opacity-60">
      <Icon name="music" size={18} />
      <span className="truncate text-sm font-bold">{sound?.title || "Выбрать звук"}</span>
    </button>
    {sound && enabled && (
      <button onClick={onClear} className="p-1 rounded-full" title="Убрать звук"><Icon name="close" size={16} /></button>
    )}
  </div>
);

/**
 * Съёмка ZHIRPEM Shorts в браузере: запись до 3 минут с паузой, переворотом камеры,
 * импортом файла и выбором звука из библиотеки. С выбранным звуком микрофон отключается:
 * видео пишется без своей дорожки, а при просмотре вместо неё играет выбранный звук.
 */
export const ShortsCreate: React.FC<{
  me: SessionUser;
  initialSound: ShortSound | null;
  onClose: () => void;
  onPublished: (v: ShortVideo) => void;
}> = ({ me, initialSound, onClose, onPublished }) => {
  const [stage, setStage] = useState<"record" | "review">("record");
  const [sound, setSound] = useState<ShortSound | null>(initialSound);
  const [showPicker, setShowPicker] = useState(false);
  const [facing, setFacing] = useState<"user" | "environment">("user");
  const [camError, setCamError] = useState("");

  const [isRecording, setIsRecording] = useState(false);
  const [isPaused, setIsPaused] = useState(false);
  const [elapsed, setElapsed] = useState(0);

  const [blob, setBlob] = useState<Blob | null>(null);
  const [blobUrl, setBlobUrl] = useState("");
  const [duration, setDuration] = useState(0);
  const [caption, setCaption] = useState("");
  const [uploading, setUploading] = useState(false);
  const [progress, setProgress] = useState(0);
  const [error, setError] = useState("");

  const previewRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const recorderRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const elapsedRef = useRef(0);
  const fileInput = useRef<HTMLInputElement>(null);
  const discardRef = useRef(false);

  const stopStream = () => {
    streamRef.current?.getTracks().forEach((t) => t.stop());
    streamRef.current = null;
  };

  // Звук играет во время съёмки, чтобы под него можно было двигаться и петь
  useEffect(() => {
    audioRef.current?.pause();
    audioRef.current = null;
    if (sound) {
      const a = new Audio(sound.audioUrl);
      a.loop = true;
      a.preload = "auto";
      audioRef.current = a;
    }
    return () => { audioRef.current?.pause(); };
  }, [sound]);

  // Камера. Без выбранного звука включаем и микрофон
  useEffect(() => {
    if (stage !== "record" || isRecording) return;
    let cancelled = false;
    setCamError("");
    stopStream();
    if (!navigator.mediaDevices?.getUserMedia) {
      setCamError("Браузер не даёт доступ к камере. Откройте сайт по https в современном браузере.");
      return;
    }
    navigator.mediaDevices
      .getUserMedia({
        video: { facingMode: facing, width: { ideal: 720 }, height: { ideal: 1280 }, frameRate: { ideal: 30 } },
        audio: !sound
      })
      .then((stream) => {
        if (cancelled) { stream.getTracks().forEach((t) => t.stop()); return; }
        streamRef.current = stream;
        if (previewRef.current) { previewRef.current.srcObject = stream; previewRef.current.play().catch(() => {}); }
      })
      .catch(() => setCamError("Нет доступа к камере. Разрешите её в настройках браузера или загрузите видео из файла."));
    return () => { cancelled = true; };
  }, [facing, stage, sound?.id]); // eslint-disable-line react-hooks/exhaustive-deps

  // Всё освобождаем при закрытии
  useEffect(() => () => {
    stopStream();
    audioRef.current?.pause();
    if (recorderRef.current && recorderRef.current.state !== "inactive") {
      discardRef.current = true;
      try { recorderRef.current.stop(); } catch { /* ignore */ }
    }
  }, []);

  const finishRecording = useCallback(() => {
    const rec = recorderRef.current;
    if (rec && rec.state !== "inactive") rec.stop();
  }, []);

  // Таймер записи (паузы не считаются). Ровно 3 минуты, дальше нельзя
  useEffect(() => {
    if (!isRecording || isPaused) return;
    const id = setInterval(() => {
      elapsedRef.current += 100;
      setElapsed(elapsedRef.current);
      if (elapsedRef.current >= SHORT_MAX_MS) finishRecording();
    }, 100);
    return () => clearInterval(id);
  }, [isRecording, isPaused, finishRecording]);

  const startRecording = () => {
    const stream = streamRef.current;
    if (!stream) return;
    const mime = pickMime();
    let rec: MediaRecorder;
    try {
      rec = new MediaRecorder(stream, mime ? { mimeType: mime, videoBitsPerSecond: 2_500_000 } : undefined);
    } catch {
      setCamError("Запись видео не поддерживается в этом браузере. Загрузите видео из файла.");
      return;
    }
    chunksRef.current = [];
    discardRef.current = false;
    rec.ondataavailable = (e) => { if (e.data && e.data.size > 0) chunksRef.current.push(e.data); };
    rec.onstop = () => {
      setIsRecording(false);
      setIsPaused(false);
      audioRef.current?.pause();
      if (audioRef.current) audioRef.current.currentTime = 0;
      stopStream();
      if (discardRef.current) return;
      const type = rec.mimeType || mime || "video/webm";
      const result = new Blob(chunksRef.current, { type });
      if (result.size === 0) { setCamError("Не удалось записать видео"); return; }
      setBlob(result);
      setBlobUrl(URL.createObjectURL(result));
      setDuration(Math.min(SHORT_MAX_MS, elapsedRef.current));
      setStage("review");
    };
    recorderRef.current = rec;
    elapsedRef.current = 0;
    setElapsed(0);
    rec.start(1000);
    setIsRecording(true);
    setIsPaused(false);
    if (audioRef.current) { audioRef.current.currentTime = 0; audioRef.current.play().catch(() => {}); }
  };

  const togglePause = () => {
    const rec = recorderRef.current;
    if (!rec) return;
    if (isPaused) { rec.resume(); audioRef.current?.play().catch(() => {}); }
    else { rec.pause(); audioRef.current?.pause(); }
    setIsPaused(!isPaused);
  };

  const retake = () => {
    if (blobUrl) URL.revokeObjectURL(blobUrl);
    setBlob(null); setBlobUrl(""); setElapsed(0); elapsedRef.current = 0; setError("");
    setStage("record");
  };

  const importFile = async (file: File) => {
    if (file.size > SHORT_MAX_UPLOAD_BYTES) { setError("Видео больше 100 МБ. Выберите файл поменьше"); return; }
    const d = await readMediaDuration(file, "video");
    if (d > SHORT_MAX_MS) { setError("Видео должно быть не длиннее 3 минут"); return; }
    stopStream();
    setError("");
    setBlob(file);
    setBlobUrl(URL.createObjectURL(file));
    setDuration(d);
    setStage("review");
  };

  const close = () => {
    if (isRecording) { discardRef.current = true; finishRecording(); }
    onClose();
  };

  const publish = async () => {
    if (!blob) return;
    setUploading(true); setError(""); setProgress(0);
    try {
      const ext = blob.type.includes("mp4") ? "mp4" : "webm";
      const v = await publishShort({
        file: blob, fileName: `short.${ext}`, caption, sound, durationMs: duration, me, onProgress: setProgress
      });
      onPublished(v);
    } catch (e: any) {
      setError(e?.message || "Не удалось опубликовать");
      setUploading(false);
    }
  };

  return (
    <div className="absolute inset-0 z-40 bg-black text-white overflow-hidden">
      {stage === "record" ? (
        <>
          <video
            ref={previewRef}
            muted
            playsInline
            className="absolute inset-0 w-full h-full object-cover"
            style={{ transform: facing === "user" ? "scaleX(-1)" : undefined }}
          />
          <div className="absolute inset-x-0 top-0 h-36 bg-gradient-to-b from-black/50 to-transparent pointer-events-none" />

          <div className="absolute top-0 inset-x-0 flex items-center justify-between px-3 pt-4">
            <RoundBtn icon="close" title="Закрыть" onClick={close} />
            <SoundChooser sound={sound} enabled={!isRecording} onChoose={() => setShowPicker(true)} onClear={() => setSound(null)} />
            <div className="w-11" />
          </div>

          <div className="absolute right-3 top-1/2 -translate-y-1/2 flex flex-col gap-3">
            <RoundBtn icon="flip" title="Перевернуть камеру" disabled={isRecording}
              onClick={() => setFacing((f) => (f === "user" ? "environment" : "user"))} />
          </div>

          {camError && (
            <div className="absolute inset-x-6 top-1/3 text-center bg-black/60 rounded-2xl p-4 text-sm backdrop-blur">{camError}</div>
          )}

          <div className="absolute bottom-0 inset-x-0 pb-10 flex flex-col items-center gap-3">
            <span className="px-3 py-1 rounded-xl bg-black/35 text-sm font-bold">{formatDuration(elapsed)} / 3:00</span>
            {sound && !isRecording && <p className="text-[11px] text-white/80">Микрофон выключен: во время съёмки играет выбранный звук</p>}
            {error && <p className="text-sm text-red-300">{error}</p>}
            <div className="flex items-center justify-between w-full px-8 mt-1">
              <div className="w-[52px] h-[52px] flex items-center justify-center">
                {!isRecording && (
                  <button onClick={() => fileInput.current?.click()} title="Из файла"
                    className="w-[52px] h-[52px] rounded-2xl border-[1.5px] border-white flex items-center justify-center">
                    <Icon name="gallery" size={24} />
                  </button>
                )}
              </div>
              <RecordButton recording={isRecording} progress={elapsed / SHORT_MAX_MS}
                onClick={() => (isRecording ? finishRecording() : startRecording())} />
              <div className="w-[52px] h-[52px] flex items-center justify-center">
                {isRecording && (
                  <button onClick={togglePause} title={isPaused ? "Продолжить" : "Пауза"}
                    className="w-[52px] h-[52px] rounded-full bg-black/40 flex items-center justify-center">
                    <Icon name={isPaused ? "play" : "pause"} size={24} solid={isPaused} />
                  </button>
                )}
              </div>
            </div>
            <input ref={fileInput} type="file" accept="video/*" className="hidden"
              onChange={(e) => { const f = e.target.files?.[0]; if (f) importFile(f); e.target.value = ""; }} />
          </div>
        </>
      ) : (
        <>
          <video src={blobUrl} className="absolute inset-0 w-full h-full object-cover" loop playsInline autoPlay muted={!!sound} />
          {sound && <audio src={sound.audioUrl} autoPlay loop />}
          <div className="absolute inset-x-0 top-0 h-36 bg-gradient-to-b from-black/50 to-transparent pointer-events-none" />
          <div className="absolute inset-x-0 bottom-0 h-80 bg-gradient-to-t from-black/75 to-transparent pointer-events-none" />

          <div className="absolute top-0 inset-x-0 flex items-center justify-between px-3 pt-4">
            <RoundBtn icon="back" title="Переснять" onClick={retake} disabled={uploading} />
            <SoundChooser sound={sound} enabled={!uploading} onChoose={() => setShowPicker(true)} onClear={() => setSound(null)} />
            <div className="w-11" />
          </div>

          <div className="absolute inset-x-0 bottom-0 p-4 space-y-3">
            <textarea
              value={caption}
              onChange={(e) => setCaption(e.target.value.slice(0, 300))}
              disabled={uploading}
              rows={2}
              placeholder="Опишите видео… можно с #хештегами"
              className="w-full bg-black/45 rounded-2xl px-4 py-3 text-sm outline-none placeholder-white/60 resize-none"
            />
            {error && <p className="text-sm text-red-300">{error}</p>}
            {uploading ? (
              <div className="space-y-2">
                <div className="h-1.5 rounded-full bg-white/20 overflow-hidden"><div className="h-full transition-all" style={{ width: `${progress * 100}%`, background: ACCENT }} /></div>
                <p className="text-sm">Загрузка… {Math.round(progress * 100)}%</p>
              </div>
            ) : (
              <button onClick={publish} className="w-full h-[52px] rounded-full font-black text-base text-white active:scale-[0.98] transition-transform" style={{ background: ACCENT }}>
                Опубликовать
              </button>
            )}
          </div>
        </>
      )}

      {showPicker && (
        <div className="absolute inset-0 z-50">
          <SoundLibrary
            me={me}
            pickMode
            onPick={(s) => { setSound(s); setShowPicker(false); }}
            onOpenSound={() => {}}
            onBack={() => setShowPicker(false)}
          />
        </div>
      )}
    </div>
  );
};
