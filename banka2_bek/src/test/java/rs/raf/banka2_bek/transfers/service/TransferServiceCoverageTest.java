package rs.raf.banka2_bek.transfers.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountStatus;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.company.model.AuthorizedPerson;
import rs.raf.banka2_bek.company.model.Company;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.exchange.ExchangeService;
import rs.raf.banka2_bek.transfers.dto.TransferFxRequestDto;
import rs.raf.banka2_bek.transfers.dto.TransferResponseDto;
import rs.raf.banka2_bek.transfers.model.Transfer;
import rs.raf.banka2_bek.transfers.repository.TransferRepository;
import rs.raf.banka2_bek.notification.service.NotificationService;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Targets remaining uncovered branches in TransferService:
 *  - FX transfer account-not-found exceptions (lines 139/142)
 *  - getAllTransfers filter branches (246/249/254)
 *  - ensureAccess null/company-authorized paths (269/274/276)
 *  - getAuthenticatedEmail edge cases (290/297/300)
 */
@ExtendWith(MockitoExtension.class)
class TransferServiceCoverageTest {

    @Mock private TransferRepository transferRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private ExchangeService exchangeService;
    @Mock private ClientRepository clientRepository;
    @Mock private NotificationService notificationService;

    private TransferService transferService;

    @BeforeEach
    void setUp() throws Exception {
        // Test isolation: clear bilo kakvu zaostalu Authentication iz prethodne
        // test klase u JVM-u (Surefire fork reuse). Bez ovog, neki testovi
        // mogu naslediti SecurityContext sa drugim emailom (npr. "marko@banka.rs"
        // iz integration testa) i Mockito strict stub puca jer code-under-test
        // poziva findByEmail sa neocekivanim argumentom.
        SecurityContextHolder.clearContext();
        transferService = new TransferService(transferRepository, accountRepository, exchangeService, clientRepository, notificationService);
        Field f = TransferService.class.getDeclaredField("bankRegistrationNumber");
        f.setAccessible(true);
        f.set(transferService, "22200022");
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

    // ---------- FX transfer: account-not-found branches ----------

    @Test
    void fxTransfer_throwsWhenFromAccountMissing() {
        TransferFxRequestDto req = new TransferFxRequestDto();
        req.setFromAccountNumber("NOPE");
        req.setToAccountNumber("OTHER");
        req.setAmount(new BigDecimal("100"));

        when(accountRepository.findForUpdateByAccountNumber("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferService.fxTransfer(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("From account not found");
    }

    @Test
    void fxTransfer_throwsWhenToAccountMissing() {
        Currency eur = new Currency(); eur.setId(2L); eur.setCode("EUR");
        Account from = new Account();
        from.setAccountNumber("AAA");
        from.setStatus(AccountStatus.ACTIVE);
        from.setCurrency(eur);

        TransferFxRequestDto req = new TransferFxRequestDto();
        req.setFromAccountNumber("AAA");
        req.setToAccountNumber("BBB");
        req.setAmount(new BigDecimal("100"));

        when(accountRepository.findForUpdateByAccountNumber("AAA")).thenReturn(Optional.of(from));
        when(accountRepository.findForUpdateByAccountNumber("BBB")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferService.fxTransfer(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("To account not found");
    }

    // ---------- getAllTransfers filter branches ----------

    private Transfer makeTransfer(String fromAcc, String toAcc, LocalDateTime createdAt) {
        Currency rsd = new Currency(); rsd.setId(1L); rsd.setCode("RSD");
        Client c = new Client();
        c.setFirstName("A"); c.setLastName("B");
        Account a = new Account(); a.setAccountNumber(fromAcc);
        Account b = new Account(); b.setAccountNumber(toAcc);
        Transfer t = new Transfer();
        t.setId(1L);
        t.setOrderNumber("ORD");
        t.setFromAccount(a); t.setToAccount(b);
        t.setFromAmount(new BigDecimal("10")); t.setToAmount(new BigDecimal("10"));
        t.setFromCurrency(rsd); t.setToCurrency(rsd);
        t.setExchangeRate(null); t.setCommission(BigDecimal.ZERO);
        t.setCreatedBy(c);
        t.setCreatedAt(createdAt);
        return t;
    }

    @Test
    void getAllTransfers_filtersByAccountNumberAndDates() {
        Client client = new Client(); client.setId(1L);

        Transfer t1 = makeTransfer("111", "222", LocalDateTime.of(2026, 1, 15, 10, 0)); // in range, acct match
        Transfer t2 = makeTransfer("333", "444", LocalDateTime.of(2026, 1, 15, 10, 0)); // acct mismatch → skip
        Transfer t3 = makeTransfer("111", "222", LocalDateTime.of(2025, 1, 1, 0, 0)); // before fromDate → skip
        Transfer t4 = makeTransfer("111", "222", LocalDateTime.of(2027, 1, 1, 0, 0)); // after toDate → skip

        when(transferRepository.findByCreatedByOrderByCreatedAtDesc(client))
                .thenReturn(List.of(t1, t2, t3, t4));

        List<TransferResponseDto> result = transferService.getAllTransfers(
                client, "111", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertThat(result).hasSize(1);
    }

    @Test
    void getAllTransfers_noFilters_returnsAll() {
        Client client = new Client(); client.setId(1L);
        Transfer t1 = makeTransfer("111", "222", LocalDateTime.of(2026, 1, 15, 10, 0));
        when(transferRepository.findByCreatedByOrderByCreatedAtDesc(client)).thenReturn(List.of(t1));

        List<TransferResponseDto> result = transferService.getAllTransfers(client, null, null, null);
        assertThat(result).hasSize(1);
    }

    @Test
    void getAllTransfers_blankAccountNumberIgnored() {
        Client client = new Client(); client.setId(1L);
        Transfer t1 = makeTransfer("111", "222", LocalDateTime.of(2026, 1, 15, 10, 0));
        when(transferRepository.findByCreatedByOrderByCreatedAtDesc(client)).thenReturn(List.of(t1));

        List<TransferResponseDto> result = transferService.getAllTransfers(client, "   ", null, null);
        assertThat(result).hasSize(1);
    }

    // ---------- ensureAccess / getAuthenticatedClient helper branches ----------

    @Test
    void internalTransfer_allowsAuthorizedCompanyPerson() {
        Currency rsd = new Currency(); rsd.setId(1L); rsd.setCode("RSD");

        Client actor = new Client(); actor.setId(99L); actor.setEmail("auth@x");
        Company company = new Company();
        AuthorizedPerson ap = new AuthorizedPerson();
        ap.setClient(actor);
        company.setAuthorizedPersons(List.of(ap));

        Account from = new Account();
        from.setAccountNumber("AAA"); from.setStatus(AccountStatus.ACTIVE);
        from.setCurrency(rsd); from.setCompany(company);
        from.setBalance(new BigDecimal("1000")); from.setAvailableBalance(new BigDecimal("1000"));

        Account to = new Account();
        to.setAccountNumber("BBB"); to.setStatus(AccountStatus.ACTIVE);
        to.setCurrency(rsd); to.setCompany(company);
        to.setBalance(BigDecimal.ZERO); to.setAvailableBalance(BigDecimal.ZERO);

        when(accountRepository.findForUpdateByAccountNumber("AAA")).thenReturn(Optional.of(from));
        when(accountRepository.findForUpdateByAccountNumber("BBB")).thenReturn(Optional.of(to));
        when(clientRepository.findByEmail("auth@x")).thenReturn(Optional.of(actor));

        authAs("auth@x");

        rs.raf.banka2_bek.transfers.dto.TransferInternalRequestDto req =
                new rs.raf.banka2_bek.transfers.dto.TransferInternalRequestDto();
        req.setFromAccountNumber("AAA"); req.setToAccountNumber("BBB");
        req.setAmount(new BigDecimal("100"));

        TransferResponseDto res = transferService.internalTransfer(req);
        assertThat(res).isNotNull();
    }

    @Test
    void internalTransfer_throwsWhenNoAccessAtAll() {
        Currency rsd = new Currency(); rsd.setId(1L); rsd.setCode("RSD");

        Client actor = new Client(); actor.setId(99L); actor.setEmail("auth@x");
        Client other = new Client(); other.setId(5L);

        Account from = new Account();
        from.setAccountNumber("AAA"); from.setStatus(AccountStatus.ACTIVE);
        from.setCurrency(rsd); from.setClient(other);
        from.setBalance(new BigDecimal("1000")); from.setAvailableBalance(new BigDecimal("1000"));

        Account to = new Account();
        to.setAccountNumber("BBB"); to.setStatus(AccountStatus.ACTIVE);
        to.setCurrency(rsd); to.setClient(other);
        to.setBalance(BigDecimal.ZERO); to.setAvailableBalance(BigDecimal.ZERO);

        when(accountRepository.findForUpdateByAccountNumber("AAA")).thenReturn(Optional.of(from));
        when(accountRepository.findForUpdateByAccountNumber("BBB")).thenReturn(Optional.of(to));
        when(clientRepository.findByEmail("auth@x")).thenReturn(Optional.of(actor));

        authAs("auth@x");

        rs.raf.banka2_bek.transfers.dto.TransferInternalRequestDto req =
                new rs.raf.banka2_bek.transfers.dto.TransferInternalRequestDto();
        req.setFromAccountNumber("AAA"); req.setToAccountNumber("BBB");
        req.setAmount(new BigDecimal("100"));

        assertThatThrownBy(() -> transferService.internalTransfer(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("do not have access");
    }

    // ---------- getAuthenticatedEmail edge cases ----------

    @Test
    void internalTransfer_throwsWhenNotAuthenticated() {
        Currency rsd = new Currency(); rsd.setId(1L); rsd.setCode("RSD");
        Account from = new Account();
        from.setAccountNumber("AAA"); from.setStatus(AccountStatus.ACTIVE); from.setCurrency(rsd);
        Account to = new Account();
        to.setAccountNumber("BBB"); to.setStatus(AccountStatus.ACTIVE); to.setCurrency(rsd);

        when(accountRepository.findForUpdateByAccountNumber("AAA")).thenReturn(Optional.of(from));
        when(accountRepository.findForUpdateByAccountNumber("BBB")).thenReturn(Optional.of(to));

        SecurityContextHolder.clearContext();

        rs.raf.banka2_bek.transfers.dto.TransferInternalRequestDto req =
                new rs.raf.banka2_bek.transfers.dto.TransferInternalRequestDto();
        req.setFromAccountNumber("AAA"); req.setToAccountNumber("BBB");
        req.setAmount(new BigDecimal("100"));

        assertThatThrownBy(() -> transferService.internalTransfer(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not authenticated");
    }

    @Test
    void internalTransfer_acceptsStringPrincipal() {
        Currency rsd = new Currency(); rsd.setId(1L); rsd.setCode("RSD");

        Client actor = new Client(); actor.setId(99L); actor.setEmail("string@x");
        Account from = new Account();
        from.setAccountNumber("AAA"); from.setStatus(AccountStatus.ACTIVE);
        from.setCurrency(rsd); from.setClient(actor);
        from.setBalance(new BigDecimal("1000")); from.setAvailableBalance(new BigDecimal("1000"));

        Account to = new Account();
        to.setAccountNumber("BBB"); to.setStatus(AccountStatus.ACTIVE);
        to.setCurrency(rsd); to.setClient(actor);
        to.setBalance(BigDecimal.ZERO); to.setAvailableBalance(BigDecimal.ZERO);

        when(accountRepository.findForUpdateByAccountNumber("AAA")).thenReturn(Optional.of(from));
        when(accountRepository.findForUpdateByAccountNumber("BBB")).thenReturn(Optional.of(to));
        when(clientRepository.findByEmail("string@x")).thenReturn(Optional.of(actor));

        // String principal
        TestingAuthenticationToken token = new TestingAuthenticationToken("string@x", null,
                List.of(new SimpleGrantedAuthority("ROLE_CLIENT")));
        token.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(token);

        rs.raf.banka2_bek.transfers.dto.TransferInternalRequestDto req =
                new rs.raf.banka2_bek.transfers.dto.TransferInternalRequestDto();
        req.setFromAccountNumber("AAA"); req.setToAccountNumber("BBB");
        req.setAmount(new BigDecimal("100"));

        TransferResponseDto res = transferService.internalTransfer(req);
        assertThat(res).isNotNull();
    }

    @Test
    void internalTransfer_throwsForUnknownPrincipalType() {
        Currency rsd = new Currency(); rsd.setId(1L); rsd.setCode("RSD");
        Account from = new Account();
        from.setAccountNumber("AAA"); from.setStatus(AccountStatus.ACTIVE); from.setCurrency(rsd);
        Account to = new Account();
        to.setAccountNumber("BBB"); to.setStatus(AccountStatus.ACTIVE); to.setCurrency(rsd);

        when(accountRepository.findForUpdateByAccountNumber("AAA")).thenReturn(Optional.of(from));
        when(accountRepository.findForUpdateByAccountNumber("BBB")).thenReturn(Optional.of(to));

        TestingAuthenticationToken token = new TestingAuthenticationToken(new Object(), null,
                List.of(new SimpleGrantedAuthority("ROLE_CLIENT")));
        token.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(token);

        rs.raf.banka2_bek.transfers.dto.TransferInternalRequestDto req =
                new rs.raf.banka2_bek.transfers.dto.TransferInternalRequestDto();
        req.setFromAccountNumber("AAA"); req.setToAccountNumber("BBB");
        req.setAmount(new BigDecimal("100"));

        assertThatThrownBy(() -> transferService.internalTransfer(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("resolve user email");
    }
}
