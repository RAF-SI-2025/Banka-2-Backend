package rs.raf.banka2_bek.account.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AccountRequestResponseDto {
    private Long id;
    private String accountType;
    private String accountSubtype;
    private String currency;
    private BigDecimal initialDeposit;
    private Boolean createCard;
    private String clientEmail;
    private String clientName;
    private String status;
    private String rejectionReason;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
    private String processedBy;
}
