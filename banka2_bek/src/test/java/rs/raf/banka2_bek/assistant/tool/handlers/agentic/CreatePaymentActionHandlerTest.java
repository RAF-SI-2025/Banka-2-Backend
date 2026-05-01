package rs.raf.banka2_bek.assistant.tool.handlers.agentic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.assistant.tool.WriteToolHandler;
import rs.raf.banka2_bek.auth.util.UserContext;
import rs.raf.banka2_bek.auth.util.UserRole;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;
import rs.raf.banka2_bek.payment.dto.CreatePaymentRequestDto;
import rs.raf.banka2_bek.payment.dto.PaymentDirection;
import rs.raf.banka2_bek.payment.dto.PaymentResponseDto;
import rs.raf.banka2_bek.payment.model.PaymentStatus;
import rs.raf.banka2_bek.payment.service.PaymentService;
import rs.raf.banka2_bek.stock.repository.ListingRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit testovi za CreatePaymentActionHandler — pun preview + execute lifecycle.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreatePaymentActionHandlerTest {

    @Mock private PaymentService paymentService;
    @Mock private AccountRepository accountRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private ListingRepository listingRepository;
    @InjectMocks private AgenticHandlerSupport support;

    private CreatePaymentActionHandler handler;
    private UserContext clientUser;

    private void setUp() {
        handler = new CreatePaymentActionHandler(paymentService, support);
        clientUser = new UserContext(1L, UserRole.CLIENT);
    }

    @Test
    @DisplayName("name = create_payment + requiresOtp = true")
    void metadata() {
        setUp();
        assertThat(handler.name()).isEqualTo("create_payment");
        assertThat(handler.requiresOtp()).isTrue();
    }

    @Test
    @DisplayName("buildPreview: vraca summary i polja sa maskiranim brojevima racuna")
    void buildPreviewHappy() {
        setUp();
        when(accountRepository.findByAccountNumber("222000000000123456")).thenReturn(Optional.empty());
        Map<String, Object> args = new HashMap<>();
        args.put("fromAccount", "222000000000123456");
        args.put("toAccount", "444000000000789012");
        args.put("amount", new BigDecimal("5000"));
        args.put("description", "Rodjendan");
        args.put("recipientName", "Stefan");

        WriteToolHandler.PreviewResult preview = handler.buildPreview(args, clientUser);

        assertThat(preview.summary()).contains("5000").contains("Stefan");
        assertThat(preview.displayFields().get("Iznos")).asString().contains("5000");
        // Inter-bank warning posto prefix toAccount-a nije 222
        assertThat(preview.warnings()).hasSize(1);
        assertThat(preview.warnings().get(0)).contains("Inter-bank");
    }

    @Test
    @DisplayName("buildPreview: throws IllegalArgumentException za missing fields")
    void buildPreviewMissingFields() {
        setUp();
        Map<String, Object> args = Map.of("fromAccount", "222000000000123456");
        assertThatThrownBy(() -> handler.buildPreview(args, clientUser))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("executeFinal: poziva PaymentService.createPayment sa propisnim DTO-em")
    void executeFinal() {
        setUp();
        Map<String, Object> args = new HashMap<>();
        args.put("fromAccount", "222000000000123456");
        args.put("toAccount", "222000000000789012");
        args.put("amount", new BigDecimal("5000"));
        args.put("description", "test");

        PaymentResponseDto resp = PaymentResponseDto.builder()
                .id(42L)
                .amount(new BigDecimal("5000"))
                .currency("RSD")
                .toAccount("222000000000789012")
                .status(PaymentStatus.COMPLETED)
                .direction(PaymentDirection.OUTGOING)
                .createdAt(LocalDateTime.now())
                .build();
        when(paymentService.createPayment(any(CreatePaymentRequestDto.class))).thenReturn(resp);

        Map<String, Object> result = handler.executeFinal(args, clientUser, "654321");

        assertThat(result.get("paymentId")).isEqualTo(42L);
        assertThat(result.get("status")).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("buildPreview: amount=0 baca IllegalArgumentException")
    void buildPreviewZeroAmount() {
        setUp();
        Map<String, Object> args = new HashMap<>();
        args.put("fromAccount", "222000000000123456");
        args.put("toAccount", "444000000000789012");
        args.put("amount", BigDecimal.ZERO);
        args.put("description", "test");
        assertThatThrownBy(() -> handler.buildPreview(args, clientUser))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("buildPreview: koristi resolved owner name iz baze ako Account postoji")
    void buildPreviewResolvedRecipient() {
        setUp();
        Account acc = new Account();
        acc.setAccountNumber("222000000000789012");
        // Bez client field-a, fallback na supplied recipientName
        when(accountRepository.findByAccountNumber("222000000000789012")).thenReturn(Optional.of(acc));

        Map<String, Object> args = new HashMap<>();
        args.put("fromAccount", "222000000000123456");
        args.put("toAccount", "222000000000789012");
        args.put("amount", new BigDecimal("100"));
        args.put("description", "test");
        args.put("recipientName", "Marko");

        WriteToolHandler.PreviewResult preview = handler.buildPreview(args, clientUser);
        // intra-bank: toAccount prefix = 222 nase banke, bez warninga
        assertThat(preview.warnings()).isEmpty();
        assertThat(preview.displayFields().get("Primalac")).isEqualTo("Marko");
    }
}
