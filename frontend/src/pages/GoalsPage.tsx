import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Bird,
  Car,
  Flag,
  GraduationCap,
  Heart,
  House,
  LifeBuoy,
  Plane,
  Sunrise,
  type LucideIcon,
} from 'lucide-react';
import { goalsApi } from '../services/endpoints';
import { Card, EmptyState, Skeleton } from '../components/ui/Card';
import { Field, Input, Modal, Money, Select } from '../components/ui/Form';
import { MeterBar, ProgressRing } from '../components/ui/Progress';
import { useToast } from '../components/ui/Toast';
import { problemDetailMessage } from '../services/api';
import { formatDate, todayInput } from '../utils/format';
import { clsx } from 'clsx';

interface Preset {
  id: string;
  kind: 'RESERVA_EMERGENCIA' | 'VIAGEM' | 'IMOVEL' | 'APOSENTADORIA' | 'OUTRO';
  name: string;
  icon: LucideIcon;
  hint: string;
  suggested: number;
}

const PRESETS: Preset[] = [
  { id: 'reserva', kind: 'RESERVA_EMERGENCIA', name: 'Reserva de emergência', icon: LifeBuoy, hint: '6 meses de despesas', suggested: 15000 },
  { id: 'viagem', kind: 'VIAGEM', name: 'Viagem dos sonhos', icon: Plane, hint: 'Aquela trip inesquecível', suggested: 8000 },
  { id: 'imovel', kind: 'IMOVEL', name: 'Entrada do imóvel', icon: House, hint: 'Sua casa própria', suggested: 60000 },
  { id: 'aposentadoria', kind: 'APOSENTADORIA', name: 'Aposentadoria', icon: Sunrise, hint: 'Liberdade no futuro', suggested: 500000 },
  { id: 'carro', kind: 'OUTRO', name: 'Carro novo', icon: Car, hint: 'Troca do veículo', suggested: 45000 },
  { id: 'educacao', kind: 'OUTRO', name: 'Educação', icon: GraduationCap, hint: 'Curso ou pós-graduação', suggested: 20000 },
  { id: 'casamento', kind: 'OUTRO', name: 'Casamento', icon: Heart, hint: 'O grande dia', suggested: 30000 },
  { id: 'liberdade', kind: 'OUTRO', name: 'Liberdade financeira', icon: Bird, hint: 'Viver de renda', suggested: 1000000 },
];

export function GoalsPage() {
  const qc = useQueryClient();
  const { notify } = useToast();
  const [picking, setPicking] = useState(false);
  const [selected, setSelected] = useState<Record<string, string>>({});
  const [contributeTo, setContributeTo] = useState<string | null>(null);
  const [sim, setSim] = useState({ initial: '10000', monthly: '1000', rate: '10', years: '10', inflation: '4' });

  const goals = useQuery({ queryKey: ['goals'], queryFn: () => goalsApi.list().then((r) => r.data) });
  const simulation = useQuery({
    queryKey: ['sim', sim],
    queryFn: () => goalsApi.simulate({
      initialAmount: sim.initial, monthlyContribution: sim.monthly,
      annualRate: sim.rate, years: Number(sim.years), annualInflation: sim.inflation,
    }).then((r) => r.data),
  });

  const createMany = useMutation({
    mutationFn: async () => {
      const chosen = PRESETS.filter((p) => selected[p.id]);
      for (const preset of chosen) {
        await goalsApi.create({
          name: preset.name,
          kind: preset.kind,
          targetAmount: selected[preset.id],
        });
      }
      return chosen.length;
    },
    onSuccess: (count) => {
      notify('success', `${count} meta${count === 1 ? '' : 's'} criada${count === 1 ? '' : 's'} · +${count * 30} XP`);
      void qc.invalidateQueries({ queryKey: ['goals'] });
      void qc.invalidateQueries({ queryKey: ['game'] });
      setSelected({});
      setPicking(false);
    },
    onError: (e) => notify('error', problemDetailMessage(e)),
  });

  const contribute = useMutation({
    mutationFn: ({ id, amount }: { id: string; amount: string }) =>
      goalsApi.contribute(id, { amount, date: todayInput() }),
    onSuccess: () => {
      notify('success', 'Aporte registrado · +25 XP');
      void qc.invalidateQueries({ queryKey: ['goals'] });
      void qc.invalidateQueries({ queryKey: ['game'] });
      setContributeTo(null);
    },
    onError: (e) => notify('error', problemDetailMessage(e)),
  });

  const list = goals.data ?? [];
  const selectedCount = Object.keys(selected).length;
  const totalSelected = useMemo(
    () => Object.values(selected).reduce((acc, v) => acc + (Number(v) || 0), 0),
    [selected],
  );

  const toggle = (preset: Preset) => {
    setSelected((prev) => {
      if (prev[preset.id]) {
        const next = { ...prev };
        delete next[preset.id];
        return next;
      }
      return { ...prev, [preset.id]: String(preset.suggested) };
    });
  };

  return (
    <div className="space-y-5">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="display text-[26px]">Metas</h1>
          <p className="text-sm text-muted">Escolha um objetivo e acompanhe o progresso</p>
        </div>
        <button className="btn-primary" onClick={() => setPicking(true)}>+ Escolher metas</button>
      </header>

      {goals.isLoading ? (
        <div className="grid gap-4 md:grid-cols-2">{Array.from({ length: 2 }).map((_, i) => <Skeleton key={i} className="h-36" />)}</div>
      ) : list.length === 0 ? (
        <Card>
          <EmptyState
            title="Nenhuma meta ainda"
            subtitle="Escolha entre objetivos prontos e defina quanto quer juntar."
            action={<button className="btn-primary mt-2" onClick={() => setPicking(true)}>Escolher minhas metas</button>}
          />
        </Card>
      ) : (
        <div className="grid gap-4 md:grid-cols-2">
          {list.map((g) => {
            const preset = PRESETS.find((p) => p.name === g.name);
            const done = g.progressPct >= 100;
            return (
              <Card key={g.id} className="animate-pop">
                <div className="flex items-center gap-4">
                  <ProgressRing
                    value={g.progressPct}
                    size={78}
                    stroke={8}
                    label={`${Math.round(g.progressPct)}%`}
                    tone={done ? 'success' : 'accent'}
                  />
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      {preset
                        ? <preset.icon size={16} aria-hidden className="shrink-0 text-accent" />
                        : <Flag size={16} aria-hidden className="shrink-0 text-accent" />}
                      <p className="font-semibold truncate">{g.name}</p>
                      {done && <span className="badge-success">concluída</span>}
                    </div>
                    <p className="text-sm text-muted mt-0.5">
                      <Money value={g.saved} /> de <Money value={g.targetAmount} />
                    </p>
                    <p className="text-xs text-muted mt-1">
                      {g.requiredMonthlyContribution > 0
                        ? <>Aporte sugerido: <strong className="text-content"><Money value={g.requiredMonthlyContribution} /></strong>/mês</>
                        : <>Defina um prazo para calcular o aporte mensal</>}
                    </p>
                  </div>
                </div>
                <div className="mt-3">
                  <MeterBar pct={g.progressPct} tone={done ? 'success' : 'accent'} />
                </div>
                <button className="btn-soft w-full mt-3" onClick={() => setContributeTo(g.id)}>+ Aporte</button>
              </Card>
            );
          })}
        </div>
      )}

      <Card title="Simulador de juros compostos">
        <div className="grid gap-3 sm:grid-cols-5">
          <Field label="Inicial"><Input type="number" value={sim.initial} onChange={(e) => setSim({ ...sim, initial: e.target.value })} /></Field>
          <Field label="Aporte mensal"><Input type="number" value={sim.monthly} onChange={(e) => setSim({ ...sim, monthly: e.target.value })} /></Field>
          <Field label="Taxa a.a. (%)"><Input type="number" step="0.1" value={sim.rate} onChange={(e) => setSim({ ...sim, rate: e.target.value })} /></Field>
          <Field label="Anos"><Input type="number" value={sim.years} onChange={(e) => setSim({ ...sim, years: e.target.value })} /></Field>
          <Field label="Inflação (%)"><Input type="number" step="0.1" value={sim.inflation} onChange={(e) => setSim({ ...sim, inflation: e.target.value })} /></Field>
        </div>
        {simulation.data && (
          <div className="grid grid-cols-3 gap-3 mt-4 text-sm">
            <div><p className="text-muted text-xs">Valor final</p><p className="font-bold"><Money value={simulation.data.finalBalance} /></p></div>
            <div><p className="text-muted text-xs">Total aportado</p><p className="font-bold"><Money value={simulation.data.totalInvested} /></p></div>
            <div><p className="text-muted text-xs">Valor real (IPCA)</p><p className="font-bold"><Money value={simulation.data.finalRealBalance} /></p></div>
          </div>
        )}
      </Card>

      {/* Catálogo de metas */}
      <Modal open={picking} title="Escolha suas metas" onClose={() => setPicking(false)}>
        <p className="text-sm text-muted mb-3">
          Selecione um ou mais objetivos. Você pode ajustar o valor de cada um.
        </p>
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
          {PRESETS.map((p) => {
            const isOn = Boolean(selected[p.id]);
            return (
              <button
                key={p.id}
                type="button"
                onClick={() => toggle(p)}
                aria-pressed={isOn}
                className={clsx(isOn ? 'tile-selected' : 'tile')}
              >
                <p.icon size={26} aria-hidden className="text-accent" />
                <span className="text-[11px] font-semibold leading-tight">{p.name}</span>
                <span className="text-[10px] text-muted leading-tight">{p.hint}</span>
              </button>
            );
          })}
        </div>

        {selectedCount > 0 && (
          <div className="mt-4 space-y-2">
            <p className="text-xs font-semibold text-muted uppercase">Valor-alvo de cada meta</p>
            {PRESETS.filter((p) => selected[p.id]).map((p) => (
              <div key={p.id} className="flex items-center gap-2">
                <p.icon size={18} aria-hidden className="shrink-0 text-accent" />
                <span className="flex-1 text-sm truncate">{p.name}</span>
                <Input
                  type="number"
                  min="1"
                  step="100"
                  className="w-32"
                  value={selected[p.id]}
                  onChange={(e) => setSelected({ ...selected, [p.id]: e.target.value })}
                  aria-label={`Valor-alvo de ${p.name}`}
                />
              </div>
            ))}
            <p className="text-sm text-right text-muted">
              Total: <strong className="text-content"><Money value={totalSelected} /></strong>
            </p>
          </div>
        )}

        <button
          className="btn-primary w-full mt-4"
          disabled={selectedCount === 0 || createMany.isPending}
          onClick={() => createMany.mutate()}
        >
          {createMany.isPending
            ? 'Criando…'
            : selectedCount === 0
              ? 'Selecione ao menos uma meta'
              : `Criar ${selectedCount} meta${selectedCount === 1 ? '' : 's'} · +${selectedCount * 30} XP`}
        </button>
      </Modal>

      <Modal open={!!contributeTo} title="Registrar aporte" onClose={() => setContributeTo(null)}>
        <ContributionForm onSubmit={(amount) => contributeTo && contribute.mutate({ id: contributeTo, amount })} />
      </Modal>
    </div>
  );
}

function ContributionForm({ onSubmit }: { onSubmit: (amount: string) => void }) {
  const [amount, setAmount] = useState('');
  return (
    <form className="space-y-4" onSubmit={(e) => { e.preventDefault(); onSubmit(amount); }}>
      <Field label="Valor do aporte">
        <Input type="number" step="0.01" min="0.01" required value={amount} onChange={(e) => setAmount(e.target.value)} />
      </Field>
      <p className="text-xs text-muted">Data do aporte: {formatDate(todayInput())}</p>
      <button className="btn-primary w-full">Aportar · +25 XP</button>
    </form>
  );
}
