package rs.raf.banka2_bek.assistant.tool.handlers.agentic;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.assistant.tool.ToolDefinition;
import rs.raf.banka2_bek.assistant.tool.WriteToolHandler;
import rs.raf.banka2_bek.auth.util.UserContext;
import rs.raf.banka2_bek.otc.dto.CounterOtcOfferDto;
import rs.raf.banka2_bek.otc.dto.OtcOfferDto;
import rs.raf.banka2_bek.otc.service.OtcService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Phase 4 v3.5 — kontraponuda u OTC pregovaranju.
 */
@Component
@RequiredArgsConstructor
public class CounterOtcOfferActionHandler implements WriteToolHandler {

    private final OtcService otcService;
    private final AgenticHandlerSupport support;

    @Override
    public String name() { return "counter_otc_offer"; }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(name())
                .description("Salje kontraponudu na postojecu OTC pregovor. Druga strana " +
                        "ce videti izmene i moze prihvatiti, odbiti ili poslati svoju kontraponudu.")
                .param(new ToolDefinition.Param("offerId", "integer",
                        "ID OTC ponude", true, null, null))
                .param(new ToolDefinition.Param("quantity", "integer",
                        "Nova kolicina akcija", true, null, null))
                .param(new ToolDefinition.Param("pricePerStock", "number",
                        "Nova cena po akciji u valuti listinga", true, null, null))
                .param(new ToolDefinition.Param("premium", "number",
                        "Nova premija za opcioni ugovor", true, null, null))
                .param(new ToolDefinition.Param("settlementDate", "string",
                        "Datum isteka opcije (ISO format YYYY-MM-DD)", true, null, null))
                .build();
    }

    @Override
    public PreviewResult buildPreview(Map<String, Object> args, UserContext user) {
        Long offerId = support.getLong(args, "offerId");
        Integer quantity = support.getInt(args, "quantity");
        BigDecimal price = support.getBigDecimal(args, "pricePerStock");
        BigDecimal premium = support.getBigDecimal(args, "premium");
        String settlement = support.getString(args, "settlementDate");
        if (offerId == null || quantity == null || price == null || premium == null || settlement == null) {
            throw new IllegalArgumentException("Nedostajuci parametri za kontraponudu");
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("Offer ID", offerId);
        fields.put("Kolicina", quantity);
        fields.put("Cena po akciji", price.toPlainString());
        fields.put("Premija", premium.toPlainString());
        fields.put("Settlement", settlement);
        return new PreviewResult("Kontraponuda za OTC #" + offerId, fields);
    }

    @Override
    public Map<String, Object> executeFinal(Map<String, Object> args, UserContext user, String otpCode) {
        CounterOtcOfferDto dto = new CounterOtcOfferDto();
        dto.setQuantity(support.getInt(args, "quantity"));
        dto.setPricePerStock(support.getBigDecimal(args, "pricePerStock"));
        dto.setPremium(support.getBigDecimal(args, "premium"));
        dto.setSettlementDate(LocalDate.parse(support.getString(args, "settlementDate")));
        OtcOfferDto resp = otcService.counterOffer(support.getLong(args, "offerId"), dto);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("offerId", resp.getId());
        result.put("status", resp.getStatus());
        result.put("quantity", resp.getQuantity());
        result.put("pricePerStock", resp.getPricePerStock());
        return result;
    }
}
