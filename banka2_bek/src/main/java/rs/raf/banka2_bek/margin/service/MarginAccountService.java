package rs.raf.banka2_bek.margin.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.account.model.AccountStatus;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.margin.dto.CreateMarginAccountDto;
import rs.raf.banka2_bek.margin.dto.MarginAccountCheckDto;
import rs.raf.banka2_bek.margin.dto.MarginAccountDto;
import rs.raf.banka2_bek.margin.dto.MarginTransactionDto;
import rs.raf.banka2_bek.margin.event.MarginAccountBlockedEvent;
import rs.raf.banka2_bek.margin.model.MarginAccount;
import rs.raf.banka2_bek.margin.model.MarginAccountStatus;
import rs.raf.banka2_bek.margin.model.MarginTransaction;
import rs.raf.banka2_bek.margin.model.MarginTransactionType;
import rs.raf.banka2_bek.margin.repository.MarginAccountRepository;
import rs.raf.banka2_bek.margin.repository.MarginTransactionRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final ClientRepository clientRepository;
    private final ApplicationEventPublisher eventPublisher;

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
     *
     * @param userId ID korisnika koji kreira margin racun
     * @param dto    DTO sa accountId i initialDeposit
     * @return kreiran MarginAccountDto
     */
    @Transactional
    public MarginAccountDto createForUser(Long userId, CreateMarginAccountDto dto) {
        if (userId == null) {
            throw new IllegalArgumentException("Authenticated user id is required.");
        }
        if (dto == null || dto.getAccountId() == null || dto.getInitialDeposit() == null) {
            throw new IllegalArgumentException("Account id and initial deposit are required.");
        }

        BigDecimal initialDeposit = dto.getInitialDeposit();
        if (initialDeposit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Initial deposit must be greater than zero.");
        }

        var account = accountRepository.findForUpdateById(dto.getAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Account not found."));

        if (account.getClient() == null || !userId.equals(account.getClient().getId())) {
            throw new IllegalStateException("You are not allowed to create a margin account for this base account.");
        }
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Base account must be active.");
        }
        if (!marginAccountRepository.findByAccountId(account.getId()).isEmpty()) {
            throw new IllegalArgumentException("Margin account already exists for this base account.");
        }

        BigDecimal availableBalance = account.getAvailableBalance() == null
                ? BigDecimal.ZERO
                : account.getAvailableBalance();
        if (availableBalance.compareTo(initialDeposit) < 0) {
            throw new IllegalArgumentException("Insufficient available balance for initial margin deposit.");
        }

        BigDecimal divisor = BigDecimal.ONE.subtract(DEFAULT_BANK_PARTICIPATION);
        BigDecimal initialMargin = initialDeposit.divide(divisor, 4, RoundingMode.HALF_UP);
        BigDecimal loanValue = initialMargin.subtract(initialDeposit).setScale(4, RoundingMode.HALF_UP);
        BigDecimal maintenanceMargin = initialMargin.multiply(MAINTENANCE_FACTOR).setScale(4, RoundingMode.HALF_UP);

        account.setAvailableBalance(availableBalance.subtract(initialDeposit));
        if (account.getBalance() != null) {
            account.setBalance(account.getBalance().subtract(initialDeposit));
        }

        MarginAccount savedMarginAccount = marginAccountRepository.save(
                MarginAccount.builder()
                        .account(account)
                        .userId(userId)
                        .initialMargin(initialMargin)
                        .loanValue(loanValue)
                        .maintenanceMargin(maintenanceMargin)
                        .bankParticipation(DEFAULT_BANK_PARTICIPATION)
                        .status(MarginAccountStatus.ACTIVE)
                        .build()
        );

        marginTransactionRepository.save(
                MarginTransaction.builder()
                        .marginAccount(savedMarginAccount)
                        .type(MarginTransactionType.DEPOSIT)
                        .amount(initialDeposit.setScale(4, RoundingMode.HALF_UP))
                        .description("Initial margin deposit")
                        .build()
        );

        log.info("Created margin account {} for user {} on base account {}",
                savedMarginAccount.getId(), userId, account.getId());

        return toDto(savedMarginAccount);
    }

    /**
     * Vraca sve margin racune za autentifikovanog korisnika.
     *
     * @param email email autentifikovanog korisnika
     * @return lista margin racuna
     */
    public List<MarginAccountDto> getMyMarginAccounts(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalStateException("Authenticated user is required.");
        }

        Long clientId = clientRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Only clients can view margin accounts."))
                .getId();

        List<MarginAccountDto> accounts = marginAccountRepository.findByUserId(clientId)
                .stream()
                .map(this::toDto)
                .toList();

        log.info("Fetched {} margin accounts for client {}", accounts.size(), clientId);
        return accounts;
    }

    /**
     * Uplata sredstava na margin racun.
     *
     * @param marginAccountId ID margin racuna
     * @param amount          iznos za uplatu
     */
    @Transactional
    public void deposit(Long marginAccountId, BigDecimal amount, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated())
            throw new IllegalStateException("Not authenticated.");

        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 1)
            throw new IllegalArgumentException("Amount must be a positive number.");

        Long clientId = clientRepository.findByEmail(authentication.getName()).orElseThrow(
                () -> new EntityNotFoundException("Only clients can deposit on margin accounts.")
        ).getId();

        // 1. find MarginAccount by id
        MarginAccount account = marginAccountRepository.findById(marginAccountId)
                .orElseThrow(
                        () -> new EntityNotFoundException("Account with id: " + marginAccountId + " not found.")
                );

        // OWNERSHIP CHECK
        if (!clientId.equals(account.getUserId()))
            throw new IllegalStateException("Only the owner of margin account with id = " + marginAccountId + " can deposit funds.");


        // 2. increase initialMargin for the amount
        account.setInitialMargin(account.getInitialMargin().add(amount));

        // 3. set new maintenanceMargin = initialMargin * MAINTENANCE_FACTOR
        account.setMaintenanceMargin(account.getInitialMargin().multiply(MAINTENANCE_FACTOR));

        // 4. if account could be unblocked -> activate it
        boolean isBlocked = account.getStatus().equals(MarginAccountStatus.BLOCKED);
        if (isBlocked) account.setStatus(MarginAccountStatus.ACTIVE);

        // 5. save marginAccount
        marginAccountRepository.save(account);

        String transactionDescription =
                "Executed transaction. Amount deposited: " + amount + ". Current balance: " + account.getInitialMargin() + ".";

        // 6. create Transaction (type = DEPOSIT)
        MarginTransaction transaction = MarginTransaction.builder()
                .marginAccount(account)
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
     *
     * @param marginAccountId ID margin racuna
     * @param amount          iznos za isplatu
     */
    @Transactional
    public void withdraw(Long marginAccountId, BigDecimal amount, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated())
            throw new IllegalStateException("Not authenticated.");

        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 1)
            throw new IllegalArgumentException("Amount must be a positive number.");


        Long clientId = clientRepository.findByEmail(authentication.getName()).orElseThrow(
                () -> new IllegalStateException("Only clients can withdraw from margin accounts.")
        ).getId();

        // 1. find MarginAccount by marginAccountId, if it doesn't exist exception is thrown
        MarginAccount account = marginAccountRepository.findById(marginAccountId).orElseThrow(
                () -> new EntityNotFoundException("Account with id: " + marginAccountId + " not found.")
        );

        // CHECK ACCOUNT OWNERSHIP
        if (!clientId.equals(account.getUserId()))
            throw new IllegalStateException("Only the owner of margin account with id = " + marginAccountId + " can withdraw funds.");

        // 2. not active accounts can't do withdraw
        if (!account.getStatus().equals(MarginAccountStatus.ACTIVE))
            throw new IllegalStateException("Account with id: " + marginAccountId + " is not active.");

        // 3. is initial_margin - amount < maintenance_margin  <==>  initialMargin - amount >= maintenanceMargin
        boolean withdrawalBelowMaintenance =
                account.getInitialMargin().subtract(amount).compareTo(account.getMaintenanceMargin()) < 0;

        // if dropped below maintenance
        if (withdrawalBelowMaintenance)
            throw new IllegalArgumentException(
                    "Funds in the account cannot be below " + account.getMaintenanceMargin() + " amount."
            );

        // 4. update initialMargin = initialMargin - amount

        account.setInitialMargin(account.getInitialMargin().subtract(amount));

        account.setMaintenanceMargin(account.getInitialMargin().multiply(MAINTENANCE_FACTOR));

        // 5. save margin account
        marginAccountRepository.save(account);

        // 6. create new Transaction (type = WITHDRAWAL)
        String description = "Executed transaction. Amount withdrawn: " + amount + ". Current balance: " + account.getInitialMargin() + ".";

        MarginTransaction transaction = MarginTransaction.builder()
                .marginAccount(account)
                .type(MarginTransactionType.WITHDRAWAL)
                .amount(amount)
                .description(description)
                .build();

        // 7. save margin transaction
        marginTransactionRepository.save(transaction);

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

        // get all about to be blocked accounts
        List<MarginAccountCheckDto> accountsForBlocking = marginAccountRepository.findAccountsForMarginCheck(MarginAccountStatus.ACTIVE.toString());

        marginAccountRepository.blockAccountsWhereMaintenanceExceedsInitial(MarginAccountStatus.BLOCKED.toString());

        for (MarginAccountCheckDto account : accountsForBlocking) {

            // for mail sending logic listen for MarginAccountBlockedEvent publish
            eventPublisher.publishEvent(
                    new MarginAccountBlockedEvent(
                            account.ownerEmail(),
                            account.maintenanceMargin().toString(),
                            account.initialMargin().toString(),
                            account.calculateMaintenanceDeficit().toString()
                    )
            );

            log.warn(
                    "MARGIN CALL: Account {} blocked. initialMargin={}, maintenanceMargin={}",
                    account.marginAccountId(),
                    account.initialMargin(),
                    account.maintenanceMargin()
            );
        }

        log.info("Daily maintenance margin check completed. Amount of blocked accounts : {}.", accountsForBlocking.size());

    }

    /**
     * Vraca istoriju transakcija za dati margin racun.
     *
     * @param marginAccountId ID margin racuna
     * @return lista transakcija sortirana od najnovije
     */
    public List<MarginTransactionDto> getTransactions(Long marginAccountId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated())
            throw new IllegalStateException("Not authenticated.");

        Long clientId = clientRepository.findByEmail(authentication.getName()).orElseThrow(
                () -> new IllegalStateException("Only clients can get the list of margin transactions.")
        ).getId();

        MarginAccount marginAccount = marginAccountRepository.findById(marginAccountId).orElseThrow(
                () -> new EntityNotFoundException("Margin account with id: " + marginAccountId + " does not exist.")
        );

        // CHECK ACCOUNT OWNERSHIP
        if (!marginAccount.getUserId().equals(clientId))
            throw new IllegalStateException("Only the owner of the margin account with id = " + marginAccountId + " can access margin account transactions.");

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


}
