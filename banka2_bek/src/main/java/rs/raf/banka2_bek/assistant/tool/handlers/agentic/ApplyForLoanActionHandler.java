package rs.raf.banka2_bek.assistant.tool.handlers.agentic;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.assistant.tool.ToolDefinition;
import rs.raf.banka2_bek.assistant.tool.WriteToolHandler;
import rs.raf.banka2_bek.auth.util.UserContext;
import rs.raf.banka2_bek.loan.dto.LoanRequestDto;
import rs.raf.banka2_bek.loan.dto.LoanRequestResponseDto;
import rs.raf.banka2_bek.loan.service.LoanService;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 4 v3.5 — klijent podnosi zahtev za kredit.
 */
@Component
@RequiredArgsConstructor
public class ApplyForLoanActionHandler implements WriteToolHandler {

    private final LoanService loanService;
    private final AgenticHandlerSupport support;

    @Override
    public String name() { return "apply_for_loan"; }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(name())
                .description("Klijent podnosi zahtev za kredit. Tipovi: GOTOVINSKI, " +
                        "STAMBENI, AUTO, REFINANSIRAJUCI, STUDENTSKI. Bez OTP-a (zahtev ne " +
                        "dira novac); zaposleni kasnije odobrava ili odbija.")
                .param(new ToolDefinition.Param("loanType", "string",
                        "Tip kredita (GOTOVINSKI/STAMBENI/AUTO/REFINANSIRAJUCI/STUDENTSKI)", true, null, null))
                .param(new ToolDefinition.Param("interestType", "string",
                        "Tip kamate (FIKSNA/VARIJABILNA)", true, null, null))
                .param(new ToolDefinition.Param("amount", "number",
                        "Iznos kredita", true, null, null))
                .param(new ToolDefinition.Param("currency", "string",
                        "Valuta (RSD, EUR, USD, ...)", true, null, null))
                .param(new ToolDefinition.Param("loanPurpose", "string",
                        "Svrha kredita", false, null, null))
                .param(new ToolDefinition.Param("repaymentPeriod", "integer",
                        "Period otplate u mesecima (gotovinski 12-84, stambeni 60-360)", true, null, null))
                .param(new ToolDefinition.Param("accountNumber", "string",
                        "Broj racuna na koji ce sredstva", true, null, null))
                .param(new ToolDefinition.Param("monthlyIncome", "number",
                        "Mesecna plata", false, null, null))
                .param(new ToolDefinition.Param("phoneNumber", "string",
                        "Kontakt telefon", false, null, null))
                .build();
    }

    @Override
    public PreviewResult buildPreview(Map<String, Object> args, UserContext user) {
        String type = support.getString(args, "loanType");
        BigDecimal amount = support.getBigDecimal(args, "amount");
        String currency = support.getString(args, "currency");
        Integer period = support.getInt(args, "repaymentPeriod");
        if (type == null || amount == null || currency == null || period == null) {
            throw new IllegalArgumentException("loanType, amount, currency, repaymentPeriod su obavezni");
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("Tip kredita", type);
        fields.put("Iznos", amount.toPlainString() + " " + currency);
        fields.put("Period otplate", period + " meseci");
        fields.put("Tip kamate", support.getString(args, "interestType"));
        String purpose = support.getString(args, "loanPurpose");
        if (purpose != null && !purpose.isBlank()) fields.put("Svrha", purpose);
        return new PreviewResult("Zahtev za " + type + " kredit (" + amount.toPlainString() + " " + currency + ")",
                fields, List.of("Zahtev ce biti prosledjen zaposlenom na pregled. Dobicete obavestenje kad bude obraden."));
    }

    @Override
    public Map<String, Object> executeFinal(Map<String, Object> args, UserContext user, String otpCode) {
        LoanRequestDto dto = new LoanRequestDto();
        dto.setLoanType(support.getString(args, "loanType"));
        dto.setInterestType(support.getString(args, "interestType"));
        dto.setAmount(support.getBigDecimal(args, "amount"));
        dto.setCurrency(support.getString(args, "currency"));
        dto.setLoanPurpose(support.getString(args, "loanPurpose"));
        dto.setRepaymentPeriod(support.getInt(args, "repaymentPeriod"));
        dto.setAccountNumber(support.getString(args, "accountNumber"));
        dto.setPhoneNumber(support.getString(args, "phoneNumber"));
        dto.setMonthlyIncome(support.getBigDecimal(args, "monthlyIncome"));
        dto.setEmploymentStatus(support.getString(args, "employmentStatus"));
        Boolean permanent = support.getBool(args, "permanentEmployment");
        dto.setPermanentEmployment(permanent);
        dto.setEmploymentPeriod(support.getInt(args, "employmentPeriod"));

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        LoanRequestResponseDto resp = loanService.createLoanRequest(dto, email);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestId", resp.getId());
        result.put("status", resp.getStatus());
        result.put("loanType", resp.getLoanType());
        return result;
    }
}
