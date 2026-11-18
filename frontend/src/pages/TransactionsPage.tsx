import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { X } from 'lucide-react';
import { accountsApi, categoriesApi, planningApi, transactionsApi } from '../services/endpoints';
import { Card, EmptyState, Skeleton } from '../components/ui/Card';
import { Field, Input, Modal, Money, Select } from '../components/ui/Form';
import { useToast } from '../components/ui/Toast';
import { problemDetailMessage } from '../services/api';
import { formatDate, todayInput } from '../utils/format';
import { INCOME_KIND_LABELS, INCOME_KIND_OPTIONS, incomeKindLabel } from '../lib/labels';

const FILTERS = [
  { value: 'ALL', label: 'Todas' },
  ...INCOME_KIND_OPTIONS,
];

export function TransactionsPage() {
  const qc = useQueryClient();
  const { notify } = useToast();
  const [page, setPage] = useState(0);
  const [filter, setFilter] = useState('ALL');
  const [modalOpen, setModalOpen] = useState(false);

  const now = new Date();
  const year = now.getFullYear();
  const month = now.getMonth() + 1;

  const transactions = useQuery({
    queryKey: ['transactions', page],
    queryFn: () => transactionsApi.list(page, 50).then((r) => r.data),
  });
  const accounts = useQuery({ queryKey: ['accounts'], queryFn: () => accountsApi.list().then((r) => r.data) });
  const categories = useQuery({ queryKey: ['categories'], queryFn: () => categoriesApi.list().then((r) => r.data) });
  const planning = useQuery({
    queryKey: ['planning-summary', year, month],
    queryFn: () => planningApi.summary(year, month).then((r) => r.data),
  });

  const remove = useMutation({
    mutationFn: (id: string) => transactionsApi.remove(id),
    onSuccess: () => {
      notify('success', 'Entrada removida');
      void qc.invalidateQueries({ queryKey: ['transactions'] });
    },
    onError: (e) => notify('error', problemDetailMessage(e)),
  });

  const categoryName = (id?: string | null) =>
    (id && categories.data?.find((c) => c.id === id)?.name) || '—';

  const incomes = useMemo(
    () => (transactions.data?.content ?? []).filter((t) => t.type === 'INCOME'),
    [transactions.data],
  );

  const visible = filter === 'ALL'
    ? incomes
    : incomes.filter((t) => (t.incomeKind ?? 'OTHER') === filter);

  const totalByKind = (kind: string) =>
    incomes.filter((t) => (t.incomeKind ?? 'OTHER') === kind)
      .reduce((acc, t) => acc + t.amount, 0);

  return (
    <div className="space-y-6">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="display text-[26px]">Entradas</h1>
          <p className="text-sm text-muted">
            Salário, aportes, estimativas e feriados remunerados
          </p>
        </div>
        <button className="btn-primary" onClick={() => setModalOpen(true)}>+ Nova entrada</button>
      </header>

      <div className="grid gap-4 grid-cols-2 lg:grid-cols-4">
        <Card>
          <p className="text-xs text-muted">Entradas do mês</p>
          <p className="num text-xl font-semibold mt-1">
            <Money value={planning.data?.monthIncome ?? 0} />
          </p>
        </Card>
        <Card>
          <p className="text-xs text-muted">Aportes</p>
          <p className="num text-xl font-semibold mt-1"><Money value={totalByKind('CONTRIBUTION')} /></p>
        </Card>
        <Card>
          <p className="text-xs text-muted">Estimativas</p>
          <p className="num text-xl font-semibold mt-1"><Money value={totalByKind('ESTIMATE')} /></p>
        </Card>
        <Card>
          <p className="text-xs text-muted">Sobra para aportar</p>
          <p className="num text-xl font-semibold mt-1">
            <Money value={planning.data?.availableToContribute ?? 0} />
          </p>
          <p className="text-xs text-muted mt-1">
            após <Money value={planning.data?.total ?? 0} /> de gastos planejados
          </p>
        </Card>
      </div>

      <Card>
        <div className="flex flex-wrap gap-1 mb-4">
          {FILTERS.map((f) => (
            <button
              key={f.value}
              onClick={() => setFilter(f.value)}
              className={filter === f.value ? 'btn-primary text-xs px-3 py-1' : 'btn-ghost text-xs px-3 py-1'}
            >
              {f.label}
            </button>
          ))}
        </div>

        {transactions.isLoading ? (
          <div className="space-y-2">
            {Array.from({ length: 5 }).map((_, i) => <Skeleton key={i} className="h-10" />)}
          </div>
        ) : visible.length === 0 ? (
          <EmptyState
            title="Nenhuma entrada"
            subtitle="Registre salário, aportes, estimativas ou feriados remunerados."
          />
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-xs text-muted border-b border-line">
                <th className="py-2">Data</th>
                <th>Descrição</th>
                <th>Natureza</th>
                <th>Categoria</th>
                <th className="text-right">Valor</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {visible.map((t) => (
                <tr key={t.id} className="border-b border-line/60">
                  <td className="py-2">{formatDate(t.date)}</td>
                  <td>{t.description}</td>
                  <td>
                    <span className="badge badge-accent">{incomeKindLabel(t.incomeKind)}</span>
                  </td>
                  <td className="text-muted">{categoryName(t.categoryId)}</td>
                  <td className="text-right"><Money value={t.amount} /></td>
                  <td className="text-right">
                    <button
                      className="btn-ghost text-danger"
                      onClick={() => remove.mutate(t.id)}
                      aria-label="Remover"
                    >
                      <X size={14} aria-hidden />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}

        {(transactions.data?.totalElements ?? 0) > 50 && (
          <div className="flex justify-between items-center pt-4 text-sm">
            <button className="btn-ghost" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
              Anterior
            </button>
            <span className="text-muted">
              Página {page + 1} de {Math.max(1, Math.ceil((transactions.data?.totalElements ?? 0) / 50))}
            </span>
            <button
              className="btn-ghost"
              disabled={(page + 1) * 50 >= (transactions.data?.totalElements ?? 0)}
              onClick={() => setPage((p) => p + 1)}
            >
              Próxima
            </button>
          </div>
        )}
      </Card>

      <IncomeModal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        accounts={accounts.data ?? []}
        categories={categories.data ?? []}
      />
    </div>
  );
}

function IncomeModal({ open, onClose, accounts, categories }: {
  open: boolean;
  onClose: () => void;
  accounts: { id: string; name: string }[];
  categories: { id: string; name: string; kind: string }[];
}) {
  const qc = useQueryClient();
  const { notify } = useToast();
  const [form, setForm] = useState({
    incomeKind: 'SALARY',
    accountId: '',
    categoryId: '',
    amount: '',
    date: todayInput(),
    description: '',
  });

  const create = useMutation({
    mutationFn: (body: Record<string, unknown>) => transactionsApi.create(body),
    onSuccess: () => {
      notify('success', 'Entrada registrada');
      void qc.invalidateQueries();
      onClose();
    },
    onError: (e) => notify('error', problemDetailMessage(e)),
  });

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.accountId) {
      notify('error', 'Selecione uma conta (crie uma em Ajustes)');
      return;
    }
    create.mutate({
      type: 'INCOME',
      incomeKind: form.incomeKind,
      accountId: form.accountId,
      categoryId: form.categoryId || undefined,
      amount: form.amount,
      date: form.date,
      description: form.description,
    });
  };

  return (
    <Modal open={open} title="Nova entrada" onClose={onClose}>
      <form onSubmit={submit} className="space-y-4">
        <Field label="Natureza da entrada">
          <Select
            value={form.incomeKind}
            onChange={(e) => setForm({ ...form, incomeKind: e.target.value })}
          >
            {INCOME_KIND_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>{o.label}</option>
            ))}
          </Select>
        </Field>
        <Field label="Conta">
          <Select value={form.accountId} onChange={(e) => setForm({ ...form, accountId: e.target.value })}>
            <option value="">Selecione...</option>
            {accounts.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
          </Select>
        </Field>
        <Field label="Categoria">
          <Select value={form.categoryId} onChange={(e) => setForm({ ...form, categoryId: e.target.value })}>
            <option value="">Sem categoria</option>
            {categories
              .filter((c) => c.kind === 'INCOME')
              .map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </Select>
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Valor">
            <Input
              type="number"
              step="0.01"
              min="0"
              required
              value={form.amount}
              onChange={(e) => setForm({ ...form, amount: e.target.value })}
            />
          </Field>
          <Field label="Data">
            <Input
              type="date"
              required
              value={form.date}
              onChange={(e) => setForm({ ...form, date: e.target.value })}
            />
          </Field>
        </div>
        <Field label="Descrição">
          <Input
            required
            maxLength={255}
            placeholder={`Ex.: ${INCOME_KIND_LABELS[form.incomeKind] ?? 'Entrada'}`}
            value={form.description}
            onChange={(e) => setForm({ ...form, description: e.target.value })}
          />
        </Field>
        <button className="btn-primary w-full" disabled={create.isPending}>Salvar entrada</button>
      </form>
    </Modal>
  );
}
