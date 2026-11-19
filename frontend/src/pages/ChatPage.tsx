import { useEffect, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { CheckCircle2, Loader2, Paperclip, Send, Sparkles, Trash2 } from 'lucide-react';
import { clsx } from 'clsx';
import { Card } from '../components/ui/Card';
import { chatApi, type ChatAction, type ChatEntry, type InvoiceImport } from '../services/endpoints';

const SUGGESTIONS = [
  'Gastei 45,90 no mercado hoje',
  'Recebi 3.200 de salário',
  'Planeje 120 de internet para 10/12',
  'Quanto sobrou para aportar?',
  'Aportar 300 na reserva de emergência',
];

const TOOL_LABELS: Record<string, string> = {
  create_income: 'Entrada registrada',
  create_expense: 'Despesa registrada',
  create_planned_expense: 'Gasto planejado',
  mark_planned_paid: 'Conta quitada',
  create_goal: 'Meta criada',
  contribute_goal: 'Aporte na meta',
  summary: 'Resumo do mês',
  list_accounts: 'Contas',
  list_planning: 'Planejamento',
};

export function ChatPage() {
  const [entries, setEntries] = useState<ChatEntry[]>([]);
  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);
  const [importing, setImporting] = useState(false);
  const [importResult, setImportResult] = useState<InvoiceImport | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [usedLlm, setUsedLlm] = useState<boolean | null>(null);
  const bottom = useRef<HTMLDivElement>(null);
  const fileInput = useRef<HTMLInputElement>(null);

  const history = useQuery({
    queryKey: ['chat-history'],
    queryFn: () => chatApi.history().then((r) => r.data),
  });

  useEffect(() => {
    if (history.data) {
      setEntries(history.data);
    }
  }, [history.data]);

  useEffect(() => {
    bottom.current?.scrollIntoView({ behavior: 'smooth' });
  }, [entries.length, sending]);

  const push = (entry: ChatEntry) => setEntries((prev) => [...prev, entry]);

  const handleSend = async (text: string) => {
    const message = text.trim();
    if (!message || sending) {
      return;
    }
    setInput('');
    setError(null);
    push({
      id: `local-${Date.now()}`,
      role: 'USER',
      content: message,
      actions: [],
      createdAt: new Date().toISOString(),
    });
    setSending(true);
    try {
      const { data } = await chatApi.send(message);
      setUsedLlm(data.usedLlm);
      push({
        id: `reply-${Date.now()}`,
        role: 'ASSISTANT',
        content: data.answer,
        actions: data.actions,
        createdAt: new Date().toISOString(),
      });
    } catch {
      setError('Não consegui falar com o servidor. Tente novamente.');
    } finally {
      setSending(false);
    }
  };

  const handleFile = async (file: File | undefined) => {
    if (!file) {
      return;
    }
    setImporting(true);
    setError(null);
    setImportResult(null);
    try {
      const { data } = await chatApi.importInvoice(file);
      setImportResult(data);
    } catch {
      setError('Não consegui ler a fatura. Envie um PDF ou um arquivo de texto.');
    } finally {
      setImporting(false);
      if (fileInput.current) {
        fileInput.current.value = '';
      }
    }
  };

  const handleClear = async () => {
    await chatApi.clear();
    setEntries([]);
    setImportResult(null);
  };

  return (
    <div className="space-y-5">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="display text-[26px]">Copiloto</h1>
          <p className="text-sm text-muted">
            Peça em português: ele registra, planeja e aporta de verdade
          </p>
        </div>
        <div className="flex items-center gap-2">
          {usedLlm !== null && (
            <span
              className={clsx('badge', usedLlm ? 'badge-accent' : 'badge-neutral')}
              title={usedLlm
                ? 'O copiloto está usando um provedor de IA conectado.'
                : 'Sem provedor de IA: o copiloto entende frases no padrão "gastei 45,90 no mercado". Conecte uma IA em Ajustes para linguagem livre.'}
            >
              <Sparkles size={12} />
              {usedLlm ? 'IA conectada' : 'Modo local'}
            </span>
          )}
          <input
            ref={fileInput}
            type="file"
            accept=".pdf,.txt,.csv"
            className="hidden"
            onChange={(e) => handleFile(e.target.files?.[0])}
          />
          <button
            className="btn-ghost"
            onClick={() => fileInput.current?.click()}
            disabled={importing}
          >
            {importing ? <Loader2 size={16} className="animate-spin" /> : <Paperclip size={16} />}
            Anexar fatura
          </button>
          <button className="btn-ghost text-danger" onClick={handleClear}>
            <Trash2 size={16} />
            Limpar
          </button>
        </div>
      </header>

      {importResult && (
        <Card>
          <div className="flex items-start gap-3">
            <CheckCircle2 size={18} className="text-success shrink-0 mt-0.5" />
            <div className="text-sm">
              <p className="font-medium">
                {importResult.created} gastos importados de {importResult.fileName}
              </p>
              <p className="text-xs text-muted mt-1">
                {importResult.extracted} linhas lidas · {importResult.skipped} ignoradas
              </p>
              <ul className="mt-2 space-y-1">
                {importResult.items.slice(0, 6).map((item) => (
                  <li key={item.id} className="flex justify-between gap-3 text-xs">
                    <span className="truncate">{item.description}</span>
                    <span className="num shrink-0">
                      {item.amount.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}
                    </span>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </Card>
      )}

      {error && <p className="text-sm text-danger">{error}</p>}

      <Card>
        <div className="min-h-[320px] max-h-[52vh] overflow-y-auto space-y-3 pr-1">
          {entries.length === 0 && !history.isLoading && (
            <div className="flex flex-col items-center justify-center h-[280px] text-center gap-2">
              <Sparkles size={22} className="text-accent" />
              <p className="text-sm font-medium">Comece com uma frase</p>
              <p className="text-xs text-muted max-w-sm">
                O copiloto executa a ação nos seus dados reais — nada de números inventados.
              </p>
            </div>
          )}

          {entries.map((entry) => (
            <div
              key={entry.id}
              className={clsx('flex', entry.role === 'USER' ? 'justify-end' : 'justify-start')}
            >
              <div
                className={clsx(
                  'max-w-[85%] rounded-xl px-3 py-2 text-sm',
                  entry.role === 'USER'
                    ? 'bg-accent text-white'
                    : 'bg-card-2 text-content',
                )}
              >
                <p className="whitespace-pre-wrap">{entry.content}</p>
                {entry.actions.length > 0 && (
                  <div className="mt-2 flex flex-wrap gap-1.5">
                    {entry.actions.map((action: ChatAction, i) => (
                      <span
                        key={`${entry.id}-${i}`}
                        className="badge badge-accent"
                        title={action.summary}
                      >
                        <CheckCircle2 size={12} />
                        {TOOL_LABELS[action.tool] ?? action.tool}
                      </span>
                    ))}
                  </div>
                )}
              </div>
            </div>
          ))}

          {sending && (
            <div className="flex justify-start">
              <div className="rounded-xl bg-card-2 px-3 py-2 text-sm text-muted flex items-center gap-2">
                <Loader2 size={14} className="animate-spin" />
                Executando...
              </div>
            </div>
          )}
          <div ref={bottom} />
        </div>

        <div className="mt-3 flex flex-wrap gap-1.5">
          {SUGGESTIONS.map((s) => (
            <button
              key={s}
              className="badge badge-neutral hover:bg-card-2"
              onClick={() => handleSend(s)}
              disabled={sending}
            >
              {s}
            </button>
          ))}
        </div>

        <form
          className="mt-3 flex items-end gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            handleSend(input);
          }}
        >
          <textarea
            className="flex-1 resize-none rounded-chip border border-line bg-card px-3 py-2 text-sm outline-none focus:border-accent"
            placeholder="Escreva como falaria com um consultor. Enter envia, Shift+Enter quebra linha."
            rows={2}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                handleSend(input);
              }
            }}
            disabled={sending}
          />
          <button className="btn-primary" type="submit" disabled={sending || !input.trim()}>
            <Send size={16} />
            Enviar
          </button>
        </form>
      </Card>
    </div>
  );
}
