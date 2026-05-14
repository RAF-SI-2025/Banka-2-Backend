package rs.raf.banka2_bek.savings.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountStatus;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.auth.util.UserContext;
import rs.raf.banka2_bek.auth.util.UserResolver;
import rs.raf.banka2_bek.otp.service.OtpService;
import rs.raf.banka2_bek.savings.dto.OpenDepositDto;
import rs.raf.banka2_bek.savings.dto.SavingsDepositDto;
import rs.raf.banka2_bek.savings.dto.SavingsTransactionDto;
import rs.raf.banka2_bek.savings.dto.ToggleAutoRenewDto;
import rs.raf.banka2_bek.savings.dto.WithdrawEarlyDto;
import rs.raf.banka2_bek.savings.entity.SavingsDeposit;
import rs.raf.banka2_bek.savings.entity.SavingsDepositStatus;
import rs.raf.banka2_bek.savings.entity.SavingsInterestRate;
import rs.raf.banka2_bek.savings.entity.SavingsTransaction;
import rs.raf.banka2_bek.savings.entity.SavingsTransactionType;
import rs.raf.banka2_bek.savings.exception.SavingsDepositNotFoundException;
import rs.raf.banka2_bek.savings.mapper.SavingsMapper;
import rs.raf.banka2_bek.savings.repository.SavingsDepositRepository;
import rs.raf.banka2_bek.savings.repository.SavingsTransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class SavingsDepositService {

    private static final Set<Integer> VALID_TERMS = Set.of(3, 6, 12, 24, 36);

    private static final Map<String, BigDecimal> MIN_DEPOSIT = Map.of(
            "RSD", new BigDecimal("10000"),
            "JPY", new BigDecimal("10000"),
            "EUR", new BigDecimal("100"),
            "USD", new BigDecimal("100"),
            "CHF", new BigDecimal("100"),
            "GBP", new BigDecimal("100"),
            "CAD", new BigDecimal("100"),
            "AUD", new BigDecimal("100")
    );

    private final SavingsDepositRepository depositRepo;
    private final SavingsTransactionRepository txRepo;
    private final SavingsInterestRateService rateService;
    private final SavingsMapper mapper;
    private final AccountRepository accountRepo;
    private final UserResolver userResolver;
    private final OtpService otpService;

    @Value("${bank.registration-number}")
    private String bankRegistrationNumber;

    @Transactional
    public SavingsDepositDto openDeposit(OpenDepositDto dto) {
        UserContext me = userResolver.resolveCurrent();
        if (!me.isClient()) {
            throw new AccessDeniedException("Samo klijenti mogu otvarati orocene depozite.");
        }
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        Map<String, Object> otpResult = otpService.verify(email, dto.getOtpCode());
        if (!Boolean.TRUE.equals(otpResult.get("verified"))) {
            throw new AccessDeniedException("OTP verifikacija nije uspela.");
        }

        if (!VALID_TERMS.contains(dto.getTermMonths())) {
            throw new IllegalArgumentException("Rok mora biti jedan od: 3, 6, 12, 24, 36 meseci");
        }

        Account source = accountRepo.findById(dto.getSourceAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Izvorni racun ne postoji"));
        verifyAccountOwnership(source, me.userId());
        if (source.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Izvorni racun nije aktivan");
        }

        Account linked = accountRepo.findById(dto.getLinkedAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Povezani racun ne postoji"));
        verifyAccountOwnership(linked, me.userId());
        if (linked.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Povezani racun nije aktivan");
        }

        if (!source.getCurrency().getId().equals(linked.getCurrency().getId())) {
            throw new IllegalArgumentException("Izvorni i povezani racun moraju biti u istoj valuti");
        }

        String currencyCode = source.getCurrency().getCode();
        BigDecimal min = MIN_DEPOSIT.getOrDefault(currencyCode, new BigDecimal("100"));
        if (dto.getPrincipalAmount().compareTo(min) < 0) {
            throw new IllegalArgumentException(
                "Minimalan iznos depozita u " + currencyCode + " je " + min);
        }

        SavingsInterestRate rate = rateService.findActive(source.getCurrency().getId(), dto.getTermMonths())
                .orElseThrow(() -> new IllegalArgumentException(
                    "Kamatna stopa nije dostupna za " + currencyCode + " rok " + dto.getTermMonths() + " meseci"));

        if (source.getAvailableBalance().compareTo(dto.getPrincipalAmount()) < 0) {
            throw new IllegalArgumentException(
                "Nedovoljno raspolozivih sredstava na racunu " + source.getAccountNumber());
        }

        source.setBalance(source.getBalance().subtract(dto.getPrincipalAmount()));
        source.setAvailableBalance(source.getAvailableBalance().subtract(dto.getPrincipalAmount()));
        accountRepo.save(source);

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate maturity = today.plusMonths(dto.getTermMonths());
        LocalDate firstInterest = today.plusMonths(1);

        SavingsDeposit deposit = SavingsDeposit.builder()
                .clientId(me.userId())
                .linkedAccountId(linked.getId())
                .principalAmount(dto.getPrincipalAmount())
                .currency(source.getCurrency())
                .termMonths(dto.getTermMonths())
                .annualInterestRate(rate.getAnnualRate())
                .startDate(today)
                .maturityDate(maturity)
                .nextInterestPaymentDate(firstInterest)
                .totalInterestPaid(BigDecimal.ZERO)
                .autoRenew(dto.getAutoRenew())
                .status(SavingsDepositStatus.ACTIVE)
                .build();
        deposit = depositRepo.save(deposit);

        txRepo.save(SavingsTransaction.builder()
                .deposit(deposit)
                .type(SavingsTransactionType.OPEN)
                .amount(dto.getPrincipalAmount())
                .currency(source.getCurrency())
                .processedDate(today)
                .description("Otvaranje depozita rok=" + dto.getTermMonths() + "m stopa=" + rate.getAnnualRate() + "% p.a.")
                .build());

        log.info("Otvoren stedni depozit id={} client={} principal={} {} term={}m",
                deposit.getId(), me.userId(), dto.getPrincipalAmount(), currencyCode, dto.getTermMonths());

        return mapper.toDepositDto(deposit);
    }

    @Transactional(readOnly = true)
    public List<SavingsDepositDto> listMyDeposits() {
        UserContext me = userResolver.resolveCurrent();
        return depositRepo.findByClientIdOrderByCreatedAtDesc(me.userId())
                .stream().map(mapper::toDepositDto).toList();
    }

    @Transactional(readOnly = true)
    public SavingsDepositDto getDeposit(Long id) {
        SavingsDeposit d = depositRepo.findById(id)
                .orElseThrow(() -> new SavingsDepositNotFoundException(id));
        UserContext me = userResolver.resolveCurrent();
        verifyDepositAccess(d, me);
        return mapper.toDepositDto(d);
    }

    @Transactional(readOnly = true)
    public List<SavingsTransactionDto> listDepositTransactions(Long depositId) {
        SavingsDeposit d = depositRepo.findById(depositId)
                .orElseThrow(() -> new SavingsDepositNotFoundException(depositId));
        UserContext me = userResolver.resolveCurrent();
        verifyDepositAccess(d, me);
        return txRepo.findByDepositIdOrderByCreatedAtDesc(depositId)
                .stream().map(mapper::toTransactionDto).toList();
    }

    @Transactional
    public SavingsDepositDto toggleAutoRenew(Long depositId, ToggleAutoRenewDto dto) {
        SavingsDeposit d = depositRepo.findById(depositId)
                .orElseThrow(() -> new SavingsDepositNotFoundException(depositId));
        UserContext me = userResolver.resolveCurrent();
        verifyOwnership(d, me);
        if (d.getStatus() != SavingsDepositStatus.ACTIVE) {
            throw new IllegalArgumentException("Auto-obnova se moze menjati samo na aktivnim depozitima");
        }
        d.setAutoRenew(dto.getAutoRenew());
        return mapper.toDepositDto(depositRepo.save(d));
    }

    @Transactional
    public SavingsDepositDto withdrawEarly(Long depositId, WithdrawEarlyDto dto) {
        SavingsDeposit d = depositRepo.findById(depositId)
                .orElseThrow(() -> new SavingsDepositNotFoundException(depositId));
        UserContext me = userResolver.resolveCurrent();
        verifyOwnership(d, me);
        if (d.getStatus() != SavingsDepositStatus.ACTIVE) {
            throw new IllegalArgumentException("Depozit nije aktivan");
        }

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Map<String, Object> otpResult = otpService.verify(email, dto.getOtpCode());
        if (!Boolean.TRUE.equals(otpResult.get("verified"))) {
            throw new AccessDeniedException("OTP verifikacija nije uspela.");
        }

        BigDecimal penalty = SavingsCalculator.earlyWithdrawalPenalty(d.getPrincipalAmount());
        BigDecimal returned = d.getPrincipalAmount().subtract(penalty);

        Account linked = accountRepo.findForUpdateById(d.getLinkedAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Povezani racun ne postoji"));
        creditAccount(linked, returned);

        Account bankAccount = accountRepo.findBankAccountByCurrencyId(
                bankRegistrationNumber, d.getCurrency().getId())
                .orElseThrow(() -> new IllegalStateException(
                    "Banka nema racun u valuti " + d.getCurrency().getCode()));
        creditAccount(bankAccount, penalty);

        d.setStatus(SavingsDepositStatus.WITHDRAWN_EARLY);
        depositRepo.save(d);

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        txRepo.save(SavingsTransaction.builder()
                .deposit(d).type(SavingsTransactionType.EARLY_WITHDRAWAL_PRINCIPAL)
                .amount(returned).currency(d.getCurrency()).processedDate(today)
                .description("Raskid pre dospeca - glavnica umanjena za 1% penal")
                .build());
        txRepo.save(SavingsTransaction.builder()
                .deposit(d).type(SavingsTransactionType.EARLY_WITHDRAWAL_PENALTY)
                .amount(penalty).currency(d.getCurrency()).processedDate(today)
                .description("Penal 1% glavnice")
                .build());

        log.info("Raskinut depozit id={} client={} penalty={}",
                d.getId(), me.userId(), penalty);

        return mapper.toDepositDto(d);
    }

    private void verifyAccountOwnership(Account account, Long clientId) {
        if (account.getClient() == null || !account.getClient().getId().equals(clientId)) {
            throw new AccessDeniedException("Racun " + account.getAccountNumber() + " ne pripada korisniku.");
        }
    }

    private void verifyOwnership(SavingsDeposit d, UserContext me) {
        if (!me.isClient() || !d.getClientId().equals(me.userId())) {
            throw new AccessDeniedException("Depozit ne pripada korisniku.");
        }
    }

    private void verifyDepositAccess(SavingsDeposit d, UserContext me) {
        if (me.isEmployee()) {
            throw new AccessDeniedException("Zaposleni koriste /admin/savings/* rute.");
        }
        if (me.isClient() && !d.getClientId().equals(me.userId())) {
            throw new AccessDeniedException("Depozit ne pripada korisniku.");
        }
    }

    private void creditAccount(Account account, BigDecimal amount) {
        account.setBalance(account.getBalance().add(amount));
        account.setAvailableBalance(account.getAvailableBalance().add(amount));
        accountRepo.save(account);
    }
}
