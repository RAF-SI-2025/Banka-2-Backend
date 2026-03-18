package rs.raf.banka2_bek.payment.model;

import jakarta.persistence.*;
import lombok.*;

import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.client.model.Client;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String orderNumber;         // Automatski generisan broj naloga

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_account_id", nullable = false)
    private Account fromAccount;        // Račun platioca

    @Column(nullable = false, length = 18)
    private String toAccountNumber;     // Broj računa primaoca (string — može biti u drugoj banci)

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    @Column(length = 200)
    private String recipientName;       // Ime i prezime / naziv primaoca

    @Column(length = 10)
    private String paymentCode;         // Šifra plaćanja (iz šifarnika)

    @Column(length = 50)
    private String referenceNumber;     // Poziv na broj

    @Column(length = 200)
    private String purpose;             // Svrha plaćanja

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PROCESSING;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private Client createdBy;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
