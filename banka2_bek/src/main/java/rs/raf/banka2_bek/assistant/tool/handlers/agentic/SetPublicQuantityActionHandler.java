package rs.raf.banka2_bek.assistant.tool.handlers.agentic;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.assistant.tool.ToolDefinition;
import rs.raf.banka2_bek.assistant.tool.WriteToolHandler;
import rs.raf.banka2_bek.auth.util.UserContext;
import rs.raf.banka2_bek.portfolio.service.PortfolioService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 4 v3.5 — postavlja "javni rezim" akcija za OTC discovery.
 */
@Component
@RequiredArgsConstructor
public class SetPublicQuantityActionHandler implements WriteToolHandler {

    private final PortfolioService portfolioService;
    private final AgenticHandlerSupport support;

    @Override
    public String name() { return "set_public_quantity"; }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(name())
                .description("Postavlja koliko akcija iz portfolio pozicije ce biti " +
                        "vidljivo u OTC discovery-ju (klijent-klijent trgovina). " +
                        "Bez OTP-a — niska osetljivost.")
                .param(new ToolDefinition.Param("portfolioId", "integer",
                        "ID portfolio pozicije (NE listingId)", true, null, null))
                .param(new ToolDefinition.Param("quantity", "integer",
                        "Broj javnih akcija (0..ukupno u poziciji)", true, null, null))
                .build();
    }

    @Override
    public PreviewResult buildPreview(Map<String, Object> args, UserContext user) {
        Long portfolioId = support.getLong(args, "portfolioId");
        Integer quantity = support.getInt(args, "quantity");
        if (portfolioId == null || quantity == null || quantity < 0) {
            throw new IllegalArgumentException("portfolioId + quantity (>= 0) su obavezni");
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("Portfolio ID", portfolioId);
        fields.put("Javni broj akcija", quantity);
        return new PreviewResult("Postavi javni rezim (" + quantity + ")",
                fields, List.of("Akcije u javnom rezimu vidljive su drugim korisnicima u OTC discovery-ju."));
    }

    @Override
    public Map<String, Object> executeFinal(Map<String, Object> args, UserContext user, String otpCode) {
        portfolioService.setPublicQuantity(
                support.getLong(args, "portfolioId"), support.getInt(args, "quantity"));
        return Map.of("status", "OK", "publicQuantity", support.getInt(args, "quantity"));
    }
}
