package br.com.investia.tax.application;

import br.com.investia.earnings.application.events.DividendRegisteredEvent;
import br.com.investia.earnings.domain.DividendType;
import br.com.investia.earnings.infrastructure.jpa.DividendJpaRepository;
import br.com.investia.portfolio.application.OperationQueryPort;
import br.com.investia.portfolio.application.events.OperationRegisteredEvent;
import br.com.investia.portfolio.domain.OperationType;
import br.com.investia.shared.domain.PeriodRange;
import br.com.investia.shared.domain.Money;
import br.com.investia.shared.domain.Percentage;
import br.com.investia.tax.api.dto.TaxDtos.*;
import br.com.investia.tax.domain.TaxEvent;
import br.com.investia.tax.domain.TaxEventKind;
import br.com.investia.tax.infrastructure.jpa.TaxEventJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Apuração tributária reativa a eventos de operações e proventos.
 * Códigos DARF usados (conferir com a tabela vigente da Receita):
 *  6015 — ganhos em renda variável (operações comuns)
 *  0685 — ganhos em renda variável (day trade)
 *  1188 — IR sobre aplicações de renda fixa
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaxService {

    private final TaxEventJpaRepository taxEvents;
    private final OperationQueryPort operations;
    private final DividendJpaRepository dividends;

    // ---------- Reações a eventos ----------

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOperation(OperationRegisteredEvent event) {
        if (event.type() != OperationType.SELL) {
            return;
        }
        String type = event.assetType();
        BigDecimal gain = TaxRules.capitalGain(event.price(),
                event.averagePriceAtSale(), event.quantity(), event.fees());
        LocalDate month = event.date().withDayOfMonth(1);

        TaxRules.GainTax result;
        switch (type) {
            case "ACAO", "ETF" -> {
                var period = PeriodRange.ofMonth(event.date().getYear(), event.date().getMonthValue());
                BigDecimal salesTotal = operations.salesTotal(event.userId(), period.start(), period.end());
                result = TaxRules.onStockSale(gain, salesTotal, event.dayTrade());
            }
            case "FII" -> result = TaxRules.onFiiSale(gain);
            case "CDB", "TESOURO_DIRETO", "DEBENTURE", "PREVIDENCIA" -> result = TaxRules.onFixedIncome(
                    gain, TaxRules.holdingDays(
                            event.positionOpenedAt() == null ? null : event.positionOpenedAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
                            event.date()), false);
            case "LCI_LCA" -> result = TaxRules.onFixedIncome(gain, 9999, true);
            default -> result = new TaxRules.GainTax(gain, BigDecimal.ZERO, BigDecimal.ZERO, gain);
        }

        boolean exempt = result.tax().signum() == 0;
        taxEvents.save(TaxEvent.builder()
                .userId(event.userId())
                .assetId(event.assetId())
                .operationId(event.operationId())
                .kind(TaxEventKind.GANHO_CAPITAL_VARIAVEL)
                .referenceMonth(month)
                .taxableAmount(Money.of(result.gain()))
                .exemptAmount(Money.of(result.exemptAmount()))
                .taxAmount(Money.of(result.tax()))
                .status(exempt ? TaxEvent.TaxStatus.ISENTO : TaxEvent.TaxStatus.PENDENTE)
                .description("Venda %s %s @ %s".formatted(event.ticker(),
                        event.quantity().stripTrailingZeros().toPlainString(), event.price()))
                .build());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDividend(DividendRegisteredEvent event) {
        TaxEventKind kind = event.type() == DividendType.JCP ? TaxEventKind.JCP : TaxEventKind.DIVIDENDO;
        // Dividendos e rendimentos de FII: isentos para PF; JCP: IR retido na fonte
        boolean isJcp = kind == TaxEventKind.JCP;
        taxEvents.save(TaxEvent.builder()
                .userId(event.userId())
                .assetId(event.assetId())
                .kind(kind)
                .referenceMonth(event.date().withDayOfMonth(1))
                .taxableAmount(Money.of(event.amount()))
                .exemptAmount(isJcp ? Money.ZERO : Money.of(event.amount()))
                .taxAmount(isJcp ? Money.of(event.withholdingTax()) : Money.ZERO)
                .status(isJcp ? TaxEvent.TaxStatus.PENDENTE : TaxEvent.TaxStatus.ISENTO)
                .description("%s %s".formatted(event.type().name(), event.ticker()))
                .build());
    }

    // ---------- Come-cotas ----------

    /** Apura come-cotas do mês sobre rendimentos de FII (20%) e fundos (15%). */
    @Transactional
    public ComeCotasResponse calculateComeCotas(UUID userId, int year, int month) {
        var period = PeriodRange.ofMonth(year, month);
        BigDecimal totalTax = BigDecimal.ZERO;
        int count = 0;
        for (var d : dividends.findAllByUserIdAndDateBetweenOrderByDateAsc(userId, period.start(), period.end())) {
            if (d.getType() != DividendType.RENDIMENTO_FII) {
                continue;
            }
            var result = TaxRules.comeCotas(d.getAmount().value(), true);
            taxEvents.save(TaxEvent.builder()
                    .userId(userId)
                    .assetId(d.getAssetId())
                    .kind(TaxEventKind.COME_COTAS)
                    .referenceMonth(period.start())
                    .taxableAmount(Money.of(result.gain()))
                    .taxAmount(Money.of(result.tax()))
                    .status(TaxEvent.TaxStatus.PENDENTE)
                    .description("Come-cotas FII")
                    .build());
            totalTax = totalTax.add(result.tax());
            count++;
        }
        return new ComeCotasResponse(count, Money.of(totalTax).value());
    }

    // ---------- Relatório DARF ----------

    @Transactional(readOnly = true)
    public DarfReport darf(UUID userId, int year, int month) {
        LocalDate ref = LocalDate.of(year, month, 1);
        List<DarfItem> items = new ArrayList<>();
        BigDecimal totalDue = BigDecimal.ZERO;

        for (TaxEvent e : taxEvents.findAllByUserIdAndReferenceMonthOrderByCreatedAtAsc(userId, ref)) {
            if (e.getStatus() == TaxEvent.TaxStatus.ISENTO || e.getTaxAmount().isZero()) {
                continue;
            }
            items.add(new DarfItem(e.getKind().name(), darfCode(e.getKind()),
                    e.getDescription(), e.getTaxableAmount().value(), e.getTaxAmount().value(),
                    e.getStatus().name()));
            totalDue = totalDue.add(e.getTaxAmount().value());
        }
        return new DarfReport(ref, Money.of(totalDue).value(), items);
    }

    private String darfCode(TaxEventKind kind) {
        return switch (kind) {
            case GANHO_CAPITAL_VARIAVEL -> "6015"; // day trade seria 0685
            case IR_RENDA_FIXA, COME_COTAS -> "1188";
            default -> "6015";
        };
    }

    @Transactional(readOnly = true)
    public List<TaxEventResponse> list(UUID userId, int year, int month) {
        LocalDate ref = LocalDate.of(year, month, 1);
        return taxEvents.findAllByUserIdAndReferenceMonthOrderByCreatedAtAsc(userId, ref).stream()
                .map(e -> new TaxEventResponse(e.getId().toString(), e.getKind().name(),
                        e.getReferenceMonth(), e.getTaxableAmount().value(),
                        e.getExemptAmount().value(), e.getTaxAmount().value(),
                        e.getStatus().name(), e.getDescription()))
                .toList();
    }
}
