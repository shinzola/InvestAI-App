package br.com.investia.ai.application;

import java.util.List;

/**
 * Porta para LLM compatível com OpenAI (chat/completions com tool calling).
 *
 * Regra do sistema: o modelo nunca inventa números — ele escolhe ferramentas
 * e redige a resposta a partir do contexto real e dos resultados devolvidos.
 */
public interface LlmClient {

    boolean enabled(LlmConfig config);

    Completion chat(LlmConfig config, List<Message> messages, List<ToolSpec> tools);

    /** Mensagem no formato do protocolo de chat. */
    record Message(String role, String content, List<ToolCall> toolCalls, String toolCallId) {

        public static Message system(String content) {
            return new Message("system", content, List.of(), null);
        }

        public static Message user(String content) {
            return new Message("user", content, List.of(), null);
        }

        public static Message assistant(String content, List<ToolCall> toolCalls) {
            return new Message("assistant", content, toolCalls == null ? List.of() : toolCalls, null);
        }

        public static Message tool(String toolCallId, String content) {
            return new Message("tool", content, List.of(), toolCallId);
        }
    }

    record ToolCall(String id, String name, String arguments) {
    }

    record ToolSpec(String name, String description, String parametersJson) {
    }

    record Completion(String content, List<ToolCall> toolCalls) {

        public boolean hasToolCalls() {
            return toolCalls != null && !toolCalls.isEmpty();
        }

        public static Completion text(String content) {
            return new Completion(content, List.of());
        }
    }
}
