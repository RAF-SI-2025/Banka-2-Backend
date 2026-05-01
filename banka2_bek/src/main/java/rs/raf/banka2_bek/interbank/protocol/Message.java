package rs.raf.banka2_bek.interbank.protocol;

import lombok.Getter;

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
 * TODO: Jackson polimorfizam — registrovati de-serializer koji bira tip
 * `message` poljem na osnovu `messageType`. Vidi Asset.java za pattern.
 */
public record Message<T>(
        IdempotenceKey idempotenceKey,
        MessageType messageType,
        T message
) {}
