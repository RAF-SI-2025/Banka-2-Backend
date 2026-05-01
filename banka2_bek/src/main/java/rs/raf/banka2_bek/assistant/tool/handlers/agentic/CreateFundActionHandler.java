package rs.raf.banka2_bek.assistant.tool.handlers.agentic;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.assistant.tool.ToolDefinition;
import rs.raf.banka2_bek.assistant.tool.WriteToolHandler;
import rs.raf.banka2_bek.auth.util.UserContext;
import rs.raf.banka2_bek.investmentfund.dto.InvestmentFundDtos.CreateFundDto;
import rs.raf.banka2_bek.investmentfund.dto.InvestmentFundDtos.InvestmentFundDetailDto;
import rs.raf.banka2_bek.investmentfund.service.InvestmentFundService;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 4 v3.5 — supervizor kreira investicioni fond.
 */
@Component
@RequiredArgsConstructor
public class CreateFundActionHandler implements WriteToolHandler {

    private final InvestmentFundService fundService;
    private final AgenticHandlerSupport support;

    @Override
    public String name() { return "create_fund"; }

    @Override
    public List<String> allowedRoles() { return List.of("EMPLOYEE"); }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(name())
                .description("Supervizor kreira novi investicioni fond. Automatski se " +
                        "otvara dinarski racun fonda, supervizor postaje menadzer.")
                .param(new ToolDefinition.Param("name", "string",
                        "Naziv fonda (3-128 karaktera, mora biti unique)", true, null, null))
                .param(new ToolDefinition.Param("description", "string",
                        "Kratak opis investicione strategije", false, null, null))
                .param(new ToolDefinition.Param("minimumContribution", "number",
                        "Minimalni ulog u RSD", true, null, null))
                .build();
    }

    @Override
    public PreviewResult buildPreview(Map<String, Object> args, UserContext user) {
        String fundName = support.getString(args, "name");
        BigDecimal minContrib = support.getBigDecimal(args, "minimumContribution");
        if (fundName == null || fundName.isBlank() || minContrib == null || minContrib.signum() <= 0) {
            throw new IllegalArgumentException("name + minimumContribution > 0 su obavezni");
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("Naziv fonda", fundName);
        fields.put("Minimalni ulog", minContrib.toPlainString() + " RSD");
        String desc = support.getString(args, "description");
        if (desc != null && !desc.isBlank()) fields.put("Opis", desc);
        return new PreviewResult("Kreiranje fonda: " + fundName, fields);
    }

    @Override
    public Map<String, Object> executeFinal(Map<String, Object> args, UserContext user, String otpCode) {
        CreateFundDto dto = new CreateFundDto();
        dto.setName(support.getString(args, "name"));
        dto.setDescription(support.getString(args, "description"));
        dto.setMinimumContribution(support.getBigDecimal(args, "minimumContribution"));
        InvestmentFundDetailDto fund = fundService.createFund(dto, user.userId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fundId", fund.getId());
        result.put("name", fund.getName());
        return result;
    }
}
