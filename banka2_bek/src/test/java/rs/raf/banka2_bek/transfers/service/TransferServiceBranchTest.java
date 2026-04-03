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
import rs.raf.banka2_bek.company.model.AuthorizedPerson;
import rs.raf.banka2_bek.company.model.Company;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.exchange.ExchangeService;
import rs.raf.banka2_bek.exchange.dto.CalculateExchangeResponseDto;
import rs.raf.banka2_bek.payment.model.PaymentStatus;
import rs.raf.banka2_bek.transfers.dto.TransferFxRequestDto;
import rs.raf.banka2_bek.transfers.dto.TransferInternalRequestDto;
import rs.raf.banka2_bek.transfers.dto.TransferResponseDto;
import rs.raf.banka2_bek.transfers.repository.TransferRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Branch-coverage tests for TransferService — targets uncovered branches
 * such as insufficient funds, FX commission math, cross-currency auto-detection,
 * pessimistic locking paths, and bank reserve checks.
 */
@ExtendWith(MockitoExtension.class)
class TransferServiceBranchTest {

    @Mock private TransferRepository transferRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private ExchangeService exchangeService;
    @Mock private ClientRepository clientRepository;

    private TransferService transferService;

    private Client client;
    private Currency rsd;
    private Currency eur;
    private Currency usd;
    private Account fromAccountRsd;
    private Account toAccountRsd;

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

        rsd = new Currency(); rsd.setId(1L); rsd.setCode("RSD");
        eur = new Currency(); eur.setId(2L); eur.setCode("EUR");
        usd = new Currency(); usd.setId(3L); usd.setCode("USD");

        client = new Client();
        client.setId(1L);
        client.setFirstName("Marko");
        client.setLastName("Markovic");
        client.setEmail("marko@test.com");

        fromAccountRsd = new Account();
        fromAccountRsd.setAccountNumber("111111111111111111");
        fromAccountRsd.setClient(client);
        fromAccountRsd.setCurrency(rsd);
        fromAccountRsd.setBalance(new BigDecimal("100000"));
        fromAccountRsd.setAvailableBalance(new BigDecimal("100000"));
        fromAccountRsd.setStatus(AccountStatus.ACTIVE);

        toAccountRsd = new Account();
        toAccountRsd.setAccountNumber("222222222222222222");
        toAccountRsd.setClient(client);
        toAccountRsd.setCurrency(rsd);
        toAccountRsd.setBalance(new BigDecimal("20000"));
        toAccountRsd.setAvailableBalance(new BigDecimal("20000"));
        toAccountRsd.setStatus(AccountStatus.ACTIVE);

        authenticateAs("marko@test.com");
        lenient().when(clientRepository.findByEmail("marko@test.com")).thenReturn(Optional.of(client));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ===== Insufficient funds (internal transfer) =====

    @Nested
    @DisplayName("Insufficient funds scenarios")
    class InsufficientFunds {

        @Test
        @DisplayName("internalTransfer rejects when balance is less than amount")
        void internalTransferInsufficientFunds() {
            fromAccountRsd.setAvailableBalance(new BigDecimal("500"));

            when(accountRepository.findForUpdateByAccountNumber("111111111111111111"))
                    .thenReturn(Optional.of(fromAccountRsd));
            when(accountRepository.findForUpdateByAccountNumber("222222222222222222"))
                    .thenReturn(Optional.of(toAccountRsd));

            TransferInternalRequestDto request = new TransferInternalRequestDto();
            request.setFromAccountNumber("111111111111111111");
            request.setToAccountNumber("222222222222222222");
            request.setAmount(new BigDecimal("1000"));

            assertThatThrownBy(() -> transferService.internalTransfer(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Insufficient funds");
        }

        @Test
        @DisplayName("internalTransfer rejects at exact balance boundary (amount == balance)")
        void internalTransferExactBalanceSucceeds() {
            // Edge case: available balance is exactly equal to amount -> should succeed
            fromAccountRsd.setAvailableBalance(new BigDecimal("1000"));
            fromAccountRsd.setBalance(new BigDecimal("1000"));

            when(accountRepository.findForUpdateByAccountNumber("111111111111111111"))
                    .thenReturn(Optional.of(fromAccountRsd));
            when(accountRepository.findForUpdateByAccountNumber("222222222222222222"))
                    .thenReturn(Optional.of(toAccountRsd));
            when(transferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            TransferInternalRequestDto request = new TransferInternalRequestDto();
            request.setFromAccountNumber("111111111111111111");
            request.setToAccountNumber("222222222222222222");
            request.setAmount(new BigDecimal("1000"));

            TransferResponseDto response = transferService.internalTransfer(request);

            assertThat(response.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            assertThat(fromAccountRsd.getAvailableBalance()).isEqualByComparingTo("0");
        }
    }

    // ===== FX transfer with exact commission calculation (0.5%) =====

    @Nested
    @DisplayName("FX commission calculation")
    class FxCommission {

        @Test
        @DisplayName("commission is exactly 0.5% rounded HALF_UP")
        void commissionExactCalculation() {
            Account fromEur = createAccount("333333333333333333", client, eur,
                    new BigDecimal("10000"), AccountStatus.ACTIVE);
            Account toUsd = createAccount("444444444444444444", client, usd,
                    new BigDecimal("5000"), AccountStatus.ACTIVE);
            Account bankEur = createBankAccount("BANK-EUR", eur, new BigDecimal("999999"));
            Account bankUsd = createBankAccount("BANK-USD", usd, new BigDecimal("999999"));

            when(accountRepository.findForUpdateByAccountNumber("333333333333333333"))
                    .thenReturn(Optional.of(fromEur));
            when(accountRepository.findForUpdateByAccountNumber("444444444444444444"))
                    .thenReturn(Optional.of(toUsd));
            when(accountRepository.findBankAccountForUpdateByCurrency("22200022", "EUR"))
                    .thenReturn(Optional.of(bankEur));
            when(accountRepository.findBankAccountForUpdateByCurrency("22200022", "USD"))
                    .thenReturn(Optional.of(bankUsd));
            // 1000 EUR -> 1085 USD at rate 1.085
            when(exchangeService.calculateCross(1000.0, "EUR", "USD"))
                    .thenReturn(new CalculateExchangeResponseDto(1085.0, 1.085, "EUR", "USD"));
            when(transferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            TransferFxRequestDto request = new TransferFxRequestDto();
            request.setFromAccountNumber("333333333333333333");
            request.setToAccountNumber("444444444444444444");
            request.setAmount(new BigDecimal("1000"));

            TransferResponseDto response = transferService.fxTransfer(request);

            // Commission = 0.5% of 1000 = 5.00
            assertThat(response.getCommission()).isEqualByComparingTo("5.00");
            // Client pays 1000 + 5 = 1005 EUR
            assertThat(fromEur.getBalance()).isEqualByComparingTo("8995");
            // Client receives 1085 USD
            assertThat(toUsd.getBalance()).isEqualByComparingTo("6085");
            // Bank receives 1005 EUR
            assertThat(bankEur.getBalance()).isEqualByComparingTo("1001004");
            // Bank pays 1085 USD
            assertThat(bankUsd.getBalance()).isEqualByComparingTo("998914");
        }

        @Test
        @DisplayName("commission rounds correctly for odd amounts (e.g. 0.5% of 333 = 1.67)")
        void commissionRounding() {
            Account fromEur = createAccount("333333333333333333", client, eur,
                    new BigDecimal("10000"), AccountStatus.ACTIVE);
            Account toUsd = createAccount("444444444444444444", client, usd,
                    new BigDecimal("5000"), AccountStatus.ACTIVE);
            Account bankEur = createBankAccount("BANK-EUR", eur, new BigDecimal("999999"));
            Account bankUsd = createBankAccount("BANK-USD", usd, new BigDecimal("999999"));

            when(accountRepository.findForUpdateByAccountNumber("333333333333333333"))
                    .thenReturn(Optional.of(fromEur));
            when(accountRepository.findForUpdateByAccountNumber("444444444444444444"))
                    .thenReturn(Optional.of(toUsd));
            when(accountRepository.findBankAccountForUpdateByCurrency("22200022", "EUR"))
                    .thenReturn(Optional.of(bankEur));
            when(accountRepository.findBankAccountForUpdateByCurrency("22200022", "USD"))
                    .thenReturn(Optional.of(bankUsd));
            when(exchangeService.calculateCross(333.0, "EUR", "USD"))
                    .thenReturn(new CalculateExchangeResponseDto(361.3, 1.085, "EUR", "USD"));
            when(transferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            TransferFxRequestDto request = new TransferFxRequestDto();
            request.setFromAccountNumber("333333333333333333");
            request.setToAccountNumber("444444444444444444");
            request.setAmount(new BigDecimal("333"));

            TransferResponseDto response = transferService.fxTransfer(request);

            // 0.5% of 333 = 1.665 -> rounds to 1.67 (HALF_UP)
            assertThat(response.getCommission()).isEqualByComparingTo("1.67");
        }
    }

    // ===== Cross-currency auto-detection (EUR->USD triggers FX) =====

    @Nested
    @DisplayName("Cross-currency auto-detection")
    class CrossCurrencyAutoDetect {

        @Test
        @DisplayName("internalTransfer with EUR->USD auto-redirects to fxTransfer")
        void eurToUsdAutoDetectsFx() {
            Account fromEur = createAccount("333333333333333333", client, eur,
                    new BigDecimal("50000"), AccountStatus.ACTIVE);
            Account toUsd = createAccount("444444444444444444", client, usd,
                    new BigDecimal("10000"), AccountStatus.ACTIVE);
            Account bankEur = createBankAccount("BANK-EUR", eur, new BigDecimal("999999"));
            Account bankUsd = createBankAccount("BANK-USD", usd, new BigDecimal("999999"));

            when(accountRepository.findForUpdateByAccountNumber("333333333333333333"))
                    .thenReturn(Optional.of(fromEur));
            when(accountRepository.findForUpdateByAccountNumber("444444444444444444"))
                    .thenReturn(Optional.of(toUsd));
            when(accountRepository.findBankAccountForUpdateByCurrency("22200022", "EUR"))
                    .thenReturn(Optional.of(bankEur));
            when(accountRepository.findBankAccountForUpdateByCurrency("22200022", "USD"))
                    .thenReturn(Optional.of(bankUsd));
            when(exchangeService.calculateCross(2000.0, "EUR", "USD"))
                    .thenReturn(new CalculateExchangeResponseDto(2170.0, 1.085, "EUR", "USD"));
            when(transferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            TransferInternalRequestDto request = new TransferInternalRequestDto();
            request.setFromAccountNumber("333333333333333333");
            request.setToAccountNumber("444444444444444444");
            request.setAmount(new BigDecimal("2000"));

            TransferResponseDto response = transferService.internalTransfer(request);

            // Verify exchange service was called (FX path used)
            verify(exchangeService).calculateCross(2000.0, "EUR", "USD");
            assertThat(response.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            // Commission should be present (FX path)
            assertThat(response.getCommission()).isEqualByComparingTo("10.00");
        }

        @Test
        @DisplayName("internalTransfer with same currency does NOT trigger FX")
        void sameCurrencyNoFx() {
            when(accountRepository.findForUpdateByAccountNumber("111111111111111111"))
                    .thenReturn(Optional.of(fromAccountRsd));
            when(accountRepository.findForUpdateByAccountNumber("222222222222222222"))
                    .thenReturn(Optional.of(toAccountRsd));
            when(transferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            TransferInternalRequestDto request = new TransferInternalRequestDto();
            request.setFromAccountNumber("111111111111111111");
            request.setToAccountNumber("222222222222222222");
            request.setAmount(new BigDecimal("5000"));

            TransferResponseDto response = transferService.internalTransfer(request);

            // Exchange service should NOT be called
            verify(exchangeService, never()).calculateCross(anyDouble(), anyString(), anyString());
            assertThat(response.getCommission()).isEqualByComparingTo("0");
        }
    }

    // ===== Pessimistic locking verification =====

    @Nested
    @DisplayName("Pessimistic locking verification")
    class PessimisticLocking {

        @Test
        @DisplayName("internalTransfer uses findForUpdateByAccountNumber (pessimistic lock)")
        void internalTransferUsesPessimisticLock() {
            when(accountRepository.findForUpdateByAccountNumber("111111111111111111"))
                    .thenReturn(Optional.of(fromAccountRsd));
            when(accountRepository.findForUpdateByAccountNumber("222222222222222222"))
                    .thenReturn(Optional.of(toAccountRsd));
            when(transferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            TransferInternalRequestDto request = new TransferInternalRequestDto();
            request.setFromAccountNumber("111111111111111111");
            request.setToAccountNumber("222222222222222222");
            request.setAmount(new BigDecimal("1000"));

            transferService.internalTransfer(request);

            // Verify pessimistic lock method was called for both accounts
            verify(accountRepository).findForUpdateByAccountNumber("111111111111111111");
            verify(accountRepository).findForUpdateByAccountNumber("222222222222222222");
        }

        @Test
        @DisplayName("fxTransfer locks client accounts AND bank accounts")
        void fxTransferLocksFourAccounts() {
            Account fromEur = createAccount("333333333333333333", client, eur,
                    new BigDecimal("50000"), AccountStatus.ACTIVE);
            Account toUsd = createAccount("444444444444444444", client, usd,
                    new BigDecimal("10000"), AccountStatus.ACTIVE);
            Account bankEur = createBankAccount("BANK-EUR", eur, new BigDecimal("999999"));
            Account bankUsd = createBankAccount("BANK-USD", usd, new BigDecimal("999999"));

            when(accountRepository.findForUpdateByAccountNumber("333333333333333333"))
                    .thenReturn(Optional.of(fromEur));
            when(accountRepository.findForUpdateByAccountNumber("444444444444444444"))
                    .thenReturn(Optional.of(toUsd));
            when(accountRepository.findBankAccountForUpdateByCurrency("22200022", "EUR"))
                    .thenReturn(Optional.of(bankEur));
            when(accountRepository.findBankAccountForUpdateByCurrency("22200022", "USD"))
                    .thenReturn(Optional.of(bankUsd));
            when(exchangeService.calculateCross(1000.0, "EUR", "USD"))
                    .thenReturn(new CalculateExchangeResponseDto(1085.0, 1.085, "EUR", "USD"));
            when(transferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            TransferFxRequestDto request = new TransferFxRequestDto();
            request.setFromAccountNumber("333333333333333333");
            request.setToAccountNumber("444444444444444444");
            request.setAmount(new BigDecimal("1000"));

            transferService.fxTransfer(request);

            // Verify all four pessimistic locks
            verify(accountRepository).findForUpdateByAccountNumber("333333333333333333");
            verify(accountRepository).findForUpdateByAccountNumber("444444444444444444");
            verify(accountRepository).findBankAccountForUpdateByCurrency("22200022", "EUR");
            verify(accountRepository).findBankAccountForUpdateByCurrency("22200022", "USD");
            // Verify all four accounts are saved
            verify(accountRepository, times(4)).save(any(Account.class));
        }
    }

    // ===== Bank account insufficient reserves =====

    @Nested
    @DisplayName("Bank account insufficient reserves")
    class BankInsufficientReserves {

        @Test
        @DisplayName("fxTransfer fails when bank target currency reserves are too low")
        void bankReservesTooLow() {
            Account fromEur = createAccount("333333333333333333", client, eur,
                    new BigDecimal("50000"), AccountStatus.ACTIVE);
            Account toUsd = createAccount("444444444444444444", client, usd,
                    new BigDecimal("10000"), AccountStatus.ACTIVE);
            Account bankEur = createBankAccount("BANK-EUR", eur, new BigDecimal("999999"));
            // Bank has only 10 USD - way less than needed
            Account bankUsd = createBankAccount("BANK-USD", usd, new BigDecimal("10"));

            when(accountRepository.findForUpdateByAccountNumber("333333333333333333"))
                    .thenReturn(Optional.of(fromEur));
            when(accountRepository.findForUpdateByAccountNumber("444444444444444444"))
                    .thenReturn(Optional.of(toUsd));
            when(accountRepository.findBankAccountForUpdateByCurrency("22200022", "EUR"))
                    .thenReturn(Optional.of(bankEur));
            when(accountRepository.findBankAccountForUpdateByCurrency("22200022", "USD"))
                    .thenReturn(Optional.of(bankUsd));
            when(exchangeService.calculateCross(5000.0, "EUR", "USD"))
                    .thenReturn(new CalculateExchangeResponseDto(5425.0, 1.085, "EUR", "USD"));

            TransferFxRequestDto request = new TransferFxRequestDto();
            request.setFromAccountNumber("333333333333333333");
            request.setToAccountNumber("444444444444444444");
            request.setAmount(new BigDecimal("5000"));

            assertThatThrownBy(() -> transferService.fxTransfer(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Bank does not have enough USD reserves");
        }

        @Test
        @DisplayName("fxTransfer fails when bank reserves exactly equal converted amount minus 1 cent")
        void bankReservesJustBelowThreshold() {
            Account fromEur = createAccount("333333333333333333", client, eur,
                    new BigDecimal("50000"), AccountStatus.ACTIVE);
            Account toUsd = createAccount("444444444444444444", client, usd,
                    new BigDecimal("10000"), AccountStatus.ACTIVE);
            Account bankEur = createBankAccount("BANK-EUR", eur, new BigDecimal("999999"));
            // convertedAmount will be 1085.0, bank has 1084.99
            Account bankUsd = createBankAccount("BANK-USD", usd, new BigDecimal("1084.99"));

            when(accountRepository.findForUpdateByAccountNumber("333333333333333333"))
                    .thenReturn(Optional.of(fromEur));
            when(accountRepository.findForUpdateByAccountNumber("444444444444444444"))
                    .thenReturn(Optional.of(toUsd));
            when(accountRepository.findBankAccountForUpdateByCurrency("22200022", "EUR"))
                    .thenReturn(Optional.of(bankEur));
            when(accountRepository.findBankAccountForUpdateByCurrency("22200022", "USD"))
                    .thenReturn(Optional.of(bankUsd));
            when(exchangeService.calculateCross(1000.0, "EUR", "USD"))
                    .thenReturn(new CalculateExchangeResponseDto(1085.0, 1.085, "EUR", "USD"));

            TransferFxRequestDto request = new TransferFxRequestDto();
            request.setFromAccountNumber("333333333333333333");
            request.setToAccountNumber("444444444444444444");
            request.setAmount(new BigDecimal("1000"));

            assertThatThrownBy(() -> transferService.fxTransfer(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Bank does not have enough USD reserves");
        }
    }

    // ===== Same account transfer rejection =====

    @Nested
    @DisplayName("Same account rejection")
    class SameAccountRejection {

        @Test
        @DisplayName("internalTransfer rejects same source and destination")
        void sameAccountInternal() {
            when(accountRepository.findForUpdateByAccountNumber("111111111111111111"))
                    .thenReturn(Optional.of(fromAccountRsd));

            TransferInternalRequestDto request = new TransferInternalRequestDto();
            request.setFromAccountNumber("111111111111111111");
            request.setToAccountNumber("111111111111111111");
            request.setAmount(new BigDecimal("1000"));

            assertThatThrownBy(() -> transferService.internalTransfer(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Accounts must be different");
        }

        @Test
        @DisplayName("fxTransfer rejects same source and destination")
        void sameAccountFx() {
            when(accountRepository.findForUpdateByAccountNumber("111111111111111111"))
                    .thenReturn(Optional.of(fromAccountRsd));

            TransferFxRequestDto request = new TransferFxRequestDto();
            request.setFromAccountNumber("111111111111111111");
            request.setToAccountNumber("111111111111111111");
            request.setAmount(new BigDecimal("1000"));

            assertThatThrownBy(() -> transferService.fxTransfer(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Accounts must be different");
        }
    }

    // ===== Source account inactive =====

    @Nested
    @DisplayName("Source account inactive")
    class SourceAccountInactive {

        @Test
        @DisplayName("internalTransfer rejects inactive source account")
        void internalSourceInactive() {
            fromAccountRsd.setStatus(AccountStatus.INACTIVE);

            when(accountRepository.findForUpdateByAccountNumber("111111111111111111"))
                    .thenReturn(Optional.of(fromAccountRsd));
            when(accountRepository.findForUpdateByAccountNumber("222222222222222222"))
                    .thenReturn(Optional.of(toAccountRsd));

            TransferInternalRequestDto request = new TransferInternalRequestDto();
            request.setFromAccountNumber("111111111111111111");
            request.setToAccountNumber("222222222222222222");
            request.setAmount(new BigDecimal("1000"));

            assertThatThrownBy(() -> transferService.internalTransfer(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Source account is not active");
        }

        @Test
        @DisplayName("fxTransfer rejects inactive source account")
        void fxSourceInactive() {
            fromAccountRsd.setStatus(AccountStatus.INACTIVE);
            toAccountRsd.setCurrency(eur);

            when(accountRepository.findForUpdateByAccountNumber("111111111111111111"))
                    .thenReturn(Optional.of(fromAccountRsd));
            when(accountRepository.findForUpdateByAccountNumber("222222222222222222"))
                    .thenReturn(Optional.of(toAccountRsd));

            TransferFxRequestDto request = new TransferFxRequestDto();
            request.setFromAccountNumber("111111111111111111");
            request.setToAccountNumber("222222222222222222");
            request.setAmount(new BigDecimal("1000"));

            assertThatThrownBy(() -> transferService.fxTransfer(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Source account is not active");
        }
    }

    // ===== FX transfer rejects same currency =====

    @Nested
    @DisplayName("FX same currency rejection")
    class FxSameCurrencyRejection {

        @Test
        @DisplayName("fxTransfer rejects when both accounts have same currency")
        void fxSameCurrency() {
            // Both accounts are RSD
            when(accountRepository.findForUpdateByAccountNumber("111111111111111111"))
                    .thenReturn(Optional.of(fromAccountRsd));
            when(accountRepository.findForUpdateByAccountNumber("222222222222222222"))
                    .thenReturn(Optional.of(toAccountRsd));

            TransferFxRequestDto request = new TransferFxRequestDto();
            request.setFromAccountNumber("111111111111111111");
            request.setToAccountNumber("222222222222222222");
            request.setAmount(new BigDecimal("1000"));

            assertThatThrownBy(() -> transferService.fxTransfer(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Accounts must have different currencies");
        }
    }

    // ===== Access control: company authorized person =====

    @Nested
    @DisplayName("Access control via authorized persons")
    class AccessControl {

        @Test
        @DisplayName("transfer succeeds for company authorized person")
        void authorizedPersonCanTransfer() {
            Client otherOwner = new Client();
            otherOwner.setId(99L);

            AuthorizedPerson ap = AuthorizedPerson.builder()
                    .id(1L)
                    .client(client)  // our actor is authorized
                    .build();

            Company company = Company.builder()
                    .id(1L)
                    .name("TestCorp")
                    .registrationNumber("12345678")
                    .taxNumber("87654321")
                    .address("Belgrade")
                    .authorizedPersons(List.of(ap))
                    .build();

            // Account belongs to company, not to client directly
            Account companyFrom = new Account();
            companyFrom.setAccountNumber("555555555555555555");
            companyFrom.setClient(null);  // no direct client owner
            companyFrom.setCompany(company);
            companyFrom.setCurrency(rsd);
            companyFrom.setBalance(new BigDecimal("100000"));
            companyFrom.setAvailableBalance(new BigDecimal("100000"));
            companyFrom.setStatus(AccountStatus.ACTIVE);

            Account companyTo = new Account();
            companyTo.setAccountNumber("666666666666666666");
            companyTo.setClient(null);
            companyTo.setCompany(company);
            companyTo.setCurrency(rsd);
            companyTo.setBalance(new BigDecimal("50000"));
            companyTo.setAvailableBalance(new BigDecimal("50000"));
            companyTo.setStatus(AccountStatus.ACTIVE);

            when(accountRepository.findForUpdateByAccountNumber("555555555555555555"))
                    .thenReturn(Optional.of(companyFrom));
            when(accountRepository.findForUpdateByAccountNumber("666666666666666666"))
                    .thenReturn(Optional.of(companyTo));
            when(transferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            TransferInternalRequestDto request = new TransferInternalRequestDto();
            request.setFromAccountNumber("555555555555555555");
            request.setToAccountNumber("666666666666666666");
            request.setAmount(new BigDecimal("5000"));

            TransferResponseDto response = transferService.internalTransfer(request);

            assertThat(response.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        }

        @Test
        @DisplayName("transfer fails for non-authorized person on company account")
        void nonAuthorizedPersonDenied() {
            Client otherClient = new Client();
            otherClient.setId(88L);

            AuthorizedPerson ap = AuthorizedPerson.builder()
                    .id(1L)
                    .client(otherClient)  // different client is authorized, not our actor
                    .build();

            Company company = Company.builder()
                    .id(1L)
                    .name("TestCorp")
                    .registrationNumber("12345678")
                    .taxNumber("87654321")
                    .address("Belgrade")
                    .authorizedPersons(List.of(ap))
                    .build();

            Account companyAccount = new Account();
            companyAccount.setAccountNumber("555555555555555555");
            companyAccount.setClient(null);
            companyAccount.setCompany(company);
            companyAccount.setCurrency(rsd);
            companyAccount.setBalance(new BigDecimal("100000"));
            companyAccount.setAvailableBalance(new BigDecimal("100000"));
            companyAccount.setStatus(AccountStatus.ACTIVE);

            when(accountRepository.findForUpdateByAccountNumber("555555555555555555"))
                    .thenReturn(Optional.of(companyAccount));
            when(accountRepository.findForUpdateByAccountNumber("222222222222222222"))
                    .thenReturn(Optional.of(toAccountRsd));

            TransferInternalRequestDto request = new TransferInternalRequestDto();
            request.setFromAccountNumber("555555555555555555");
            request.setToAccountNumber("222222222222222222");
            request.setAmount(new BigDecimal("1000"));

            assertThatThrownBy(() -> transferService.internalTransfer(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("You do not have access to the specified account");
        }
    }

    // ===== getTransferById =====

    @Nested
    @DisplayName("getTransferById")
    class GetTransferById {

        @Test
        @DisplayName("throws when transfer not found")
        void transferNotFound() {
            when(transferRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> transferService.getTransferById(999L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Transfer not found");
        }
    }

    // ===== Authentication with String principal =====

    @Nested
    @DisplayName("Authentication with String principal")
    class StringPrincipal {

        @Test
        @DisplayName("works when principal is a plain String")
        void stringPrincipalResolves() {
            // Set authentication with a raw String principal instead of UserDetails
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("marko@test.com", null,
                            List.of(new SimpleGrantedAuthority("ROLE_CLIENT")))
            );

            when(accountRepository.findForUpdateByAccountNumber("111111111111111111"))
                    .thenReturn(Optional.of(fromAccountRsd));
            when(accountRepository.findForUpdateByAccountNumber("222222222222222222"))
                    .thenReturn(Optional.of(toAccountRsd));
            when(transferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            TransferInternalRequestDto request = new TransferInternalRequestDto();
            request.setFromAccountNumber("111111111111111111");
            request.setToAccountNumber("222222222222222222");
            request.setAmount(new BigDecimal("1000"));

            TransferResponseDto response = transferService.internalTransfer(request);

            assertThat(response.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        }
    }

    // ===== Client not found for authenticated email =====

    @Nested
    @DisplayName("Client not found")
    class ClientNotFound {

        @Test
        @DisplayName("throws when no client matches the authenticated email")
        void clientNotFoundForEmail() {
            authenticateAs("ghost@test.com");
            when(clientRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

            when(accountRepository.findForUpdateByAccountNumber("111111111111111111"))
                    .thenReturn(Optional.of(fromAccountRsd));
            when(accountRepository.findForUpdateByAccountNumber("222222222222222222"))
                    .thenReturn(Optional.of(toAccountRsd));

            TransferInternalRequestDto request = new TransferInternalRequestDto();
            request.setFromAccountNumber("111111111111111111");
            request.setToAccountNumber("222222222222222222");
            request.setAmount(new BigDecimal("1000"));

            assertThatThrownBy(() -> transferService.internalTransfer(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Client not found for authenticated user");
        }
    }

    // ===== Helper methods =====

    private Account createAccount(String number, Client owner, Currency currency,
                                  BigDecimal balance, AccountStatus status) {
        Account account = new Account();
        account.setAccountNumber(number);
        account.setClient(owner);
        account.setCurrency(currency);
        account.setBalance(balance);
        account.setAvailableBalance(balance);
        account.setStatus(status);
        return account;
    }

    private Account createBankAccount(String number, Currency currency, BigDecimal balance) {
        Account account = new Account();
        account.setAccountNumber(number);
        account.setCurrency(currency);
        account.setBalance(balance);
        account.setAvailableBalance(balance);
        account.setStatus(AccountStatus.ACTIVE);
        return account;
    }
}
