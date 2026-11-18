export interface TransactionPage {
  content: Transaction[];
  page: number;
  size: number;
  totalElements: number;
}

export interface TokenPair {
  accessToken: string;
  refreshToken: string;
  expiresInMinutes: number;
}

export type InvestorProfile = 'CONSERVADOR' | 'MODERADO' | 'ARROJADO';

export interface Profile {
  email: string;
  fullName: string;
  investorProfile: InvestorProfile;
  baseCurrency: string;
  timezone: string;
  desiredMonthlyIncome?: number;
}

export interface Account {
  id: string;
  name: string;
  type: string;
  institution?: string | null;
  initialBalance: number;
  balance: number;
  color?: string | null;
  archived: boolean;
  card?: CreditCard | null;
}

export interface Category {
  id: string;
  name: string;
  kind: 'INCOME' | 'EXPENSE';
  parentId?: string | null;
  color?: string | null;
  icon?: string | null;
}

export interface Transaction {
  id: string;
  type: 'INCOME' | 'EXPENSE' | 'TRANSFER';
  accountId: string;
  destinationAccountId?: string | null;
  categoryId?: string | null;
  subcategoryId?: string | null;
  amount: number;
  date: string;
  description: string;
  notes?: string | null;
  tags: string[];
  status: string;
  source: string;
  incomeKind?: string | null;
  installmentNumber?: number | null;
  installmentTotal?: number | null;
}

export interface BudgetItem {
  categoryId: string;
  categoryName: string;
  limitAmount: number;
  spent: number;
  remaining: number;
  spentPct: number;
  alertThresholdPct: number;
  level: string;
  atypical: boolean;
}

export interface Budget {
  month: string;
  totalLimit: number;
  totalSpent: number;
  totalSpentPct: number;
  items: BudgetItem[];
  previousMonthSpent: number;
  monthOverMonthPct: number;
}

export interface Asset {
  id: string;
  ticker: string;
  name: string;
  type: string;
  sector?: string | null;
}

export interface Position {
  assetId: string;
  ticker: string;
  name: string;
  type: string;
  quantity: number;
  averagePrice: number;
  currentPrice: number;
  marketValue: number;
  invested: number;
  unrealizedResult: number;
  unrealizedPct: number;
  realizedResult: number;
}

export interface Allocation {
  assetClass: string;
  currentPct: number;
  targetPct: number;
}

export interface RebalanceItem {
  assetClass: string;
  action: 'BUY' | 'SELL' | 'HOLD';
  amount: number;
}

export interface Dividend {
  id: string;
  assetId: string;
  ticker: string;
  type: string;
  amount: number;
  withholdingTax: number;
  netAmount: number;
  date: string;
}

export interface EarningsSummary {
  totalLast12m: number;
  averageLast12m: number;
  projectionNext12m: number;
  desiredMonthlyIncome: number;
  missingForDesiredIncome: number;
  coveragePct: number;
}

export interface Goal {
  id: string;
  name: string;
  kind: string;
  targetAmount: number;
  targetDate?: string | null;
  monthlyContribution: number;
  status: string;
  saved: number;
  progressPct: number;
  requiredMonthlyContribution: number;
}

export interface SimulationPoint {
  month: number;
  balance: number;
  invested: number;
  realBalance: number;
}

export interface Simulation {
  finalBalance: number;
  totalInvested: number;
  totalInterest: number;
  finalRealBalance: number;
  series: SimulationPoint[];
}

export interface Summary {
  income: number;
  expense: number;
  balance: number;
  expenseChangePct: number;
  incomeChangePct: number;
  cashBalance: number;
  portfolioValue: number;
  netWorth: number;
  budgetUsedPct: number;
}

export interface HealthScore {
  score: number;
  classification: string;
  breakdown: Record<string, number>;
  savingsRatePct: number;
  reserveMonthsPct: number;
  maxConcentrationPct: number;
}

export interface DarfItem {
  kind: string;
  darfCode: string;
  description: string;
  taxableAmount: number;
  taxAmount: number;
  status: string;
}

export interface Darf {
  month: string;
  totalTaxDue: number;
  items: DarfItem[];
}

export interface Insight {
  kind: string;
  answer: string;
  createdAt: string;
}

export interface PlannedExpense {
  id: string;
  description: string;
  amount: number;
  categoryId?: string | null;
  dueDate: string;
  kind: 'FIXED' | 'VARIABLE' | 'CREDIT_CARD';
  status: 'PLANNED' | 'PAID';
  notes?: string | null;
  cardAccountId?: string | null;
  invoiceClosingDate?: string | null;
  invoiceDueDate?: string | null;
}

export interface PlanningSummary {
  totalFixed: number;
  totalVariable: number;
  totalCreditCard: number;
  total: number;
  paid: number;
  pending: number;
  count: number;
  paidCount: number;
  monthIncome: number;
  availableToContribute: number;
}

export interface CreditCard {
  id: string;
  accountId: string;
  brand?: string | null;
  closingDay: number;
  dueDay: number;
  limitTotal: number;
  limitUsed: number;
  limitAvailable: number;
}
