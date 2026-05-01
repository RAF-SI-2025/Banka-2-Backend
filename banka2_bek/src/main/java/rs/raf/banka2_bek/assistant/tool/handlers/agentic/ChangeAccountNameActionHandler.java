package rs.raf.banka2_bek.assistant.tool.handlers.agentic;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.account.service.AccountService;
import rs.raf.banka2_bek.assistant.tool.ToolDefinition;
import rs.raf.banka2_bek.assistant.tool.WriteToolHandler;
import rs.raf.banka2_bek.auth.util.UserContext;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Phase 4 v3.5 — menja prilagodjeni naziv racuna.
 */
@Component
@RequiredArgsConstructor
public class ChangeAccountNameActionHandler implements WriteToolHandler {

    private final AccountService accountService;
    private final AgenticHandlerSupport support;

    @Override
    public String name() { return "change_account_name"; }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(name())
                .description("Menja prilagodjeni naziv racuna (informativno polje koje korisnik vidi u listi).")
                .param(new ToolDefinition.Param("accountId", "integer",
                        "ID racuna", true, null, null))
                .param(new ToolDefinition.Param("newName", "string",
                        "Novi naziv (mora biti drugaciji od trenutnog i unique kod istog vlasnika)",
                        true, null, null))
                .build();
    }

    @Override
    public PreviewResult buildPreview(Map<String, Object> args, UserContext user) {
        Long id = support.getLong(args, "accountId");
        String newName = support.getString(args, "newName");
        if (id == null || newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("accountId + newName su obavezni");
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("Racun ID", id);
        fields.put("Novi naziv", newName);
        return new PreviewResult("Promena naziva racuna #" + id + " → " + newName, fields);
    }

    @Override
    public Map<String, Object> executeFinal(Map<String, Object> args, UserContext user, String otpCode) {
        accountService.updateAccountName(support.getLong(args, "accountId"), support.getString(args, "newName"));
        return Map.of("status", "OK", "newName", support.getString(args, "newName"));
    }
}
