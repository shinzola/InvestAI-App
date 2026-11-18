package br.com.investia.ai.application;

import br.com.investia.planning.api.dto.PlanningDtos.PlannedExpenseRequest;
import br.com.investia.planning.api.dto.PlanningDtos.PlannedExpenseResponse;
import br.com.investia.planning.application.PlanningService;
import br.com.investia.planning.domain.PlannedExpenseKind;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lê a fatura enviada (PDF ou texto/CSV) e transforma as linhas em gastos
 * planejados. A extração é determinística; nada é inventado.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceImportService {

    private static final Pattern AMOUNT_AT_END = Pattern.compile(
            "(\\d{1,3}(?:\\.\\d{3})*,\\d{2}|\\d+\\.\\d{2})\\s*$");
    private static final Pattern DATE_IN_LINE = Pattern.compile(
            "(\\d{2}/\\d{2}(?:/\\d{4})?)");
    private static final Pattern NOISE = Pattern.compile(
            "(?i)\\b(total|subtotal|saldo|limite|disponível|disponivel|pagamento|"
                    + "vencimento|fechamento|fatura|encargos|juros|iof|anuidade|resumo)\\b");
    private static final int MAX_ITEMS = 100;

    private final PlanningService planning;

    public record ImportedItem(String id, String description, BigDecimal amount, LocalDate dueDate) {
    }

    public record ImportResult(String fileName, int extracted, int created, int skipped,
                               List<ImportedItem> items, String preview) {
    }

    @Transactional
    public ImportResult importInvoice(UUID userId, MultipartFile file, UUID cardAccountId,
                                      int year, int month) throws IOException {
        String text = extractText(file);
        List<ImportedItem> created = new ArrayList<>();
        int skipped = 0;

        for (String rawLine : text.split("\\R")) {
            String line = rawLine.trim();
            if (line.length() < 4 || NOISE.matcher(line).find()) {
                continue;
            }
            Matcher amountMatcher = AMOUNT_AT_END.matcher(line);
            if (!amountMatcher.find()) {
                continue;
            }
            BigDecimal amount = toAmount(amountMatcher.group(1));
            if (amount == null || amount.signum() <= 0) {
                skipped++;
                continue;
            }
            String description = line.substring(0, amountMatcher.start()).trim();
            description = DATE_IN_LINE.matcher(description).replaceAll(" ")
                    .replaceAll("[^\\p{L}\\p{N} ]", " ").replaceAll("\\s+", " ").trim();
            if (description.length() < 2) {
                skipped++;
                continue;
            }
            if (description.length() > 120) {
                description = description.substring(0, 120).trim();
            }
            if (created.size() >= MAX_ITEMS) {
                skipped++;
                continue;
            }

            LocalDate due = YearMonth.of(year, month).atDay(Math.min(LocalDate.now().getDayOfMonth(),
                    YearMonth.of(year, month).lengthOfMonth()));

            PlannedExpenseResponse saved = planning.create(userId, new PlannedExpenseRequest(
                    description, amount, null, due,
                    cardAccountId == null ? PlannedExpenseKind.VARIABLE : PlannedExpenseKind.CREDIT_CARD,
                    null, "Importado de " + safeName(file), cardAccountId));

            created.add(new ImportedItem(saved.id(), saved.description(), saved.amount(), saved.dueDate()));
        }

        String preview = text.length() > 400 ? text.substring(0, 400) : text;
        return new ImportResult(safeName(file), text.split("\\R").length, created.size(),
                skipped, created, preview);
    }

    private String extractText(MultipartFile file) throws IOException {
        String name = safeName(file).toLowerCase(Locale.ROOT);
        byte[] bytes = file.getBytes();
        if (name.endsWith(".pdf")) {
            try (PDDocument doc = Loader.loadPDF(bytes)) {
                PDFTextStripper stripper = new PDFTextStripper();
                return stripper.getText(doc);
            }
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String safeName(MultipartFile file) {
        String n = file.getOriginalFilename();
        return n == null || n.isBlank() ? "fatura" : n;
    }

    private static BigDecimal toAmount(String raw) {
        try {
            String s = raw.contains(",") ? raw.replace(".", "").replace(",", ".") : raw;
            return new BigDecimal(s).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
