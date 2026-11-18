package br.com.investia.transactions.application.importation;

import br.com.investia.transactions.api.dto.TransactionDtos.ImportResult;
import br.com.investia.transactions.api.dto.TransactionDtos.TransactionRequest;
import br.com.investia.transactions.application.TransactionService;
import br.com.investia.transactions.domain.TransactionType;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Importação de extratos OFX e CSV com detecção automática de tipo. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportService {

    private final OfxParser ofxParser = new OfxParser();
    private final CsvTransactionParser csvParser = new CsvTransactionParser();
    private final TransactionService transactionService;

    @Transactional
    public ImportResult importOfx(UUID userId, UUID accountId, MultipartFile file) {
        List<String> errors = new ArrayList<>();
        int imported = 0;
        int skipped = 0;
        try {
            String content = new String(file.getBytes(), StandardCharsets.ISO_8859_1);
            for (OfxParser.OfxTransaction trn : ofxParser.parse(content)) {
                try {
                    transactionService.create(userId, new TransactionRequest(
                            trn.amount().signum() >= 0 ? TransactionType.INCOME : TransactionType.EXPENSE,
                            accountId, null, null, null,
                            trn.amount().abs(),
                            trn.date(), trn.memo(), "Importado via OFX",
                            List.of("ofx"), null, null, null));
                    imported++;
                } catch (Exception e) {
                    skipped++;
                    errors.add(e.getMessage());
                }
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Arquivo OFX inválido: " + e.getMessage());
        }
        return new ImportResult(imported, skipped, errors);
    }

    @Transactional
    public ImportResult importCsv(UUID userId, UUID accountId, MultipartFile file) {
        List<String> errors = new ArrayList<>();
        int imported = 0;
        int skipped = 0;
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            for (CsvTransactionParser.CsvTransaction trn : csvParser.parse(content)) {
                try {
                    transactionService.create(userId, new TransactionRequest(
                            trn.amount().signum() >= 0 ? TransactionType.INCOME : TransactionType.EXPENSE,
                            accountId, null, null, null,
                            trn.amount().abs(),
                            trn.date(), trn.description(), "Importado via CSV",
                            List.of("csv"), null, null, null));
                    imported++;
                } catch (Exception e) {
                    skipped++;
                    errors.add(e.getMessage());
                }
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Arquivo CSV inválido: " + e.getMessage());
        }
        return new ImportResult(imported, skipped, errors);
    }
}
