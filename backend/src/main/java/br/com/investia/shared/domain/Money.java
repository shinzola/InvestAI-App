package br.com.investia.shared.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Value Object monetário. Escala 2, arredondamento HALF_UP.
 * Nunca represente dinheiro com float/double.
 */
public record Money(BigDecimal value) implements Comparable<Money> {

    public static final Money ZERO = new Money(BigDecimal.ZERO);

    public Money {
        value = value.setScale(2, RoundingMode.HALF_UP);
    }

    public static Money of(String value) {
        return new Money(new BigDecimal(value));
    }

    public static Money of(long value) {
        return new Money(BigDecimal.valueOf(value));
    }

    public static Money of(BigDecimal value) {
        return new Money(value);
    }

    public Money add(Money other) {
        return new Money(value.add(other.value));
    }

    public Money subtract(Money other) {
        return new Money(value.subtract(other.value));
    }

    public Money multiply(BigDecimal factor) {
        return new Money(value.multiply(factor).setScale(2, RoundingMode.HALF_UP));
    }

    public Money multiply(long factor) {
        return new Money(value.multiply(BigDecimal.valueOf(factor)));
    }

    public Money abs() {
        return new Money(value.abs());
    }

    public boolean isNegative() {
        return value.signum() < 0;
    }

    public boolean isPositive() {
        return value.signum() > 0;
    }

    public boolean isZero() {
        return value.signum() == 0;
    }

    /** Divide por divisor, preservando escala 2 (HALF_UP). */
    public Money divideBy(BigDecimal divisor) {
        return new Money(value.divide(divisor, 2, RoundingMode.HALF_UP));
    }

    /** Percentual de "this" sobre "total", arredondado a 2 casas. */
    public Percentage shareOf(Money total) {
        if (total.isZero()) {
            return Percentage.zero();
        }
        return Percentage.of(value.multiply(BigDecimal.valueOf(100))
                .divide(total.value, 2, RoundingMode.HALF_UP));
    }

    @Override
    public int compareTo(Money other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toPlainString();
    }
}
