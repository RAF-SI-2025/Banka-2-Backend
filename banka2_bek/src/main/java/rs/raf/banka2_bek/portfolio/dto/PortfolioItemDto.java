package rs.raf.banka2_bek.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioItemDto {
    private Long id;
    private Long listingId;
    private String listingTicker;
    private String listingName;
    private String listingType;
    private Integer quantity;
    private BigDecimal averageBuyPrice;
    private BigDecimal currentPrice;
    private BigDecimal profit;
    private BigDecimal profitPercent;
    private Integer publicQuantity;
    private LocalDateTime lastModified;
}
