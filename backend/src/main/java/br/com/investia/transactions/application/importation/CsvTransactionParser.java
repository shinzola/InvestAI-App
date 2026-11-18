package br.com.investia.transactions.application.importation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser CSV flexível: aceita cabeçalho "date;description;amount" com
 * separador ; ou , e datas dd/MM/yyyy ou yyyy-MM-dd.
 */
public class CsvTransactionParser {

    public record CsvTransaction(LocalDate date, String description, BigDecimal amount) {
    }

    public List<CsvTransaction> parse(String content) {
        List<CsvTransaction> result = new ArrayList<>();
        for (String line : content.lines().toList()) {
            if (line.isBlank() || line.toLowerCase().startsWith("date") || line.toLowerCase().startsWith("data")) {
                continue;
            }
            String[] cols = line.split("[;,]", -1);
            if (cols.length < 3) {
                continue;
            }
            try {
                result.add(new CsvTransaction(
                        parseDate(cols[0].trim()),
                        cols[1].trim(),
                        parseAmount(cols[2].trim())));
            } catch (Exception ignored) {
                // linha malformada é ignorada e reportada como skip
            }
        }
        return result;
    }

    /** Aceita "1.234,56" (BR) e "1234.56" (ISO). */
    private BigDecimal parseAmount(String raw) {
        String value = raw.contains(",")
                ? raw.replace(".", "").replace(",", ".")
                : raw;
        return new BigDecimal(value);
    }

    private LocalDate parseDate(String value) {
        for (String pattern : new String[]{"dd/MM/yyyy", "yyyy-MM-dd", "dd-MM-yyyy"}) {
            try {
                return LocalDate.parse(value, DateTimeFormatter.ofPattern(pattern));
            } catch (Exception ignored) {
                // tenta próximo padrão
            }
        }
        throw new IllegalArgumentException("Data inválida: " + value);
    }
}
