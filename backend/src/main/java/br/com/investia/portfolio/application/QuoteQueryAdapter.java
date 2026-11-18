package br.com.investia.portfolio.application;

import br.com.investia.portfolio.domain.Asset;
import br.com.investia.portfolio.infrastructure.jpa.AssetJpaRepository;
import br.com.investia.portfolio.infrastructure.jpa.AssetQuoteJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuoteQueryAdapter implements QuoteQueryPort {

    private final AssetJpaRepository assets;
    private final AssetQuoteJpaRepository quotes;

    @Override
    @Transactional(readOnly = true)
    public List<QuotePoint> quotesOfTicker(UUID userId, String ticker, LocalDate start, LocalDate end) {
        return assets.findByUserIdAndTickerIgnoreCase(userId, ticker)
                .map(asset -> quotes
                        .findAllByAssetIdAndDateBetweenOrderByDateAsc(asset.getId(), start, end)
                        .stream()
                        .map(q -> new QuotePoint(q.getDate(), q.getPrice()))
                        .toList())
                .orElse(List.of());
    }
}
