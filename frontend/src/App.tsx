import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './contexts/AuthContext';
import { AppShell } from './components/layout/AppShell';
import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';

// Páginas carregadas sob demanda: reduz o bundle inicial
const DashboardPage = lazy(() => import('./pages/DashboardPage').then((m) => ({ default: m.DashboardPage })));
const TransactionsPage = lazy(() => import('./pages/TransactionsPage').then((m) => ({ default: m.TransactionsPage })));
const PlanningPage = lazy(() => import('./pages/PlanningPage').then((m) => ({ default: m.PlanningPage })));
const BudgetPage = lazy(() => import('./pages/BudgetPage').then((m) => ({ default: m.BudgetPage })));
const PortfolioPage = lazy(() => import('./pages/PortfolioPage').then((m) => ({ default: m.PortfolioPage })));
const EarningsPage = lazy(() => import('./pages/EarningsPage').then((m) => ({ default: m.EarningsPage })));
const GoalsPage = lazy(() => import('./pages/GoalsPage').then((m) => ({ default: m.GoalsPage })));
const ReportsPage = lazy(() => import('./pages/ReportsPage').then((m) => ({ default: m.ReportsPage })));
const SettingsPage = lazy(() => import('./pages/SettingsPage').then((m) => ({ default: m.SettingsPage })));
const ChatPage = lazy(() => import('./pages/ChatPage').then((m) => ({ default: m.ChatPage })));

function PageFallback() {
  return <div className="animate-pulse rounded-lg bg-card-2 h-64" aria-busy="true" />;
}

function Protected({ children }: { children: React.ReactNode }) {
  const { token } = useAuth();
  if (!token) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

function PublicOnly({ children }: { children: React.ReactNode }) {
  const { token } = useAuth();
  if (token) return <Navigate to="/" replace />;
  return <>{children}</>;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<PublicOnly><LoginPage /></PublicOnly>} />
      <Route path="/register" element={<PublicOnly><RegisterPage /></PublicOnly>} />
      <Route element={<Protected><AppShell /></Protected>}>
        <Route index element={<Suspense fallback={<PageFallback />}><DashboardPage /></Suspense>} />
        <Route path="/chat" element={<Suspense fallback={<PageFallback />}><ChatPage /></Suspense>} />
        <Route path="/transactions" element={<Suspense fallback={<PageFallback />}><TransactionsPage /></Suspense>} />
        <Route path="/planning" element={<Suspense fallback={<PageFallback />}><PlanningPage /></Suspense>} />
        <Route path="/budget" element={<Suspense fallback={<PageFallback />}><BudgetPage /></Suspense>} />
        <Route path="/portfolio" element={<Suspense fallback={<PageFallback />}><PortfolioPage /></Suspense>} />
        <Route path="/earnings" element={<Suspense fallback={<PageFallback />}><EarningsPage /></Suspense>} />
        <Route path="/goals" element={<Suspense fallback={<PageFallback />}><GoalsPage /></Suspense>} />
        <Route path="/reports" element={<Suspense fallback={<PageFallback />}><ReportsPage /></Suspense>} />
        <Route path="/settings" element={<Suspense fallback={<PageFallback />}><SettingsPage /></Suspense>} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
