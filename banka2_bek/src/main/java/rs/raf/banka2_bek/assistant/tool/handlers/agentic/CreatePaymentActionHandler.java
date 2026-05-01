package rs.raf.banka2_bek.assistant.tool.handlers.agentic;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.assistant.tool.ToolDefinition;
import rs.raf.banka2_bek.assistant.tool.WriteToolHandler;
import rs.raf.banka2_bek.auth.util.UserContext;
import rs.raf.banka2_bek.payment.dto.CreatePaymentRequestDto;
import rs.raf.banka2_bek.payment.dto.PaymentResponseDto;
import rs.raf.banka2_bek.payment.model.PaymentCode;
import rs.raf.banka2_bek.payment.service.PaymentService;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 4 v3.5 — agentic write handler za POST /payments.
 *
 * LLM args:
 *   - fromAccount (string, 18 cifara)
 *   - toAccount   (string, 18 cifara)
 *   - amount      (number)
 *   - description (string)
 *   - paymentCode (string enum, default "TRANSFER")
 *   - referenceNumber (string, opciono)
 *   - recipientName (string, opciono)
 *
 * Phase 4 flow:
 *   1. buildPreview() — resolve primalca po broju, vrati summary "Placanje X RSD ime"
 *   2. executeFinal() — POST /payments preko PaymentService
 */
@Component
@RequiredArgsConstructor
public class CreatePaymentActionHandler implements WriteToolHandler {

    private final PaymentService paymentService;
    private final AgenticHandlerSupport support;

    @Override
    public String name() { return "create_payment"; }

    @Override
    public boolean requiresOtp() { return true; }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(name())
                .description("Inicira placanje novca sa korisnikovog racuna ka racunu " +
                        "primaoca. Zahteva potvrdu korisnika kroz preview modal + OTP. " +
                        "Pozovi samo kad agentic mode je aktivan i korisnik EKSPLICITNO " +
                        "trazi placanje.")
                .param(new ToolDefinition.Param("fromAccount", "string",
                        "Broj racuna platioca (18 cifara, prefix 222 za nasu banku)", true, null, null))
                .param(new ToolDefinition.Param("toAccount", "string",
                        "Broj racuna primaoca (18 cifara)", true, null, null))
                .param(new ToolDefinition.Param("amount", "number",
                        "Iznos u valuti racuna platioca", true, null, null))
                .param(new ToolDefinition.Param("description", "string",
                        "Svrha placanja", true, null, null))
                .param(new ToolDefinition.Param("paymentCode", "string",
                        "Sifra placanja (default TRANSFER)", false, "TRANSFER", null))
                .param(new ToolDefinition.Param("referenceNumber", "string",
                        "Poziv na broj (opciono)", false, null, null))
                .param(new ToolDefinition.Param("recipientName", "string",
                        "Naziv primaoca (informativno, BE resolve-uje stvarno ime)",
                        false, null, null))
                .build();
    }

    @Override
    public PreviewResult buildPreview(Map<String, Object> args, UserContext user) {
        String fromAcc = support.getString(args, "fromAccount");
        String toAcc = support.getString(args, "toAccount");
        BigDecimal amount = support.getBigDecimal(args, "amount");
        String desc = support.getString(args, "description");

        if (fromAcc == null || toAcc == null || amount == null
                || amount.signum() <= 0 || desc == null) {
            throw new IllegalArgumentException(
                    "Nedostajuci ili neispravni parametri: fromAccount, toAccount, amount > 0, description.");
        }

        // Resolve primalca iz baze ako moze (informativno za UI)
        String resolvedRecipient = support.resolveOwnerName(toAcc);
        if (resolvedRecipient == null) {
            String declared = support.getString(args, "recipientName");
            resolvedRecipient = declared != null ? declared : "(nepoznat primalac)";
        }

        String currency = support.findAccountByNumber(fromAcc)
                .map(a -> a.getCurrency() == null ? "RSD" : a.getCurrency().getCode())
                .orElse("RSD");

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("Sa racuna", support.maskAccount(fromAcc));
        fields.put("Na racun", support.maskAccount(toAcc));
        fields.put("Primalac", resolvedRecipient);
        fields.put("Iznos", amount.toPlainString() + " " + currency);
        fields.put("Svrha", desc);
        String paymentCode = support.getString(args, "paymentCode");
        if (paymentCode != null) fields.put("Sifra", paymentCode);
        String ref = support.getString(args, "referenceNumber");
        if (ref != null && !ref.isBlank()) fields.put("Poziv na broj", ref);

        List<String> warnings = new java.util.ArrayList<>();
        // Detekcija inter-bank: prefix 222 = nasa banka. Svi ostali = drugi.
        if (toAcc.length() >= 3 && !toAcc.startsWith("222")) {
            warnings.add("Inter-bank placanje (drugi prefix racuna). Status moze biti " +
                    "INITIATED→COMMITTING→COMMITTED, traje par sekundi do nekoliko minuta.");
        }

        String summary = "Placanje " + amount.toPlainString() + " " + currency
                + " ka " + resolvedRecipient;
        return new PreviewResult(summary, fields, warnings);
    }

    @Override
    public Map<String, Object> executeFinal(Map<String, Object> args, UserContext user, String otpCode) {
        CreatePaymentRequestDto dto = new CreatePaymentRequestDto();
        dto.setFromAccount(support.getString(args, "fromAccount"));
        dto.setToAccount(support.getString(args, "toAccount"));
        dto.setAmount(support.getBigDecimal(args, "amount"));
        dto.setDescription(support.getString(args, "description"));
        dto.setRecipientName(support.getString(args, "recipientName"));
        dto.setReferenceNumber(support.getString(args, "referenceNumber"));
        String code = support.getString(args, "paymentCode");
        try {
            dto.setPaymentCode(code == null ? PaymentCode.CODE_289 : PaymentCode.valueOf(code));
        } catch (IllegalArgumentException e) {
            dto.setPaymentCode(PaymentCode.CODE_289);
        }
        dto.setOtpCode(otpCode);

        PaymentResponseDto response = paymentService.createPayment(dto);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("paymentId", response.getId());
        result.put("status", response.getStatus() == null ? "" : response.getStatus().name());
        result.put("amount", response.getAmount());
        result.put("currency", response.getCurrency());
        result.put("toAccount", support.maskAccount(response.getToAccount()));
        result.put("createdAt", response.getCreatedAt());
        return result;
    }
}
