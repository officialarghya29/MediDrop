import { Link } from 'react-router-dom';

export function LogoMark({
  size = 36,
  variant = 'mark',
  label = 'MediDrop',
}: {
  size?: number;
  variant?: 'mark' | 'emblem';
  label?: string;
}) {
  return (
    <img
      src={variant === 'emblem' ? '/brand/emblem.png' : '/brand/mark.png'}
      alt={label}
      style={{ height: size, width: 'auto' }}
      className="shrink-0 object-contain"
      draggable={false}
    />
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
    <Link to={to} className={`inline-flex items-center gap-2.5 ${className}`} aria-label={compact ? 'MediDrop home' : undefined}>
      <LogoMark label="" />
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
