package rs.raf.banka2_bek.interbank.protocol;

/**
 * Spec ref: protokol §2.9 Message exchange + §2.11 Sending messages
 *
 * Generic envelope za sve poruke izmedju banaka. message body je polimorfan:
 *  - messageType == NEW_TX     => message: Transaction
 *  - messageType == COMMIT_TX  => message: CommitTransaction
 *  - messageType == ROLLBACK_TX=> message: RollbackTransaction
 *
 * Transport: POST /interbank, Content-Type application/json, X-Api-Key header.
 *
 * Polimorfizam: pri deserijalizaciji se konkretni tip {@code T} bira preko
 * Jackson generic type-a u sendMessage poziva {@link rs.raf.banka2_bek.interbank.service.InterbankClient}.
 */
public record Message<T>(
        IdempotenceKey idempotenceKey,
        MessageType messageType,
        T message
) {}
