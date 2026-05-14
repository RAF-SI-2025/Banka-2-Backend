package rs.raf.banka2_bek.payment.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import rs.raf.banka2_bek.payment.dto.CreatePaymentRequestDto;
import rs.raf.banka2_bek.payment.dto.PaymentListItemDto;
import rs.raf.banka2_bek.payment.dto.PaymentResponseDto;
import rs.raf.banka2_bek.payment.model.PaymentStatus;
import rs.raf.banka2_bek.transaction.dto.TransactionListItemDto;
import rs.raf.banka2_bek.transaction.dto.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the two default methods on the {@link PaymentService} interface
 * ({@code getPayments(Pageable)} and {@code getPaymentHistory(Pageable)}) by
 * providing a minimal stub implementation and verifying the delegation.
 *
 * NOTE: JaCoCo reports the interface itself as "19 missed instructions" — this
 * is the bytecode for the two default methods plus their bridge stubs. There is
 * nothing else to cover here; the rest of the interface consists of abstract
 * method declarations which JaCoCo does not count.
 */
class PaymentServiceCoverageTest {

    @Test
    void defaultGetPayments_delegatesToOverloadWithNulls() {
        AtomicBoolean called = new AtomicBoolean(false);
        PaymentService svc = new PaymentService() {
            @Override public PaymentResponseDto createPayment(CreatePaymentRequestDto request) { return null; }
            @Override public Page<PaymentListItemDto> getPayments(Pageable pageable,
                                                                  LocalDateTime fromDate,
                                                                  LocalDateTime toDate,
                                                                  String accountNumber,
                                                                  BigDecimal minAmount,
                                                                  BigDecimal maxAmount,
                                                                  PaymentStatus status) {
                called.set(true);
                assertThat(fromDate).isNull();
                assertThat(toDate).isNull();
                assertThat(accountNumber).isNull();
                assertThat(minAmount).isNull();
                assertThat(maxAmount).isNull();
                assertThat(status).isNull();
                return new PageImpl<>(Collections.emptyList());
            }
            @Override public PaymentResponseDto getPaymentById(Long paymentId) { return null; }
            @Override public byte[] getPaymentReceipt(Long paymentId) { return new byte[0]; }
            @Override public void validatePayment(CreatePaymentRequestDto request) { /* no-op for coverage */ }
            @Override public Long recordAbortedPayment(CreatePaymentRequestDto request, String reason) { return null; }
            @Override public Page<TransactionListItemDto> getPaymentHistory(Pageable pageable,
                                                                            LocalDateTime fromDate,
                                                                            LocalDateTime toDate,
                                                                            BigDecimal minAmount,
                                                                            BigDecimal maxAmount,
                                                                            TransactionType type) {
                return new PageImpl<>(Collections.emptyList());
            }
        };

        Page<PaymentListItemDto> page = svc.getPayments(Pageable.unpaged());
        assertThat(called).isTrue();
        assertThat(page).isEmpty();
    }

    @Test
    void defaultGetPaymentHistory_delegatesToOverloadWithNulls() {
        AtomicBoolean called = new AtomicBoolean(false);
        PaymentService svc = new PaymentService() {
            @Override public PaymentResponseDto createPayment(CreatePaymentRequestDto request) { return null; }
            @Override public Page<PaymentListItemDto> getPayments(Pageable pageable,
                                                                  LocalDateTime fromDate,
                                                                  LocalDateTime toDate,
                                                                  String accountNumber,
                                                                  BigDecimal minAmount,
                                                                  BigDecimal maxAmount,
                                                                  PaymentStatus status) {
                return new PageImpl<>(Collections.emptyList());
            }
            @Override public PaymentResponseDto getPaymentById(Long paymentId) { return null; }
            @Override public byte[] getPaymentReceipt(Long paymentId) { return new byte[0]; }
            @Override public void validatePayment(CreatePaymentRequestDto request) { /* no-op for coverage */ }
            @Override public Long recordAbortedPayment(CreatePaymentRequestDto request, String reason) { return null; }
            @Override public Page<TransactionListItemDto> getPaymentHistory(Pageable pageable,
                                                                            LocalDateTime fromDate,
                                                                            LocalDateTime toDate,
                                                                            BigDecimal minAmount,
                                                                            BigDecimal maxAmount,
                                                                            TransactionType type) {
                called.set(true);
                assertThat(fromDate).isNull();
                assertThat(toDate).isNull();
                assertThat(minAmount).isNull();
                assertThat(maxAmount).isNull();
                assertThat(type).isNull();
                return new PageImpl<>(Collections.emptyList());
            }
        };

        Page<TransactionListItemDto> page = svc.getPaymentHistory(Pageable.unpaged());
        assertThat(called).isTrue();
        assertThat(page).isEmpty();
    }
}
