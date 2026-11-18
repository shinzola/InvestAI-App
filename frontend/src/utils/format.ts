/**
 * Formata datas vindas da API.
 *
 * Datas puras ("2026-11-09") são interpretadas por `new Date()` como meia-noite
 * UTC; exibidas em America/Sao_Paulo (UTC-3) isso recua um dia. Por isso datas
 * sem hora são montadas como horário local.
 */
export function formatDate(value: string | null | undefined): string {
  if (!value) return '—';
  const dateOnly = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  const date = dateOnly
    ? new Date(Number(dateOnly[1]), Number(dateOnly[2]) - 1, Number(dateOnly[3]))
    : new Date(value);
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleDateString('pt-BR');
}

/** Data de hoje no formato aceito por <input type="date"> (local, não UTC). */
export function todayInput(): string {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
}
