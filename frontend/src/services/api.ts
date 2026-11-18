import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios';

const baseURL = import.meta.env.VITE_API_URL ?? '/api/v1';

export const api = axios.create({ baseURL, timeout: 20_000 });

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('investia:access');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

let refreshing: Promise<string | null> | null = null;

async function refreshAccessToken(): Promise<string | null> {
  const refreshToken = localStorage.getItem('investia:refresh');
  if (!refreshToken) return null;
  try {
    const { data } = await axios.post(`${baseURL}/auth/refresh`, { refreshToken });
    localStorage.setItem('investia:access', data.accessToken);
    localStorage.setItem('investia:refresh', data.refreshToken);
    return data.accessToken as string;
  } catch {
    return null;
  }
}

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as InternalAxiosRequestConfig & { _retry?: boolean };
    const isAuthCall = original?.url?.includes('/auth/');
    if (error.response?.status === 401 && original && !original._retry && !isAuthCall) {
      original._retry = true;
      refreshing ??= refreshAccessToken().finally(() => {
        refreshing = null;
      });
      const token = await refreshing;
      if (token) {
        original.headers.Authorization = `Bearer ${token}`;
        return api(original);
      }
      window.dispatchEvent(new Event('investia:logout'));
    }
    return Promise.reject(error);
  },
);

interface ProblemDetail {
  title?: string;
  detail?: string;
  status?: number;
}

export function problemDetailMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const problem = error.response?.data as ProblemDetail | undefined;
    return problem?.detail ?? problem?.title ?? error.message;
  }
  return error instanceof Error ? error.message : 'Erro inesperado';
}
