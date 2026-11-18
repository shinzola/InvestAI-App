package br.com.investia.transactions.api;

import br.com.investia.identity.infrastructure.security.SecurityUtils;
import br.com.investia.shared.api.PageResult;
import br.com.investia.transactions.api.dto.TransactionDtos.*;
import br.com.investia.transactions.application.CategoryService;
import br.com.investia.transactions.application.RecurringService;
import br.com.investia.transactions.application.TransactionService;
import br.com.investia.transactions.application.importation.ImportService;
import br.com.investia.transactions.domain.Category;
import br.com.investia.transactions.domain.CategoryKind;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Tag(name = "Transações", description = "Lançamentos, recorrências e importação de extratos")
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class TransactionController {

    private final TransactionService service;
    private final CategoryService categories;
    private final RecurringService recurring;
    private final ImportService imports;

    @Operation(summary = "Lista transações paginadas")
    @GetMapping
    public TransactionPage list(@RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "20") int size) {
        PageResult<TransactionResponse> result = service.list(SecurityUtils.currentUserId(), page, size);
        return new TransactionPage(result.content(), result.page(), result.size(), result.totalElements());
    }

    @Operation(summary = "Transações por período")
    @GetMapping("/period")
    public List<TransactionResponse> byPeriod(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return service.listByPeriod(SecurityUtils.currentUserId(), start, end);
    }

    @Operation(summary = "Cria transação (com suporte a parcelamento)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse create(@Valid @RequestBody TransactionRequest req) {
        return service.create(SecurityUtils.currentUserId(), req);
    }

    @Operation(summary = "Atualiza transação")
    @PutMapping("/{id}")
    public TransactionResponse update(@PathVariable UUID id, @Valid @RequestBody TransactionRequest req) {
        return service.update(SecurityUtils.currentUserId(), id, req);
    }

    @Operation(summary = "Remove transação")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(SecurityUtils.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Anexa comprovante (URL de storage)")
    @PostMapping("/{id}/attachments")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<Void> addAttachment(@PathVariable UUID id,
                                              @RequestParam String fileName,
                                              @RequestParam String url) {
        service.addAttachment(SecurityUtils.currentUserId(), id, fileName, contentTypeOf(url), url);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(summary = "Importa extrato OFX ou CSV")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult importStatement(@RequestParam UUID accountId,
                                        @RequestParam MultipartFile file) {
        var userId = SecurityUtils.currentUserId();
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (name.endsWith(".ofx") || name.endsWith(".qfx")) {
            return imports.importOfx(userId, accountId, file);
        }
        return imports.importCsv(userId, accountId, file);
    }

    // ---- Recorrências ----

    @Operation(summary = "Lista regras de recorrência")
    @GetMapping("/recurring")
    public List<RecurringResponse> listRecurring() {
        return recurring.list(SecurityUtils.currentUserId());
    }

    @Operation(summary = "Cria regra de recorrência")
    @PostMapping("/recurring")
    @ResponseStatus(HttpStatus.CREATED)
    public RecurringResponse createRecurring(@Valid @RequestBody RecurringRequest req) {
        return recurring.create(SecurityUtils.currentUserId(), req);
    }

    @Operation(summary = "Remove regra de recorrência")
    @DeleteMapping("/recurring/{id}")
    public ResponseEntity<Void> deleteRecurring(@PathVariable UUID id) {
        recurring.delete(SecurityUtils.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    // ---- Categorias (atalhos para UX do módulo) ----

    @Operation(summary = "Categorias em formato plano")
    @GetMapping("/categories")
    public List<CategoryResponse> categories() {
        return categories.list(SecurityUtils.currentUserId()).stream()
                .map(CategoryController::toResponse).toList();
    }

    private String contentTypeOf(String url) {
        return url.toLowerCase().endsWith(".pdf") ? "application/pdf" : "image/*";
    }
}
