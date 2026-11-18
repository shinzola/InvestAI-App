import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { taxApi } from '../services/endpoints';
import { Card, EmptyState, Skeleton } from '../components/ui/Card';
import { Money, Select } from '../components/ui/Form';

export function ReportsPage() {
  const now = new Date();
  const [year, setYear] = useState(now.getFullYear());
  const [month, setMonth] = useState(now.getMonth() + 1);

  const darf = useQuery({
    queryKey: ['darf', year, month],
    queryFn: () => taxApi.darf(year, month).then((r) => r.data),
  });

  const items = darf.data?.items ?? [];

  return (
    <div className="space-y-6">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="display text-[26px]">Relatórios</h1>
          <p className="text-sm text-muted">Apuração de DARF e relatório fiscal</p>
        </div>
        <div className="flex gap-2">
          <Select value={month} onChange={(e) => setMonth(Number(e.target.value))} aria-label="Mês">
            {Array.from({ length: 12 }, (_, i) => (
              <option key={i + 1} value={i + 1}>{new Date(2024, i, 1).toLocaleDateString('pt-BR', { month: 'long' })}</option>
            ))}
          </Select>
          <Select value={year} onChange={(e) => setYear(Number(e.target.value))} aria-label="Ano">
            {[year - 1, year, year + 1].map((y) => <option key={y} value={y}>{y}</option>)}
          </Select>
        </div>
      </header>

      <Card title="Apuração de DARF">
        {darf.isLoading ? (
          <Skeleton className="h-32" />
        ) : items.length === 0 ? (
          <EmptyState title="Sem apuração" subtitle="Registre vendas de renda variável para calcular o DARF." />
        ) : (
          <div className="space-y-3 text-sm">
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
              <div>
                <p className="text-xs text-muted">Imposto devido</p>
                <p className="font-bold"><Money value={darf.data?.totalTaxDue ?? 0} /></p>
              </div>
              <div>
                <p className="text-xs text-muted">Competência</p>
                <p className="font-bold">{darf.data?.month ?? '—'}</p>
              </div>
              <div>
                <p className="text-xs text-muted">Lançamentos</p>
                <p className="font-bold">{items.length}</p>
              </div>
            </div>
            <table className="w-full text-sm tabular-nums mt-2">
              <thead>
                <tr className="text-left text-xs text-muted border-b border-line">
                  <th className="py-2">Descrição</th><th>Código</th>
                  <th className="text-right">Base</th><th className="text-right">IR</th><th className="text-right">Status</th>
                </tr>
              </thead>
              <tbody>
                {items.map((i, idx) => (
                  <tr key={`${i.darfCode}-${idx}`} className="border-b border-line/60">
                    <td className="py-2">{i.description}</td>
                    <td className="text-muted">{i.darfCode}</td>
                    <td className="text-right"><Money value={i.taxableAmount} /></td>
                    <td className="text-right"><Money value={i.taxAmount} /></td>
                    <td className="text-right text-muted">{i.status}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  );
}
