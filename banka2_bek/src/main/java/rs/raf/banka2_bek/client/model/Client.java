package rs.raf.banka2_bek.client.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.payment.model.PaymentRecipient;
import rs.raf.banka2_bek.payment.model.PaymentStatus;

@Entity
@Table(name = "clients", uniqueConstraints = {
        @UniqueConstraint(name = "uk_clients_email", columnNames = "email")
}
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String firstName;

    @Column(nullable = false, length = 50)
    private String lastName;

    @Column(nullable = false)
    private LocalDate dateOfBirth;

    @Column(length = 10)
    private String gender;          // M / F / OTHER

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(length = 200)
    private String address;

    @Column(nullable = false, length = 255)
    private String password;        // Heširana lozinka (bcrypt/Argon2)

    @Column(nullable = false, length = 64)
    private String saltPassword;    // Random salt, generiše se pri kreiranju

    // Token za aktivaciju naloga — brisan nakon aktivacije
    @Column(length = 255)
    private String activationToken;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = false;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    // ── Relacije ──────────────────────────────────────────────────────────────

    @OneToMany(mappedBy = "client", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Account> accounts = new ArrayList<>();

    @OneToMany(mappedBy = "client", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PaymentRecipient> paymentRecipients = new ArrayList<>();
}
