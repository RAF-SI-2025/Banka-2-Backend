package rs.raf.banka2_bek.assistant.tool.handlers.agentic;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.assistant.tool.ToolDefinition;
import rs.raf.banka2_bek.assistant.tool.WriteToolHandler;
import rs.raf.banka2_bek.auth.util.UserContext;
import rs.raf.banka2_bek.order.dto.CreateOrderDto;
import rs.raf.banka2_bek.order.dto.OrderDto;
import rs.raf.banka2_bek.order.service.OrderService;
import rs.raf.banka2_bek.stock.model.Listing;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 4 v3.5 — kreira SELL order. Identican signature kao BUY.
 */
@Component
@RequiredArgsConstructor
public class CreateSellOrderActionHandler implements WriteToolHandler {

    private final OrderService orderService;
    private final AgenticHandlerSupport support;

    @Override
    public String name() { return "create_sell_order"; }

    @Override
    public boolean requiresOtp() { return true; }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(name())
                .description("Kreira SELL order — prodaje hartiju iz portfolija. " +
                        "Tip ordera se izvodi iz prisustva limitValue/stopValue.")
                .param(new ToolDefinition.Param("listingId", "integer",
                        "ID listing-a za prodaju", true, null, null))
                .param(new ToolDefinition.Param("ticker", "string",
                        "Ticker simbol kao alternativa", false, null, null))
                .param(new ToolDefinition.Param("quantity", "integer",
                        "Broj hartija za prodaju (>= 1)", true, null, null))
                .param(new ToolDefinition.Param("accountId", "integer",
                        "ID racuna na koji ce sredstva", true, null, null))
                .param(new ToolDefinition.Param("limitValue", "number",
                        "Minimalna cena (LIMIT)", false, null, null))
                .param(new ToolDefinition.Param("stopValue", "number",
                        "Aktivaciona cena (STOP)", false, null, null))
                .param(new ToolDefinition.Param("allOrNone", "boolean",
                        "AON flag", false, false, null))
                .param(new ToolDefinition.Param("margin", "boolean",
                        "Margin flag", false, false, null))
                .build();
    }

    @Override
    public PreviewResult buildPreview(Map<String, Object> args, UserContext user) {
        Long listingId = support.getLong(args, "listingId");
        Listing listing;
        if (listingId != null) {
            listing = support.findListing(listingId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Listing #" + listingId + " ne postoji"));
        } else {
            String ticker = support.getString(args, "ticker");
            if (ticker == null) {
                throw new IllegalArgumentException("Mora se proslediti listingId ili ticker");
            }
            listing = support.findListingByTicker(ticker)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Hartija sa ticker-om '" + ticker + "' ne postoji"));
        }

        Integer quantity = support.getInt(args, "quantity");
        Long accountId = support.getLong(args, "accountId");
        if (quantity == null || quantity < 1 || accountId == null) {
            throw new IllegalArgumentException("Nedostaju parametri: quantity (>= 1), accountId");
        }
        BigDecimal limitValue = support.getBigDecimal(args, "limitValue");
        BigDecimal stopValue = support.getBigDecimal(args, "stopValue");

        String orderType = CreateBuyOrderActionHandler.orderTypeFor(limitValue, stopValue);

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("Smer", "PRODAJA (SELL)");
        fields.put("Hartija", listing.getTicker() + " (" + listing.getName() + ")");
        fields.put("Kolicina", quantity);
        fields.put("Tip naloga", orderType);
        if (limitValue != null) fields.put("Limit cena", limitValue.toPlainString());
        if (stopValue != null) fields.put("Stop cena", stopValue.toPlainString());
        Boolean aon = support.getBool(args, "allOrNone");
        if (Boolean.TRUE.equals(aon)) fields.put("AON", "Da");
        Boolean margin = support.getBool(args, "margin");
        if (Boolean.TRUE.equals(margin)) fields.put("Margin", "Da");

        BigDecimal approxPerUnit = limitValue != null ? limitValue
                : (stopValue != null ? stopValue : listing.getPrice());
        if (approxPerUnit != null) {
            BigDecimal approxTotal = approxPerUnit.multiply(BigDecimal.valueOf(quantity));
            fields.put("Priblizan prihod", approxTotal.toPlainString());
        }

        List<String> warnings = new java.util.ArrayList<>();
        warnings.add("Prodaja smanjuje portfolio, profit/gubitak se obracunava i porez na " +
                "kapitalnu dobit (15%) ide na drzavni racun mesecno.");

        String summary = "SELL " + quantity + "× " + listing.getTicker() + " (" + orderType + ")";
        return new PreviewResult(summary, fields, warnings);
    }

    @Override
    public Map<String, Object> executeFinal(Map<String, Object> args, UserContext user, String otpCode) {
        CreateOrderDto dto = new CreateOrderDto();
        Long listingId = support.getLong(args, "listingId");
        if (listingId == null) {
            String ticker = support.getString(args, "ticker");
            listingId = support.findListingByTicker(ticker)
                    .orElseThrow(() -> new IllegalArgumentException("Hartija ne postoji"))
                    .getId();
        }
        dto.setListingId(listingId);
        dto.setQuantity(support.getInt(args, "quantity"));
        dto.setAccountId(support.getLong(args, "accountId"));
        Integer cs = support.getInt(args, "contractSize");
        dto.setContractSize(cs == null ? 1 : cs);
        dto.setDirection("SELL");
        BigDecimal limitValue = support.getBigDecimal(args, "limitValue");
        BigDecimal stopValue = support.getBigDecimal(args, "stopValue");
        dto.setLimitValue(limitValue);
        dto.setStopValue(stopValue);
        dto.setOrderType(CreateBuyOrderActionHandler.orderTypeFor(limitValue, stopValue));
        dto.setAllOrNone(Boolean.TRUE.equals(support.getBool(args, "allOrNone")));
        dto.setMargin(Boolean.TRUE.equals(support.getBool(args, "margin")));
        dto.setOtpCode(otpCode);

        OrderDto created = orderService.createOrder(dto);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", created.getId());
        result.put("status", created.getStatus());
        result.put("orderType", created.getOrderType());
        result.put("quantity", created.getQuantity());
        result.put("listingId", created.getListingId());
        return result;
    }
}
