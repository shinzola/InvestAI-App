package br.com.investia.portfolio.api.dto;

import java.util.UUID;

import br.com.investia.portfolio.domain.Asset;
import br.com.investia.portfolio.domain.AssetType;
import br.com.investia.portfolio.domain.OperationType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class PortfolioDtos {

    private PortfolioDtos() {
    }

    public record AssetRequest(
            @NotBlank @Size(max = 20) String ticker,
            @NotBlank @Size(max = 160) String name,
            @NotNull AssetType type,
            @Size(max = 80) String sector) {
    }

    public record AssetResponse(
            String id, String ticker, String name, String type, String sector) {

        public static AssetResponse of(Asset a) {
            return new AssetResponse(a.getId().toString(), a.getTicker(), a.getName(),
                    a.getType().name(), a.getSector());
        }
    }

    public record OperationRequest(
            @NotNull UUID assetId,
            @NotNull OperationType type,
            @NotNull @DecimalMin("0.00000001") BigDecimal quantity,
            @NotNull @DecimalMin("0.00000001") BigDecimal price,
            @DecimalMin("0.00") BigDecimal fees,
            @NotNull LocalDate date,
            @Size(max = 80) String broker,
            boolean dayTrade) {
    }

    public record OperationResponse(
            String id, String assetId, String ticker, String type,
            BigDecimal quantity, BigDecimal price, BigDecimal fees,
            BigDecimal total, LocalDate date, String broker, boolean dayTrade) {
    }

    public record PositionResponse(
            String assetId, String ticker, String name, String type,
            BigDecimal quantity, BigDecimal averagePrice, BigDecimal currentPrice,
            BigDecimal marketValue, BigDecimal invested, BigDecimal unrealizedResult,
            double unrealizedPct, BigDecimal realizedResult) {
    }

    public record QuoteRequest(
            @NotNull LocalDate date,
            @NotNull @DecimalMin("0.00000001") BigDecimal price,
            String source) {
    }
}
