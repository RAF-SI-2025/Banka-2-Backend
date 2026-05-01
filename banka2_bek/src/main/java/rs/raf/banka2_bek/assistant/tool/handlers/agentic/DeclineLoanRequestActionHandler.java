package rs.raf.banka2_bek.assistant.tool.handlers.agentic;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.assistant.tool.ToolDefinition;
import rs.raf.banka2_bek.assistant.tool.WriteToolHandler;
import rs.raf.banka2_bek.auth.util.UserContext;
import rs.raf.banka2_bek.loan.dto.LoanRequestResponseDto;
import rs.raf.banka2_bek.loan.service.LoanService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 4 v3.5 — zaposleni odbija zahtev za kredit.
 */
@Component
@RequiredArgsConstructor
public class DeclineLoanRequestActionHandler implements WriteToolHandler {

    private final LoanService loanService;
    private final AgenticHandlerSupport support;

    @Override
    public String name() { return "decline_loan_request"; }

    @Override
    public List<String> allowedRoles() { return List.of("EMPLOYEE"); }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(name())
                .description("Zaposleni odbija zahtev za kredit. Status se postavlja na REJECTED, klijent dobija mejl.")
                .param(new ToolDefinition.Param("requestId", "integer",
                        "ID zahteva za kredit", true, null, null))
                .build();
    }

    @Override
    public PreviewResult buildPreview(Map<String, Object> args, UserContext user) {
        Long requestId = support.getLong(args, "requestId");
        if (requestId == null) throw new IllegalArgumentException("requestId je obavezan");
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("Zahtev ID", requestId);
        fields.put("Akcija", "Odbijanje");
        return new PreviewResult("Odbij kredit zahtev #" + requestId, fields);
    }

    @Override
    public Map<String, Object> executeFinal(Map<String, Object> args, UserContext user, String otpCode) {
        LoanRequestResponseDto resp = loanService.rejectLoanRequest(support.getLong(args, "requestId"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestId", resp.getId());
        result.put("status", resp.getStatus());
        return result;
    }
}
