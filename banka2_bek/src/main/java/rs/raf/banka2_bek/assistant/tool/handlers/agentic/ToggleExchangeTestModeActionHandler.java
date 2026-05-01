package rs.raf.banka2_bek.assistant.tool.handlers.agentic;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.assistant.tool.ToolDefinition;
import rs.raf.banka2_bek.assistant.tool.WriteToolHandler;
import rs.raf.banka2_bek.auth.util.UserContext;
import rs.raf.banka2_bek.berza.service.ExchangeManagementService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 4 v3.5 — admin/supervizor toggle-uje test mode na berzi.
 */
@Component
@RequiredArgsConstructor
public class ToggleExchangeTestModeActionHandler implements WriteToolHandler {

    private final ExchangeManagementService exchangeService;
    private final AgenticHandlerSupport support;

    @Override
    public String name() { return "toggle_exchange_test_mode"; }

    @Override
    public List<String> allowedRoles() { return List.of("EMPLOYEE"); }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(name())
                .description("Toggle-uje test mode na berzi (NYSE/NASDAQ/CME/LSE/XETRA/BELEX). " +
                        "Test mode = berza se ponasa kao otvorena + simulacija cena umesto Alpha Vantage poziva. " +
                        "Samo admin/supervizor.")
                .param(new ToolDefinition.Param("acronym", "string",
                        "Akronim berze (npr. NYSE, NASDAQ, BELEX)", true, null, null))
                .param(new ToolDefinition.Param("enabled", "boolean",
                        "true = ukljuci test mode, false = iskljuci", true, null, null))
                .build();
    }

    @Override
    public PreviewResult buildPreview(Map<String, Object> args, UserContext user) {
        String acronym = support.getString(args, "acronym");
        Boolean enabled = support.getBool(args, "enabled");
        if (acronym == null || enabled == null) {
            throw new IllegalArgumentException("acronym + enabled (boolean) su obavezni");
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("Berza", acronym);
        fields.put("Test mode", enabled ? "UKLJUCEN" : "ISKLJUCEN");
        return new PreviewResult(
                "Test mode na " + acronym + " → " + (enabled ? "ON" : "OFF"), fields);
    }

    @Override
    public Map<String, Object> executeFinal(Map<String, Object> args, UserContext user, String otpCode) {
        exchangeService.setTestMode(support.getString(args, "acronym"),
                Boolean.TRUE.equals(support.getBool(args, "enabled")));
        return Map.of("status", "OK", "acronym", support.getString(args, "acronym"),
                "testMode", support.getBool(args, "enabled"));
    }
}
