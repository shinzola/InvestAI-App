package br.com.investia.shared.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Value Object de percentual (0–100), escala 2. */
public record Percentage(BigDecimal value) {

    public Percentage {
        value = value.setScale(2, RoundingMode.HALF_UP);
    }

    public static Percentage of(String value) {
        return new Percentage(new BigDecimal(value));
    }

    public static Percentage of(BigDecimal value) {
        return new Percentage(value);
    }

    public static Percentage zero() {
        return new Percentage(BigDecimal.ZERO);
    }

    /** Fator decimal (ex.: 25.50 -> 0.255). */
    public BigDecimal asDecimal() {
        return value.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
    }

    public boolean isAbove(Percentage other) {
        return value.compareTo(other.value) > 0;
    }

    @Override
    public String toString() {
        return value.toPlainString() + "%";
    }
}
