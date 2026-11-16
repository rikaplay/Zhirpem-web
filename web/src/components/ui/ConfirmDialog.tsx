"use client";

import React from "react";

export const ConfirmDialog: React.FC<{
  title: string;
  text: string;
  confirmLabel?: string;
  danger?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}> = ({ title, text, confirmLabel = "Удалить", danger = true, onConfirm, onCancel }) => (
  <div className="fixed inset-0 z-[120] flex items-center justify-center p-6" onClick={onCancel}>
    <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" />
    <div className="relative bg-white dark:bg-zinc-900 rounded-3xl p-6 max-w-sm w-full" onClick={(e) => e.stopPropagation()}>
      <h3 className="font-black text-lg mb-2">{title}</h3>
      <p className="text-sm text-zinc-500 mb-5">{text}</p>
      <div className="flex gap-3">
        <button onClick={onCancel} className="flex-1 py-3 rounded-2xl bg-zinc-100 dark:bg-zinc-800 font-bold">Отмена</button>
        <button
          onClick={onConfirm}
          className={`flex-1 py-3 rounded-2xl text-white font-black ${danger ? "bg-red-500" : "bg-primary"}`}
        >
          {confirmLabel}
        </button>
      </div>
    </div>
  </div>
);
