package br.com.investia.portfolio.application;

import br.com.investia.identity.application.UserDataContributor;
import br.com.investia.portfolio.api.dto.PortfolioDtos.*;
import br.com.investia.portfolio.application.events.OperationRegisteredEvent;
import br.com.investia.portfolio.domain.*;
import br.com.investia.portfolio.infrastructure.jpa.*;
import br.com.investia.shared.domain.ApiException;
import br.com.investia.shared.domain.BusinessException;
import br.com.investia.shared.domain.Money;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * Casos de uso da carteira: ativos, operações (com consolidação de preço
 * médio), posições e cotações.
 */
@Service
@RequiredArgsConstructor
public class PortfolioService implements UserDataContributor {

    private final AssetJpaRepository assets;
    private final AssetQuoteJpaRepository quotes;
    private final OperationJpaRepository operations;
    private final PositionJpaRepository positions;
    private final ApplicationEventPublisher events;

    // ---------- Ativos ----------

    @Transactional(readOnly = true)
    public List<AssetResponse> listAssets(UUID userId) {
        return assets.findAllByUserIdOrderByTickerAsc(userId).stream()
                .map(AssetResponse::of).toList();
    }

    @Transactional
    public AssetResponse createAsset(UUID userId, AssetRequest req) {
        assets.findByUserIdAndTickerIgnoreCase(userId, req.ticker().trim())
                .ifPresent(a -> {
                    throw new BusinessException("Ativo já cadastrado");
                });
        Asset asset = assets.save(Asset.builder()
                .userId(userId)
                .ticker(req.ticker().trim().toUpperCase())
                .name(req.name().trim())
                .type(req.type())
                .sector(req.sector())
                .build());
        return AssetResponse.of(asset);
    }

    @Transactional
    public void deleteAsset(UUID userId, UUID id) {
        Asset asset = findAsset(userId, id);
        assets.delete(asset);
    }

    // ---------- Operações ----------

    @Transactional
    public OperationResponse registerOperation(UUID userId, OperationRequest req) {
        Asset asset = findAsset(userId, req.assetId());
        BigDecimal fees = req.fees() == null ? BigDecimal.ZERO : req.fees();
        Operation operation = Operation.builder()
                .userId(userId)
                .assetId(asset.getId())
                .type(req.type())
                .quantity(req.quantity())
                .price(req.price())
                .fees(Money.of(fees))
                .date(req.date())
                .broker(req.broker())
                .dayTrade(req.dayTrade())
                .build();
        operation.setTotal(operation.computeTotal());
        operation = operations.save(operation);

        updatePosition(userId, operation);

        events.publishEvent(new OperationRegisteredEvent(
                userId, operation.getId(), asset.getId(), asset.getTicker(),
                asset.getType().name(), operation.getType(),
                operation.getQuantity(), operation.getPrice(),
                operation.getTotal().value(), fees,
                positions.findByUserIdAndAssetId(userId, asset.getId())
                        .map(p -> p.getAveragePrice()).orElse(BigDecimal.ZERO),
                positions.findByUserIdAndAssetId(userId, asset.getId())
                        .map(p -> p.getCreatedAt()).orElse(null),
                operation.isDayTrade(), operation.getDate()));

        return toOperationResponse(operation, asset);
    }

    /**
     * Consolida a posição com a fórmula do requisito:
     * pm = (qtd_atual × pm_atual + qtd_comprada × preço) / (qtd_atual + qtd_comprada).
     * Vendas: preço médio inalterado; resultado realizado acumula.
     */
    private void updatePosition(UUID userId, Operation op) {
        Position position = positions.findByUserIdAndAssetId(userId, op.getAssetId())
                .orElseGet(() -> Position.builder().userId(userId).assetId(op.getAssetId()).build());

        if (op.getType() == OperationType.BUY) {
            BigDecimal newAvg = PositionCalculator.averagePriceAfterBuy(
                    position.getQuantity(), position.getAveragePrice(),
                    op.getQuantity(), op.getPrice());
            BigDecimal newQty = position.getQuantity().add(op.getQuantity());
            position.setAveragePrice(newAvg);
            position.setQuantity(newQty);
            position.setTotalInvested(position.getTotalInvested()
                    .add(Money.of(op.getQuantity().multiply(op.getPrice()))).add(op.getFees()));
        } else {
            if (position.getQuantity().compareTo(op.getQuantity()) < 0) {
                throw new BusinessException("Venda maior que a posição atual");
            }
            BigDecimal realized = PositionCalculator.realizedResult(
                    op.getQuantity(), op.getPrice(), position.getAveragePrice(), op.getFees().value());
            position.setQuantity(position.getQuantity().subtract(op.getQuantity()));
            position.setRealizedResult(position.getRealizedResult().add(Money.of(realized)));
            position.setTotalInvested(position.getTotalInvested()
                    .subtract(Money.of(op.getQuantity().multiply(position.getAveragePrice()))));
        }
        positions.save(position);
    }

    @Transactional(readOnly = true)
    public List<OperationResponse> listOperations(UUID userId, UUID assetId) {
        List<Operation> ops = assetId == null
                ? operations.findAllByUserIdOrderByDateAsc(userId)
                : operations.findAllByUserIdAndAssetIdOrderByDateAsc(userId, assetId);
        return ops.stream().map(o -> {
            Asset a = assets.findById(o.getAssetId()).orElse(null);
            return toOperationResponse(o, a);
        }).toList();
    }

    // ---------- Posições ----------

    /** Posições consolidadas com valor de mercado e P&L aberto. */
    @Transactional(readOnly = true)
    public List<PositionResponse> listPositions(UUID userId) {
        List<PositionResponse> result = new ArrayList<>();
        for (Position p : positions.findAllByUserId(userId)) {
            if (p.getQuantity().signum() == 0 && p.getRealizedResult().isPositive()) {
                // posição zerada mas com resultado realizado: exibe como linha de histórico
            }
            Asset asset = assets.findById(p.getAssetId()).orElse(null);
            if (asset == null) {
                continue;
            }
            BigDecimal currentPrice = quotes.findFirstByAssetIdOrderByDateDesc(asset.getId())
                    .map(q -> q.getPrice()).orElse(p.getAveragePrice());
            BigDecimal marketValue = p.getQuantity().multiply(currentPrice)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal invested = p.getAveragePrice().multiply(p.getQuantity())
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal unrealized = marketValue.subtract(invested);
            double pct = invested.signum() == 0
                    ? 0
                    : unrealized.multiply(BigDecimal.valueOf(100)).divide(invested, 2, RoundingMode.HALF_UP).doubleValue();
            result.add(new PositionResponse(asset.getId().toString(), asset.getTicker(),
                    asset.getName(), asset.getType().name(), p.getQuantity(), p.getAveragePrice(),
                    currentPrice, marketValue, invested, unrealized, pct, p.getRealizedResult().value()));
        }
        return result;
    }

    // ---------- Cotações ----------

    @Transactional
    public void registerQuote(UUID userId, UUID assetId, QuoteRequest req) {
        findAsset(userId, assetId);
        AssetQuote quote = quotes.findByAssetIdAndDate(assetId, req.date())
                .orElseGet(() -> AssetQuote.builder().assetId(assetId).date(req.date()).build());
        quote.setUserId(userId);
        quote.setPrice(req.price());
        quote.setSource(req.source() == null ? "MANUAL" : req.source());
        quotes.save(quote);
    }

    @Transactional(readOnly = true)
    public List<AssetQuote> quotes(UUID userId, UUID assetId, LocalDate start, LocalDate end) {
        findAsset(userId, assetId);
        return quotes.findAllByAssetIdAndDateBetweenOrderByDateAsc(assetId, start, end);
    }

    private Asset findAsset(UUID userId, UUID id) {
        Asset asset = assets.findById(id)
                .orElseThrow(() -> ApiException.notFound("Ativo não encontrado"));
        if (!asset.getUserId().equals(userId)) {
            throw ApiException.notFound("Ativo não encontrado");
        }
        return asset;
    }

    private OperationResponse toOperationResponse(Operation o, Asset a) {
        return new OperationResponse(o.getId().toString(), o.getAssetId().toString(),
                a == null ? "?" : a.getTicker(), o.getType().name(), o.getQuantity(),
                o.getPrice(), o.getFees().value(), o.getTotal().value(), o.getDate(),
                o.getBroker(), o.isDayTrade());
    }

    // ---------- LGPD ----------

    @Override
    public String key() {
        return "operacoes";
    }

    @Override
    public List<Map<String, Object>> collect(UUID userId) {
        return listOperations(userId, null).stream()
                .map(o -> Map.<String, Object>of(
                        "data", o.date().toString(), "ticker", o.ticker(),
                        "tipo", o.type(), "quantidade", String.valueOf(o.quantity()),
                        "preco", String.valueOf(o.price())))
                .toList();
    }
}
