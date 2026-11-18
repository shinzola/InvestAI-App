import { useQuery } from '@tanstack/react-query';
import { analyticsApi, budgetsApi, earningsApi, goalsApi, portfolioApi, transactionsApi } from '../services/endpoints';
import {
  computeBadges, computeLevel, computeMissions, computeStreak, computeXp,
  type Badge, type Level, type Mission,
} from '../lib/gamification';
import { todayInput } from '../utils/format';

export interface GameState {
  xp: number;
  level: Level;
  streak: number;
  badges: Badge[];
  missions: Mission[];
  loading: boolean;
}

/**
 * Deriva o estado de gamificação a partir dos dados reais do usuário.
 * Uma única rodada de consultas alimenta nível, sequência, badges e missões.
 */
export function useGame(): GameState {
  const now = new Date();
  const year = now.getFullYear();
  const month = now.getMonth() + 1;

  const transactions = useQuery({
    queryKey: ['game', 'transactions'],
    queryFn: () => transactionsApi.list(0, 200).then((r) => r.data),
  });
  const goals = useQuery({ queryKey: ['game', 'goals'], queryFn: () => goalsApi.list().then((r) => r.data) });
  const positions = useQuery({ queryKey: ['game', 'positions'], queryFn: () => portfolioApi.positions().then((r) => r.data) });
  const dividends = useQuery({ queryKey: ['game', 'dividends'], queryFn: () => earningsApi.list().then((r) => r.data) });
  const budget = useQuery({
    queryKey: ['game', 'budget', year, month],
    queryFn: () => budgetsApi.get(year, month).then((r) => r.data),
  });
  const health = useQuery({
    queryKey: ['game', 'health', year, month],
    queryFn: () => analyticsApi.health(year, month).then((r) => r.data),
  });

  const loading = transactions.isLoading || goals.isLoading || health.isLoading;

  const txList = transactions.data?.content ?? [];
  const goalList = goals.data ?? [];
  const days = txList.map((t) => t.date);
  const today = todayInput();

  const snapshot = {
    transactionCount: transactions.data?.totalElements ?? txList.length,
    transactionDays: days,
    goalCount: goalList.length,
    completedGoals: goalList.filter((g) => g.progressPct >= 100).length,
    contributionCount: goalList.filter((g) => g.saved > 0).length,
    operationCount: (positions.data ?? []).length,
    dividendCount: (dividends.data ?? []).length,
    hasBudget: (budget.data?.items?.length ?? 0) > 0,
    healthScore: health.data?.score ?? 0,
    lastTransactionDate: days[0] ?? null,
  };

  const xp = computeXp(snapshot);
  const streak = computeStreak(days);

  return {
    xp,
    level: computeLevel(xp),
    streak,
    badges: computeBadges(snapshot, streak),
    missions: computeMissions(snapshot, today),
    loading,
  };
}
