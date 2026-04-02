package rs.raf.banka2_bek.margin.service;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountStatus;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.margin.dto.CreateMarginAccountDto;
import rs.raf.banka2_bek.margin.dto.MarginAccountCheckDto;
import rs.raf.banka2_bek.margin.dto.MarginAccountDto;
import rs.raf.banka2_bek.margin.event.MarginAccountBlockedEvent;
import rs.raf.banka2_bek.margin.model.MarginAccount;
import rs.raf.banka2_bek.margin.model.MarginAccountStatus;
import rs.raf.banka2_bek.margin.model.MarginTransaction;
import rs.raf.banka2_bek.margin.model.MarginTransactionType;
import rs.raf.banka2_bek.margin.repository.MarginAccountRepository;
import rs.raf.banka2_bek.margin.repository.MarginTransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarginAccountServiceTest {

    @Mock
    private MarginAccountRepository marginAccountRepository;
    @Mock
    private MarginTransactionRepository marginTransactionRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private ClientRepository clientRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private MarginAccountService marginAccountService;

    @BeforeEach
    void setUp() {
        marginAccountService = new MarginAccountService(
                marginAccountRepository,
                marginTransactionRepository,
                accountRepository,
                clientRepository,
                eventPublisher
        );
    }

    @Test
    void getMyMarginAccounts_returnsOnlyAuthenticatedClientAccounts() {
        Client client = Client.builder().id(10L).email("client@test.com").build();

        Account account = Account.builder().id(5L).accountNumber("222000112345678911").build();
        MarginAccount marginAccount = MarginAccount.builder()
                .id(77L)
                .account(account)
                .userId(10L)
                .initialMargin(new BigDecimal("10000.0000"))
                .loanValue(new BigDecimal("5000.0000"))
                .maintenanceMargin(new BigDecimal("5000.0000"))
                .bankParticipation(new BigDecimal("0.50"))
                .status(MarginAccountStatus.ACTIVE)
                .build();

        when(clientRepository.findByEmail("client@test.com")).thenReturn(Optional.of(client));
        when(marginAccountRepository.findByUserId(10L)).thenReturn(List.of(marginAccount));

        List<MarginAccountDto> result = marginAccountService.getMyMarginAccounts("client@test.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(77L);
        assertThat(result.get(0).getUserId()).isEqualTo(10L);
        assertThat(result.get(0).getAccountId()).isEqualTo(5L);
        assertThat(result.get(0).getAccountNumber()).isEqualTo("222000112345678911");
    }

    @Test
    void getMyMarginAccounts_throwsWhenEmailDoesNotBelongToClient() {
        when(clientRepository.findByEmail("employee@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> marginAccountService.getMyMarginAccounts("employee@test.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only clients can view margin accounts.");
    }

    @Test
    void createForUser_success_calculatesAndPersistsAllValues() {
        Long userId = 10L;
        Account baseAccount = activeAccount(1L, userId, "10000.0000", "10000.0000");
        CreateMarginAccountDto dto = new CreateMarginAccountDto(1L, new BigDecimal("5000.00"));

        when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(baseAccount));
        when(marginAccountRepository.findByAccountId(1L)).thenReturn(List.of());
        when(marginAccountRepository.save(any(MarginAccount.class))).thenAnswer(invocation -> {
            MarginAccount marginAccount = invocation.getArgument(0);
            marginAccount.setId(55L);
            marginAccount.setCreatedAt(LocalDateTime.now());
            return marginAccount;
        });

        MarginAccountDto result = marginAccountService.createForUser(userId, dto);

        assertThat(result.getId()).isEqualTo(55L);
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getStatus()).isEqualTo(MarginAccountStatus.ACTIVE.name());
        assertThat(result.getBankParticipation()).isEqualByComparingTo("0.50");
        assertThat(result.getInitialMargin()).isEqualByComparingTo("10000.0000");
        assertThat(result.getLoanValue()).isEqualByComparingTo("5000.0000");
        assertThat(result.getMaintenanceMargin()).isEqualByComparingTo("5000.0000");

        assertThat(baseAccount.getAvailableBalance()).isEqualByComparingTo("5000.0000");
        assertThat(baseAccount.getBalance()).isEqualByComparingTo("5000.0000");

        ArgumentCaptor<MarginTransaction> txCaptor = ArgumentCaptor.forClass(MarginTransaction.class);
        verify(marginTransactionRepository).save(txCaptor.capture());

        MarginTransaction tx = txCaptor.getValue();
        assertThat(tx.getType()).isEqualTo(MarginTransactionType.DEPOSIT);
        assertThat(tx.getAmount()).isEqualByComparingTo("5000.0000");
        assertThat(tx.getDescription()).isEqualTo("Initial margin deposit");
    }

    @Test
    void createForUser_throwsWhenUserIdMissing() {
        CreateMarginAccountDto dto = new CreateMarginAccountDto(1L, new BigDecimal("100.00"));

        assertThatThrownBy(() -> marginAccountService.createForUser(null, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Authenticated user id is required.");
    }

    @Test
    void createForUser_throwsWhenDepositIsNonPositive() {
        CreateMarginAccountDto dto = new CreateMarginAccountDto(1L, BigDecimal.ZERO);

        assertThatThrownBy(() -> marginAccountService.createForUser(10L, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Initial deposit must be greater than zero.");
    }

    @Test
    void createForUser_throwsWhenAccountMissing() {
        CreateMarginAccountDto dto = new CreateMarginAccountDto(1L, new BigDecimal("100.00"));
        when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> marginAccountService.createForUser(10L, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account not found.");
    }

    @Test
    void createForUser_throwsWhenAccountOwnedByAnotherClient() {
        Account baseAccount = activeAccount(1L, 999L, "1000.00", "1000.00");
        CreateMarginAccountDto dto = new CreateMarginAccountDto(1L, new BigDecimal("100.00"));

        when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(baseAccount));

        assertThatThrownBy(() -> marginAccountService.createForUser(10L, dto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("You are not allowed to create a margin account for this base account.");
    }

    @Test
    void createForUser_throwsWhenBaseAccountIsInactive() {
        Account baseAccount = activeAccount(1L, 10L, "1000.00", "1000.00");
        baseAccount.setStatus(AccountStatus.BLOCKED);
        CreateMarginAccountDto dto = new CreateMarginAccountDto(1L, new BigDecimal("100.00"));

        when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(baseAccount));

        assertThatThrownBy(() -> marginAccountService.createForUser(10L, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Base account must be active.");
    }

    @Test
    void createForUser_throwsWhenMarginAccountAlreadyExists() {
        Account baseAccount = activeAccount(1L, 10L, "1000.00", "1000.00");
        CreateMarginAccountDto dto = new CreateMarginAccountDto(1L, new BigDecimal("100.00"));

        when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(baseAccount));
        when(marginAccountRepository.findByAccountId(1L)).thenReturn(List.of(new MarginAccount()));

        assertThatThrownBy(() -> marginAccountService.createForUser(10L, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Margin account already exists for this base account.");
    }

    @Test
    void createForUser_throwsWhenInsufficientAvailableBalance() {
        Account baseAccount = activeAccount(1L, 10L, "50.00", "50.00");
        CreateMarginAccountDto dto = new CreateMarginAccountDto(1L, new BigDecimal("100.00"));

        when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(baseAccount));
        when(marginAccountRepository.findByAccountId(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> marginAccountService.createForUser(10L, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Insufficient available balance for initial margin deposit.");
    }

    // ── deposit() tests ────────────────────────────────────────────────────────

    @Test
    void deposit_success_increasesInitialMarginAndRecalculatesMaintenanceMargin() {
        Authentication auth = authenticatedAs("client@test.com");
        Client client = Client.builder().id(10L).email("client@test.com").build();
        MarginAccount account = activeMarginAccount(10L, "10000", "5000", MarginAccountStatus.ACTIVE);

        when(clientRepository.findByEmail("client@test.com")).thenReturn(Optional.of(client));
        when(marginAccountRepository.findById(1L)).thenReturn(Optional.of(account));

        marginAccountService.deposit(1L, new BigDecimal("2000"), auth);

        assertThat(account.getInitialMargin()).isEqualByComparingTo("12000");
        assertThat(account.getMaintenanceMargin()).isEqualByComparingTo("6000");

        verify(marginAccountRepository).save(account);

        ArgumentCaptor<MarginTransaction> txCaptor = ArgumentCaptor.forClass(MarginTransaction.class);
        verify(marginTransactionRepository).save(txCaptor.capture());
        assertThat(txCaptor.getValue().getType()).isEqualTo(MarginTransactionType.DEPOSIT);
        assertThat(txCaptor.getValue().getAmount()).isEqualByComparingTo("2000");
    }

    @Test
    void deposit_success_unblocksBLOCKEDAccount() {
        Authentication auth = authenticatedAs("client@test.com");
        Client client = Client.builder().id(10L).email("client@test.com").build();
        MarginAccount account = activeMarginAccount(10L, "10000", "5000", MarginAccountStatus.BLOCKED);

        when(clientRepository.findByEmail("client@test.com")).thenReturn(Optional.of(client));
        when(marginAccountRepository.findById(1L)).thenReturn(Optional.of(account));

        marginAccountService.deposit(1L, new BigDecimal("2000"), auth);

        assertThat(account.getStatus()).isEqualTo(MarginAccountStatus.ACTIVE);
    }

    @Test
    void deposit_doesNotChangeStatus_whenAccountIsAlreadyActive() {
        Authentication auth = authenticatedAs("client@test.com");
        Client client = Client.builder().id(10L).email("client@test.com").build();
        MarginAccount account = activeMarginAccount(10L, "10000", "5000", MarginAccountStatus.ACTIVE);

        when(clientRepository.findByEmail("client@test.com")).thenReturn(Optional.of(client));
        when(marginAccountRepository.findById(1L)).thenReturn(Optional.of(account));

        marginAccountService.deposit(1L, new BigDecimal("2000"), auth);

        assertThat(account.getStatus()).isEqualTo(MarginAccountStatus.ACTIVE);
    }

    @Test
    void deposit_throwsWhenAuthenticationIsNull() {
        assertThatThrownBy(() -> marginAccountService.deposit(1L, new BigDecimal("100"), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Not authenticated.");
    }

    @Test
    void deposit_throwsWhenNotAuthenticated() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(false);

        assertThatThrownBy(() -> marginAccountService.deposit(1L, new BigDecimal("100"), auth))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Not authenticated.");
    }

    @Test
    void deposit_throwsWhenAmountIsNull() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);

        assertThatThrownBy(() -> marginAccountService.deposit(1L, null, auth))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Amount must be a positive number.");
    }

    @Test
    void deposit_throwsWhenAmountIsZero() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);

        assertThatThrownBy(() -> marginAccountService.deposit(1L, BigDecimal.ZERO, auth))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Amount must be a positive number.");
    }

    @Test
    void deposit_throwsWhenAmountIsNegative() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);

        assertThatThrownBy(() -> marginAccountService.deposit(1L, new BigDecimal("-100"), auth))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Amount must be a positive number.");
    }

    @Test
    void deposit_throwsWhenClientNotFound() {
        Authentication auth = authenticatedAs("unknown@test.com");
        when(clientRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> marginAccountService.deposit(1L, new BigDecimal("100"), auth))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Only clients can deposit on margin accounts.");
    }

    @Test
    void deposit_throwsWhenMarginAccountNotFound() {
        Authentication auth = authenticatedAs("client@test.com");
        Client client = Client.builder().id(10L).email("client@test.com").build();
        when(clientRepository.findByEmail("client@test.com")).thenReturn(Optional.of(client));
        when(marginAccountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> marginAccountService.deposit(99L, new BigDecimal("100"), auth))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void deposit_throwsWhenCallerIsNotOwner() {
        Authentication auth = authenticatedAs("attacker@test.com");
        Client attacker = Client.builder().id(20L).email("attacker@test.com").build();
        MarginAccount account = activeMarginAccount(10L, "10000", "5000", MarginAccountStatus.ACTIVE);

        when(clientRepository.findByEmail("attacker@test.com")).thenReturn(Optional.of(attacker));
        when(marginAccountRepository.findById(1L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> marginAccountService.deposit(1L, new BigDecimal("100"), auth))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("can deposit funds");
    }

    // ── withdraw() tests ───────────────────────────────────────────────────────

    @Test
    void withdraw_success_decreasesInitialMarginAndRecalculatesMaintenanceMargin() {
        Authentication auth = authenticatedAs("client@test.com");
        Client client = Client.builder().id(10L).email("client@test.com").build();
        MarginAccount account = activeMarginAccount(10L, "10000", "5000", MarginAccountStatus.ACTIVE);

        when(clientRepository.findByEmail("client@test.com")).thenReturn(Optional.of(client));
        when(marginAccountRepository.findById(1L)).thenReturn(Optional.of(account));

        marginAccountService.withdraw(1L, new BigDecimal("2000"), auth);

        assertThat(account.getInitialMargin()).isEqualByComparingTo("8000");
        assertThat(account.getMaintenanceMargin()).isEqualByComparingTo("4000");

        verify(marginAccountRepository).save(account);

        ArgumentCaptor<MarginTransaction> txCaptor = ArgumentCaptor.forClass(MarginTransaction.class);
        verify(marginTransactionRepository).save(txCaptor.capture());
        assertThat(txCaptor.getValue().getType()).isEqualTo(MarginTransactionType.WITHDRAWAL);
        assertThat(txCaptor.getValue().getAmount()).isEqualByComparingTo("2000");
    }

    @Test
    void withdraw_throwsWhenAuthenticationIsNull() {
        assertThatThrownBy(() -> marginAccountService.withdraw(1L, new BigDecimal("100"), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Not authenticated.");
    }

    @Test
    void withdraw_throwsWhenNotAuthenticated() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(false);

        assertThatThrownBy(() -> marginAccountService.withdraw(1L, new BigDecimal("100"), auth))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Not authenticated.");
    }

    @Test
    void withdraw_throwsWhenAmountIsNull() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);

        assertThatThrownBy(() -> marginAccountService.withdraw(1L, null, auth))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Amount must be a positive number.");
    }

    @Test
    void withdraw_throwsWhenAmountIsZero() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);

        assertThatThrownBy(() -> marginAccountService.withdraw(1L, BigDecimal.ZERO, auth))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Amount must be a positive number.");
    }

    @Test
    void withdraw_throwsWhenAmountIsNegative() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);

        assertThatThrownBy(() -> marginAccountService.withdraw(1L, new BigDecimal("-100"), auth))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Amount must be a positive number.");
    }

    @Test
    void withdraw_throwsWhenClientNotFound() {
        Authentication auth = authenticatedAs("unknown@test.com");
        when(clientRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> marginAccountService.withdraw(1L, new BigDecimal("100"), auth))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only clients can withdraw from margin accounts.");
    }

    @Test
    void withdraw_throwsWhenMarginAccountNotFound() {
        Authentication auth = authenticatedAs("client@test.com");
        Client client = Client.builder().id(10L).email("client@test.com").build();
        when(clientRepository.findByEmail("client@test.com")).thenReturn(Optional.of(client));
        when(marginAccountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> marginAccountService.withdraw(99L, new BigDecimal("100"), auth))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void withdraw_throwsWhenCallerIsNotOwner() {
        Authentication auth = authenticatedAs("attacker@test.com");
        Client attacker = Client.builder().id(20L).email("attacker@test.com").build();
        MarginAccount account = activeMarginAccount(10L, "10000", "5000", MarginAccountStatus.ACTIVE);

        when(clientRepository.findByEmail("attacker@test.com")).thenReturn(Optional.of(attacker));
        when(marginAccountRepository.findById(1L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> marginAccountService.withdraw(1L, new BigDecimal("100"), auth))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("can withdraw funds");
    }

    @Test
    void withdraw_throwsWhenAccountIsNotActive() {
        Authentication auth = authenticatedAs("client@test.com");
        Client client = Client.builder().id(10L).email("client@test.com").build();
        MarginAccount account = activeMarginAccount(10L, "10000", "5000", MarginAccountStatus.BLOCKED);

        when(clientRepository.findByEmail("client@test.com")).thenReturn(Optional.of(client));
        when(marginAccountRepository.findById(1L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> marginAccountService.withdraw(1L, new BigDecimal("100"), auth))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is not active");
    }

    @Test
    void withdraw_throwsWhenWithdrawalWouldDropBelowMaintenanceMargin() {
        Authentication auth = authenticatedAs("client@test.com");
        Client client = Client.builder().id(10L).email("client@test.com").build();
        MarginAccount account = activeMarginAccount(10L, "10000", "5000", MarginAccountStatus.ACTIVE);

        when(clientRepository.findByEmail("client@test.com")).thenReturn(Optional.of(client));
        when(marginAccountRepository.findById(1L)).thenReturn(Optional.of(account));

        // 10000 - 6000 = 4000 < 5000 (maintenanceMargin)
        assertThatThrownBy(() -> marginAccountService.withdraw(1L, new BigDecimal("6000"), auth))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Funds in the account cannot be below");
    }

    // ── checkMaintenanceMargin() tests ─────────────────────────────────────────

    @Test
    void checkMaintenanceMargin_doesNothingWhenNoAccountsNeedBlocking() {
        when(marginAccountRepository.findAccountsForMarginCheck("ACTIVE")).thenReturn(List.of());

        marginAccountService.checkMaintenanceMargin();

        verify(marginAccountRepository).blockAccountsWhereMaintenanceExceedsInitial("BLOCKED");
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void checkMaintenanceMargin_blocksAccountsAndPublishesEvent() {
        MarginAccountCheckDto account = new MarginAccountCheckDto(
                1L, "owner@test.com", new BigDecimal("5000"), new BigDecimal("4000")
        );
        when(marginAccountRepository.findAccountsForMarginCheck("ACTIVE")).thenReturn(List.of(account));

        marginAccountService.checkMaintenanceMargin();

        verify(marginAccountRepository).blockAccountsWhereMaintenanceExceedsInitial("BLOCKED");

        ArgumentCaptor<MarginAccountBlockedEvent> eventCaptor =
                ArgumentCaptor.forClass(MarginAccountBlockedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        MarginAccountBlockedEvent event = eventCaptor.getValue();
        assertThat(event.getEmail()).isEqualTo("owner@test.com");
        assertThat(event.getMaintenanceMargin()).isEqualTo("5000");
        assertThat(event.getInitialMargin()).isEqualTo("4000");
        assertThat(event.getDeficit()).isEqualTo("1000"); // 5000 - 4000
    }

    @Test
    void checkMaintenanceMargin_publishesOneEventPerBlockedAccount() {
        List<MarginAccountCheckDto> accounts = List.of(
                new MarginAccountCheckDto(1L, "a@test.com", new BigDecimal("5000"), new BigDecimal("4000")),
                new MarginAccountCheckDto(2L, "b@test.com", new BigDecimal("6000"), new BigDecimal("3000")),
                new MarginAccountCheckDto(3L, "c@test.com", new BigDecimal("7000"), new BigDecimal("2000"))
        );
        when(marginAccountRepository.findAccountsForMarginCheck("ACTIVE")).thenReturn(accounts);

        marginAccountService.checkMaintenanceMargin();

        verify(eventPublisher, times(3)).publishEvent(any(MarginAccountBlockedEvent.class));
    }

    // ── getTransactions() tests ────────────────────────────────────────────────

    @Test
    void getTransactions_success_returnsTransactionsSortedNewestFirst() {
        Authentication auth = authenticatedAs("client@test.com");
        Client client = Client.builder().id(10L).email("client@test.com").build();
        MarginAccount account = activeMarginAccount(10L, "10000", "5000", MarginAccountStatus.ACTIVE);

        MarginTransaction tx1 = MarginTransaction.builder()
                .id(1L)
                .marginAccount(account)
                .type(MarginTransactionType.DEPOSIT)
                .amount(new BigDecimal("1000"))
                .createdAt(LocalDateTime.now().minusHours(1))
                .build();
        MarginTransaction tx2 = MarginTransaction.builder()
                .id(2L)
                .marginAccount(account)
                .type(MarginTransactionType.WITHDRAWAL)
                .amount(new BigDecimal("500"))
                .createdAt(LocalDateTime.now())
                .build();

        when(clientRepository.findByEmail("client@test.com")).thenReturn(Optional.of(client));
        when(marginAccountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(marginTransactionRepository.findByMarginAccountIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(tx2, tx1));

        var result = marginAccountService.getTransactions(1L, auth);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getType()).isEqualTo("WITHDRAWAL");
        assertThat(result.get(0).getAmount()).isEqualByComparingTo("500");
    }

    @Test
    void getTransactions_success_returnsEmptyListWhenNoTransactions() {
        Authentication auth = authenticatedAs("client@test.com");
        Client client = Client.builder().id(10L).email("client@test.com").build();
        MarginAccount account = activeMarginAccount(10L, "10000", "5000", MarginAccountStatus.ACTIVE);

        when(clientRepository.findByEmail("client@test.com")).thenReturn(Optional.of(client));
        when(marginAccountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(marginTransactionRepository.findByMarginAccountIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of());

        var result = marginAccountService.getTransactions(1L, auth);

        assertThat(result).isEmpty();
    }

    @Test
    void getTransactions_throwsWhenAuthenticationIsNull() {
        assertThatThrownBy(() -> marginAccountService.getTransactions(1L, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Not authenticated.");
    }

    @Test
    void getTransactions_throwsWhenNotAuthenticated() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(false);

        assertThatThrownBy(() -> marginAccountService.getTransactions(1L, auth))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Not authenticated.");
    }

    @Test
    void getTransactions_throwsWhenClientNotFound() {
        Authentication auth = authenticatedAs("unknown@test.com");
        when(clientRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> marginAccountService.getTransactions(1L, auth))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only clients can get the list of margin transactions.");
    }

    @Test
    void getTransactions_throwsWhenMarginAccountNotFound() {
        Authentication auth = authenticatedAs("client@test.com");
        Client client = Client.builder().id(10L).email("client@test.com").build();
        when(clientRepository.findByEmail("client@test.com")).thenReturn(Optional.of(client));
        when(marginAccountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> marginAccountService.getTransactions(99L, auth))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void getTransactions_throwsWhenCallerIsNotOwner() {
        Authentication auth = authenticatedAs("attacker@test.com");
        Client attacker = Client.builder().id(20L).email("attacker@test.com").build();
        MarginAccount account = activeMarginAccount(10L, "10000", "5000", MarginAccountStatus.ACTIVE);

        when(clientRepository.findByEmail("attacker@test.com")).thenReturn(Optional.of(attacker));
        when(marginAccountRepository.findById(1L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> marginAccountService.getTransactions(1L, auth))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("can access margin account transactions");
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private Account activeAccount(Long accountId, Long clientId, String available, String balance) {
        Client client = Client.builder().id(clientId).email("client@test.com").build();
        return Account.builder()
                .id(accountId)
                .client(client)
                .status(AccountStatus.ACTIVE)
                .availableBalance(new BigDecimal(available))
                .balance(new BigDecimal(balance))
                .build();
    }

    private MarginAccount activeMarginAccount(Long userId, String initialMargin,
                                              String maintenanceMargin, MarginAccountStatus status) {
        return MarginAccount.builder()
                .id(1L)
                .userId(userId)
                .initialMargin(new BigDecimal(initialMargin))
                .maintenanceMargin(new BigDecimal(maintenanceMargin))
                .bankParticipation(new BigDecimal("0.50"))
                .status(status)
                .build();
    }

    private Authentication authenticatedAs(String email) {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn(email);
        return auth;
    }
}

