package rs.raf.banka2_bek.actuary.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateActuaryLimitDto {
    // TODO: Supervizor moze da menja limit i needApproval za agente
    private BigDecimal dailyLimit;
    private Boolean needApproval;
}
