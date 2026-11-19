# InvestIA — Finanças pessoais e investimentos (Brasil)

Sistema modular de finanças pessoais e carteira de investimentos, com copiloto de IA que
responde **apenas com números calculados a partir dos seus dados**.

- **Backend:** Java 21 · Spring Boot 3.3 · PostgreSQL 16 · Redis 7 · Flyway · JWT
- **Frontend:** React 18 · Vite · TypeScript · Tailwind CSS v4 · React Query · Recharts
- **Arquitetura:** Clean Architecture modular (bounded contexts) com eventos de domínio

---

## 1. Como rodar

### Docker Compose (recomendado)

```bash
docker compose up --build
```

| Serviço | URL |
|---|---|
| Frontend | http://localhost:5173 |
| API | http://localhost:8080/api/v1 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Health | http://localhost:8080/actuator/health |

### Desenvolvimento local

```bash
# infraestrutura
docker compose up -d postgres redis

# backend
cd backend && mvn spring-boot:run

# frontend
cd frontend && npm install && npm run dev
```

Variáveis de ambiente principais (backend):

| Variável | Padrão | Descrição |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/investia` | JDBC do PostgreSQL |
| `DB_USER` / `DB_PASSWORD` | `investia` / `investia` | Credenciais |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Cache e rate limiting |
| `JWT_SECRET` | (dev) | **Obrigatório em produção**, mínimo 32 bytes |
| `JWT_ACCESS_TTL_MINUTES` | `15` | Validade do access token |
| `JWT_REFRESH_TTL_DAYS` | `30` | Validade do refresh token |
| `AI_PROVIDER_URL` / `AI_PROVIDER_KEY` | vazio | LLM opcional (só redige texto) |

> O copiloto funciona **sem** LLM: as respostas numéricas vêm de consultas ao banco e o LLM,
> quando configurado, apenas reformula a frase.

---

## 2. Módulos (bounded contexts)

| Módulo | Responsabilidade |
|---|---|
| `shared` | `Money` (BigDecimal), `Percentage`, `PeriodRange`, erros RFC 7807, auditoria |
| `identity` | Registro, login, JWT + refresh rotativo, LGPD (exportar/excluir), recuperação de senha |
| `accounts` | Contas, cartões de crédito, saldo consolidado |
| `transactions` | Receitas/despesas/transferências, parcelamento, recorrências, importação OFX/CSV |
| `budgeting` | Envelopes por categoria, alertas 80%/100%, gasto atípico |
| `portfolio` | Ativos, operações, preço médio, posições, cotações, alocação-alvo |
| `earnings` | Proventos, média 12m, projeção, "viver de renda" |
| `goals` | Metas, aportes, aporte necessário (PMT), simulador com IPCA |
| `analytics` | Sumário, score de saúde, volatilidade, drawdown, Sharpe, beta, benchmarks |
| `tax` | Isenção R$ 20k, 15%/20% day trade, renda fixa regressiva, come-cotas, DARF |
| `ai` | Copiloto, categorização por regras, alertas |

### Comunicação entre módulos

Módulos não se importam diretamente: usam **portas** (`TransactionQueryPort`,
`OperationQueryPort`, `QuoteQueryPort`) e **eventos** (`TransactionCreatedEvent`,
`BudgetAlertRaisedEvent`). Assim `ai` e `budgeting` reagem a transações sem acoplamento.

---

## 3. Regras financeiras implementadas

- **Preço médio (compras):** `((qtd_atual × pm_atual) + (qtd_compra × preço) + taxas) / (qtd_atual + qtd_compra)`
- **Venda:** reduz quantidade, mantém preço médio, registra resultado realizado.
- **IR ações à vista:** isento até R$ 20.000 de vendas no mês; acima disso 15% sobre o ganho.
- **Day trade:** 20% sobre o ganho, sem isenção.
- **Renda fixa:** alíquota regressiva (22,5% → 15%) por prazo.
- **Come-cotas:** 15% (fundos DI) / 20% (multimercado) em maio e novembro.
- **Aporte necessário:** PMT com taxa real = `(1+nominal)/(1+IPCA) − 1`.
- **Score de saúde:** poupança, reserva de emergência, concentração, orçamento (pesos em `HealthScoreService`).

---

## 4. Segurança

- Senhas com BCrypt; JWT assinado (HS256) com `jti`; refresh tokens armazenados como hash,
  com **rotação e detecção de reuso** (reuso revoga a família).
- Bloqueio progressivo após tentativas falhas + rate limiting no Redis.
- CORS restrito, headers de segurança, correlation-id por requisição.
- LGPD: exportação completa dos dados e exclusão de conta.

---

## 5. Testes

```bash
cd backend
mvn test                 # unitários (32 testes)
mvn verify               # + integração com Testcontainers (requer Docker)
```

Cobertura atual dos unitários: `Money`, `PositionCalculator`, `TaxRules`, `FinancialMath`,
`Goal`, `CategorizationRules`, `InsightService`, `BigDecimalPrecision`.

---

## 6. Estrutura

```
backend/
  src/main/java/br/com/investia/
    shared/ identity/ accounts/ transactions/ budgeting/
    portfolio/ earnings/ goals/ analytics/ tax/ ai/
  src/main/resources/db/migration/   # V1 schema, V2 seeds
frontend/
  src/{components,contexts,pages,services,styles,types}
docker-compose.yml
```

## 7. Limitações conhecidas

- Cotações usam o último preço cadastrado (integração com provedor externo é um adaptador isolado).
- Relatórios PDF dependem do endpoint de exportação; o CSV de transações é gerado no backend.
- Testes de integração exigem Docker (não executados neste ambiente).

---

## 8. Gamificação e design (v2)

A interface foi reformulada com identidade roxa (lavanda) e mecânicas de progressão.

**Design tokens** (`frontend/src/styles/tokens.css`): paleta clara `#f7f5ff` / `#7c5cf0` e escura
`#14102a` / `#a78bfa`. Nenhuma cor é escrita direto nas telas — tudo passa por variáveis CSS,
então os dois temas acompanham qualquer componente novo.

**Progressão** (`frontend/src/lib/gamification.ts`): XP, nível, sequência de dias, conquistas e
missões são **derivados de dados reais**, sem estado paralelo no banco.

| Ação | XP |
|---|---|
| Transação registrada | 10 (até 100 transações) |
| Meta criada | 30 |
| Meta concluída | 120 |
| Aporte em meta | 25 |
| Operação de carteira | 20 |
| Provento registrado | 15 |
| Orçamento definido | 40 |
| Score de saúde | pontos do score |

Cada nível custa `100 × nível` de XP. A sequência (`streak`) conta dias consecutivos com
lançamento, começando hoje ou ontem. Nove conquistas cobrem do primeiro lançamento à
conclusão de meta e score acima de 80.

**Seleção de metas** (`/goals`): catálogo com oito objetivos prontos (reserva, viagem, imóvel,
aposentadoria, carro, educação, casamento, liberdade financeira). O usuário seleciona vários,
ajusta o valor-alvo de cada um e cria todos de uma vez.

---

## 9. Copiloto IA e provedores

O copiloto (`/chat`) é um agente com tool calling: o modelo escolhe entre nove ferramentas
(`create_expense`, `create_income`, `create_planned_expense`, `create_goal`, `contribute_goal`,
`mark_planned_paid`, `summary`, `list_goals`, `list_accounts`), recebe os resultados reais e só
então redige a resposta. Nenhum número é inventado pelo modelo.

### Provedores suportados

Qualquer endpoint compatível com `POST /v1/chat/completions` e `GET /v1/models`:

| Preset | Base URL | Modelo padrão |
|---|---|---|
| OmniBridge (Gemini) | `http://host.docker.internal:8000/v1` | `gemini-3.6-flash` |
| OpenAI | `https://api.openai.com/v1` | `gpt-4o-mini` |
| Groq | `https://api.groq.com/openai/v1` | `llama-3.3-70b-versatile` |
| Ollama (local) | `http://host.docker.internal:11434/v1` | `llama3.1` |

A configuração fica em **Ajustes → Inteligência artificial**, por usuário, com precedência sobre
as variáveis de ambiente. A chave é gravada no banco e devolvida sempre mascarada (`••••1234`).

### OmniBridge (Gemini sem custo por token)

O [OmniBridge](https://github.com/exelite-dev/Free-Gemini-pro-API) é um proxy auto-hospedado que
traduz sessões web do Gemini para o formato OpenAI, com tool calling e `reasoning_content`.

```bash
git clone https://github.com/exelite-dev/Free-Gemini-pro-API
cd Free-Gemini-pro-API
cp .env.example .env          # defina ADMIN_PASSWORD e ADMIN_SECRET_KEY
docker compose up -d          # sobe em http://localhost:8000
```

Depois, em `http://localhost:8000/admin`: cadastre a conta Gemini (cookies `__Secure-1PSID` e
`__Secure-1PSIDTS`) e gere uma chave `sk-omni-...`. No InvestIA, use o preset **OmniBridge (Gemini)**,
cole a chave e clique em **Buscar modelos do provedor** para escolher entre os modelos listados.

Detalhes de implementação:

- `LlmConfig.normalizedBaseUrl()` completa o `/v1` quando a URL vem sem versão
  (`http://localhost:8000` → `http://localhost:8000/v1`), evitando 404.
- `OpenAiCompatibleLlmClient.models()` consome `GET /v1/models` e alimenta os chips de modelo na UI.
- `parse()` aceita `content` como texto, nulo ou lista de partes e cai para `reasoning_content`
  quando o provedor devolve o texto apenas no fluxo de raciocínio.
- Sem chave configurada, o copiloto continua funcionando no modo determinístico em pt-BR.

