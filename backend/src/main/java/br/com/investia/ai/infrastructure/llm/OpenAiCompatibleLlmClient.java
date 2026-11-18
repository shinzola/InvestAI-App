package br.com.investia.ai.infrastructure.llm;

import br.com.investia.ai.application.LlmClient;
import br.com.investia.ai.application.LlmConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
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
                .baseUrl(config.safeBaseUrl())
                .defaultHeader("Authorization", "Bearer " + config.apiKey().trim())
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                    setConnectTimeout((int) Duration.ofSeconds(15).toMillis());
                    setReadTimeout((int) Duration.ofSeconds(90).toMillis());
                }})
                .build()
                .post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        return parse(response);
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
            String content = message.path("content").isNull() ? null : message.path("content").asText(null);
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
}
