package rs.raf.banka2_bek.actuary.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ActuaryInfoDto {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private String employeeEmail;
    private String employeePosition;
    private String actuaryType; // AGENT ili SUPERVISOR
    private BigDecimal dailyLimit;
    private BigDecimal usedLimit;
    private boolean needApproval;
}
