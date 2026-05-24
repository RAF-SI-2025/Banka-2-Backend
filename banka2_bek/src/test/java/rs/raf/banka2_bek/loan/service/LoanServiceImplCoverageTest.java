package rs.raf.banka2_bek.loan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountStatus;
import rs.raf.banka2_bek.account.model.AccountType;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.currency.repository.CurrencyRepository;
import rs.raf.banka2_bek.loan.dto.LoanResponseDto;
import rs.raf.banka2_bek.loan.model.*;
import rs.raf.banka2_bek.loan.repository.LoanInstallmentRepository;
import rs.raf.banka2_bek.loan.repository.LoanRepository;
import rs.raf.banka2_bek.loan.repository.LoanRequestRepository;
import rs.raf.banka2_bek.loan.service.implementation.LoanServiceImpl;
import rs.raf.banka2_bek.notification.service.MailSenderService;
import rs.raf.banka2_bek.notification.service.NotificationService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Coverage test targeting remaining missed branches/lines in LoanServiceImpl.earlyRepayment():
 *  - Client not found during earlyRepayment (L309)
 *  - Account not found during earlyRepayment (L333)
 *  - Branch for already-paid installment (L359 false side)
 */
@ExtendWith(MockitoExtension.class)
class LoanServiceImplCoverageTest {

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
                .balance(BigDecimal.valueOf(200000))
                .availableBalance(BigDecimal.valueOf(200000))
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

    private Loan activeLoan() {
        return Loan.builder()
                .id(1L).loanNumber("LN-COV001").loanType(LoanType.CASH)
                .interestType(InterestType.FIXED).amount(BigDecimal.valueOf(100000))
                .repaymentPeriod(12).nominalRate(new BigDecimal("6.25"))
                .effectiveRate(new BigDecimal("8.00")).monthlyPayment(new BigDecimal("8700"))
                .startDate(LocalDate.now().minusMonths(3)).endDate(LocalDate.now().plusMonths(9))
                .remainingDebt(BigDecimal.valueOf(75000)).currency(rsd)
                .status(LoanStatus.ACTIVE).account(account).client(client)
                .loanPurpose("Test").build();
    }

    @Test
    @DisplayName("earlyRepayment throws when client (by email) not found")
    void earlyRepayment_clientNotFound() {
        Loan loan = activeLoan();
        when(loanRepository.findById(1L)).thenReturn(Optional.of(loan));
        when(clientRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loanService.earlyRepayment(1L, "ghost@test.com"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Klijent nije pronadjen");
    }

    @Test
    @DisplayName("earlyRepayment throws when client account (findForUpdateById) not found")
    void earlyRepayment_accountNotFound() {
        Loan loan = activeLoan();

        LoanInstallment unpaid = LoanInstallment.builder()
                .id(1L).loan(loan).amount(new BigDecimal("8700"))
                .principalAmount(new BigDecimal("8200"))
                .interestAmount(new BigDecimal("500"))
                .paid(false).build();

        when(loanRepository.findById(1L)).thenReturn(Optional.of(loan));
        when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));
        when(installmentRepository.findByLoanIdOrderByExpectedDueDateAsc(1L)).thenReturn(List.of(unpaid));
        when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loanService.earlyRepayment(1L, "stefan@test.com"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Racun klijenta nije pronadjen");
    }

    @Test
    @DisplayName("earlyRepayment skips already-paid installments (L359 false branch)")
    void earlyRepayment_skipsAlreadyPaidInstallment() {
        Loan loan = activeLoan();
        loan.setRemainingDebt(BigDecimal.valueOf(8200));

        LoanInstallment paidInst = LoanInstallment.builder()
                .id(1L).loan(loan).amount(new BigDecimal("8700"))
                .principalAmount(new BigDecimal("8200"))
                .interestAmount(new BigDecimal("500"))
                .paid(true).build();

        LoanInstallment unpaidInst = LoanInstallment.builder()
                .id(2L).loan(loan).amount(new BigDecimal("8700"))
                .principalAmount(new BigDecimal("8200"))
                .interestAmount(new BigDecimal("500"))
                .paid(false).build();

        when(loanRepository.findById(1L)).thenReturn(Optional.of(loan));
        when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));
        when(installmentRepository.findByLoanIdOrderByExpectedDueDateAsc(1L))
                .thenReturn(List.of(paidInst, unpaidInst));
        when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(account));
        when(accountRepository.findBankAccountForUpdateByCurrency("22200022", "RSD"))
                .thenReturn(Optional.of(bankAccount));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(installmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(loanRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LoanResponseDto result = loanService.earlyRepayment(1L, "stefan@test.com");

        assertThat(result.getStatus()).isEqualTo("PAID_OFF");
        // Paid installment remains with its original actualDueDate (null because never set)
        // Only the unpaid installment should have been flipped to paid
        assertThat(paidInst.getPaid()).isTrue();
        assertThat(unpaidInst.getPaid()).isTrue();
        assertThat(unpaidInst.getActualDueDate()).isEqualTo(LocalDate.now());
    }
}
