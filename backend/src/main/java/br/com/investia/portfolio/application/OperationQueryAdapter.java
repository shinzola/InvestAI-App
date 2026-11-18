package br.com.investia.portfolio.application;

import br.com.investia.portfolio.domain.OperationType;
import br.com.investia.portfolio.infrastructure.jpa.OperationJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OperationQueryAdapter implements OperationQueryPort {

    private final OperationJpaRepository operations;

    @Override
    @Transactional(readOnly = true)
    public BigDecimal salesTotal(UUID userId, LocalDate start, LocalDate end) {
        return operations.findAllByUserIdAndDateBetweenOrderByDateAsc(userId, start, end).stream()
                .filter(o -> o.getType() == OperationType.SELL)
                .map(o -> o.getTotal().value())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
