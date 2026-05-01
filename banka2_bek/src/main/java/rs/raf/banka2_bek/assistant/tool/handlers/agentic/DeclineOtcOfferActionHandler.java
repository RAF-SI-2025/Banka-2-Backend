package rs.raf.banka2_bek.assistant.tool.handlers.agentic;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.assistant.tool.ToolDefinition;
import rs.raf.banka2_bek.assistant.tool.WriteToolHandler;
import rs.raf.banka2_bek.auth.util.UserContext;
import rs.raf.banka2_bek.otc.service.OtcService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Phase 4 v3.5 — odustaje od OTC pregovora.
 */
@Component
@RequiredArgsConstructor
public class DeclineOtcOfferActionHandler implements WriteToolHandler {

    private final OtcService otcService;
    private final AgenticHandlerSupport support;

    @Override
    public String name() { return "decline_otc_offer"; }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(name())
                .description("Odustaje od OTC pregovora. Ponuda se brise iz aktivne liste oba ucesnika.")
                .param(new ToolDefinition.Param("offerId", "integer",
                        "ID ponude od koje se odustaje", true, null, null))
                .build();
    }

    @Override
    public PreviewResult buildPreview(Map<String, Object> args, UserContext user) {
        Long offerId = support.getLong(args, "offerId");
        if (offerId == null) throw new IllegalArgumentException("offerId je obavezan");
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("Offer ID", offerId);
        fields.put("Akcija", "Odustajanje");
        return new PreviewResult("Odustajanje od OTC ponude #" + offerId, fields);
    }

    @Override
    public Map<String, Object> executeFinal(Map<String, Object> args, UserContext user, String otpCode) {
        otcService.declineOffer(support.getLong(args, "offerId"));
        return Map.of("status", "DECLINED", "offerId", support.getLong(args, "offerId"));
    }
}
