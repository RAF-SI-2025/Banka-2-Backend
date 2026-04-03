package rs.raf.banka2_bek.berza.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Set;

/**
 * DTO za prikaz berze klijentu.
 *
 * Sadrzi sve podatke iz entiteta + computed polja:
 * - isCurrentlyOpen: da li je berza trenutno otvorena (uzima u obzir timezone i testMode)
 * - currentLocalTime: trenutno vreme u timezone-u berze (format HH:mm:ss)
 * - nextOpenTime: kada se berza sledeci put otvara (ISO 8601 datetime string)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExchangeDto {

    private Long id;
    private String name;
    private String acronym;
    private String micCode;
    private String country;
    private String currency;
    private String timeZone;
    private String openTime;
    private String closeTime;
    private String preMarketOpenTime;
    private String postMarketCloseTime;
    private boolean testMode;
    private boolean active;

    // ── Computed polja ──────────────────────────────────────────────────────────

    /** Da li je berza trenutno otvorena (uzima u obzir timezone, radno vreme, vikende, testMode) */
    private boolean isCurrentlyOpen;

    /** Trenutno lokalno vreme u timezone-u berze, format "HH:mm:ss" */
    private String currentLocalTime;

    /** Kada se berza sledeci put otvara (ISO 8601), null ako je trenutno otvorena */
    private String nextOpenTime;

    /** Datumi praznika kada je berza zatvorena */
    private Set<LocalDate> holidays;
}
