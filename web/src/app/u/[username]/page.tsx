"use client";

import { useEffect } from "react";
import { useParams } from "next/navigation";

// Страница для QR-кода профиля: https://<сайт>/u/<ник>
// На Android пытается открыть приложение, на остальных устройствах открывает профиль в вебе.
const PACKAGE = "com.RIKAPLAY.zhirpem_app";

export default function OpenProfileInApp() {
  const params = useParams<{ username: string }>();
  const username = decodeURIComponent(String(params?.username ?? ""));

  useEffect(() => {
    if (!username) return;
    if (/android/i.test(navigator.userAgent)) {
      const fallback = encodeURIComponent(`${window.location.origin}/?u=${encodeURIComponent(username)}`);
      window.location.href =
        `intent://user/${encodeURIComponent(username)}#Intent;scheme=zhirpem;package=${PACKAGE};` +
        `S.browser_fallback_url=${fallback};end`;
    } else {
      window.location.replace(`/?u=${encodeURIComponent(username)}`);
    }
  }, [username]);

  return (
    <main className="min-h-screen flex flex-col items-center justify-center gap-4 p-6 text-center">
      <h1 className="text-2xl font-bold">@{username}</h1>
      <p className="opacity-70">Открываем профиль…</p>
      <a
        href={`/?u=${encodeURIComponent(username)}`}
        className="rounded-full bg-primary px-6 py-3 font-semibold text-white"
      >
        Открыть в вебе
      </a>
    </main>
  );
}
