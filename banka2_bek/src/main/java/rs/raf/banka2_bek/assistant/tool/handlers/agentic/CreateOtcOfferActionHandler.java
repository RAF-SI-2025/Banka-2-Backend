package rs.raf.banka2_bek.assistant.tool.handlers.agentic;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.assistant.tool.ToolDefinition;
import rs.raf.banka2_bek.assistant.tool.WriteToolHandler;
import rs.raf.banka2_bek.auth.util.UserContext;
import rs.raf.banka2_bek.otc.dto.CreateOtcOfferDto;
import rs.raf.banka2_bek.otc.dto.OtcOfferDto;
import rs.raf.banka2_bek.otc.service.OtcService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Phase 4 v3.5 — kreiranje OTC ponude (kupac inicira pregovor sa prodavcem).
 */
@Component
@RequiredArgsConstructor
public class CreateOtcOfferActionHandler implements WriteToolHandler {

    private final OtcService otcService;
    private final AgenticHandlerSupport support;

    @Override
    public String name() { return "create_otc_offer"; }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(name())
                .description("Inicira OTC pregovor — kreira ponudu za kupovinu akcija od " +
                        "drugog korisnika (klijent ili supervizor). Druga strana moze " +
                        "prihvatiti, odbiti ili poslati kontraponudu.")
                .param(new ToolDefinition.Param("listingId", "integer",
                        "ID listing-a (akcija) za koju se trazi", true, null, null))
                .param(new ToolDefinition.Param("sellerId", "integer",
                        "ID prodavca (drugi user u istoj banci)", true, null, null))
                .param(new ToolDefinition.Param("quantity", "integer",
                        "Broj akcija", true, null, null))
                .param(new ToolDefinition.Param("pricePerStock", "number",
                        "Predlozena cena po akciji", true, null, null))
                .param(new ToolDefinition.Param("premium", "number",
                        "Predlozena premija za opcioni ugovor", true, null, null))
                .param(new ToolDefinition.Param("settlementDate", "string",
                        "Datum isteka opcije (ISO YYYY-MM-DD, mora biti u buducnosti)", true, null, null))
                .build();
    }

    @Override
    public PreviewResult buildPreview(Map<String, Object> args, UserContext user) {
        Long listingId = support.getLong(args, "listingId");
        Long sellerId = support.getLong(args, "sellerId");
        Integer qty = support.getInt(args, "quantity");
        BigDecimal price = support.getBigDecimal(args, "pricePerStock");
        BigDecimal premium = support.getBigDecimal(args, "premium");
        String settlement = support.getString(args, "settlementDate");
        if (listingId == null || sellerId == null || qty == null || price == null
                || premium == null || settlement == null) {
            throw new IllegalArgumentException("Sva polja su obavezna");
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("Listing ID", listingId);
        fields.put("Prodavac ID", sellerId);
        fields.put("Kolicina", qty);
        fields.put("Cena po akciji", price.toPlainString());
        fields.put("Premija", premium.toPlainString());
        fields.put("Settlement", settlement);
        return new PreviewResult("OTC ponuda za " + qty + " akcija", fields);
    }

    @Override
    public Map<String, Object> executeFinal(Map<String, Object> args, UserContext user, String otpCode) {
        CreateOtcOfferDto dto = new CreateOtcOfferDto();
        dto.setListingId(support.getLong(args, "listingId"));
        dto.setSellerId(support.getLong(args, "sellerId"));
        dto.setQuantity(support.getInt(args, "quantity"));
        dto.setPricePerStock(support.getBigDecimal(args, "pricePerStock"));
        dto.setPremium(support.getBigDecimal(args, "premium"));
        dto.setSettlementDate(LocalDate.parse(support.getString(args, "settlementDate")));
        OtcOfferDto resp = otcService.createOffer(dto);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("offerId", resp.getId());
        result.put("status", resp.getStatus());
        return result;
    }
}
