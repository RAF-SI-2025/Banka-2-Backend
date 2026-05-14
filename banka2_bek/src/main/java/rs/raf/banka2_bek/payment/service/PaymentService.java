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

    /**
     * Preflight validacija placanja BEZ persistovanja. Provera vlasnistva,
     * stanja, limita i postojanja primaoca. Koristi se iz `POST /payments/request-otp`
     * (Sc T2-009 fix) da bi se odbacio nevazece placanje PRE generisanja OTP koda.
     * Baca IllegalArgumentException sa user-friendly porukom ako validacija puca.
     */
    void validatePayment(CreatePaymentRequestDto request);

    /**
     * T2-012 audit trail: kada OTP istekne / korisnik unese 3 puta pogresan kod,
     * persistira ABORTED payment red da bi se sacuvao audit. Best-effort —
     * ako podaci nisu kompletni, vraca null bez exception-a (klijent vec
     * dobija 403 status koji ne treba zamenjivati internim 500).
     *
     * @return id zapisa ako je persistovan, inace null
     */
    Long recordAbortedPayment(CreatePaymentRequestDto request, String reason);

    default Page<PaymentListItemDto> getPayments(Pageable pageable) {
        return getPayments(pageable, null, null, null, null, null, null);
    }

    Page<PaymentListItemDto> getPayments(
            Pageable pageable,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            String accountNumber,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            PaymentStatus status
    );

    PaymentResponseDto getPaymentById(Long paymentId);

    byte[] getPaymentReceipt(Long paymentId);

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
