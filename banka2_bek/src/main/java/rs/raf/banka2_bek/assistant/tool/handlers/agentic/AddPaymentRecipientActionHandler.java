package rs.raf.banka2_bek.assistant.tool.handlers.agentic;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.assistant.tool.ToolDefinition;
import rs.raf.banka2_bek.assistant.tool.WriteToolHandler;
import rs.raf.banka2_bek.auth.util.UserContext;
import rs.raf.banka2_bek.payment.dto.CreatePaymentRecipientRequestDto;
import rs.raf.banka2_bek.payment.dto.PaymentRecipientResponseDto;
import rs.raf.banka2_bek.payment.service.PaymentRecipientService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Phase 4 v3.5 — dodaje primaoca placanja u korisnikovu listu sablona.
 */
@Component
@RequiredArgsConstructor
public class AddPaymentRecipientActionHandler implements WriteToolHandler {

    private final PaymentRecipientService recipientService;
    private final AgenticHandlerSupport support;

    @Override
    public String name() { return "add_payment_recipient"; }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(name())
                .description("Dodaje novog primaoca u korisnikovu listu sablona za placanje. " +
                        "Koristi se za brze ponovne uplate.")
                .param(new ToolDefinition.Param("name", "string",
                        "Naziv primaoca (max 100 chars)", true, null, null))
                .param(new ToolDefinition.Param("accountNumber", "string",
                        "18-cifren broj racuna primaoca", true, null, null))
                .build();
    }

    @Override
    public PreviewResult buildPreview(Map<String, Object> args, UserContext user) {
        String n = support.getString(args, "name");
        String acc = support.getString(args, "accountNumber");
        if (n == null || n.isBlank() || acc == null || acc.length() != 18) {
            throw new IllegalArgumentException("name + 18-cifren accountNumber su obavezni");
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("Naziv", n);
        fields.put("Broj racuna", support.maskAccount(acc));
        return new PreviewResult("Novi primalac: " + n, fields);
    }

    @Override
    public Map<String, Object> executeFinal(Map<String, Object> args, UserContext user, String otpCode) {
        CreatePaymentRecipientRequestDto dto = new CreatePaymentRecipientRequestDto();
        dto.setName(support.getString(args, "name"));
        dto.setAccountNumber(support.getString(args, "accountNumber"));
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        PaymentRecipientResponseDto resp = recipientService.createPaymentRecipient(dto, email);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("recipientId", resp.getId());
        result.put("name", resp.getName());
        return result;
    }
}
