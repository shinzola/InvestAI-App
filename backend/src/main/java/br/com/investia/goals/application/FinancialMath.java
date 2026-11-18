package br.com.investia.goals.application;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Matemática financeira pura (serviço de domínio):
 * juros compostos e aporte necessário (PMT) para atingir uma meta.
 */
public final class FinancialMath {

    private FinancialMath() {
    }

    /** Taxa anual -> taxa mensal equivalente: (1+i_anual)^(1/12) − 1. */
    public static BigDecimal annualToMonthly(BigDecimal annualRatePct) {
        double a = annualRatePct.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP).doubleValue();
        double m = Math.pow(1 + a, 1.0 / 12) - 1;
        return BigDecimal.valueOf(m).setScale(8, RoundingMode.HALF_UP);
    }

    /**
     * Aporte mensal (PMT) para atingir target em n meses a taxa i mensal:
     * PMT = FV × i / ((1+i)^n − 1). Com i = 0, PMT = FV / n.
     */
    public static BigDecimal requiredMonthlyContribution(BigDecimal target, BigDecimal monthlyRate,
                                                         int months) {
        if (months <= 0) {
            return BigDecimal.ZERO;
        }
        if (monthlyRate.signum() == 0) {
            return target.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
        }
        double i = monthlyRate.doubleValue();
        double factor = Math.pow(1 + i, months) - 1;
        return target.multiply(monthlyRate)
                .divide(BigDecimal.valueOf(factor), 2, RoundingMode.HALF_UP);
    }

    /** FV de um valor inicial capitalizado por n meses. */
    public static BigDecimal futureValue(BigDecimal present, BigDecimal monthlyRate, int months) {
        double fv = present.doubleValue() * Math.pow(1 + monthlyRate.doubleValue(), months);
        return BigDecimal.valueOf(fv).setScale(2, RoundingMode.HALF_UP);
    }

    /** Corrige valor nominal pela inflação acumulada (valor real). */
    public static BigDecimal deflate(BigDecimal nominal, BigDecimal monthlyInflation, int months) {
        double real = nominal.doubleValue() / Math.pow(1 + monthlyInflation.doubleValue(), months);
        return BigDecimal.valueOf(real).setScale(2, RoundingMode.HALF_UP);
    }

    /** Composto discreto: base × (1 + taxa)^n (usado em testes com MathContext). */
    public static BigDecimal compound(BigDecimal base, BigDecimal monthlyRate, int months) {
        return base.multiply(BigDecimal.ONE.add(monthlyRate).pow(months, MathContext.DECIMAL64))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
