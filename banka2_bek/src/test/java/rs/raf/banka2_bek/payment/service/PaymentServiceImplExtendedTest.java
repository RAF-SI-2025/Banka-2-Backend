package rs.raf.banka2_bek.payment.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountStatus;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.exchange.ExchangeService;
import rs.raf.banka2_bek.exchange.dto.CalculateExchangeResponseDto;
import rs.raf.banka2_bek.exchange.dto.ExchangeRateDto;
import rs.raf.banka2_bek.payment.dto.CreatePaymentRequestDto;
import rs.raf.banka2_bek.payment.dto.PaymentDirection;
import rs.raf.banka2_bek.payment.dto.PaymentListItemDto;
import rs.raf.banka2_bek.payment.dto.PaymentResponseDto;
import rs.raf.banka2_bek.payment.model.Payment;
import rs.raf.banka2_bek.payment.model.PaymentCode;
import rs.raf.banka2_bek.payment.model.PaymentStatus;
import rs.raf.banka2_bek.payment.repository.PaymentAccountRepository;
import rs.raf.banka2_bek.payment.repository.PaymentRepository;
import rs.raf.banka2_bek.payment.service.implementation.PaymentServiceImpl;
import rs.raf.banka2_bek.notification.service.MailNotificationService;
import rs.raf.banka2_bek.transaction.service.TransactionService;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Extended tests for PaymentServiceImpl - covers additional edge cases.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceImplExtendedTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentAccountRepository paymentAccountRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private TransactionService transactionService;
    @Mock private PaymentReceiptPdfGenerator paymentReceiptPdfGenerator;
    @Mock private ExchangeService exchangeService;
    @Mock private MailNotificationService mailNotificationService;

    private PaymentServiceImpl paymentService;

    private Client client;
    private Currency eur;
    private Currency usd;
    private Account fromAccount;
    private Account toAccount;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentServiceImpl(
                paymentRepository, paymentAccountRepository, accountRepository,
                clientRepository, transactionService, paymentReceiptPdfGenerator,
                exchangeService, mailNotificationService, "22200022");

        client = new Client();
        client.setId(10L);
        client.setEmail("client@test.com");
        client.setActive(true);

        eur = Currency.builder().id(1L).code("EUR").name("Euro").symbol("E").country("EU").active(true).build();
        usd = Currency.builder().id(2L).code("USD").name("US Dollar").symbol("$").country("US").active(true).build();

        fromAccount = baseAccount(1L, "111111111111111111", client, eur, new BigDecimal("5000.00"));
        toAccount = baseAccount(2L, "222222222222222222", null, eur, new BigDecimal("2000.00"));

        authenticateAs(client.getEmail());
        lenient().when(clientRepository.findByEmail(client.getEmail())).thenReturn(Optional.of(client));
        lenient().when(exchangeService.getAllRates()).thenReturn(defaultRates());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ===== Email failure does not block payment =====

    @Nested
    @DisplayName("Email failure resilience")
    class EmailFailureResilience {

        @Test
        @DisplayName("payment succeeds even when email notification fails")
        void emailFailureDoesNotBlock() {
            CreatePaymentRequestDto request = buildRequest("100.00");

            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));
            when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                p.setId(1L);
                p.setCreatedAt(LocalDateTime.now());
                return p;
            });
            doThrow(new RuntimeException("SMTP error")).when(mailNotificationService)
                    .sendPaymentConfirmationMail(anyString(), any(), anyString(), anyString(), anyString(), any(), anyString());

            PaymentResponseDto response = paymentService.createPayment(request);

            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getStatus().name()).isEqualTo("COMPLETED");
        }
    }

    // ===== Cross-currency bank account checks =====

    @Nested
    @DisplayName("Cross-currency bank account checks")
    class CrossCurrencyBankChecks {

        @Test
        @DisplayName("throws when bank has insufficient target currency")
        void bankInsufficientTargetCurrency() {
            toAccount.setCurrency(usd);

            Account bankUsd = baseAccount(101L, "BANK-USD", null, usd, new BigDecimal("1")); // very low

            CreatePaymentRequestDto request = buildRequest("100.00");

            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));
            when(accountRepository.findBankAccountForUpdateByCurrency("22200022", "USD")).thenReturn(Optional.of(bankUsd));
            when(exchangeService.calculateCross(100.0, "EUR", "USD"))
                    .thenReturn(new CalculateExchangeResponseDto(108.0, 1.08, "EUR", "USD"));

            assertThatThrownBy(() -> paymentService.createPayment(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("nema dovoljno");
        }

        @Test
        @DisplayName("throws when bank target currency account not found")
        void bankTargetAccountNotFound() {
            toAccount.setCurrency(usd);

            CreatePaymentRequestDto request = buildRequest("100.00");

            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));
            when(accountRepository.findBankAccountForUpdateByCurrency("22200022", "USD")).thenReturn(Optional.empty());
            when(exchangeService.calculateCross(100.0, "EUR", "USD"))
                    .thenReturn(new CalculateExchangeResponseDto(108.0, 1.08, "EUR", "USD"));

            assertThatThrownBy(() -> paymentService.createPayment(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("nema racun");
        }

        @Test
        @DisplayName("throws when bank source currency account not found in cross-currency")
        void bankSourceAccountNotFound() {
            toAccount.setCurrency(usd);

            Account bankUsd = baseAccount(101L, "BANK-USD", null, usd, new BigDecimal("1000000"));

            CreatePaymentRequestDto request = buildRequest("100.00");

            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));
            when(accountRepository.findBankAccountForUpdateByCurrency("22200022", "USD")).thenReturn(Optional.of(bankUsd));
            when(accountRepository.findBankAccountForUpdateByCurrency("22200022", "EUR")).thenReturn(Optional.empty());
            when(exchangeService.calculateCross(100.0, "EUR", "USD"))
                    .thenReturn(new CalculateExchangeResponseDto(108.0, 1.08, "EUR", "USD"));

            assertThatThrownBy(() -> paymentService.createPayment(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("nema racun");
        }
    }

    // ===== Cross-currency fee and balance verification =====

    @Nested
    @DisplayName("Cross-currency fee details")
    class CrossCurrencyFeeDetails {

        @Test
        @DisplayName("fee is exactly 0.5% of amount (not of converted amount)")
        void feeIsOnSourceAmount() {
            toAccount.setCurrency(usd);

            Account bankEur = baseAccount(100L, "BANK-EUR", null, eur, new BigDecimal("1000000"));
            Account bankUsd = baseAccount(101L, "BANK-USD", null, usd, new BigDecimal("1000000"));

            CreatePaymentRequestDto request = buildRequest("200.00");

            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));
            when(accountRepository.findBankAccountForUpdateByCurrency("22200022", "EUR")).thenReturn(Optional.of(bankEur));
            when(accountRepository.findBankAccountForUpdateByCurrency("22200022", "USD")).thenReturn(Optional.of(bankUsd));
            when(exchangeService.calculateCross(200.0, "EUR", "USD"))
                    .thenReturn(new CalculateExchangeResponseDto(216.0, 1.08, "EUR", "USD"));
            when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                p.setId(1L);
                p.setCreatedAt(LocalDateTime.now());
                return p;
            });

            PaymentResponseDto response = paymentService.createPayment(request);

            // Fee should be 0.5% of 200 = 1.00
            ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository).saveAndFlush(captor.capture());
            assertThat(captor.getValue().getFee()).isEqualByComparingTo("1.00");

            // Client pays 200 + 1 = 201
            assertThat(fromAccount.getBalance()).isEqualByComparingTo("4799.00");
        }
    }

    // ===== Same-currency no fee =====

    @Nested
    @DisplayName("Same-currency payment")
    class SameCurrencyPayment {

        @Test
        @DisplayName("no fee charged for same-currency payment")
        void noFeeForSameCurrency() {
            CreatePaymentRequestDto request = buildRequest("500.00");

            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));
            when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                p.setId(1L);
                p.setCreatedAt(LocalDateTime.now());
                return p;
            });

            PaymentResponseDto response = paymentService.createPayment(request);

            // Balance should be exactly 5000 - 500 = 4500 (no fee)
            assertThat(fromAccount.getBalance()).isEqualByComparingTo("4500.00");
            assertThat(fromAccount.getAvailableBalance()).isEqualByComparingTo("4500.00");
            assertThat(toAccount.getBalance()).isEqualByComparingTo("2500.00");
        }
    }

    // ===== Spending tracking =====

    @Nested
    @DisplayName("Spending tracking")
    class SpendingTracking {

        @Test
        @DisplayName("daily and monthly spending updated after payment")
        void spendingUpdated() {
            fromAccount.setDailySpending(new BigDecimal("100.00"));
            fromAccount.setMonthlySpending(new BigDecimal("1000.00"));

            CreatePaymentRequestDto request = buildRequest("250.00");

            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));
            when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                p.setId(1L);
                p.setCreatedAt(LocalDateTime.now());
                return p;
            });

            paymentService.createPayment(request);

            assertThat(fromAccount.getDailySpending()).isEqualByComparingTo("350.00");
            assertThat(fromAccount.getMonthlySpending()).isEqualByComparingTo("1250.00");
        }

        @Test
        @DisplayName("daily limit check considers existing spending")
        void dailyLimitWithExistingSpending() {
            fromAccount.setDailyLimit(new BigDecimal("500.00"));
            fromAccount.setDailySpending(new BigDecimal("450.00")); // only 50 left

            CreatePaymentRequestDto request = buildRequest("100.00"); // exceeds remaining 50

            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));

            assertThatThrownBy(() -> paymentService.createPayment(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("dnevni limit");
        }

        @Test
        @DisplayName("monthly limit check considers existing spending")
        void monthlyLimitWithExistingSpending() {
            fromAccount.setMonthlyLimit(new BigDecimal("2000.00"));
            fromAccount.setMonthlySpending(new BigDecimal("1950.00")); // only 50 left

            CreatePaymentRequestDto request = buildRequest("100.00"); // exceeds remaining 50

            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));

            assertThatThrownBy(() -> paymentService.createPayment(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("mesecni limit");
        }
    }

    // ===== Limit edge cases: zero limit =====

    @Nested
    @DisplayName("Limit edge cases")
    class LimitEdgeCases {

        @Test
        @DisplayName("daily limit of zero is treated as no limit")
        void dailyLimitZero() {
            fromAccount.setDailyLimit(BigDecimal.ZERO);

            CreatePaymentRequestDto request = buildRequest("100.00");

            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));
            when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                p.setId(1L);
                p.setCreatedAt(LocalDateTime.now());
                return p;
            });

            paymentService.createPayment(request);

            assertThat(fromAccount.getBalance()).isEqualByComparingTo("4900.00");
        }

        @Test
        @DisplayName("monthly limit of zero is treated as no limit")
        void monthlyLimitZero() {
            fromAccount.setMonthlyLimit(BigDecimal.ZERO);

            CreatePaymentRequestDto request = buildRequest("100.00");

            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));
            when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                p.setId(1L);
                p.setCreatedAt(LocalDateTime.now());
                return p;
            });

            paymentService.createPayment(request);

            assertThat(fromAccount.getBalance()).isEqualByComparingTo("4900.00");
        }
    }

    // ===== Insufficient funds with fee consideration =====

    @Nested
    @DisplayName("Insufficient funds with cross-currency fee")
    class InsufficientFundsWithFee {

        @Test
        @DisplayName("fails when balance covers amount but not amount+fee in cross-currency")
        void amountOkButFeeExceedsBalance() {
            toAccount.setCurrency(usd);
            fromAccount.setAvailableBalance(new BigDecimal("100.00"));
            fromAccount.setBalance(new BigDecimal("100.00"));

            CreatePaymentRequestDto request = buildRequest("100.00");
            // fee = 0.5% of 100 = 0.50, total = 100.50 > 100.00

            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));

            assertThatThrownBy(() -> paymentService.createPayment(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Nedovoljno sredstava");
        }
    }

    // ===== Payment order number generation =====

    @Nested
    @DisplayName("Order number generation")
    class OrderNumber {

        @Test
        @DisplayName("order number starts with PAY- prefix")
        void orderNumberPrefix() {
            CreatePaymentRequestDto request = buildRequest("50.00");

            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));
            when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                p.setId(1L);
                p.setCreatedAt(LocalDateTime.now());
                return p;
            });

            paymentService.createPayment(request);

            ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository).saveAndFlush(captor.capture());
            assertThat(captor.getValue().getOrderNumber()).startsWith("PAY-");
            assertThat(captor.getValue().getOrderNumber()).hasSize(20); // "PAY-" + 16 chars
        }
    }

    // ===== Payment status transitions =====

    @Nested
    @DisplayName("Payment status")
    class PaymentStatusTests {

        @Test
        @DisplayName("payment is created with COMPLETED status")
        void completedStatus() {
            CreatePaymentRequestDto request = buildRequest("100.00");

            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));
            when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                p.setId(1L);
                p.setCreatedAt(LocalDateTime.now());
                return p;
            });

            PaymentResponseDto response = paymentService.createPayment(request);
            assertThat(response.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        }
    }

    // ===== getPaymentById =====

    @Nested
    @DisplayName("getPaymentById")
    class GetPaymentById {

        @Test
        @DisplayName("throws when payment not found")
        void notFound() {
            when(paymentRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.getPaymentById(999L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("nije pronadjeno");
        }

        @Test
        @DisplayName("returns payment when found")
        void found() {
            Payment payment = Payment.builder()
                    .id(1L).orderNumber("PAY-1234567890123456")
                    .fromAccount(fromAccount).toAccountNumber("222222222222222222")
                    .amount(new BigDecimal("100.00")).fee(BigDecimal.ZERO)
                    .currency(eur).status(PaymentStatus.COMPLETED)
                    .createdBy(client).createdAt(LocalDateTime.now())
                    .build();

            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

            PaymentResponseDto response = paymentService.getPaymentById(1L);

            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getOrderNumber()).isEqualTo("PAY-1234567890123456");
            assertThat(response.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        }
    }

    // ===== Transaction recording =====

    @Nested
    @DisplayName("Transaction recording")
    class TransactionRecording {

        @Test
        @DisplayName("records payment settlement transaction on success")
        void recordsTransaction() {
            CreatePaymentRequestDto request = buildRequest("100.00");

            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));
            when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                p.setId(1L);
                p.setCreatedAt(LocalDateTime.now());
                return p;
            });

            paymentService.createPayment(request);

            verify(transactionService).recordPaymentSettlement(
                    any(Payment.class), eq(toAccount), eq(client),
                    argThat(amount -> amount.compareTo(new BigDecimal("100.00")) == 0));
        }
    }

    // ===== Helpers =====

    private void authenticateAs(String email) {
        org.springframework.security.core.userdetails.User principal =
                new org.springframework.security.core.userdetails.User(
                        email, "x",
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

    private CreatePaymentRequestDto buildRequest(String amount) {
        CreatePaymentRequestDto request = new CreatePaymentRequestDto();
        request.setFromAccount("111111111111111111");
        request.setToAccount("222222222222222222");
        request.setAmount(new BigDecimal(amount));
        request.setPaymentCode(PaymentCode.CODE_289);
        request.setReferenceNumber("REF-1");
        request.setDescription("Test payment");
        return request;
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

    // ===== Cross-currency full success path =====

    @Nested
    @DisplayName("Cross-currency full success path")
    class CrossCurrencySuccess {

        @Test
        @DisplayName("successful cross-currency payment updates all bank accounts and spending")
        void fullCrossCurrencyFlow() {
            toAccount.setCurrency(usd);

            Account bankEur = baseAccount(100L, "BANK-EUR", null, eur, new BigDecimal("1000000"));
            Account bankUsd = baseAccount(101L, "BANK-USD", null, usd, new BigDecimal("1000000"));

            fromAccount.setDailySpending(new BigDecimal("100.00"));
            fromAccount.setMonthlySpending(new BigDecimal("500.00"));

            CreatePaymentRequestDto request = buildRequest("1000.00");

            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));
            when(accountRepository.findBankAccountForUpdateByCurrency("22200022", "USD")).thenReturn(Optional.of(bankUsd));
            when(accountRepository.findBankAccountForUpdateByCurrency("22200022", "EUR")).thenReturn(Optional.of(bankEur));
            when(exchangeService.calculateCross(1000.0, "EUR", "USD"))
                    .thenReturn(new CalculateExchangeResponseDto(1080.0, 1.08, "EUR", "USD"));
            when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                p.setId(1L);
                p.setCreatedAt(LocalDateTime.now());
                return p;
            });

            PaymentResponseDto response = paymentService.createPayment(request);

            assertThat(response.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            // fee = 0.5% of 1000 = 5.00, total from client = 1005.00
            assertThat(fromAccount.getBalance()).isEqualByComparingTo("3995.00");
            assertThat(fromAccount.getDailySpending()).isEqualByComparingTo("1100.00");
            assertThat(fromAccount.getMonthlySpending()).isEqualByComparingTo("1500.00");
            // Bank EUR account gets amount+fee from client
            assertThat(bankEur.getBalance()).isEqualByComparingTo("1001005.00");
            // Bank USD account pays converted amount to recipient
            assertThat(bankUsd.getBalance()).isEqualByComparingTo("998920.00");
            // Recipient gets converted amount
            assertThat(toAccount.getBalance()).isEqualByComparingTo("3080.00");
        }
    }

    // ===== Validation edge cases =====

    @Nested
    @DisplayName("Validation edge cases")
    class ValidationEdgeCases {

        @Test
        @DisplayName("throws when from account not found")
        void fromAccountNotFound() {
            CreatePaymentRequestDto request = buildRequest("100.00");
            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.createPayment(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("posiljaoca ne postoji");
        }

        @Test
        @DisplayName("throws when to account not found")
        void toAccountNotFound() {
            CreatePaymentRequestDto request = buildRequest("100.00");
            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.createPayment(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("primaoca ne postoji");
        }

        @Test
        @DisplayName("throws when from account is not active")
        void fromAccountNotActive() {
            fromAccount.setStatus(AccountStatus.BLOCKED);
            CreatePaymentRequestDto request = buildRequest("100.00");
            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));

            assertThatThrownBy(() -> paymentService.createPayment(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("posiljaoca nije aktivan");
        }

        @Test
        @DisplayName("throws when to account is not active")
        void toAccountNotActive() {
            toAccount.setStatus(AccountStatus.BLOCKED);
            CreatePaymentRequestDto request = buildRequest("100.00");
            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));

            assertThatThrownBy(() -> paymentService.createPayment(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("primaoca nije aktivan");
        }

        @Test
        @DisplayName("throws when from and to accounts are the same")
        void sameAccount() {
            CreatePaymentRequestDto request = buildRequest("100.00");
            request.setToAccount(fromAccount.getAccountNumber());
            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(fromAccount));

            assertThatThrownBy(() -> paymentService.createPayment(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("razliciti");
        }

        @Test
        @DisplayName("throws when from account has no client")
        void fromAccountNoClient() {
            fromAccount.setClient(null);
            CreatePaymentRequestDto request = buildRequest("100.00");
            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));

            assertThatThrownBy(() -> paymentService.createPayment(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ne pripada");
        }

        @Test
        @DisplayName("throws when from account belongs to different client")
        void fromAccountDifferentClient() {
            Client other = new Client();
            other.setId(99L);
            fromAccount.setClient(other);
            CreatePaymentRequestDto request = buildRequest("100.00");
            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));

            assertThatThrownBy(() -> paymentService.createPayment(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ne pripada");
        }

        @Test
        @DisplayName("throws when insufficient funds (same currency, no fee)")
        void insufficientFundsSameCurrency() {
            fromAccount.setAvailableBalance(new BigDecimal("50.00"));
            fromAccount.setBalance(new BigDecimal("50.00"));
            CreatePaymentRequestDto request = buildRequest("100.00");
            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));

            assertThatThrownBy(() -> paymentService.createPayment(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Nedovoljno sredstava");
        }

        @Test
        @DisplayName("null daily limit does not block payment")
        void nullDailyLimit() {
            fromAccount.setDailyLimit(null);
            CreatePaymentRequestDto request = buildRequest("100.00");
            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));
            when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                p.setId(1L);
                p.setCreatedAt(LocalDateTime.now());
                return p;
            });

            PaymentResponseDto response = paymentService.createPayment(request);
            assertThat(response.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        }

        @Test
        @DisplayName("null monthly limit does not block payment")
        void nullMonthlyLimit() {
            fromAccount.setMonthlyLimit(null);
            CreatePaymentRequestDto request = buildRequest("100.00");
            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));
            when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                p.setId(1L);
                p.setCreatedAt(LocalDateTime.now());
                return p;
            });

            PaymentResponseDto response = paymentService.createPayment(request);
            assertThat(response.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        }
    }

    // ===== Order number retry on DataIntegrityViolationException =====

    @Nested
    @DisplayName("Order number retry logic")
    class OrderNumberRetry {

        @Test
        @DisplayName("retries on unique constraint violation for order_number")
        void retriesOnUniqueConstraint() {
            CreatePaymentRequestDto request = buildRequest("100.00");
            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));

            DataIntegrityViolationException uniqueEx = mock(DataIntegrityViolationException.class);
            Exception rootCause = new Exception("Duplicate entry for key 'order_number'");
            when(uniqueEx.getMostSpecificCause()).thenReturn(rootCause);

            when(paymentRepository.saveAndFlush(any(Payment.class)))
                    .thenThrow(uniqueEx) // first attempt fails
                    .thenAnswer(inv -> { // second succeeds
                        Payment p = inv.getArgument(0);
                        p.setId(1L);
                        p.setCreatedAt(LocalDateTime.now());
                        return p;
                    });

            PaymentResponseDto response = paymentService.createPayment(request);
            assertThat(response.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            verify(paymentRepository, times(2)).saveAndFlush(any(Payment.class));
        }

        @Test
        @DisplayName("throws after max retries exhausted")
        void throwsAfterMaxRetries() {
            CreatePaymentRequestDto request = buildRequest("100.00");
            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));

            DataIntegrityViolationException uniqueEx = mock(DataIntegrityViolationException.class);
            Exception rootCause = new Exception("Duplicate entry for key 'order_number'");
            when(uniqueEx.getMostSpecificCause()).thenReturn(rootCause);

            when(paymentRepository.saveAndFlush(any(Payment.class))).thenThrow(uniqueEx);

            assertThatThrownBy(() -> paymentService.createPayment(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Generisanje broja");
        }

        @Test
        @DisplayName("rethrows DataIntegrityViolationException with null message")
        void rethrowsOnNullMessage() {
            CreatePaymentRequestDto request = buildRequest("100.00");
            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));

            DataIntegrityViolationException ex = mock(DataIntegrityViolationException.class);
            Exception rootCause = mock(Exception.class);
            when(rootCause.getMessage()).thenReturn(null);
            when(ex.getMostSpecificCause()).thenReturn(rootCause);

            when(paymentRepository.saveAndFlush(any(Payment.class))).thenThrow(ex);

            assertThatThrownBy(() -> paymentService.createPayment(request))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("rethrows DataIntegrityViolationException for non-order_number violation")
        void rethrowsNonOrderNumberViolation() {
            CreatePaymentRequestDto request = buildRequest("100.00");
            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
            when(paymentAccountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));

            DataIntegrityViolationException ex = mock(DataIntegrityViolationException.class);
            Exception rootCause = new Exception("Foreign key constraint failed on 'fk_currency'");
            when(ex.getMostSpecificCause()).thenReturn(rootCause);

            when(paymentRepository.saveAndFlush(any(Payment.class))).thenThrow(ex);

            assertThatThrownBy(() -> paymentService.createPayment(request))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    // ===== getPayments =====

    @Nested
    @DisplayName("getPayments")
    class GetPayments {

        @Test
        @DisplayName("returns empty page when no client authenticated")
        void noClient() {
            SecurityContextHolder.clearContext();
            Page<PaymentListItemDto> result = paymentService.getPayments(
                    PageRequest.of(0, 10), null, null, null, null, null, null);
            assertThat(result.getContent()).isEmpty();
        }

        @Test
        @DisplayName("returns payments for authenticated client")
        void withClient() {
            Payment payment = Payment.builder()
                    .id(1L).orderNumber("PAY-1234567890123456")
                    .fromAccount(fromAccount).toAccountNumber("222222222222222222")
                    .amount(new BigDecimal("100.00")).fee(BigDecimal.ZERO)
                    .currency(eur).status(PaymentStatus.COMPLETED)
                    .createdBy(client).createdAt(LocalDateTime.now())
                    .build();

            when(paymentRepository.findByUserAccountsWithFilters(
                    eq(client.getId()), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(payment)));

            Page<PaymentListItemDto> result = paymentService.getPayments(
                    PageRequest.of(0, 10), null, null, null, null, null, null);
            assertThat(result.getContent()).hasSize(1);
        }
    }

    // ===== resolveDirection - edge cases =====

    @Nested
    @DisplayName("Payment direction resolution")
    class DirectionResolution {

        @Test
        @DisplayName("payment with null fromAccount client resolves to INCOMING")
        void nullFromAccountClient() {
            Account noClientAccount = baseAccount(50L, "333333333333333333", null, eur, new BigDecimal("1000"));
            Payment payment = Payment.builder()
                    .id(1L).orderNumber("PAY-1234567890123456")
                    .fromAccount(noClientAccount).toAccountNumber("222222222222222222")
                    .amount(new BigDecimal("100.00")).fee(BigDecimal.ZERO)
                    .currency(eur).status(PaymentStatus.COMPLETED)
                    .createdBy(client).createdAt(LocalDateTime.now())
                    .build();

            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

            PaymentResponseDto response = paymentService.getPaymentById(1L);
            assertThat(response.getDirection()).isEqualTo(PaymentDirection.INCOMING);
        }

        @Test
        @DisplayName("payment with null fromAccount resolves to INCOMING")
        void nullFromAccount() {
            Payment payment = Payment.builder()
                    .id(2L).orderNumber("PAY-9876543210123456")
                    .fromAccount(null).toAccountNumber("222222222222222222")
                    .amount(new BigDecimal("200.00")).fee(BigDecimal.ZERO)
                    .currency(eur).status(PaymentStatus.COMPLETED)
                    .createdBy(client).createdAt(LocalDateTime.now())
                    .build();

            when(paymentRepository.findById(2L)).thenReturn(Optional.of(payment));

            PaymentResponseDto response = paymentService.getPaymentById(2L);
            assertThat(response.getDirection()).isEqualTo(PaymentDirection.INCOMING);
        }

        @Test
        @DisplayName("outgoing payment when fromAccount client matches")
        void outgoingPayment() {
            Payment payment = Payment.builder()
                    .id(3L).orderNumber("PAY-ABCDEF1234567890")
                    .fromAccount(fromAccount).toAccountNumber("222222222222222222")
                    .amount(new BigDecimal("300.00")).fee(BigDecimal.ZERO)
                    .currency(eur).status(PaymentStatus.COMPLETED)
                    .createdBy(client).createdAt(LocalDateTime.now())
                    .build();

            when(paymentRepository.findById(3L)).thenReturn(Optional.of(payment));

            PaymentResponseDto response = paymentService.getPaymentById(3L);
            assertThat(response.getDirection()).isEqualTo(PaymentDirection.OUTGOING);
        }

        @Test
        @DisplayName("incoming payment when fromAccount client differs")
        void incomingPaymentDifferentClient() {
            Client other = new Client();
            other.setId(99L);
            Account otherAccount = baseAccount(60L, "444444444444444444", other, eur, new BigDecimal("5000"));

            Payment payment = Payment.builder()
                    .id(4L).orderNumber("PAY-XXXXXX1234567890")
                    .fromAccount(otherAccount).toAccountNumber("222222222222222222")
                    .amount(new BigDecimal("400.00")).fee(BigDecimal.ZERO)
                    .currency(eur).status(PaymentStatus.COMPLETED)
                    .createdBy(client).createdAt(LocalDateTime.now())
                    .build();

            when(paymentRepository.findById(4L)).thenReturn(Optional.of(payment));

            PaymentResponseDto response = paymentService.getPaymentById(4L);
            assertThat(response.getDirection()).isEqualTo(PaymentDirection.INCOMING);
        }
    }

    // ===== Auth helpers =====

    @Nested
    @DisplayName("Auth helper edge cases")
    class AuthHelpers {

        @Test
        @DisplayName("throws when not authenticated")
        void notAuthenticated() {
            SecurityContextHolder.clearContext();

            assertThatThrownBy(() -> paymentService.getPaymentById(1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Klijent nije pronadjen");
        }

        @Test
        @DisplayName("throws when client not found in DB")
        void clientNotInDb() {
            when(clientRepository.findByEmail(client.getEmail())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.getPaymentById(1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Klijent nije pronadjen");
        }
    }

    // ===== toResponse/toListItem mapper null handling =====

    @Nested
    @DisplayName("Mapper null handling")
    class MapperNullHandling {

        @Test
        @DisplayName("toResponse handles null currency gracefully")
        void nullCurrency() {
            Payment payment = Payment.builder()
                    .id(1L).orderNumber("PAY-1234567890123456")
                    .fromAccount(fromAccount).toAccountNumber("222222222222222222")
                    .amount(new BigDecimal("100.00")).fee(BigDecimal.ZERO)
                    .currency(null).status(PaymentStatus.COMPLETED)
                    .createdBy(client).createdAt(LocalDateTime.now())
                    .build();

            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

            PaymentResponseDto response = paymentService.getPaymentById(1L);
            assertThat(response.getCurrency()).isNull();
        }
    }
}
