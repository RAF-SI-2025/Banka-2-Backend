package rs.raf.banka2_bek.payment.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountStatus;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.exchange.ExchangeService;
import rs.raf.banka2_bek.exchange.dto.CalculateExchangeResponseDto;
import rs.raf.banka2_bek.exchange.dto.ExchangeRateDto;
import rs.raf.banka2_bek.payment.dto.CreatePaymentRequestDto;
import rs.raf.banka2_bek.payment.dto.PaymentResponseDto;
import rs.raf.banka2_bek.payment.model.PaymentCode;
import rs.raf.banka2_bek.payment.model.Payment;
import rs.raf.banka2_bek.payment.model.PaymentStatus;
import rs.raf.banka2_bek.payment.repository.PaymentAccountRepository;
import rs.raf.banka2_bek.payment.repository.PaymentRepository;
import rs.raf.banka2_bek.payment.service.implementation.PaymentServiceImpl;
import rs.raf.banka2_bek.notification.service.MailSenderService;
import rs.raf.banka2_bek.interbank.service.BankRoutingService;
import rs.raf.banka2_bek.interbank.service.TransactionExecutorService;
import rs.raf.banka2_bek.interbank.service.InterbankPaymentAsyncService;
import rs.raf.banka2_bek.interbank.repository.InterbankTransactionRepository;
import rs.raf.banka2_bek.notification.service.NotificationService;
import rs.raf.banka2_bek.transaction.dto.TransactionResponseDto;
import rs.raf.banka2_bek.transaction.dto.TransactionType;
import rs.raf.banka2_bek.transaction.service.TransactionService;
import rs.raf.banka2_bek.client.model.Client;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentAccountRepository paymentAccountRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private ClientRepository clientRepository;
    @Mock
    private TransactionService transactionService;
    @Mock
    private PaymentReceiptPdfGenerator paymentReceiptPdfGenerator;
    @Mock
    private ExchangeService exchangeService;
    @Mock
    private MailSenderService mailSenderService;
    @Mock
    private BankRoutingService bankRoutingService;
    @Mock
    private TransactionExecutorService transactionExecutorService;
    @Mock
    private InterbankPaymentAsyncService interbankPaymentAsyncService;
    @Mock
    private InterbankTransactionRepository interbankTransactionRepository;
    @Mock
    private NotificationService notificationService;

    private PaymentServiceImpl paymentService;

    private CreatePaymentRequestDto request;
    private Client client;
    private Currency eur;
    private Account fromAccount;
    private Account toAccount;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentServiceImpl(
                paymentRepository, paymentAccountRepository, accountRepository,
                clientRepository, transactionService, paymentReceiptPdfGenerator,
                exchangeService, mailSenderService,
                bankRoutingService, transactionExecutorService,
                interbankPaymentAsyncService, interbankTransactionRepository,
                "22200022", notificationService);

        lenient().when(bankRoutingService.isLocalAccount(any())).thenReturn(true);

        request = new CreatePaymentRequestDto();
        request.setFromAccount("111111111111111111");
        request.setToAccount("222222222222222222");
        request.setAmount(new BigDecimal("100.00"));
        request.setPaymentCode(PaymentCode.CODE_289);
        request.setReferenceNumber("REF-1");
        request.setDescription("Test payment");

        client = new Client();
        client.setId(10L);
        client.setEmail("client@test.com");
        client.setActive(true);
//        client.setRole("CLIENT");

        eur = Currency.builder().id(1L).code("EUR").name("Euro").symbol("E").country("EU").active(true).build();

        fromAccount = baseAccount(1L, request.getFromAccount(), client, eur, new BigDecimal("1000.00"));
        toAccount = baseAccount(2L, request.getToAccount(), null, eur, new BigDecimal("500.00"));

        authenticateAs(client.getEmail());
        lenient().when(clientRepository.findByEmail(client.getEmail())).thenReturn(Optional.of(client));
        lenient().when(exchangeService.getAllRates()).thenReturn(defaultRates());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createPayment_success_updatesBalances_savesPayment_andRecordsTransactions() {
        when(paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
        when(paymentAccountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));

        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(99L);
            p.setCreatedAt(LocalDateTime.now());
            return p;
        });

        PaymentResponseDto response = paymentService.createPayment(request);

        assertThat(response.getId()).isEqualTo(99L);
        assertThat(response.getStatus().name()).isEqualTo("COMPLETED");
        assertThat(response.getAmount()).isEqualByComparingTo("100.00");

        assertThat(fromAccount.getBalance()).isEqualByComparingTo("900.00");
        assertThat(fromAccount.getAvailableBalance()).isEqualByComparingTo("900.00");
        assertThat(fromAccount.getDailySpending()).isEqualByComparingTo("100.00");
        assertThat(fromAccount.getMonthlySpending()).isEqualByComparingTo("100.00");

        assertThat(toAccount.getBalance()).isEqualByComparingTo("600.00");
        assertThat(toAccount.getAvailableBalance()).isEqualByComparingTo("600.00");

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).saveAndFlush(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getOrderNumber()).startsWith("PAY-");
        assertThat(paymentCaptor.getValue().getFee()).isEqualByComparingTo("0");

        verify(transactionService).recordPaymentSettlement(
                any(Payment.class),
                eq(toAccount),
                eq(client),
                argThat(credited -> credited.compareTo(new BigDecimal("100.00")) == 0)
        );
    }

    @Test
    void createPayment_success_crossCurrency_appliesFeeAndFxRate() {
        Currency usd = Currency.builder()
                .id(2L)
                .code("USD")
                .name("US Dollar")
                .symbol("$")
                .country("US")
                .active(true)
                .build();
        toAccount.setCurrency(usd);

        Account bankEurAccount = baseAccount(100L, "BANK-EUR", null, eur, new BigDecimal("1000000"));
        Account bankUsdAccount = baseAccount(101L, "BANK-USD", null, usd, new BigDecimal("1000000"));

        when(paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount()))
                .thenReturn(Optional.of(fromAccount));
        when(paymentAccountRepository.findForUpdateByAccountNumber(request.getToAccount()))
                .thenReturn(Optional.of(toAccount));
        when(accountRepository.findBankAccountForUpdateByCurrency("22200022", "EUR"))
                .thenReturn(Optional.of(bankEurAccount));
        when(accountRepository.findBankAccountForUpdateByCurrency("22200022", "USD"))
                .thenReturn(Optional.of(bankUsdAccount));
        when(exchangeService.calculateCross(100.0, "EUR", "USD"))
                .thenReturn(new CalculateExchangeResponseDto(108.01843318, 1.0801843318, "EUR", "USD"));

        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(200L);
            p.setCreatedAt(LocalDateTime.now());
            return p;
        });

        PaymentResponseDto response = paymentService.createPayment(request);

        assertThat(response.getId()).isEqualTo(200L);
        assertThat(response.getStatus().name()).isEqualTo("COMPLETED");

        // Fee = 0.5% of 100.00 = 0.50000
        assertThat(fromAccount.getBalance()).isEqualByComparingTo("899.50000");
        assertThat(fromAccount.getAvailableBalance()).isEqualByComparingTo("899.50000");

        // credited amount = 108.01843318
        assertThat(toAccount.getBalance()).isEqualByComparingTo("608.01843318");
        assertThat(toAccount.getAvailableBalance()).isEqualByComparingTo("608.01843318");

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).saveAndFlush(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getFee()).isEqualByComparingTo("0.50000");

        verify(transactionService).recordPaymentSettlement(
                any(Payment.class),
                eq(toAccount),
                eq(client),
                argThat(credited -> credited.compareTo(new BigDecimal("108.01843318")) == 0)
        );
    }


    @Test
    void createPayment_retriesOnUniqueViolation_thenSucceeds() {
        when(paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
        when(paymentAccountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));

        AtomicInteger attempts = new AtomicInteger(0);
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> {
            if (attempts.getAndIncrement() == 0) {
                throw uniqueViolation();
            }
            Payment p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });

        PaymentResponseDto response = paymentService.createPayment(request);

        assertThat(response.getId()).isEqualTo(1L);
        verify(paymentRepository, times(2)).saveAndFlush(any(Payment.class));
    }

    @Test
    void createPayment_throwsWhenAllUniqueRetriesFail() {
        when(paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
        when(paymentAccountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenThrow(uniqueViolation());

        assertThatThrownBy(() -> paymentService.createPayment(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Generisanje broja placanja nije uspelo");
    }

    @Test
    void createPayment_propagatesDataIntegrityViolationWhenCauseMessageIsNull() {
        when(paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
        when(paymentAccountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));

        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "constraint",
                new RuntimeException()
        );
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenThrow(ex);

        assertThatThrownBy(() -> paymentService.createPayment(request))
                .isSameAs(ex);
    }

    @Test
    void createPayment_propagatesNonUniqueDataIntegrityViolation() {
        when(paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
        when(paymentAccountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));

        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "constraint",
                new RuntimeException("Cannot add or update child row")
        );
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenThrow(ex);

        assertThatThrownBy(() -> paymentService.createPayment(request))
                .isSameAs(ex);
    }

    @Test
    void createPayment_throwsWhenFromAccountMissing() {
        when(paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.createPayment(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Racun posiljaoca ne postoji");
    }

    @Test
    void createPayment_throwsWhenToAccountMissing() {
        when(paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
        when(paymentAccountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.createPayment(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Racun primaoca ne postoji");
    }

    @Test
    void createPayment_throwsWhenFromAccountInactive() {
        fromAccount.setStatus(AccountStatus.INACTIVE);
        when(paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));

        assertThatThrownBy(() -> paymentService.createPayment(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Racun posiljaoca nije aktivan");
    }

    @Test
    void createPayment_throwsWhenToAccountInactive() {
        toAccount.setStatus(AccountStatus.INACTIVE);
        when(paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
        when(paymentAccountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));

        assertThatThrownBy(() -> paymentService.createPayment(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Racun primaoca nije aktivan");
    }

    @Test
    void createPayment_throwsWhenSameAccount() {
        toAccount.setId(fromAccount.getId());
        when(paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
        when(paymentAccountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));

        assertThatThrownBy(() -> paymentService.createPayment(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Racuni moraju biti razliciti");
    }

    @Test
    void createPayment_throwsWhenFromAccountNotOwnedByAuthenticatedUser() {
        Client other = new Client();
        other.setId(777L);
        fromAccount.setClient(other);

        when(paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));

        assertThatThrownBy(() -> paymentService.createPayment(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ne pripada klijentu");
    }

    @Test
    void createPayment_throwsWhenDailyLimitExceeded() {
        fromAccount.setDailyLimit(new BigDecimal("50.00"));

        when(paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
        when(paymentAccountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));

        assertThatThrownBy(() -> paymentService.createPayment(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Prekoracen dnevni limit");
    }

    @Test
    void createPayment_succeedsWhenDailyLimitMissing() {
        fromAccount.setDailyLimit(null);

        when(paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
        when(paymentAccountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(1L);
            p.setCreatedAt(java.time.LocalDateTime.now());
            return p;
        });

        PaymentResponseDto response = paymentService.createPayment(request);
        assertThat(response.getId()).isEqualTo(1L);
    }

    @Test
    void createPayment_throwsWhenMonthlyLimitExceeded() {
        fromAccount.setMonthlyLimit(new BigDecimal("70.00"));

        when(paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
        when(paymentAccountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));

        assertThatThrownBy(() -> paymentService.createPayment(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Prekoracen mesecni limit");
    }

    @Test
    void createPayment_succeedsWhenMonthlyLimitMissing() {
        fromAccount.setMonthlyLimit(null);

        when(paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
        when(paymentAccountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(1L);
            p.setCreatedAt(java.time.LocalDateTime.now());
            return p;
        });

        PaymentResponseDto response = paymentService.createPayment(request);
        assertThat(response.getId()).isEqualTo(1L);
    }

    @Test
    void createPayment_throwsWhenInsufficientFunds() {
        fromAccount.setAvailableBalance(new BigDecimal("20.00"));

        when(paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
        when(paymentAccountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));

        assertThatThrownBy(() -> paymentService.createPayment(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nedovoljno sredstava");
    }

    @Test
    void createPayment_throwsWhenAvailableBalanceMissing() {
        fromAccount.setAvailableBalance(null);

        when(paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
        when(paymentAccountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));

        assertThatThrownBy(() -> paymentService.createPayment(request))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void createPayment_throwsWhenAuthenticationMissing() {
        SecurityContextHolder.clearContext();

        when(paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));

        assertThatThrownBy(() -> paymentService.createPayment(request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getPaymentReceipt_returnsPdfForOwnedTransaction() {
        TransactionResponseDto transaction = TransactionResponseDto.builder()
                .id(55L)
                .type(TransactionType.PAYMENT)
                .accountNumber(fromAccount.getAccountNumber())
                .currencyCode(eur.getCode())
                .debit(new BigDecimal("50.00"))
                .build();

        byte[] expected = "%PDF-test".getBytes();

        when(transactionService.getReceiptTransaction(55L, client.getId())).thenReturn(transaction);
        when(paymentReceiptPdfGenerator.generate(transaction)).thenReturn(expected);

        byte[] result = paymentService.getPaymentReceipt(55L);

        assertThat(result).isEqualTo(expected);
        verify(paymentReceiptPdfGenerator).generate(transaction);
    }

    @Test
    void getPaymentReceipt_throwsWhenTransactionNotOwnedOrMissing() {
        when(transactionService.getReceiptTransaction(55L, client.getId()))
                .thenThrow(new IllegalArgumentException("Transaction with ID 55 not found for authenticated client."));

        assertThatThrownBy(() -> paymentService.getPaymentReceipt(55L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Transaction with ID 55 not found");
    }

//    @Test
//    void createPayment_throwsWhenPrincipalIsNotUserDetails() {
//        SecurityContextHolder.getContext().setAuthentication(
//                new UsernamePasswordAuthenticationToken(
//                        "rawPrincipal",
//                        null,
//                        List.of(new SimpleGrantedAuthority("ROLE_CLIENT"))
//                )
//        );
//
//        when(accountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
//        when(accountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));
//
//        assertThatThrownBy(() -> paymentService.createPayment(request))
//                .isInstanceOf(IllegalArgumentException.class)
//                .hasMessageContaining("Authenticated user is required");
//    }
//
//    @Test
//    void createPayment_throwsWhenAuthenticatedUserNotFoundInDb() {
//        authenticateAs("missing@test.com");
//        when(userRepository.findByEmail("missing@test.com")).thenReturn(Optional.empty());
//
//        when(accountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
//        when(accountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));
//
//        assertThatThrownBy(() -> paymentService.createPayment(request))
//                .isInstanceOf(IllegalArgumentException.class)
//                .hasMessageContaining("Authenticated client does not exist");
//    }


    // ========== validatePayment ==========

    @Test
    void validatePayment_throwsWhenRequestNull() {
        assertThatThrownBy(() -> paymentService.validatePayment(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Podaci o placanju nedostaju");
    }

    @Test
    void validatePayment_throwsWhenFromAccountBlank() {
        request.setFromAccount("");
        assertThatThrownBy(() -> paymentService.validatePayment(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Racun posiljaoca nedostaje");
    }

    @Test
    void validatePayment_throwsWhenToAccountNull() {
        request.setToAccount(null);
        assertThatThrownBy(() -> paymentService.validatePayment(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Racun primaoca nedostaje");
    }

    @Test
    void validatePayment_throwsWhenAmountZero() {
        request.setAmount(BigDecimal.ZERO);
        assertThatThrownBy(() -> paymentService.validatePayment(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Iznos mora biti veci od 0");
    }

    @Test
    void validatePayment_throwsWhenFromAccountNotFound() {
        when(accountRepository.findByAccountNumber(request.getFromAccount())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> paymentService.validatePayment(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Racun posiljaoca ne postoji");
    }

    @Test
    void validatePayment_throwsWhenToAccountNotFound() {
        when(accountRepository.findByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByAccountNumber(request.getToAccount())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> paymentService.validatePayment(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Racun primaoca ne postoji");
    }

    @Test
    void validatePayment_throwsWhenFromAccountInactive() {
        fromAccount.setStatus(AccountStatus.INACTIVE);
        when(accountRepository.findByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));
        assertThatThrownBy(() -> paymentService.validatePayment(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Racun posiljaoca nije aktivan");
    }

    @Test
    void validatePayment_throwsWhenToAccountInactive() {
        toAccount.setStatus(AccountStatus.INACTIVE);
        when(accountRepository.findByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));
        assertThatThrownBy(() -> paymentService.validatePayment(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Racun primaoca nije aktivan");
    }

    @Test
    void validatePayment_throwsWhenSameAccount() {
        when(accountRepository.findByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByAccountNumber(request.getToAccount())).thenReturn(Optional.of(fromAccount));
        assertThatThrownBy(() -> paymentService.validatePayment(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Racuni moraju biti razliciti");
    }

    @Test
    void validatePayment_throwsWhenFromAccountNotOwnedByClient() {
        Client other = new Client();
        other.setId(777L);
        fromAccount.setClient(other);
        when(accountRepository.findByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));
        assertThatThrownBy(() -> paymentService.validatePayment(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Racun ne pripada klijentu");
    }

    @Test
    void validatePayment_throwsWhenInsufficientFunds() {
        fromAccount.setAvailableBalance(new BigDecimal("5.00"));
        when(accountRepository.findByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));
        assertThatThrownBy(() -> paymentService.validatePayment(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nedovoljno sredstava");
    }

    @Test
    void validatePayment_throwsWhenDailyLimitExceeded() {
        fromAccount.setDailyLimit(new BigDecimal("50.00"));
        when(accountRepository.findByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));
        assertThatThrownBy(() -> paymentService.validatePayment(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Prekoracen dnevni limit");
    }

    @Test
    void validatePayment_throwsWhenMonthlyLimitExceeded() {
        fromAccount.setMonthlyLimit(new BigDecimal("70.00"));
        when(accountRepository.findByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));
        assertThatThrownBy(() -> paymentService.validatePayment(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Prekoracen mesecni limit");
    }

    @Test
    void validatePayment_succeedsForValidRequest() {
        when(accountRepository.findByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));
        paymentService.validatePayment(request); // no exception
    }

    // ========== recordAbortedPayment ==========

    @Test
    void recordAbortedPayment_returnsNullForNullRequest() {
        assertThat(paymentService.recordAbortedPayment(null, "otp")).isNull();
    }

    @Test
    void recordAbortedPayment_returnsNullWhenFromAccountNull() {
        request.setFromAccount(null);
        assertThat(paymentService.recordAbortedPayment(request, "otp")).isNull();
    }

    @Test
    void recordAbortedPayment_returnsNullWhenAmountNull() {
        request.setAmount(null);
        assertThat(paymentService.recordAbortedPayment(request, "otp")).isNull();
    }

    @Test
    void recordAbortedPayment_returnsNullWhenFromAccountNotFound() {
        when(accountRepository.findByAccountNumber(request.getFromAccount())).thenReturn(Optional.empty());
        assertThat(paymentService.recordAbortedPayment(request, "otp")).isNull();
    }

    @Test
    void recordAbortedPayment_returnsNullWhenNotAuthenticated() {
        SecurityContextHolder.clearContext();
        when(accountRepository.findByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
        assertThat(paymentService.recordAbortedPayment(request, "otp")).isNull();
    }

    @Test
    void recordAbortedPayment_returnsNullWhenWrongClient() {
        Client other = new Client();
        other.setId(999L);
        fromAccount.setClient(other);
        when(accountRepository.findByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
        assertThat(paymentService.recordAbortedPayment(request, "otp")).isNull();
    }

    @Test
    void recordAbortedPayment_savesAbortedPaymentWithExplicitReason() {
        when(accountRepository.findByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(77L);
            return p;
        });

        Long id = paymentService.recordAbortedPayment(request, "OTP max retries exceeded");

        assertThat(id).isEqualTo(77L);
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.ABORTED);
        assertThat(captor.getValue().getPurpose()).isEqualTo("OTP max retries exceeded");
    }

    @Test
    void recordAbortedPayment_usesDefaultPurposeWhenReasonNull() {
        when(accountRepository.findByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(78L);
            return p;
        });

        Long id = paymentService.recordAbortedPayment(request, null);

        assertThat(id).isEqualTo(78L);
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getPurpose()).isEqualTo("OTP otkazano");
    }

    @Test
    void recordAbortedPayment_setsNullPaymentCodeWhenMissing() {
        request.setPaymentCode(null);
        when(accountRepository.findByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(79L);
            return p;
        });

        Long id = paymentService.recordAbortedPayment(request, "expired");

        assertThat(id).isEqualTo(79L);
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getPaymentCode()).isNull();
    }

    private void authenticateAs(String email) {
        org.springframework.security.core.userdetails.User principal =
                new org.springframework.security.core.userdetails.User(
                        email,
                        "x",
                        List.of(new SimpleGrantedAuthority("ROLE_CLIENT"))
                );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    private Account baseAccount(Long id, String accountNumber, Client owner, Currency currency, BigDecimal balance) {
        return Account.builder()
                .id(id)
                .accountNumber(accountNumber)
                .currency(currency)
                .status(AccountStatus.ACTIVE)
                .client(owner)
                .balance(balance)
                .availableBalance(balance)
                .dailyLimit(new BigDecimal("5000.00"))
                .monthlyLimit(new BigDecimal("20000.00"))
                .dailySpending(BigDecimal.ZERO)
                .monthlySpending(BigDecimal.ZERO)
                .build();
    }

    private DataIntegrityViolationException uniqueViolation() {
        return new DataIntegrityViolationException(
                "duplicate key",
                new RuntimeException("Duplicate entry for key 'uk_payments_order_number'")
        );
    }

    private List<ExchangeRateDto> defaultRates() {
        return List.of(
                new ExchangeRateDto("RSD", 1.0),
                new ExchangeRateDto("EUR", 0.008532423208191127),
                new ExchangeRateDto("USD", 0.009216589861751152),
                new ExchangeRateDto("CHF", 0.008143322475570033),
                new ExchangeRateDto("GBP", 0.00727802037845706),
                new ExchangeRateDto("JPY", 1.36986301369863),
                new ExchangeRateDto("CAD", 0.012484394506866417),
                new ExchangeRateDto("AUD", 0.013966480446927373)
        );
    }

}
