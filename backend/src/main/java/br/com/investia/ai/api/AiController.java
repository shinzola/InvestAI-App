package br.com.investia.ai.api;

import br.com.investia.ai.application.CopilotService;
import br.com.investia.ai.application.CategorizationRules;
import br.com.investia.ai.application.InsightService;
import br.com.investia.identity.infrastructure.security.SecurityUtils;
import br.com.investia.transactions.application.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Tag(name = "Copiloto IA", description = "Perguntas, categorização automática e insights")
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AiController {

    private final CopilotService copilot;
    private final InsightService insights;
    private final CategorizationRules rules;
    private final CategoryService categories;

    public record AskRequest(@NotBlank String question) {
    }

    public record CategorizeRequest(@NotBlank String description, boolean income) {
    }

    @Operation(summary = "Pergunta ao copiloto (resposta derivada de dados reais)")
    @PostMapping("/ask")
    public CopilotService.CopilotAnswer ask(@RequestBody AskRequest req) {
        return copilot.ask(SecurityUtils.currentUserId(), req.question());
    }

    @Operation(summary = "Sugestão de categoria para uma descrição")
    @PostMapping("/categorize")
    public Map<String, Object> categorize(@RequestBody CategorizeRequest req) {
        Optional<String> suggestion = rules.suggestCategory(req.description(), req.income());
        return Map.of(
                "suggestion", suggestion.orElse("Outros"),
                "ruleBased", suggestion.isPresent());
    }

    @Operation(summary = "Insights e alertas recentes")
    @GetMapping("/insights")
    public List<Map<String, Object>> insights() {
        return insights.recent(SecurityUtils.currentUserId()).stream()
                .map(i -> Map.<String, Object>of(
                        "kind", i.getKind().name(),
                        "answer", i.getAnswer() == null ? "" : i.getAnswer(),
                        "createdAt", i.getCreatedAt().toString()))
                .toList();
    }

    @Operation(summary = "Gera narrativa do mês")
    @PostMapping("/narrative/{year}/{month}")
    public Map<String, Object> narrative(@PathVariable int year, @PathVariable int month) {
        insights.monthlyNarrative(SecurityUtils.currentUserId(), year, month);
        return Map.of("status", "ok");
    }
}
