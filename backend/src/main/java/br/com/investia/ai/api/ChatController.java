package br.com.investia.ai.api;

import br.com.investia.ai.application.ChatService;
import br.com.investia.ai.application.InvoiceImportService;
import br.com.investia.identity.infrastructure.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Chat do copiloto", description = "Chat que executa ações e importa faturas")
@RestController
@RequestMapping("/api/v1/ai/chat")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class ChatController {

    private final ChatService chat;
    private final InvoiceImportService invoices;

    public record SendRequest(@NotBlank String message) {
    }

    @Operation(summary = "Envia uma mensagem e executa a ação pedida")
    @PostMapping
    public ChatService.ChatReply send(@RequestBody SendRequest req) {
        return chat.send(SecurityUtils.currentUserId(), req.message());
    }

    @Operation(summary = "Histórico da conversa")
    @GetMapping("/history")
    public List<ChatService.ChatEntry> history() {
        return chat.history(SecurityUtils.currentUserId());
    }

    @Operation(summary = "Limpa o histórico")
    @DeleteMapping("/history")
    public Map<String, Object> clear() {
        chat.clear(SecurityUtils.currentUserId());
        return Map.of("status", "ok");
    }

    @Operation(summary = "Importa uma fatura (PDF ou texto) como gastos planejados")
    @PostMapping(value = "/invoice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public InvoiceImportService.ImportResult invoice(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String cardAccountId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) throws IOException {
        LocalDate now = LocalDate.now();
        UUID card = cardAccountId == null || cardAccountId.isBlank()
                ? null : UUID.fromString(cardAccountId);
        return invoices.importInvoice(SecurityUtils.currentUserId(), file, card,
                year == null ? now.getYear() : year,
                month == null ? now.getMonthValue() : month);
    }
}
