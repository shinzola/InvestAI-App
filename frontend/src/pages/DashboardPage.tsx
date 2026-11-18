import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Check, Flame } from 'lucide-react';
import { analyticsApi, aiApi } from '../services/endpoints';
import { Card, StatCard, Skeleton, EmptyState } from '../components/ui/Card';
import { MeterBar, ProgressRing, XpBar } from '../components/ui/Progress';
import { useGame } from '../hooks/useGame';
import { useAuth } from '../contexts/AuthContext';
import { clsx } from 'clsx';

const brl = (v: number) => v.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });

const SCORE_LABELS: Record<string, string> = {
  poupanca: 'Poupança',
  reserva: 'Reserva',
  diversificacao: 'Diversificação',
  adesaoOrcamento: 'Adesão ao orçamento',
};

const CLASSIFICATION_LABELS: Record<string, string> = {
  SAUDAVEL: 'Saudável',
  ATENCAO: 'Atenção',
  CRITICO: 'Crítico',
};

export function DashboardPage() {
  const now = new Date();
  const year = now.getFullYear();
  const month = now.getMonth() + 1;
  const { profile } = useAuth();
  const game = useGame();

  const summary = useQuery({
    queryKey: ['summary', year, month],
    queryFn: () => analyticsApi.summary(year, month).then((r) => r.data),
  });
  const health = useQuery({
    queryKey: ['health', year, month],
    queryFn: () => analyticsApi.health(year, month).then((r) => r.data),
  });
  const insights = useQuery({
    queryKey: ['insights'],
    queryFn: () => aiApi.insights().then((r) => r.data),
  });

  const s = summary.data;
  const firstName = profile?.fullName?.split(' ')[0] ?? 'investidor';
  const doneMissions = game.missions.filter((m) => m.done).length;
  const unlockedBadges = game.badges.filter((b) => b.unlocked);

  return (
    <div className="space-y-5">
      {/* Hero gamificado */}
      <section className="hero animate-pop">
        <div className="flex flex-col sm:flex-row sm:items-center gap-5">
          <div className="shrink-0 rounded-full bg-white/15 p-1">
            <ProgressRing
              value={game.level.progressPct}
              size={104}
              stroke={10}
              label={<span className="text-white text-xl">{game.level.level}</span>}
              sublabel="nível"
            />
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-xs uppercase tracking-wider text-white/70">Olá, {firstName}</p>
            <h1 className="display text-[26px] sm:text-[30px] mt-1">
              {firstName}
            </h1>
            <p className="text-sm text-white/80 mt-1">
              {doneMissions} de {game.missions.length} missões concluídas
              {game.streak > 0 && (
                <>
                  {' · '}
                  <Flame size={13} aria-hidden className="inline align-[-2px]" />{' '}
                  {game.streak} dia{game.streak === 1 ? '' : 's'} seguidos
                </>
              )}
            </p>
            <div className="mt-3">
              <XpBar pct={game.level.progressPct} />
              <p className="text-[11px] text-white/70 mt-1">
                Faltam {game.level.xpForNext - game.level.xpIntoLevel} XP para o nível {game.level.level + 1}
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* Números do mês */}
      <div className="grid gap-3 grid-cols-2 lg:grid-cols-4">
        <StatCard label="Receitas" value={brl(s?.income ?? 0)} tone="positive" />
        <StatCard label="Despesas" value={brl(s?.expense ?? 0)} tone="negative" />
        <StatCard label="Saldo do mês" value={brl(s?.balance ?? 0)} tone={(s?.balance ?? 0) >= 0 ? 'positive' : 'negative'} />
        <StatCard label="Patrimônio" value={brl(s?.netWorth ?? 0)} hint={`Carteira: ${brl(s?.portfolioValue ?? 0)}`} />
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        {/* Missões */}
        <Card title="Missões de hoje" className="lg:col-span-2">
          <div className="space-y-2">
            {game.missions.map((m) => (
              <Link
                key={m.id}
                to={m.to ?? '/'}
                className={clsx(m.done ? 'mission-done' : 'mission', 'hover:border-accent')}
              >
                <span
                  className={clsx(
                    'mt-0.5 w-6 h-6 shrink-0 rounded-full flex items-center justify-center text-xs font-bold',
                    m.done ? 'bg-success text-white' : 'border border-line text-muted',
                  )}
                  aria-hidden
                >
                  {m.done && <Check size={13} aria-hidden />}
                </span>
                <span className="flex-1 min-w-0">
                  <span className={clsx('block text-sm font-medium', m.done && 'line-through opacity-70')}>
                    {m.label}
                  </span>
                  <span className="block text-xs text-muted">{m.description}</span>
                </span>
                <span className="badge-accent shrink-0">+{m.xp} XP</span>
              </Link>
            ))}
          </div>
        </Card>

        {/* Saúde financeira */}
        <Card title="Saúde financeira">
          {health.data ? (
            <div className="flex flex-col items-center gap-3">
              <ProgressRing
                value={health.data.score}
                size={112}
                stroke={11}
                label={health.data.score}
                sublabel="de 100"
                tone={health.data.score >= 80 ? 'success' : health.data.score >= 60 ? 'warning' : 'accent'}
              />
              <span className="badge-accent">
                {CLASSIFICATION_LABELS[health.data.classification] ?? health.data.classification}
              </span>
              <div className="w-full space-y-1.5 text-xs">
                {Object.entries(health.data.breakdown).map(([k, v]) => (
                  <div key={k} className="flex justify-between">
                    <span className="text-muted">{SCORE_LABELS[k] ?? k}</span>
                    <span className="font-semibold">{v} pts</span>
                  </div>
                ))}
              </div>
            </div>
          ) : (
            <Skeleton className="h-44" />
          )}
        </Card>
      </div>

      {/* Conquistas */}
      <Card title={`Conquistas · ${unlockedBadges.length}/${game.badges.length}`}>
        <div className="grid grid-cols-3 sm:grid-cols-5 lg:grid-cols-9 gap-2">
          {game.badges.map((b) => (
            <div
              key={b.id}
              title={b.description}
              className={clsx(
                'flex flex-col items-center gap-1 rounded-xl border p-2 text-center transition-all',
                b.unlocked
                  ? 'border-accent bg-accent-soft'
                  : 'border-line bg-card opacity-45 grayscale',
              )}
            >
              <b.icon size={20} aria-hidden />
              <span className="text-[10px] font-medium leading-tight">{b.label}</span>
            </div>
          ))}
        </div>
      </Card>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card title="Orçamento do mês">
          <div className="flex items-center justify-between text-sm mb-2">
            <span className="text-muted">Utilizado</span>
            <span className="font-semibold">{s?.budgetUsedPct ?? 0}%</span>
          </div>
          <MeterBar pct={s?.budgetUsedPct ?? 0} />
          <Link to="/budget" className="btn-soft w-full mt-3">Ajustar orçamento</Link>
        </Card>

        <Card title="Copiloto — insights">
          {insights.data?.length ? (
            <ul className="space-y-2">
              {insights.data.slice(0, 3).map((i, idx) => (
                <li key={idx} className="text-sm border-l-2 border-accent pl-3">
                  <p>{i.answer}</p>
                  <p className="text-xs text-muted">{new Date(i.createdAt).toLocaleDateString('pt-BR')}</p>
                </li>
              ))}
            </ul>
          ) : (
            <EmptyState title="Nenhum insight ainda" subtitle="Lance transações para o copiloto analisar seus números." />
          )}
        </Card>
      </div>
    </div>
  );
}
