package rs.raf.banka2_bek.watchlist.dto;

// ============================================================
// TODO [B6 - Watchlist | Nosilac: L. Draskovic]
//
// DTO koji predstavlja jednu listu pracenih hartija u API odgovoru.
//
// IMPLEMENTIRATI (polja):
//   - Long id                        -- primarni kljuc liste
//   - Long ownerId                   -- ID vlasnika
//   - String ownerType               -- "CLIENT" ili "EMPLOYEE"
//   - String name                    -- naziv liste (npr. "Moje akcije")
//   - int itemCount                  -- broj hartija u listi (izracunava service)
//   - LocalDateTime createdAt        -- datum kreiranja
//
// Lombok: @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
//
// Konvencija: pratiti paket `savings` kao sablon (npr. SavingsDepositDto).
// Spec: Zadaci_Backend.pdf, zadatak B6.
// ============================================================

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WatchlistDto {

    private Long id;
    private Long ownerId;
    private String ownerType;
    private String name;
    private int itemCount;
    private LocalDateTime createdAt;
}