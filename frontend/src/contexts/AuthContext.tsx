import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import type { Profile } from '../types';
import { authApi, usersApi } from '../services/endpoints';

interface AuthContextValue {
  token: string | null;
  profile: Profile | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (input: { email: string; password: string; fullName: string; acceptTerms: boolean }) => Promise<void>;
  logout: () => void;
  refreshProfile: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue>(null!);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(() => localStorage.getItem('investia:access'));
  const [profile, setProfile] = useState<Profile | null>(null);
  const [loading, setLoading] = useState(false);

  const refreshProfile = useCallback(async () => {
    try {
      const { data } = await usersApi.me();
      setProfile(data);
    } catch {
      setProfile(null);
    }
  }, []);

  useEffect(() => {
    if (token) void refreshProfile();
  }, [token, refreshProfile]);

  const login = useCallback(async (email: string, password: string) => {
    setLoading(true);
    try {
      const { data } = await authApi.login({ email, password });
      localStorage.setItem('investia:access', data.accessToken);
      localStorage.setItem('investia:refresh', data.refreshToken);
      setToken(data.accessToken);
      await refreshProfile();
    } finally {
      setLoading(false);
    }
  }, [refreshProfile]);

  const register = useCallback(
    async (input: { email: string; password: string; fullName: string; acceptTerms: boolean }) => {
      setLoading(true);
      try {
        await authApi.register(input);
        await login(input.email, input.password);
      } finally {
        setLoading(false);
      }
    },
    [login],
  );

  const logout = useCallback(() => {
    const refresh = localStorage.getItem('investia:refresh');
    if (refresh) void authApi.logout(refresh).catch(() => {});
    localStorage.removeItem('investia:access');
    localStorage.removeItem('investia:refresh');
    setToken(null);
    setProfile(null);
  }, []);

  // logout global disparado pelo interceptor de 401
  useEffect(() => {
    const handler = () => logout();
    window.addEventListener('investia:logout', handler);
    return () => window.removeEventListener('investia:logout', handler);
  }, [logout]);

  return (
    <AuthContext.Provider value={{ token, profile, loading, login, register, logout, refreshProfile }}>
      {children}
    </AuthContext.Provider>
  );
}

export const useAuth = () => useContext(AuthContext);
