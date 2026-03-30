package rs.raf.banka2_bek.margin.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.auth.config.GlobalSecurityConfig;
import rs.raf.banka2_bek.auth.model.User;
import rs.raf.banka2_bek.auth.repository.UserRepository;
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
import java.util.List;

/**
 * Servis za upravljanje margin racunima.
 * <p>
 * Specifikacija: Celina 3 - Margin racuni
 * <p>
 * Kljucne formule:
 * initialMargin     = deposit / (1 - bankParticipation)
 * loanValue          = initialMargin - deposit
 * maintenanceMargin  = initialMargin * 0.5  (za akcije)
 * <p>
 * Margin call: ako initialMargin padne ispod maintenanceMargin, racun se blokira.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MarginAccountService {

    private final MarginAccountRepository marginAccountRepository;
    private final MarginTransactionRepository marginTransactionRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    /**
     * Podrazumevani procenat ucestva banke (50%)
     */
    private static final BigDecimal DEFAULT_BANK_PARTICIPATION = new BigDecimal("0.50");

    /**
     * Faktor za izracunavanje maintenance margine (50% od initial za akcije)
     */
    private static final BigDecimal MAINTENANCE_FACTOR = new BigDecimal("0.50");

    /**
     * Kreira novi margin racun za korisnika.
     * <p>
     * TODO: Implementirati logiku:
     *   1. Pronaci Account po accountId, baciti exception ako ne postoji
     *   2. Proveriti da li korisnik (userId) ima pristup tom racunu
     *   3. Izracunati:
     *      - bankParticipation = DEFAULT_BANK_PARTICIPATION (0.50)
     *      - initialMargin = initialDeposit / (1 - bankParticipation)
     *        Npr. deposit=5000, bank=50% → initialMargin = 5000 / 0.5 = 10000
     *      - loanValue = initialMargin - initialDeposit
     *        Npr. loanValue = 10000 - 5000 = 5000
     *      - maintenanceMargin = initialMargin * MAINTENANCE_FACTOR
     *        Npr. maintenanceMargin = 10000 * 0.5 = 5000
     *   4. Kreirati MarginAccount entitet sa svim izracunatim vrednostima
     *   5. Sacuvati u bazu
     *   6. Kreirati DEPOSIT MarginTransaction za pocetni depozit
     *   7. Mapirati u MarginAccountDto i vratiti
     *
     * @param userId ID korisnika koji kreira margin racun
     * @param dto    DTO sa accountId i initialDeposit
     * @return kreiran MarginAccountDto
     */
    @Transactional
    public MarginAccountDto createForUser(Long userId, CreateMarginAccountDto dto) {
        // TODO: Implement full account validation and fund transfer
        log.info("Creating margin account for user {} with deposit {}", userId, dto.getInitialDeposit());
        return new MarginAccountDto();
    }

    /**
     * Vraca sve margin racune za autentifikovanog korisnika.
     * <p>
     * TODO: Implementirati logiku:
     *   1. Pronaci korisnika po email-u (User ili Employee)
     *   2. Dohvatiti sve margin racune za tog korisnika (findByUserId)
     *   3. Mapirati u listu MarginAccountDto
     *
     * @param email email autentifikovanog korisnika
     * @return lista margin racuna
     */
    public List<MarginAccountDto> getMyMarginAccounts(String email) {
        // TODO: Look up user by email and fetch their margin accounts
        log.info("Fetching margin accounts for user {}", email);
        return List.of();
    }

    /**
     * Uplata sredstava na margin racun.
     * <p>
     * TODO: Implementirati logiku:
     *   1. Pronaci MarginAccount po ID-ju, baciti exception ako ne postoji
     *   2. Dodati amount na initialMargin
     *   3. Preracunati maintenanceMargin = initialMargin * MAINTENANCE_FACTOR
     *   4. Ako je racun bio BLOCKED i sada initialMargin >= maintenanceMargin:
     *      - Promeniti status na ACTIVE
     *      - Logirati "Margin account {} unblocked after deposit of {}"
     *   5. Sacuvati MarginAccount
     *   6. Kreirati DEPOSIT MarginTransaction
     *   7. Sacuvati MarginTransaction
     *
     * @param marginAccountId ID margin racuna
     * @param amount          iznos za uplatu
     */
    @Transactional
    public void deposit(Long marginAccountId, BigDecimal amount, Authentication authentication) {
        // TODO: Implement deposit logic with margin recalculation

        if (notClient(authentication))
            throw new IllegalStateException("Access denied.");

        User user = userRepository.findByEmail(authentication.getName()).orElseThrow(
                () -> new EntityNotFoundException("Access denied.")
        );

        // 1. find MarginAccount by id
        MarginAccount marginAccount = marginAccountRepository.findById(marginAccountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found with id: " + marginAccountId));

        // OWNERSHIP CHECK
        if (!user.getId().equals(marginAccount.getUserId()))
            throw new IllegalStateException("Access denied.");

        // 2. increase initialMargin for the amount
        BigDecimal updatedInitialMargin = marginAccount.getInitialMargin().add(amount);
        marginAccount.setInitialMargin(updatedInitialMargin);

        // 3. set new maintenanceMargin = initialMargin * MAINTENANCE_FACTOR
        marginAccount.setMaintenanceMargin(marginAccount.getInitialMargin().multiply(MAINTENANCE_FACTOR));


        // initialMargin >= maintenanceMargin?
        boolean overMaintenanceMargin = marginAccount.getInitialMargin().compareTo(marginAccount.getMaintenanceMargin()) >= 0;
        // account.status == BLOCKED ?
        boolean isBlocked = marginAccount.getStatus().equals(MarginAccountStatus.BLOCKED);

        // 4. if account could be unblocked -> activate it
        if (isBlocked && overMaintenanceMargin) marginAccount.setStatus(MarginAccountStatus.ACTIVE);

        // 5. save marginAccount
        marginAccountRepository.save(marginAccount);

        String transactionDescription =
                "Executed transaction. Amount deposited: " + amount + ". Current balance: " + updatedInitialMargin + ".";

        // 6. create Transaction (type = DEPOSIT)
        MarginTransaction transaction = MarginTransaction.builder()
                .marginAccount(marginAccount)
                .type(MarginTransactionType.DEPOSIT)
                .amount(amount)
                .description(transactionDescription)
                .build();

        // 7. save Transaction
        marginTransactionRepository.save(transaction);

        log.info("Deposit {} to margin account {}", amount, marginAccountId);
    }

    /**
     * Isplata sredstava sa margin racuna.
     * <p>
     * TODO: Implementirati logiku:
     *   1. Pronaci MarginAccount po ID-ju, baciti exception ako ne postoji
     *   2. Proveriti da je racun ACTIVE (blokirani racuni ne dozvoljavaju isplate)
     *   3. Proveriti: initialMargin - amount >= maintenanceMargin
     *      - Ako nije, baciti exception "Isplata bi smanjila marginu ispod maintenance nivoa"
     *   4. Smanjiti initialMargin za amount
     *   5. Sacuvati MarginAccount
     *   6. Kreirati WITHDRAWAL MarginTransaction
     *   7. Sacuvati MarginTransaction
     *
     * @param marginAccountId ID margin racuna
     * @param amount          iznos za isplatu
     */
    @Transactional
    public void withdraw(Long marginAccountId, BigDecimal amount, Authentication authentication) {

        // double check for client role (already checked in global security config)

        if (notClient(authentication))
            throw new IllegalStateException("Access denied.");

        User user = userRepository.findByEmail(authentication.getName()).orElseThrow(
                () -> new IllegalStateException("Access denied.")
        );

        // 1. find MarginAccount by marginAccountId, if it doesn't exist exception is thrown
        MarginAccount marginAccount = marginAccountRepository.findById(marginAccountId).orElseThrow(
                () -> new EntityNotFoundException("Account with id: " + marginAccountId)
        );

        // CHECK ACCOUNT OWNERSHIP
        if (!user.getId().equals(marginAccount.getUserId()))
            throw new IllegalStateException("Access denied.");

        // 2. not active accounts can't do withdraw
        if (!marginAccount.getStatus().equals(MarginAccountStatus.ACTIVE))
            throw new IllegalStateException("Account with id: " + marginAccountId + " is not active.");

        // 3. is initial_margin - amount < maintenance_margin  <==>  initialMargin - amount >= maintenanceMargin
        boolean withdrawalBelowMaintenance =
                marginAccount.getInitialMargin().subtract(amount).compareTo(marginAccount.getMaintenanceMargin()) < 0;

        // if dropped below maintenance
        if (withdrawalBelowMaintenance)
            throw new IllegalArgumentException(
                    "Funds in the account cannot be below " + marginAccount.getMaintenanceMargin() + " amount."
            );

        // 4. update initialMargin = initialMargin - amount
        BigDecimal updatedInitialMargin = marginAccount.getInitialMargin().subtract(amount);
        marginAccount.setInitialMargin(updatedInitialMargin);

        // 5. save margin account
        marginAccountRepository.save(marginAccount);

        // 6. create new Transaction (type = WITHDRAWAL)
        String transactionDescription =
                "Executed transaction. Amount withdrawn: " + amount + ". Current balance: " + updatedInitialMargin + ".";

        MarginTransaction marginTransaction = MarginTransaction.builder()
                .marginAccount(marginAccount)
                .type(MarginTransactionType.WITHDRAWAL)
                .amount(amount)
                .description(transactionDescription)
                .build();

        // 7. save margin transaction
        marginTransactionRepository.save(marginTransaction);

        log.info("Withdraw {} from margin account {}", amount, marginAccountId);
    }

    /**
     * Dnevna provera maintenance margine za sve aktivne margin racune.
     * Pokrece se automatski svaki dan u ponoc.
     * <p>
     * TODO: Implementirati logiku
     *   5. TODO (buducnost): Poslati email notifikaciju korisniku o margin call-u
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void checkMaintenanceMargin() {

        log.info("Running daily maintenance margin check...");

        List<MarginAccount> activeAccounts = marginAccountRepository.findByStatus(MarginAccountStatus.ACTIVE);
        int blockedCount = 0;

        for (MarginAccount account : activeAccounts) {
            if (account.getInitialMargin().compareTo(account.getMaintenanceMargin()) < 0) {
                account.setStatus(MarginAccountStatus.BLOCKED);
                marginAccountRepository.save(account);
                blockedCount++;
                log.warn("MARGIN CALL: Account {} blocked. initialMargin={}, maintenanceMargin={}",
                        account.getId(), account.getInitialMargin(), account.getMaintenanceMargin());
            }
        }

        log.info("Daily maintenance margin check completed. Amount of blocked accounts : {}.", blockedCount);

    }

    /**
     * Vraca istoriju transakcija za dati margin racun.
     * @param marginAccountId ID margin racuna
     * @return lista transakcija sortirana od najnovije
     */
    public List<MarginTransactionDto> getTransactions(Long marginAccountId) {
        // TODO: Validate account access before returning transactions
        if (!marginAccountRepository.existsById(marginAccountId)) {
            throw new EntityNotFoundException("Margin account with id: " + marginAccountId + " does not exist.");
        }

        return marginTransactionRepository.findByMarginAccountIdOrderByCreatedAtDesc(marginAccountId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    // ── Helper metode ───────────────────────────────────────────────────────────

    /**
     * TODO: Mapira MarginAccount entitet u MarginAccountDto.
     */
    private MarginAccountDto toDto(MarginAccount marginAccount) {
        return MarginAccountDto.builder()
                .id(marginAccount.getId())
                .accountId(marginAccount.getAccount() != null ? marginAccount.getAccount().getId() : null)
                .accountNumber(marginAccount.getAccount() != null ? marginAccount.getAccount().getAccountNumber() : null)
                .userId(marginAccount.getUserId())
                .initialMargin(marginAccount.getInitialMargin())
                .loanValue(marginAccount.getLoanValue())
                .maintenanceMargin(marginAccount.getMaintenanceMargin())
                .bankParticipation(marginAccount.getBankParticipation())
                .status(marginAccount.getStatus() != null ? marginAccount.getStatus().name() : null)
                .createdAt(marginAccount.getCreatedAt())
                .build();
    }

    private MarginTransactionDto toDto(MarginTransaction transaction) {
        return MarginTransactionDto.builder()
                .id(transaction.getId())
                .marginAccountId(transaction.getMarginAccount() != null ? transaction.getMarginAccount().getId() : null)
                .type(transaction.getType() != null ? transaction.getType().name() : null)
                .amount(transaction.getAmount())
                .description(transaction.getDescription())
                .createdAt(transaction.getCreatedAt())
                .build();
    }


    private boolean notClient(Authentication authentication) {
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (authority.getAuthority() != null && authority.getAuthority().contains("CLIENT"))
                return false;
        }
        return true;
    }

}
