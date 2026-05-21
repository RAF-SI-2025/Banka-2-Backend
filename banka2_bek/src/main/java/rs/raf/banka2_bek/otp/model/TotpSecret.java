package rs.raf.banka2_bek.otp.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

// ============================================================
// TODO [B3 - TOTP verifikacioni kod | Nosilac: Nikola Stamenkovic]
//
// Entitet koji cuva TOTP tajni kljuc (base32) za jednog korisnika.
//
// IMPLEMENTIRATI:
//   - userId (Long, @Column nullable=false, unique=true) —
//       interni ID korisnika (User.id) ciji se TOTP cuva;
//       unique constraint jer svaki korisnik ima tacno jedan aktivni secret
//   - secret (String, @Column nullable=false, length=64) —
//       base32-enkodovani tajni kljuc kompatibilan sa RFC 6238;
//       generise se npr. kroz GoogleAuthenticator.createCredentials().getKey()
//       (biblioteka `com.warrenstrange:googleauth:1.5.0` vec je preporucena u spec-u)
//   - createdAt (LocalDateTime, @CreationTimestamp, @Column nullable=false,
//       updatable=false) — vreme kreiranja/obnove secretа
//
// Konvencija: pratiti paket `savings` kao sablon (jakarta.persistence.*,
//   Lombok @Builder/@Getter/@Setter/@NoArgsConstructor/@AllArgsConstructor,
//   @ColumnDefault za defaultne vrednosti).
// Spec: Zadaci_Backend.pdf, zadatak B3.
// ============================================================
@Entity
@Table(name = "totp_secrets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TotpSecret {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false, length = 64)
    private String secret;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
