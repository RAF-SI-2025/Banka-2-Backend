package rs.raf.banka2_bek.assistant.tool.handlers.agentic;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.assistant.tool.ToolDefinition;
import rs.raf.banka2_bek.assistant.tool.WriteToolHandler;
import rs.raf.banka2_bek.auth.util.UserContext;
import rs.raf.banka2_bek.card.dto.CardResponseDto;
import rs.raf.banka2_bek.card.dto.CreateCardRequestDto;
import rs.raf.banka2_bek.card.model.CardType;
import rs.raf.banka2_bek.card.service.CardService;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 4 v3.5 — klijent zahteva novu karticu (max 2/lichni racun, 1/osoba poslovni).
 */
@Component
@RequiredArgsConstructor
public class RequestNewCardActionHandler implements WriteToolHandler {

    private final CardService cardService;
    private final AgenticHandlerSupport support;

    @Override
    public String name() { return "request_new_card"; }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(name())
                .description("Klijent zahteva novu karticu za odredjeni racun. BE proverava limit " +
                        "kartica po racunu (max 2/lichni, 1/osoba poslovni). Posle uspeha kartica " +
                        "automatski kreirana — bez dodatne mejl konfirmacije iz UI flow-a.")
                .param(new ToolDefinition.Param("accountId", "integer",
                        "ID racuna za koji se kreira kartica", true, null, null))
                .param(new ToolDefinition.Param("cardLimit", "number",
                        "Limit kartice (opciono)", false, null, null))
                .param(new ToolDefinition.Param("cardType", "string",
                        "Tip kartice (VISA, MASTERCARD, DINACARD, AMEX)", false, "VISA", null))
                .build();
    }

    @Override
    public PreviewResult buildPreview(Map<String, Object> args, UserContext user) {
        Long accountId = support.getLong(args, "accountId");
        if (accountId == null) throw new IllegalArgumentException("accountId je obavezan");
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("Racun ID", accountId);
        BigDecimal limit = support.getBigDecimal(args, "cardLimit");
        if (limit != null) fields.put("Limit kartice", limit.toPlainString());
        String type = support.getString(args, "cardType");
        if (type != null) fields.put("Tip", type);
        return new PreviewResult("Nova kartica za racun #" + accountId, fields,
                List.of("Kartica ce biti dostupna odmah u listi tvojih kartica."));
    }

    @Override
    public Map<String, Object> executeFinal(Map<String, Object> args, UserContext user, String otpCode) {
        CreateCardRequestDto dto = new CreateCardRequestDto();
        dto.setAccountId(support.getLong(args, "accountId"));
        dto.setCardLimit(support.getBigDecimal(args, "cardLimit"));
        String type = support.getString(args, "cardType");
        if (type != null) {
            try { dto.setCardType(CardType.valueOf(type.toUpperCase())); }
            catch (IllegalArgumentException e) { /* ignore — service will pick default */ }
        }
        CardResponseDto resp = cardService.createCard(dto);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cardId", resp.getId());
        result.put("status", resp.getStatus());
        result.put("cardNumberMasked", resp.getCardNumber() == null ? "" :
                resp.getCardNumber().substring(0, 4) + "****" +
                resp.getCardNumber().substring(resp.getCardNumber().length() - 4));
        return result;
    }
}
