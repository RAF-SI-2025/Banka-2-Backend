package rs.raf.banka2_bek.employee.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Vraca trenutno stanje aktivacionog tokena za FE da bi mogao da odluci
 * sta da prikaze pre nego sto user popuni formu.
 *
 * Spec Celina 1 Sc 9 + ad-hoc bag prijavljen 12.05.2026 ("FE dozvoljava
 * reotvaranje forme za aktivaciju naloga nakon sto je activation token vec
 * iskoriscen"). Pre fix-a, FE je uvek renderovao formu cim postoji `token`
 * query param, pa korisnik moze prosto da osvezi stranicu i ponovo pokusa
 * aktivaciju iako je nalog vec aktiviran (BE bi vratio 400 ali korisnik je
 * imao formu pred sobom).
 *
 * `status` enum-like polje: VALID / USED / EXPIRED / INVALID / ALREADY_ACTIVE.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivationTokenStatusDto {
    /** Jedna od vrednosti: VALID, USED, EXPIRED, INVALID, ALREADY_ACTIVE. */
    private String status;
    /** Trenutak isteka tokena (null ako je INVALID). */
    private LocalDateTime expiresAt;
    /** Email vlasnika tokena radi prikaza (null ako je INVALID, sigurnosno minimalno). */
    private String email;
}
