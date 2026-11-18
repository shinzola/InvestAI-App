/**
 * Gamificação do InvestIA.
 *
 * Tudo é derivado de dados reais da API — não existe XP "de enfeite":
 * cada ponto corresponde a uma ação registrada pelo usuário.
 */

import {
  Coins,
  Flag,
  Flame,
  FolderOpen,
  Gem,
  Sprout,
  Target,
  TrendingUp,
  Trophy,
  type LucideIcon,
} from 'lucide-react';

export interface GameSnapshot {
  transactionCount: number;
  transactionDays: string[];
  goalCount: number;
  completedGoals: number;
  contributionCount: number;
  operationCount: number;
  dividendCount: number;
  hasBudget: boolean;
  healthScore: number;
  lastTransactionDate?: string | null;
}

export interface Level {
  level: number;
  title: string;
  xp: number;
  xpIntoLevel: number;
  xpForNext: number;
  progressPct: number;
}

export interface Badge {
  id: string;
  label: string;
  description: string;
  icon: LucideIcon;
  unlocked: boolean;
}

export interface Mission {
  id: string;
  label: string;
  description: string;
  xp: number;
  done: boolean;
  to?: string;
}

const LEVEL_TITLES = [
  'Aprendiz',
  'Poupador',
  'Organizador',
  'Investidor',
  'Estrategista',
  'Gestor',
  'Especialista',
  'Mestre',
  'Lenda',
];

/** XP acumulado a partir das ações registradas. */
export function computeXp(s: GameSnapshot): number {
  return (
    Math.min(s.transactionCount, 100) * 10 +
    s.goalCount * 30 +
    s.completedGoals * 120 +
    s.contributionCount * 25 +
    s.operationCount * 20 +
    s.dividendCount * 15 +
    (s.hasBudget ? 40 : 0) +
    Math.round(s.healthScore)
  );
}

/** Cada nível custa 100 × nível de XP. */
export function computeLevel(xp: number): Level {
  let level = 1;
  let remaining = xp;
  while (remaining >= level * 100 && level < 99) {
    remaining -= level * 100;
    level += 1;
  }
  const xpForNext = level * 100;
  return {
    level,
    title: LEVEL_TITLES[Math.min(level - 1, LEVEL_TITLES.length - 1)],
    xp,
    xpIntoLevel: remaining,
    xpForNext,
    progressPct: Math.round((remaining / xpForNext) * 100),
  };
}

/** Dias consecutivos com transação, contando de hoje para trás. */
export function computeStreak(days: string[], today = new Date()): number {
  const set = new Set(days.map((d) => d.slice(0, 10)));
  let streak = 0;
  const cursor = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  // se hoje ainda não teve lançamento, a sequência pode começar ontem
  if (!set.has(toIso(cursor))) cursor.setDate(cursor.getDate() - 1);
  while (set.has(toIso(cursor))) {
    streak += 1;
    cursor.setDate(cursor.getDate() - 1);
  }
  return streak;
}

function toIso(d: Date): string {
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${m}-${day}`;
}

export function computeBadges(s: GameSnapshot, streak: number): Badge[] {
  return [
    {
      id: 'first-step',
      label: 'Primeiro passo',
      description: 'Registrou a primeira transação',
      icon: Sprout,
      unlocked: s.transactionCount >= 1,
    },
    {
      id: 'organized',
      label: 'Organizado',
      description: '10 transações registradas',
      icon: FolderOpen,
      unlocked: s.transactionCount >= 10,
    },
    {
      id: 'budgeter',
      label: 'Planejador',
      description: 'Definiu um orçamento mensal',
      icon: Target,
      unlocked: s.hasBudget,
    },
    {
      id: 'investor',
      label: 'Investidor',
      description: 'Registrou a primeira operação',
      icon: TrendingUp,
      unlocked: s.operationCount >= 1,
    },
    {
      id: 'passive-income',
      label: 'Renda passiva',
      description: 'Recebeu o primeiro provento',
      icon: Coins,
      unlocked: s.dividendCount >= 1,
    },
    {
      id: 'goal-setter',
      label: 'Sonhador',
      description: 'Criou a primeira meta',
      icon: Flag,
      unlocked: s.goalCount >= 1,
    },
    {
      id: 'goal-crusher',
      label: 'Conquistador',
      description: 'Concluiu uma meta',
      icon: Trophy,
      unlocked: s.completedGoals >= 1,
    },
    {
      id: 'streak-3',
      label: 'Constância',
      description: '3 dias seguidos lançando',
      icon: Flame,
      unlocked: streak >= 3,
    },
    {
      id: 'healthy',
      label: 'Saúde de ferro',
      description: 'Score de saúde acima de 80',
      icon: Gem,
      unlocked: s.healthScore >= 80,
    },
  ];
}

export function computeMissions(s: GameSnapshot, todayIso: string): Mission[] {
  const launchedToday = s.transactionDays.some((d) => d.slice(0, 10) === todayIso);
  return [
    {
      id: 'daily-log',
      label: 'Lance uma transação hoje',
      description: 'Mantenha a sequência viva',
      xp: 10,
      done: launchedToday,
      to: '/transactions',
    },
    {
      id: 'budget',
      label: 'Defina o orçamento do mês',
      description: 'Limites por categoria evitam surpresas',
      xp: 40,
      done: s.hasBudget,
      to: '/budget',
    },
    {
      id: 'goal',
      label: 'Escolha uma meta',
      description: 'Todo plano começa com um alvo',
      xp: 30,
      done: s.goalCount >= 1,
      to: '/goals',
    },
    {
      id: 'invest',
      label: 'Registre um aporte em ativos',
      description: 'Coloque o dinheiro para trabalhar',
      xp: 20,
      done: s.operationCount >= 1,
      to: '/portfolio',
    },
  ];
}
