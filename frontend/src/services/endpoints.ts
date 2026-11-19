import { api } from './api';
import type {
  Account, Allocation, Asset, Budget, Category, CreditCard, Darf, Dividend, EarningsSummary, Goal,
  HealthScore, Insight, PlannedExpense, PlanningSummary, Position, Profile, RebalanceItem,
  Simulation, Summary, TokenPair, Transaction, TransactionPage,
} from '../types';

export const authApi = {
  login: (body: { email: string; password: string }) => api.post<TokenPair>('/auth/login', body),
  register: (body: { email: string; password: string; fullName: string; acceptTerms: boolean }) =>
    api.post('/auth/register', body),
  refresh: (refreshToken: string) => api.post<TokenPair>('/auth/refresh', { refreshToken }),
  logout: (refreshToken: string) => api.post('/auth/logout', { refreshToken }),
};

export const usersApi = {
  me: () => api.get<Profile>('/users/me'),
  updateProfile: (body: Record<string, unknown>) => api.put<Profile>('/users/me', body),
  changePassword: (body: { currentPassword: string; newPassword: string }) =>
    api.post('/users/me/password', body),
  exportData: () => api.get('/users/me/export'),
  deleteAccount: () => api.delete('/users/me'),
};

export const accountsApi = {
  list: () => api.get<Account[]>('/accounts'),
  create: (body: Record<string, unknown>) => api.post<Account>('/accounts', body),
  update: (id: string, body: Record<string, unknown>) => api.put<Account>(`/accounts/${id}`, body),
  configureCard: (body: Record<string, unknown>) => api.post<CreditCard>('/accounts/credit-card', body),
  cards: () => api.get<CreditCard[]>('/accounts/credit-card'),
};

export const categoriesApi = {
  list: () => api.get<Category[]>('/categories'),
  create: (body: Record<string, unknown>) => api.post<Category>('/categories', body),
};

export const transactionsApi = {
  list: (page = 0, size = 20) =>
    api.get<TransactionPage>('/transactions', { params: { page, size } }),
  create: (body: Record<string, unknown>) => api.post<Transaction>('/transactions', body),
  remove: (id: string) => api.delete(`/transactions/${id}`),
};

export const planningApi = {
  list: (year: number, month: number) =>
    api.get<PlannedExpense[]>('/planning', { params: { year, month } }),
  summary: (year: number, month: number) =>
    api.get<PlanningSummary>('/planning/summary', { params: { year, month } }),
  create: (body: Record<string, unknown>) => api.post<PlannedExpense>('/planning', body),
  update: (id: string, body: Record<string, unknown>) => api.put<PlannedExpense>(`/planning/${id}`, body),
  togglePaid: (id: string) => api.patch<PlannedExpense>(`/planning/${id}/paid`),
  remove: (id: string) => api.delete(`/planning/${id}`),
};

export const budgetsApi = {  get: (year: number, month: number) => api.get<Budget>(`/budgets/${year}/${month}`),
  save: (year: number, month: number, items: { categoryId: string; limitAmount: string }[]) =>
    api.put<Budget>(`/budgets/${year}/${month}`, { items }),
};

export const portfolioApi = {
  assets: () => api.get<Asset[]>('/portfolio/assets'),
  createAsset: (body: Record<string, unknown>) => api.post<Asset>('/portfolio/assets', body),
  positions: () => api.get<Position[]>('/portfolio/positions'),
  operations: () => api.get('/portfolio/operations'),
  createOperation: (body: Record<string, unknown>) => api.post('/portfolio/operations', body),
  allocation: () => api.get<Allocation[]>('/analytics/allocation'),
  rebalance: () => api.get<RebalanceItem[]>('/analytics/rebalancing'),
};

export const earningsApi = {
  list: () => api.get<Dividend[]>('/earnings'),
  create: (body: Record<string, unknown>) => api.post<Dividend>('/earnings', body),
  summary: () => api.get<EarningsSummary>('/earnings/summary'),
  monthly: () => api.get<{ month: string; net: number; gross: number }[]>('/earnings/monthly'),
};

export const goalsApi = {
  list: () => api.get<Goal[]>('/goals'),
  create: (body: Record<string, unknown>) => api.post<Goal>('/goals', body),
  contribute: (id: string, body: { amount: string; date: string; note?: string }) =>
    api.post(`/goals/${id}/contributions`, body),
  simulate: (body: Record<string, unknown>) => api.post<Simulation>('/goals/simulate', body),
};

export const analyticsApi = {
  summary: (year: number, month: number) => api.get<Summary>('/analytics/summary', { params: { year, month } }),
  health: (year: number, month: number) => api.get<HealthScore>('/analytics/health', { params: { year, month } }),
};

export const taxApi = {
  darf: (year: number, month: number) => api.get<Darf>('/tax/darf', { params: { year, month } }),
};

export const aiApi = {
  insights: () => api.get<Insight[]>('/ai/insights'),
  ask: (question: string) => api.post<{ answer: string }>('/ai/ask', { question }),
};

export interface ChatAction {
  tool: string;
  summary: string;
  data: Record<string, unknown>;
}

export interface ChatEntry {
  id: string;
  role: 'USER' | 'ASSISTANT';
  content: string;
  actions: ChatAction[];
  createdAt: string;
}

export interface ChatReply {
  answer: string;
  actions: ChatAction[];
  usedLlm: boolean;
}

export interface InvoiceImport {
  fileName: string;
  extracted: number;
  created: number;
  skipped: number;
  items: { id: string; description: string; amount: number; dueDate: string }[];
  preview: string;
}

export const chatApi = {
  send: (message: string) => api.post<ChatReply>('/ai/chat', { message }, { timeout: 120_000 }),
  history: () => api.get<ChatEntry[]>('/ai/chat/history'),
  clear: () => api.delete('/ai/chat/history'),
  importInvoice: (file: File, cardAccountId?: string, year?: number, month?: number) => {
    const form = new FormData();
    form.append('file', file);
    if (cardAccountId) form.append('cardAccountId', cardAccountId);
    if (year) form.append('year', String(year));
    if (month) form.append('month', String(month));
    return api.post<InvoiceImport>('/ai/chat/invoice', form);
  },
};

export interface AiSettings {
  baseUrl: string;
  model: string;
  apiKeyMasked: string;
  configured: boolean;
  source: 'user' | 'environment';
}

export interface AiTestResult {
  ok: boolean;
  message: string;
  sample: string | null;
}

export interface AiPreset {
  id: string;
  label: string;
  baseUrl: string;
  model: string;
  hint: string;
}

export interface AiModels {
  baseUrl: string;
  model: string;
  models: string[];
  discovered: boolean;
}

export const aiSettingsApi = {
  get: () => api.get<AiSettings>('/ai/settings'),
  save: (payload: { baseUrl: string; model: string; apiKey?: string }) =>
    api.put<AiSettings>('/ai/settings', payload),
  clear: () => api.delete('/ai/settings'),
  test: () => api.post<AiTestResult>('/ai/settings/test'),
  models: () => api.get<AiModels>('/ai/settings/models'),
  presets: () => api.get<AiPreset[]>('/ai/settings/presets'),
};
