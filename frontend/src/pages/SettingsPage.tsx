import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { accountsApi, aiSettingsApi, categoriesApi, usersApi } from '../services/endpoints';
import { Card, EmptyState } from '../components/ui/Card';
import { Field, Input, Money, Select } from '../components/ui/Form';
import { useAuth } from '../contexts/AuthContext';
import { useToast } from '../components/ui/Toast';
import { problemDetailMessage } from '../services/api';
import { ACCOUNT_TYPE_OPTIONS, BANKS, CATEGORY_KIND_LABELS, accountCardLabel, accountTypeLabel } from '../lib/labels';
import type { Account } from '../types';

export function SettingsPage() {
  const qc = useQueryClient();
  const { notify } = useToast();
  const { profile, refreshProfile, logout } = useAuth();
  const [account, setAccount] = useState({
    name: '', type: 'CHECKING', institution: '', initialBalance: '0',
    hasCreditCard: false, closingDay: '1', dueDay: '10', limitTotal: '0', limitUsed: '0',
  });
  const [editingAccount, setEditingAccount] = useState<string | null>(null);
  const [category, setCategory] = useState({ name: '', kind: 'EXPENSE' });

  const accounts = useQuery({ queryKey: ['accounts'], queryFn: () => accountsApi.list().then((r) => r.data) });
  const categories = useQuery({ queryKey: ['categories'], queryFn: () => categoriesApi.list().then((r) => r.data) });

  const aiSettings = useQuery({ queryKey: ['ai-settings'], queryFn: () => aiSettingsApi.get().then((r) => r.data) });
  const [ai, setAi] = useState({ baseUrl: '', model: '', apiKey: '' });
  const [aiTest, setAiTest] = useState<{ ok: boolean; message: string } | null>(null);

  useEffect(() => {
    if (aiSettings.data) {
      setAi((prev) => ({ ...prev, baseUrl: aiSettings.data.baseUrl, model: aiSettings.data.model }));
    }
  }, [aiSettings.data]);

  const saveAi = useMutation({
    mutationFn: () => aiSettingsApi.save({ baseUrl: ai.baseUrl, model: ai.model, apiKey: ai.apiKey || undefined }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['ai-settings'] });
      setAi((prev) => ({ ...prev, apiKey: '' }));
      setAiTest(null);
      notify('success', 'Provedor de IA salvo');
    },
    onError: (e) => notify('error', problemDetailMessage(e)),
  });

  const testAi = useMutation({
    mutationFn: () => aiSettingsApi.test().then((r) => r.data),
    onSuccess: (data) => setAiTest({ ok: data.ok, message: data.message }),
    onError: (e) => setAiTest({ ok: false, message: problemDetailMessage(e) }),
  });

  const accountBody = () => ({
    name: account.name,
    type: account.type,
    institution: account.institution || undefined,
    initialBalance: account.initialBalance,
    hasCreditCard: account.hasCreditCard,
    closingDay: account.hasCreditCard ? Number(account.closingDay) : undefined,
    dueDay: account.hasCreditCard ? Number(account.dueDay) : undefined,
    limitTotal: account.hasCreditCard ? account.limitTotal : undefined,
    limitUsed: account.hasCreditCard ? account.limitUsed : undefined,
  });

  const resetAccount = () => {
    setAccount({
      name: '', type: 'CHECKING', institution: '', initialBalance: '0',
      hasCreditCard: false, closingDay: '1', dueDay: '10', limitTotal: '0', limitUsed: '0',
    });
    setEditingAccount(null);
  };

  const invalidateAccounts = () => {
    void qc.invalidateQueries({ queryKey: ['accounts'] });
    void qc.invalidateQueries({ queryKey: ['cards'] });
  };

  const createAccount = useMutation({
    mutationFn: () => accountsApi.create(accountBody()),
    onSuccess: () => {
      notify('success', 'Conta criada');
      invalidateAccounts();
      resetAccount();
    },
    onError: (e) => notify('error', problemDetailMessage(e)),
  });

  const updateAccount = useMutation({
    mutationFn: (id: string) => accountsApi.update(id, accountBody()),
    onSuccess: () => {
      notify('success', 'Conta atualizada');
      invalidateAccounts();
      resetAccount();
    },
    onError: (e) => notify('error', problemDetailMessage(e)),
  });

  const startEditAccount = (a: Account) => {
    setEditingAccount(a.id);
    setAccount({
      name: a.name,
      type: a.type,
      institution: a.institution ?? '',
      initialBalance: String(a.initialBalance ?? 0),
      hasCreditCard: Boolean(a.card),
      closingDay: String(a.card?.closingDay ?? 1),
      dueDay: String(a.card?.dueDay ?? 10),
      limitTotal: String(a.card?.limitTotal ?? 0),
      limitUsed: String(a.card?.limitUsed ?? 0),
    });
  };

  const createCategory = useMutation({
    mutationFn: () => categoriesApi.create(category),
    onSuccess: () => {
      notify('success', 'Categoria criada');
      void qc.invalidateQueries({ queryKey: ['categories'] });
      setCategory({ name: '', kind: 'EXPENSE' });
    },
    onError: (e) => notify('error', problemDetailMessage(e)),
  });

  const updateProfile = useMutation({
    mutationFn: (body: Record<string, unknown>) => usersApi.updateProfile(body),
    onSuccess: () => {
      notify('success', 'Perfil atualizado');
      void refreshProfile();
    },
    onError: (e) => notify('error', problemDetailMessage(e)),
  });

  const exportData = async () => {
    const { data } = await usersApi.exportData();
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'investia-dados.json';
    a.click();
    URL.revokeObjectURL(url);
  };

  const deleteAccount = async () => {
    if (!confirm('Excluir sua conta e todos os dados? Esta ação é irreversível.')) return;
    try {
      await usersApi.deleteAccount();
      notify('success', 'Conta excluída');
      logout();
    } catch (e) {
      notify('error', problemDetailMessage(e));
    }
  };

  return (
    <div className="space-y-6">
      <header>
        <h1 className="display text-[26px]">Ajustes</h1>
        <p className="text-sm text-muted">Perfil, contas, categorias e privacidade</p>
      </header>

      <Card title="Perfil de investidor">
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Perfil">
            <Select
              value={profile?.investorProfile ?? 'MODERADO'}
              onChange={(e) => updateProfile.mutate({ investorProfile: e.target.value })}
            >
              <option value="CONSERVADOR">Conservador</option>
              <option value="MODERADO">Moderado</option>
              <option value="ARROJADO">Arrojado</option>
            </Select>
          </Field>
          <Field label="Fuso horário">
            <Input
              defaultValue={profile?.timezone ?? 'America/Sao_Paulo'}
              onBlur={(e) => updateProfile.mutate({ timezone: e.target.value })}
            />
          </Field>
        </div>
      </Card>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card title="Contas">
          {accounts.data?.length ? (
            <ul className="space-y-2 text-sm mb-4">
              {accounts.data.map((a) => {
                const card = a.card;
                const pct = card && card.limitTotal > 0
                  ? Math.min(100, Math.round((card.limitUsed / card.limitTotal) * 100))
                  : 0;
                return (
                  <li key={a.id} className="rounded-lg px-2 py-2 hover:bg-card-2">
                    <div className="flex justify-between gap-2">
                      <span>
                        {a.name}
                        <span className="text-muted"> · {accountTypeLabel(a.type)}</span>
                        {a.institution ? <span className="text-muted"> · {a.institution}</span> : null}
                        <span className="badge badge-accent ml-2">{accountCardLabel(Boolean(card))}</span>
                      </span>
                      <span className="flex items-center gap-2 shrink-0">
                        <Money value={a.balance} />
                        <button
                          type="button"
                          className="btn-ghost text-xs px-2 py-1"
                          onClick={() => startEditAccount(a)}
                        >
                          Editar
                        </button>
                      </span>
                    </div>
                    {card && (
                      <div className="mt-1.5">
                        <div className="flex items-center justify-between text-xs text-muted">
                          <span>
                            Limite <Money value={card.limitUsed} /> de{' '}
                            <Money value={card.limitTotal} />
                            {' · '}fecha dia {card.closingDay}, vence dia {card.dueDay}
                          </span>
                        </div>
                        <div className="mt-1 h-1.5 rounded-full bg-card-2 overflow-hidden">
                          <div
                            className={pct >= 80 ? 'h-full bg-danger' : 'h-full bg-accent'}
                            style={{ width: `${pct}%` }}
                          />
                        </div>
                      </div>
                    )}
                  </li>
                );
              })}
            </ul>
          ) : <EmptyState title="Nenhuma conta" subtitle="Crie uma conta para lançar transações." />}
          <form
            className="space-y-3"
            onSubmit={(e) => {
              e.preventDefault();
              if (editingAccount) updateAccount.mutate(editingAccount);
              else createAccount.mutate();
            }}
          >
            <Field label="Nome"><Input required value={account.name} onChange={(e) => setAccount({ ...account, name: e.target.value })} /></Field>
            <Field label="Banco / instituição">
              <Input
                list="bancos"
                maxLength={120}
                placeholder="Ex.: Nubank"
                value={account.institution}
                onChange={(e) => setAccount({ ...account, institution: e.target.value })}
              />
              <datalist id="bancos">
                {BANKS.map((b) => <option key={b} value={b} />)}
              </datalist>
            </Field>
            <div className="grid grid-cols-2 gap-3">
              <Field label="Tipo">
                <Select
                  value={account.type}
                  onChange={(e) => {
                    const type = e.target.value;
                    setAccount({
                      ...account,
                      type,
                      hasCreditCard: type === 'CREDIT_CARD' ? true : account.hasCreditCard,
                    });
                  }}
                >
                  {ACCOUNT_TYPE_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
                </Select>
              </Field>
              <Field label="Saldo inicial"><Input type="number" step="0.01" value={account.initialBalance} onChange={(e) => setAccount({ ...account, initialBalance: e.target.value })} /></Field>
            </div>
            <label className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={account.hasCreditCard}
                onChange={(e) => setAccount({ ...account, hasCreditCard: e.target.checked })}
              />
              Esta conta também tem cartão de crédito
            </label>
            {account.hasCreditCard && (
              <div className="rounded-lg border border-line p-3 space-y-3">
                <p className="text-xs font-medium text-muted">Fatura e limite do cartão</p>
                <div className="grid grid-cols-2 gap-3">
                  <Field label="Fecha dia">
                    <Input
                      type="number" min="1" max="28" required
                      value={account.closingDay}
                      onChange={(e) => setAccount({ ...account, closingDay: e.target.value })}
                    />
                  </Field>
                  <Field label="Vence dia">
                    <Input
                      type="number" min="1" max="28" required
                      value={account.dueDay}
                      onChange={(e) => setAccount({ ...account, dueDay: e.target.value })}
                    />
                  </Field>
                </div>
                <div className="grid grid-cols-2 gap-3">
                  <Field label="Limite máximo">
                    <Input
                      type="number" step="0.01" min="0"
                      value={account.limitTotal}
                      onChange={(e) => setAccount({ ...account, limitTotal: e.target.value })}
                    />
                  </Field>
                  <Field label="Limite já gasto">
                    <Input
                      type="number" step="0.01" min="0"
                      value={account.limitUsed}
                      onChange={(e) => setAccount({ ...account, limitUsed: e.target.value })}
                    />
                  </Field>
                </div>
              </div>
            )}
            {editingAccount ? (
              <div className="flex gap-2">
                <button className="btn-primary flex-1" disabled={updateAccount.isPending}>
                  Salvar alterações
                </button>
                <button type="button" className="btn-ghost" onClick={resetAccount}>Cancelar</button>
              </div>
            ) : (
              <button className="btn-primary w-full" disabled={createAccount.isPending}>Criar conta</button>
            )}
          </form>
        </Card>

        <Card title="Categorias">
          {categories.data?.length ? (
            <ul className="space-y-1 text-sm mb-4 max-h-48 overflow-y-auto">
              {categories.data.map((c) => (
                <li key={c.id} className="flex justify-between">
                  <span>{c.name}</span>
                  <span className="badge badge-accent">{CATEGORY_KIND_LABELS[c.kind] ?? c.kind}</span>
                </li>
              ))}
            </ul>
          ) : <EmptyState title="Nenhuma categoria" />}
          <form className="space-y-3" onSubmit={(e) => { e.preventDefault(); createCategory.mutate(); }}>
            <Field label="Nome"><Input required value={category.name} onChange={(e) => setCategory({ ...category, name: e.target.value })} /></Field>
            <Field label="Tipo">
              <Select value={category.kind} onChange={(e) => setCategory({ ...category, kind: e.target.value })}>
                <option value="EXPENSE">Despesa</option>
                <option value="INCOME">Receita</option>
              </Select>
            </Field>
            <button className="btn-primary w-full" disabled={createCategory.isPending}>Criar categoria</button>
          </form>
        </Card>
      </div>

      <Card title="Inteligência artificial">
        <p className="text-xs text-muted mb-3">
          Conecte um provedor compatível com OpenAI (OpenAI, Abacus RouteLLM, Groq, Together, Ollama).
          Com chave, o copiloto conversa com memória e encadeia ações; sem chave, ele segue no modo local.
        </p>
        <div className="flex flex-wrap items-center gap-2 mb-3">
          <span className={aiSettings.data?.configured ? 'badge badge-accent' : 'badge badge-neutral'}>
            {aiSettings.data?.configured ? 'IA conectada' : 'Modo local'}
          </span>
          {aiSettings.data?.apiKeyMasked && (
            <span className="text-xs text-muted num">{aiSettings.data.apiKeyMasked}</span>
          )}
          <span className="text-xs text-muted">
            origem: {aiSettings.data?.source === 'user' ? 'Ajustes' : 'ambiente'}
          </span>
        </div>
        <form
          className="space-y-3"
          onSubmit={(e) => { e.preventDefault(); saveAi.mutate(); }}
        >
          <Field label="Base URL">
            <Input
              required
              placeholder="https://api.openai.com/v1"
              value={ai.baseUrl}
              onChange={(e) => setAi({ ...ai, baseUrl: e.target.value })}
            />
          </Field>
          <Field label="Modelo">
            <Input
              required
              placeholder="gpt-4o-mini"
              value={ai.model}
              onChange={(e) => setAi({ ...ai, model: e.target.value })}
            />
          </Field>
          <Field label="Chave da API">
            <Input
              type="password"
              autoComplete="off"
              placeholder={aiSettings.data?.configured ? 'Deixe vazio para manter a atual' : 'sk-...'}
              value={ai.apiKey}
              onChange={(e) => setAi({ ...ai, apiKey: e.target.value })}
            />
          </Field>
          <div className="flex flex-wrap gap-2">
            <button className="btn-primary" type="submit" disabled={saveAi.isPending}>
              Salvar provedor
            </button>
            <button
              className="btn-ghost"
              type="button"
              onClick={() => testAi.mutate()}
              disabled={testAi.isPending}
            >
              {testAi.isPending ? 'Testando...' : 'Testar conexão'}
            </button>
          </div>
        </form>
        {aiTest && (
          <p className={aiTest.ok ? 'text-xs text-success mt-2' : 'text-xs text-danger mt-2'}>
            {aiTest.message}
          </p>
        )}
      </Card>

      <Card title="Privacidade (LGPD)">
        <div className="flex flex-wrap gap-2">
          <button className="btn-ghost" onClick={exportData}>Exportar meus dados</button>
          <button className="btn-ghost text-danger" onClick={deleteAccount}>Excluir minha conta</button>
        </div>
      </Card>
    </div>
  );
}
