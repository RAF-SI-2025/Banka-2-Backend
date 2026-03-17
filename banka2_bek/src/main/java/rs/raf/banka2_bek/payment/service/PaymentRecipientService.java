package rs.raf.banka2_bek.payment.service;

import org.springframework.data.domain.Page;
import rs.raf.banka2_bek.payment.dto.CreatePaymentRecipientRequestDto;
import rs.raf.banka2_bek.payment.dto.PaymentRecipientResponseDto;
import rs.raf.banka2_bek.payment.dto.UpdatePaymentRecipientRequestDto;

/**
 * Service for payment recipient CRUD. All operations are scoped to the current client.
 */
public interface PaymentRecipientService {

    /**
     * Returns a paginated list of payment recipients for the client identified by the given email.
     *
     * @param clientEmail client email
     * @param page        zero-based page index
     * @param limit       page size
     * @return page of payment recipient response DTOs
     */
    Page<PaymentRecipientResponseDto> getPaymentRecipients(String clientEmail, int page, int limit);

    /**
     * Creates a new payment recipient for the client identified by the given email.
     */
    PaymentRecipientResponseDto createPaymentRecipient(CreatePaymentRecipientRequestDto request, String clientEmail);

    /**
     * Updates an existing payment recipient by ID if it belongs to the client.
     */
    PaymentRecipientResponseDto updatePaymentRecipient(Long id, UpdatePaymentRecipientRequestDto request, String clientEmail);

    /**
     * Deletes a payment recipient by ID if it belongs to the client.
     */
    void deletePaymentRecipient(Long id, String clientEmail);
}
