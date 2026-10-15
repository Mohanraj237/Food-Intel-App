import type { SVGProps } from 'react';

/**
 * The icon set: inline SVG on a 24×24 grid, 1.75px rounded strokes, `currentColor`.
 *
 * Replaces the Unicode symbol glyphs (⌂ ⌕ ▣ ⚙ ◔ ☖ ⊕ ▤) the shell and tiles used
 * before — those came from different symbol blocks with different optical weights and read as
 * placeholder characters rather than an icon family. Around thirty icons cover the whole
 * product; no icon library, no licence question, no bundle cost beyond these paths.
 *
 * Decorative by default (`aria-hidden`). Pass `label` for an icon that is the only content of
 * a control.
 */
export type IconName =
  | 'home'
  | 'search'
  | 'scan'
  | 'camera'
  | 'user'
  | 'today'
  | 'family'
  | 'plus'
  | 'food'
  | 'heart'
  | 'heart-filled'
  | 'chevron-right'
  | 'chevron-left'
  | 'chevron-down'
  | 'arrow-left'
  | 'arrow-right'
  | 'water'
  | 'steps'
  | 'sleep'
  | 'meal'
  | 'mood'
  | 'body'
  | 'readings'
  | 'habit'
  | 'compare'
  | 'bell'
  | 'settings'
  | 'shield'
  | 'info'
  | 'close'
  | 'check'
  | 'sparkles'
  | 'clock'
  | 'share'
  | 'history'
  | 'bookmark'
  | 'flag'
  | 'list'
  | 'sun'
  | 'moon'
  | 'leaf'
  | 'alert'
  | 'lock'
  | 'download'
  | 'trash'
  | 'edit'
  | 'grid'
  | 'goal'
  | 'insight'
  | 'chart';

const PATHS: Record<IconName, JSX.Element> = {
  home: (
    <>
      <path d="M3 10.5 12 3l9 7.5" />
      <path d="M5 9.5V20a1 1 0 0 0 1 1h4v-6h4v6h4a1 1 0 0 0 1-1V9.5" />
    </>
  ),
  search: (
    <>
      <circle cx="11" cy="11" r="7" />
      <path d="m20 20-3.5-3.5" />
    </>
  ),
  scan: (
    <>
      <path d="M4 8V5.5A1.5 1.5 0 0 1 5.5 4H8M16 4h2.5A1.5 1.5 0 0 1 20 5.5V8M20 16v2.5a1.5 1.5 0 0 1-1.5 1.5H16M8 20H5.5A1.5 1.5 0 0 1 4 18.5V16" />
      <path d="M8 9v6M11 9v6M14 9v6M17 9v6" />
    </>
  ),
  camera: (
    <>
      <path d="M4 8.5A1.5 1.5 0 0 1 5.5 7H8l1.2-2h5.6L16 7h2.5A1.5 1.5 0 0 1 20 8.5v9a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 17.5z" />
      <circle cx="12" cy="13" r="3.5" />
    </>
  ),
  user: (
    <>
      <circle cx="12" cy="8" r="4" />
      <path d="M4.5 20.5c1.2-3.6 4-5.5 7.5-5.5s6.3 1.9 7.5 5.5" />
    </>
  ),
  today: (
    <>
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M5.3 18.7l1.4-1.4M17.3 6.7l1.4-1.4" />
    </>
  ),
  family: (
    <>
      <circle cx="9" cy="8" r="3.2" />
      <circle cx="17" cy="9.5" r="2.5" />
      <path d="M3 19.5c.8-3.3 3-5 6-5s5.2 1.7 6 5" />
      <path d="M15.5 14.8c2.6 0 4.6 1.4 5.3 4.2" />
    </>
  ),
  plus: <path d="M12 5v14M5 12h14" />,
  food: (
    <>
      <path d="M3.5 13h17c0 4-3.4 7-8.5 7s-8.5-3-8.5-7Z" />
      <path d="M8 13c0-3 1.8-5 4-5s4 2 4 5" />
      <path d="M12 8V5.5M12 5.5c1.5-.3 2.5-1.3 2.5-2.5" />
    </>
  ),
  heart: (
    <path d="M12 20.5s-7.5-4.6-7.5-10.4A4.1 4.1 0 0 1 8.6 6c1.5 0 2.7.8 3.4 2 .7-1.2 1.9-2 3.4-2a4.1 4.1 0 0 1 4.1 4.1c0 5.8-7.5 10.4-7.5 10.4Z" />
  ),
  'heart-filled': (
    <path
      fill="currentColor"
      d="M12 20.5s-7.5-4.6-7.5-10.4A4.1 4.1 0 0 1 8.6 6c1.5 0 2.7.8 3.4 2 .7-1.2 1.9-2 3.4-2a4.1 4.1 0 0 1 4.1 4.1c0 5.8-7.5 10.4-7.5 10.4Z"
    />
  ),
  'chevron-right': <path d="m9 6 6 6-6 6" />,
  'chevron-left': <path d="m15 6-6 6 6 6" />,
  'chevron-down': <path d="m6 9 6 6 6-6" />,
  'arrow-left': <path d="M19 12H5M11 6l-6 6 6 6" />,
  'arrow-right': <path d="M5 12h14M13 6l6 6-6 6" />,
  water: <path d="M12 3.5s6 6.4 6 10.6a6 6 0 0 1-12 0c0-4.2 6-10.6 6-10.6Z" />,
  steps: (
    <>
      <path d="M7.5 3.5c1.9 0 3 2 3 4.5s-.6 4-1.5 5c-.9 1-2.9.8-3.6-.6-.9-1.8-.6-8.9 2.1-8.9Z" />
      <path d="M6.2 15.5c1.4-.2 2.6.2 3 1.5.3 1.1-.4 2.3-1.6 2.5-1.3.2-2.4-.7-2.5-1.9-.1-1 .2-1.9 1.1-2.1Z" />
      <path d="M16.5 8c-1.9 0-3 2-3 4.5s.6 4 1.5 5c.9 1 2.9.8 3.6-.6.9-1.8.6-8.9-2.1-8.9Z" />
    </>
  ),
  sleep: <path d="M20 14.5A8.5 8.5 0 0 1 9.5 4a8.5 8.5 0 1 0 10.5 10.5Z" />,
  meal: (
    <>
      <path d="M6 3v7.5a2.5 2.5 0 0 0 5 0V3M8.5 3v18" />
      <path d="M17.5 3c-2 1.5-3 4-3 7v2h3v9" />
    </>
  ),
  mood: (
    <>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M8.5 14.5c.9 1.3 2.1 2 3.5 2s2.6-.7 3.5-2" />
      <path d="M9 9.5h.01M15 9.5h.01" strokeWidth="2.5" />
    </>
  ),
  body: (
    <>
      <path d="M5.5 4h13A1.5 1.5 0 0 1 20 5.5v13a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 18.5v-13A1.5 1.5 0 0 1 5.5 4Z" />
      <path d="M8 11.5c0-2 1.8-3.5 4-3.5s4 1.5 4 3.5M12 8v3.5" />
    </>
  ),
  readings: <path d="M3 12h4l2.5-6 3 12 2.5-8 1.5 2H21" />,
  habit: (
    <>
      <circle cx="12" cy="12" r="8.5" />
      <path d="m8.5 12.5 2.3 2.3 4.7-5" />
    </>
  ),
  compare: <path d="M4 8h14l-3-3M20 16H6l3 3" />,
  bell: (
    <>
      <path d="M6 16.5V11a6 6 0 0 1 12 0v5.5l1.5 2h-15z" />
      <path d="M10 20.5a2 2 0 0 0 4 0" />
    </>
  ),
  settings: (
    <>
      <path d="M4 7h10M18 7h2M4 17h4M12 17h8" />
      <circle cx="16" cy="7" r="2" />
      <circle cx="10" cy="17" r="2" />
    </>
  ),
  shield: (
    <>
      <path d="M12 3 5 6v5.5c0 4.3 2.9 7.8 7 9.5 4.1-1.7 7-5.2 7-9.5V6z" />
      <path d="m9.5 12 1.8 1.8 3.4-3.6" />
    </>
  ),
  info: (
    <>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12 11v5M12 8h.01" strokeWidth="2.5" />
    </>
  ),
  close: <path d="M6 6l12 12M18 6 6 18" />,
  check: <path d="m5 12.5 4.5 4.5L19 7.5" />,
  sparkles: (
    <>
      <path d="M12 3.5 13.8 9l5.7 1.8-5.7 1.8L12 18.2l-1.8-5.6L4.5 10.8 10.2 9z" />
      <path d="M19 16.5l.7 2 2 .7-2 .7-.7 2-.7-2-2-.7 2-.7z" />
    </>
  ),
  clock: (
    <>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12 7.5V12l3 2" />
    </>
  ),
  share: (
    <>
      <path d="M12 3.5v11M8 7.5l4-4 4 4" />
      <path d="M5 12.5v6A1.5 1.5 0 0 0 6.5 20h11a1.5 1.5 0 0 0 1.5-1.5v-6" />
    </>
  ),
  history: (
    <>
      <path d="M4 12a8 8 0 1 0 2.3-5.7" />
      <path d="M4 4v4.5h4.5M12 8v4.5l3 1.5" />
    </>
  ),
  bookmark: <path d="M6.5 4h11v16.5L12 16.5l-5.5 4z" />,
  flag: <path d="M6 20.5V4.5c4-2 8 2 12 0v9c-4 2-8-2-12 0" />,
  list: <path d="M8 6h12M8 12h12M8 18h12M4 6h.01M4 12h.01M4 18h.01" strokeWidth="2.5" />,
  sun: (
    <>
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M5.3 18.7l1.4-1.4M17.3 6.7l1.4-1.4" />
    </>
  ),
  moon: <path d="M20 14.5A8.5 8.5 0 0 1 9.5 4a8.5 8.5 0 1 0 10.5 10.5Z" />,
  leaf: (
    <>
      <path d="M5 19c0-8 5-13 14-13-1 9-5 13-13 13z" />
      <path d="M5 19c3-4 6-7 10-9" />
    </>
  ),
  alert: (
    <>
      <path d="M12 4 3 20h18z" />
      <path d="M12 10v4M12 17h.01" strokeWidth="2.5" />
    </>
  ),
  lock: (
    <>
      <rect x="5" y="11" width="14" height="10" rx="2" />
      <path d="M8 11V8a4 4 0 0 1 8 0v3" />
    </>
  ),
  download: <path d="M12 4v11M7.5 10.5 12 15l4.5-4.5M5 19h14" />,
  trash: <path d="M5 7h14M9.5 7V4.5h5V7M7 7l.8 12.5h8.4L17 7M10 11v5M14 11v5" />,
  edit: <path d="M4 20h4l10.5-10.5-4-4L4 16zM13 7l4 4" />,
  grid: (
    <>
      <rect x="4" y="4" width="6.5" height="6.5" rx="1.5" />
      <rect x="13.5" y="4" width="6.5" height="6.5" rx="1.5" />
      <rect x="4" y="13.5" width="6.5" height="6.5" rx="1.5" />
      <rect x="13.5" y="13.5" width="6.5" height="6.5" rx="1.5" />
    </>
  ),
  goal: (
    <>
      <circle cx="12" cy="12" r="8.5" />
      <circle cx="12" cy="12" r="4.5" />
      <circle cx="12" cy="12" r="1" fill="currentColor" />
    </>
  ),
  insight: (
    <>
      <path d="M9 18h6M10 21h4" />
      <path d="M12 3a6.5 6.5 0 0 0-4 11.6c.7.6 1 1.4 1 2.4h6c0-1 .3-1.8 1-2.4A6.5 6.5 0 0 0 12 3Z" />
    </>
  ),
  chart: <path d="M4 20h16M6 16v-5M11 16V8M16 16v-3M21 16V5" />,
};

interface IconProps extends Omit<SVGProps<SVGSVGElement>, 'name'> {
  name: IconName;
  /** Pixel size; the grid scales with it. */
  size?: number;
  /** Accessible name when the icon is meaningful on its own. */
  label?: string;
}

export function Icon({ name, size = 22, label, className, ...rest }: IconProps) {
  return (
    <svg
      viewBox="0 0 24 24"
      width={size}
      height={size}
      fill="none"
      stroke="currentColor"
      strokeWidth={1.75}
      strokeLinecap="round"
      strokeLinejoin="round"
      className={['icon', className ?? ''].filter(Boolean).join(' ')}
      aria-hidden={label ? undefined : true}
      role={label ? 'img' : undefined}
      aria-label={label}
      focusable="false"
      {...rest}
    >
      {label && <title>{label}</title>}
      {PATHS[name]}
    </svg>
  );
}
