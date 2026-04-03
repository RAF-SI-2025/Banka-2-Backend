package rs.raf.banka2_bek.card.dto;

import lombok.Builder;
import lombok.Data;
import rs.raf.banka2_bek.card.model.CardStatus;
import rs.raf.banka2_bek.card.model.CardType;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class CardResponseDto {
    private Long id;
    private String cardNumber;
    private String cardName;
    private String cvv;
    private CardType cardType;
    private Long accountId;
    private String accountNumber;
    private String ownerName;
    private BigDecimal cardLimit;
    private CardStatus status;
    private LocalDate createdAt;
    private LocalDate expirationDate;
}
