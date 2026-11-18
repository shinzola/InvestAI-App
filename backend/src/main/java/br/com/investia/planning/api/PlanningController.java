package br.com.investia.planning.api;

import br.com.investia.identity.infrastructure.security.SecurityUtils;
import br.com.investia.planning.api.dto.PlanningDtos.*;
import br.com.investia.planning.application.PlanningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Planejamento", description = "Gastos fixos e individuais do mês, por data")
@RestController
@RequestMapping("/api/v1/planning")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class PlanningController {

    private final PlanningService planning;

    @Operation(summary = "Lista os gastos planejados do mês, ordenados por data")
    @GetMapping
    public List<PlannedExpenseResponse> list(@RequestParam int year, @RequestParam int month) {
        return planning.list(SecurityUtils.currentUserId(), year, month);
    }

    @Operation(summary = "Totais do mês e quanto sobra para aportar")
    @GetMapping("/summary")
    public PlanningSummary summary(@RequestParam int year, @RequestParam int month) {
        return planning.summary(SecurityUtils.currentUserId(), year, month);
    }

    @Operation(summary = "Planeja um gasto")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlannedExpenseResponse create(@Valid @RequestBody PlannedExpenseRequest req) {
        return planning.create(SecurityUtils.currentUserId(), req);
    }

    @Operation(summary = "Atualiza um gasto planejado")
    @PutMapping("/{id}")
    public PlannedExpenseResponse update(@PathVariable UUID id,
                                         @Valid @RequestBody PlannedExpenseRequest req) {
        return planning.update(SecurityUtils.currentUserId(), id, req);
    }

    @Operation(summary = "Marca como pago ou volta para previsto")
    @PatchMapping("/{id}/paid")
    public PlannedExpenseResponse togglePaid(@PathVariable UUID id) {
        return planning.togglePaid(SecurityUtils.currentUserId(), id);
    }

    @Operation(summary = "Remove um gasto planejado")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        planning.delete(SecurityUtils.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
