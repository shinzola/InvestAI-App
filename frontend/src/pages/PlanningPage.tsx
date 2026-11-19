import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ChevronLeft, ChevronRight, X } from 'lucide-react';
import { accountsApi, categoriesApi, planningApi } from '../services/endpoints';
import { Card, EmptyState, Skeleton } from '../components/ui/Card';
import { Field, Input, Modal, Money, Select, Switch } from '../components/ui/Form';
import { useToast } from '../components/ui/Toast';
import { problemDetailMessage } from '../services/api';
import { formatDate, todayInput } from '../utils/format';
import { PLANNING_KIND_LABELS, PLANNING_STATUS_LABELS } from '../lib/labels';
import type { PlannedExpense } from '../types';

const monthLabel = (year: number, month: number) =>
  new Date(year, month - 1, 1).toLocaleDateString('pt-BR', { month: 'long', year: 'numeric' });

/** Data padrão do gasto: hoje no mês corrente, dia 1 nos demais meses. */
const defaultDueDate = (year: number, month: number) => {
  const now = new Date();
  if (year === now.getFullYear() && month === now.getMonth() + 1) {
    return todayInput();
  }
  return `${year}-${String(month).padStart(2, '0')}-01`;
};

export function PlanningPage() {
  const qc = useQueryClient();
  const { notify } = useToast();
  const today = new Date();
  const [cursor, setCursor] = useState({ year: today.getFullYear(), month: today.getMonth() + 1 });
  const [modalOpen, setModalOpen] = useState(false);

  const list = useQuery({
    queryKey: ['planning', cursor.year, cursor.month],
    queryFn: () => planningApi.list(cursor.year, cursor.month).then((r) => r.data),
  });
  const summary = useQuery({
    queryKey: ['planning-summary', cursor.year, cursor.month],
    queryFn: () => planningApi.summary(cursor.year, cursor.month).then((r) => r.data),
  });
  const categories = useQuery({
    queryKey: ['categories'],
    queryFn: () => categoriesApi.list().then((r) => r.data),
  });
  const accounts = useQuery({
    queryKey: ['accounts'],
    queryFn: () => accountsApi.list().then((r) => r.data),
  });
  const cardAccounts = (accounts.data ?? []).filter((a) => a.card);

  const invalidate = () => {
    void qc.invalidateQueries({ queryKey: ['planning'] });
    void qc.invalidateQueries({ queryKey: ['planning-summary'] });
  };

  const toggle = useMutation({
    mutationFn: (id: string) => planningApi.togglePaid(id),
    onSuccess: invalidate,
    onError: (e) => notify('error', problemDetailMessage(e)),
  });

  const remove = useMutation({
    mutationFn: (id: string) => planningApi.remove(id),
    onSuccess: () => { notify('success', 'Gasto removido'); invalidate(); },
    onError: (e) => notify('error', problemDetailMessage(e)),
  });

  // agrupa por data de vencimento
  const byDate = useMemo(() => {
    const groups = new Map<string, PlannedExpense[]>();
    for (const item of list.data ?? []) {
      const key = item.dueDate;
      groups.set(key, [...(groups.get(key) ?? []), item]);
    }
    return [...groups.entries()].sort(([a], [b]) => a.localeCompare(b));
  }, [list.data]);

  const shift = (delta: number) => {
    const d = new Date(cursor.year, cursor.month - 1 + delta, 1);
    setCursor({ year: d.getFullYear(), month: d.getMonth() + 1 });
  };

  const s = summary.data;

  return (
    <div className="space-y-6">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="display text-[26px]">Planejamento</h1>
          <p className="text-sm text-muted">Todos os gastos do mês, fixos e individuais, por data</p>
        </div>
        <div className="flex items-center gap-2">
          <button className="btn-ghost" onClick={() => shift(-1)} aria-label="Mês anterior">
            <ChevronLeft size={16} aria-hidden />
          </button>
          <span className="text-sm font-medium capitalize min-w-[150px] text-center">
            {monthLabel(cursor.year, cursor.month)}
          </span>
          <button className="btn-ghost" onClick={() => shift(1)} aria-label="Próximo mês">
            <ChevronRight size={16} aria-hidden />
          </button>
          <button className="btn-primary" onClick={() => setModalOpen(true)}>+ Gasto</button>
        </div>
      </header>

      <div className="grid gap-4 grid-cols-2 lg:grid-cols-5">
        <Card>
          <p className="text-xs text-muted">Total planejado</p>
          <p className="num text-xl font-semibold mt-1"><Money value={s?.total ?? 0} /></p>
          <p className="text-xs text-muted mt-1">{s?.count ?? 0} gastos</p>
        </Card>
        <Card>
          <p className="text-xs text-muted">Fixos</p>
          <p className="num text-xl font-semibold mt-1"><Money value={s?.totalFixed ?? 0} /></p>
          <p className="text-xs text-muted mt-1">Individuais: <Money value={s?.totalVariable ?? 0} /></p>
        </Card>
        <Card>
          <p className="text-xs text-muted">Cartão de crédito</p>
          <p className="num text-xl font-semibold mt-1"><Money value={s?.totalCreditCard ?? 0} /></p>
          <p className="text-xs text-muted mt-1">Fatura do mês</p>
        </Card>
        <Card>
          <p className="text-xs text-muted">Já pago</p>
          <p className="num text-xl font-semibold mt-1 text-success"><Money value={s?.paid ?? 0} /></p>
          <p className="text-xs text-muted mt-1">
            {s?.paidCount ?? 0} de {s?.count ?? 0} quitados
          </p>
        </Card>
        <Card>
          <p className="text-xs text-muted">Sobra para aportar</p>
          <p className="num text-xl font-semibold mt-1">
            <Money value={s?.availableToContribute ?? 0} />
          </p>
          <p className="text-xs text-muted mt-1">
            Entradas do mês: <Money value={s?.monthIncome ?? 0} />
          </p>
        </Card>
      </div>

      <Card title="Gastos por data">
        {list.isLoading ? (
          <div className="space-y-2">
            {Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-12" />)}
          </div>
        ) : byDate.length === 0 ? (
          <EmptyState
            title="Nenhum gasto planejado"
            subtitle="Adicione aluguel, contas fixas e gastos individuais do mês."
          />
        ) : (
          <div className="space-y-5">
            {byDate.map(([date, items]) => (
              <div key={date}>
                <div className="flex items-center gap-2 mb-2">
                  <span className="badge badge-accent">{formatDate(date)}</span>
                  <span className="text-xs text-muted">
                    <Money value={items.reduce((acc, i) => acc + i.amount, 0)} />
                  </span>
                </div>
                <ul className="space-y-1">
                  {items.map((item) => (
                    <li
                      key={item.id}
                      className="flex items-center gap-3 rounded-lg px-2 py-2 hover:bg-card-2"
                    >
                      <Switch
                        checked={item.status === 'PAID'}
                        onChange={() => toggle.mutate(item.id)}
                        aria-label={`Marcar ${item.description} como pago`}
                      />
                      <span className={item.status === 'PAID' ? 'line-through text-muted' : ''}>
                        {item.description}
                      </span>
                      <span className="badge">{PLANNING_KIND_LABELS[item.kind]}</span>
                      {item.kind === 'CREDIT_CARD' && item.invoiceDueDate && (
                        <span className="text-xs text-muted">
                          fatura vence {formatDate(item.invoiceDueDate)}
                        </span>
                      )}
                      <span className="text-xs text-muted">
                        {PLANNING_STATUS_LABELS[item.status]}
                      </span>
                      <span className="flex-1" />
                      <span className="num text-sm"><Money value={item.amount} /></span>
                      <button
                        className="btn-ghost text-danger"
                        onClick={() => remove.mutate(item.id)}
                        aria-label="Remover"
                      >
                        <X size={14} aria-hidden />
                      </button>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
        )}
      </Card>

      <PlanningModal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        year={cursor.year}
        month={cursor.month}
        categories={categories.data ?? []}
        cardAccounts={cardAccounts}
        onSaved={invalidate}
      />
    </div>
  );
}

function PlanningModal({ open, onClose, year, month, categories, cardAccounts, onSaved }: {
  open: boolean;
  onClose: () => void;
  year: number;
  month: number;
  categories: { id: string; name: string; kind: string }[];
  cardAccounts: { id: string; name: string }[];
  onSaved: () => void;
}) {
  const { notify } = useToast();
  const emptyForm = () => ({
    description: '',
    amount: '',
    dueDate: defaultDueDate(year, month),
    kind: 'VARIABLE',
    categoryId: '',
    notes: '',
    cardAccountId: '',
  });
  const [form, setForm] = useState(emptyForm);

  // ao abrir, a data acompanha o mês em exibição (evita criar fora do mês visível)
  useEffect(() => {
    if (open) {
      setForm(emptyForm());
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, year, month]);

  const create = useMutation({
    mutationFn: (body: Record<string, unknown>) => planningApi.create(body),
    onSuccess: () => {
      notify('success', 'Gasto planejado');
      onSaved();
      onClose();
      setForm(emptyForm());
    },
    onError: (e) => notify('error', problemDetailMessage(e)),
  });

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    create.mutate({
      description: form.description,
      amount: form.amount,
      dueDate: form.dueDate,
      kind: form.kind,
      categoryId: form.categoryId || undefined,
      notes: form.notes || undefined,
      cardAccountId: form.kind === 'CREDIT_CARD' ? form.cardAccountId || undefined : undefined,
    });
  };

  return (
    <Modal open={open} title={`Novo gasto · ${monthLabel(year, month)}`} onClose={onClose}>
      <form onSubmit={submit} className="space-y-4">
        <Field label="Descrição">
          <Input
            required
            maxLength={255}
            placeholder="Ex.: Aluguel, mercado, IPTU"
            value={form.description}
            onChange={(e) => setForm({ ...form, description: e.target.value })}
          />
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <div className={form.kind === 'CREDIT_CARD' ? 'col-span-2' : ''}>
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
          </div>
          {form.kind !== 'CREDIT_CARD' && (
            <Field label="Data de vencimento">
              <Input
                type="date"
                required
                value={form.dueDate}
                onChange={(e) => setForm({ ...form, dueDate: e.target.value })}
              />
            </Field>
          )}
        </div>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Tipo">
            <Select value={form.kind} onChange={(e) => setForm({ ...form, kind: e.target.value })}>
              <option value="FIXED">Fixo (todo mês)</option>
              <option value="VARIABLE">Individual do mês</option>
              <option value="CREDIT_CARD">Cartão de crédito</option>
            </Select>
          </Field>
          <Field label="Categoria">
            <Select
              value={form.categoryId}
              onChange={(e) => setForm({ ...form, categoryId: e.target.value })}
            >
              <option value="">Sem categoria</option>
              {categories
                .filter((c) => c.kind === 'EXPENSE')
                .map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </Select>
          </Field>
        </div>
        {form.kind === 'CREDIT_CARD' && (
          <div className="rounded-lg border border-line p-3 space-y-3">
            <p className="text-xs font-medium text-muted">Fatura do cartão</p>
            <Field label="Cartão">
              <Select
                value={form.cardAccountId}
                onChange={(e) => setForm({ ...form, cardAccountId: e.target.value })}
              >
                <option value="">Selecione o cartão</option>
                {cardAccounts.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
              </Select>
            </Field>
            <p className="text-xs text-muted">
              Fechamento e vencimento vêm do cadastro do cartão. Ao marcar como pago,
              o valor volta para o limite disponível.
            </p>
          </div>
        )}
        <Field label="Observação">
          <Input
            maxLength={255}
            placeholder="Opcional"
            value={form.notes}
            onChange={(e) => setForm({ ...form, notes: e.target.value })}
          />
        </Field>
        <button className="btn-primary w-full" disabled={create.isPending}>Adicionar ao planejamento</button>
      </form>
    </Modal>
  );
}
