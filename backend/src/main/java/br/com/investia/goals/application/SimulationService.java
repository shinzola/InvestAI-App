package br.com.investia.goals.application;

import br.com.investia.goals.api.dto.GoalDtos.*;
import br.com.investia.shared.domain.Money;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Simulador de juros compostos com correção pela inflação (IPCA).
 * Retorna série mês a mês com saldo nominal, investido e saldo real.
 */
@Service
public class SimulationService {

    public SimulationResponse simulate(SimulationRequest req) {
        BigDecimal monthlyRate = FinancialMath.annualToMonthly(
                req.annualRate() == null ? new BigDecimal("10.00") : req.annualRate());
        BigDecimal monthlyInflation = FinancialMath.annualToMonthly(
                req.annualInflation() == null ? BigDecimal.ZERO : req.annualInflation());

        int months = req.years() * 12;
        BigDecimal balance = req.initialAmount();
        BigDecimal invested = req.initialAmount();

        List<SimulationPoint> series = new ArrayList<>();
        for (int m = 1; m <= months; m++) {
            balance = balance.add(req.monthlyContribution());
            invested = invested.add(req.monthlyContribution());
            balance = Money.of(balance).multiply(BigDecimal.ONE.add(monthlyRate)).value();
            series.add(new SimulationPoint(m, balance, invested,
                    FinancialMath.deflate(balance, monthlyInflation, m)));
        }

        BigDecimal totalInterest = Money.of(balance).subtract(Money.of(invested)).value();
        return new SimulationResponse(balance, Money.of(invested).value(), totalInterest,
                series.isEmpty() ? balance : series.get(series.size() - 1).realBalance(), series);
    }
}
