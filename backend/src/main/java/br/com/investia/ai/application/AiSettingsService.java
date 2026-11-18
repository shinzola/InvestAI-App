package br.com.investia.ai.application;

import br.com.investia.ai.domain.AiSettings;
import br.com.investia.ai.infrastructure.jpa.AiSettingsJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Resolve a configuração de LLM: a do usuário (Ajustes) tem precedência
 * sobre a do ambiente. Sem chave em nenhum dos dois, o copiloto opera
 * em modo determinístico.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiSettingsService {

    private final AiSettingsJpaRepository repository;
    private final LlmClient llm;

    @Value("${app.ai.api-key:}")
    private String envApiKey;

    @Value("${app.ai.base-url:https://api.openai.com/v1}")
    private String envBaseUrl;

    @Value("${app.ai.model:gpt-4o-mini}")
    private String envModel;

    public record SettingsView(String baseUrl, String model, String apiKeyMasked,
                               boolean configured, String source) {
    }

    public record SaveRequest(String baseUrl, String model, String apiKey) {
    }

    public record TestResult(boolean ok, String message, String sample) {
    }

    @Transactional(readOnly = true)
    public LlmConfig resolve(UUID userId) {
        return repository.findById(userId)
                .map(s -> new LlmConfig(s.getBaseUrl(), s.getModel(), s.getApiKey()))
                .filter(LlmConfig::usable)
                .orElseGet(() -> new LlmConfig(envBaseUrl, envModel, envApiKey));
    }

    @Transactional(readOnly = true)
    public SettingsView get(UUID userId) {
        return repository.findById(userId)
                .map(s -> new SettingsView(s.getBaseUrl(), s.getModel(), mask(s.getApiKey()),
                        true, "user"))
                .orElseGet(() -> new SettingsView(envBaseUrl, envModel, mask(envApiKey),
                        envApiKey != null && !envApiKey.isBlank(), "environment"));
    }

    @Transactional
    public SettingsView save(UUID userId, SaveRequest req) {
        AiSettings current = repository.findById(userId).orElse(null);
        String key = req.apiKey() == null || req.apiKey().isBlank()
                ? (current == null ? "" : current.getApiKey())
                : req.apiKey().trim();
        AiSettings saved = repository.save(AiSettings.builder()
                .userId(userId)
                .baseUrl(blank(req.baseUrl()) ? envBaseUrl : req.baseUrl().trim())
                .model(blank(req.model()) ? envModel : req.model().trim())
                .apiKey(key)
                .build());
        return new SettingsView(saved.getBaseUrl(), saved.getModel(), mask(saved.getApiKey()),
                !saved.getApiKey().isBlank(), "user");
    }

    @Transactional
    public void clear(UUID userId) {
        repository.deleteById(userId);
    }

    /** Faz uma chamada mínima para validar chave, URL e modelo. */
    @Transactional(readOnly = true)
    public TestResult test(UUID userId) {
        LlmConfig cfg = resolve(userId);
        if (!llm.enabled(cfg)) {
            return new TestResult(false, "Nenhuma chave de IA configurada.", null);
        }
        try {
            LlmClient.Completion c = llm.chat(cfg,
                    List.of(LlmClient.Message.system("Responda em uma frase curta, em português."),
                            LlmClient.Message.user("Diga apenas: conexão ok")),
                    List.of());
            if (c == null || c.content() == null || c.content().isBlank()) {
                return new TestResult(false, "O provedor respondeu vazio.", null);
            }
            return new TestResult(true, "Conexão com " + cfg.safeModel() + " funcionando.", c.content());
        } catch (RuntimeException e) {
            log.warn("Teste de LLM falhou: {}", e.getMessage());
            return new TestResult(false, "Falha ao chamar o provedor: " + e.getMessage(), null);
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    static String mask(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        String trimmed = key.trim();
        if (trimmed.length() <= 4) {
            return "••••";
        }
        return "••••" + trimmed.substring(trimmed.length() - 4);
    }
}
