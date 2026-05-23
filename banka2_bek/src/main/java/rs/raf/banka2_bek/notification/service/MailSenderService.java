package rs.raf.banka2_bek.notification.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import rs.raf.banka2_bek.notification.event.InAppNotificationEvent;
import rs.raf.banka2_bek.notification.template.AccountCreatedConfirmationEmailTemplate;
import rs.raf.banka2_bek.notification.template.AccountLockedEmailTemplate;
import rs.raf.banka2_bek.notification.template.ActivationConfirmedEmailTemplate;
import rs.raf.banka2_bek.notification.template.ActivationEmailTemplate;
import rs.raf.banka2_bek.notification.template.OtpEmailTemplate;
import rs.raf.banka2_bek.notification.template.PasswordResetEmailTemplate;
import rs.raf.banka2_bek.notification.template.TransactionEmailTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;

/*
 * [B1 — Foundation] Email dispatch hub for the notification module.
 * The methods below are called either directly by legacy listeners (account
 * creation, activation, OTP) or via sendInAppNotificationMail() which is
 * triggered by InAppNotificationEventListener for every in-app notification
 * whose type has sendsEmail = true.
 *
 * ── ALREADY IMPLEMENTED (B1 foundation) ──────────────────────────────────
 *   Payment:         sendPaymentConfirmationMail(...)
 *   Card:            sendCardBlockedMail(...), sendCardUnblockedMail(...)
 *   Loan lifecycle:  sendLoanRequestSubmittedMail(...), sendLoanApprovedMail(...),
 *                    sendLoanRejectedMail(...)
 *   Installment:     sendInstallmentPaidMail(...), sendInstallmentFailedMail(...)
 *   Generic in-app:  sendInAppNotificationMail(...) — branded fallback used for
 *                    all types until B4 adds type-based dispatch.
 *
 * ── TODO [B4 — Petar Poznanovic] ─────────────────────────────────────────
 *   1. Add TransactionEmailTemplate methods (or a new template bean) for:
 *      - Transfer confirmation (TRANSFER)
 *      - Limit change notification (LIMIT_CHANGE)
 *      - Order lifecycle: ORDER_APPROVED, ORDER_DECLINED, ORDER_EXECUTED,
 *                         ORDER_PARTIAL_FILL, ORDER_CANCELLED
 *      - OTC events: OTC_COUNTER_OFFER, OTC_ACCEPTED, OTC_DECLINED,
 *                    OTC_CONTRACT_EXPIRING
 *   2. Replace the generic body of sendInAppNotificationMail() with a
 *      switch on event.getNotificationType() that dispatches to each specific
 *      method above; use event.getReferenceId() to load domain data where
 *      needed (e.g., fetch Payment by id for amount/account details).
 *      Retain the current generic fallback for unrecognised types.
 *   3. Add corresponding wiring: each service in payment, transfers, card,
 *      loan, order, otc calls NotificationService.notify() on the relevant
 *      event — notify() takes care of everything else.
 *
 */
@Service
public class MailSenderService {

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String passwordResetUrlBase;
    private final String passwordResetPagePath;
    private final String activationUrlBase;
    private final String activationPagePath;
    private final PasswordResetEmailTemplate passwordResetEmailTemplate;
    private final AccountLockedEmailTemplate accountLockedEmailTemplate;
    private final ActivationEmailTemplate activationEmailTemplate;
    private final ActivationConfirmedEmailTemplate activationConfirmedEmailTemplate;
    private final AccountCreatedConfirmationEmailTemplate accountCreatedConfirmationEmailTemplate;
    private final OtpEmailTemplate otpEmailTemplate;
    private final TransactionEmailTemplate transactionEmailTemplate;

    public MailSenderService(JavaMailSender mailSender,
                             PasswordResetEmailTemplate passwordResetEmailTemplate,
                             AccountLockedEmailTemplate accountLockedEmailTemplate,
                             ActivationEmailTemplate activationEmailTemplate,
                             ActivationConfirmedEmailTemplate activationConfirmedEmailTemplate,
                             AccountCreatedConfirmationEmailTemplate accountCreatedConfirmationEmailTemplate,
                             OtpEmailTemplate otpEmailTemplate,
                             TransactionEmailTemplate transactionEmailTemplate,
                             @Value("${spring.mail.username}") String fromAddress,
                             @Value("${notification.password-reset-url-base}") String passwordResetUrlBase,
                             @Value("${notification.password-reset-page-path:/reset-password}") String passwordResetPagePath,
                             @Value("${notification.activation-url-base}") String activationUrlBase,
                             @Value("${notification.activation-page-path:/activate-account}") String activationPagePath) {
        this.mailSender = mailSender;
        this.passwordResetEmailTemplate = passwordResetEmailTemplate;
        this.accountLockedEmailTemplate = accountLockedEmailTemplate;
        this.activationEmailTemplate = activationEmailTemplate;
        this.activationConfirmedEmailTemplate = activationConfirmedEmailTemplate;
        this.fromAddress = fromAddress;
        this.passwordResetUrlBase = passwordResetUrlBase;
        this.passwordResetPagePath = passwordResetPagePath;
        this.activationUrlBase = activationUrlBase;
        this.activationPagePath = activationPagePath;
        this.accountCreatedConfirmationEmailTemplate = accountCreatedConfirmationEmailTemplate;
        this.otpEmailTemplate = otpEmailTemplate;
        this.transactionEmailTemplate = transactionEmailTemplate;
    }

    public void sendPasswordResetMail(String toEmail, String token) {
        String resetLink = passwordResetUrlBase + passwordResetPagePath + "?token=" + token;
        String subject = passwordResetEmailTemplate.buildSubject();
        String html = passwordResetEmailTemplate.buildBody(resetLink);

        HtmlMailSender.sendHtmlMail(mailSender, fromAddress, toEmail, subject, html);
    }

    public void sendAccountLockedMail(String toEmail, int lockMinutes) {
        String resetLink = passwordResetUrlBase + passwordResetPagePath;
        String subject = accountLockedEmailTemplate.buildSubject();
        String html = accountLockedEmailTemplate.buildBody(lockMinutes, resetLink);
        HtmlMailSender.sendHtmlMail(mailSender, fromAddress, toEmail, subject, html);
    }

    public void sendActivationMail(String toEmail, String firstName, String token) {
        String activationLink = activationUrlBase + activationPagePath + "?token=" + token;
        String subject = activationEmailTemplate.buildSubject();
        String html = activationEmailTemplate.buildBody(activationLink, firstName);

        HtmlMailSender.sendHtmlMail(mailSender, fromAddress, toEmail, subject, html);
    }

    public void sendActivationConfirmationMail(String toEmail, String firstName) {
        String subject = activationConfirmedEmailTemplate.buildSubject();
        String html = activationConfirmedEmailTemplate.buildBody(firstName);

        HtmlMailSender.sendHtmlMail(mailSender, fromAddress, toEmail, subject, html);
    }

    public void sendAccountCreatedConfirmationMail(String toEmail, String firstName, String accountNumber, String accountType) {
        String subject = accountCreatedConfirmationEmailTemplate.buildSubject();
        String html = accountCreatedConfirmationEmailTemplate.buildBody(firstName, accountNumber, accountType);

        HtmlMailSender.sendHtmlMail(mailSender, fromAddress, toEmail, subject, html);
    }

    public void sendOtpMail(String toEmail, String code, int expiryMinutes) {
        String subject = otpEmailTemplate.buildSubject();
        String html = otpEmailTemplate.buildBody(code, expiryMinutes);

        HtmlMailSender.sendHtmlMail(mailSender, fromAddress, toEmail, subject, html);
    }

    public void sendPaymentConfirmationMail(String toEmail, BigDecimal amount, String currency,
                                            String fromAccount, String toAccount,
                                            LocalDate date, String status) {
        String subject = transactionEmailTemplate.buildPaymentSubject();
        String html = transactionEmailTemplate.buildPaymentBody(amount, currency, fromAccount, toAccount, date, status);
        HtmlMailSender.sendHtmlMail(mailSender, fromAddress, toEmail, subject, html);
    }

    public void sendCardBlockedMail(String toEmail, String last4Digits, LocalDate blockDate) {
        String subject = transactionEmailTemplate.buildCardBlockedSubject();
        String html = transactionEmailTemplate.buildCardBlockedBody(last4Digits, blockDate);
        HtmlMailSender.sendHtmlMail(mailSender, fromAddress, toEmail, subject, html);
    }

    public void sendCardUnblockedMail(String toEmail, String last4Digits) {
        String subject = transactionEmailTemplate.buildCardUnblockedSubject();
        String html = transactionEmailTemplate.buildCardUnblockedBody(last4Digits);
        HtmlMailSender.sendHtmlMail(mailSender, fromAddress, toEmail, subject, html);
    }

    public void sendLoanRequestSubmittedMail(String toEmail, String loanType,
                                             BigDecimal amount, String currency) {
        String subject = transactionEmailTemplate.buildLoanRequestSubject();
        String html = transactionEmailTemplate.buildLoanRequestBody(loanType, amount, currency);
        HtmlMailSender.sendHtmlMail(mailSender, fromAddress, toEmail, subject, html);
    }

    public void sendLoanApprovedMail(String toEmail, String loanNumber, BigDecimal amount,
                                     String currency, BigDecimal monthlyPayment, LocalDate startDate) {
        String subject = transactionEmailTemplate.buildLoanApprovedSubject();
        String html = transactionEmailTemplate.buildLoanApprovedBody(loanNumber, amount, currency, monthlyPayment, startDate);
        HtmlMailSender.sendHtmlMail(mailSender, fromAddress, toEmail, subject, html);
    }

    public void sendLoanRejectedMail(String toEmail, String loanType,
                                     BigDecimal amount, String currency) {
        String subject = transactionEmailTemplate.buildLoanRejectedSubject();
        String html = transactionEmailTemplate.buildLoanRejectedBody(loanType, amount, currency);
        HtmlMailSender.sendHtmlMail(mailSender, fromAddress, toEmail, subject, html);
    }

    public void sendInstallmentPaidMail(String toEmail, String loanNumber,
                                        BigDecimal installmentAmount, String currency,
                                        BigDecimal remainingDebt) {
        String subject = transactionEmailTemplate.buildInstallmentPaidSubject();
        String html = transactionEmailTemplate.buildInstallmentPaidBody(loanNumber, installmentAmount, currency, remainingDebt);
        HtmlMailSender.sendHtmlMail(mailSender, fromAddress, toEmail, subject, html);
    }

    public void sendInstallmentFailedMail(String toEmail, String loanNumber,
                                          BigDecimal amountDue, String currency,
                                          LocalDate nextRetryDate) {
        String subject = transactionEmailTemplate.buildInstallmentFailedSubject();
        String html = transactionEmailTemplate.buildInstallmentFailedBody(loanNumber, amountDue, currency, nextRetryDate);
        HtmlMailSender.sendHtmlMail(mailSender, fromAddress, toEmail, subject, html);
    }


    /**
     * [B1] Sends a generic branded email for any in-app notification type.
     *
     * <p>Called by {@link rs.raf.banka2_bek.notification.listener.InAppNotificationEventListener}
     * after the notification transaction commits. Uses {@code event.title} as
     * the email subject and personalizes the greeting with {@code firstName}
     * when it is present and non-blank; falls back to a neutral greeting
     * otherwise.
     *
     * <p>[B4 — Petar] This method is the primary extension point: replace the
     * generic body with a {@code switch} on {@code event.getNotificationType()}
     * that delegates to the appropriate {@code send*Mail(...)} method
     * (e.g. {@link #sendPaymentConfirmationMail} for {@code PAYMENT}).
     * Use {@code event.getReferenceId()} to load domain-specific data where
     * needed for rich template rendering. Retain this generic path as the
     * default fallback for any type without a dedicated template.
     *
     * @param event the event carrying recipient contact details and
     *              notification content; must not be {@code null}
     */
    public void sendInAppNotificationMail(InAppNotificationEvent event) {
        String greeting = (event.getFirstName() != null && !event.getFirstName().isBlank())
                ? "Poštovani " + event.getFirstName() + ","
                : "Poštovani,";
        String html = """
                <div style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;max-width:520px;margin:0 auto;color:#1f2937;">
                    <div style="background:linear-gradient(135deg,#6366f1,#7c3aed);padding:24px;border-radius:12px 12px 0 0;">
                        <p style="margin:0;font-size:12px;letter-spacing:0.08em;text-transform:uppercase;color:rgba(255,255,255,0.7);">Banka 2</p>
                        <h1 style="margin:4px 0 0 0;font-size:20px;color:#ffffff;">%s</h1>
                    </div>
                    <div style="padding:24px;background:#ffffff;border:1px solid #e5e7eb;border-top:none;border-radius:0 0 12px 12px;">
                        <p style="margin:0 0 12px 0;font-size:14px;color:#4b5563;">%s</p>
                        <p style="margin:0;font-size:14px;color:#4b5563;line-height:1.6;">%s</p>
                        <p style="margin:24px 0 0 0;font-size:11px;color:#9ca3af;">Ovo je automatska poruka od Banka 2. Molimo ne odgovarajte na ovaj email.</p>
                    </div>
                </div>
                """.formatted(event.getTitle(), greeting, event.getBody());
        HtmlMailSender.sendHtmlMail(mailSender, fromAddress, event.getRecipientEmail(), event.getTitle(), html);
    }

}

