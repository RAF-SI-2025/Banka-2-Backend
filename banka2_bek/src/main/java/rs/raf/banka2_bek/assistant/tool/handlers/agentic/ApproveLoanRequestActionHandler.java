package rs.raf.banka2_bek.assistant.tool.handlers.agentic;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.assistant.tool.ToolDefinition;
import rs.raf.banka2_bek.assistant.tool.WriteToolHandler;
import rs.raf.banka2_bek.auth.util.UserContext;
import rs.raf.banka2_bek.loan.dto.LoanResponseDto;
import rs.raf.banka2_bek.loan.service.LoanService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 4 v3.5 — zaposleni odobrava zahtev za kredit.
 */
@Component
@RequiredArgsConstructor
public class ApproveLoanRequestActionHandler implements WriteToolHandler {

    private final LoanService loanService;
    private final AgenticHandlerSupport support;

    @Override
    public String name() { return "approve_loan_request"; }

    @Override
    public List<String> allowedRoles() { return List.of("EMPLOYEE"); }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(name())
                .description("Zaposleni odobrava zahtev za kredit. Sredstva se " +
                        "automatski uplacuju na klijentov racun, kreira se Loan entity sa " +
                        "rasporedom rata.")
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
        fields.put("Akcija", "Odobravanje + uplata");
        return new PreviewResult("Odobri kredit zahtev #" + requestId, fields,
                List.of("Sredstva ce odmah biti uplacena na klijentov racun + kreiraju se rate."));
    }

    @Override
    public Map<String, Object> executeFinal(Map<String, Object> args, UserContext user, String otpCode) {
        LoanResponseDto resp = loanService.approveLoanRequest(support.getLong(args, "requestId"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("loanId", resp.getId());
        result.put("status", resp.getStatus());
        result.put("amount", resp.getAmount());
        return result;
    }
}
