package rs.raf.banka2_bek.otc.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// ============================================================
// TODO [B10 - Istorija OTC pregovora | Nosilac: Aja Timotic]
//
// DTO koji se vraca klijentu pri pregledu historije jednog
// pregovora ili liste istorijskih zapisa.
//
// IMPLEMENTIRATI — dodati sledeca polja:
//
//   - id : Long
//       Primarni kljuc zapisa.
//
//   - negotiationId : Long
//       ID originalne ponude (OtcOffer) na koji se zapis odnosi.
//
//   - quantity : Integer
//       Kolicina akcija u toj iteraciji pregovora.
//
//   - pricePerShare : BigDecimal
//       Cena po akciji u toj iteraciji.
//       Import: import java.math.BigDecimal;
//
//   - premium : BigDecimal
//       Premija opcije u toj iteraciji.
//
//   - settlementDate : LocalDate
//       Datum izmirenja koji je vazio u toj iteraciji.
//       Import: import java.time.LocalDate;
//
//   - status : String
//       Status ponude u trenutku zapisa.
//
//   - modifiedById : Long
//       ID korisnika koji je izvrsio izmenu.
//
//   - modifiedByName : String
//       Puno ime korisnika (snapshot).
//
//   - createdAt : LocalDateTime
//       Vreme kad je snimak nastao.
//       Import: import java.time.LocalDateTime;
//
// Anotacije na klasi: @Getter, @Setter, @NoArgsConstructor,
//   @AllArgsConstructor, @Builder (Lombok — prati OtcOfferDto
//   i SavingsDepositDto kao sablon za stil anotacija).
//
// Importi: import lombok.*;
//
// Konvencija: pratiti paket `savings` kao sablon.
// Spec: Zadaci_Backend.pdf, zadatak B10.
// ============================================================
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtcNegotiationHistoryDto {
    private Long id;

    private Long negotiationId;

    private Integer quantity;

    private BigDecimal pricePerShare;

    private BigDecimal premium;

    private LocalDate settlementDate;

    private String status;

    private Long modifiedById;

    private String modifiedByName;

    private LocalDateTime createdAt;
}
