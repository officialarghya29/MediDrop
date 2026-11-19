import { Link } from 'react-router-dom';

export function LogoMark({ size = 36 }: { size?: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 64 64"
      role="img"
      aria-label="MediDrop logo"
      className="shrink-0"
    >
      <rect width="64" height="64" rx="16" fill="#0b8b68" />
      <path d="M32 13c7 8 12 13.8 12 21a12 12 0 0 1-24 0c0-7.2 5-13 12-21Z" fill="#e4f7ef" opacity=".32" />
      <rect x="28.5" y="19" width="7" height="22" rx="2.2" fill="#fff" />
      <rect x="21" y="26.5" width="22" height="7" rx="2.2" fill="#fff" />
      <path d="M18 48h20" stroke="#fff" strokeWidth="3" strokeLinecap="round" opacity=".72" />
    </svg>
  );
}

export default function Logo({
  to = '/',
  compact = false,
  className = '',
}: {
  to?: string;
  compact?: boolean;
  className?: string;
}) {
  return (
    <Link to={to} className={`inline-flex items-center gap-2.5 ${className}`} aria-label="MediDrop home">
      <LogoMark />
      {!compact && (
        <span className="leading-tight">
          <span className="block text-[17px] font-extrabold tracking-tight text-navy-900">MediDrop</span>
          <span className="block text-[10.5px] font-medium uppercase tracking-[0.14em] text-brand-700">
            Healthcare, delivered
          </span>
        </span>
      )}
    </Link>
  );
}
