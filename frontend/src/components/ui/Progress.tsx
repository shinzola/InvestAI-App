import { clsx } from 'clsx';
import type { ReactNode } from 'react';

/** Anel de progresso em SVG (usado para nível e metas). */
export function ProgressRing({
  value, size = 96, stroke = 9, label, sublabel, tone = 'accent',
}: {
  value: number;
  size?: number;
  stroke?: number;
  label?: ReactNode;
  sublabel?: string;
  tone?: 'accent' | 'success' | 'warning';
}) {
  const clamped = Math.max(0, Math.min(100, value));
  const radius = (size - stroke) / 2;
  const circumference = 2 * Math.PI * radius;
  const offset = circumference - (clamped / 100) * circumference;
  const strokeColor =
    tone === 'success' ? 'var(--success)' : tone === 'warning' ? 'var(--warning)' : 'var(--accent)';

  return (
    <div className="relative inline-flex items-center justify-center" style={{ width: size, height: size }}>
      <svg width={size} height={size} className="-rotate-90" aria-hidden>
        <circle cx={size / 2} cy={size / 2} r={radius} fill="none" stroke="var(--card-2)" strokeWidth={stroke} />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke={strokeColor}
          strokeWidth={stroke}
          strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={offset}
          style={{ transition: 'stroke-dashoffset 700ms ease' }}
        />
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center">
        <span className="text-lg font-bold leading-none">{label ?? `${Math.round(clamped)}%`}</span>
        {sublabel && <span className="text-[10px] text-muted mt-0.5">{sublabel}</span>}
      </div>
    </div>
  );
}

/** Barra de XP com rótulo de nível. */
export function XpBar({ pct, className }: { pct: number; className?: string }) {
  return (
    <div className={clsx('xp-track', className)} role="progressbar" aria-valuenow={Math.round(pct)} aria-valuemin={0} aria-valuemax={100}>
      <div className="xp-fill" style={{ width: `${Math.max(0, Math.min(100, pct))}%` }} />
    </div>
  );
}

/** Barra simples com cor por faixa (orçamento, metas). */
export function MeterBar({ pct, tone }: { pct: number; tone?: 'success' | 'warning' | 'danger' | 'accent' }) {
  const resolved = tone ?? (pct >= 100 ? 'danger' : pct >= 80 ? 'warning' : 'success');
  const color =
    resolved === 'danger' ? 'var(--danger)'
      : resolved === 'warning' ? 'var(--warning)'
        : resolved === 'accent' ? 'var(--accent)'
          : 'var(--success)';
  return (
    <div className="h-2.5 w-full rounded-full bg-card-2 overflow-hidden">
      <div
        className="h-full rounded-full transition-all duration-700"
        style={{ width: `${Math.max(0, Math.min(100, pct))}%`, background: color }}
      />
    </div>
  );
}
