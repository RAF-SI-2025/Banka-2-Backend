package rs.raf.banka2_bek.assistant.tool.handlers.agentic;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.assistant.tool.ToolDefinition;
import rs.raf.banka2_bek.assistant.tool.WriteToolHandler;
import rs.raf.banka2_bek.auth.util.UserContext;
import rs.raf.banka2_bek.otc.dto.OtcOfferDto;
import rs.raf.banka2_bek.otc.service.OtcService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 4 v3.5 — prihvatanje OTC ponude. Kupac placa premiju → kreira ugovor.
 */
@Component
@RequiredArgsConstructor
public class AcceptOtcOfferActionHandler implements WriteToolHandler {

    private final OtcService otcService;
    private final AgenticHandlerSupport support;

    @Override
    public String name() { return "accept_otc_offer"; }

    @Override
    public boolean requiresOtp() { return true; }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(name())
                .description("Kupac prihvata OTC ponudu — automatski placa premiju i kreira opcioni ugovor.")
                .param(new ToolDefinition.Param("offerId", "integer",
                        "ID ponude koja se prihvata", true, null, null))
                .param(new ToolDefinition.Param("buyerAccountId", "integer",
                        "ID racuna kupca sa kog se placa premija", true, null, null))
                .build();
    }

    @Override
    public PreviewResult buildPreview(Map<String, Object> args, UserContext user) {
        Long offerId = support.getLong(args, "offerId");
        Long accId = support.getLong(args, "buyerAccountId");
        if (offerId == null || accId == null) {
            throw new IllegalArgumentException("offerId + buyerAccountId su obavezni");
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("Offer ID", offerId);
        fields.put("Racun kupca ID", accId);
        return new PreviewResult("Prihvatanje OTC ponude #" + offerId, fields,
                List.of("Premija ce biti odmah prebacena na racun prodavca."));
    }

    @Override
    public Map<String, Object> executeFinal(Map<String, Object> args, UserContext user, String otpCode) {
        OtcOfferDto resp = otcService.acceptOffer(
                support.getLong(args, "offerId"),
                support.getLong(args, "buyerAccountId"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("offerId", resp.getId());
        result.put("status", resp.getStatus());
        return result;
    }
}
