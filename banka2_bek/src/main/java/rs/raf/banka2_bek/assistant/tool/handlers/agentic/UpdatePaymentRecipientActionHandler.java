package rs.raf.banka2_bek.assistant.tool.handlers.agentic;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.assistant.tool.ToolDefinition;
import rs.raf.banka2_bek.assistant.tool.WriteToolHandler;
import rs.raf.banka2_bek.auth.util.UserContext;
import rs.raf.banka2_bek.payment.dto.PaymentRecipientResponseDto;
import rs.raf.banka2_bek.payment.dto.UpdatePaymentRecipientRequestDto;
import rs.raf.banka2_bek.payment.service.PaymentRecipientService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Phase 5 v3.5 — azuriranje postojeceg primaoca placanja (naziv ili broj racuna).
 */
@Component
@RequiredArgsConstructor
public class UpdatePaymentRecipientActionHandler implements WriteToolHandler {

    private final PaymentRecipientService recipientService;
    private final AgenticHandlerSupport support;

    @Override
    public String name() { return "update_payment_recipient"; }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(name())
                .description("Menja naziv ili broj racuna postojeceg sablona primaoca placanja.")
                .param(new ToolDefinition.Param("recipientId", "integer",
                        "ID primaoca u korisnikovoj listi sablona", true, null, null))
                .param(new ToolDefinition.Param("name", "string",
                        "Novi naziv (opciono)", false, null, null))
                .param(new ToolDefinition.Param("accountNumber", "string",
                        "Novi 18-cifren broj racuna (opciono)", false, null, null))
                .build();
    }

    @Override
    public PreviewResult buildPreview(Map<String, Object> args, UserContext user) {
        Long id = support.getLong(args, "recipientId");
        if (id == null) throw new IllegalArgumentException("recipientId je obavezan");
        String n = support.getString(args, "name");
        String acc = support.getString(args, "accountNumber");
        if ((n == null || n.isBlank()) && (acc == null || acc.isBlank())) {
            throw new IllegalArgumentException("Bar jedan od name/accountNumber mora biti postavljen");
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("Primalac ID", id);
        if (n != null && !n.isBlank()) fields.put("Novi naziv", n);
        if (acc != null && !acc.isBlank()) fields.put("Novi racun", support.maskAccount(acc));
        return new PreviewResult("Azuriraj primaoca #" + id, fields);
    }

    @Override
    public Map<String, Object> executeFinal(Map<String, Object> args, UserContext user, String otpCode) {
        UpdatePaymentRecipientRequestDto dto = new UpdatePaymentRecipientRequestDto();
        dto.setName(support.getString(args, "name"));
        dto.setAccountNumber(support.getString(args, "accountNumber"));
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        PaymentRecipientResponseDto resp = recipientService.updatePaymentRecipient(
                support.getLong(args, "recipientId"), dto, email);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("recipientId", resp.getId());
        result.put("name", resp.getName());
        return result;
    }
}
