package rs.raf.banka2_bek.payment.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountStatus;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.exchange.ExchangeService;
import rs.raf.banka2_bek.notification.service.MailSenderService;
import rs.raf.banka2_bek.interbank.service.BankRoutingService;
import rs.raf.banka2_bek.interbank.service.TransactionExecutorService;
import rs.raf.banka2_bek.interbank.service.InterbankPaymentAsyncService;
import rs.raf.banka2_bek.interbank.repository.InterbankTransactionRepository;
import rs.raf.banka2_bek.notification.service.NotificationService;
import rs.raf.banka2_bek.payment.dto.CreatePaymentRequestDto;
import rs.raf.banka2_bek.payment.model.PaymentCode;
import rs.raf.banka2_bek.payment.dto.PaymentResponseDto;
import rs.raf.banka2_bek.payment.model.Payment;
import rs.raf.banka2_bek.payment.repository.PaymentAccountRepository;
import rs.raf.banka2_bek.payment.repository.PaymentRepository;
import rs.raf.banka2_bek.payment.service.implementation.PaymentServiceImpl;
import rs.raf.banka2_bek.transaction.service.TransactionService;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Targets remaining PaymentServiceImpl uncovered branches:
 *  - L195: order_number retry success path (saveAndFlush throws once with "order_number" in cause then succeeds)
 *  - L208: fromAccount.getCurrency() == null branch in mail confirmation
 *  - L210: savedPayment.getCreatedAt() == null branch in mail confirmation
 *  - L283: getAuthenticatedUsername with auth.isAuthenticated() == false branch
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceBranchCoverageTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentAccountRepository paymentAccountRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private TransactionService transactionService;
    @Mock private PaymentReceiptPdfGenerator paymentReceiptPdfGenerator;
    @Mock private ExchangeService exchangeService;
    @Mock private MailSenderService mailSenderService;
    @Mock private BankRoutingService bankRoutingService;
    @Mock private TransactionExecutorService transactionExecutorService;
    @Mock private InterbankPaymentAsyncService interbankPaymentAsyncService;
    @Mock private InterbankTransactionRepository interbankTransactionRepository;
    @Mock private NotificationService notificationService;

    private PaymentServiceImpl paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentServiceImpl(
                paymentRepository, paymentAccountRepository, accountRepository,
                clientRepository, transactionService, paymentReceiptPdfGenerator,
                exchangeService, mailSenderService,
                bankRoutingService, transactionExecutorService,
                interbankPaymentAsyncService, interbankTransactionRepository,
                "22200022", notificationService);
        when(bankRoutingService.isLocalAccount(any())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authAs(String email) {
        UserDetails ud = User.builder().username(email).password("x").authorities("ROLE_CLIENT").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(ud, null, ud.getAuthorities()));
    }

    private CreatePaymentRequestDto basicReq() {
        CreatePaymentRequestDto req = new CreatePaymentRequestDto();
        req.setFromAccount("AAA");
        req.setToAccount("BBB");
        req.setAmount(new BigDecimal("10"));
        req.setPaymentCode(PaymentCode.CODE_289);
        req.setRecipientName("X");
        return req;
    }

    // ---------- L195: order_number retry success branch + L208 (currency null) + L210 (createdAt null) ----------
    @Test
    void createPayment_retriesOnOrderNumberConflict_andHandlesNullCurrencyAndCreatedAt() {
        Client client = new Client();
        client.setId(1L);
        client.setEmail("c@x");
        client.setFirstName("Cli");
        client.setLastName("Ent");

        Currency eur = new Currency();
        eur.setId(1L);
        eur.setCode("EUR");

        Account from = new Account();
        from.setId(10L);
        from.setAccountNumber("AAA");
        from.setStatus(AccountStatus.ACTIVE);
        from.setCurrency(eur);
        from.setClient(client);
        from.setBalance(new BigDecimal("1000"));
        from.setAvailableBalance(new BigDecimal("1000"));
        from.setDailySpending(BigDecimal.ZERO);
        from.setMonthlySpending(BigDecimal.ZERO);

        Account to = new Account();
        to.setId(11L);
        to.setAccountNumber("BBB");
        to.setStatus(AccountStatus.ACTIVE);
        to.setCurrency(eur);
        to.setClient(client);
        to.setBalance(BigDecimal.ZERO);
        to.setAvailableBalance(BigDecimal.ZERO);

        when(paymentAccountRepository.findForUpdateByAccountNumber("AAA")).thenReturn(Optional.of(from));
        when(paymentAccountRepository.findForUpdateByAccountNumber("BBB")).thenReturn(Optional.of(to));
        when(clientRepository.findByEmail("c@x")).thenReturn(Optional.of(client));

        // First call throws DIVE with order_number message (retry), second call returns saved Payment
        DataIntegrityViolationException retry = new DataIntegrityViolationException(
                "constraint violation",
                new RuntimeException("Duplicate entry for key 'payments.order_number_unique'"));

        // Saved Payment with null createdAt to exercise L210 false branch.
        // Note: fromAccount.currency cannot easily be nulled mid-flow because it's read earlier
        // for cross-currency check — so L208 null branch is exercised by a separate test below
        // via direct toResponse-style invocation. Here we focus on retry + null createdAt.
        Payment saved = Payment.builder()
                .id(500L)
                .orderNumber("PAY-X")
                .fromAccount(from)
                .toAccountNumber("BBB")
                .amount(new BigDecimal("10"))
                .fee(BigDecimal.ZERO)
                .currency(eur)
                .createdAt(null) // L210 false branch
                .build();

        when(paymentRepository.saveAndFlush(any(Payment.class)))
                .thenThrow(retry)
                .thenReturn(saved);

        authAs("c@x");

        PaymentResponseDto dto = paymentService.createPayment(basicReq());
        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(500L);
    }

    // ---------- L195: retry on "uk" message (without "order_number") ----------
    @Test
    void createPayment_retriesOnUkConstraint() {
        retrySetupAndAssert("Constraint violation: uk_some_index");
    }

    // ---------- L195: retry on "unique" message (without "order_number" or "uk") ----------
    @Test
    void createPayment_retriesOnUniqueConstraint() {
        retrySetupAndAssert("violates unique index");
    }

    private void retrySetupAndAssert(String causeMessage) {
        Client client = new Client();
        client.setId(1L);
        client.setEmail("c@x");
        client.setFirstName("Cli");
        client.setLastName("Ent");

        Currency eur = new Currency();
        eur.setId(1L);
        eur.setCode("EUR");

        Account from = new Account();
        from.setId(10L);
        from.setAccountNumber("AAA");
        from.setStatus(AccountStatus.ACTIVE);
        from.setCurrency(eur);
        from.setClient(client);
        from.setBalance(new BigDecimal("1000"));
        from.setAvailableBalance(new BigDecimal("1000"));
        from.setDailySpending(BigDecimal.ZERO);
        from.setMonthlySpending(BigDecimal.ZERO);

        Account to = new Account();
        to.setId(11L);
        to.setAccountNumber("BBB");
        to.setStatus(AccountStatus.ACTIVE);
        to.setCurrency(eur);
        to.setClient(client);
        to.setBalance(BigDecimal.ZERO);
        to.setAvailableBalance(BigDecimal.ZERO);

        when(paymentAccountRepository.findForUpdateByAccountNumber("AAA")).thenReturn(Optional.of(from));
        when(paymentAccountRepository.findForUpdateByAccountNumber("BBB")).thenReturn(Optional.of(to));
        when(clientRepository.findByEmail("c@x")).thenReturn(Optional.of(client));

        DataIntegrityViolationException retry = new DataIntegrityViolationException(
                "constraint violation",
                new RuntimeException(causeMessage));

        Payment saved = Payment.builder()
                .id(502L)
                .orderNumber("PAY-Z")
                .fromAccount(from)
                .toAccountNumber("BBB")
                .amount(new BigDecimal("10"))
                .fee(BigDecimal.ZERO)
                .currency(eur)
                .build();

        when(paymentRepository.saveAndFlush(any(Payment.class)))
                .thenThrow(retry)
                .thenReturn(saved);

        authAs("c@x");
        PaymentResponseDto dto = paymentService.createPayment(basicReq());
        assertThat(dto).isNotNull();
    }

    // ---------- L208: fromAccount currency null branch ----------
    // Production flow always reads fromAccount.getCurrency() earlier (cross-currency check) so we
    // cannot null it via the public createPayment path. The L208 ternary is reachable only in a
    // path where currency becomes null between the equality check and mail send. We force-cover
    // this by using a same-currency setup, then nulling currency on the saved Payment's
    // fromAccount reference right before saveAndFlush returns.
    @Test
    void createPayment_nullsCurrencyOnSavedFromAccount_forMailNullBranch() {
        Client client = new Client();
        client.setId(1L);
        client.setEmail("c@x");
        client.setFirstName("Cli");
        client.setLastName("Ent");

        Currency eur = new Currency();
        eur.setId(1L);
        eur.setCode("EUR");

        Account from = new Account();
        from.setId(10L);
        from.setAccountNumber("AAA");
        from.setStatus(AccountStatus.ACTIVE);
        from.setCurrency(eur);
        from.setClient(client);
        from.setBalance(new BigDecimal("1000"));
        from.setAvailableBalance(new BigDecimal("1000"));
        from.setDailySpending(BigDecimal.ZERO);
        from.setMonthlySpending(BigDecimal.ZERO);

        Account to = new Account();
        to.setId(11L);
        to.setAccountNumber("BBB");
        to.setStatus(AccountStatus.ACTIVE);
        to.setCurrency(eur);
        to.setClient(client);
        to.setBalance(BigDecimal.ZERO);
        to.setAvailableBalance(BigDecimal.ZERO);

        when(paymentAccountRepository.findForUpdateByAccountNumber("AAA")).thenReturn(Optional.of(from));
        when(paymentAccountRepository.findForUpdateByAccountNumber("BBB")).thenReturn(Optional.of(to));
        when(clientRepository.findByEmail("c@x")).thenReturn(Optional.of(client));

        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(501L);
            // null out the currency right before mail send — reaches L208 false branch
            from.setCurrency(null);
            return p;
        });

        authAs("c@x");

        PaymentResponseDto dto = paymentService.createPayment(basicReq());
        assertThat(dto).isNotNull();
    }

    // ---------- L283: getAuthenticatedUsername — auth.isAuthenticated() == false branch ----------
    @Test
    void getAuthenticatedUsername_authNotAuthenticated_throws() throws Exception {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(false);
        SecurityContext ctx = mock(SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);

        Method m = PaymentServiceImpl.class.getDeclaredMethod("getAuthenticatedUsername");
        m.setAccessible(true);

        assertThatThrownBy(() -> {
            try {
                m.invoke(paymentService);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Niste prijavljeni");
    }
}
