package rs.raf.banka2_bek.assistant.tool.handlers.agentic;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.assistant.tool.ToolDefinition;
import rs.raf.banka2_bek.assistant.tool.WriteToolHandler;
import rs.raf.banka2_bek.auth.util.UserContext;
import rs.raf.banka2_bek.card.dto.CardResponseDto;
import rs.raf.banka2_bek.card.service.CardService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 4 v3.5 — zaposleni odblokira karticu.
 */
@Component
@RequiredArgsConstructor
public class UnblockCardActionHandler implements WriteToolHandler {

    private final CardService cardService;
    private final AgenticHandlerSupport support;

    @Override
    public String name() { return "unblock_card"; }

    @Override
    public List<String> allowedRoles() { return List.of("EMPLOYEE"); }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(name())
                .description("Zaposleni odblokira karticu (klijent moze samo blokirati svoju, ne odblokirati).")
                .param(new ToolDefinition.Param("cardId", "integer",
                        "ID kartice", true, null, null))
                .build();
    }

    @Override
    public PreviewResult buildPreview(Map<String, Object> args, UserContext user) {
        Long cardId = support.getLong(args, "cardId");
        if (cardId == null) throw new IllegalArgumentException("cardId je obavezan");
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("Kartica ID", cardId);
        fields.put("Akcija", "Odblokiranje");
        return new PreviewResult("Odblokiraj karticu #" + cardId, fields);
    }

    @Override
    public Map<String, Object> executeFinal(Map<String, Object> args, UserContext user, String otpCode) {
        CardResponseDto card = cardService.unblockCard(support.getLong(args, "cardId"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cardId", card.getId());
        result.put("status", card.getStatus());
        return result;
    }
}
