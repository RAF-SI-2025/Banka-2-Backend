package rs.raf.banka2_bek.interbank.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import rs.raf.banka2_bek.interbank.protocol.MessageType;

import java.time.LocalDateTime;

/*
================================================================================
 INTERBANK MESSAGE — AUDIT LOG + IDEMPOTENCY (PROTOKOL §2.2, §2.9)
 Spec ref: protokol §2.2 Idempotence keys (banka MORA pratiti zauvijek),
           §2.9 Message exchange (at-most-once semantika preko ponavljanja)
--------------------------------------------------------------------------------
 SVRHA:
   1) IDEMPOTENCY: pri prijemu poruke sa istim (routingNumber, locallyGeneratedKey)
      vracamo cached response (responseBody + httpStatus) atomicno.
   2) AUDIT: ko je sta poslao i kad — za debug + reklamacije.
   3) RETRY (outbound): poruke u status=PENDING se retry-uju dok ne dobiju
      200/204 (status=SENT) ili dosegnu MAX_RETRY (status=STUCK).

 KLJUCEVI (po protokolu §2.2):
  - sender_routing_number — int (ne string), prve 3 cifre racuna posiljaoca
  - locally_generated_key — string max 64 bajta, opaque za nas
  - PAR (sender_routing_number, locally_generated_key) je UNIQUE — ova kombinacija
    cini full IdempotenceKey

 STATUSI:
  - PENDING  — outbound: poslata ali odgovor jos nije primljen (retry-uje se);
               ili 202 Accepted primljen
  - SENT     — outbound: 200/204 primljen
  - STUCK    — outbound: dosegnut MAX_RETRY, supervisor mora intervenisati
  - INBOUND  — primljena poruka (uvek terminalna; retry handluje pošiljač)

 INDEX:
  - (sender_routing_number, locally_generated_key) UNIQUE — fast idempotency lookup
  - (status, last_attempt_at) — za retry scheduler

 NAPOMENA O PERZISTENCIJI BODY-JA:
  request_body i response_body su JSON string-ovi (cuvamo originalni payload).
  Razmotri @JdbcTypeCode(SqlTypes.JSON) za PG jsonb mapiranje — sad plain text.
================================================================================
*/
@Entity
@Table(name = "interbank_messages", indexes = {
        @Index(
                name = "idx_ibm_idempotence",
                columnList = "sender_routing_number, locally_generated_key",
                unique = true
        ),
        @Index(name = "idx_ibm_status_attempt", columnList = "status, last_attempt_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterbankMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** §2.2 IdempotenceKey.routingNumber — banka posiljaoca. */
    @Column(name = "sender_routing_number", nullable = false)
    private Integer senderRoutingNumber;

    /** §2.2 IdempotenceKey.locallyGeneratedKey — max 64 bajta. */
    @Column(name = "locally_generated_key", nullable = false, length = 64)
    private String locallyGeneratedKey;

    /** §2.12 — NEW_TX / COMMIT_TX / ROLLBACK_TX. */
    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 16)
    private MessageType messageType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InterbankMessageDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InterbankMessageStatus status;

    /** Routing number druge banke (za inbound: posiljalac; outbound: primalac). */
    @Column(name = "peer_routing_number", nullable = false)
    private Integer peerRoutingNumber;

    /** §2.8.2 transactionId.id — string oblika ForeignBankId.id; za korelaciju. */
    @Column(name = "transaction_id", length = 64)
    private String transactionId;

    /** Originalni JSON request payload — koristimo za retry i audit. */
    @Column(name = "request_body", columnDefinition = "text")
    private String requestBody;

    /** Cached response (§2.2): vracamo isti odgovor pri retry-u. */
    @Column(name = "response_body", columnDefinition = "text")
    private String responseBody;

    /** HTTP status koji smo poslali (inbound) ili primili (outbound). */
    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "retry_count", nullable = false)
    @ColumnDefault("0")
    private Integer retryCount = 0;

    @Column(name = "last_error", length = 1024)
    private String lastError;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * Optimistic lock version — sprecava race condition izmedju dva retry
     * scheduler-a koja istovremeno povuku istu PENDING poruku. JPA baca
     * OptimisticLockException ako se verzija promenila izmedju load-a i save-a;
     * scheduler tretira to kao "drugi je vec uzeo, preskoci do sledeceg ciklusa".
     */
    @Version
    @Column(name = "version")
    private Long version;

}
