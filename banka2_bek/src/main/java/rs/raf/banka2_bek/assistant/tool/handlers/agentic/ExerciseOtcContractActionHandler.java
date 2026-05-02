package rs.raf.banka2_bek.assistant.tool.handlers.agentic;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.assistant.tool.ToolDefinition;
import rs.raf.banka2_bek.assistant.tool.WriteToolHandler;
import rs.raf.banka2_bek.auth.util.UserContext;
import rs.raf.banka2_bek.otc.dto.OtcContractDto;
import rs.raf.banka2_bek.otc.service.OtcService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 4 v3.5 — iskoriscenje opcionog ugovora (kupac uzima akcije po strike ceni).
 */
@Component
@RequiredArgsConstructor
public class ExerciseOtcContractActionHandler implements WriteToolHandler {

    private final OtcService otcService;
    private final AgenticHandlerSupport support;

    @Override
    public String name() { return "exercise_otc_contract"; }

    @Override
    public boolean requiresOtp() { return true; }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(name())
                .description("Kupac iskoriscava opcioni ugovor — placa strike cenu i preuzima akcije od prodavca.")
                .param(new ToolDefinition.Param("contractId", "integer",
                        "ID OTC ugovora", true, null, null))
                .param(new ToolDefinition.Param("buyerAccountId", "integer",
                        "ID racuna kupca", true, null, null))
                .build();
    }

    @Override
    public PreviewResult buildPreview(Map<String, Object> args, UserContext user) {
        Long contractId = support.getLong(args, "contractId");
        Long accId = support.getLong(args, "buyerAccountId");
        if (contractId == null || accId == null) {
            throw new IllegalArgumentException("contractId + buyerAccountId su obavezni");
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("Contract ID", contractId);
        fields.put("Racun ID", accId);
        return new PreviewResult("Iskoriscenje OTC ugovora #" + contractId, fields,
                List.of("Akcije ce preci u tvoj portfolio, strike cena se odmah skida sa racuna."));
    }

    @Override
    public Map<String, Object> executeFinal(Map<String, Object> args, UserContext user, String otpCode) {
        OtcContractDto contract = otcService.exerciseContract(
                support.getLong(args, "contractId"),
                support.getLong(args, "buyerAccountId"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contractId", contract.getId());
        result.put("status", contract.getStatus());
        return result;
    }
}
