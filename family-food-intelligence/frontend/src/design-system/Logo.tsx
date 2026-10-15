import './Logo.css';

/**
 * The brand mark: a bowl with a leaf rising from it, on a rounded terracotta tile.
 * Food first, freshness second — nothing clinical. `public/favicon.svg` is the same mark.
 */
export function LogoMark({ size = 28 }: { size?: number }) {
  return (
    <svg
      className="logo-mark"
      width={size}
      height={size}
      viewBox="0 0 32 32"
      aria-hidden="true"
      focusable="false"
    >
      <rect width="32" height="32" rx="9" fill="var(--brand)" />
      <path
        d="M7.5 17.5h17c0 4.4-3.6 7.5-8.5 7.5s-8.5-3.1-8.5-7.5Z"
        fill="var(--on-brand)"
      />
      <path
        d="M16 15.5c-.2-3.6 1.9-6.6 6.2-7.6-.2 3.9-2.2 6.9-6.2 7.6Z"
        fill="var(--on-brand)"
        opacity="0.92"
      />
      <path
        d="M16 15.5c-1.1-2.2-3-3.6-5.4-4.1"
        stroke="var(--on-brand)"
        strokeWidth="1.8"
        strokeLinecap="round"
        fill="none"
        opacity="0.92"
      />
    </svg>
  );
}
