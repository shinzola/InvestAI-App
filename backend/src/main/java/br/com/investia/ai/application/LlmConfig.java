package br.com.investia.ai.application;

/** Configuração efetiva de um provedor de LLM compatível com OpenAI. */
public record LlmConfig(String baseUrl, String model, String apiKey) {

    public boolean usable() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String safeBaseUrl() {
        return baseUrl == null || baseUrl.isBlank() ? "https://api.openai.com/v1" : baseUrl.trim();
    }

    public String safeModel() {
        return model == null || model.isBlank() ? "gpt-4o-mini" : model.trim();
    }
}
