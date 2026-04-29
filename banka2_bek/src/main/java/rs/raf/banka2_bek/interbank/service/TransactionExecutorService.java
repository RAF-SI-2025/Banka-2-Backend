package rs.raf.banka2_bek.interbank.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.interbank.exception.InterbankExceptions;
import rs.raf.banka2_bek.interbank.model.InterbankTransaction;
import rs.raf.banka2_bek.interbank.model.InterbankTransactionStatus;
import rs.raf.banka2_bek.interbank.protocol.*;
import rs.raf.banka2_bek.interbank.repository.InterbankTransactionRepository;
import java.time.LocalDateTime;
import java.util.*;
import rs.raf.banka2_bek.interbank.protocol.*;

import java.util.List;

/*
================================================================================
 TODO — TRANSACTION EXECUTOR (PROTOKOL §2.8)
 Zaduzen: BE tim
 Spec ref: A protocol for bank-to-bank asset exchange.htm, sekcije:
   §2.8.3 Transaction formation
   §2.8.4 Local transaction execution (2PC: prepare/commit local)
   §2.8.5 Remote transaction execution (Initiating Bank coordinator)
   §2.8.6 Verification of received transactions
   §2.8.7 Local transaction rollback
   §2.8.8 Remote transaction rollback
--------------------------------------------------------------------------------

GENERIC EXECUTOR — sve interbank operacije (placanja, OTC option exercise,
forex, sta god) se izrazavaju kroz Transaction objekat sa Postings. Nema
posebnog payment service-a — Transaction sa MONAS Asset-om JE placanje;
Transaction sa OPTION Asset + STOCK Asset JE OTC exercise; itd.

KAO INICIJATOR (IB = Initiating Bank):

 1. Transaction formTransaction(List<Posting> postings, String message,
                                String paymentCode, String paymentPurpose);
    - Generise transactionId = ForeignBankId(ourRoutingNumber, UUID)
    - Validira balanced (sum debita == sum kredita po asset-u)
    - Vraca novi Transaction record

 2. void execute(Transaction tx);
    - Identifikuje sve banke ucesnice (parsira routingNumber-e iz Postings)
    - Ako samo MI ucestvujemo:
        sequential local prepare → local commit (dve odvojene local lokalne
        transakcije) — §2.8.4 last paragraph
    - Inace: postajemo koordinator
        a) prepareLocal(tx) u istoj lokalnoj transakciji kao log poruka:
           Message<NEW_TX> za svaku ostalu banku → message log
        b) ako prepareLocal fail → ROLLBACK lokalno, NEMA poruka
        c) inace (prepareLocal ok) → InterbankRetryScheduler kupi poruke
           iz message log-a i salje
        d) primaju se TransactionVote response-i
        e) u lokalnoj transakciji: zapisi glasove
        f) ako svi YES → produce Message<COMMIT_TX> za svakog,
                         u istoj lokalnoj transakciji commitLocal(tx)
        g) ako bilo koji NO → produce Message<ROLLBACK_TX> za svakog,
                              u istoj lokalnoj transakciji rollbackLocal(tx)

 3. TransactionVote prepareLocal(Transaction tx);
    - Verifikacija (§2.8.6):
        a) balanced
        b) svi racuni postoje
        c) debit racuni mogu da prime asset (UNACCEPTABLE_ASSET ako npr.
           STOCK na valutni racun)
        d) credit racuni imaju dovoljno asset-a (INSUFFICIENT_ASSET)
        e) opcije: OPTION pseudo-account mora biti tacno credit-ovan k
           akcijama i debit-ovan k*pi sredstava (OPTION_AMOUNT_INCORRECT)
        f) opcije: nije iskoriscena ni istekla (OPTION_USED_OR_EXPIRED)
        g) opcije: pregovor postoji (OPTION_NEGOTIATION_NOT_FOUND)
    - Ako sve prolazi: rezervisi sredstva za debite, glasaj YES
    - Inace: glasaj NO sa konkretnim NoVoteReason listom
    - SVA verifikacija + rezervacija u JEDNOJ lokalnoj transakciji

 4. void commitLocal(ForeignBankId transactionId);
    - Obrise rezervacije, primeni postinge (debit povecava saldo, credit
      smanjuje), zapise local Transaction trag
    - INVARIANTA: nakon commit-a, NE moze se rollback-ovati (§2.8.7)
    - U JEDNOJ lokalnoj transakciji

 5. void rollbackLocal(ForeignBankId transactionId);
    - Otpusti rezervirana sredstva
    - Markira tx kao FAILED u local logovima

KAO PRIMALAC (RB = Recipient Bank):

 6. TransactionVote handleNewTx(Transaction tx, IdempotenceKey key);
    - InterbankMessageService.findOrSaveResponse(key) — idempotency check
    - prepareLocal(tx) — vrati YES/NO
    - U JEDNOJ lokalnoj transakciji: i idempotency record i prepare

 7. void handleCommitTx(CommitTransaction body, IdempotenceKey key);
    - InterbankMessageService.findOrSaveResponse(key)
    - commitLocal(body.transactionId) — atomicno
    - Vrati 204 No Content

 8. void handleRollbackTx(RollbackTransaction body, IdempotenceKey key);
    - InterbankMessageService.findOrSaveResponse(key)
    - rollbackLocal(body.transactionId)
    - Vrati 204 No Content

POMOCNICI:
 - DOMENSKI MAPING: Posting/TxAccount/Asset → naseg Account/Listing/Portfolio
   stanja. Treba pomocni servis (npr. PostingApplier) koji na "primeni posting"
   aktivira pravu domensku akciju (transfer novca, prenos akcija, formiranje
   opcionog ugovora — §3.6.1).
 - OPCIJE: za OPTION asset variantu, primanje credit-a od OPTION pseudo-acct-a
   znaci "buyer dobija akcije po strike-u"; debit OPTION → "seller gubi akcije";
   primanje monetarnih sredstava: kupac plati strike, prodavac primi.
 - CURRENCY KONVERZIJA: ako primalac primi MONAS sa drugacijom valutom od
   ciljnog racuna, koristi CurrencyConversionService po srednjem kursu (bez
   provizije — interbank).

DEPENDENCY INJECTION (planirano):
   InterbankClient client                    — outbound HTTP
   InterbankMessageService messages          — idempotency + log
   BankRoutingService routing                — RoutingNumber resolve
   InterbankTransactionRepository txRepo     — perzistencija stanja transakcije
   AccountRepository accounts                — ACCOUNT lookup
   FundReservationService reservations       — debit reservacija (§2.8.4)
   PortfolioRepository portfolios            — STOCK/OPTION mapiranje
   OptionContractRepository optionContracts  — OPTION asset persistence
   CurrencyConversionService fx              — interbank konverzija
================================================================================
*/
@Service
@RequiredArgsConstructor
public class TransactionExecutorService {

    private final InterbankMessageService messageService;
    private final InterbankClient client;
    private final BankRoutingService routing;
    private final InterbankTransactionRepository txRepo;
    private final ObjectMapper objectMapper;

    /**
     * §2.8.5: self-proxy so that @Transactional on prepareLocal/commitLocal/rollbackLocal
     * is respected when called from execute() (Spring AOP does not intercept self-invocation
     * through `this`; going through the proxy here ensures each sub-method gets its own
     * local transaction as annotated).
     */
    @Lazy
    @Autowired
    private TransactionExecutorService self;

    @Transactional
    public Transaction formTransaction(/* TODO: argumenti */) {
        throw new UnsupportedOperationException("TODO: §2.8.3 Transaction formation");
    }

    /**
     * §2.8.5 Coordinator — orchestrates the two-phase commit across all involved banks.
     * Not @Transactional itself: each phase (prepare, commit/rollback) runs in its own
     * local transaction so DB locks are released before network I/O begins.
     *
     * TODO: prepareLocal + recordOutbound(NEW_TX) must commit in the SAME local transaction
     *   per §2.8.5 ("record these messages in the message log in the same local transaction
     *   as its local preparation"). Extract to a @Transactional prepareTxPhase(tx, remoteRns)
     *   called via self once prepareLocal is implemented.
     * TODO: similarly, commitLocal/rollbackLocal + recordOutbound(COMMIT/ROLLBACK_TX) must
     *   be in one local transaction per §2.8.5 / §2.8.8.
     */
    public void execute(Transaction tx) {
        Set<Integer> remoteRns = collectRemoteRoutingNumbers(tx);

        if (remoteRns.isEmpty()) {
            // §2.8.4 last paragraph: fully local — two sequential local transactions
            TransactionVote vote = self.prepareLocal(tx);
            if (vote.vote() == TransactionVote.Vote.YES) {
                self.commitLocal(tx.transactionId());
            } else {
                self.rollbackLocal(tx.transactionId());
            }
            return;
        }

        // §2.8.5: promote to coordinator
        TransactionVote myVote = self.prepareLocal(tx);
        if (myVote.vote() == TransactionVote.Vote.NO) {
            // prepareLocal already rolled back local reservations; no messages sent
            return;
        }

        // Log PENDING NEW_TX messages for each remote bank
        record Phase1Entry(IdempotenceKey key, Message<Transaction> envelope) {}
        Map<Integer, Phase1Entry> phase1 = new LinkedHashMap<>();

        for (int remoteRn : remoteRns) {
            IdempotenceKey key = messageService.generateKey();
            Message<Transaction> envelope = new Message<>(key, MessageType.NEW_TX, tx);
            try {
                messageService.recordOutbound(key, remoteRn,
                        MessageType.NEW_TX,
                        objectMapper.writeValueAsString(envelope), tx.transactionId().id());
            } catch (JsonProcessingException e) {
                throw new InterbankExceptions.InterbankProtocolException(
                        "Failed to serialize NEW_TX for routing " + remoteRn + ": " + e.getMessage());
            }
            phase1.put(remoteRn, new Phase1Entry(key, envelope));
        }

        saveCoordinatorState(tx, InterbankTransactionStatus.PREPARING);

        // Send NEW_TX to each remote bank and collect votes
        Map<Integer, TransactionVote> votes = new LinkedHashMap<>();
        for (var entry : phase1.entrySet()) {
            int remoteRn = entry.getKey();
            IdempotenceKey key = entry.getValue().key();
            Message<Transaction> envelope = entry.getValue().envelope();

            TransactionVote vote;
            try {
                vote = client.sendMessage(remoteRn, MessageType.NEW_TX, envelope, TransactionVote.class);
                if (vote == null) {
                    // 202 Accepted: remote still processing; scheduler will retry and deliver vote
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

        boolean allYes = votes.values().stream().allMatch(v -> v.vote() == TransactionVote.Vote.YES);

        if (allYes) {
            self.commitLocal(tx.transactionId());
            sendPhase2Messages(remoteRns, MessageType.COMMIT_TX, new CommitTransaction(tx.transactionId()), tx.transactionId().id());
        } else {
            self.rollbackLocal(tx.transactionId());
            sendPhase2Messages(remoteRns, MessageType.ROLLBACK_TX, new RollbackTransaction(tx.transactionId()), tx.transactionId().id());
        }
    }

    @Transactional
    public TransactionVote prepareLocal(Transaction tx) {
        throw new UnsupportedOperationException("TODO: §2.8.4 + §2.8.6 prepare + verification");
    }

    @Transactional
    public void commitLocal(ForeignBankId transactionId) {
        throw new UnsupportedOperationException("TODO: §2.8.4 commit local");
    }

    @Transactional
    public void rollbackLocal(ForeignBankId transactionId) {
        throw new UnsupportedOperationException("TODO: §2.8.7 rollback local");
    }

    @Transactional
    public TransactionVote handleNewTx(Transaction tx, IdempotenceKey key) {
        throw new UnsupportedOperationException("TODO: §2.12.1 NEW_TX handler (inbound)");
    }

    @Transactional
    public void handleCommitTx(CommitTransaction body, IdempotenceKey key) {
        throw new UnsupportedOperationException("TODO: §2.12.2 COMMIT_TX handler");
    }

    @Transactional
    public void handleRollbackTx(RollbackTransaction body, IdempotenceKey key) {
        throw new UnsupportedOperationException("TODO: §2.12.3 ROLLBACK_TX handler");
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Collects routing numbers from tx postings that belong to banks other than ours. */
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
            if (rn != myRn) {
                result.add(rn);
            }
        }
        return result;
    }

    /** Logs and immediately fires phase-2 messages (COMMIT_TX or ROLLBACK_TX) to all remote banks.
     *  Delivery failures stay PENDING for the retry scheduler. */
    private <T> void sendPhase2Messages(Set<Integer> remoteRns, MessageType type, T body, String transactionId) {
        MessageType localType = MessageType.valueOf(type.name());

        for (int remoteRn : remoteRns) {
            IdempotenceKey key = messageService.generateKey();
            Message<T> envelope = new Message<>(key, type, body);
            try {
                messageService.recordOutbound(key, remoteRn, localType,
                        objectMapper.writeValueAsString(envelope), transactionId);
            } catch (JsonProcessingException e) {
                throw new InterbankExceptions.InterbankProtocolException(
                        "Failed to serialize " + type + " for routing " + remoteRn + ": " + e.getMessage());
            }
            try {
                client.sendMessage(remoteRn, type, envelope, Void.class);
                messageService.markOutboundSent(key, 204, null);
            } catch (InterbankExceptions.InterbankCommunicationException e) {
                messageService.markOutboundFailed(key, e.getMessage());
                // Entry stays PENDING; scheduler retries from message log
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
}
