package rs.raf.banka2_bek.payment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.payment.dto.CreatePaymentRecipientRequestDto;
import rs.raf.banka2_bek.payment.dto.PaymentRecipientResponseDto;
import rs.raf.banka2_bek.payment.dto.UpdatePaymentRecipientRequestDto;
import rs.raf.banka2_bek.payment.model.PaymentRecipient;
import rs.raf.banka2_bek.payment.repository.PaymentRecipientRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentRecipientServiceImplTest {

    private static final String CLIENT_EMAIL = "client@test.com";
    private static final long EXISTING_RECIPIENT_ID = 10L;

    @Mock
    private PaymentRecipientRepository paymentRecipientRepository;

    @Mock
    private ClientRepository clientRepository;

    @InjectMocks
    private rs.raf.banka2_bek.payment.service.implementation.PaymentRecipientServiceImpl paymentRecipientService;

    private Client client;
    private PaymentRecipient existingRecipient;
    private CreatePaymentRecipientRequestDto createRequest;

    @BeforeEach
    void setUp() {
        client = Client.builder()
                .id(1L)
                .email(CLIENT_EMAIL)
                .firstName("Test")
                .lastName("Client")
                .build();
        existingRecipient = PaymentRecipient.builder()
                .id(EXISTING_RECIPIENT_ID)
                .client(client)
                .name("Existing Recipient")
                .accountNumber("123456789012345678")
                .build();
        createRequest = new CreatePaymentRecipientRequestDto();
        createRequest.setName("New Recipient");
        createRequest.setAccountNumber("987654321098765432");
    }

    @Test
    void getPaymentRecipients_returnsListForClient() {
        Pageable pageable = PageRequest.of(0, 10);
        when(clientRepository.findByEmail(CLIENT_EMAIL)).thenReturn(Optional.of(client));
        when(paymentRecipientRepository.findByClientOrderByCreatedAtDesc(eq(client), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(existingRecipient), pageable, 1));

        var result = paymentRecipientService.getPaymentRecipients(CLIENT_EMAIL, 0, 10);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(EXISTING_RECIPIENT_ID);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Existing Recipient");
        assertThat(result.getContent().get(0).getAccountNumber()).isEqualTo("123456789012345678");
        verify(clientRepository).findByEmail(CLIENT_EMAIL);
        verify(paymentRecipientRepository).findByClientOrderByCreatedAtDesc(eq(client), any(Pageable.class));
    }

    @Test
    void getPaymentRecipients_throwsWhenClientNotFound() {
        when(clientRepository.findByEmail(CLIENT_EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentRecipientService.getPaymentRecipients(CLIENT_EMAIL, 0, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Client not found");
    }

    @Test
    void createPaymentRecipient_savesAndReturnsDto() {
        when(clientRepository.findByEmail(CLIENT_EMAIL)).thenReturn(Optional.of(client));
        when(paymentRecipientRepository.save(any(PaymentRecipient.class))).thenAnswer(inv -> {
            PaymentRecipient r = inv.getArgument(0);
            r.setId(20L);
            return r;
        });

        PaymentRecipientResponseDto result = paymentRecipientService.createPaymentRecipient(createRequest, CLIENT_EMAIL);

        assertThat(result.getId()).isEqualTo(20L);
        assertThat(result.getName()).isEqualTo("New Recipient");
        assertThat(result.getAccountNumber()).isEqualTo("987654321098765432");
        assertThat(result.getCreatedAt()).isNotNull();

        ArgumentCaptor<PaymentRecipient> captor = ArgumentCaptor.forClass(PaymentRecipient.class);
        verify(paymentRecipientRepository).save(captor.capture());
        PaymentRecipient saved = captor.getValue();
        assertThat(saved.getClient()).isSameAs(client);
        assertThat(saved.getName()).isEqualTo("New Recipient");
        assertThat(saved.getAccountNumber()).isEqualTo("987654321098765432");
    }

    @Test
    void createPaymentRecipient_throwsWhenClientNotFound() {
        when(clientRepository.findByEmail(CLIENT_EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentRecipientService.createPaymentRecipient(createRequest, CLIENT_EMAIL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Client not found");
    }

    @Test
    void updatePaymentRecipient_updatesNameAndAccountNumber() {
        when(clientRepository.findByEmail(CLIENT_EMAIL)).thenReturn(Optional.of(client));
        when(paymentRecipientRepository.findByIdAndClient(10L, client)).thenReturn(Optional.of(existingRecipient));
        when(paymentRecipientRepository.save(any(PaymentRecipient.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdatePaymentRecipientRequestDto updateRequest = new UpdatePaymentRecipientRequestDto();
        updateRequest.setName("Updated Name");
        updateRequest.setAccountNumber("111111111111111111");

        PaymentRecipientResponseDto result = paymentRecipientService.updatePaymentRecipient(10L, updateRequest, CLIENT_EMAIL);

        assertThat(result.getName()).isEqualTo("Updated Name");
        assertThat(result.getAccountNumber()).isEqualTo("111111111111111111");
        verify(paymentRecipientRepository).save(existingRecipient);
        assertThat(existingRecipient.getName()).isEqualTo("Updated Name");
        assertThat(existingRecipient.getAccountNumber()).isEqualTo("111111111111111111");
    }

    @Test
    void updatePaymentRecipient_updatesOnlyProvidedFields() {
        when(clientRepository.findByEmail(CLIENT_EMAIL)).thenReturn(Optional.of(client));
        when(paymentRecipientRepository.findByIdAndClient(10L, client)).thenReturn(Optional.of(existingRecipient));
        when(paymentRecipientRepository.save(any(PaymentRecipient.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdatePaymentRecipientRequestDto updateRequest = new UpdatePaymentRecipientRequestDto();
        updateRequest.setName("Only Name Updated");

        paymentRecipientService.updatePaymentRecipient(10L, updateRequest, CLIENT_EMAIL);

        assertThat(existingRecipient.getName()).isEqualTo("Only Name Updated");
        assertThat(existingRecipient.getAccountNumber()).isEqualTo("123456789012345678");
    }

    @Test
    void updatePaymentRecipient_throwsWhenRecipientNotFound() {
        when(clientRepository.findByEmail(CLIENT_EMAIL)).thenReturn(Optional.of(client));
        when(paymentRecipientRepository.findByIdAndClient(99L, client)).thenReturn(Optional.empty());

        UpdatePaymentRecipientRequestDto updateRequest = new UpdatePaymentRecipientRequestDto();
        updateRequest.setName("New Name");

        assertThatThrownBy(() -> paymentRecipientService.updatePaymentRecipient(99L, updateRequest, CLIENT_EMAIL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void deletePaymentRecipient_deletesWhenExists() {
        when(clientRepository.findByEmail(CLIENT_EMAIL)).thenReturn(Optional.of(client));
        when(paymentRecipientRepository.existsByIdAndClient(10L, client)).thenReturn(true);

        paymentRecipientService.deletePaymentRecipient(10L, CLIENT_EMAIL);

        verify(paymentRecipientRepository).deleteById(10L);
    }

    @Test
    void deletePaymentRecipient_throwsWhenRecipientNotFound() {
        when(clientRepository.findByEmail(CLIENT_EMAIL)).thenReturn(Optional.of(client));
        when(paymentRecipientRepository.existsByIdAndClient(99L, client)).thenReturn(false);

        assertThatThrownBy(() -> paymentRecipientService.deletePaymentRecipient(99L, CLIENT_EMAIL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");

        verify(paymentRecipientRepository).existsByIdAndClient(99L, client);
    }
}
