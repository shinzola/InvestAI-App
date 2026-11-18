import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { budgetsApi, categoriesApi } from '../services/endpoints';
import { Card, EmptyState, Skeleton } from '../components/ui/Card';
import { MeterBar } from '../components/ui/Progress';
import { Field, Input, Modal, Money, Select } from '../components/ui/Form';
import { useToast } from '../components/ui/Toast';
import { problemDetailMessage } from '../services/api';

export function BudgetPage() {
  const qc = useQueryClient();
  const { notify } = useToast();
  const now = new Date();
  const [year, setYear] = useState(now.getFullYear());
  const [month, setMonth] = useState(now.getMonth() + 1);
  const [open, setOpen] = useState(false);

  const budget = useQuery({
    queryKey: ['budget', year, month],
    queryFn: () => budgetsApi.get(year, month).then((r) => r.data),
  });
  const categories = useQuery({ queryKey: ['categories'], queryFn: () => categoriesApi.list().then((r) => r.data) });

  const save = useMutation({
    mutationFn: (items: { categoryId: string; limitAmount: string }[]) => budgetsApi.save(year, month, items),
    onSuccess: () => {
      notify('success', 'Orçamento salvo');
      void qc.invalidateQueries({ queryKey: ['budget'] });
      setOpen(false);
    },
    onError: (e) => notify('error', problemDetailMessage(e)),
  });

  const items = budget.data?.items ?? [];

  const addItem = (item: { categoryId: string; limitAmount: string }) => {
    const existing = items
      .filter((i) => i.categoryId !== item.categoryId)
      .map((i) => ({ categoryId: i.categoryId, limitAmount: String(i.limitAmount) }));
    save.mutate([...existing, item]);
  };

  return (
    <div className="space-y-6">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="display text-[26px]">Orçamento</h1>
          <p className="text-sm text-muted">Limites por categoria e alertas em 80% / 100%</p>
        </div>
        <div className="flex gap-2">
          <Select value={month} onChange={(e) => setMonth(Number(e.target.value))} aria-label="Mês">
            {Array.from({ length: 12 }, (_, i) => (
              <option key={i + 1} value={i + 1}>
                {new Date(2024, i, 1).toLocaleDateString('pt-BR', { month: 'long' })}
              </option>
            ))}
          </Select>
          <Select value={year} onChange={(e) => setYear(Number(e.target.value))} aria-label="Ano">
            {[year - 1, year, year + 1].map((y) => <option key={y} value={y}>{y}</option>)}
          </Select>
          <button className="btn-primary" onClick={() => setOpen(true)}>+ Item</button>
        </div>
      </header>

      {budget.data && (
        <div className="grid gap-4 sm:grid-cols-3">
          <Card><p className="text-xs text-muted">Limite total</p><p className="text-xl font-bold"><Money value={budget.data.totalLimit} /></p></Card>
          <Card><p className="text-xs text-muted">Gasto total</p><p className="text-xl font-bold"><Money value={budget.data.totalSpent} /></p></Card>
          <Card>
            <p className="text-xs text-muted">Utilizado</p>
            <p className="text-xl font-bold">{budget.data.totalSpentPct}%</p>
          </Card>
        </div>
      )}

      {budget.isLoading ? (
        <div className="space-y-2">{Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-14" />)}</div>
      ) : items.length === 0 ? (
        <Card><EmptyState title="Nenhum orçamento definido" subtitle="Defina limites por categoria para acompanhar seus gastos." /></Card>
      ) : (
        <div className="grid gap-4 md:grid-cols-2">
          {items.map((item) => {
            const pct = Math.round(item.spentPct);
            const tone = pct >= 100 ? 'danger' : pct >= item.alertThresholdPct ? 'warning' : 'success';
            return (
              <Card key={item.categoryId}>
                <div className="flex justify-between items-baseline mb-2">
                  <span className="font-semibold">{item.categoryName}</span>
                  <span className="text-sm text-muted">{pct}%</span>
                </div>
                <MeterBar pct={pct} tone={tone} />                <div className="flex justify-between text-sm mt-2">
                  <span><Money value={item.spent} /> de <Money value={item.limitAmount} /></span>
                  <span className={item.remaining < 0 ? 'text-danger' : 'text-muted'}>
                    Saldo: <Money value={item.remaining} />
                  </span>
                </div>
                {pct >= item.alertThresholdPct && (
                  <p className={`text-xs mt-2 ${pct >= 100 ? 'text-danger' : 'text-warning'}`}>
                    {pct >= 100 ? 'Limite ultrapassado' : `Atenção: ${item.alertThresholdPct}% do limite atingido`}
                  </p>
                )}
                {item.atypical && <p className="text-xs mt-1 text-warning">Gasto atípico neste mês</p>}
              </Card>
            );
          })}
        </div>
      )}

      <Modal open={open} title="Novo item de orçamento" onClose={() => setOpen(false)}>
        <BudgetForm
          categories={categories.data ?? []}
          onSubmit={addItem}
          pending={save.isPending}
        />
      </Modal>
    </div>
  );
}

function BudgetForm({ categories, onSubmit, pending }: {
  categories: { id: string; name: string; kind: string }[];
  onSubmit: (v: { categoryId: string; limitAmount: string }) => void;
  pending: boolean;
}) {
  const [categoryId, setCategoryId] = useState('');
  const [limit, setLimit] = useState('');
  return (
    <form
      className="space-y-4"
      onSubmit={(e) => {
        e.preventDefault();
        onSubmit({ categoryId, limitAmount: limit });
      }}
    >
      <Field label="Categoria">
        <Select required value={categoryId} onChange={(e) => setCategoryId(e.target.value)}>
          <option value="">Selecione…</option>
          {categories.filter((c) => c.kind === 'EXPENSE').map((c) => (
            <option key={c.id} value={c.id}>{c.name}</option>
          ))}
        </Select>
      </Field>
      <Field label="Limite mensal">
        <Input type="number" step="0.01" min="0.01" required value={limit} onChange={(e) => setLimit(e.target.value)} />
      </Field>
      <button className="btn-primary w-full" disabled={pending}>Salvar</button>
    </form>
  );
}
