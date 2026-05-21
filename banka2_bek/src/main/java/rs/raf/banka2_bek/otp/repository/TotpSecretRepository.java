package rs.raf.banka2_bek.otp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rs.raf.banka2_bek.otp.model.TotpSecret;

import java.util.Optional;

// ============================================================
// TODO [B3 - TOTP verifikacioni kod | Nosilac: Nikola Stamenkovic]
//
// JPA repozitorijum za pristup tabeli totp_secrets.
//
// IMPLEMENTIRATI (custom metode):
//   - Optional<TotpSecret> findByUserId(Long userId) —
//       vraca aktivni TOTP secret za datog korisnika;
//       koristi se u TotpService.verify() i generateSecret()
//       da se utvrdi da li secret vec postoji pre nego sto se kreira novi
//   - void deleteByUserId(Long userId) —
//       brise postojeci secret pri regeneraciji (rotate);
//       pozvati pre snimanja novog TotpSecret u generateSecret()
//
// Konvencija: pratiti paket `savings` kao sablon
//   (Spring Data derived query metode, bez @Query osim kad je neophodna).
// Spec: Zadaci_Backend.pdf, zadatak B3.
// ============================================================
public interface TotpSecretRepository extends JpaRepository<TotpSecret, Long> {

    Optional<TotpSecret> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
