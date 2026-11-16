"use client";

import React, { useRef } from "react";

/**
 * Удержание пальца (или правая кнопка мыши на компьютере).
 * wasLong() нужен, чтобы обычный клик после удержания не срабатывал.
 */
export function useLongPress(onLong: () => void, ms = 550) {
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const fired = useRef(false);
  const start = useRef<{ x: number; y: number } | null>(null);

  const clear = () => {
    if (timer.current) { clearTimeout(timer.current); timer.current = null; }
  };

  return {
    handlers: {
      onPointerDown: (e: React.PointerEvent) => {
        fired.current = false;
        start.current = { x: e.clientX, y: e.clientY };
        clear();
        timer.current = setTimeout(() => {
          fired.current = true;
          if (typeof navigator !== "undefined" && navigator.vibrate) navigator.vibrate(20);
          onLong();
        }, ms);
      },
      onPointerMove: (e: React.PointerEvent) => {
        if (start.current && Math.hypot(e.clientX - start.current.x, e.clientY - start.current.y) > 10) clear();
      },
      onPointerUp: clear,
      onPointerLeave: clear,
      onPointerCancel: clear,
      onContextMenu: (e: React.MouseEvent) => {
        e.preventDefault();
        fired.current = true;
        clear();
        onLong();
      }
    },
    wasLong: () => fired.current
  };
}
