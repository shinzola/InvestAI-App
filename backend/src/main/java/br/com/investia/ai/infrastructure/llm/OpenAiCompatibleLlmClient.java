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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

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

        String response = httpPost(config.normalizedBaseUrl() + "/chat/completions", config.apiKey(), body);

        return parse(response);
    }

    /**
     * POST JSON lendo o corpo como texto, sem negociar content-type.
     * Proxies como o OmniBridge podem responder application/octet-stream, o que
     * faz os conversores do RestClient recusarem o corpo.
     */
    private String httpPost(String url, String apiKey, Map<String, Object> body) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(90))
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("HTTP " + response.statusCode() + ": " + response.body());
            }
            return response.body();
        } catch (IOException e) {
            throw new IllegalStateException("Falha de rede ao chamar " + url + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Chamada interrompida: " + url, e);
        }
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
            HttpRequest request = HttpRequest.newBuilder(URI.create(config.normalizedBaseUrl() + "/models"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + config.apiKey().trim())
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                log.warn("Listagem de modelos em {} retornou HTTP {}", config.normalizedBaseUrl(), response.statusCode());
                return List.of();
            }
            JsonNode root = mapper.readTree(response.body());
            List<String> ids = new ArrayList<>();
            for (JsonNode node : root.path("data")) {
                String id = node.path("id").asText("");
                if (!id.isBlank()) {
                    ids.add(id);
                }
            }
            ids.sort(String::compareTo);
            return ids;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Listagem de modelos interrompida em {}", config.normalizedBaseUrl());
            return List.of();
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
