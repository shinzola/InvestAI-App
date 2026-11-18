package br.com.investia.portfolio.api;

import br.com.investia.identity.infrastructure.security.SecurityUtils;
import br.com.investia.portfolio.api.dto.PortfolioDtos.*;
import br.com.investia.portfolio.application.PortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Tag(name = "Carteira", description = "Ativos, operações, posições e cotações")
@RestController
@RequestMapping("/api/v1/portfolio")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class PortfolioController {

    private final PortfolioService service;

    // ---- Ativos ----

    @Operation(summary = "Lista ativos")
    @GetMapping("/assets")
    public List<AssetResponse> assets() {
        return service.listAssets(SecurityUtils.currentUserId());
    }

    @Operation(summary = "Cadastra ativo")
    @PostMapping("/assets")
    @ResponseStatus(HttpStatus.CREATED)
    public AssetResponse createAsset(@Valid @RequestBody AssetRequest req) {
        return service.createAsset(SecurityUtils.currentUserId(), req);
    }

    @Operation(summary = "Remove ativo")
    @DeleteMapping("/assets/{id}")
    public ResponseEntity<Void> deleteAsset(@PathVariable UUID id) {
        service.deleteAsset(SecurityUtils.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    // ---- Operações ----

    @Operation(summary = "Lista operações (opcionalmente por ativo)")
    @GetMapping("/operations")
    public List<OperationResponse> operations(@RequestParam(required = false) UUID assetId) {
        return service.listOperations(SecurityUtils.currentUserId(), assetId);
    }

    @Operation(summary = "Registra operação (compra/venda) e consolida posição")
    @PostMapping("/operations")
    @ResponseStatus(HttpStatus.CREATED)
    public OperationResponse registerOperation(@Valid @RequestBody OperationRequest req) {
        return service.registerOperation(SecurityUtils.currentUserId(), req);
    }

    // ---- Posições ----

    @Operation(summary = "Posições consolidadas com P&L")
    @GetMapping("/positions")
    public List<PositionResponse> positions() {
        return service.listPositions(SecurityUtils.currentUserId());
    }

    // ---- Cotações ----

    @Operation(summary = "Registra cotação do dia")
    @PostMapping("/assets/{id}/quotes")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<Void> registerQuote(@PathVariable UUID id, @Valid @RequestBody QuoteRequest req) {
        service.registerQuote(SecurityUtils.currentUserId(), id, req);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(summary = "Histórico de cotações")
    @GetMapping("/assets/{id}/quotes")
    public List<QuoteView> quotes(@PathVariable UUID id,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return service.quotes(SecurityUtils.currentUserId(), id, start, end).stream()
                .map(q -> new QuoteView(q.getDate(), q.getPrice(), q.getSource()))
                .toList();
    }

    public record QuoteView(LocalDate date, java.math.BigDecimal price, String source) {
    }
}
