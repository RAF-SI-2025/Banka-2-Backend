package rs.raf.banka2_bek.assistant.tool.handlers.agentic;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.assistant.tool.ToolDefinition;
import rs.raf.banka2_bek.assistant.tool.WriteToolHandler;
import rs.raf.banka2_bek.auth.util.UserContext;
import rs.raf.banka2_bek.payment.service.PaymentRecipientService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 5 v3.5 — brisanje primaoca placanja iz korisnikovog sablona.
 *
 * Jedini DELETE handler u v3.5 katalogu — opravdano jer je niska osetljivost
 * (sablon, ne stvarna sredstva). User moze uvek ponovo dodati primaoca.
 */
@Component
@RequiredArgsConstructor
public class DeletePaymentRecipientActionHandler implements WriteToolHandler {

    private final PaymentRecipientService recipientService;
    private final AgenticHandlerSupport support;

    @Override
    public String name() { return "delete_payment_recipient"; }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(name())
                .description("Brise primaoca iz korisnikovog sablona. Stvarni racun primaoca " +
                        "ostaje aktivan u banci — samo se uklanja iz tvoje liste sablona za brze placanje.")
                .param(new ToolDefinition.Param("recipientId", "integer",
                        "ID primaoca u sablonima", true, null, null))
                .build();
    }

    @Override
    public PreviewResult buildPreview(Map<String, Object> args, UserContext user) {
        Long id = support.getLong(args, "recipientId");
        if (id == null) throw new IllegalArgumentException("recipientId je obavezan");
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("Primalac ID", id);
        fields.put("Akcija", "Uklanjanje iz sablona");
        return new PreviewResult("Obrisi primaoca #" + id + " iz tvoje liste",
                fields, List.of("Stvarni racun primaoca ostaje aktivan; brises samo svoj sablon."));
    }

    @Override
    public Map<String, Object> executeFinal(Map<String, Object> args, UserContext user, String otpCode) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        recipientService.deletePaymentRecipient(support.getLong(args, "recipientId"), email);
        return Map.of("status", "OK", "recipientId", support.getLong(args, "recipientId"));
    }
}
