package rs.raf.banka2_bek.interbank.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountStatus;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.interbank.exception.InterbankExceptions;
import rs.raf.banka2_bek.interbank.model.InterbankTransaction;
import rs.raf.banka2_bek.interbank.model.InterbankTransactionStatus;
import rs.raf.banka2_bek.interbank.protocol.*;
import rs.raf.banka2_bek.interbank.repository.InterbankTransactionRepository;
import rs.raf.banka2_bek.order.service.CurrencyConversionService;
import rs.raf.banka2_bek.portfolio.model.Portfolio;
import rs.raf.banka2_bek.portfolio.repository.PortfolioRepository;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.repository.ListingRepository;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TransactionExecutorService {

    private final InterbankMessageService messageService;
    private final InterbankClient client;
    private final BankRoutingService routing;
    private final InterbankTransactionRepository txRepo;
    private final ObjectMapper objectMapper;
    private final AccountRepository accountRepository;
    private final PortfolioRepository portfolioRepository;
    private final InterbankReservationApplier reservationApplier;
    private final ListingRepository listingRepository;
    private final CurrencyConversionService currencyConversionService;

    /**
     * §2.8.5: self-proxy so that @Transactional on phase methods is respected when called
     * from execute() (Spring AOP does not intercept self-invocation through `this`).
     */
    @Lazy
    @Autowired
    TransactionExecutorService self;

    // -------------------------------------------------------------------------
    // Nested record for phase-1 result
    // -------------------------------------------------------------------------

    record Phase1Result(
            TransactionVote vote,
            Map<Integer, IdempotenceKey> keys,
            Map<Integer, Message<Transaction>> envelopes
    ) {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public Transaction formTransaction(
            List<Posting> postings, String message,
            String callNumber, String paymentCode, String paymentPurpose) {

        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : bytes) sb.append(String.format("%02x", b));

        ForeignBankId txId = new ForeignBankId(routing.myRoutingNumber(), sb.toString());
        return new Transaction(postings, txId, message, callNumber, paymentCode, paymentPurpose);
    }

    /**
     * §2.8.5 Coordinator — orchestrates the two-phase commit across all involved banks.
     * Not @Transactional itself: each phase runs in its own local transaction so DB locks
     * are released before network I/O begins.
     */
    public void execute(Transaction tx) {
        Set<Integer> remoteRns = collectRemoteRoutingNumbers(tx);

        if (remoteRns.isEmpty()) {
            // §2.8.4 last paragraph: fully local — two sequential local transactions.
            // Coordinator record must be persisted first so commitLocal/rollbackLocal
            // can find the transactionBody.
            saveCoordinatorState(tx, InterbankTransactionStatus.PREPARING);
            TransactionVote vote = self.prepareLocal(tx);
            if (vote.vote() == TransactionVote.Vote.YES) {
                self.commitLocal(tx.transactionId());
            } else {
                self.rollbackLocal(tx.transactionId());
            }
            return;
        }

        // §2.8.5: promote to coordinator — prepare + log outbound atomically
        Phase1Result phase1 = self.prepareTxPhase(tx, remoteRns);
        if (phase1.vote().vote() == TransactionVote.Vote.NO) return;

        // Network I/O outside any @Transactional
        Map<Integer, TransactionVote> votes = sendPhase1Network(phase1);
        boolean allYes = votes.values().stream().allMatch(v -> v.vote() == TransactionVote.Vote.YES);

        if (allYes) {
            Map<Integer, IdempotenceKey> commitKeys = self.commitTxPhase(tx.transactionId(), remoteRns);
            sendPhase2Network(commitKeys, MessageType.COMMIT_TX, new CommitTransaction(tx.transactionId()));
        } else {
            Map<Integer, IdempotenceKey> rollbackKeys = self.rollbackTxPhase(tx.transactionId(), remoteRns);
            sendPhase2Network(rollbackKeys, MessageType.ROLLBACK_TX, new RollbackTransaction(tx.transactionId()));
        }
    }

    /**
     * §2.8.5: atomically saves coordinator state, validates/reserves locally, and logs
     * outbound NEW_TX messages — all in one DB transaction.
     */
    @Transactional
    Phase1Result prepareTxPhase(Transaction tx, Set<Integer> remoteRns) {
        saveCoordinatorState(tx, InterbankTransactionStatus.PREPARING);

        List<NoVoteReason> violations = doValidateAndReserve(tx);
        if (!violations.isEmpty()) {
            return new Phase1Result(
                    new TransactionVote(TransactionVote.Vote.NO, violations),
                    Map.of(), Map.of());
        }

        Map<Integer, IdempotenceKey> keys = new LinkedHashMap<>();
        Map<Integer, Message<Transaction>> envelopes = new LinkedHashMap<>();

        for (int rn : remoteRns) {
            IdempotenceKey key = messageService.generateKey();
            Message<Transaction> env = new Message<>(key, MessageType.NEW_TX, tx);
            try {
                messageService.recordOutbound(key, rn, MessageType.NEW_TX,
                        objectMapper.writeValueAsString(env), tx.transactionId().id());
            } catch (JsonProcessingException e) {
                throw new InterbankExceptions.InterbankProtocolException(
                        "Failed to serialize NEW_TX for routing " + rn + ": " + e.getMessage());
            }
            keys.put(rn, key);
            envelopes.put(rn, env);
        }

        return new Phase1Result(new TransactionVote(TransactionVote.Vote.YES, List.of()), keys, envelopes);
    }

    /**
     * §2.8.5: atomically commits locally and logs outbound COMMIT_TX messages.
     */
    @Transactional
    Map<Integer, IdempotenceKey> commitTxPhase(ForeignBankId txId, Set<Integer> remoteRns) {
        commitLocal(txId); // direct call — joins this @Transactional

        Map<Integer, IdempotenceKey> keys = new LinkedHashMap<>();
        CommitTransaction body = new CommitTransaction(txId);
        for (int rn : remoteRns) {
            IdempotenceKey key = messageService.generateKey();
            Message<CommitTransaction> env = new Message<>(key, MessageType.COMMIT_TX, body);
            try {
                messageService.recordOutbound(key, rn, MessageType.COMMIT_TX,
                        objectMapper.writeValueAsString(env), txId.id());
            } catch (JsonProcessingException e) {
                throw new InterbankExceptions.InterbankProtocolException(
                        "Failed to serialize COMMIT_TX for routing " + rn + ": " + e.getMessage());
            }
            keys.put(rn, key);
        }
        return keys;
    }

    /**
     * §2.8.8: atomically rolls back locally and logs outbound ROLLBACK_TX messages.
     */
    @Transactional
    Map<Integer, IdempotenceKey> rollbackTxPhase(ForeignBankId txId, Set<Integer> remoteRns) {
        rollbackLocal(txId); // direct call — joins this @Transactional

        Map<Integer, IdempotenceKey> keys = new LinkedHashMap<>();
        RollbackTransaction body = new RollbackTransaction(txId);
        for (int rn : remoteRns) {
            IdempotenceKey key = messageService.generateKey();
            Message<RollbackTransaction> env = new Message<>(key, MessageType.ROLLBACK_TX, body);
            try {
                messageService.recordOutbound(key, rn, MessageType.ROLLBACK_TX,
                        objectMapper.writeValueAsString(env), txId.id());
            } catch (JsonProcessingException e) {
                throw new InterbankExceptions.InterbankProtocolException(
                        "Failed to serialize ROLLBACK_TX for routing " + rn + ": " + e.getMessage());
            }
            keys.put(rn, key);
        }
        return keys;
    }

    @Transactional
    public TransactionVote prepareLocal(Transaction tx) {
        List<NoVoteReason> violations = doValidateAndReserve(tx);
        if (violations.isEmpty())
            return new TransactionVote(TransactionVote.Vote.YES, List.of());
        return new TransactionVote(TransactionVote.Vote.NO, violations);
    }

    @Transactional
    public void commitLocal(ForeignBankId transactionId) {
        InterbankTransaction ibTx = txRepo.findByTransactionRoutingNumberAndTransactionIdString(
                        transactionId.routingNumber(), transactionId.id())
                .orElseThrow(() -> new InterbankExceptions.InterbankProtocolException(
                        "No record for transaction " + transactionId));

        if (ibTx.getStatus() == InterbankTransactionStatus.COMMITTED) return;
        if (ibTx.getStatus() == InterbankTransactionStatus.ROLLED_BACK)
            throw new InterbankExceptions.InterbankProtocolException(
                    "Cannot commit rolled-back transaction " + transactionId);

        Transaction tx;
        try {
            tx = objectMapper.readValue(ibTx.getTransactionBody(), Transaction.class);
        } catch (JsonProcessingException e) {
            throw new InterbankExceptions.InterbankProtocolException(
                    "Failed to parse transaction body: " + e.getMessage());
        }

        for (Posting p : tx.postings()) {
            if (isPostingRemote(p)) continue;
            boolean isDebit = p.amount().compareTo(BigDecimal.ZERO) > 0;
            BigDecimal abs = p.amount().abs();

            if (p.asset() instanceof Asset.Monas m && p.account() instanceof TxAccount.Account a) {
                Account acct = accountRepository.findForUpdateByAccountNumber(a.num())
                        .orElseThrow(() -> new InterbankExceptions.InterbankProtocolException(
                                "Account not found: " + a.num()));
                String fromCcy = m.asset().currency().name();
                String toCcy = acct.getCurrency().getCode();
                BigDecimal converted = currencyConversionService.convert(abs, fromCcy, toCcy);
                reservationApplier.commitMonas(a.num(), converted, isDebit);

            } else if (p.asset() instanceof Asset.Stock s && p.account() instanceof TxAccount.Person pe) {
                Listing listing = listingRepository.findByTicker(s.asset().ticker())
                        .orElseThrow(() -> new InterbankExceptions.InterbankProtocolException(
                                "Listing not found: " + s.asset().ticker()));
                Long userId = Long.parseLong(pe.id().id());
                reservationApplier.commitStock(userId, "CLIENT", listing.getId(),
                        abs.intValueExact(), isDebit);
            }
            // OptionAsset: no-op for T1
        }

        ibTx.setStatus(InterbankTransactionStatus.COMMITTED);
        ibTx.setCommittedAt(LocalDateTime.now());
        ibTx.setLastActivityAt(LocalDateTime.now());
        txRepo.save(ibTx);
    }

    @Transactional
    public void rollbackLocal(ForeignBankId transactionId) {
        InterbankTransaction ibTx = txRepo.findByTransactionRoutingNumberAndTransactionIdString(
                        transactionId.routingNumber(), transactionId.id())
                .orElseThrow(() -> new InterbankExceptions.InterbankProtocolException(
                        "No record for transaction " + transactionId));

        if (ibTx.getStatus() == InterbankTransactionStatus.ROLLED_BACK
                || ibTx.getStatus() == InterbankTransactionStatus.COMMITTED) return;

        Transaction tx;
        try {
            tx = objectMapper.readValue(ibTx.getTransactionBody(), Transaction.class);
        } catch (JsonProcessingException e) {
            throw new InterbankExceptions.InterbankProtocolException(
                    "Failed to parse transaction body: " + e.getMessage());
        }

        for (Posting p : tx.postings()) {
            if (isPostingRemote(p)) continue;
            if (p.amount().compareTo(BigDecimal.ZERO) >= 0) continue; // only credits were reserved

            BigDecimal abs = p.amount().abs();

            if (p.asset() instanceof Asset.Monas m && p.account() instanceof TxAccount.Account a) {
                Account acct = accountRepository.findForUpdateByAccountNumber(a.num())
                        .orElseThrow(() -> new InterbankExceptions.InterbankProtocolException(
                                "Account not found: " + a.num()));
                String fromCcy = m.asset().currency().name();
                String toCcy = acct.getCurrency().getCode();
                BigDecimal converted = currencyConversionService.convert(abs, fromCcy, toCcy);
                reservationApplier.releaseMonas(a.num(), converted);

            } else if (p.asset() instanceof Asset.Stock s && p.account() instanceof TxAccount.Person pe) {
                Listing listing = listingRepository.findByTicker(s.asset().ticker())
                        .orElseThrow(() -> new InterbankExceptions.InterbankProtocolException(
                                "Listing not found: " + s.asset().ticker()));
                Long userId = Long.parseLong(pe.id().id());
                reservationApplier.releaseStock(userId, "CLIENT", listing.getId(), abs.intValueExact());
            }
        }

        ibTx.setStatus(InterbankTransactionStatus.ROLLED_BACK);
        ibTx.setRolledBackAt(LocalDateTime.now());
        ibTx.setLastActivityAt(LocalDateTime.now());
        txRepo.save(ibTx);
    }

    /**
     * §2.12.1: inbound NEW_TX handler. Atomically persists recipient state,
     * validates/reserves, caches the response for idempotency.
     */
    @Transactional
    public TransactionVote handleNewTx(Transaction tx, IdempotenceKey key) {
        Optional<String> cached = messageService.findCachedResponse(key);
        if (cached.isPresent()) {
            try {
                return objectMapper.readValue(cached.get(), TransactionVote.class);
            } catch (JsonProcessingException e) {
                throw new InterbankExceptions.InterbankProtocolException(
                        "Failed to parse cached vote: " + e.getMessage());
            }
        }

        saveRecipientState(tx);

        List<NoVoteReason> violations = doValidateAndReserve(tx);
        TransactionVote vote;
        if (violations.isEmpty()) {
            vote = new TransactionVote(TransactionVote.Vote.YES, List.of());
        } else {
            vote = new TransactionVote(TransactionVote.Vote.NO, violations);
            updateTransactionStatus(tx.transactionId(), InterbankTransactionStatus.ROLLED_BACK, null);
        }

        try {
            messageService.recordInboundResponse(key, MessageType.NEW_TX,
                    objectMapper.writeValueAsString(tx), 200,
                    objectMapper.writeValueAsString(vote),
                    tx.transactionId().id());
        } catch (JsonProcessingException e) {
            throw new InterbankExceptions.InterbankProtocolException(
                    "Failed to serialize handleNewTx response: " + e.getMessage());
        }

        return vote;
    }

    /**
     * §2.12.2: inbound COMMIT_TX handler. Atomically commits and caches the response.
     */
    @Transactional
    public void handleCommitTx(CommitTransaction body, IdempotenceKey key) {
        if (messageService.findCachedResponse(key).isPresent()) return;

        commitLocal(body.transactionId()); // direct call — joins this @Transactional

        try {
            messageService.recordInboundResponse(key, MessageType.COMMIT_TX,
                    objectMapper.writeValueAsString(body), 204, "",
                    body.transactionId().id());
        } catch (JsonProcessingException e) {
            throw new InterbankExceptions.InterbankProtocolException(
                    "Failed to serialize handleCommitTx response: " + e.getMessage());
        }
    }

    /**
     * §2.12.3: inbound ROLLBACK_TX handler. Atomically rolls back and caches the response.
     */
    @Transactional
    public void handleRollbackTx(RollbackTransaction body, IdempotenceKey key) {
        if (messageService.findCachedResponse(key).isPresent()) return;

        rollbackLocal(body.transactionId()); // direct call — joins this @Transactional

        try {
            messageService.recordInboundResponse(key, MessageType.ROLLBACK_TX,
                    objectMapper.writeValueAsString(body), 204, "",
                    body.transactionId().id());
        } catch (JsonProcessingException e) {
            throw new InterbankExceptions.InterbankProtocolException(
                    "Failed to serialize handleRollbackTx response: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Set<Integer> collectRemoteRoutingNumbers(Transaction tx) {
        int myRn = routing.myRoutingNumber();
        Set<Integer> result = new LinkedHashSet<>();
        for (Posting posting : tx.postings()) {
            int rn;
            if (posting.account() instanceof TxAccount.Person p) {
                rn = p.id().routingNumber();
            } else if (posting.account() instanceof TxAccount.Account a) {
                rn = routing.parseRoutingNumber(a.num());
            } else if (posting.account() instanceof TxAccount.Option o) {
                rn = o.id().routingNumber();
            } else {
                continue;
            }
            if (rn != myRn) result.add(rn);
        }
        return result;
    }

    /** Sends pre-logged NEW_TX envelopes, collects votes. Network-only; no @Transactional. */
    private Map<Integer, TransactionVote> sendPhase1Network(Phase1Result phase1) {
        Map<Integer, TransactionVote> votes = new LinkedHashMap<>();
        for (Map.Entry<Integer, Message<Transaction>> entry : phase1.envelopes().entrySet()) {
            int remoteRn = entry.getKey();
            IdempotenceKey key = phase1.keys().get(remoteRn);
            Message<Transaction> envelope = entry.getValue();
            TransactionVote vote;
            try {
                vote = client.sendMessage(remoteRn, MessageType.NEW_TX, envelope, TransactionVote.class);
                if (vote == null) {
                    messageService.markOutboundSent(key, 202, null);
                    vote = new TransactionVote(TransactionVote.Vote.NO, List.of());
                } else {
                    try {
                        messageService.markOutboundSent(key, 200, objectMapper.writeValueAsString(vote));
                    } catch (JsonProcessingException ignored) {
                        messageService.markOutboundSent(key, 200, null);
                    }
                }
            } catch (InterbankExceptions.InterbankCommunicationException e) {
                messageService.markOutboundFailed(key, e.getMessage());
                vote = new TransactionVote(TransactionVote.Vote.NO, List.of());
            }
            votes.put(remoteRn, vote);
        }
        return votes;
    }

    /** Fires pre-logged phase-2 messages (COMMIT_TX or ROLLBACK_TX). Network-only; no @Transactional. */
    private <T> void sendPhase2Network(Map<Integer, IdempotenceKey> keys, MessageType type, T body) {
        for (Map.Entry<Integer, IdempotenceKey> entry : keys.entrySet()) {
            int remoteRn = entry.getKey();
            IdempotenceKey key = entry.getValue();
            Message<T> envelope = new Message<>(key, type, body);
            try {
                client.sendMessage(remoteRn, type, envelope, Void.class);
                messageService.markOutboundSent(key, 204, null);
            } catch (InterbankExceptions.InterbankCommunicationException e) {
                messageService.markOutboundFailed(key, e.getMessage());
            }
        }
    }

    private void saveCoordinatorState(Transaction tx, InterbankTransactionStatus status) {
        try {
            InterbankTransaction ibt = new InterbankTransaction();
            ibt.setTransactionRoutingNumber(tx.transactionId().routingNumber());
            ibt.setTransactionIdString(tx.transactionId().id());
            ibt.setRole(InterbankTransaction.InterbankTransactionRole.INITIATOR);
            ibt.setStatus(status);
            ibt.setTransactionBody(objectMapper.writeValueAsString(tx));
            ibt.setRetryCount(0);
            LocalDateTime now = LocalDateTime.now();
            ibt.setCreatedAt(now);
            ibt.setLastActivityAt(now);
            txRepo.save(ibt);
        } catch (JsonProcessingException e) {
            throw new InterbankExceptions.InterbankProtocolException(
                    "Failed to serialize transaction body: " + e.getMessage());
        }
    }

    private void saveRecipientState(Transaction tx) {
        try {
            InterbankTransaction ibt = new InterbankTransaction();
            ibt.setTransactionRoutingNumber(tx.transactionId().routingNumber());
            ibt.setTransactionIdString(tx.transactionId().id());
            ibt.setRole(InterbankTransaction.InterbankTransactionRole.RECIPIENT);
            ibt.setStatus(InterbankTransactionStatus.PREPARED);
            ibt.setTransactionBody(objectMapper.writeValueAsString(tx));
            ibt.setRetryCount(0);
            LocalDateTime now = LocalDateTime.now();
            ibt.setCreatedAt(now);
            ibt.setLastActivityAt(now);
            txRepo.save(ibt);
        } catch (JsonProcessingException e) {
            throw new InterbankExceptions.InterbankProtocolException(
                    "Failed to serialize transaction body: " + e.getMessage());
        }
    }

    private void updateTransactionStatus(ForeignBankId txId,
            InterbankTransactionStatus status, String failureReason) {
        txRepo.findByTransactionRoutingNumberAndTransactionIdString(
                        txId.routingNumber(), txId.id())
                .ifPresent(ibt -> {
                    ibt.setStatus(status);
                    ibt.setLastActivityAt(LocalDateTime.now());
                    if (failureReason != null) ibt.setFailureReason(failureReason);
                    txRepo.save(ibt);
                });
    }

    private boolean isPostingRemote(Posting p) {
        TxAccount account = p.account();
        if (account instanceof TxAccount.Account a) {
            return !routing.isLocalAccount(a.num());
        } else if (account instanceof TxAccount.Person pe) {
            return pe.id().routingNumber() != routing.myRoutingNumber();
        } else if (account instanceof TxAccount.Option o) {
            return o.id().routingNumber() != routing.myRoutingNumber();
        }
        return false;
    }

    /**
     * Two-pass validate-and-reserve.
     * Pass 1: collect all violations (no DB writes).
     * Pass 2: make reservations only if Pass 1 found no violations.
     */
    private List<NoVoteReason> doValidateAndReserve(Transaction tx) {
        BigDecimal sum = tx.postings().stream()
                .map(Posting::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(BigDecimal.ZERO) != 0) {
            return List.of(new NoVoteReason(NoVoteReason.Reason.UNBALANCED_TX, null));
        }

        List<NoVoteReason> violations = new ArrayList<>();

        for (Posting p : tx.postings()) {
            if (isPostingRemote(p)) continue;

            boolean isCredit = p.amount().compareTo(BigDecimal.ZERO) < 0;
            BigDecimal abs = p.amount().abs();
            Asset asset = p.asset();
            TxAccount account = p.account();

            if (asset instanceof Asset.Monas m && account instanceof TxAccount.Account a) {
                Optional<Account> acctOpt = accountRepository.findByAccountNumber(a.num());
                if (acctOpt.isEmpty() || acctOpt.get().getStatus() != AccountStatus.ACTIVE) {
                    violations.add(new NoVoteReason(NoVoteReason.Reason.NO_SUCH_ACCOUNT, p));
                    continue;
                }
                if (isCredit) {
                    Account acct = acctOpt.get();
                    String postingCcy = m.asset().currency().name();
                    String accountCcy = acct.getCurrency().getCode();
                    if (!postingCcy.equals(accountCcy)) {
                        violations.add(new NoVoteReason(NoVoteReason.Reason.UNACCEPTABLE_ASSET, p));
                        continue;
                    }
                    if (acct.getAvailableBalance().compareTo(abs) < 0) {
                        violations.add(new NoVoteReason(NoVoteReason.Reason.INSUFFICIENT_ASSET, p));
                    }
                }

            } else if (asset instanceof Asset.Stock s && account instanceof TxAccount.Person pe) {
                Long userId;
                try {
                    userId = Long.parseLong(pe.id().id());
                } catch (NumberFormatException e) {
                    violations.add(new NoVoteReason(NoVoteReason.Reason.NO_SUCH_ACCOUNT, p));
                    continue;
                }
                Optional<Listing> listingOpt = listingRepository.findByTicker(s.asset().ticker());
                if (listingOpt.isEmpty()) {
                    violations.add(new NoVoteReason(NoVoteReason.Reason.NO_SUCH_ASSET, p));
                    continue;
                }
                if (isCredit) {
                    Listing listing = listingOpt.get();
                    Optional<Portfolio> portfolioOpt = portfolioRepository
                            .findByUserIdAndUserRoleAndListingIdForUpdate(userId, "CLIENT", listing.getId());
                    if (portfolioOpt.isEmpty()) {
                        violations.add(new NoVoteReason(NoVoteReason.Reason.NO_SUCH_ASSET, p));
                        continue;
                    }
                    if (portfolioOpt.get().getAvailableQuantity() < abs.intValueExact()) {
                        violations.add(new NoVoteReason(NoVoteReason.Reason.INSUFFICIENT_ASSET, p));
                    }
                }

            } else if (asset instanceof Asset.OptionAsset) {
                violations.add(new NoVoteReason(NoVoteReason.Reason.OPTION_NEGOTIATION_NOT_FOUND, p));

            } else {
                violations.add(new NoVoteReason(NoVoteReason.Reason.UNACCEPTABLE_ASSET, p));
            }
        }

        if (!violations.isEmpty()) return violations;

        // Pass 2 — reservations (credit postings only, amount < 0)
        for (Posting p : tx.postings()) {
            if (isPostingRemote(p)) continue;
            if (p.amount().compareTo(BigDecimal.ZERO) >= 0) continue;

            BigDecimal abs = p.amount().abs();
            Asset asset = p.asset();
            TxAccount account = p.account();

            if (asset instanceof Asset.Monas && account instanceof TxAccount.Account a) {
                reservationApplier.reserveMonas(a.num(), abs);

            } else if (asset instanceof Asset.Stock s && account instanceof TxAccount.Person pe) {
                Long userId = Long.parseLong(pe.id().id());
                Listing listing = listingRepository.findByTicker(s.asset().ticker()).orElseThrow();
                reservationApplier.reserveStock(userId, "CLIENT", listing.getId(), abs.intValueExact());
            }
        }

        return violations;
    }
}
