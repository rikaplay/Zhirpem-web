"use client";

import React from "react";
import { Icon } from "@/components/ui/Icon";
import { IconName } from "@/components/ui/iconData";

interface BottomNavProps {
  activeTab: string;
  onTabChange: (tab: string) => void;
  /** Панель плавно уезжает вниз (чат, настройки, съёмка). На вкладке Shorts она не скрывается. */
  hidden?: boolean;
}

const ITEMS: { id: string; icon: IconName; label: string }[] = [
  { id: "home", icon: "home", label: "Главная" },
  { id: "search", icon: "search", label: "Поиск" },
  { id: "shorts", icon: "shorts", label: "Shorts" },
  { id: "notifications", icon: "bell", label: "Уведомления" },
  { id: "messages", icon: "chat", label: "Сообщения" }
];

/**
 * Нижняя панель со стеклом (размытие фона) и «жидким» индикатором, который перетекает
 * между вкладками. Те же иконки, что в приложении.
 */
export const BottomNav: React.FC<BottomNavProps> = ({ activeTab, onTabChange, hidden = false }) => {
  const activeIndex = ITEMS.findIndex((i) => i.id === activeTab);
  const segment = 100 / ITEMS.length;

  return (
    <div
      className="fixed bottom-6 left-1/2 w-[92%] max-w-[420px] h-[68px] z-50 transition-all duration-500 ease-[cubic-bezier(0.34,1.2,0.64,1)]"
      style={{
        transform: `translate(-50%, ${hidden ? "170%" : "0"})`,
        opacity: hidden ? 0 : 1,
        pointerEvents: hidden ? "none" : "auto"
      }}
    >
      <div className="relative w-full h-full glass-bar rounded-full shadow-[0_20px_50px_rgba(0,0,0,0.25)] flex items-center">
        {/* Жидкий индикатор (линза) */}
        <div
          className="absolute top-1/2 h-[52px] rounded-full bg-primary/20 border border-primary/30 backdrop-blur-md transition-all duration-500 ease-[cubic-bezier(0.34,1.56,0.64,1)]"
          style={{
            width: `calc(${segment}% - 10px)`,
            left: `calc(${Math.max(activeIndex, 0) * segment}% + 5px)`,
            transform: "translateY(-50%)",
            opacity: activeIndex === -1 ? 0 : 1
          }}
        />
        {ITEMS.map((item) => {
          const active = item.id === activeTab;
          return (
            <button
              key={item.id}
              onClick={() => onTabChange(item.id)}
              aria-label={item.label}
              className={`relative z-10 flex-1 h-full flex items-center justify-center transition-all duration-300 active:scale-75 ${
                active ? "text-primary dark:text-primary-dark scale-110" : "text-zinc-500 dark:text-zinc-400 hover:text-primary"
              }`}
            >
              <Icon name={item.icon} size={26} bold={active} />
            </button>
          );
        })}
      </div>
    </div>
  );
};
