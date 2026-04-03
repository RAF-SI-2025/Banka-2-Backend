package rs.raf.banka2_bek.transfers.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import rs.raf.banka2_bek.payment.model.PaymentStatus;
import rs.raf.banka2_bek.transfer.model.Transfer;
import rs.raf.banka2_bek.transfers.dto.TransferFxRequestDto;
import rs.raf.banka2_bek.transfers.dto.TransferInternalRequestDto;
import rs.raf.banka2_bek.transfers.dto.TransferResponseDto;
import rs.raf.banka2_bek.transfers.repository.TransferRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Extended tests for TransferService - covers additional scenarios.
 */
@ExtendWith(MockitoExtension.class)
class TransferServiceExtendedTest {

    @Mock private TransferRepository transferRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private ExchangeService exchangeService;
    @Mock private ClientRepository clientRepository;

    private TransferService transferService;

    private Account fromAccount;
    private Account toAccount;
    private Client client;
    private Currency rsd;
    private Currency eur;

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

    @BeforeEach
    void setUp() throws Exception {
        SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_GLOBAL);
        transferService = new TransferService(
                transferRepository, accountRepository, exchangeService, clientRepository);
        java.lang.reflect.Field field = TransferService.class.getDeclaredField("bankRegistrationNumber");
        field.setAccessible(true);
        field.set(transferService, "22200022");

        rsd = new Currency();
        rsd.setId(1L);
        rsd.setCode("RSD");

        eur = new Currency();
        eur.setId(2L);
        eur.setCode("EUR");

        client = new Client();
        client.setId(1L);
        client.setFirstName("Ana");
        client.setLastName("Petrovic");
        client.setEmail("ana@test.com");

        fromAccount = new Account();
        fromAccount.setAccountNumber("111111111111111111");
        fromAccount.setClient(client);
        fromAccount.setCurrency(rsd);
        fromAccount.setAvailableBalance(new BigDecimal("50000"));
        fromAccount.setBalance(new BigDecimal("50000"));
        fromAccount.setStatus(AccountStatus.ACTIVE);

        toAccount = new Account();
        toAccount.setAccountNumber("222222222222222222");
        toAccount.setClient(client);
        toAccount.setCurrency(rsd);
        toAccount.setAvailableBalance(new BigDecimal("10000"));
        toAccount.setBalance(new BigDecimal("10000"));
        toAccount.setStatus(AccountStatus.ACTIVE);

        authenticateAs("ana@test.com");
        lenient().when(clientRepository.findByEmail("ana@test.com")).thenReturn(Optional.of(client));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ===== Auto-detect FX when currencies differ =====

    @Nested
    @DisplayName("internalTransfer auto-detects FX")
    class AutoDetectFx {

        @Test
        @DisplayName("redirects to FX transfer when currencies differ")
        void autoRedirectsToFx() {
            toAccount.setCurrency(eur);

            Account bankRsd = new Account();
            bankRsd.setAccountNumber("BANK-RSD");
            bankRsd.setCurrency(rsd);
            bankRsd.setBalance(new BigDecimal("1000000"));
            bankRsd.setAvailableBalance(new BigDecimal("1000000"));
            bankRsd.setStatus(AccountStatus.ACTIVE);

            Account bankEur = new Account();
            bankEur.setAccountNumber("BANK-EUR");
            bankEur.setCurrency(eur);
            bankEur.setBalance(new BigDecimal("1000000"));
            bankEur.setAvailableBalance(new BigDecimal("1000000"));
            bankEur.setStatus(AccountStatus.ACTIVE);

            when(accountRepository.findForUpdateByAccountNumber("111111111111111111")).thenReturn(Optional.of(fromAccount));
            when(accountRepository.findForUpdateByAccountNumber("222222222222222222")).thenReturn(Optional.of(toAccount));
            when(accountRepository.findBankAccountForUpdateByCurrency("22200022", "RSD")).thenReturn(Optional.of(bankRsd));
            when(accountRepository.findBankAccountForUpdateByCurrency("22200022", "EUR")).thenReturn(Optional.of(bankEur));
            when(exchangeService.calculateCross(5000.0, "RSD", "EUR"))
                    .thenReturn(new CalculateExchangeResponseDto(42.5, 0.0085, "RSD", "EUR"));
            when(transferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            TransferInternalRequestDto request = new TransferInternalRequestDto();
            request.setFromAccountNumber("111111111111111111");
            request.setToAccountNumber("222222222222222222");
            request.setAmount(new BigDecimal("5000"));

            TransferResponseDto response = transferService.internalTransfer(request);

            assertThat(response.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            // Should have used exchange service (FX path)
            verify(exchangeService).calculateCross(5000.0, "RSD", "EUR");
        }
    }

    // ===== FX transfer commission calculation =====

    @Nested
    @DisplayName("fxTransfer commission")
    class FxTransferCommission {

        @Test
        @DisplayName("commission is 0.5% of transfer amount")
        void commissionCalculation() {
            toAccount.setCurrency(eur);

            Account bankRsd = new Account();
            bankRsd.setAccountNumber("BANK-RSD");
            bankRsd.setCurrency(rsd);
            bankRsd.setBalance(new BigDecimal("1000000"));
            bankRsd.setAvailableBalance(new BigDecimal("1000000"));
            bankRsd.setStatus(AccountStatus.ACTIVE);

            Account bankEur = new Account();
            bankEur.setAccountNumber("BANK-EUR");
            bankEur.setCurrency(eur);
            bankEur.setBalance(new BigDecimal("1000000"));
            bankEur.setAvailableBalance(new BigDecimal("1000000"));
            bankEur.setStatus(AccountStatus.ACTIVE);

            when(accountRepository.findForUpdateByAccountNumber("111111111111111111")).thenReturn(Optional.of(fromAccount));
            when(accountRepository.findForUpdateByAccountNumber("222222222222222222")).thenReturn(Optional.of(toAccount));
            when(accountRepository.findBankAccountForUpdateByCurrency("22200022", "RSD")).thenReturn(Optional.of(bankRsd));
            when(accountRepository.findBankAccountForUpdateByCurrency("22200022", "EUR")).thenReturn(Optional.of(bankEur));
            when(exchangeService.calculateCross(10000.0, "RSD", "EUR"))
                    .thenReturn(new CalculateExchangeResponseDto(85.0, 0.0085, "RSD", "EUR"));
            when(transferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            TransferFxRequestDto request = new TransferFxRequestDto();
            request.setFromAccountNumber("111111111111111111");
            request.setToAccountNumber("222222222222222222");
            request.setAmount(new BigDecimal("10000"));

            TransferResponseDto response = transferService.fxTransfer(request);

            // Commission = 0.5% of 10000 = 50
            assertThat(response.getCommission()).isEqualByComparingTo("50.00");
            // Client pays 10000 + 50 = 10050
            assertThat(fromAccount.getBalance()).isEqualByComparingTo("39950");
            // Bank receives 10050 in RSD
            assertThat(bankRsd.getBalance()).isEqualByComparingTo("1010050");
        }

        @Test
        @DisplayName("fails when bank has insufficient target currency reserves")
        void bankInsufficientReserves() {
            toAccount.setCurrency(eur);

            Account bankRsd = new Account();
            bankRsd.setAccountNumber("BANK-RSD");
            bankRsd.setCurrency(rsd);
            bankRsd.setBalance(new BigDecimal("1000000"));
            bankRsd.setAvailableBalance(new BigDecimal("1000000"));
            bankRsd.setStatus(AccountStatus.ACTIVE);

            Account bankEur = new Account();
            bankEur.setAccountNumber("BANK-EUR");
            bankEur.setCurrency(eur);
            bankEur.setBalance(new BigDecimal("1")); // very low
            bankEur.setAvailableBalance(new BigDecimal("1"));
            bankEur.setStatus(AccountStatus.ACTIVE);

            when(accountRepository.findForUpdateByAccountNumber("111111111111111111")).thenReturn(Optional.of(fromAccount));
            when(accountRepository.findForUpdateByAccountNumber("222222222222222222")).thenReturn(Optional.of(toAccount));
            when(accountRepository.findBankAccountForUpdateByCurrency("22200022", "RSD")).thenReturn(Optional.of(bankRsd));
            when(accountRepository.findBankAccountForUpdateByCurrency("22200022", "EUR")).thenReturn(Optional.of(bankEur));
            when(exchangeService.calculateCross(10000.0, "RSD", "EUR"))
                    .thenReturn(new CalculateExchangeResponseDto(85.0, 0.0085, "RSD", "EUR"));

            TransferFxRequestDto request = new TransferFxRequestDto();
            request.setFromAccountNumber("111111111111111111");
            request.setToAccountNumber("222222222222222222");
            request.setAmount(new BigDecimal("10000"));

            assertThatThrownBy(() -> transferService.fxTransfer(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("does not have enough");
        }

        @Test
        @DisplayName("fails when insufficient funds including commission")
        void insufficientFundsWithCommission() {
            // Balance is 50000, amount is 49800, commission is 249 -> total 50049 > 50000
            toAccount.setCurrency(eur);

            when(accountRepository.findForUpdateByAccountNumber("111111111111111111")).thenReturn(Optional.of(fromAccount));
            when(accountRepository.findForUpdateByAccountNumber("222222222222222222")).thenReturn(Optional.of(toAccount));

            TransferFxRequestDto request = new TransferFxRequestDto();
            request.setFromAccountNumber("111111111111111111");
            request.setToAccountNumber("222222222222222222");
            request.setAmount(new BigDecimal("49800"));

            assertThatThrownBy(() -> transferService.fxTransfer(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Nedovoljno sredstava");
        }
    }

    // ===== Destination account inactive =====

    @Nested
    @DisplayName("internalTransfer destination inactive")
    class DestinationInactive {

        @Test
        @DisplayName("fails when destination account is inactive")
        void destinationInactive() {
            toAccount.setStatus(AccountStatus.INACTIVE);

            when(accountRepository.findForUpdateByAccountNumber("111111111111111111")).thenReturn(Optional.of(fromAccount));
            when(accountRepository.findForUpdateByAccountNumber("222222222222222222")).thenReturn(Optional.of(toAccount));

            TransferInternalRequestDto request = new TransferInternalRequestDto();
            request.setFromAccountNumber("111111111111111111");
            request.setToAccountNumber("222222222222222222");
            request.setAmount(new BigDecimal("1000"));

            assertThatThrownBy(() -> transferService.internalTransfer(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Destination account is not active");
        }
    }

    // ===== Account not found =====

    @Nested
    @DisplayName("Account not found scenarios")
    class AccountNotFound {

        @Test
        @DisplayName("internalTransfer fails when from account not found")
        void fromAccountNotFound() {
            when(accountRepository.findForUpdateByAccountNumber("111111111111111111")).thenReturn(Optional.empty());

            TransferInternalRequestDto request = new TransferInternalRequestDto();
            request.setFromAccountNumber("111111111111111111");
            request.setToAccountNumber("222222222222222222");
            request.setAmount(new BigDecimal("1000"));

            assertThatThrownBy(() -> transferService.internalTransfer(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("From account not found");
        }

        @Test
        @DisplayName("internalTransfer fails when to account not found")
        void toAccountNotFound() {
            when(accountRepository.findForUpdateByAccountNumber("111111111111111111")).thenReturn(Optional.of(fromAccount));
            when(accountRepository.findForUpdateByAccountNumber("222222222222222222")).thenReturn(Optional.empty());

            TransferInternalRequestDto request = new TransferInternalRequestDto();
            request.setFromAccountNumber("111111111111111111");
            request.setToAccountNumber("222222222222222222");
            request.setAmount(new BigDecimal("1000"));

            assertThatThrownBy(() -> transferService.internalTransfer(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("To account not found");
        }

        @Test
        @DisplayName("fxTransfer fails when bank source currency account not found")
        void bankSourceAccountNotFound() {
            toAccount.setCurrency(eur);

            when(accountRepository.findForUpdateByAccountNumber("111111111111111111")).thenReturn(Optional.of(fromAccount));
            when(accountRepository.findForUpdateByAccountNumber("222222222222222222")).thenReturn(Optional.of(toAccount));
            when(accountRepository.findBankAccountForUpdateByCurrency("22200022", "RSD")).thenReturn(Optional.empty());

            TransferFxRequestDto request = new TransferFxRequestDto();
            request.setFromAccountNumber("111111111111111111");
            request.setToAccountNumber("222222222222222222");
            request.setAmount(new BigDecimal("1000"));

            assertThatThrownBy(() -> transferService.fxTransfer(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Bank account for RSD not found");
        }

        @Test
        @DisplayName("fxTransfer fails when bank target currency account not found")
        void bankTargetAccountNotFound() {
            toAccount.setCurrency(eur);

            Account bankRsd = new Account();
            bankRsd.setAccountNumber("BANK-RSD");
            bankRsd.setCurrency(rsd);
            bankRsd.setBalance(new BigDecimal("1000000"));
            bankRsd.setAvailableBalance(new BigDecimal("1000000"));
            bankRsd.setStatus(AccountStatus.ACTIVE);

            when(accountRepository.findForUpdateByAccountNumber("111111111111111111")).thenReturn(Optional.of(fromAccount));
            when(accountRepository.findForUpdateByAccountNumber("222222222222222222")).thenReturn(Optional.of(toAccount));
            when(accountRepository.findBankAccountForUpdateByCurrency("22200022", "RSD")).thenReturn(Optional.of(bankRsd));
            when(accountRepository.findBankAccountForUpdateByCurrency("22200022", "EUR")).thenReturn(Optional.empty());

            TransferFxRequestDto request = new TransferFxRequestDto();
            request.setFromAccountNumber("111111111111111111");
            request.setToAccountNumber("222222222222222222");
            request.setAmount(new BigDecimal("1000"));

            assertThatThrownBy(() -> transferService.fxTransfer(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Bank account for EUR not found");
        }
    }

    // ===== Authentication failures =====

    @Nested
    @DisplayName("Authentication failures")
    class AuthenticationFailures {

        @Test
        @DisplayName("internalTransfer fails when not authenticated")
        void notAuthenticated() {
            SecurityContextHolder.clearContext();

            when(accountRepository.findForUpdateByAccountNumber("111111111111111111")).thenReturn(Optional.of(fromAccount));
            when(accountRepository.findForUpdateByAccountNumber("222222222222222222")).thenReturn(Optional.of(toAccount));

            TransferInternalRequestDto request = new TransferInternalRequestDto();
            request.setFromAccountNumber("111111111111111111");
            request.setToAccountNumber("222222222222222222");
            request.setAmount(new BigDecimal("1000"));

            assertThatThrownBy(() -> transferService.internalTransfer(request))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("fxTransfer fails when not authenticated")
        void fxNotAuthenticated() {
            SecurityContextHolder.clearContext();
            toAccount.setCurrency(eur);

            when(accountRepository.findForUpdateByAccountNumber("111111111111111111")).thenReturn(Optional.of(fromAccount));
            when(accountRepository.findForUpdateByAccountNumber("222222222222222222")).thenReturn(Optional.of(toAccount));

            TransferFxRequestDto request = new TransferFxRequestDto();
            request.setFromAccountNumber("111111111111111111");
            request.setToAccountNumber("222222222222222222");
            request.setAmount(new BigDecimal("1000"));

            assertThatThrownBy(() -> transferService.fxTransfer(request))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    // ===== getAllTransfers with filters =====

    @Nested
    @DisplayName("getAllTransfers with filters")
    class GetAllTransfersFiltered {

        @Test
        @DisplayName("filters by account number")
        void filtersByAccountNumber() {
            Transfer t1 = new Transfer();
            t1.setFromAccount(fromAccount);
            t1.setToAccount(toAccount);
            t1.setFromAmount(new BigDecimal("1000"));
            t1.setFromCurrency(rsd);
            t1.setToCurrency(rsd);
            t1.setCommission(BigDecimal.ZERO);
            t1.setStatus(PaymentStatus.COMPLETED);
            t1.setCreatedBy(client);
            t1.setCreatedAt(LocalDateTime.now());

            Account otherAccount = new Account();
            otherAccount.setAccountNumber("333333333333333333");
            otherAccount.setCurrency(rsd);

            Transfer t2 = new Transfer();
            t2.setFromAccount(otherAccount);
            t2.setToAccount(toAccount);
            t2.setFromAmount(new BigDecimal("2000"));
            t2.setFromCurrency(rsd);
            t2.setToCurrency(rsd);
            t2.setCommission(BigDecimal.ZERO);
            t2.setStatus(PaymentStatus.COMPLETED);
            t2.setCreatedBy(client);
            t2.setCreatedAt(LocalDateTime.now());

            when(transferRepository.findByCreatedByOrderByCreatedAtDesc(client))
                    .thenReturn(List.of(t1, t2));

            List<TransferResponseDto> result = transferService.getAllTransfers(client, "111111111111111111", null, null);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getFromAccountNumber()).isEqualTo("111111111111111111");
        }

        @Test
        @DisplayName("filters by date range")
        void filtersByDateRange() {
            Transfer t1 = new Transfer();
            t1.setFromAccount(fromAccount);
            t1.setToAccount(toAccount);
            t1.setFromAmount(new BigDecimal("1000"));
            t1.setFromCurrency(rsd);
            t1.setToCurrency(rsd);
            t1.setCommission(BigDecimal.ZERO);
            t1.setStatus(PaymentStatus.COMPLETED);
            t1.setCreatedBy(client);
            t1.setCreatedAt(LocalDateTime.of(2026, 3, 15, 10, 0));

            Transfer t2 = new Transfer();
            t2.setFromAccount(fromAccount);
            t2.setToAccount(toAccount);
            t2.setFromAmount(new BigDecimal("2000"));
            t2.setFromCurrency(rsd);
            t2.setToCurrency(rsd);
            t2.setCommission(BigDecimal.ZERO);
            t2.setStatus(PaymentStatus.COMPLETED);
            t2.setCreatedBy(client);
            t2.setCreatedAt(LocalDateTime.of(2026, 4, 1, 10, 0));

            when(transferRepository.findByCreatedByOrderByCreatedAtDesc(client))
                    .thenReturn(List.of(t1, t2));

            List<TransferResponseDto> result = transferService.getAllTransfers(
                    client, null, LocalDate.of(2026, 3, 20), LocalDate.of(2026, 4, 5));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getAmount()).isEqualByComparingTo("2000");
        }

        @Test
        @DisplayName("returns all when no filters applied")
        void noFilters() {
            Transfer t1 = new Transfer();
            t1.setFromAccount(fromAccount);
            t1.setToAccount(toAccount);
            t1.setFromAmount(new BigDecimal("1000"));
            t1.setFromCurrency(rsd);
            t1.setToCurrency(rsd);
            t1.setCommission(BigDecimal.ZERO);
            t1.setStatus(PaymentStatus.COMPLETED);
            t1.setCreatedBy(client);
            t1.setCreatedAt(LocalDateTime.now());

            when(transferRepository.findByCreatedByOrderByCreatedAtDesc(client))
                    .thenReturn(List.of(t1));

            List<TransferResponseDto> result = transferService.getAllTransfers(client, null, null, null);

            assertThat(result).hasSize(1);
        }
    }

    // ===== fxTransfer destination account inactive =====

    @Nested
    @DisplayName("fxTransfer destination account inactive")
    class FxDestinationInactive {

        @Test
        @DisplayName("fails when destination account is inactive for FX")
        void fxDestinationInactive() {
            toAccount.setCurrency(eur);
            toAccount.setStatus(AccountStatus.INACTIVE);

            when(accountRepository.findForUpdateByAccountNumber("111111111111111111")).thenReturn(Optional.of(fromAccount));
            when(accountRepository.findForUpdateByAccountNumber("222222222222222222")).thenReturn(Optional.of(toAccount));

            TransferFxRequestDto request = new TransferFxRequestDto();
            request.setFromAccountNumber("111111111111111111");
            request.setToAccountNumber("222222222222222222");
            request.setAmount(new BigDecimal("1000"));

            assertThatThrownBy(() -> transferService.fxTransfer(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Destination account is not active");
        }
    }

    // ===== Balance verification after internal transfer =====

    @Nested
    @DisplayName("Balance verification")
    class BalanceVerification {

        @Test
        @DisplayName("both balance and availableBalance update correctly")
        void balanceAndAvailableBalanceMatch() {
            when(accountRepository.findForUpdateByAccountNumber("111111111111111111")).thenReturn(Optional.of(fromAccount));
            when(accountRepository.findForUpdateByAccountNumber("222222222222222222")).thenReturn(Optional.of(toAccount));
            when(transferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            TransferInternalRequestDto request = new TransferInternalRequestDto();
            request.setFromAccountNumber("111111111111111111");
            request.setToAccountNumber("222222222222222222");
            request.setAmount(new BigDecimal("7500"));

            transferService.internalTransfer(request);

            assertThat(fromAccount.getBalance()).isEqualByComparingTo("42500");
            assertThat(fromAccount.getAvailableBalance()).isEqualByComparingTo("42500");
            assertThat(toAccount.getBalance()).isEqualByComparingTo("17500");
            assertThat(toAccount.getAvailableBalance()).isEqualByComparingTo("17500");
        }
    }
}
