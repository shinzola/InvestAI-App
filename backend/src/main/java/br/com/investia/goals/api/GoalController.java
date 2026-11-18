package br.com.investia.goals.api;

import br.com.investia.goals.api.dto.GoalDtos.*;
import br.com.investia.goals.application.GoalService;
import br.com.investia.goals.application.SimulationService;
import br.com.investia.identity.infrastructure.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Metas", description = "Metas financeiras e simulador de juros compostos")
@RestController
@RequestMapping("/api/v1/goals")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class GoalController {

    private final GoalService goals;
    private final SimulationService simulator;

    @Operation(summary = "Lista metas com progresso")
    @GetMapping
    public java.util.List<GoalResponse> list() {
        return goals.list(SecurityUtils.currentUserId());
    }

    @Operation(summary = "Cria meta")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GoalResponse create(@Valid @RequestBody GoalRequest req) {
        return goals.create(SecurityUtils.currentUserId(), req);
    }

    @Operation(summary = "Atualiza meta")
    @PutMapping("/{id}")
    public GoalResponse update(@PathVariable UUID id, @Valid @RequestBody GoalRequest req) {
        return goals.update(SecurityUtils.currentUserId(), id, req);
    }

    @Operation(summary = "Registra aporte na meta")
    @PostMapping("/{id}/contributions")
    public GoalResponse contribute(@PathVariable UUID id, @Valid @RequestBody ContributionRequest req) {
        return goals.contribute(SecurityUtils.currentUserId(), id, req);
    }

    @Operation(summary = "Remove meta")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        goals.delete(SecurityUtils.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Simula juros compostos corrigidos pela inflação")
    @PostMapping("/simulate")
    public SimulationResponse simulate(@Valid @RequestBody SimulationRequest req) {
        return simulator.simulate(req);
    }
}
