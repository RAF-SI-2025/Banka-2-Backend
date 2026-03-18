package rs.raf.banka2_bek.payment.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import rs.raf.banka2_bek.payment.dto.CreatePaymentRequestDto;
import rs.raf.banka2_bek.payment.dto.PaymentListItemDto;
import rs.raf.banka2_bek.payment.dto.PaymentResponseDto;
import rs.raf.banka2_bek.payment.model.PaymentStatus;
import rs.raf.banka2_bek.transaction.dto.TransactionListItemDto;
import rs.raf.banka2_bek.transaction.dto.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface PaymentService {

    PaymentResponseDto createPayment(CreatePaymentRequestDto request);

    default Page<PaymentListItemDto> getPayments(Pageable pageable) {
        return getPayments(pageable, null, null, null, null, null);
    }

    Page<PaymentListItemDto> getPayments(
            Pageable pageable,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            PaymentStatus status
    );

    PaymentResponseDto getPaymentById(Long paymentId);

    default Page<TransactionListItemDto> getPaymentHistory(Pageable pageable) {
        return getPaymentHistory(pageable, null, null, null, null, null);
    }

    Page<TransactionListItemDto> getPaymentHistory(
            Pageable pageable,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            TransactionType type
    );
}
