package br.com.investia.ai.application;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Categorização automática determinística (baseada em regras/palavras-chave).
 * Resultado é sugestão; o usuário confirma na UI.
 */
@Component
public class CategorizationRules {

    /** Palavras-chave → nome de categoria despesa padrão. */
    private static final List<Map.Entry<String, String>> EXPENSE_RULES = List.of(
            Map.entry("ifood", "Delivery"),
            Map.entry("ubereats", "Delivery"),
            Map.entry("rappi", "Delivery"),
            Map.entry("delivery", "Delivery"),
            Map.entry("restaurante", "Alimentação"),
            Map.entry("mercado", "Mercado"),
            Map.entry("supermercado", "Mercado"),
            Map.entry("padaria", "Mercado"),
            Map.entry("uber", "Transporte"),
            Map.entry("99app", "Transporte"),
            Map.entry("posto", "Transporte"),
            Map.entry("combustivel", "Transporte"),
            Map.entry("combustível", "Transporte"),
            Map.entry("aluguel", "Aluguel"),
            Map.entry("condominio", "Moradia"),
            Map.entry("condomínio", "Moradia"),
            Map.entry("luz", "Moradia"),
            Map.entry("agua", "Moradia"),
            Map.entry("água", "Moradia"),
            Map.entry("internet", "Moradia"),
            Map.entry("netflix", "Assinaturas"),
            Map.entry("spotify", "Assinaturas"),
            Map.entry("hbo", "Assinaturas"),
            Map.entry("disney", "Assinaturas"),
            Map.entry("farmacia", "Saúde"),
            Map.entry("farmácia", "Saúde"),
            Map.entry("medico", "Saúde"),
            Map.entry("médico", "Saúde"),
            Map.entry("dentista", "Saúde"),
            Map.entry("academia", "Saúde"),
            Map.entry("escola", "Educação"),
            Map.entry("faculdade", "Educação"),
            Map.entry("curso", "Educação"),
            Map.entry("livro", "Educação"));

    private static final List<String> INCOME_KEYWORDS =
            List.of("salario", "salário", "pix recebido", "deposito", "depósito", "dividendo", "jcp");

    public Optional<String> suggestCategory(String description, boolean income) {
        String normalized = normalize(description);
        if (income) {
            return INCOME_KEYWORDS.stream()
                    .filter(normalized::contains)
                    .map(k -> (String) "Salário")
                    .findFirst()
                    .or(() -> Optional.of("Investimentos").filter(k -> normalized.contains("dividendo")));
        }
        return EXPENSE_RULES.stream()
                .filter(rule -> normalized.contains(normalize(rule.getKey())))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
