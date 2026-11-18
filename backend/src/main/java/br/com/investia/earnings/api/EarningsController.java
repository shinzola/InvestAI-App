package br.com.investia.earnings.api;

import br.com.investia.earnings.api.dto.EarningsDtos.*;
import br.com.investia.earnings.application.EarningsService;
import br.com.investia.identity.infrastructure.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Proventos", description = "Renda passiva: dividendos, JCP, rendimentos e cupons")
@RestController
@RequestMapping("/api/v1/earnings")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class EarningsController {

    private final EarningsService service;

    @Operation(summary = "Histórico de proventos")
    @GetMapping
    public List<DividendResponse> history() {
        return service.history(SecurityUtils.currentUserId());
    }

    @Operation(summary = "Registra provento")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DividendResponse register(@Valid @RequestBody DividendRequest req) {
        return service.register(SecurityUtils.currentUserId(), req);
    }

    @Operation(summary = "Resumo 12m: média, projeção e cobertura da meta")
    @GetMapping("/summary")
    public EarningsSummary summary() {
        return service.summary(SecurityUtils.currentUserId());
    }

    @Operation(summary = "Série mensal de renda passiva")
    @GetMapping("/monthly")
    public List<MonthlyEarnings> monthly(@RequestParam(defaultValue = "12") int months) {
        return service.monthlySeries(SecurityUtils.currentUserId(), months);
    }

    @Operation(summary = "Remove provento")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        service.delete(SecurityUtils.currentUserId(), id);
    }
}
