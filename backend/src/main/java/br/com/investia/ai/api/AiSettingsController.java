package br.com.investia.ai.api;

import br.com.investia.ai.application.AiSettingsService;
import br.com.investia.identity.infrastructure.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Configuração de IA", description = "Provedor de LLM usado pelo copiloto")
@RestController
@RequestMapping("/api/v1/ai/settings")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AiSettingsController {

    private final AiSettingsService settings;

    @Operation(summary = "Configuração atual (chave mascarada)")
    @GetMapping
    public AiSettingsService.SettingsView get() {
        return settings.get(SecurityUtils.currentUserId());
    }

    @Operation(summary = "Salva o provedor de LLM do usuário")
    @PutMapping
    public AiSettingsService.SettingsView save(@RequestBody AiSettingsService.SaveRequest req) {
        return settings.save(SecurityUtils.currentUserId(), req);
    }

    @Operation(summary = "Remove a configuração do usuário e volta ao padrão do ambiente")
    @DeleteMapping
    public Map<String, Object> clear() {
        settings.clear(SecurityUtils.currentUserId());
        return Map.of("status", "ok");
    }

    @Operation(summary = "Testa a conexão com o provedor")
    @PostMapping("/test")
    public AiSettingsService.TestResult test() {
        return settings.test(SecurityUtils.currentUserId());
    }
}
