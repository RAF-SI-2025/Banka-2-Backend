package rs.raf.banka2_bek.assistant.tool.handlers.agentic;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.assistant.tool.ToolDefinition;
import rs.raf.banka2_bek.assistant.tool.WriteToolHandler;
import rs.raf.banka2_bek.auth.util.UserContext;
import rs.raf.banka2_bek.card.dto.CardResponseDto;
import rs.raf.banka2_bek.card.service.CardService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Phase 4 v3.5 — blokira sopstvenu karticu (klijent) ili karticu klijenta (zaposleni).
 */
@Component
@RequiredArgsConstructor
public class BlockCardActionHandler implements WriteToolHandler {

    private final CardService cardService;
    private final AgenticHandlerSupport support;

    @Override
    public String name() { return "block_card"; }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(name())
                .description("Blokira karticu po ID-u. Klijent moze samo svoje. Posle " +
                        "blokade kartica nije u funkciji dok ne bude odblokirana od strane zaposlenog.")
                .param(new ToolDefinition.Param("cardId", "integer",
                        "ID kartice za blokiranje", true, null, null))
                .build();
    }

    @Override
    public PreviewResult buildPreview(Map<String, Object> args, UserContext user) {
        Long cardId = support.getLong(args, "cardId");
        if (cardId == null) throw new IllegalArgumentException("cardId je obavezan");
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("Kartica ID", cardId);
        fields.put("Akcija", "Blokiranje");
        return new PreviewResult("Blokiranje kartice #" + cardId, fields);
    }

    @Override
    public Map<String, Object> executeFinal(Map<String, Object> args, UserContext user, String otpCode) {
        CardResponseDto card = cardService.blockCard(support.getLong(args, "cardId"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cardId", card.getId());
        result.put("status", card.getStatus());
        return result;
    }
}
