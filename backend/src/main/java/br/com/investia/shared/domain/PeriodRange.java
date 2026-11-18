package br.com.investia.shared.domain;

import java.time.LocalDate;

/** Value Object de período fechado [start, end]. */
public record PeriodRange(LocalDate start, LocalDate end) {

    public PeriodRange {
        if (start != null && end != null && start.isAfter(end)) {
            throw new IllegalArgumentException("Período inválido: start > end");
        }
    }

    public static PeriodRange ofMonth(int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        return new PeriodRange(start, start.withDayOfMonth(start.lengthOfMonth()));
    }

    /** Últimos n meses, incluindo o mês corrente. */
    public static PeriodRange lastMonths(int months, LocalDate reference) {
        LocalDate end = reference;
        LocalDate start = reference.minusMonths(months - 1L).withDayOfMonth(1);
        return new PeriodRange(start, end);
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(start) && !date.isAfter(end);
    }

    public long days() {
        return java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
    }
}
