package rs.raf.banka2_bek.assistant.tool.handlers.agentic;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.assistant.tool.ToolDefinition;
import rs.raf.banka2_bek.assistant.tool.WriteToolHandler;
import rs.raf.banka2_bek.auth.util.UserContext;
import rs.raf.banka2_bek.investmentfund.dto.InvestmentFundDtos.ClientFundTransactionDto;
import rs.raf.banka2_bek.investmentfund.dto.InvestmentFundDtos.WithdrawFundDto;
import rs.raf.banka2_bek.investmentfund.service.InvestmentFundService;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Phase 4 v3.5 — povlacenje sredstava iz fonda. Null amount = sva pozicija.
 */
@Component
@RequiredArgsConstructor
public class WithdrawFromFundActionHandler implements WriteToolHandler {

    private final InvestmentFundService fundService;
    private final AgenticHandlerSupport support;

    @Override
    public String name() { return "withdraw_from_fund"; }

    @Override
    public boolean requiresOtp() { return true; }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(name())
                .description("Povlacenje novca iz investicionog fonda. Bez amount-a → " +
                        "povlaci celu poziciju. Klijent placa konverzionu proviziju ako je " +
                        "destinacija u drugoj valuti.")
                .param(new ToolDefinition.Param("fundId", "integer", "ID fonda", true, null, null))
                .param(new ToolDefinition.Param("amount", "number",
                        "Iznos u RSD; null/empty = sva pozicija", false, null, null))
                .param(new ToolDefinition.Param("destinationAccountId", "integer",
                        "ID racuna na koji ide novac", true, null, null))
                .build();
    }

    @Override
    public PreviewResult buildPreview(Map<String, Object> args, UserContext user) {
        Long fundId = support.getLong(args, "fundId");
        Long destAcc = support.getLong(args, "destinationAccountId");
        BigDecimal amount = support.getBigDecimal(args, "amount");
        if (fundId == null || destAcc == null) {
            throw new IllegalArgumentException("fundId + destinationAccountId su obavezni");
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("Fond ID", fundId);
        fields.put("Iznos", amount == null ? "Cela pozicija" : amount.toPlainString() + " RSD");
        fields.put("Na racun ID", destAcc);
        return new PreviewResult("Povlacenje iz fonda #" + fundId, fields);
    }

    @Override
    public Map<String, Object> executeFinal(Map<String, Object> args, UserContext user, String otpCode) {
        WithdrawFundDto dto = new WithdrawFundDto();
        dto.setAmount(support.getBigDecimal(args, "amount"));
        dto.setDestinationAccountId(support.getLong(args, "destinationAccountId"));
        ClientFundTransactionDto tx = fundService.withdraw(
                support.getLong(args, "fundId"), dto, user.userId(), user.userRole());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("transactionId", tx.getId());
        result.put("amount", tx.getAmountRsd());
        result.put("status", tx.getStatus());
        return result;
    }
}
