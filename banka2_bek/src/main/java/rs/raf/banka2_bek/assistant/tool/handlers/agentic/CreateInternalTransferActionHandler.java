package rs.raf.banka2_bek.assistant.tool.handlers.agentic;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.assistant.tool.ToolDefinition;
import rs.raf.banka2_bek.assistant.tool.WriteToolHandler;
import rs.raf.banka2_bek.auth.util.UserContext;
import rs.raf.banka2_bek.transfers.dto.TransferInternalRequestDto;
import rs.raf.banka2_bek.transfers.dto.TransferResponseDto;
import rs.raf.banka2_bek.transfers.service.TransferService;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Phase 4 v3.5 — interni transfer izmedju svojih racuna iste valute.
 */
@Component
@RequiredArgsConstructor
public class CreateInternalTransferActionHandler implements WriteToolHandler {

    private final TransferService transferService;
    private final AgenticHandlerSupport support;

    @Override
    public String name() { return "create_transfer_internal"; }

    @Override
    public boolean requiresOtp() { return true; }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(name())
                .description("Internal transfer izmedju svojih racuna ISTE valute. " +
                        "Bez provizije, instant. Za FX (razlicite valute) koristi " +
                        "create_transfer_fx.")
                .param(new ToolDefinition.Param("fromAccountNumber", "string",
                        "Broj racuna sa kog se prebacuje", true, null, null))
                .param(new ToolDefinition.Param("toAccountNumber", "string",
                        "Broj racuna na koji se prebacuje", true, null, null))
                .param(new ToolDefinition.Param("amount", "number",
                        "Iznos za prebacivanje", true, null, null))
                .build();
    }

    @Override
    public PreviewResult buildPreview(Map<String, Object> args, UserContext user) {
        String from = support.getString(args, "fromAccountNumber");
        String to = support.getString(args, "toAccountNumber");
        BigDecimal amount = support.getBigDecimal(args, "amount");
        if (from == null || to == null || amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Nedostajuci parametri: fromAccountNumber, toAccountNumber, amount > 0");
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("Sa racuna", support.maskAccount(from));
        fields.put("Na racun", support.maskAccount(to));
        fields.put("Iznos", amount.toPlainString());
        return new PreviewResult("Transfer " + amount.toPlainString() + " interno", fields);
    }

    @Override
    public Map<String, Object> executeFinal(Map<String, Object> args, UserContext user, String otpCode) {
        TransferInternalRequestDto dto = new TransferInternalRequestDto();
        dto.setFromAccountNumber(support.getString(args, "fromAccountNumber"));
        dto.setToAccountNumber(support.getString(args, "toAccountNumber"));
        dto.setAmount(support.getBigDecimal(args, "amount"));
        dto.setOtpCode(otpCode);
        TransferResponseDto resp = transferService.internalTransfer(dto);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("transferId", resp.getId());
        result.put("status", resp.getStatus() == null ? "" : resp.getStatus().name());
        result.put("amount", resp.getAmount());
        result.put("fromAccount", support.maskAccount(resp.getFromAccountNumber()));
        result.put("toAccount", support.maskAccount(resp.getToAccountNumber()));
        return result;
    }
}
