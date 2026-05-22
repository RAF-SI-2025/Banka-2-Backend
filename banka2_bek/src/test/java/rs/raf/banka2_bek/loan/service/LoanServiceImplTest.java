package rs.raf.banka2_bek.loan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountStatus;
import rs.raf.banka2_bek.account.model.AccountType;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.currency.repository.CurrencyRepository;
import rs.raf.banka2_bek.loan.dto.*;
import rs.raf.banka2_bek.loan.model.*;
import rs.raf.banka2_bek.loan.repository.LoanInstallmentRepository;
import rs.raf.banka2_bek.loan.repository.LoanRepository;
import rs.raf.banka2_bek.loan.repository.LoanRequestRepository;
import rs.raf.banka2_bek.loan.service.implementation.LoanServiceImpl;
import rs.raf.banka2_bek.notification.service.MailSenderService;
import rs.raf.banka2_bek.notification.service.NotificationService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanServiceImplTest {

    @Mock private LoanRequestRepository loanRequestRepository;
    @Mock private LoanRepository loanRepository;
    @Mock private LoanInstallmentRepository installmentRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private MailSenderService mailSenderService;
    @Mock private NotificationService notificationService;

    private LoanServiceImpl loanService;

    private Client client;
    private Account account;
    private Account bankAccount;
    private Currency rsd;

    @BeforeEach
    void setUp() {
        loanService = new LoanServiceImpl(
                loanRequestRepository, loanRepository, installmentRepository,
                accountRepository, clientRepository, currencyRepository,
                mailSenderService, "22200022", notificationService);

        rsd = new Currency();
        rsd.setId(8L);
        rsd.setCode("RSD");

        client = Client.builder()
                .id(1L).firstName("Stefan").lastName("Jovanovic")
                .email("stefan@test.com").build();

        account = Account.builder()
                .id(1L).accountNumber("222000112345678911")
                .accountType(AccountType.CHECKING)
                .currency(rsd).client(client)
                .balance(BigDecimal.valueOf(100000))
                .availableBalance(BigDecimal.valueOf(100000))
                .status(AccountStatus.ACTIVE)
                .build();

        bankAccount = Account.builder()
                .id(99L).accountNumber("222000220000000001")
                .accountType(AccountType.CHECKING)
                .currency(rsd)
                .balance(BigDecimal.valueOf(999999999))
                .availableBalance(BigDecimal.valueOf(999999999))
                .status(AccountStatus.ACTIVE)
                .build();
    }

    @Nested
    @DisplayName("createLoanRequest")
    class CreateLoanRequest {

        @Test
        @DisplayName("uspesno kreira zahtev za kredit")
        void success() {
            LoanRequestDto dto = new LoanRequestDto();
            dto.setLoanType("CASH");
            dto.setInterestType("FIXED");
            dto.setAmount(BigDecimal.valueOf(100000));
            dto.setCurrency("RSD");
            dto.setLoanPurpose("Renoviranje stana");
            dto.setRepaymentPeriod(24);
            dto.setAccountNumber("222000112345678911");
            dto.setPhoneNumber("+381601234567");

            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));
            when(accountRepository.findByAccountNumber("222000112345678911")).thenReturn(Optional.of(account));
            when(currencyRepository.findByCode("RSD")).thenReturn(Optional.of(rsd));
            when(loanRequestRepository.save(any(LoanRequest.class))).thenAnswer(inv -> {
                LoanRequest r = inv.getArgument(0);
                r.setId(1L);
                return r;
            });

            LoanRequestResponseDto result = loanService.createLoanRequest(dto, "stefan@test.com");

            assertThat(result).isNotNull();
            assertThat(result.getLoanType()).isEqualTo("CASH");
            assertThat(result.getAmount()).isEqualByComparingTo("100000");
            assertThat(result.getStatus()).isEqualTo("PENDING");
            assertThat(result.getClientName()).isEqualTo("Stefan Jovanovic");
            verify(loanRequestRepository).save(any(LoanRequest.class));
        }

        @Test
        @DisplayName("baca gresku za nepostojeceg klijenta")
        void clientNotFound() {
            LoanRequestDto dto = new LoanRequestDto();
            dto.setAccountNumber("222000112345678911");
            when(clientRepository.findByEmail("nepostojeci@test.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> loanService.createLoanRequest(dto, "nepostojeci@test.com"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Klijent nije pronadjen");
        }

        @Test
        @DisplayName("baca gresku za nepostojeci racun")
        void accountNotFound() {
            LoanRequestDto dto = new LoanRequestDto();
            dto.setAccountNumber("999999999999999999");
            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));
            when(accountRepository.findByAccountNumber("999999999999999999")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> loanService.createLoanRequest(dto, "stefan@test.com"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Racun nije pronadjen");
        }

        @Test
        @DisplayName("baca gresku kad valuta kredita ne odgovara valuti racuna")
        void currencyMismatch() {
            Currency eur = new Currency();
            eur.setId(1L);
            eur.setCode("EUR");

            LoanRequestDto dto = new LoanRequestDto();
            dto.setLoanType("CASH");
            dto.setInterestType("FIXED");
            dto.setAmount(BigDecimal.valueOf(1000));
            dto.setCurrency("EUR");
            dto.setRepaymentPeriod(12);
            dto.setAccountNumber("222000112345678911");

            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));
            when(accountRepository.findByAccountNumber("222000112345678911")).thenReturn(Optional.of(account));
            when(currencyRepository.findByCode("EUR")).thenReturn(Optional.of(eur));

            assertThatThrownBy(() -> loanService.createLoanRequest(dto, "stefan@test.com"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Valuta");
        }

        @Test
        @DisplayName("baca gresku kad racun ne pripada klijentu")
        void accountNotOwnedByClient() {
            Client other = Client.builder().id(99L).firstName("Drugi").lastName("Klijent").email("drugi@test.com").build();
            Account otherAccount = Account.builder()
                    .id(2L).accountNumber("222000112345678912")
                    .currency(rsd).client(other).build();

            LoanRequestDto dto = new LoanRequestDto();
            dto.setLoanType("CASH");
            dto.setInterestType("FIXED");
            dto.setAmount(BigDecimal.valueOf(1000));
            dto.setCurrency("RSD");
            dto.setRepaymentPeriod(12);
            dto.setAccountNumber("222000112345678912");

            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));
            when(accountRepository.findByAccountNumber("222000112345678912")).thenReturn(Optional.of(otherAccount));

            assertThatThrownBy(() -> loanService.createLoanRequest(dto, "stefan@test.com"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("ne pripada klijentu");
        }
    }

    @Nested
    @DisplayName("approveLoanRequest")
    class ApproveLoanRequest {

        @Test
        @DisplayName("odobrava zahtev i kreira kredit sa ratama")
        void success() {
            LoanRequest request = LoanRequest.builder()
                    .id(1L).loanType(LoanType.CASH).interestType(InterestType.FIXED)
                    .amount(BigDecimal.valueOf(100000)).currency(rsd)
                    .repaymentPeriod(24).account(account).client(client)
                    .status(LoanStatus.PENDING).build();

            when(loanRequestRepository.findById(1L)).thenReturn(Optional.of(request));
            when(loanRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(loanRepository.save(any(Loan.class))).thenAnswer(inv -> {
                Loan l = inv.getArgument(0);
                l.setId(1L);
                return l;
            });
            when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(account));
            when(accountRepository.findBankAccountForUpdateByCurrency("22200022", "RSD")).thenReturn(Optional.of(bankAccount));
            when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(installmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            LoanResponseDto result = loanService.approveLoanRequest(1L);

            assertThat(result).isNotNull();
            assertThat(result.getLoanNumber()).startsWith("LN-");
            assertThat(result.getStatus()).isEqualTo("ACTIVE");
            assertThat(result.getMonthlyPayment()).isPositive();
            assertThat(result.getNominalRate()).isEqualByComparingTo("6.25");
            assertThat(result.getRepaymentPeriod()).isEqualTo(24);

            // Verify installments created (24 months)
            verify(installmentRepository, times(24)).save(any(LoanInstallment.class));
            // Verify account balance increased
            verify(accountRepository).save(argThat(a ->
                    a.getBalance().compareTo(BigDecimal.valueOf(200000)) == 0));
        }

        @Test
        @DisplayName("baca gresku za vec obradjen zahtev")
        void alreadyProcessed() {
            LoanRequest request = LoanRequest.builder()
                    .id(1L).status(LoanStatus.APPROVED).build();
            when(loanRequestRepository.findById(1L)).thenReturn(Optional.of(request));

            assertThatThrownBy(() -> loanService.approveLoanRequest(1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("vec obradjen");
        }
    }

    @Nested
    @DisplayName("rejectLoanRequest")
    class RejectLoanRequest {

        @Test
        @DisplayName("odbija zahtev za kredit")
        void success() {
            LoanRequest request = LoanRequest.builder()
                    .id(1L).loanType(LoanType.STUDENT).interestType(InterestType.VARIABLE)
                    .amount(BigDecimal.valueOf(50000)).currency(rsd)
                    .repaymentPeriod(12).account(account).client(client)
                    .status(LoanStatus.PENDING).build();

            when(loanRequestRepository.findById(1L)).thenReturn(Optional.of(request));
            when(loanRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            LoanRequestResponseDto result = loanService.rejectLoanRequest(1L);

            assertThat(result.getStatus()).isEqualTo("REJECTED");
        }
    }

    @Nested
    @DisplayName("getMyLoans")
    class GetMyLoans {

        @Test
        @DisplayName("vraca praznu listu za nepostojeceg klijenta")
        void nonExistentClient() {
            when(clientRepository.findByEmail("nepostojeci@test.com")).thenReturn(Optional.empty());

            Page<LoanResponseDto> result = loanService.getMyLoans("nepostojeci@test.com", PageRequest.of(0, 10));
            assertThat(result.getContent()).isEmpty();
        }

        @Test
        @DisplayName("vraca kredite za klijenta")
        void returnsLoans() {
            Loan loan = Loan.builder()
                    .id(1L).loanNumber("LN-12345678").loanType(LoanType.CASH)
                    .interestType(InterestType.FIXED).amount(BigDecimal.valueOf(100000))
                    .repaymentPeriod(24).nominalRate(new BigDecimal("6.25"))
                    .effectiveRate(new BigDecimal("8.00")).monthlyPayment(new BigDecimal("4500.00"))
                    .startDate(LocalDate.now()).endDate(LocalDate.now().plusMonths(24))
                    .remainingDebt(BigDecimal.valueOf(90000)).currency(rsd)
                    .status(LoanStatus.ACTIVE).account(account).client(client)
                    .loanPurpose("Test").createdAt(LocalDateTime.now()).build();

            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));
            when(loanRepository.findByClientId(eq(1L), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(loan)));

            Page<LoanResponseDto> result = loanService.getMyLoans("stefan@test.com", PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getLoanNumber()).isEqualTo("LN-12345678");
            assertThat(result.getContent().get(0).getStatus()).isEqualTo("ACTIVE");
        }
    }

    @Nested
    @DisplayName("getInstallments")
    class GetInstallments {

        @Test
        @DisplayName("vraca rate za kredit")
        void returnsInstallments() {
            Loan loan = Loan.builder().id(1L).build();
            LoanInstallment inst = LoanInstallment.builder()
                    .id(1L).loan(loan).amount(new BigDecimal("4500"))
                    .interestRate(new BigDecimal("8.00")).currency(rsd)
                    .expectedDueDate(LocalDate.now().plusMonths(1)).paid(false).build();

            when(installmentRepository.findByLoanIdOrderByExpectedDueDateAsc(1L))
                    .thenReturn(List.of(inst));

            List<InstallmentResponseDto> result = loanService.getInstallments(1L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getAmount()).isEqualByComparingTo("4500");
            assertThat(result.get(0).getPaid()).isFalse();
        }
    }

    @Nested
    @DisplayName("Kamatne stope po specifikaciji")
    class InterestRates {

        @Test
        @DisplayName("kredit 100k RSD sa gotovinski tipom - nominalna 6.25%, marza 1.75%")
        void cashLoan100k() {
            LoanRequest request = LoanRequest.builder()
                    .id(1L).loanType(LoanType.CASH).interestType(InterestType.FIXED)
                    .amount(BigDecimal.valueOf(100000)).currency(rsd)
                    .repaymentPeriod(12).account(account).client(client)
                    .status(LoanStatus.PENDING).build();

            when(loanRequestRepository.findById(1L)).thenReturn(Optional.of(request));
            when(loanRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(loanRepository.save(any(Loan.class))).thenAnswer(inv -> {
                Loan l = inv.getArgument(0);
                l.setId(1L);
                return l;
            });
            when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(account));
            when(accountRepository.findBankAccountForUpdateByCurrency("22200022", "RSD")).thenReturn(Optional.of(bankAccount));
            when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(installmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            LoanResponseDto result = loanService.approveLoanRequest(1L);

            // 100k <= 500k => base=6.25%, CASH margin=1.75%, effective=8.00%
            assertThat(result.getNominalRate()).isEqualByComparingTo("6.25");
            assertThat(result.getEffectiveRate()).isEqualByComparingTo("8.00");
        }

        @Test
        @DisplayName("studentski kredit 300k - nominalna 6.25%, marza 0.75%")
        void studentLoan300k() {
            LoanRequest request = LoanRequest.builder()
                    .id(2L).loanType(LoanType.STUDENT).interestType(InterestType.FIXED)
                    .amount(BigDecimal.valueOf(300000)).currency(rsd)
                    .repaymentPeriod(36).account(account).client(client)
                    .status(LoanStatus.PENDING).build();

            when(loanRequestRepository.findById(2L)).thenReturn(Optional.of(request));
            when(loanRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(loanRepository.save(any(Loan.class))).thenAnswer(inv -> {
                Loan l = inv.getArgument(0);
                l.setId(2L);
                return l;
            });
            when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(account));
            when(accountRepository.findBankAccountForUpdateByCurrency("22200022", "RSD")).thenReturn(Optional.of(bankAccount));
            when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(installmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            LoanResponseDto result = loanService.approveLoanRequest(2L);

            // 300k <= 500k => base=6.25%, STUDENT margin=0.75%, effective=7.00%
            assertThat(result.getNominalRate()).isEqualByComparingTo("6.25");
            assertThat(result.getEffectiveRate()).isEqualByComparingTo("7.00");
        }

        @Test
        @DisplayName("stambeni kredit 5M - nominalna 5.50%, marza 1.50%")
        void mortgageLoan5M() {
            LoanRequest request = LoanRequest.builder()
                    .id(3L).loanType(LoanType.MORTGAGE).interestType(InterestType.FIXED)
                    .amount(BigDecimal.valueOf(5000000)).currency(rsd)
                    .repaymentPeriod(240).account(account).client(client)
                    .status(LoanStatus.PENDING).build();

            when(loanRequestRepository.findById(3L)).thenReturn(Optional.of(request));
            when(loanRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(loanRepository.save(any(Loan.class))).thenAnswer(inv -> {
                Loan l = inv.getArgument(0);
                l.setId(3L);
                return l;
            });
            when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(account));
            when(accountRepository.findBankAccountForUpdateByCurrency("22200022", "RSD")).thenReturn(Optional.of(bankAccount));
            when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(installmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            LoanResponseDto result = loanService.approveLoanRequest(3L);

            // 5M = 2M-5M range => base=5.50%, MORTGAGE margin=1.50%, effective=7.00%
            assertThat(result.getNominalRate()).isEqualByComparingTo("5.50");
            assertThat(result.getEffectiveRate()).isEqualByComparingTo("7.00");
            verify(installmentRepository, times(240)).save(any());
        }
    }
}
