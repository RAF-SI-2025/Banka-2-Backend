package rs.raf.banka2_bek.payment.service.implementation;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.payment.dto.CreatePaymentRecipientRequestDto;
import rs.raf.banka2_bek.payment.dto.PaymentRecipientResponseDto;
import rs.raf.banka2_bek.payment.dto.UpdatePaymentRecipientRequestDto;
import rs.raf.banka2_bek.payment.model.PaymentRecipient;
import rs.raf.banka2_bek.payment.repository.PaymentRecipientRepository;
import rs.raf.banka2_bek.payment.service.PaymentRecipientService;

@Service
@RequiredArgsConstructor
public class PaymentRecipientServiceImpl implements PaymentRecipientService {

    private final PaymentRecipientRepository paymentRecipientRepository;
    private final ClientRepository clientRepository;

    @Override
    public Page<PaymentRecipientResponseDto> getPaymentRecipients(String clientEmail, int page, int limit) {
        Client client = getClientByEmail(clientEmail);
        Pageable pageable = PageRequest.of(page, limit);
        return paymentRecipientRepository.findByClientOrderByCreatedAtDesc(client, pageable)
                .map(this::toResponse);
    }

    @Override
    @Transactional
    public PaymentRecipientResponseDto createPaymentRecipient(CreatePaymentRecipientRequestDto request, String clientEmail) {
        Client client = getClientByEmail(clientEmail);
        PaymentRecipient recipient = PaymentRecipient.builder()
                .client(client)
                .name(request.getName())
                .accountNumber(request.getAccountNumber())
                .build();
        recipient = paymentRecipientRepository.save(recipient);
        return toResponse(recipient);
    }

    @Override
    @Transactional
    public PaymentRecipientResponseDto updatePaymentRecipient(Long id, UpdatePaymentRecipientRequestDto request, String clientEmail) {
        Client client = getClientByEmail(clientEmail);
        PaymentRecipient recipient = paymentRecipientRepository.findByIdAndClient(id, client)
                .orElseThrow(() -> new IllegalArgumentException("Payment recipient with ID " + id + " not found."));

        if (request.getName() != null) {
            recipient.setName(request.getName());
        }
        if (request.getAccountNumber() != null) {
            recipient.setAccountNumber(request.getAccountNumber());
        }
        recipient = paymentRecipientRepository.save(recipient);
        return toResponse(recipient);
    }

    @Override
    @Transactional
    public void deletePaymentRecipient(Long id, String clientEmail) {
        Client client = getClientByEmail(clientEmail);
        if (!paymentRecipientRepository.existsByIdAndClient(id, client)) {
            throw new IllegalArgumentException("Payment recipient with ID " + id + " not found.");
        }
        paymentRecipientRepository.deleteById(id);
    }

    private Client getClientByEmail(String email) {
        return clientRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Client not found for email: " + email));
    }

    private PaymentRecipientResponseDto toResponse(PaymentRecipient r) {
        return PaymentRecipientResponseDto.builder()
                .id(r.getId())
                .name(r.getName())
                .accountNumber(r.getAccountNumber())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
