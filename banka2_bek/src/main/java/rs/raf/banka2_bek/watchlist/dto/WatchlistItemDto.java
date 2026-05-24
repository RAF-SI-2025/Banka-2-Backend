package rs.raf.banka2_bek.watchlist.dto;

// ============================================================
// TODO [B6 - Watchlist | Nosilac: L. Draskovic]
//
// DTO koji predstavlja jednu stavku liste pracenja u API odgovoru.
// Sadrzi i zive trzisnee podatke koji se dohvataju iz listing/security servisa.
//
// IMPLEMENTIRATI (polja):
//   - Long id              -- primarni kljuc WatchlistItem reda
//   - Long watchlistId     -- ID liste kojoj pripada
//   - Long listingId       -- ID listinga (hartije) sa berze
//   - String ticker        -- simbol, npr. "AAPL"
//   - String listingName   -- puni naziv, npr. "Apple Inc."
//   - String securityType  -- "STOCK", "FOREX", "FUTURE", "OPTION"
//   - String exchangeName  -- naziv berze, npr. "NASDAQ"
//   - BigDecimal currentPrice  -- trenutna cena (dohvata se iz listing servisa)
//   - BigDecimal dailyChange   -- promena od otvaranja u % (moze biti negativna)
//   - Long volume              -- obim trgovanja za danasnji dan
//   - LocalDateTime addedAt    -- kada je korisnik dodao hartiju na listu
//
// Lombok: @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
//
// Konvencija: pratiti paket `savings` kao sablon (npr. SavingsTransactionDto).
// Spec: Zadaci_Backend.pdf, zadatak B6.
// ============================================================

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WatchlistItemDto {

    private Long id;
    private Long watchlistId;
    private Long listingId;

    private String ticker;
    private String listingName;
    private String securityType;
    private String exchangeName;

    private BigDecimal currentPrice;
    private BigDecimal dailyChange;
    private Long volume;

    private LocalDateTime addedAt;
}