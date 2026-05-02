package rs.raf.banka2_bek.assistant.tool.handlers.agentic;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.assistant.tool.ToolDefinition;
import rs.raf.banka2_bek.assistant.tool.WriteToolHandler;
import rs.raf.banka2_bek.auth.util.UserContext;
import rs.raf.banka2_bek.transfers.dto.TransferFxRequestDto;
import rs.raf.banka2_bek.transfers.dto.TransferResponseDto;
import rs.raf.banka2_bek.transfers.service.TransferService;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 4 v3.5 — FX transfer izmedju svojih racuna RAZLICITIH valuta.
 * Klijenti placaju 0.5% proviziju + razliku u kursu.
 */
@Component
@RequiredArgsConstructor
public class CreateFxTransferActionHandler implements WriteToolHandler {

    private final TransferService transferService;
    private final AgenticHandlerSupport support;

    @Override
    public String name() { return "create_transfer_fx"; }

    @Override
    public boolean requiresOtp() { return true; }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(name())
                .description("FX transfer izmedju svojih racuna RAZLICITIH valuta. " +
                        "Klijenti placaju 0.5% proviziju + razliku u kursu.")
                .param(new ToolDefinition.Param("fromAccountNumber", "string",
                        "Broj racuna sa kog ide novac", true, null, null))
                .param(new ToolDefinition.Param("toAccountNumber", "string",
                        "Broj racuna u drugoj valuti", true, null, null))
                .param(new ToolDefinition.Param("amount", "number",
                        "Iznos u valuti from racuna", true, null, null))
                .build();
    }

    @Override
    public PreviewResult buildPreview(Map<String, Object> args, UserContext user) {
        String from = support.getString(args, "fromAccountNumber");
        String to = support.getString(args, "toAccountNumber");
        BigDecimal amount = support.getBigDecimal(args, "amount");
        if (from == null || to == null || amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Nedostajuci parametri");
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("Sa racuna", support.maskAccount(from));
        fields.put("Na racun", support.maskAccount(to));
        fields.put("Iznos (from)", amount.toPlainString());
        return new PreviewResult("FX transfer " + amount.toPlainString(),
                fields, List.of("FX transfer placa 0.5% proviziju + dnevni kurs."));
    }

    @Override
    public Map<String, Object> executeFinal(Map<String, Object> args, UserContext user, String otpCode) {
        TransferFxRequestDto dto = new TransferFxRequestDto();
        dto.setFromAccountNumber(support.getString(args, "fromAccountNumber"));
        dto.setToAccountNumber(support.getString(args, "toAccountNumber"));
        dto.setAmount(support.getBigDecimal(args, "amount"));
        dto.setOtpCode(otpCode);
        TransferResponseDto resp = transferService.fxTransfer(dto);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("transferId", resp.getId());
        result.put("status", resp.getStatus() == null ? "" : resp.getStatus().name());
        result.put("fromAmount", resp.getAmount());
        result.put("toAmount", resp.getToAmount());
        result.put("commission", resp.getCommission());
        result.put("exchangeRate", resp.getExchangeRate());
        return result;
    }
}
