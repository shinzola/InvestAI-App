package br.com.investia.ai.application;

/** Configuração efetiva de um provedor de LLM compatível com OpenAI. */
public record LlmConfig(String baseUrl, String model, String apiKey) {

    public boolean usable() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String safeBaseUrl() {
        return baseUrl == null || baseUrl.isBlank() ? "https://api.openai.com/v1" : baseUrl.trim();
    }

    /**
     * Base URL pronta para uso: sem barra final e com o segmento de versão.
     * Proxies como o OmniBridge costumam ser informados sem o /v1
     * (ex.: http://localhost:8000), o que faria o POST cair em 404.
     */
    public String normalizedBaseUrl() {
        String url = safeBaseUrl();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.matches(".*/v\\d+[a-z]*(/.*)?$")) {
            return url;
        }
        return url + "/v1";
    }

    public String safeModel() {
        return model == null || model.isBlank() ? "gpt-4o-mini" : model.trim();
    }
}
