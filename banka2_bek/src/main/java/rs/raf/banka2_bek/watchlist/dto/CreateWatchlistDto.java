package rs.raf.banka2_bek.watchlist.dto;

// ============================================================
// TODO [B6 - Watchlist | Nosilac: L. Draskovic]
//
// DTO za kreiranje nove liste pracenih hartija (request body za POST /watchlists).
// Koristi Jakarta Validation anotacije kao sto radi OpenDepositDto u savings paketu.
//
// IMPLEMENTIRATI (polja):
//   - @NotBlank @Size(min=1, max=120) String name
//       -- naziv liste; mora biti jedinstven po vlasniku (provera u service-u)
//
// Napomena: ownerType se odredjuje automatski u WatchlistService na osnovu JWT role
// (CLIENT ili EMPLOYEE), ne salje se u request body-ju.
//
// Koristiti za oba slucaja:
//   - Kreiranje liste (POST /watchlists)
//   - Preimenovanje liste (PATCH /watchlists/{id})
//
// Lombok: @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
//
// Konvencija: pratiti paket `savings` kao sablon (npr. OpenDepositDto).
// Spec: Zadaci_Backend.pdf, zadatak B6.
// ============================================================

import jakarta.validation.constraints.NotBlank;
import lombok.*;

// Napomena: skeleton je prazan pa nosi samo @NoArgsConstructor. Pri dodavanju
// polja vratiti @AllArgsConstructor i @Builder (videti Lombok napomenu u TODO-u).
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateWatchlistDto {
    @NotBlank(message = "Watchlist name cannot be blank.")
    private String name;
}
