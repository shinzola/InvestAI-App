import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { earningsApi, portfolioApi } from '../services/endpoints';
import { Card, EmptyState, Skeleton, StatCard } from '../components/ui/Card';
import { Field, Input, Modal, Money, Select } from '../components/ui/Form';
import { useToast } from '../components/ui/Toast';
import { problemDetailMessage } from '../services/api';
import { formatDate, todayInput } from '../utils/format';

const brl = (v: number) => v.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });

export function EarningsPage() {
  const qc = useQueryClient();
  const { notify } = useToast();
  const [open, setOpen] = useState(false);

  const history = useQuery({ queryKey: ['earnings'], queryFn: () => earningsApi.list().then((r) => r.data) });
  const summary = useQuery({ queryKey: ['earnings-summary'], queryFn: () => earningsApi.summary().then((r) => r.data) });

  const create = useMutation({
    mutationFn: (body: Record<string, unknown>) => earningsApi.create(body),
    onSuccess: () => {
      notify('success', 'Provento registrado');
      void qc.invalidateQueries();
      setOpen(false);
    },
    onError: (e) => notify('error', problemDetailMessage(e)),
  });

  return (
    <div className="space-y-6">
      <header className="flex items-center justify-between">
        <div>
          <h1 className="display text-[26px]">Proventos</h1>
          <p className="text-sm text-muted">Dividendos, JCP, rendimentos e cupons</p>
        </div>
        <button className="btn-primary" onClick={() => setOpen(true)}>+ Provento</button>
      </header>

      <div className="grid gap-4 grid-cols-1 sm:grid-cols-3">
        <StatCard label="Média mensal (12m)" value={brl(summary.data?.averageLast12m ?? 0)} />
        <StatCard label="Projeção 12 meses" value={brl(summary.data?.projectionNext12m ?? 0)} />
        <StatCard
          label="Falta para viver de renda"
          value={brl(summary.data?.missingForDesiredIncome ?? 0)}
          hint={`Meta mensal: ${brl(summary.data?.desiredMonthlyIncome ?? 0)}`}
        />
      </div>

      <Card title="Cobertura da renda desejada">
        <div className="flex items-center gap-3">
          <div className="flex-1 h-3 rounded-full bg-card-2 overflow-hidden">
            <div className="h-full bg-accent" style={{ width: `${Math.min(summary.data?.coveragePct ?? 0, 100)}%` }} />
          </div>
          <span className="text-sm font-semibold tabular-nums">{summary.data?.coveragePct ?? 0}%</span>
        </div>
        <p className="text-xs text-muted mt-2">
          Total recebido nos últimos 12 meses: {brl(summary.data?.totalLast12m ?? 0)}
        </p>
      </Card>

      <Card title="Histórico">
        {history.isLoading ? (
          <div className="space-y-2">{Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-9" />)}</div>
        ) : (history.data?.length ?? 0) === 0 ? (
          <EmptyState title="Nenhum provento registrado" subtitle="Registre dividendos e rendimentos para acompanhar sua renda passiva." />
        ) : (
          <table className="w-full text-sm tabular-nums">
            <thead>
              <tr className="text-left text-xs text-muted border-b border-line">
                <th className="py-2">Data</th><th>Tipo</th><th>Ativo</th><th className="text-right">Líquido</th>
              </tr>
            </thead>
            <tbody>
              {history.data?.map((d) => (
                <tr key={d.id} className="border-b border-line/60">
                  <td className="py-2">{formatDate(d.date)}</td>
                  <td className="text-muted">{d.type}</td>
                  <td>{d.ticker ?? '—'}</td>
                  <td className="text-right"><Money value={d.netAmount} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>

      <Modal open={open} title="Novo provento" onClose={() => setOpen(false)}>
        <DividendForm onSubmit={(v) => create.mutate(v)} pending={create.isPending} />
      </Modal>
    </div>
  );
}

function DividendForm({ onSubmit, pending }: { onSubmit: (v: Record<string, unknown>) => void; pending: boolean }) {
  const assets = useQuery({ queryKey: ['assets'], queryFn: () => portfolioApi.assets().then((r) => r.data) });
  const [form, setForm] = useState({
    assetId: '', type: 'DIVIDEND', amount: '', withholdingTax: '0',
    date: todayInput(),
  });
  return (
    <form className="space-y-4" onSubmit={(e) => { e.preventDefault(); onSubmit(form); }}>
      <Field label="Ativo">
        <Select required value={form.assetId} onChange={(e) => setForm({ ...form, assetId: e.target.value })}>
          <option value="">Selecione…</option>
          {assets.data?.map((a) => <option key={a.id} value={a.id}>{a.ticker}</option>)}
        </Select>
      </Field>
      <Field label="Tipo">
        <Select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value })}>
          {['DIVIDEND', 'JCP', 'FII_YIELD', 'COUPON'].map((t) => <option key={t} value={t}>{t}</option>)}
        </Select>
      </Field>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Valor bruto">
          <Input type="number" step="0.01" min="0.01" required value={form.amount} onChange={(e) => setForm({ ...form, amount: e.target.value })} />
        </Field>
        <Field label="IR retido">
          <Input type="number" step="0.01" min="0" value={form.withholdingTax} onChange={(e) => setForm({ ...form, withholdingTax: e.target.value })} />
        </Field>
      </div>
      <Field label="Data">
        <Input type="date" required value={form.date} onChange={(e) => setForm({ ...form, date: e.target.value })} />
      </Field>
      <button className="btn-primary w-full" disabled={pending}>Salvar</button>
    </form>
  );
}
