"use client";

import React from "react";
import { ICONS, IconName } from "./iconData";

interface IconProps extends React.SVGProps<SVGSVGElement> {
  name: IconName;
  size?: number;
  /** Жирный вариант: толще линия и лёгкая заливка (как у выбранной вкладки в приложении). */
  bold?: boolean;
  /** Сплошная заливка текущим цветом (например, лайкнутое сердце). */
  solid?: boolean;
}

export const Icon: React.FC<IconProps> = ({ name, size = 24, bold = false, solid = false, className, ...rest }) => {
  const spec = ICONS[name];
  if (!spec) return null;
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={bold ? 1.9 : 1.5}
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      aria-hidden="true"
      {...rest}
    >
      {spec.paths.map((d, i) => (
        <path
          key={i}
          d={d}
          fill={solid ? "currentColor" : bold && spec.fill ? "currentColor" : "none"}
          fillOpacity={solid ? 1 : bold && spec.fill ? 0.22 : 0}
        />
      ))}
    </svg>
  );
};
