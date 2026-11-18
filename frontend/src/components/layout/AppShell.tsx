import { NavLink, Outlet } from 'react-router-dom';
import {
  ArrowDownToLine,
  CalendarDays,
  Coins,
  Flag,
  Flame,
  Home,
  MessageSquare,
  Moon,
  ReceiptText,
  Settings,
  Sun,
  Target,
  TrendingUp,
} from 'lucide-react';
import { useAuth } from '../../contexts/AuthContext';
import { useTheme } from '../../contexts/ThemeContext';
import { useGame } from '../../hooks/useGame';
import { clsx } from 'clsx';

const NAV = [
  { to: '/', label: 'Início', icon: Home },
  { to: '/chat', label: 'Copiloto', icon: MessageSquare },
  { to: '/transactions', label: 'Entradas', icon: ArrowDownToLine },
  { to: '/planning', label: 'Planejamento', icon: CalendarDays },
  { to: '/goals', label: 'Metas', icon: Flag },
  { to: '/budget', label: 'Orçamento', icon: Target },
  { to: '/portfolio', label: 'Carteira', icon: TrendingUp },
  { to: '/earnings', label: 'Proventos', icon: Coins },
  { to: '/reports', label: 'Relatórios', icon: ReceiptText },
  { to: '/settings', label: 'Ajustes', icon: Settings },
];

export function AppShell() {
  const { profile, logout } = useAuth();
  const { theme, toggle } = useTheme();
  const { level, xp, streak } = useGame();

  return (
    <div className="flex min-h-screen">
      {/* Sidebar */}
      <aside
        className="hidden md:flex flex-col border-r border-line bg-card shrink-0"
        style={{ width: 'var(--sidebar-w)' }}
      >
        <div
          className="flex items-center gap-2.5 px-4 border-b border-line"
          style={{ height: 'var(--header-h)' }}
        >
          <span className="w-9 h-9 rounded-chip bg-accent text-accent-ink flex items-center justify-center font-bold text-sm">
            IA
          </span>
          <div className="min-w-0">
            <p className="display text-[15px] leading-none">InvestIA</p>
            <p className="text-[11px] text-muted mt-1 truncate">Finanças e investimentos</p>
          </div>
        </div>

        <nav className="flex-1 p-3 space-y-1 overflow-y-auto">
          {NAV.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/'}
              className={({ isActive }) => clsx('nav-item', isActive && 'nav-item-active')}
            >
              <item.icon size={16} aria-hidden className="shrink-0" />
              {item.label}
            </NavLink>
          ))}
        </nav>

        {/* Progresso */}
        <div className="p-3 border-t border-line">
          <div className="rounded-chip bg-card-2 p-3">
            <div className="flex items-center justify-between mb-2">
              <span className="text-[11px] font-semibold text-muted uppercase tracking-wider">
                Nível {level.level}
              </span>
              {streak > 0 && (
                <span className="badge-warning" title={`${streak} dia(s) seguidos`}>
                  <Flame size={12} aria-hidden /> <span className="num">{streak}</span>
                </span>
              )}
            </div>
            <div className="xp-track">
              <div className="xp-fill" style={{ width: `${level.progressPct}%` }} />
            </div>
            <p className="text-[11px] text-muted mt-2">
              <span className="num text-content">{level.xpIntoLevel}</span> /{' '}
              <span className="num">{level.xpForNext}</span> XP · {level.title}
            </p>
            <p className="text-[11px] text-muted">
              Total <span className="num text-content">{xp}</span> XP
            </p>
          </div>
        </div>
      </aside>

      {/* Conteúdo */}
      <div className="flex-1 flex flex-col min-w-0">
        <header
          className="flex items-center justify-between gap-2 px-4 md:px-6 border-b border-line bg-card sticky top-0 z-30"
          style={{ height: 'var(--header-h)' }}
        >
          <div className="md:hidden display text-[15px]">InvestIA</div>
          <div className="hidden md:block min-w-0">
            <p className="eyebrow">Olá</p>
            <p className="text-sm font-semibold truncate">{profile?.fullName ?? 'Investidor'}</p>
          </div>
          <div className="flex-1" />
          <span className="hidden sm:inline-flex badge-accent">
            Nível <span className="num">{level.level}</span>
          </span>
          <button className="btn-ghost px-3" onClick={toggle} aria-label="Alternar tema">
            {theme === 'dark' ? <Sun size={16} aria-hidden /> : <Moon size={16} aria-hidden />}
          </button>
          <button className="btn-ghost" onClick={logout}>Sair</button>
        </header>

        {/* Navegação mobile */}
        <nav className="md:hidden flex overflow-x-auto border-b border-line bg-card px-2 gap-1">
          {NAV.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/'}
              className={({ isActive }) =>
                clsx(
                  'px-3 py-2 text-xs whitespace-nowrap rounded-chip my-1',
                  isActive ? 'bg-active text-accent font-semibold' : 'text-muted',
                )
              }
            >
              <item.icon size={14} aria-hidden className="inline" /> {item.label}
            </NavLink>
          ))}
        </nav>

        <main className="flex-1 p-4 md:p-6 max-w-7xl w-full mx-auto">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
