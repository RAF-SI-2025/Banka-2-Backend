package rs.raf.banka2_bek.margin.dto;


import java.math.BigDecimal;

public record MarginAccountCheckDto(
        Long marginAccountId,
        /* from Client table */
        String ownerEmail,
        BigDecimal maintenanceMargin,
        BigDecimal initialMargin
) {
    public BigDecimal calculateMaintenanceDeficit() {
        return maintenanceMargin.subtract(initialMargin).max(BigDecimal.ZERO);
    }
}