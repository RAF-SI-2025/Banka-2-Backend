package rs.raf.banka2_bek.profitbank.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

public final class ProfitBankDtos {

    private ProfitBankDtos() {}

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ActuaryProfitDto {
        private Long employeeId;
        private String name;
        private String position; // "SUPERVISOR" ili "AGENT"
        private BigDecimal totalProfitRsd;
        private Integer ordersDone;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class BankFundPositionDto {
        private Long fundId;
        private String fundName;
        private String managerName;
        private BigDecimal percentShare;
        private BigDecimal rsdValue;
        private BigDecimal profitRsd;
    }
}
