package rs.raf.banka2_bek.loan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
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
import rs.raf.banka2_bek.notification.service.MailNotificationService;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Extended tests for LoanServiceImpl - covers scenarios not in the original test file.
 */
@ExtendWith(MockitoExtension.class)
class LoanServiceImplExtendedTest {

    @Mock private LoanRequestRepository loanRequestRepository;
    @Mock private LoanRepository loanRepository;
    @Mock private LoanInstallmentRepository installmentRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private MailNotificationService mailNotificationService;

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
                mailNotificationService, "22200022");

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

    // ===== createLoanRequest: all loan types =====

    @Nested
    @DisplayName("createLoanRequest - all loan types")
    class CreateLoanRequestAllTypes {

        @ParameterizedTest(name = "loan type {0}")
        @EnumSource(LoanType.class)
        @DisplayName("creates request for each loan type")
        void createsRequestForEachLoanType(LoanType loanType) {
            LoanRequestDto dto = new LoanRequestDto();
            dto.setLoanType(loanType.name());
            dto.setInterestType("FIXED");
            dto.setAmount(BigDecimal.valueOf(200000));
            dto.setCurrency("RSD");
            dto.setLoanPurpose("Test " + loanType);
            dto.setRepaymentPeriod(12);
            dto.setAccountNumber("222000112345678911");

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
            assertThat(result.getLoanType()).isEqualTo(loanType.name());
            assertThat(result.getStatus()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("creates request with VARIABLE interest type")
        void variableInterestType() {
            LoanRequestDto dto = new LoanRequestDto();
            dto.setLoanType("CASH");
            dto.setInterestType("VARIABLE");
            dto.setAmount(BigDecimal.valueOf(50000));
            dto.setCurrency("RSD");
            dto.setRepaymentPeriod(6);
            dto.setAccountNumber("222000112345678911");

            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));
            when(accountRepository.findByAccountNumber("222000112345678911")).thenReturn(Optional.of(account));
            when(currencyRepository.findByCode("RSD")).thenReturn(Optional.of(rsd));
            when(loanRequestRepository.save(any())).thenAnswer(inv -> {
                LoanRequest r = inv.getArgument(0);
                r.setId(1L);
                return r;
            });

            LoanRequestResponseDto result = loanService.createLoanRequest(dto, "stefan@test.com");
            assertThat(result.getInterestType()).isEqualTo("VARIABLE");
        }

        @Test
        @DisplayName("creates request with optional employment fields populated")
        void withEmploymentFields() {
            LoanRequestDto dto = new LoanRequestDto();
            dto.setLoanType("MORTGAGE");
            dto.setInterestType("FIXED");
            dto.setAmount(BigDecimal.valueOf(5000000));
            dto.setCurrency("RSD");
            dto.setRepaymentPeriod(240);
            dto.setAccountNumber("222000112345678911");
            dto.setPhoneNumber("+381641234567");
            dto.setEmploymentStatus("EMPLOYED");
            dto.setMonthlyIncome(BigDecimal.valueOf(150000));
            dto.setPermanentEmployment(true);
            dto.setEmploymentPeriod(60);

            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));
            when(accountRepository.findByAccountNumber("222000112345678911")).thenReturn(Optional.of(account));
            when(currencyRepository.findByCode("RSD")).thenReturn(Optional.of(rsd));
            when(loanRequestRepository.save(any())).thenAnswer(inv -> {
                LoanRequest r = inv.getArgument(0);
                r.setId(1L);
                return r;
            });

            LoanRequestResponseDto result = loanService.createLoanRequest(dto, "stefan@test.com");
            assertThat(result.getPhoneNumber()).isEqualTo("+381641234567");
            assertThat(result.getEmploymentStatus()).isEqualTo("EMPLOYED");
            assertThat(result.getMonthlyIncome()).isEqualByComparingTo(new BigDecimal("150000"));
            assertThat(result.getPermanentEmployment()).isTrue();
            assertThat(result.getEmploymentPeriod()).isEqualTo(60);
        }

        @Test
        @DisplayName("email failure does not prevent request creation")
        void emailFailureDoesNotBlock() {
            LoanRequestDto dto = new LoanRequestDto();
            dto.setLoanType("STUDENT");
            dto.setInterestType("FIXED");
            dto.setAmount(BigDecimal.valueOf(100000));
            dto.setCurrency("RSD");
            dto.setRepaymentPeriod(12);
            dto.setAccountNumber("222000112345678911");

            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));
            when(accountRepository.findByAccountNumber("222000112345678911")).thenReturn(Optional.of(account));
            when(currencyRepository.findByCode("RSD")).thenReturn(Optional.of(rsd));
            when(loanRequestRepository.save(any())).thenAnswer(inv -> {
                LoanRequest r = inv.getArgument(0);
                r.setId(1L);
                return r;
            });
            doThrow(new RuntimeException("SMTP error")).when(mailNotificationService)
                    .sendLoanRequestSubmittedMail(anyString(), anyString(), any(), anyString());

            LoanRequestResponseDto result = loanService.createLoanRequest(dto, "stefan@test.com");
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("throws for unsupported currency")
        void unsupportedCurrency() {
            LoanRequestDto dto = new LoanRequestDto();
            dto.setLoanType("CASH");
            dto.setInterestType("FIXED");
            dto.setAmount(BigDecimal.valueOf(10000));
            dto.setCurrency("XYZ");
            dto.setRepaymentPeriod(12);
            dto.setAccountNumber("222000112345678911");

            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));
            when(accountRepository.findByAccountNumber("222000112345678911")).thenReturn(Optional.of(account));
            when(currencyRepository.findByCode("XYZ")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> loanService.createLoanRequest(dto, "stefan@test.com"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Valuta nije podrzana");
        }
    }

    // ===== Interest rate table: all amount brackets =====

    @Nested
    @DisplayName("Interest rate brackets")
    class InterestRateBrackets {

        private void setupApprovalMocks(LoanRequest request) {
            when(loanRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));
            when(loanRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(loanRepository.save(any(Loan.class))).thenAnswer(inv -> {
                Loan l = inv.getArgument(0);
                l.setId(request.getId());
                return l;
            });
            when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(account));
            when(accountRepository.findBankAccountForUpdateByCurrency("22200022", "RSD")).thenReturn(Optional.of(bankAccount));
            when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(installmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        }

        @ParameterizedTest(name = "amount={0} -> nominalRate={1}")
        @CsvSource({
                "100000, 6.25",
                "500000, 6.25",
                "500001, 6.00",
                "1000000, 6.00",
                "1000001, 5.75",
                "2000000, 5.75",
                "2000001, 5.50",
                "5000000, 5.50",
                "5000001, 5.25",
                "10000000, 5.25",
                "10000001, 5.00",
                "20000000, 5.00",
                "20000001, 4.75",
                "50000000, 4.75"
        })
        @DisplayName("base rate depends on amount bracket")
        void baseRateByAmount(long amount, String expectedRate) {
            LoanRequest request = LoanRequest.builder()
                    .id(10L).loanType(LoanType.CASH).interestType(InterestType.FIXED)
                    .amount(BigDecimal.valueOf(amount)).currency(rsd)
                    .repaymentPeriod(12).account(account).client(client)
                    .status(LoanStatus.PENDING).build();

            setupApprovalMocks(request);

            LoanResponseDto result = loanService.approveLoanRequest(10L);
            assertThat(result.getNominalRate()).isEqualByComparingTo(expectedRate);
        }

        @ParameterizedTest(name = "loanType={0} -> margin={1}")
        @CsvSource({
                "CASH, 1.75",
                "MORTGAGE, 1.50",
                "AUTO, 1.25",
                "REFINANCING, 1.00",
                "STUDENT, 0.75"
        })
        @DisplayName("margin depends on loan type")
        void marginByLoanType(LoanType loanType, String expectedMargin) {
            LoanRequest request = LoanRequest.builder()
                    .id(20L).loanType(loanType).interestType(InterestType.FIXED)
                    .amount(BigDecimal.valueOf(100000)).currency(rsd)
                    .repaymentPeriod(12).account(account).client(client)
                    .status(LoanStatus.PENDING).build();

            setupApprovalMocks(request);

            LoanResponseDto result = loanService.approveLoanRequest(20L);
            BigDecimal expectedEffective = new BigDecimal("6.25").add(new BigDecimal(expectedMargin));
            assertThat(result.getEffectiveRate()).isEqualByComparingTo(expectedEffective.toPlainString());
        }
    }

    // ===== Monthly payment annuity formula verification =====

    @Nested
    @DisplayName("Monthly payment annuity formula")
    class AnnuityFormula {

        @Test
        @DisplayName("monthly payment matches manual annuity calculation")
        void verifyAnnuityFormula() {
            BigDecimal principal = BigDecimal.valueOf(100000);
            int months = 24;
            BigDecimal effectiveRate = new BigDecimal("8.00"); // 6.25 nominal + 1.75 CASH margin
            BigDecimal monthlyRate = effectiveRate.divide(BigDecimal.valueOf(1200), 10, RoundingMode.HALF_UP);

            // A = P * r * (1+r)^n / ((1+r)^n - 1)
            BigDecimal onePlusR = BigDecimal.ONE.add(monthlyRate);
            BigDecimal onePlusRn = onePlusR.pow(months, MathContext.DECIMAL128);
            BigDecimal expectedPayment = principal
                    .multiply(monthlyRate)
                    .multiply(onePlusRn)
                    .divide(onePlusRn.subtract(BigDecimal.ONE), 4, RoundingMode.HALF_UP);

            LoanRequest request = LoanRequest.builder()
                    .id(1L).loanType(LoanType.CASH).interestType(InterestType.FIXED)
                    .amount(principal).currency(rsd)
                    .repaymentPeriod(months).account(account).client(client)
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

            assertThat(result.getMonthlyPayment()).isEqualByComparingTo(expectedPayment.toPlainString());
        }
    }

    // ===== approveLoanRequest edge cases =====

    @Nested
    @DisplayName("approveLoanRequest edge cases")
    class ApproveLoanRequestEdgeCases {

        @Test
        @DisplayName("throws when request not found")
        void requestNotFound() {
            when(loanRequestRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> loanService.approveLoanRequest(999L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("nije pronadjen");
        }

        @Test
        @DisplayName("throws when request already rejected")
        void alreadyRejected() {
            LoanRequest request = LoanRequest.builder()
                    .id(1L).status(LoanStatus.REJECTED).build();
            when(loanRequestRepository.findById(1L)).thenReturn(Optional.of(request));

            assertThatThrownBy(() -> loanService.approveLoanRequest(1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("vec obradjen");
        }

        @Test
        @DisplayName("throws when bank has insufficient funds")
        void bankInsufficientFunds() {
            bankAccount.setAvailableBalance(BigDecimal.valueOf(50)); // too little

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

            assertThatThrownBy(() -> loanService.approveLoanRequest(1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("nema dovoljno sredstava");
        }

        @Test
        @DisplayName("throws when client account not found during disbursement")
        void clientAccountNotFound() {
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
            when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> loanService.approveLoanRequest(1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Racun klijenta");
        }

        @Test
        @DisplayName("throws when bank account for currency not found")
        void bankAccountCurrencyNotFound() {
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
            when(accountRepository.findBankAccountForUpdateByCurrency("22200022", "RSD")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> loanService.approveLoanRequest(1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Bankovski racun");
        }

        @Test
        @DisplayName("email failure does not roll back approval")
        void emailFailureOnApproval() {
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
            doThrow(new RuntimeException("SMTP down")).when(mailNotificationService)
                    .sendLoanApprovedMail(anyString(), anyString(), any(), anyString(), any(), any());

            LoanResponseDto result = loanService.approveLoanRequest(1L);
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo("ACTIVE");
        }
    }

    // ===== rejectLoanRequest edge cases =====

    @Nested
    @DisplayName("rejectLoanRequest edge cases")
    class RejectLoanRequestEdgeCases {

        @Test
        @DisplayName("throws when request not found")
        void notFound() {
            when(loanRequestRepository.findById(999L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> loanService.rejectLoanRequest(999L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("nije pronadjen");
        }

        @Test
        @DisplayName("throws when already approved")
        void alreadyApproved() {
            LoanRequest request = LoanRequest.builder()
                    .id(1L).status(LoanStatus.APPROVED).build();
            when(loanRequestRepository.findById(1L)).thenReturn(Optional.of(request));
            assertThatThrownBy(() -> loanService.rejectLoanRequest(1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("vec obradjen");
        }

        @Test
        @DisplayName("email failure does not block rejection")
        void emailFailureOnRejection() {
            LoanRequest request = LoanRequest.builder()
                    .id(1L).loanType(LoanType.CASH).interestType(InterestType.FIXED)
                    .amount(BigDecimal.valueOf(50000)).currency(rsd)
                    .repaymentPeriod(12).account(account).client(client)
                    .status(LoanStatus.PENDING).build();

            when(loanRequestRepository.findById(1L)).thenReturn(Optional.of(request));
            when(loanRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            doThrow(new RuntimeException("SMTP")).when(mailNotificationService)
                    .sendLoanRejectedMail(anyString(), anyString(), any(), anyString());

            LoanRequestResponseDto result = loanService.rejectLoanRequest(1L);
            assertThat(result.getStatus()).isEqualTo("REJECTED");
        }
    }

    // ===== getLoanRequests with filters =====

    @Nested
    @DisplayName("getLoanRequests")
    class GetLoanRequests {

        @Test
        @DisplayName("returns filtered by status PENDING")
        void filteredByStatus() {
            LoanRequest req = LoanRequest.builder()
                    .id(1L).loanType(LoanType.CASH).interestType(InterestType.FIXED)
                    .amount(BigDecimal.valueOf(100000)).currency(rsd)
                    .repaymentPeriod(12).account(account).client(client)
                    .status(LoanStatus.PENDING).build();

            when(loanRequestRepository.findByStatus(eq(LoanStatus.PENDING), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(req)));

            Page<LoanRequestResponseDto> result = loanService.getLoanRequests(LoanStatus.PENDING, PageRequest.of(0, 10));
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getStatus()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("returns all when status is null")
        void allRequests() {
            when(loanRequestRepository.findAll(any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            Page<LoanRequestResponseDto> result = loanService.getLoanRequests(null, PageRequest.of(0, 10));
            assertThat(result.getContent()).isEmpty();
            verify(loanRequestRepository).findAll(any(Pageable.class));
            verify(loanRequestRepository, never()).findByStatus(any(), any());
        }
    }

    // ===== getAllLoans =====

    @Nested
    @DisplayName("getAllLoans")
    class GetAllLoans {

        @Test
        @DisplayName("delegates filters to repository")
        void withFilters() {
            when(loanRepository.findWithFilters(eq(LoanType.CASH), eq(LoanStatus.ACTIVE), eq("222000112345678911"), any()))
                    .thenReturn(Page.empty());

            Page<LoanResponseDto> result = loanService.getAllLoans(LoanType.CASH, LoanStatus.ACTIVE, "222000112345678911", PageRequest.of(0, 10));
            assertThat(result.getContent()).isEmpty();
            verify(loanRepository).findWithFilters(LoanType.CASH, LoanStatus.ACTIVE, "222000112345678911", PageRequest.of(0, 10));
        }
    }

    // ===== getLoanById =====

    @Nested
    @DisplayName("getLoanById")
    class GetLoanById {

        @Test
        @DisplayName("returns loan by id")
        void success() {
            Loan loan = Loan.builder()
                    .id(1L).loanNumber("LN-ABCD1234").loanType(LoanType.AUTO)
                    .interestType(InterestType.FIXED).amount(BigDecimal.valueOf(500000))
                    .repaymentPeriod(36).nominalRate(new BigDecimal("6.25"))
                    .effectiveRate(new BigDecimal("7.50")).monthlyPayment(new BigDecimal("15000"))
                    .startDate(LocalDate.now()).endDate(LocalDate.now().plusMonths(36))
                    .remainingDebt(BigDecimal.valueOf(400000)).currency(rsd)
                    .status(LoanStatus.ACTIVE).account(account).client(client)
                    .loanPurpose("Auto kredit").build();

            when(loanRepository.findById(1L)).thenReturn(Optional.of(loan));

            LoanResponseDto result = loanService.getLoanById(1L);
            assertThat(result.getLoanNumber()).isEqualTo("LN-ABCD1234");
            assertThat(result.getLoanType()).isEqualTo("AUTO");
        }

        @Test
        @DisplayName("throws when loan not found")
        void notFound() {
            when(loanRepository.findById(999L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> loanService.getLoanById(999L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("nije pronadjen");
        }
    }

    // ===== getMyLoanRequests =====

    @Nested
    @DisplayName("getMyLoanRequests")
    class GetMyLoanRequests {

        @Test
        @DisplayName("returns empty list for non-existent client")
        void clientNotFound() {
            when(clientRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());
            List<LoanRequestResponseDto> result = loanService.getMyLoanRequests("ghost@test.com");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns loan requests for client")
        void returnsRequests() {
            LoanRequest req = LoanRequest.builder()
                    .id(1L).loanType(LoanType.STUDENT).interestType(InterestType.VARIABLE)
                    .amount(BigDecimal.valueOf(200000)).currency(rsd)
                    .repaymentPeriod(24).account(account).client(client)
                    .status(LoanStatus.PENDING).build();

            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));
            when(loanRequestRepository.findByClientIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(req));

            List<LoanRequestResponseDto> result = loanService.getMyLoanRequests("stefan@test.com");
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getLoanType()).isEqualTo("STUDENT");
        }
    }

    // ===== earlyRepayment =====

    @Nested
    @DisplayName("earlyRepayment")
    class EarlyRepayment {

        @Test
        @DisplayName("successfully pays off loan early")
        void success() {
            Loan loan = Loan.builder()
                    .id(1L).loanNumber("LN-EARLY001").loanType(LoanType.CASH)
                    .interestType(InterestType.FIXED).amount(BigDecimal.valueOf(100000))
                    .repaymentPeriod(12).nominalRate(new BigDecimal("6.25"))
                    .effectiveRate(new BigDecimal("8.00")).monthlyPayment(new BigDecimal("8700"))
                    .startDate(LocalDate.now().minusMonths(3)).endDate(LocalDate.now().plusMonths(9))
                    .remainingDebt(BigDecimal.valueOf(75000)).currency(rsd)
                    .status(LoanStatus.ACTIVE).account(account).client(client)
                    .loanPurpose("Test").build();

            LoanInstallment unpaidInstallment = LoanInstallment.builder()
                    .id(1L).loan(loan).amount(new BigDecimal("8700"))
                    .principalAmount(new BigDecimal("8200"))
                    .interestAmount(new BigDecimal("500"))
                    .interestRate(new BigDecimal("8.00")).currency(rsd)
                    .expectedDueDate(LocalDate.now().plusMonths(1)).paid(false).build();

            when(loanRepository.findById(1L)).thenReturn(Optional.of(loan));
            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));
            when(installmentRepository.findByLoanIdOrderByExpectedDueDateAsc(1L)).thenReturn(List.of(unpaidInstallment));
            when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(account));
            when(accountRepository.findBankAccountForUpdateByCurrency("22200022", "RSD")).thenReturn(Optional.of(bankAccount));
            when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(installmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(loanRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            LoanResponseDto result = loanService.earlyRepayment(1L, "stefan@test.com");
            assertThat(result.getStatus()).isEqualTo("PAID_OFF");
            assertThat(result.getRemainingDebt()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("throws when loan not found")
        void loanNotFound() {
            when(loanRepository.findById(999L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> loanService.earlyRepayment(999L, "stefan@test.com"))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("throws when loan is pending")
        void loanPending() {
            Loan loan = Loan.builder().id(1L).status(LoanStatus.PENDING).build();
            when(loanRepository.findById(1L)).thenReturn(Optional.of(loan));

            assertThatThrownBy(() -> loanService.earlyRepayment(1L, "stefan@test.com"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("nije aktivan");
        }

        @Test
        @DisplayName("throws when loan already paid off")
        void loanAlreadyPaid() {
            Loan loan = Loan.builder().id(1L).status(LoanStatus.PAID_OFF).build();
            when(loanRepository.findById(1L)).thenReturn(Optional.of(loan));

            assertThatThrownBy(() -> loanService.earlyRepayment(1L, "stefan@test.com"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("vec otplacen");
        }

        @Test
        @DisplayName("throws when loan does not belong to client")
        void loanNotOwnedByClient() {
            Client other = Client.builder().id(99L).firstName("Drugi").lastName("Klijent").email("drugi@test.com").build();
            Loan loan = Loan.builder().id(1L).status(LoanStatus.ACTIVE).client(other)
                    .loanType(LoanType.CASH).interestType(InterestType.FIXED)
                    .amount(BigDecimal.valueOf(100000)).repaymentPeriod(12)
                    .nominalRate(new BigDecimal("6.25")).effectiveRate(new BigDecimal("8.00"))
                    .monthlyPayment(new BigDecimal("8700"))
                    .startDate(LocalDate.now()).endDate(LocalDate.now().plusMonths(12))
                    .remainingDebt(BigDecimal.valueOf(100000)).currency(rsd)
                    .account(account).build();

            when(loanRepository.findById(1L)).thenReturn(Optional.of(loan));
            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));

            assertThatThrownBy(() -> loanService.earlyRepayment(1L, "stefan@test.com"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("ne pripada");
        }

        @Test
        @DisplayName("throws when insufficient funds for early repayment")
        void insufficientFunds() {
            account.setAvailableBalance(BigDecimal.valueOf(100)); // not enough

            Loan loan = Loan.builder()
                    .id(1L).loanNumber("LN-LOW001").loanType(LoanType.CASH)
                    .interestType(InterestType.FIXED).amount(BigDecimal.valueOf(100000))
                    .repaymentPeriod(12).nominalRate(new BigDecimal("6.25"))
                    .effectiveRate(new BigDecimal("8.00")).monthlyPayment(new BigDecimal("8700"))
                    .startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusMonths(11))
                    .remainingDebt(BigDecimal.valueOf(92000)).currency(rsd)
                    .status(LoanStatus.ACTIVE).account(account).client(client)
                    .loanPurpose("Test").build();

            LoanInstallment inst = LoanInstallment.builder()
                    .id(1L).loan(loan).amount(new BigDecimal("8700"))
                    .interestAmount(new BigDecimal("600")).paid(false).build();

            when(loanRepository.findById(1L)).thenReturn(Optional.of(loan));
            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));
            when(installmentRepository.findByLoanIdOrderByExpectedDueDateAsc(1L)).thenReturn(List.of(inst));
            when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(account));

            assertThatThrownBy(() -> loanService.earlyRepayment(1L, "stefan@test.com"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Nedovoljno sredstava");
        }

        @Test
        @DisplayName("throws when loan status is REJECTED")
        void loanRejected() {
            Loan loan = Loan.builder().id(1L).status(LoanStatus.REJECTED).build();
            when(loanRepository.findById(1L)).thenReturn(Optional.of(loan));

            assertThatThrownBy(() -> loanService.earlyRepayment(1L, "stefan@test.com"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("nije aktivan");
        }

        @Test
        @DisplayName("throws when loan status is PAID")
        void loanPaid() {
            Loan loan = Loan.builder().id(1L).status(LoanStatus.PAID).build();
            when(loanRepository.findById(1L)).thenReturn(Optional.of(loan));

            assertThatThrownBy(() -> loanService.earlyRepayment(1L, "stefan@test.com"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("vec otplacen");
        }

        @Test
        @DisplayName("early repayment when payoff amount is zero completes immediately")
        void payoffAmountZero() {
            Loan loan = Loan.builder()
                    .id(1L).loanNumber("LN-ZERO001").loanType(LoanType.CASH)
                    .interestType(InterestType.FIXED).amount(BigDecimal.valueOf(100000))
                    .repaymentPeriod(12).nominalRate(new BigDecimal("6.25"))
                    .effectiveRate(new BigDecimal("8.00")).monthlyPayment(new BigDecimal("8700"))
                    .startDate(LocalDate.now().minusMonths(12)).endDate(LocalDate.now())
                    .remainingDebt(BigDecimal.ZERO).currency(rsd)
                    .status(LoanStatus.ACTIVE).account(account).client(client)
                    .loanPurpose("Test").build();

            // All installments already paid
            LoanInstallment paidInst = LoanInstallment.builder()
                    .id(1L).loan(loan).amount(new BigDecimal("8700"))
                    .interestAmount(new BigDecimal("500")).paid(true).build();

            when(loanRepository.findById(1L)).thenReturn(Optional.of(loan));
            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));
            when(installmentRepository.findByLoanIdOrderByExpectedDueDateAsc(1L)).thenReturn(List.of(paidInst));
            when(loanRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            LoanResponseDto result = loanService.earlyRepayment(1L, "stefan@test.com");
            assertThat(result.getStatus()).isEqualTo("PAID_OFF");
        }

        @Test
        @DisplayName("throws when account currency differs from loan currency")
        void currencyMismatch() {
            Currency eur = new Currency();
            eur.setId(1L);
            eur.setCode("EUR");

            Account eurAccount = Account.builder()
                    .id(2L).accountNumber("222000121345678921")
                    .accountType(AccountType.CHECKING).currency(eur).client(client)
                    .balance(BigDecimal.valueOf(999999)).availableBalance(BigDecimal.valueOf(999999))
                    .status(AccountStatus.ACTIVE).build();

            Loan loan = Loan.builder()
                    .id(1L).loanNumber("LN-MIX001").loanType(LoanType.CASH)
                    .interestType(InterestType.FIXED).amount(BigDecimal.valueOf(100000))
                    .repaymentPeriod(12).nominalRate(new BigDecimal("6.25"))
                    .effectiveRate(new BigDecimal("8.00")).monthlyPayment(new BigDecimal("8700"))
                    .startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusMonths(11))
                    .remainingDebt(BigDecimal.valueOf(92000)).currency(rsd)
                    .status(LoanStatus.ACTIVE).account(eurAccount).client(client)
                    .loanPurpose("Test").build();

            LoanInstallment inst = LoanInstallment.builder()
                    .id(1L).loan(loan).amount(new BigDecimal("8700"))
                    .interestAmount(new BigDecimal("600")).paid(false).build();

            when(loanRepository.findById(1L)).thenReturn(Optional.of(loan));
            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));
            when(installmentRepository.findByLoanIdOrderByExpectedDueDateAsc(1L)).thenReturn(List.of(inst));
            when(accountRepository.findForUpdateById(2L)).thenReturn(Optional.of(eurAccount));

            assertThatThrownBy(() -> loanService.earlyRepayment(1L, "stefan@test.com"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Valuta racuna i kredita se razlikuju");
        }

        @Test
        @DisplayName("throws when bank account not found for early repayment")
        void bankAccountNotFound() {
            Loan loan = Loan.builder()
                    .id(1L).loanNumber("LN-NOBANK1").loanType(LoanType.CASH)
                    .interestType(InterestType.FIXED).amount(BigDecimal.valueOf(100000))
                    .repaymentPeriod(12).nominalRate(new BigDecimal("6.25"))
                    .effectiveRate(new BigDecimal("8.00")).monthlyPayment(new BigDecimal("8700"))
                    .startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusMonths(11))
                    .remainingDebt(BigDecimal.valueOf(92000)).currency(rsd)
                    .status(LoanStatus.ACTIVE).account(account).client(client)
                    .loanPurpose("Test").build();

            LoanInstallment inst = LoanInstallment.builder()
                    .id(1L).loan(loan).amount(new BigDecimal("8700"))
                    .interestAmount(new BigDecimal("600")).paid(false).build();

            when(loanRepository.findById(1L)).thenReturn(Optional.of(loan));
            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));
            when(installmentRepository.findByLoanIdOrderByExpectedDueDateAsc(1L)).thenReturn(List.of(inst));
            when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(account));
            when(accountRepository.findBankAccountForUpdateByCurrency("22200022", "RSD")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> loanService.earlyRepayment(1L, "stefan@test.com"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Bankovski racun");
        }

        @Test
        @DisplayName("early repayment with null interest amount on installment treated as zero")
        void nullInterestAmount() {
            Loan loan = Loan.builder()
                    .id(1L).loanNumber("LN-NULLINT").loanType(LoanType.CASH)
                    .interestType(InterestType.FIXED).amount(BigDecimal.valueOf(100000))
                    .repaymentPeriod(12).nominalRate(new BigDecimal("6.25"))
                    .effectiveRate(new BigDecimal("8.00")).monthlyPayment(new BigDecimal("8700"))
                    .startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusMonths(11))
                    .remainingDebt(BigDecimal.valueOf(50000)).currency(rsd)
                    .status(LoanStatus.ACTIVE).account(account).client(client)
                    .loanPurpose("Test").build();

            LoanInstallment instNullInterest = LoanInstallment.builder()
                    .id(1L).loan(loan).amount(new BigDecimal("8700"))
                    .interestAmount(null).paid(false).build();

            when(loanRepository.findById(1L)).thenReturn(Optional.of(loan));
            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));
            when(installmentRepository.findByLoanIdOrderByExpectedDueDateAsc(1L)).thenReturn(List.of(instNullInterest));
            when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(account));
            when(accountRepository.findBankAccountForUpdateByCurrency("22200022", "RSD")).thenReturn(Optional.of(bankAccount));
            when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(installmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(loanRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            LoanResponseDto result = loanService.earlyRepayment(1L, "stefan@test.com");
            assertThat(result.getStatus()).isEqualTo("PAID_OFF");
        }
    }

    // ===== createLoanRequest additional branches =====

    @Nested
    @DisplayName("createLoanRequest - additional branches")
    class CreateLoanRequestBranches {

        @Test
        @DisplayName("throws when client not found")
        void clientNotFound() {
            when(clientRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

            LoanRequestDto dto = new LoanRequestDto();
            assertThatThrownBy(() -> loanService.createLoanRequest(dto, "ghost@test.com"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Klijent nije pronadjen");
        }

        @Test
        @DisplayName("throws when account not found")
        void accountNotFound() {
            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));

            LoanRequestDto dto = new LoanRequestDto();
            dto.setAccountNumber("NONEXISTENT");
            when(accountRepository.findByAccountNumber("NONEXISTENT")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> loanService.createLoanRequest(dto, "stefan@test.com"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Racun nije pronadjen");
        }

        @Test
        @DisplayName("throws when account does not belong to client")
        void accountNotOwnedByClient() {
            Client other = Client.builder().id(99L).firstName("Other").lastName("Person")
                    .email("other@test.com").build();
            Account otherAccount = Account.builder()
                    .id(2L).accountNumber("222000112345678999")
                    .currency(rsd).client(other).build();

            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));
            when(accountRepository.findByAccountNumber("222000112345678999")).thenReturn(Optional.of(otherAccount));

            LoanRequestDto dto = new LoanRequestDto();
            dto.setAccountNumber("222000112345678999");
            dto.setCurrency("RSD");

            assertThatThrownBy(() -> loanService.createLoanRequest(dto, "stefan@test.com"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("ne pripada");
        }

        @Test
        @DisplayName("throws when currency mismatch between account and loan request")
        void currencyMismatchInRequest() {
            Currency eur = new Currency();
            eur.setId(1L);
            eur.setCode("EUR");

            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));
            when(accountRepository.findByAccountNumber("222000112345678911")).thenReturn(Optional.of(account));
            when(currencyRepository.findByCode("EUR")).thenReturn(Optional.of(eur));

            LoanRequestDto dto = new LoanRequestDto();
            dto.setAccountNumber("222000112345678911");
            dto.setCurrency("EUR"); // account is RSD

            assertThatThrownBy(() -> loanService.createLoanRequest(dto, "stefan@test.com"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("poklapa sa valutom racuna");
        }

        @Test
        @DisplayName("throws when account has no client")
        void accountHasNoClient() {
            Account noClientAccount = Account.builder()
                    .id(3L).accountNumber("222000112345670000")
                    .currency(rsd).client(null).build();

            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));
            when(accountRepository.findByAccountNumber("222000112345670000")).thenReturn(Optional.of(noClientAccount));

            LoanRequestDto dto = new LoanRequestDto();
            dto.setAccountNumber("222000112345670000");
            dto.setCurrency("RSD");

            assertThatThrownBy(() -> loanService.createLoanRequest(dto, "stefan@test.com"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("ne pripada");
        }
    }

    // ===== All base rate brackets =====

    @Nested
    @DisplayName("Base rate - all amount brackets")
    class BaseRateAllBrackets {

        @ParameterizedTest
        @CsvSource({
                "400000, 6.25",    // <= 500k
                "500000, 6.25",    // exactly 500k
                "500001, 6.00",    // > 500k, <= 1M
                "1000000, 6.00",   // exactly 1M
                "1000001, 5.75",   // > 1M, <= 2M
                "2000000, 5.75",   // exactly 2M
                "2000001, 5.50",   // > 2M, <= 5M
                "5000000, 5.50",   // exactly 5M
                "5000001, 5.25",   // > 5M, <= 10M
                "10000000, 5.25",  // exactly 10M
                "10000001, 5.00",  // > 10M, <= 20M
                "20000000, 5.00",  // exactly 20M
                "20000001, 4.75"   // > 20M
        })
        @DisplayName("approve loan verifies base rate for amount")
        void baseRateByAmount(long amount, String expectedBaseRate) {
            LoanRequest request = LoanRequest.builder()
                    .id(1L).loanType(LoanType.CASH).interestType(InterestType.FIXED)
                    .amount(BigDecimal.valueOf(amount)).currency(rsd)
                    .loanPurpose("Test").repaymentPeriod(12)
                    .account(account).client(client).status(LoanStatus.PENDING).build();

            when(loanRequestRepository.findById(1L)).thenReturn(Optional.of(request));
            when(loanRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(loanRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(account));
            when(accountRepository.findBankAccountForUpdateByCurrency("22200022", "RSD")).thenReturn(Optional.of(bankAccount));
            when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            lenient().when(installmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            LoanResponseDto result = loanService.approveLoanRequest(1L);

            BigDecimal expectedMargin = new BigDecimal("1.75"); // CASH margin
            BigDecimal expectedEffective = new BigDecimal(expectedBaseRate).add(expectedMargin);
            assertThat(result.getNominalRate()).isEqualByComparingTo(expectedBaseRate);
            assertThat(result.getEffectiveRate()).isEqualByComparingTo(expectedEffective);
        }
    }

    // ===== All loan type margins =====

    @Nested
    @DisplayName("Loan type margin - all types")
    class LoanTypeMarginAll {

        @ParameterizedTest
        @CsvSource({
                "CASH, 1.75",
                "MORTGAGE, 1.50",
                "AUTO, 1.25",
                "REFINANCING, 1.00",
                "STUDENT, 0.75"
        })
        @DisplayName("margin depends on loan type")
        void marginByLoanType(String loanType, String expectedMargin) {
            LoanRequest request = LoanRequest.builder()
                    .id(1L).loanType(LoanType.valueOf(loanType)).interestType(InterestType.FIXED)
                    .amount(BigDecimal.valueOf(100000)).currency(rsd)
                    .loanPurpose("Test").repaymentPeriod(12)
                    .account(account).client(client).status(LoanStatus.PENDING).build();

            when(loanRequestRepository.findById(1L)).thenReturn(Optional.of(request));
            when(loanRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(loanRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(account));
            when(accountRepository.findBankAccountForUpdateByCurrency("22200022", "RSD")).thenReturn(Optional.of(bankAccount));
            when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            lenient().when(installmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            LoanResponseDto result = loanService.approveLoanRequest(1L);

            BigDecimal baseRate = new BigDecimal("6.25"); // 100k <= 500k
            BigDecimal effectiveRate = baseRate.add(new BigDecimal(expectedMargin));
            assertThat(result.getEffectiveRate()).isEqualByComparingTo(effectiveRate);
        }
    }

    // ===== getMyLoans =====

    @Nested
    @DisplayName("getMyLoans")
    class GetMyLoans {

        @Test
        @DisplayName("returns empty page for non-existent client")
        void nonExistentClient() {
            when(clientRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

            Page<LoanResponseDto> result = loanService.getMyLoans("ghost@test.com", PageRequest.of(0, 10));
            assertThat(result.getContent()).isEmpty();
        }

        @Test
        @DisplayName("returns loans for existing client")
        void existingClient() {
            Loan loan = Loan.builder()
                    .id(1L).loanNumber("LN-MY001").loanType(LoanType.CASH)
                    .interestType(InterestType.FIXED).amount(BigDecimal.valueOf(100000))
                    .repaymentPeriod(12).nominalRate(new BigDecimal("6.25"))
                    .effectiveRate(new BigDecimal("8.00")).monthlyPayment(new BigDecimal("8700"))
                    .startDate(LocalDate.now()).endDate(LocalDate.now().plusMonths(12))
                    .remainingDebt(BigDecimal.valueOf(100000)).currency(rsd)
                    .status(LoanStatus.ACTIVE).account(account).client(client)
                    .loanPurpose("Test").build();

            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));
            when(loanRepository.findByClientId(eq(1L), any())).thenReturn(new PageImpl<>(List.of(loan)));

            Page<LoanResponseDto> result = loanService.getMyLoans("stefan@test.com", PageRequest.of(0, 10));
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getLoanNumber()).isEqualTo("LN-MY001");
        }
    }

    // ===== getInstallments =====

    @Nested
    @DisplayName("getInstallments")
    class GetInstallments {

        @Test
        @DisplayName("returns installments for loan")
        void returnsInstallments() {
            LoanInstallment inst = LoanInstallment.builder()
                    .id(1L).amount(new BigDecimal("8700"))
                    .principalAmount(new BigDecimal("8200"))
                    .interestAmount(new BigDecimal("500"))
                    .interestRate(new BigDecimal("8.00")).currency(rsd)
                    .expectedDueDate(LocalDate.now().plusMonths(1)).paid(false).build();

            when(installmentRepository.findByLoanIdOrderByExpectedDueDateAsc(1L)).thenReturn(List.of(inst));

            List<InstallmentResponseDto> result = loanService.getInstallments(1L);
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getPaid()).isFalse();
        }

        @Test
        @DisplayName("returns empty list when no installments")
        void emptyInstallments() {
            when(installmentRepository.findByLoanIdOrderByExpectedDueDateAsc(999L)).thenReturn(List.of());

            List<InstallmentResponseDto> result = loanService.getInstallments(999L);
            assertThat(result).isEmpty();
        }
    }

    // ===== VARIABLE interest type =====

    @Nested
    @DisplayName("Variable interest type in approval")
    class VariableInterestApproval {

        @Test
        @DisplayName("approves loan with VARIABLE interest type")
        void variableInterest() {
            LoanRequest request = LoanRequest.builder()
                    .id(1L).loanType(LoanType.MORTGAGE).interestType(InterestType.VARIABLE)
                    .amount(BigDecimal.valueOf(500000)).currency(rsd)
                    .loanPurpose("House").repaymentPeriod(24)
                    .account(account).client(client).status(LoanStatus.PENDING).build();

            when(loanRequestRepository.findById(1L)).thenReturn(Optional.of(request));
            when(loanRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(loanRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(account));
            when(accountRepository.findBankAccountForUpdateByCurrency("22200022", "RSD")).thenReturn(Optional.of(bankAccount));
            when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            lenient().when(installmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            LoanResponseDto result = loanService.approveLoanRequest(1L);
            assertThat(result.getInterestType()).isEqualTo("VARIABLE");
            assertThat(result.getStatus()).isEqualTo("ACTIVE");
        }
    }
}
