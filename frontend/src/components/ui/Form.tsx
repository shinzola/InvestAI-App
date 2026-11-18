import { clsx } from 'clsx';
import { X } from 'lucide-react';
import { forwardRef, type ReactNode } from 'react';

export function Field({ label, error, children }: { label: string; error?: string; children: ReactNode }) {
  return (
    <div>
      <label className="label">{label}</label>
      {children}
      {error && <p className="text-xs text-danger mt-1">{error}</p>}
    </div>
  );
}

/**
 * forwardRef é obrigatório: o register() do react-hook-form devolve um `ref`
 * e, sem repasse ao elemento nativo, o RHF não lê o valor e valida como vazio.
 */
export const Input = forwardRef<HTMLInputElement, React.InputHTMLAttributes<HTMLInputElement>>(
  function Input({ className, ...props }, ref) {
    return <input ref={ref} className={clsx('input', className)} {...props} />;
  },
);

export const Select = forwardRef<HTMLSelectElement, React.SelectHTMLAttributes<HTMLSelectElement>>(
  function Select({ className, ...props }, ref) {
    return <select ref={ref} className={clsx('input', className)} {...props} />;
  },
);

export function Modal({ open, title, onClose, children }: {
  open: boolean;
  title: string;
  onClose: () => void;
  children: ReactNode;
}) {
  if (!open) return null;
  return (
    <div
      className="fixed inset-0 z-40 flex items-center justify-center bg-black/50 p-4"
      role="dialog"
      aria-modal="true"
      aria-label={title}
      onClick={onClose}
    >
      <div
        className="card w-full max-w-lg max-h-[90vh] overflow-y-auto"
        onClick={(e) => e.stopPropagation()}
      >
        <header className="flex items-center justify-between mb-4">
          <h3 className="font-semibold">{title}</h3>
          <button className="btn-ghost" onClick={onClose} aria-label="Fechar">
            <X size={16} aria-hidden />
          </button>
        </header>
        {children}
      </div>
    </div>
  );
}

export function Money({ value, className }: { value: number; className?: string }) {
  const formatted = value.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  return (
    <span className={clsx(value < 0 ? 'text-danger' : value > 0 ? 'text-success' : '', className)}>
      {formatted}
    </span>
  );
}
