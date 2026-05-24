package rs.raf.banka2_bek.recurringorder.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// ============================================================
// TODO [B8 - Trajni nalozi (DCA / RecurringOrder) | Nosilac: Nikola Djurovic]
//
// DTO koji se vraca klijentu pri GET operacijama trajnog naloga.
//
// IMPLEMENTIRATI — dodati sva polja:
//   - Long id                    -> identifikator trajnog naloga
//   - Long ownerId               -> vlasnik naloga
//   - String ownerType           -> "CLIENT" ili "EMPLOYEE"
//   - Long listingId             -> ID hartije od vrednosti
//   - String listingTicker       -> ticker simbola (npr. "AAPL"); popuniti iz Listing entiteta
//                                   u mapper klasi / service sloju
//   - String direction           -> "BUY" ili "SELL"
//   - String mode                -> "BY_QUANTITY" ili "BY_AMOUNT" (toString() enuma)
//   - java.math.BigDecimal value -> kolicina ili iznos zavisno od mode-a
//   - Long accountId             -> ID racuna
//   - String cadence             -> "DAILY", "WEEKLY" ili "MONTHLY"
//   - java.time.LocalDateTime nextRun -> sledece izvrsavanje
//   - boolean active             -> da li je nalog aktivan
//   - java.time.LocalDateTime createdAt
//   - java.time.LocalDateTime updatedAt
//
// Koristiti Lombok @Data (ili @Getter @Setter @NoArgsConstructor @AllArgsConstructor)
// konzistentno sa ostalim DTO klasama u projektu (videti savings.dto.*).
//
// Konvencija: pratiti paket `savings` kao sablon.
// Spec: Zadaci_Backend.pdf, zadatak B8.
// ============================================================
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecurringOrderDto {

    private Long id;
    private Long ownerId;
    private String ownerType;
    private Long listingId;
    private String listingTicker;
    private String direction;
    private String mode;
    private BigDecimal value;
    private Long accountId;
    private String cadence;
    private LocalDateTime nextRun;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
