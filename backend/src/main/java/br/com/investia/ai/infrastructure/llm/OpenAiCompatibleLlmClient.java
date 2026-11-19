package br.com.investia.ai.infrastructure.llm;

import br.com.investia.ai.application.LlmClient;
import br.com.investia.ai.application.LlmConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cliente compatível com OpenAI (chat/completions) com suporte a tool calling.
 * Funciona com OpenAI, Abacus RouteLLM, Groq, Together, Ollama e afins —
 * basta apontar base-url e model.
 */
@Slf4j
@Component
public class OpenAiCompatibleLlmClient implements LlmClient {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public boolean enabled(LlmConfig config) {
        return config != null && config.usable();
    }

    @Override
    public Completion chat(LlmConfig config, List<Message> messages, List<ToolSpec> tools) {
        if (!enabled(config)) {
            return Completion.text(null);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.safeModel());
        body.put("messages", messages.stream().map(this::toWire).toList());
        body.put("temperature", 0.3);
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools.stream().map(this::toWireTool).toList());
            body.put("tool_choice", "auto");
        }

        String response = RestClient.builder()
                .baseUrl(config.normalizedBaseUrl())
                .defaultHeader("Authorization", "Bearer " + config.apiKey().trim())
                .requestFactory(requestFactory())
                .build()
                .post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        return parse(response);
    }

    /**
     * Lista os modelos do provedor. Funciona com OpenAI e com proxies
     * compatíveis (OmniBridge expõe GET /v1/models com os modelos Gemini).
     */
    @Override
    public List<String> models(LlmConfig config) {
        if (!enabled(config)) {
            return List.of();
        }
        try {
            String response = RestClient.builder()
                    .baseUrl(config.normalizedBaseUrl())
                    .defaultHeader("Authorization", "Bearer " + config.apiKey().trim())
                    .requestFactory(requestFactory())
                    .build()
                    .get()
                    .uri("/models")
                    .retrieve()
                    .body(String.class);
            JsonNode root = mapper.readTree(response);
            List<String> ids = new ArrayList<>();
            for (JsonNode node : root.path("data")) {
                String id = node.path("id").asText("");
                if (!id.isBlank()) {
                    ids.add(id);
                }
            }
            ids.sort(String::compareTo);
            return ids;
        } catch (RuntimeException | java.io.IOException e) {
            log.warn("Falha ao listar modelos em {}: {}", config.normalizedBaseUrl(), e.getMessage());
            return List.of();
        }
    }

    private SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(15).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(90).toMillis());
        return factory;
    }

    private Map<String, Object> toWire(Message m) {
        Map<String, Object> wire = new LinkedHashMap<>();
        wire.put("role", m.role());
        if ("assistant".equals(m.role()) && m.toolCalls() != null && !m.toolCalls().isEmpty()) {
            wire.put("content", m.content() == null ? "" : m.content());
            wire.put("tool_calls", m.toolCalls().stream().map(tc -> Map.of(
                    "id", tc.id(),
                    "type", "function",
                    "function", Map.of("name", tc.name(), "arguments", tc.arguments()))).toList());
            return wire;
        }
        if ("tool".equals(m.role())) {
            wire.put("tool_call_id", m.toolCallId());
            wire.put("content", m.content() == null ? "" : m.content());
            return wire;
        }
        wire.put("content", m.content() == null ? "" : m.content());
        return wire;
    }

    private Map<String, Object> toWireTool(ToolSpec spec) {
        Map<String, Object> params;
        try {
            params = mapper.readValue(spec.parametersJson(), Map.class);
        } catch (Exception e) {
            params = Map.of("type", "object", "properties", Map.of());
        }
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", spec.name(),
                        "description", spec.description(),
                        "parameters", params));
    }

    private Completion parse(String response) {
        try {
            JsonNode root = mapper.readTree(response);
            JsonNode message = root.path("choices").path(0).path("message");
            String content = text(message.path("content"));
            if (content == null || content.isBlank()) {
                content = text(message.path("reasoning_content"));
            }
            List<ToolCall> calls = new ArrayList<>();
            for (JsonNode call : message.path("tool_calls")) {
                JsonNode fn = call.path("function");
                String id = call.path("id").asText("call_" + calls.size());
                calls.add(new ToolCall(id, fn.path("name").asText(""),
                        fn.path("arguments").isMissingNode() ? "{}" : fn.path("arguments").asText("{}")));
            }
            return new Completion(content, calls);
        } catch (Exception e) {
            log.warn("Resposta de LLM ilegível: {}", e.getMessage());
            return Completion.text(null);
        }
    }

    /** Aceita content como texto, nulo ou lista de partes (formato multimodal). */
    private String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode part : node) {
                sb.append(part.path("text").asText(""));
            }
            return sb.toString();
        }
        return node.asText(null);
    }
}
