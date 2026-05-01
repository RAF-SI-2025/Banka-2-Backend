package rs.raf.banka2_bek.assistant.tool.handlers.agentic;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.account.service.AccountService;
import rs.raf.banka2_bek.assistant.tool.ToolDefinition;
import rs.raf.banka2_bek.assistant.tool.WriteToolHandler;
import rs.raf.banka2_bek.auth.util.UserContext;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Phase 4 v3.5 — promena dnevnog/mesecnog limita racuna.
 */
@Component
@RequiredArgsConstructor
public class ChangeAccountLimitActionHandler implements WriteToolHandler {

    private final AccountService accountService;
    private final AgenticHandlerSupport support;

    @Override
    public String name() { return "change_account_limit"; }

    @Override
    public boolean requiresOtp() { return true; }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(name())
                .description("Menja dnevni i/ili mesecni limit racuna. Samo vlasnik racuna. Zahteva OTP.")
                .param(new ToolDefinition.Param("accountId", "integer",
                        "ID racuna", true, null, null))
                .param(new ToolDefinition.Param("dailyLimit", "number",
                        "Novi dnevni limit (opciono)", false, null, null))
                .param(new ToolDefinition.Param("monthlyLimit", "number",
                        "Novi mesecni limit (opciono)", false, null, null))
                .build();
    }

    @Override
    public PreviewResult buildPreview(Map<String, Object> args, UserContext user) {
        Long accountId = support.getLong(args, "accountId");
        BigDecimal daily = support.getBigDecimal(args, "dailyLimit");
        BigDecimal monthly = support.getBigDecimal(args, "monthlyLimit");
        if (accountId == null || (daily == null && monthly == null)) {
            throw new IllegalArgumentException("accountId + bar jedan od dailyLimit/monthlyLimit");
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("Racun ID", accountId);
        if (daily != null) fields.put("Novi dnevni limit", daily.toPlainString());
        if (monthly != null) fields.put("Novi mesecni limit", monthly.toPlainString());
        return new PreviewResult("Promena limita racuna #" + accountId, fields);
    }

    @Override
    public Map<String, Object> executeFinal(Map<String, Object> args, UserContext user, String otpCode) {
        accountService.updateAccountLimits(
                support.getLong(args, "accountId"),
                support.getBigDecimal(args, "dailyLimit"),
                support.getBigDecimal(args, "monthlyLimit"));
        return Map.of("status", "OK", "accountId", support.getLong(args, "accountId"));
    }
}
