package br.com.investia.tax.api.dto;

import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.LocalDate;

public final class TaxDtos {

    private TaxDtos() {
    }

    public record TaxEventResponse(
            String id, String kind, LocalDate referenceMonth,
            BigDecimal taxableAmount, BigDecimal exemptAmount, BigDecimal taxAmount,
            String status, String description) {
    }

    public record DarfReport(
            LocalDate month, BigDecimal totalTaxDue,
            List<DarfItem> items) {
    }

    public record DarfItem(
            String kind, String darfCode, String description,
            BigDecimal taxableAmount, BigDecimal taxAmount, String status) {
    }

    public record ComeCotasResponse(int events, BigDecimal taxAmount) {
    }
}
