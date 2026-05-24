package rs.raf.banka2_bek.payment.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
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
import rs.raf.banka2_bek.notification.service.MailSenderService;
import rs.raf.banka2_bek.interbank.service.BankRoutingService;
import rs.raf.banka2_bek.interbank.service.TransactionExecutorService;
import rs.raf.banka2_bek.interbank.service.InterbankPaymentAsyncService;
import rs.raf.banka2_bek.interbank.repository.InterbankTransactionRepository;
import rs.raf.banka2_bek.notification.service.NotificationService;
import rs.raf.banka2_bek.payment.dto.CreatePaymentRequestDto;
import rs.raf.banka2_bek.payment.dto.PaymentResponseDto;
import rs.raf.banka2_bek.payment.model.Payment;
import rs.raf.banka2_bek.payment.model.PaymentCode;
import rs.raf.banka2_bek.payment.repository.PaymentAccountRepository;
import rs.raf.banka2_bek.payment.repository.PaymentRepository;
import rs.raf.banka2_bek.payment.service.implementation.PaymentServiceImpl;
import rs.raf.banka2_bek.transaction.service.TransactionService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Targets remaining PaymentServiceImpl uncovered branches:
 *  - line 195: DIVE rethrow for non-order_number message
 *  - lines 208/210: null currency / null createdAt in payment confirmation mail args (toResponse)
 *  - lines 266/268: null currency / null fromAccount in toListItem
 *  - lines 283/285/286: auth null, principal-not-UserDetails branches in getAuthenticatedUsername
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceImplCoverageTest {

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
        lenient().when(bankRoutingService.isLocalAccount(any())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authAs(String email) {
        org.springframework.security.core.userdetails.User principal =
                new org.springframework.security.core.userdetails.User(email, "x",
                        List.of(new SimpleGrantedAuthority("ROLE_CLIENT")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    // ---------- line 195: DIVE rethrow when message is unrelated ----------

    @Test
    void createPayment_rethrowsDataIntegrityViolationWhenMessageUnrelated() {
        Client client = new Client(); client.setId(1L); client.setEmail("c@x");
        Currency eur = new Currency(); eur.setId(1L); eur.setCode("EUR");

        Account from = new Account();
        from.setId(10L); from.setAccountNumber("AAA"); from.setStatus(AccountStatus.ACTIVE);
        from.setCurrency(eur); from.setClient(client);
        from.setBalance(new BigDecimal("1000")); from.setAvailableBalance(new BigDecimal("1000"));
        from.setDailySpending(BigDecimal.ZERO); from.setMonthlySpending(BigDecimal.ZERO);

        Account to = new Account();
        to.setId(11L); to.setAccountNumber("BBB"); to.setStatus(AccountStatus.ACTIVE);
        to.setCurrency(eur); to.setClient(client);
        to.setBalance(BigDecimal.ZERO); to.setAvailableBalance(BigDecimal.ZERO);

        when(paymentAccountRepository.findForUpdateByAccountNumber("AAA")).thenReturn(Optional.of(from));
        when(paymentAccountRepository.findForUpdateByAccountNumber("BBB")).thenReturn(Optional.of(to));
        when(clientRepository.findByEmail("c@x")).thenReturn(Optional.of(client));

        DataIntegrityViolationException ex = new DataIntegrityViolationException("boom",
                new RuntimeException("some other database error"));
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenThrow(ex);

        authAs("c@x");

        CreatePaymentRequestDto req = new CreatePaymentRequestDto();
        req.setFromAccount("AAA"); req.setToAccount("BBB");
        req.setAmount(new BigDecimal("10"));
        req.setPaymentCode(PaymentCode.CODE_289);
        req.setRecipientName("X");

        assertThatThrownBy(() -> paymentService.createPayment(req))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------- lines 208/210 + 266/268: null currency / null fromAccount in mapping ----------

    @Test
    void getPaymentById_handlesNullCurrencyAndFromAccount() {
        Client client = new Client(); client.setId(1L); client.setEmail("c@x");
        when(clientRepository.findByEmail("c@x")).thenReturn(Optional.of(client));

        Payment p = Payment.builder()
                .id(99L).orderNumber("ORD1")
                .fromAccount(null)   // null fromAccount branch (toResponse 254/resolveDirection)
                .toAccountNumber("BBB")
                .amount(new BigDecimal("10")).fee(BigDecimal.ZERO)
                .currency(null)      // null currency branch
                .paymentCode("289").recipientName("X")
                .build();

        when(paymentRepository.findById(99L)).thenReturn(Optional.of(p));

        authAs("c@x");

        PaymentResponseDto dto = paymentService.getPaymentById(99L);
        assertThat(dto.getId()).isEqualTo(99L);
        assertThat(dto.getCurrency()).isNull();
        assertThat(dto.getFromAccount()).isNull();
    }

    @Test
    void getPayments_listItemHandlesNullCurrencyAndFromAccount() {
        Client client = new Client(); client.setId(1L); client.setEmail("c@x");
        when(clientRepository.findByEmail("c@x")).thenReturn(Optional.of(client));

        Payment p = Payment.builder()
                .id(77L).orderNumber("ORD2")
                .fromAccount(null)
                .toAccountNumber("XX")
                .amount(new BigDecimal("5")).fee(BigDecimal.ZERO)
                .currency(null)
                .paymentCode("289").recipientName("Y")
                .build();

        when(paymentRepository.findByUserAccountsWithFilters(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(p)));

        authAs("c@x");

        Page<?> page = paymentService.getPayments(PageRequest.of(0, 10), null, null, null, null, null, null);
        assertThat(page.getContent()).hasSize(1);
    }

    // ---------- lines 283/285/286: auth/principal branches ----------

    @Test
    void getPaymentById_throwsWhenNotAuthenticated() {
        SecurityContextHolder.clearContext();
        // getOptionalClient catches the "Niste prijavljeni" IAE and returns null,
        // then getAuthenticatedClient rethrows "Klijent nije pronadjen" — this still
        // exercises lines 283 (auth-null check) and 291 (null rethrow).
        assertThatThrownBy(() -> paymentService.getPaymentById(1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getPaymentById_throwsWhenPrincipalNotUserDetails() {
        TestingAuthenticationToken token = new TestingAuthenticationToken(new Object(), null,
                List.of(new SimpleGrantedAuthority("ROLE_X")));
        token.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(token);

        // getOptionalClient catches the IAE from getAuthenticatedUsername and returns null,
        // getAuthenticatedClient then rethrows "Klijent nije pronadjen"
        assertThatThrownBy(() -> paymentService.getPaymentById(1L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
