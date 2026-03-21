package rs.raf.banka2_bek.order.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderDto {
    private Long id;
    private String userName;
    private String userRole;
    private String listingTicker;
    private String listingName;
    private String listingType;
    private String orderType;
    private Integer quantity;
    private Integer contractSize;
    private BigDecimal pricePerUnit;
    private BigDecimal limitValue;
    private BigDecimal stopValue;
    private String direction;
    private String status;
    private String approvedBy;
    private boolean isDone;
    private Integer remainingPortions;
    private boolean afterHours;
    private boolean allOrNone;
    private boolean margin;
    private BigDecimal approximatePrice;
    private LocalDateTime createdAt;
    private LocalDateTime lastModification;
}
