"use client";

import React from "react";
import { Icon } from "@/components/ui/Icon";
import { compactCount, resolveShort, ShortVideo, thumbUrl, useShortsStore } from "@/lib/shorts";
import { useLongPress } from "@/lib/useLongPress";

/** Превью видео в сетке 9:16 со счётчиком просмотров. Удержание вызывает onLongPress. */
export const ShortThumb: React.FC<{
  video: ShortVideo;
  onClick: () => void;
  onLongPress?: () => void;
}> = ({ video: raw, onClick, onLongPress }) => {
  useShortsStore();
  const video = resolveShort(raw);
  const lp = useLongPress(() => onLongPress && onLongPress());
  return (
    <div
      className="relative aspect-[9/16] rounded-md overflow-hidden bg-zinc-200 dark:bg-zinc-800 cursor-pointer"
      {...(onLongPress ? lp.handlers : {})}
      onClick={() => { if (onLongPress && lp.wasLong()) return; onClick(); }}
    >
      <img src={thumbUrl(video.videoUrl, 320)} alt="" className="w-full h-full object-cover" draggable={false} />
      <div className="absolute inset-x-0 bottom-0 h-11 bg-gradient-to-t from-black/60 to-transparent" />
      <div className="absolute left-1.5 bottom-1.5 flex items-center gap-1 text-white text-xs font-bold">
        <Icon name="play" size={14} solid />
        {compactCount(video.views)}
      </div>
    </div>
  );
};
