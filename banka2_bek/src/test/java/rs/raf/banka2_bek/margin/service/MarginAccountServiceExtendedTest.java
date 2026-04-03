package rs.raf.banka2_bek.margin.service;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import rs.raf.banka2_bek.margin.dto.MarginAccountDto;
import rs.raf.banka2_bek.margin.dto.MarginTransactionDto;
import rs.raf.banka2_bek.margin.model.MarginAccount;
import rs.raf.banka2_bek.margin.model.MarginAccountStatus;
import rs.raf.banka2_bek.margin.model.MarginTransaction;
import rs.raf.banka2_bek.margin.model.MarginTransactionType;
import rs.raf.banka2_bek.margin.repository.MarginAccountRepository;
import rs.raf.banka2_bek.margin.repository.MarginTransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Extended tests for MarginAccountService — covers remaining branches
 * not exercised by MarginAccountServiceTest.
 */
@ExtendWith(MockitoExtension.class)
class MarginAccountServiceExtendedTest {

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

    private MarginAccountService service;

    @BeforeEach
    void setUp() {
        service = new MarginAccountService(
                marginAccountRepository,
                marginTransactionRepository,
                accountRepository,
                clientRepository,
                eventPublisher
        );
    }

    // ── createForUser — missing branch coverage ───────────────────────────────

    @Nested
    @DisplayName("createForUser - DTO validation branches")
    class CreateForUserDtoBranches {

        @Test
        @DisplayName("throws when dto is null")
        void throwsWhenDtoIsNull() {
            assertThatThrownBy(() -> service.createForUser(10L, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Account id and initial deposit are required.");
        }

        @Test
        @DisplayName("throws when dto.accountId is null")
        void throwsWhenAccountIdIsNull() {
            CreateMarginAccountDto dto = new CreateMarginAccountDto(null, new BigDecimal("100"));

            assertThatThrownBy(() -> service.createForUser(10L, dto))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Account id and initial deposit are required.");
        }

        @Test
        @DisplayName("throws when dto.initialDeposit is null")
        void throwsWhenInitialDepositIsNull() {
            CreateMarginAccountDto dto = new CreateMarginAccountDto(1L, null);

            assertThatThrownBy(() -> service.createForUser(10L, dto))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Account id and initial deposit are required.");
        }

        @Test
        @DisplayName("throws when initial deposit is negative")
        void throwsWhenDepositNegative() {
            CreateMarginAccountDto dto = new CreateMarginAccountDto(1L, new BigDecimal("-500"));

            assertThatThrownBy(() -> service.createForUser(10L, dto))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Initial deposit must be greater than zero.");
        }

        @Test
        @DisplayName("throws when account has null client")
        void throwsWhenAccountClientIsNull() {
            Account account = Account.builder()
                    .id(1L)
                    .client(null)
                    .status(AccountStatus.ACTIVE)
                    .availableBalance(new BigDecimal("10000"))
                    .balance(new BigDecimal("10000"))
                    .build();

            CreateMarginAccountDto dto = new CreateMarginAccountDto(1L, new BigDecimal("100"));
            when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(account));

            assertThatThrownBy(() -> service.createForUser(10L, dto))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("You are not allowed to create a margin account for this base account.");
        }

        @Test
        @DisplayName("handles null availableBalance as zero — insufficient for any deposit")
        void handlesNullAvailableBalance() {
            Client client = Client.builder().id(10L).email("client@test.com").build();
            Account account = Account.builder()
                    .id(1L)
                    .client(client)
                    .status(AccountStatus.ACTIVE)
                    .availableBalance(null)
                    .balance(new BigDecimal("10000"))
                    .build();

            CreateMarginAccountDto dto = new CreateMarginAccountDto(1L, new BigDecimal("100"));
            when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(account));
            when(marginAccountRepository.findByAccountId(1L)).thenReturn(List.of());

            assertThatThrownBy(() -> service.createForUser(10L, dto))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Insufficient available balance for initial margin deposit.");
        }

        @Test
        @DisplayName("handles null balance on base account during deduction")
        void handlesNullBalanceOnAccount() {
            Client client = Client.builder().id(10L).email("client@test.com").build();
            Account account = Account.builder()
                    .id(1L)
                    .client(client)
                    .status(AccountStatus.ACTIVE)
                    .availableBalance(new BigDecimal("10000"))
                    .balance(null) // null balance branch
                    .build();

            CreateMarginAccountDto dto = new CreateMarginAccountDto(1L, new BigDecimal("5000"));
            when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(account));
            when(marginAccountRepository.findByAccountId(1L)).thenReturn(List.of());
            when(marginAccountRepository.save(any(MarginAccount.class))).thenAnswer(inv -> {
                MarginAccount ma = inv.getArgument(0);
                ma.setId(1L);
                ma.setCreatedAt(LocalDateTime.now());
                return ma;
            });

            MarginAccountDto result = service.createForUser(10L, dto);

            assertThat(result).isNotNull();
            // balance should remain null (the if-branch was skipped)
            assertThat(account.getBalance()).isNull();
            // availableBalance should be reduced
            assertThat(account.getAvailableBalance()).isEqualByComparingTo("5000");
        }
    }

    // ── getMyMarginAccounts — extra branches ──────────────────────────────────

    @Nested
    @DisplayName("getMyMarginAccounts - extra branches")
    class GetMyAccountsBranches {

        @Test
        @DisplayName("throws when email is null")
        void throwsWhenEmailIsNull() {
            assertThatThrownBy(() -> service.getMyMarginAccounts(null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Authenticated user is required.");
        }

        @Test
        @DisplayName("throws when email is blank")
        void throwsWhenEmailIsBlank() {
            assertThatThrownBy(() -> service.getMyMarginAccounts("   "))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Authenticated user is required.");
        }

        @Test
        @DisplayName("returns empty list when client has no margin accounts")
        void returnsEmptyList() {
            Client client = Client.builder().id(10L).email("client@test.com").build();
            when(clientRepository.findByEmail("client@test.com")).thenReturn(Optional.of(client));
            when(marginAccountRepository.findByUserId(10L)).thenReturn(Collections.emptyList());

            List<MarginAccountDto> result = service.getMyMarginAccounts("client@test.com");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns multiple margin accounts for same client")
        void returnsMultipleAccounts() {
            Client client = Client.builder().id(10L).email("client@test.com").build();

            Account acc1 = Account.builder().id(1L).accountNumber("111").build();
            Account acc2 = Account.builder().id(2L).accountNumber("222").build();

            MarginAccount ma1 = MarginAccount.builder()
                    .id(1L).account(acc1).userId(10L)
                    .initialMargin(new BigDecimal("10000")).loanValue(new BigDecimal("5000"))
                    .maintenanceMargin(new BigDecimal("5000")).bankParticipation(new BigDecimal("0.50"))
                    .status(MarginAccountStatus.ACTIVE).build();

            MarginAccount ma2 = MarginAccount.builder()
                    .id(2L).account(acc2).userId(10L)
                    .initialMargin(new BigDecimal("20000")).loanValue(new BigDecimal("10000"))
                    .maintenanceMargin(new BigDecimal("10000")).bankParticipation(new BigDecimal("0.50"))
                    .status(MarginAccountStatus.BLOCKED).build();

            when(clientRepository.findByEmail("client@test.com")).thenReturn(Optional.of(client));
            when(marginAccountRepository.findByUserId(10L)).thenReturn(List.of(ma1, ma2));

            List<MarginAccountDto> result = service.getMyMarginAccounts("client@test.com");

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getAccountNumber()).isEqualTo("111");
            assertThat(result.get(1).getAccountNumber()).isEqualTo("222");
            assertThat(result.get(1).getStatus()).isEqualTo("BLOCKED");
        }
    }

    // ── deposit — additional edge cases ───────────────────────────────────────

    @Nested
    @DisplayName("deposit - edge cases")
    class DepositEdgeCases {

        @Test
        @DisplayName("deposit of exactly 1 (boundary) succeeds")
        void depositExactlyOne() {
            Authentication auth = authenticatedAs("client@test.com");
            Client client = Client.builder().id(10L).email("client@test.com").build();
            MarginAccount account = marginAccount(10L, "5000", "2500", MarginAccountStatus.ACTIVE);

            when(clientRepository.findByEmail("client@test.com")).thenReturn(Optional.of(client));
            when(marginAccountRepository.findById(1L)).thenReturn(Optional.of(account));

            service.deposit(1L, new BigDecimal("1"), auth);

            assertThat(account.getInitialMargin()).isEqualByComparingTo("5001");
            verify(marginAccountRepository).save(account);
            verify(marginTransactionRepository).save(any(MarginTransaction.class));
        }

        @Test
        @DisplayName("large deposit on blocked account reactivates it")
        void largeDepositReactivatesBlocked() {
            Authentication auth = authenticatedAs("client@test.com");
            Client client = Client.builder().id(10L).email("client@test.com").build();
            MarginAccount account = marginAccount(10L, "1000", "2000", MarginAccountStatus.BLOCKED);

            when(clientRepository.findByEmail("client@test.com")).thenReturn(Optional.of(client));
            when(marginAccountRepository.findById(1L)).thenReturn(Optional.of(account));

            service.deposit(1L, new BigDecimal("50000"), auth);

            assertThat(account.getStatus()).isEqualTo(MarginAccountStatus.ACTIVE);
            assertThat(account.getInitialMargin()).isEqualByComparingTo("51000");
            assertThat(account.getMaintenanceMargin()).isEqualByComparingTo("25500");
        }

        @Test
        @DisplayName("deposit transaction description contains correct balance info")
        void depositTransactionDescription() {
            Authentication auth = authenticatedAs("client@test.com");
            Client client = Client.builder().id(10L).email("client@test.com").build();
            MarginAccount account = marginAccount(10L, "10000", "5000", MarginAccountStatus.ACTIVE);

            when(clientRepository.findByEmail("client@test.com")).thenReturn(Optional.of(client));
            when(marginAccountRepository.findById(1L)).thenReturn(Optional.of(account));

            service.deposit(1L, new BigDecimal("3000"), auth);

            var txCaptor = org.mockito.ArgumentCaptor.forClass(MarginTransaction.class);
            verify(marginTransactionRepository).save(txCaptor.capture());

            String desc = txCaptor.getValue().getDescription();
            assertThat(desc).contains("3000");
            assertThat(desc).contains("13000"); // 10000 + 3000
        }
    }

    // ── withdraw — additional edge cases ──────────────────────────────────────

    @Nested
    @DisplayName("withdraw - edge cases")
    class WithdrawEdgeCases {

        @Test
        @DisplayName("withdraw exactly to maintenance margin boundary succeeds")
        void withdrawExactlyToMaintenance() {
            Authentication auth = authenticatedAs("client@test.com");
            Client client = Client.builder().id(10L).email("client@test.com").build();
            // initialMargin=10000, maintenanceMargin=5000 => max withdraw = 10000-5000 = 5000
            MarginAccount account = marginAccount(10L, "10000", "5000", MarginAccountStatus.ACTIVE);

            when(clientRepository.findByEmail("client@test.com")).thenReturn(Optional.of(client));
            when(marginAccountRepository.findById(1L)).thenReturn(Optional.of(account));

            service.withdraw(1L, new BigDecimal("5000"), auth);

            assertThat(account.getInitialMargin()).isEqualByComparingTo("5000");
            // new maintenance = 5000 * 0.5 = 2500
            assertThat(account.getMaintenanceMargin()).isEqualByComparingTo("2500");
            verify(marginAccountRepository).save(account);
        }

        @Test
        @DisplayName("withdraw 1 penny over maintenance boundary fails")
        void withdrawJustOverMaintenanceFails() {
            Authentication auth = authenticatedAs("client@test.com");
            Client client = Client.builder().id(10L).email("client@test.com").build();
            MarginAccount account = marginAccount(10L, "10000", "5000", MarginAccountStatus.ACTIVE);

            when(clientRepository.findByEmail("client@test.com")).thenReturn(Optional.of(client));
            when(marginAccountRepository.findById(1L)).thenReturn(Optional.of(account));

            // 10000 - 5001 = 4999 < 5000 (maintenance) => should fail
            assertThatThrownBy(() -> service.withdraw(1L, new BigDecimal("5001"), auth))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Funds in the account cannot be below");

            verify(marginAccountRepository, never()).save(any());
        }

        @Test
        @DisplayName("withdraw transaction description contains correct amount and balance")
        void withdrawTransactionDescription() {
            Authentication auth = authenticatedAs("client@test.com");
            Client client = Client.builder().id(10L).email("client@test.com").build();
            MarginAccount account = marginAccount(10L, "10000", "5000", MarginAccountStatus.ACTIVE);

            when(clientRepository.findByEmail("client@test.com")).thenReturn(Optional.of(client));
            when(marginAccountRepository.findById(1L)).thenReturn(Optional.of(account));

            service.withdraw(1L, new BigDecimal("2000"), auth);

            var txCaptor = org.mockito.ArgumentCaptor.forClass(MarginTransaction.class);
            verify(marginTransactionRepository).save(txCaptor.capture());

            String desc = txCaptor.getValue().getDescription();
            assertThat(desc).contains("2000");
            assertThat(desc).contains("8000"); // 10000 - 2000
            assertThat(txCaptor.getValue().getType()).isEqualTo(MarginTransactionType.WITHDRAWAL);
        }

        @Test
        @DisplayName("withdraw from BLOCKED account throws even with small amount")
        void withdrawFromBlockedAccountFails() {
            Authentication auth = authenticatedAs("client@test.com");
            Client client = Client.builder().id(10L).email("client@test.com").build();
            MarginAccount account = marginAccount(10L, "10000", "5000", MarginAccountStatus.BLOCKED);

            when(clientRepository.findByEmail("client@test.com")).thenReturn(Optional.of(client));
            when(marginAccountRepository.findById(1L)).thenReturn(Optional.of(account));

            assertThatThrownBy(() -> service.withdraw(1L, new BigDecimal("1"), auth))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("is not active");
        }
    }

    // ── getTransactions — extra branches ──────────────────────────────────────

    @Nested
    @DisplayName("getTransactions - extra branches")
    class GetTransactionsExtended {

        @Test
        @DisplayName("returns transactions with all DTO fields mapped correctly")
        void mapsAllFieldsCorrectly() {
            Authentication auth = authenticatedAs("client@test.com");
            Client client = Client.builder().id(10L).email("client@test.com").build();
            MarginAccount account = marginAccount(10L, "10000", "5000", MarginAccountStatus.ACTIVE);

            LocalDateTime now = LocalDateTime.of(2026, 4, 1, 12, 0);
            MarginTransaction tx = MarginTransaction.builder()
                    .id(42L)
                    .marginAccount(account)
                    .type(MarginTransactionType.DEPOSIT)
                    .amount(new BigDecimal("1500"))
                    .description("Test deposit")
                    .createdAt(now)
                    .build();

            when(clientRepository.findByEmail("client@test.com")).thenReturn(Optional.of(client));
            when(marginAccountRepository.findById(1L)).thenReturn(Optional.of(account));
            when(marginTransactionRepository.findByMarginAccountIdOrderByCreatedAtDesc(1L))
                    .thenReturn(List.of(tx));

            List<MarginTransactionDto> result = service.getTransactions(1L, auth);

            assertThat(result).hasSize(1);
            MarginTransactionDto dto = result.get(0);
            assertThat(dto.getId()).isEqualTo(42L);
            assertThat(dto.getMarginAccountId()).isEqualTo(1L);
            assertThat(dto.getType()).isEqualTo("DEPOSIT");
            assertThat(dto.getAmount()).isEqualByComparingTo("1500");
            assertThat(dto.getDescription()).isEqualTo("Test deposit");
            assertThat(dto.getCreatedAt()).isEqualTo(now);
        }

        @Test
        @DisplayName("maps transaction with null marginAccount gracefully")
        void handlesNullMarginAccountInTransaction() {
            Authentication auth = authenticatedAs("client@test.com");
            Client client = Client.builder().id(10L).email("client@test.com").build();
            MarginAccount account = marginAccount(10L, "10000", "5000", MarginAccountStatus.ACTIVE);

            MarginTransaction tx = MarginTransaction.builder()
                    .id(1L)
                    .marginAccount(null) // edge: null margin account reference
                    .type(MarginTransactionType.WITHDRAWAL)
                    .amount(new BigDecimal("100"))
                    .build();

            when(clientRepository.findByEmail("client@test.com")).thenReturn(Optional.of(client));
            when(marginAccountRepository.findById(1L)).thenReturn(Optional.of(account));
            when(marginTransactionRepository.findByMarginAccountIdOrderByCreatedAtDesc(1L))
                    .thenReturn(List.of(tx));

            List<MarginTransactionDto> result = service.getTransactions(1L, auth);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getMarginAccountId()).isNull();
        }

        @Test
        @DisplayName("maps transaction with null type gracefully")
        void handlesNullTypeInTransaction() {
            Authentication auth = authenticatedAs("client@test.com");
            Client client = Client.builder().id(10L).email("client@test.com").build();
            MarginAccount account = marginAccount(10L, "10000", "5000", MarginAccountStatus.ACTIVE);

            MarginTransaction tx = MarginTransaction.builder()
                    .id(1L)
                    .marginAccount(account)
                    .type(null) // edge: null type
                    .amount(new BigDecimal("100"))
                    .build();

            when(clientRepository.findByEmail("client@test.com")).thenReturn(Optional.of(client));
            when(marginAccountRepository.findById(1L)).thenReturn(Optional.of(account));
            when(marginTransactionRepository.findByMarginAccountIdOrderByCreatedAtDesc(1L))
                    .thenReturn(List.of(tx));

            List<MarginTransactionDto> result = service.getTransactions(1L, auth);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getType()).isNull();
        }
    }

    // ── toDto (MarginAccount) — null branches ─────────────────────────────────

    @Nested
    @DisplayName("toDto - null-safe branches")
    class ToDtoNullBranches {

        @Test
        @DisplayName("toDto handles margin account with null account reference")
        void toDtoNullAccount() {
            Client client = Client.builder().id(10L).email("client@test.com").build();

            MarginAccount ma = MarginAccount.builder()
                    .id(1L)
                    .account(null) // null account
                    .userId(10L)
                    .initialMargin(new BigDecimal("10000"))
                    .loanValue(new BigDecimal("5000"))
                    .maintenanceMargin(new BigDecimal("5000"))
                    .bankParticipation(new BigDecimal("0.50"))
                    .status(null) // null status too
                    .build();

            when(clientRepository.findByEmail("client@test.com")).thenReturn(Optional.of(client));
            when(marginAccountRepository.findByUserId(10L)).thenReturn(List.of(ma));

            List<MarginAccountDto> result = service.getMyMarginAccounts("client@test.com");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getAccountId()).isNull();
            assertThat(result.get(0).getAccountNumber()).isNull();
            assertThat(result.get(0).getStatus()).isNull();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private MarginAccount marginAccount(Long userId, String initialMargin,
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
