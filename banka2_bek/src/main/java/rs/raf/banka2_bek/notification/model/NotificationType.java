package rs.raf.banka2_bek.notification.model;

import lombok.Getter;

/**
 * [B1 — Foundation] Central registry of all notification types in the system.
 *
 * <p>When {@code sendsEmail = true}, {@code NotificationServiceImpl.notify()} also
 * publishes an {@code InAppNotificationEvent} that the async listener forwards to
 * the email channel. Currently, all email types share the same generic branded
 * template. Once B4 adds type-based dispatch inside
 * {@code MailSenderService.sendInAppNotificationMail()}, each type will render
 * its own template.
 *
 * <p>Task ownership — which task wires {@code notify()} calls that produce each type:
 * <ul>
 *   <li>[B2 — Andjela Vilcek] {@code ACCOUNT_LOCKED} — fired from
 *       {@code AccountLockoutService} after 5 failed login attempts.</li>
 *   <li>[B4 — Petar Poznanovic] {@code PAYMENT}, {@code TRANSFER},
 *       {@code LIMIT_CHANGE}, {@code CARD_BLOCKED}, {@code CARD_UNBLOCKED},
 *       {@code LOAN_CREATED}, {@code LOAN_APPROVED}, {@code LOAN_REJECTED},
 *       {@code ORDER_PENDING}, {@code ORDER_APPROVED}, {@code ORDER_DECLINED},
 *       {@code ORDER_EXECUTED}, {@code ORDER_PARTIAL_FILL}, {@code ORDER_CANCELLED},
 *       {@code OTC_COUNTER_OFFER}, {@code OTC_ACCEPTED}, {@code OTC_DECLINED},
 *       {@code OTC_CONTRACT_EXPIRING} — wired into payment, transfers, card,
 *       loan, order and otc modules.</li>
 *   <li>[B5 — Aleksa Vucinic] TODO: add {@code PRICE_ALERT(false)} — fired by the
 *       price-check scheduler when a threshold is crossed. In-app only, no email.</li>
 *   <li>[B8 — Nikola Djurovic] TODO: add {@code RECURRING_ORDER_SKIPPED(false)} —
 *       fired by {@code RecurringOrderScheduler} when an order is skipped due to
 *       insufficient funds. In-app only, no email.</li>
 *   <li>[B1] {@code GENERAL} — fallback for ad-hoc notifications without a
 *       dedicated type.</li>
 * </ul>
 */
@Getter
public enum NotificationType {

    // [B4 — Petar] Financial / account events
    PAYMENT(true),
    TRANSFER(true),
    LIMIT_CHANGE(true),
    CARD_BLOCKED(true),
    CARD_UNBLOCKED(true),
    LOAN_CREATED(true),
    LOAN_APPROVED(true),
    LOAN_REJECTED(true),

    // [B4 — Petar] Order lifecycle events
    ORDER_PENDING(false),
    ORDER_APPROVED(false),
    ORDER_DECLINED(false),
    ORDER_EXECUTED(false),
    ORDER_PARTIAL_FILL(false),
    ORDER_CANCELLED(false),

    // [B4 — Petar] OTC events
    OTC_COUNTER_OFFER(false),
    OTC_ACCEPTED(false),
    OTC_DECLINED(false),
    OTC_CONTRACT_EXPIRING(false),

    // [B2 — Andjela] Account security events
    ACCOUNT_LOCKED(true),

    // [B5 — Aleksa] TODO: PRICE_ALERT(false)
    // [B8 — Nikola Djurovic] Recurring order events
    RECURRING_ORDER_SKIPPED(false),

    // [B1] Fallback type for ad-hoc notifications
    GENERAL(false);

    private final boolean sendsEmail;

    NotificationType(boolean sendsEmail) {
        this.sendsEmail = sendsEmail;
    }

}
