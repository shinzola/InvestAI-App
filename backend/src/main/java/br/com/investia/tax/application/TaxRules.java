package br.com.investia.tax.application;

import br.com.investia.shared.domain.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Regras tributárias brasileiras (serviço de domínio puro):
 *  - Ações (à vista): isenção de ganho se total de vendas no mês <= R$ 20.000;
 *    caso contrário 15% (swing) e 20% (day trade).
 *  - FIIs: 20%, sem isenção.
 *  - Renda fixa: alíquota regressiva por prazo (22,5% / 20% / 17,5% / 15%);
 *    LCI/LCA isentas.
 *  - Come-cotas: 15% (fundos longos) / 20% (FIIs), antecipação sobre rendimentos.
 */
public final class TaxRules {

    public static final BigDecimal MONTHLY_SALE_EXEMPTION = new BigDecimal("20000");
    public static final BigDecimal SWING_TRADE_RATE = new BigDecimal("15.00");
    public static final BigDecimal DAY_TRADE_RATE = new BigDecimal("20.00");
    public static final BigDecimal FII_RATE = new BigDecimal("20.00");

    private TaxRules() {
    }

    public record GainTax(BigDecimal gain, BigDecimal ratePct, BigDecimal tax,
                          BigDecimal exemptAmount) {
    }

    /** Ganho líquido de uma venda: (preço − preço médio) × qtd − taxas. */
    public static BigDecimal capitalGain(BigDecimal price, BigDecimal averagePrice,
                                         BigDecimal quantity, BigDecimal fees) {
        return price.subtract(averagePrice).multiply(quantity).subtract(fees)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** IR sobre venda de ações/ETF (vista). dayTrade ignora isenção mensal. */
    public static GainTax onStockSale(BigDecimal gain, BigDecimal monthSalesTotal, boolean dayTrade) {
        if (gain.signum() <= 0) {
            return new GainTax(gain, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        if (!dayTrade && monthSalesTotal.compareTo(MONTHLY_SALE_EXEMPTION) <= 0) {
            return new GainTax(gain, BigDecimal.ZERO, BigDecimal.ZERO, gain); // isento
        }
        BigDecimal rate = dayTrade ? DAY_TRADE_RATE : SWING_TRADE_RATE;
        return new GainTax(gain, rate, apply(gain, rate), BigDecimal.ZERO);
    }

    /** IR sobre venda de FII: 20%, sem isenção mensal. */
    public static GainTax onFiiSale(BigDecimal gain) {
        if (gain.signum() <= 0) {
            return new GainTax(gain, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        return new GainTax(gain, FII_RATE, apply(gain, FII_RATE), BigDecimal.ZERO);
    }

    /** IR de renda fixa regressivo pelo prazo em dias; LCI/LCA isentas. */
    public static GainTax onFixedIncome(BigDecimal gain, long holdingDays, boolean lciLca) {
        if (lciLca || gain.signum() <= 0) {
            return new GainTax(gain, BigDecimal.ZERO, BigDecimal.ZERO,
                    lciLca ? gain : BigDecimal.ZERO);
        }
        BigDecimal rate;
        if (holdingDays <= 180) {
            rate = new BigDecimal("22.50");
        } else if (holdingDays <= 360) {
            rate = new BigDecimal("20.00");
        } else if (holdingDays <= 720) {
            rate = new BigDecimal("17.50");
        } else {
            rate = new BigDecimal("15.00");
        }
        return new GainTax(gain, rate, apply(gain, rate), BigDecimal.ZERO);
    }

    /** Come-cotas: antecipação do IR sobre rendimentos. */
    public static GainTax comeCotas(BigDecimal earning, boolean fii) {
        BigDecimal rate = fii ? FII_RATE : new BigDecimal("15.00");
        return new GainTax(earning, rate, apply(earning, rate), BigDecimal.ZERO);
    }

    /** Dias de posse entre abertura da posição e a data da operação. */
    public static long holdingDays(LocalDate openedAt, LocalDate soldAt) {
        if (openedAt == null) {
            return 0;
        }
        return Math.max(0, ChronoUnit.DAYS.between(openedAt, soldAt));
    }

    private static BigDecimal apply(BigDecimal base, BigDecimal ratePct) {
        return base.multiply(ratePct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
