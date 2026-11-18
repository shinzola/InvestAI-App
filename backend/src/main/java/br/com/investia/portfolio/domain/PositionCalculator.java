package br.com.investia.portfolio.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Serviço de domínio (puro): preço médio e resultado realizado.
 * Fórmula do requisito:
 *   novo_preco_medio = (qtd_atual × pm_atual + qtd_comprada × preco_compra)
 *                      / (qtd_atual + qtd_comprada)
 */
public final class PositionCalculator {

    private PositionCalculator() {
    }

    /** Novo preço médio após uma compra. */
    public static BigDecimal averagePriceAfterBuy(BigDecimal currentQty, BigDecimal currentAvg,
                                                  BigDecimal buyQty, BigDecimal buyPrice) {
        BigDecimal newQty = currentQty.add(buyQty);
        if (newQty.signum() == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal cost = currentQty.multiply(currentAvg).add(buyQty.multiply(buyPrice));
        return cost.divide(newQty, 8, RoundingMode.HALF_UP);
    }

    /** Resultado realizado de uma venda (preço − preço médio) × qtd − taxas. */
    public static BigDecimal realizedResult(BigDecimal sellQty, BigDecimal sellPrice,
                                            BigDecimal averagePrice, BigDecimal fees) {
        return sellPrice.subtract(averagePrice).multiply(sellQty)
                .subtract(fees)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
