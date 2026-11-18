package br.com.investia.budgeting.api;

import br.com.investia.budgeting.api.dto.BudgetDtos.*;
import br.com.investia.budgeting.application.BudgetService;
import br.com.investia.identity.infrastructure.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Orçamento", description = "Orçamento mensal por envelopes")
@RestController
@RequestMapping("/api/v1/budgets")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class BudgetController {

    private final BudgetService service;

    @Operation(summary = "Orçamento do mês com limites, gastos e alertas")
    @GetMapping("/{year}/{month}")
    public BudgetResponse get(@PathVariable int year, @PathVariable int month) {
        return service.getBudget(SecurityUtils.currentUserId(), year, month);
    }

    @Operation(summary = "Define/atualiza envelopes do mês")
    @PutMapping("/{year}/{month}")
    public BudgetResponse upsert(@PathVariable int year, @PathVariable int month,
                                 @Valid @RequestBody BudgetUpsertRequest req) {
        return service.upsertBudget(SecurityUtils.currentUserId(), year, month, req);
    }
}
