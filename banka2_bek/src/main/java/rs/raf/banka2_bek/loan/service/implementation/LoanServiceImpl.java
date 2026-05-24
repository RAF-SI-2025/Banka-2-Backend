package rs.raf.banka2_bek.loan.service.implementation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.currency.repository.CurrencyRepository;
import rs.raf.banka2_bek.loan.dto.*;
import rs.raf.banka2_bek.loan.model.*;
import rs.raf.banka2_bek.loan.service.LoanService;
import rs.raf.banka2_bek.loan.repository.LoanInstallmentRepository;
import rs.raf.banka2_bek.loan.repository.LoanRepository;
import rs.raf.banka2_bek.loan.repository.LoanRequestRepository;
import rs.raf.banka2_bek.notification.model.NotificationType;
import rs.raf.banka2_bek.notification.service.MailSenderService;
import rs.raf.banka2_bek.notification.service.NotificationService;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

// TODO [B4 - Okidaci notifikacija | Nosilac: Petar Poznanovic]
// Nakon kreiranja kreditnog zahteva i nakon odobravanja/odbijanja kredita,
// pozvati notifikacioni servis da obavesti klijenta.
// Primer tacaka integracije:
//   - createLoanRequest() -> posle save novog LoanRequest:
//       mailNotificationService.sendLoanRequestReceivedEmail(client.getEmail(), loanRequest.getId());
//   - approveLoan() -> posle loan.setStatus(ACTIVE) i save:
//       mailNotificationService.sendLoanApprovedEmail(client.getEmail(), loan.getLoanAmount(), loan.getCurrency());
//   - rejectLoan() -> posle loanRequest.setStatus(REJECTED) i save:
//       mailNotificationService.sendLoanRejectedEmail(client.getEmail(), loanRequest.getId());
// Notifikacioni servis (mailNotificationService) je vec injektovan u ovoj klasi.
@Slf4j
@Service
public class LoanServiceImpl implements LoanService {

    private final LoanRequestRepository loanRequestRepository;
    private final LoanRepository loanRepository;
    private final LoanInstallmentRepository installmentRepository;
    private final AccountRepository accountRepository;
    private final ClientRepository clientRepository;
    private final CurrencyRepository currencyRepository;
    private final MailSenderService mailSenderService;
    private final String bankRegistrationNumber;

    private final NotificationService notificationService;

    public LoanServiceImpl(LoanRequestRepository loanRequestRepository,
                           LoanRepository loanRepository,
                           LoanInstallmentRepository installmentRepository,
                           AccountRepository accountRepository,
                           ClientRepository clientRepository,
                           CurrencyRepository currencyRepository,
                           MailSenderService mailSenderService,
                           @Value("${bank.registration-number}") String bankRegistrationNumber,
                           NotificationService notificationService) {
        this.loanRequestRepository = loanRequestRepository;
        this.loanRepository = loanRepository;
        this.installmentRepository = installmentRepository;
        this.accountRepository = accountRepository;
        this.clientRepository = clientRepository;
        this.currencyRepository = currencyRepository;
        this.mailSenderService = mailSenderService;
        this.bankRegistrationNumber = bankRegistrationNumber;   
        this.notificationService = notificationService;
    }

    @Override
    @Transactional
    public LoanRequestResponseDto createLoanRequest(LoanRequestDto request, String clientEmail) {
        Client client = clientRepository.findByEmail(clientEmail)
                .orElseThrow(() -> new RuntimeException("Klijent nije pronadjen"));

        Account account = accountRepository.findByAccountNumber(request.getAccountNumber())
                .orElseThrow(() -> new RuntimeException("Racun nije pronadjen"));

        if (account.getClient() == null || !account.getClient().getId().equals(client.getId())) {
            throw new RuntimeException("Racun ne pripada klijentu");
        }

        Currency currency = currencyRepository.findByCode(request.getCurrency())
                .orElseThrow(() -> new RuntimeException("Valuta nije podrzana: " + request.getCurrency()));

        if (!account.getCurrency().getCode().equals(currency.getCode())) {
            throw new RuntimeException("Valuta kredita mora da se poklapa sa valutom racuna");
        }

        LoanRequest loanRequest = LoanRequest.builder()
                .loanType(LoanType.valueOf(request.getLoanType()))
                .interestType(InterestType.valueOf(request.getInterestType()))
                .amount(request.getAmount())
                .currency(currency)
                .loanPurpose(request.getLoanPurpose())
                .repaymentPeriod(request.getRepaymentPeriod())
                .account(account)
                .client(client)
                .phoneNumber(request.getPhoneNumber())
                .employmentStatus(request.getEmploymentStatus())
                .monthlyIncome(request.getMonthlyIncome())
                .permanentEmployment(request.getPermanentEmployment())
                .employmentPeriod(request.getEmploymentPeriod())
                .build();

        LoanRequestResponseDto response = toRequestResponse(loanRequestRepository.save(loanRequest));

        try {
            mailSenderService.sendLoanRequestSubmittedMail(
                    client.getEmail(),
                    loanRequest.getLoanType().name(),
                    loanRequest.getAmount(),
                    currency.getCode());
        } catch (Exception e) {
            log.warn("Failed to send loan request notification email", e);
        }

        try {
            notificationService.notify(
                    client.getId(),
                    "CLIENT",
                    NotificationType.LOAN_CREATED,
                    "Zahtev za kredit primljen",
                    "Vaš zahtev za kredit od " + loanRequest.getAmount() + " " + currency.getCode() + " je uspešno podnet i čeka na obradu.",
                    "LOAN_REQUEST",
                    loanRequest.getId()
            );
        } catch (Exception e) {
            log.warn("Failed to send loan created notification: {}", e.getMessage());
        }

        return response;
    }

    @Override
    public Page<LoanRequestResponseDto> getLoanRequests(LoanStatus status, Pageable pageable) {
        if (status != null) {
            return loanRequestRepository.findByStatus(status, pageable).map(this::toRequestResponse);
        }
        return loanRequestRepository.findAll(pageable).map(this::toRequestResponse);
    }

    @Override
    @Transactional
    public LoanResponseDto approveLoanRequest(Long requestId) {
        LoanRequest request = loanRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Zahtev za kredit nije pronadjen"));

        if (request.getStatus() != LoanStatus.PENDING) {
            throw new RuntimeException("Zahtev je vec obradjen");
        }

        request.setStatus(LoanStatus.APPROVED);
        loanRequestRepository.save(request);

        BigDecimal nominalRate = getBaseRate(request.getAmount());
        BigDecimal margin = getMargin(request.getLoanType());
        BigDecimal effectiveRate = nominalRate.add(margin);
        BigDecimal monthlyRate = effectiveRate.divide(BigDecimal.valueOf(1200), 10, RoundingMode.HALF_UP);
        int n = request.getRepaymentPeriod();

        // A = P * r * (1+r)^n / ((1+r)^n - 1)
        BigDecimal onePlusR = BigDecimal.ONE.add(monthlyRate);
        BigDecimal onePlusRn = onePlusR.pow(n, MathContext.DECIMAL128);
        BigDecimal monthlyPayment = request.getAmount()
                .multiply(monthlyRate)
                .multiply(onePlusRn)
                .divide(onePlusRn.subtract(BigDecimal.ONE), 4, RoundingMode.HALF_UP);

        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusMonths(n);

        String loanNumber = "LN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Loan loan = Loan.builder()
                .loanNumber(loanNumber)
                .loanType(request.getLoanType())
                .interestType(request.getInterestType())
                .amount(request.getAmount())
                .repaymentPeriod(n)
                .nominalRate(nominalRate)
                .effectiveRate(effectiveRate)
                .monthlyPayment(monthlyPayment)
                .startDate(startDate)
                .endDate(endDate)
                .remainingDebt(request.getAmount())
                .currency(request.getCurrency())
                .status(LoanStatus.ACTIVE)
                .account(request.getAccount())
                .client(request.getClient())
                .loanPurpose(request.getLoanPurpose())
                .build();

        loan = loanRepository.save(loan);

        // Disburse loan: bank pays from its account, client receives funds
        Account account = accountRepository.findForUpdateById(request.getAccount().getId())
                .orElseThrow(() -> new RuntimeException("Racun klijenta nije pronadjen"));

        String currencyCode = request.getCurrency().getCode();
        Account bankAccount = accountRepository.findBankAccountForUpdateByCurrency(bankRegistrationNumber, currencyCode)
                .orElseThrow(() -> new RuntimeException("Bankovski racun za " + currencyCode + " nije pronadjen"));

        if (bankAccount.getAvailableBalance().compareTo(request.getAmount()) < 0) {
            throw new RuntimeException("Banka nema dovoljno sredstava za isplatu kredita");
        }

        // Deduct from bank
        bankAccount.setBalance(bankAccount.getBalance().subtract(request.getAmount()));
        bankAccount.setAvailableBalance(bankAccount.getAvailableBalance().subtract(request.getAmount()));
        accountRepository.save(bankAccount);

        // Credit to client
        account.setBalance(account.getBalance().add(request.getAmount()));
        account.setAvailableBalance(account.getAvailableBalance().add(request.getAmount()));
        accountRepository.save(account);

        // Generate installments with principal/interest breakdown
        BigDecimal remainingPrincipal = request.getAmount();
        for (int i = 1; i <= n; i++) {
            BigDecimal interestPortion = remainingPrincipal.multiply(monthlyRate).setScale(4, RoundingMode.HALF_UP);
            BigDecimal principalPortion = monthlyPayment.subtract(interestPortion);
            if (i == n) {
                principalPortion = remainingPrincipal;
            }
            remainingPrincipal = remainingPrincipal.subtract(principalPortion);

            LoanInstallment installment = LoanInstallment.builder()
                    .loan(loan)
                    .amount(monthlyPayment)
                    .principalAmount(principalPortion)
                    .interestAmount(interestPortion)
                    .interestRate(effectiveRate)
                    .currency(request.getCurrency())
                    .expectedDueDate(startDate.plusMonths(i))
                    .paid(false)
                    .build();
            installmentRepository.save(installment);
        }

        try {
            mailSenderService.sendLoanApprovedMail(
                    request.getClient().getEmail(),
                    loan.getLoanNumber(),
                    loan.getAmount(),
                    loan.getCurrency().getCode(),
                    loan.getMonthlyPayment(),
                    loan.getStartDate());
        } catch (Exception e) {
            log.warn("Failed to send loan approval notification email", e);
        }

        try {
            notificationService.notify(
                    request.getClient().getId(),
                    "CLIENT",
                    NotificationType.LOAN_APPROVED,
                    "Kredit odobren",
                    "Vaš kredit od " + loan.getAmount() + " " + loan.getCurrency().getCode() + " je odobren i sredstva su preneta na vaš račun.",
                    "LOAN",
                    loan.getId()
            );
        } catch (Exception e) {
            log.warn("Failed to send loan approved notification: {}", e.getMessage());
        }

        return toLoanResponse(loan);
    }

    @Override
    @Transactional
    public LoanRequestResponseDto rejectLoanRequest(Long requestId) {
        LoanRequest request = loanRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Zahtev za kredit nije pronadjen"));

        if (request.getStatus() != LoanStatus.PENDING) {
            throw new RuntimeException("Zahtev je vec obradjen");
        }

        request.setStatus(LoanStatus.REJECTED);
        LoanRequestResponseDto response = toRequestResponse(loanRequestRepository.save(request));

        try {
            mailSenderService.sendLoanRejectedMail(
                    request.getClient().getEmail(),
                    request.getLoanType().name(),
                    request.getAmount(),
                    request.getCurrency().getCode());
        } catch (Exception e) {
            log.warn("Failed to send loan rejection notification email", e);
        }

        return response;
    }

    @Override
    public Page<LoanResponseDto> getMyLoans(String clientEmail, Pageable pageable) {
        Client client = clientRepository.findByEmail(clientEmail).orElse(null);
        if (client == null) return Page.empty(pageable);
        return loanRepository.findByClientId(client.getId(), pageable).map(this::toLoanResponse);
    }

    @Override
    public Page<LoanResponseDto> getAllLoans(LoanType loanType, LoanStatus status, String accountNumber, Pageable pageable) {
        return loanRepository.findWithFilters(loanType, status, accountNumber, pageable).map(this::toLoanResponse);
    }

    @Override
    public LoanResponseDto getLoanById(Long loanId) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new RuntimeException("Kredit nije pronadjen"));
        return toLoanResponse(loan);
    }

    @Override
    public List<InstallmentResponseDto> getInstallments(Long loanId) {
        return installmentRepository.findByLoanIdOrderByExpectedDueDateAsc(loanId).stream()
                .map(this::toInstallmentResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<LoanRequestResponseDto> getMyLoanRequests(String clientEmail) {
        Client client = clientRepository.findByEmail(clientEmail).orElse(null);
        if (client == null) return List.of();
        return loanRequestRepository.findByClientIdOrderByCreatedAtDesc(client.getId()).stream()
                .map(this::toRequestResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public LoanResponseDto earlyRepayment(Long loanId, String clientEmail) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new RuntimeException("Kredit nije pronadjen"));

        if (loan.getStatus() == LoanStatus.REJECTED || loan.getStatus() == LoanStatus.PENDING) {
            throw new RuntimeException("Kredit nije aktivan");
        }
        if (loan.getStatus() == LoanStatus.PAID || loan.getStatus() == LoanStatus.PAID_OFF) {
            throw new RuntimeException("Kredit je vec otplacen");
        }

        Client client = clientRepository.findByEmail(clientEmail)
                .orElseThrow(() -> new RuntimeException("Klijent nije pronadjen"));

        if (!loan.getClient().getId().equals(client.getId())) {
            throw new RuntimeException("Kredit ne pripada klijentu");
        }

        // Izracunaj ukupan iznos za otplatu: preostali principal + neplacena kamata
        List<LoanInstallment> allInstallments = installmentRepository.findByLoanIdOrderByExpectedDueDateAsc(loanId);
        BigDecimal remainingPrincipal = loan.getRemainingDebt();
        BigDecimal unpaidInterest = allInstallments.stream()
                .filter(i -> !Boolean.TRUE.equals(i.getPaid()))
                .map(i -> i.getInterestAmount() != null ? i.getInterestAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal payoffAmount = remainingPrincipal.add(unpaidInterest);

        if (payoffAmount.compareTo(BigDecimal.ZERO) <= 0) {
            loan.setRemainingDebt(BigDecimal.ZERO);
            loan.setStatus(LoanStatus.PAID_OFF);
            loan.setEndDate(LocalDate.now());
            return toLoanResponse(loanRepository.save(loan));
        }

        Account account = accountRepository.findForUpdateById(loan.getAccount().getId())
                .orElseThrow(() -> new RuntimeException("Racun klijenta nije pronadjen"));

        if (!account.getCurrency().getId().equals(loan.getCurrency().getId())) {
            throw new RuntimeException("Valuta racuna i kredita se razlikuju");
        }

        if (account.getAvailableBalance().compareTo(payoffAmount) < 0) {
            throw new RuntimeException("Nedovoljno sredstava na racunu (potrebno: " +
                    payoffAmount + " = " + remainingPrincipal + " principal + " + unpaidInterest + " kamata)");
        }

        // Deduct from client
        account.setBalance(account.getBalance().subtract(payoffAmount));
        account.setAvailableBalance(account.getAvailableBalance().subtract(payoffAmount));
        accountRepository.save(account);

        // Credit to bank account
        String currencyCode = loan.getCurrency().getCode();
        Account bankAccount = accountRepository.findBankAccountForUpdateByCurrency(bankRegistrationNumber, currencyCode)
                .orElseThrow(() -> new RuntimeException("Bankovski racun za " + currencyCode + " nije pronadjen"));
        bankAccount.setBalance(bankAccount.getBalance().add(payoffAmount));
        bankAccount.setAvailableBalance(bankAccount.getAvailableBalance().add(payoffAmount));
        accountRepository.save(bankAccount);

        LocalDate today = LocalDate.now();
        for (LoanInstallment installment : allInstallments) {
            if (!Boolean.TRUE.equals(installment.getPaid())) {
                installment.setPaid(true);
                installment.setActualDueDate(today);
                installmentRepository.save(installment);
            }
        }

        loan.setRemainingDebt(BigDecimal.ZERO);
        loan.setStatus(LoanStatus.PAID_OFF);
        loan.setEndDate(today);
        loanRepository.save(loan);

        return toLoanResponse(loan);
    }

    // --- Interest rate tables from spec ---

    private BigDecimal getBaseRate(BigDecimal amount) {
        // Convert to RSD equivalent for rate lookup (simplified: assume RSD or use amount directly)
        double amt = amount.doubleValue();
        if (amt <= 500_000) return new BigDecimal("6.25");
        if (amt <= 1_000_000) return new BigDecimal("6.00");
        if (amt <= 2_000_000) return new BigDecimal("5.75");
        if (amt <= 5_000_000) return new BigDecimal("5.50");
        if (amt <= 10_000_000) return new BigDecimal("5.25");
        if (amt <= 20_000_000) return new BigDecimal("5.00");
        return new BigDecimal("4.75");
    }

    private BigDecimal getMargin(LoanType type) {
        return switch (type) {
            case CASH -> new BigDecimal("1.75");
            case MORTGAGE -> new BigDecimal("1.50");
            case AUTO -> new BigDecimal("1.25");
            case REFINANCING -> new BigDecimal("1.00");
            case STUDENT -> new BigDecimal("0.75");
        };
    }

    // --- Mappers ---

    private LoanRequestResponseDto toRequestResponse(LoanRequest r) {
        return LoanRequestResponseDto.builder()
                .id(r.getId())
                .loanType(r.getLoanType().name())
                .interestType(r.getInterestType().name())
                .amount(r.getAmount())
                .currency(r.getCurrency().getCode())
                .loanPurpose(r.getLoanPurpose())
                .repaymentPeriod(r.getRepaymentPeriod())
                .accountNumber(r.getAccount().getAccountNumber())
                .phoneNumber(r.getPhoneNumber())
                .employmentStatus(r.getEmploymentStatus())
                .monthlyIncome(r.getMonthlyIncome())
                .permanentEmployment(r.getPermanentEmployment())
                .employmentPeriod(r.getEmploymentPeriod())
                .status(r.getStatus().name())
                .createdAt(r.getCreatedAt())
                .clientEmail(r.getClient().getEmail())
                .clientName(r.getClient().getFirstName() + " " + r.getClient().getLastName())
                .build();
    }

    private LoanResponseDto toLoanResponse(Loan l) {
        return LoanResponseDto.builder()
                .id(l.getId())
                .loanNumber(l.getLoanNumber())
                .loanType(l.getLoanType().name())
                .interestType(l.getInterestType().name())
                .amount(l.getAmount())
                .repaymentPeriod(l.getRepaymentPeriod())
                .nominalRate(l.getNominalRate())
                .effectiveRate(l.getEffectiveRate())
                .monthlyPayment(l.getMonthlyPayment())
                .startDate(l.getStartDate())
                .endDate(l.getEndDate())
                .remainingDebt(l.getRemainingDebt())
                .currency(l.getCurrency().getCode())
                .status(l.getStatus().name())
                .accountNumber(l.getAccount().getAccountNumber())
                .loanPurpose(l.getLoanPurpose())
                .createdAt(l.getCreatedAt())
                .build();
    }

    private InstallmentResponseDto toInstallmentResponse(LoanInstallment i) {
        return InstallmentResponseDto.builder()
                .id(i.getId())
                .amount(i.getAmount())
                .principalAmount(i.getPrincipalAmount())
                .interestAmount(i.getInterestAmount())
                .interestRate(i.getInterestRate())
                .currency(i.getCurrency().getCode())
                .expectedDueDate(i.getExpectedDueDate())
                .actualDueDate(i.getActualDueDate())
                .paid(i.getPaid())
                .build();
    }
}
