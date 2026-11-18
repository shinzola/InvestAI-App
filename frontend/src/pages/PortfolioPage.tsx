import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { portfolioApi } from '../services/endpoints';
import { Card, EmptyState, Skeleton, StatCard } from '../components/ui/Card';
import { Field, Input, Modal, Money, Select } from '../components/ui/Form';
import { useToast } from '../components/ui/Toast';
import { problemDetailMessage } from '../services/api';
import { todayInput } from '../utils/format';

const brl = (v: number) => v.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });

export function PortfolioPage() {
  const { notify } = useToast();
  const [open, setOpen] = useState(false);

  const positions = useQuery({ queryKey: ['positions'], queryFn: () => portfolioApi.positions().then((r) => r.data) });
  const allocation = useQuery({ queryKey: ['allocation'], queryFn: () => portfolioApi.allocation().then((r) => r.data) });
  const rebalance = useQuery({ queryKey: ['rebalance'], queryFn: () => portfolioApi.rebalance().then((r) => r.data) });

  const list = positions.data ?? [];
  const total = list.reduce((acc, p) => acc + p.marketValue, 0);
  const invested = list.reduce((acc, p) => acc + p.invested, 0);
  const unrealized = list.reduce((acc, p) => acc + p.unrealizedResult, 0);

  return (
    <div className="space-y-6">
      <header className="flex items-center justify-between">
        <div>
          <h1 className="display text-[26px]">Carteira</h1>
          <p className="text-sm text-muted">Posições, preço médio e rebalanceamento</p>
        </div>
        <button className="btn-primary" onClick={() => setOpen(true)}>+ Operação</button>
      </header>

      <div className="grid gap-4 grid-cols-1 sm:grid-cols-3">
        <StatCard label="Valor de mercado" value={brl(total)} />
        <StatCard label="Custo investido" value={brl(invested)} />
        <StatCard
          label="Resultado não realizado"
          value={brl(unrealized)}
          tone={unrealized >= 0 ? 'positive' : 'negative'}
        />
      </div>

      <Card title="Posições">
        {positions.isLoading ? (
          <div className="space-y-2">{Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-10" />)}</div>
        ) : list.length === 0 ? (
          <EmptyState title="Carteira vazia" subtitle="Registre uma compra para começar a acompanhar suas posições." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm tabular-nums">
              <thead>
                <tr className="text-left text-xs text-muted border-b border-line">
                  <th className="py-2">Ativo</th><th>Classe</th><th className="text-right">Qtd</th>
                  <th className="text-right">Preço médio</th><th className="text-right">Valor</th><th className="text-right">P&L</th>
                </tr>
              </thead>
              <tbody>
                {list.map((p) => (
                  <tr key={p.assetId} className="border-b border-line/60">
                    <td className="py-2 font-medium">{p.ticker}</td>
                    <td className="text-muted">{p.type}</td>
                    <td className="text-right">{p.quantity}</td>
                    <td className="text-right"><Money value={p.averagePrice} /></td>
                    <td className="text-right"><Money value={p.marketValue} /></td>
                    <td className="text-right"><Money value={p.unrealizedResult} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card title="Alocação por classe">
          {allocation.data?.length ? (
            <ul className="space-y-2 text-sm">
              {allocation.data.map((a) => (
                <li key={a.assetClass}>
                  <div className="flex justify-between">
                    <span>{a.assetClass}</span>
                    <span className="tabular-nums">{a.currentPct}% <span className="text-muted">/ alvo {a.targetPct}%</span></span>
                  </div>
                  <div className="h-2 rounded-full bg-card-2 mt-1 overflow-hidden">
                    <div className="h-full bg-accent" style={{ width: `${Math.min(a.currentPct, 100)}%` }} />
                  </div>
                </li>
              ))}
            </ul>
          ) : <EmptyState title="Sem alocação-alvo" subtitle="Defina metas por classe para ver o desvio." />}
        </Card>

        <Card title="Sugestão de rebalanceamento">
          {rebalance.data?.length ? (
            <table className="w-full text-sm tabular-nums">
              <thead>
                <tr className="text-left text-xs text-muted border-b border-line">
                  <th className="py-2">Classe</th><th className="text-right">Ação</th><th className="text-right">Valor</th>
                </tr>
              </thead>
              <tbody>
                {rebalance.data.filter((r) => r.action !== 'HOLD').map((r) => (
                  <tr key={r.assetClass} className="border-b border-line/60">
                    <td className="py-2">{r.assetClass}</td>
                    <td className={`text-right ${r.action === 'BUY' ? 'text-success' : 'text-danger'}`}>
                      {r.action === 'BUY' ? 'Comprar' : 'Vender'}
                    </td>
                    <td className="text-right"><Money value={r.amount} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : <EmptyState title="Carteira equilibrada" subtitle="Nenhum ajuste necessário ou alocação-alvo não definida." />}
        </Card>
      </div>

      <OperationModal open={open} onClose={() => setOpen(false)} onDone={() => notify('success', 'Operação registrada')} />
    </div>
  );
}

function OperationModal({ open, onClose, onDone }: { open: boolean; onClose: () => void; onDone: () => void }) {
  const qc = useQueryClient();
  const { notify } = useToast();
  const assets = useQuery({ queryKey: ['assets'], queryFn: () => portfolioApi.assets().then((r) => r.data) });
  const [form, setForm] = useState({
    ticker: '', assetClass: 'STOCK', type: 'BUY', quantity: '', price: '', fees: '0',
    date: todayInput(),
  });

  const create = useMutation({
    mutationFn: async () => {
      const ticker = form.ticker.trim().toUpperCase();
      const existing = assets.data?.find((a) => a.ticker === ticker);
      const assetId = existing?.id ?? (await portfolioApi.createAsset({
        ticker, name: ticker, type: form.assetClass,
      })).data.id;
      return portfolioApi.createOperation({
        assetId, type: form.type, quantity: form.quantity, price: form.price,
        fees: form.fees, date: form.date,
      });
    },
    onSuccess: () => {
      onDone();
      void qc.invalidateQueries();
      onClose();
    },
    onError: (e) => notify('error', problemDetailMessage(e)),
  });

  return (
    <Modal open={open} title="Nova operação" onClose={onClose}>
      <form className="space-y-4" onSubmit={(e) => { e.preventDefault(); create.mutate(); }}>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Ticker">
            <Input required maxLength={20} placeholder="PETR4" value={form.ticker}
              onChange={(e) => setForm({ ...form, ticker: e.target.value.toUpperCase() })} />
          </Field>
          <Field label="Classe">
            <Select value={form.assetClass} onChange={(e) => setForm({ ...form, assetClass: e.target.value })}>
              {['STOCK', 'FII', 'ETF', 'TESOURO', 'CDB', 'LCI', 'LCA', 'DEBENTURE', 'FUND', 'PENSION', 'CRYPTO']
                .map((c) => <option key={c} value={c}>{c}</option>)}
            </Select>
          </Field>
        </div>
        <Field label="Tipo">
          <Select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value })}>
            <option value="BUY">Compra</option>
            <option value="SELL">Venda</option>
          </Select>
        </Field>
        <div className="grid grid-cols-3 gap-3">
          <Field label="Quantidade">
            <Input type="number" step="0.00000001" min="0" required value={form.quantity}
              onChange={(e) => setForm({ ...form, quantity: e.target.value })} />
          </Field>
          <Field label="Preço">
            <Input type="number" step="0.01" min="0" required value={form.price}
              onChange={(e) => setForm({ ...form, price: e.target.value })} />
          </Field>
          <Field label="Corretagem">
            <Input type="number" step="0.01" min="0" value={form.fees}
              onChange={(e) => setForm({ ...form, fees: e.target.value })} />
          </Field>
        </div>
        <Field label="Data">
          <Input type="date" required value={form.date} onChange={(e) => setForm({ ...form, date: e.target.value })} />
        </Field>
        <button className="btn-primary w-full" disabled={create.isPending}>Registrar</button>
      </form>
    </Modal>
  );
}
