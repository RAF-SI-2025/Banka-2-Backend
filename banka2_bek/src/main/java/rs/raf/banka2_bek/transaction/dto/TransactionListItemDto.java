package rs.raf.banka2_bek.transaction.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Value
@Builder
public class TransactionListItemDto {
    Long id;
    String accountNumber;
    TransactionType type;
    BigDecimal debit;
    BigDecimal credit;
    LocalDateTime createdAt;
}
