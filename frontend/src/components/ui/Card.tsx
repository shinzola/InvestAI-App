import { clsx } from 'clsx';
import { BarChart3 } from 'lucide-react';
import type { ReactNode } from 'react';

export function Card({ title, action, children, className }: {
  title?: string;
  action?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <section className={clsx('card', className)}>
      {(title || action) && (
        <header className="flex items-center justify-between mb-3">
          {title && <h2 className="text-sm font-semibold text-muted uppercase tracking-wide">{title}</h2>}
          {action}
        </header>
      )}
      {children}
    </section>
  );
}

export function StatCard({ label, value, hint, tone }: {
  label: string;
  value: string;
  hint?: string;
  tone?: 'positive' | 'negative' | 'neutral';
}) {
  const toneClass =
    tone === 'positive' ? 'text-success' : tone === 'negative' ? 'text-danger' : 'text-content';
  return (
    <div className="card">
      <p className="eyebrow">{label}</p>
      <p className={clsx('num text-[22px] font-semibold mt-2 leading-none', toneClass)}>{value}</p>
      {hint && <p className="text-[11px] text-muted mt-2">{hint}</p>}
    </div>
  );
}

export function EmptyState({ title, subtitle, action }: { title: string; subtitle?: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center py-10 text-center gap-2">
      <div className="w-12 h-12 rounded-full bg-card-2 flex items-center justify-center text-muted">
        <BarChart3 size={22} aria-hidden />
      </div>
      <p className="font-semibold">{title}</p>
      {subtitle && <p className="text-sm text-muted max-w-xs">{subtitle}</p>}
      {action}
    </div>
  );
}

export function Skeleton({ className }: { className?: string }) {
  return <div className={clsx('animate-pulse rounded-lg bg-card-2', className)} />;
}
