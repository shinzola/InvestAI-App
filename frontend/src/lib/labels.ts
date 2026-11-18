/** Rótulos em português para os enums da API. */

export const ACCOUNT_TYPE_LABELS: Record<string, string> = {
  CHECKING: 'Conta corrente',
  SAVINGS: 'Poupança',
  CREDIT_CARD: 'Cartão de crédito',
  DIGITAL_WALLET: 'Carteira digital',
  CASH: 'Dinheiro',
  INVESTMENT: 'Conta de investimento',
};

/** Uma conta corrente pode ter cartão de débito e crédito no mesmo cadastro. */
export const accountCardLabel = (hasCard?: boolean | null) =>
  hasCard ? 'Débito e crédito' : 'Débito';

export const ACCOUNT_TYPE_OPTIONS = Object.entries(ACCOUNT_TYPE_LABELS).map(([value, label]) => ({
  value,
  label,
}));

export const accountTypeLabel = (type?: string | null) =>
  (type && ACCOUNT_TYPE_LABELS[type]) || type || '—';

export const CATEGORY_KIND_LABELS: Record<string, string> = {
  INCOME: 'Receita',
  EXPENSE: 'Despesa',
};

/** Bancos e instituições sugeridos no cadastro de conta. */
export const BANKS = [
  'Nubank', 'Itaú', 'Bradesco', 'Banco do Brasil', 'Caixa Econômica',
  'Santander', 'Inter', 'C6 Bank', 'BTG Pactual', 'Sicredi', 'Sicoob',
  'PicPay', 'Mercado Pago', 'PagBank', 'XP Investimentos', 'Rico',
  'Clear', 'Avenue', 'Nomad', 'Wise',
];

export const INCOME_KIND_LABELS: Record<string, string> = {
  SALARY: 'Salário',
  CONTRIBUTION: 'Aporte',
  ESTIMATE: 'Estimativa',
  PAID_HOLIDAY: 'Feriado remunerado',
  EXTRA: 'Renda extra',
  DIVIDEND: 'Dividendos',
  OTHER: 'Outras entradas',
};

export const INCOME_KIND_OPTIONS = Object.entries(INCOME_KIND_LABELS).map(([value, label]) => ({
  value,
  label,
}));

export const incomeKindLabel = (kind?: string | null) =>
  (kind && INCOME_KIND_LABELS[kind]) || 'Entrada';

export const PLANNING_KIND_LABELS: Record<string, string> = {
  FIXED: 'Fixo',
  VARIABLE: 'Individual do mês',
  CREDIT_CARD: 'Cartão de crédito',
};

export const PLANNING_STATUS_LABELS: Record<string, string> = {
  PLANNED: 'Previsto',
  PAID: 'Pago',
};
