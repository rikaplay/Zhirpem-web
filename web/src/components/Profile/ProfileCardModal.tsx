"use client";

import React, { useEffect, useRef, useState } from "react";
import { QRCodeSVG } from "qrcode.react";
import { db } from "@/lib/firebase";
import app from "@/lib/firebase";
import { collection, doc, getCountFromServer, onSnapshot, query, where } from "firebase/firestore";
import { Icon } from "@/components/ui/Icon";
import { useUserAvatar } from "@/lib/avatars";
import { compactCount } from "@/lib/shorts";
import { useLongPress } from "@/lib/useLongPress";

interface Props {
  username: string;
  fallbackName?: string;
  fallbackAvatar?: string | null;
  fallbackStatus?: string | null;
  onClose: () => void;
  onOpenProfile: () => void;
}

/** Ссылка, которая зашита в QR: страница /u/ник сама откроет приложение, а на компьютере — профиль в вебе. */
const profileLink = (username: string) =>
  `${typeof window !== "undefined" ? window.location.origin : ""}/u/${encodeURIComponent(username)}`;

/**
 * Виртуальная карта профиля (как кошелёк в Telegram): фон — баннер или его цвет, текстура пластика,
 * чип, аватарка, ник, статус, «в сети», подписчики. Карта наклоняется за пальцем, по удержанию
 * открывается QR на профиль, а удержание на логотипе увеличивает его.
 */
export const ProfileCardModal: React.FC<Props> = ({
  username, fallbackName, fallbackAvatar, fallbackStatus, onClose, onOpenProfile
}) => {
  const [user, setUser] = useState<any>({});
  const [followers, setFollowers] = useState(0);
  const [online, setOnline] = useState(false);
  const [showQr, setShowQr] = useState(false);
  const [lens, setLens] = useState(false);
  const [copied, setCopied] = useState(false);
  const [tilt, setTilt] = useState({ x: 0, y: 0, mx: 50, my: 30, touching: false });
  const [pressed, setPressed] = useState(false);
  const cardRef = useRef<HTMLDivElement>(null);

  const avatar = useUserAvatar(username, fallbackAvatar);

  useEffect(() => {
    const unsub = onSnapshot(doc(db, "users", username), (d) => setUser(d.exists() ? d.data() : {}));
    getCountFromServer(query(collection(db, "follows"), where("following", "==", username)))
      .then((s) => setFollowers(s.data().count)).catch(() => {});

    // «В сети» берём из Realtime Database, если её адрес указан в переменных окружения
    let off: (() => void) | undefined;
    const dbUrl = process.env.NEXT_PUBLIC_FIREBASE_DATABASE_URL;
    if (dbUrl) {
      import("firebase/database").then(({ getDatabase, ref, onValue }) => {
        const rtdb = getDatabase(app, dbUrl);
        off = onValue(ref(rtdb, `status/${username}/state`), (s) => setOnline(s.val() === "online"));
      }).catch(() => {});
    }
    return () => { unsub(); if (off) off(); };
  }, [username]);

  const longPress = useLongPress(() => setShowQr(true), 600);

  const onMove = (e: React.PointerEvent) => {
    const el = cardRef.current;
    if (!el) return;
    const r = el.getBoundingClientRect();
    const nx = ((e.clientX - r.left) / r.width - 0.5) * 2;
    const ny = ((e.clientY - r.top) / r.height - 0.5) * 2;
    setTilt({ x: -ny * 14, y: nx * 14, mx: ((e.clientX - r.left) / r.width) * 100, my: ((e.clientY - r.top) / r.height) * 100, touching: true });
    longPress.handlers.onPointerMove(e);
  };
  const reset = () => setTilt({ x: 0, y: 0, mx: 50, my: 30, touching: false });

  const bannerColor: string = user.bannerColor || "#4a6cf7";
  const name: string = user.name || fallbackName || username;
  const status: string = user.status || fallbackStatus || "";
  const link = profileLink(username);

  const bg = user.bannerUrl
    ? `url(${user.bannerUrl}) center/cover`
    : `linear-gradient(135deg, ${bannerColor}, ${bannerColor}cc 55%, #00000088)`;

  const copy = async () => {
    try { await navigator.clipboard.writeText(link); setCopied(true); setTimeout(() => setCopied(false), 1500); } catch { /* ignore */ }
  };

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center" onClick={() => (showQr ? setShowQr(false) : onClose())}>
      <div className="absolute inset-0 bg-black/60 backdrop-blur-md" />

      {/* ============ КАРТА ============ */}
      <div
        className="relative transition-transform duration-500 ease-out"
        style={{
          width: "min(92vw, 400px)",
          aspectRatio: "1.586",
          transform: showQr ? "translateY(-30vh) scale(0.9)" : undefined,
          perspective: 900
        }}
        onClick={(e) => e.stopPropagation()}
      >
        <div
          ref={cardRef}
          {...longPress.handlers}
          onPointerMove={onMove}
          onPointerDown={(e) => { setPressed(true); longPress.handlers.onPointerDown(e); onMove(e); }}
          onPointerUp={() => { setPressed(false); reset(); longPress.handlers.onPointerUp(); }}
          onPointerLeave={() => { setPressed(false); reset(); longPress.handlers.onPointerLeave(); }}
          className={`absolute inset-0 rounded-[22px] overflow-hidden shadow-[0_30px_60px_rgba(0,0,0,.5)] border border-white/40 select-none touch-none ${tilt.touching ? "" : "card-sway"}`}
          style={{
            background: bg,
            transform: `rotateX(${tilt.x}deg) rotateY(${tilt.y}deg) scale(${pressed ? 0.975 : 1})`,
            transition: tilt.touching ? "transform .05s linear" : "transform .5s cubic-bezier(.2,.8,.2,1)"
          }}
        >
          {/* текстура пластика: перелив и зерно */}
          <div className="absolute inset-0 bg-gradient-to-br from-white/25 via-transparent to-black/30 pointer-events-none" />
          <div className="absolute inset-0 pointer-events-none opacity-60 mix-blend-overlay"
            style={{ backgroundImage: "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='160' height='160'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='.9' numOctaves='2' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='160' height='160' filter='url(%23n)' opacity='.55'/%3E%3C/svg%3E\")" }} />
          <div className="absolute inset-0 pointer-events-none opacity-25"
            style={{ backgroundImage: "repeating-linear-gradient(115deg, rgba(255,255,255,.6) 0 1px, transparent 1px 7px)" }} />
          {/* затемнение под текстом внизу слева */}
          <div className="absolute inset-0 pointer-events-none" style={{ background: "linear-gradient(to top right, rgba(0,0,0,.55), transparent 55%)" }} />
          {/* блик, который едет за пальцем */}
          <div className="absolute inset-0 pointer-events-none"
            style={{ background: `radial-gradient(circle at ${tilt.mx}% ${tilt.my}%, rgba(255,255,255,${tilt.touching ? 0.3 : 0.16}), transparent 60%)` }} />

          {/* аватарка */}
          <div className="absolute left-[4.5%] top-[8%] w-[14%] aspect-square rounded-full overflow-hidden border-2 border-white/70 bg-white/20 flex items-center justify-center text-white font-black text-xl">
            {avatar ? <img src={avatar} alt="" className="w-full h-full object-cover" draggable={false} /> : name.charAt(0).toUpperCase()}
          </div>

          {/* кнопка «Профиль» */}
          <button
            onPointerDown={(e) => e.stopPropagation()}
            onClick={() => { onClose(); onOpenProfile(); }}
            className="absolute right-[4.5%] top-[9%] px-4 py-2 rounded-full bg-white/25 border border-white/45 text-white text-[13px] font-semibold backdrop-blur-sm active:scale-95 transition-transform"
          >
            Профиль
          </button>

          {/* чип */}
          <div className="absolute left-[5%] top-[37%] w-[11.5%] aspect-[46/35] rounded-[7px] border border-[#8a6a1e]/70 overflow-hidden"
            style={{ background: "linear-gradient(135deg,#f6e3a1,#d4a94a 40%,#f0d688 70%,#b88a2e)" }}>
            <div className="absolute inset-0" style={{
              backgroundImage: "linear-gradient(#7a5a14a6 1px,transparent 1px), linear-gradient(90deg,#7a5a14a6 1px,transparent 1px)",
              backgroundSize: "100% 33%, 34% 100%", backgroundPosition: "0 0, 33% 0", opacity: 0.7
            }} />
            <div className="absolute inset-0 bg-gradient-to-br from-white/45 to-transparent" />
          </div>

          {/* лого вместо алмаза: чёрно-белое, при удержании увеличивается */}
          <div
            className="absolute"
            style={{ left: "76%", top: "43%", width: "20%", aspectRatio: "1", transform: "translate(-50%,-50%)" }}
            onPointerDown={(e) => { e.stopPropagation(); setLens(true); }}
            onPointerUp={() => setLens(false)}
            onPointerLeave={() => setLens(false)}
            onPointerCancel={() => setLens(false)}
          >
            <div
              className="absolute inset-0 rounded-full transition-all duration-300"
              style={{
                transform: lens ? "scale(2.1)" : "scale(1)",
                background: lens ? "rgba(255,255,255,.12)" : "transparent",
                backdropFilter: lens ? "blur(3px) saturate(1.4)" : undefined,
                WebkitBackdropFilter: lens ? "blur(3px) saturate(1.4)" : undefined,
                boxShadow: lens ? "inset 0 0 0 1.5px rgba(255,255,255,.55), 0 0 30px rgba(255,255,255,.25)" : "none"
              }}
            />
            <img src="/logo-white.png" alt="" draggable={false}
              className="absolute inset-0 w-full h-full object-contain transition-transform duration-300"
              style={{ transform: lens ? "scale(1.9)" : "scale(1.3)", opacity: lens ? 1 : 0.8 }} />
          </div>

          {/* низ слева: статус, в сети, ник */}
          <div className="absolute left-[5%] bottom-[7%] right-[34%] text-white" style={{ textShadow: "0 2px 4px rgba(0,0,0,.45)" }}>
            {status && <p className="text-[12px] truncate">{status}</p>}
            <div className="flex items-center gap-1.5 mt-0.5">
              <span className="w-2 h-2 rounded-full" style={{ background: online ? "#4caf50" : "rgba(255,255,255,.45)" }} />
              <span className="text-[11px] opacity-90">{online ? "в сети" : "не в сети"}</span>
            </div>
            <p className="mt-1.5 font-mono font-semibold text-[19px] tracking-[2px] uppercase truncate"
              style={{ textShadow: "0 1.2px 0 rgba(255,255,255,.38), -1px -1.5px 2px rgba(0,0,0,.5)" }}>
              {username}
            </p>
          </div>

          {/* низ справа: подписчики */}
          <div className="absolute right-[4.5%] bottom-[7%] px-3 py-1.5 rounded-[14px] bg-black/25 border border-white/25 text-center text-white">
            <div className="text-[15px] font-bold leading-tight">{compactCount(followers)}</div>
            <div className="text-[9px] opacity-75">подписчиков</div>
          </div>
        </div>
      </div>

      {/* ============ ПАНЕЛЬ С QR ============ */}
      <div
        className={`fixed inset-x-0 bottom-0 flex justify-center transition-transform duration-500 ease-out ${showQr ? "translate-y-0" : "translate-y-full"}`}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="w-full max-w-[500px] rounded-t-[34px] px-6 pt-5 pb-8 flex flex-col items-center text-white"
          style={{ background: `linear-gradient(180deg, ${bannerColor}, #0d0d0d)` }}>
          <button onClick={() => setShowQr(false)} className="self-start w-10 h-10 rounded-full bg-white/20 flex items-center justify-center mb-2">
            <Icon name="close" size={18} />
          </button>
          <div className="bg-white rounded-[28px] p-5">
            <QRCodeSVG
              value={link}
              size={240}
              level="H"
              imageSettings={{ src: "/logo-bw.png", height: 54, width: 54, excavate: true }}
            />
          </div>
          <button onClick={copy} className="mt-4 px-6 py-2.5 rounded-full bg-white/90 text-zinc-900 font-bold text-sm">
            {copied ? "Скопировано" : "Копировать ссылку"}
          </button>
          <p className="mt-3 text-sm text-center opacity-90">Отсканируйте код камерой — откроется профиль @{username} в Жирпем</p>
        </div>
      </div>
    </div>
  );
};
