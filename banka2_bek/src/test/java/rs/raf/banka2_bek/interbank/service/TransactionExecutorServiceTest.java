package rs.raf.banka2_bek.interbank.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountStatus;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.currency.model.Currency;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TransactionExecutorService (§2.8 2PC coordinator + §2.12 inbound handlers).
 * The `self` proxy is replaced with a Mockito mock via ReflectionTestUtils so @Transactional
 * boundaries are exercised without a Spring context.
 */
@ExtendWith(MockitoExtension.class)
class TransactionExecutorServiceTest {

    @Mock private InterbankMessageService messageService;
    @Mock private InterbankClient client;
    @Mock private BankRoutingService routing;
    @Mock private InterbankTransactionRepository txRepo;
    @Mock private AccountRepository accountRepository;
    @Mock private PortfolioRepository portfolioRepository;
    @Mock private InterbankReservationApplier reservationApplier;
    @Mock private ListingRepository listingRepository;
    @Mock private CurrencyConversionService currencyConversionService;

    /** Self-proxy replaced with a mock so @Transactional sub-methods can be stubbed. */
    @Mock private TransactionExecutorService self;

    private TransactionExecutorService service;
    private ObjectMapper objectMapper;

    private static final int MY_RN     = 222;
    private static final int REMOTE_RN = 111;

    // Account numbers: prefix = routing number, padded to 9 chars for simplicity
    private static final String ACCT_A = MY_RN + "100001"; // local debit account
    private static final String ACCT_B = MY_RN + "100002"; // local credit account
    private static final String ACCT_REMOTE = REMOTE_RN + "900001";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        service = new TransactionExecutorService(
                messageService, client, routing, txRepo, objectMapper,
                accountRepository, portfolioRepository, reservationApplier,
                listingRepository, currencyConversionService);

        ReflectionTestUtils.setField(service, "self", self);

        lenient().when(routing.myRoutingNumber()).thenReturn(MY_RN);

        // Parse routing number from account number prefix (first 3 digits)
        lenient().when(routing.parseRoutingNumber(any())).thenAnswer(inv -> {
            String num = (String) inv.getArgument(0);
            return Integer.parseInt(num.substring(0, 3));
        });

        // isLocalAccount: true when account number starts with MY_RN
        lenient().when(routing.isLocalAccount(any())).thenAnswer(inv -> {
            String num = (String) inv.getArgument(0);
            return num.startsWith(String.valueOf(MY_RN));
        });
    }

    // =========================================================================
    // formTransaction
    // =========================================================================

    @Test
    @DisplayName("formTransaction: transactionId routingNumber equals our routing number")
    void formTransaction_routingNumberIsOurs() {
        Transaction tx = service.formTransaction(List.of(), "msg", "ref", "289", "transfer");
        assertThat(tx.transactionId().routingNumber()).isEqualTo(MY_RN);
    }

    @Test
    @DisplayName("formTransaction: each call produces a unique 64-char hex id")
    void formTransaction_uniqueIds() {
        Transaction tx1 = service.formTransaction(List.of(), null, null, null, null);
        Transaction tx2 = service.formTransaction(List.of(), null, null, null, null);
        assertThat(tx1.transactionId().id()).hasSize(64);
        assertThat(tx1.transactionId().id()).isNotEqualTo(tx2.transactionId().id());
    }

    @Test
    @DisplayName("formTransaction: postings and metadata are preserved")
    void formTransaction_preservesPostingsAndMetadata() {
        Posting p = new Posting(
                new TxAccount.Account(ACCT_A),
                BigDecimal.valueOf(100),
                new Asset.Monas(new MonetaryAsset(CurrencyCode.RSD)));
        Transaction tx = service.formTransaction(
                List.of(p), "my message", "ref-001", "289", "salary");
        assertThat(tx.postings()).containsExactly(p);
        assertThat(tx.message()).isEqualTo("my message");
    }

    // =========================================================================
    // execute — local-only path
    // =========================================================================

    @Test
    @DisplayName("execute local-only: YES vote → commitLocal called, no messageService/client interaction")
    void execute_localOnly_yesVote_commitsLocal() {
        Transaction tx = localMonasTx();
        stubTxSave();
        when(self.prepareLocal(tx)).thenReturn(yesVote());

        service.execute(tx);

        verify(self).prepareLocal(tx);
        verify(self).commitLocal(tx.transactionId());
        verify(self, never()).rollbackLocal(any());
        verifyNoInteractions(messageService, client);
    }

    @Test
    @DisplayName("execute local-only: NO vote → rollbackLocal called, no messageService/client interaction")
    void execute_localOnly_noVote_rollsBackLocal() {
        Transaction tx = localMonasTx();
        stubTxSave();
        when(self.prepareLocal(tx)).thenReturn(noVote());

        service.execute(tx);

        verify(self).prepareLocal(tx);
        verify(self).rollbackLocal(tx.transactionId());
        verify(self, never()).commitLocal(any());
        verifyNoInteractions(messageService, client);
    }

    @Test
    @DisplayName("execute local-only: saves INITIATOR/PREPARING coordinator record before prepareLocal")
    void execute_localOnly_savesCoordinatorStateFirst() {
        Transaction tx = localMonasTx();
        stubTxSave();
        when(self.prepareLocal(tx)).thenReturn(yesVote());

        service.execute(tx);

        ArgumentCaptor<InterbankTransaction> cap = ArgumentCaptor.forClass(InterbankTransaction.class);
        verify(txRepo).save(cap.capture());
        InterbankTransaction saved = cap.getValue();
        assertThat(saved.getRole()).isEqualTo(InterbankTransaction.InterbankTransactionRole.INITIATOR);
        assertThat(saved.getStatus()).isEqualTo(InterbankTransactionStatus.PREPARING);
        assertThat(saved.getTransactionRoutingNumber()).isEqualTo(MY_RN);
        assertThat(saved.getTransactionIdString()).isEqualTo(tx.transactionId().id());
        assertThat(saved.getTransactionBody()).isNotBlank();
    }

    // =========================================================================
    // execute — coordinator path
    // =========================================================================

    @Test
    @DisplayName("execute coordinator: all YES → commitTxPhase called + COMMIT_TX sent to remote")
    void execute_coordinator_allYes_commitsAndSendsCommit() {
        Transaction tx = mixedMonasTx();
        IdempotenceKey p1Key = new IdempotenceKey(MY_RN, "p1-key");
        IdempotenceKey commitKey = new IdempotenceKey(MY_RN, "commit-key");

        when(self.prepareTxPhase(eq(tx), eq(Set.of(REMOTE_RN)))).thenReturn(
                phase1Yes(p1Key, tx));
        when(client.sendMessage(eq(REMOTE_RN), eq(MessageType.NEW_TX), any(), eq(TransactionVote.class)))
                .thenReturn(yesVote());
        when(self.commitTxPhase(tx.transactionId(), Set.of(REMOTE_RN)))
                .thenReturn(Map.of(REMOTE_RN, commitKey));
        when(client.sendMessage(eq(REMOTE_RN), eq(MessageType.COMMIT_TX), any(), eq(Void.class)))
                .thenReturn(null);

        service.execute(tx);

        verify(self).commitTxPhase(tx.transactionId(), Set.of(REMOTE_RN));
        verify(self, never()).rollbackTxPhase(any(), any());
        verify(client).sendMessage(eq(REMOTE_RN), eq(MessageType.COMMIT_TX), any(), eq(Void.class));
        verify(messageService).markOutboundSent(eq(commitKey), eq(204), isNull());
    }

    @Test
    @DisplayName("execute coordinator: remote votes NO → rollbackTxPhase called + ROLLBACK_TX sent")
    void execute_coordinator_remoteNo_rollsBackAndSendsRollback() {
        Transaction tx = mixedMonasTx();
        IdempotenceKey p1Key = new IdempotenceKey(MY_RN, "p1-key");
        IdempotenceKey rbKey = new IdempotenceKey(MY_RN, "rb-key");

        when(self.prepareTxPhase(eq(tx), eq(Set.of(REMOTE_RN)))).thenReturn(phase1Yes(p1Key, tx));
        when(client.sendMessage(eq(REMOTE_RN), eq(MessageType.NEW_TX), any(), eq(TransactionVote.class)))
                .thenReturn(noVote());
        when(self.rollbackTxPhase(tx.transactionId(), Set.of(REMOTE_RN)))
                .thenReturn(Map.of(REMOTE_RN, rbKey));
        when(client.sendMessage(eq(REMOTE_RN), eq(MessageType.ROLLBACK_TX), any(), eq(Void.class)))
                .thenReturn(null);

        service.execute(tx);

        verify(self).rollbackTxPhase(tx.transactionId(), Set.of(REMOTE_RN));
        verify(self, never()).commitTxPhase(any(), any());
        verify(client).sendMessage(eq(REMOTE_RN), eq(MessageType.ROLLBACK_TX), any(), eq(Void.class));
    }

    @Test
    @DisplayName("execute coordinator: prepareTxPhase returns NO → no network calls at all")
    void execute_coordinator_phase1No_noNetworkCalls() {
        Transaction tx = mixedMonasTx();
        when(self.prepareTxPhase(eq(tx), eq(Set.of(REMOTE_RN)))).thenReturn(
                new TransactionExecutorService.Phase1Result(noVote(), Map.of(), Map.of()));

        service.execute(tx);

        verify(self, never()).commitTxPhase(any(), any());
        verify(self, never()).rollbackTxPhase(any(), any());
        verifyNoInteractions(client, messageService);
    }

    @Test
    @DisplayName("execute coordinator: remote returns null (202 Accepted) → treated as NO → rollback")
    void execute_coordinator_remote202_treatedAsNo() {
        Transaction tx = mixedMonasTx();
        IdempotenceKey p1Key = new IdempotenceKey(MY_RN, "key-202");

        when(self.prepareTxPhase(eq(tx), eq(Set.of(REMOTE_RN)))).thenReturn(phase1Yes(p1Key, tx));
        when(client.sendMessage(eq(REMOTE_RN), eq(MessageType.NEW_TX), any(), eq(TransactionVote.class)))
                .thenReturn(null);
        when(self.rollbackTxPhase(any(), any())).thenReturn(Map.of());

        service.execute(tx);

        verify(self).rollbackTxPhase(tx.transactionId(), Set.of(REMOTE_RN));
        verify(messageService).markOutboundSent(eq(p1Key), eq(202), isNull());
    }

    @Test
    @DisplayName("execute coordinator: communication exception → treated as NO → rollback")
    void execute_coordinator_communicationException_rollsBack() {
        Transaction tx = mixedMonasTx();
        IdempotenceKey p1Key = new IdempotenceKey(MY_RN, "key-err");

        when(self.prepareTxPhase(eq(tx), eq(Set.of(REMOTE_RN)))).thenReturn(phase1Yes(p1Key, tx));
        when(client.sendMessage(eq(REMOTE_RN), eq(MessageType.NEW_TX), any(), eq(TransactionVote.class)))
                .thenThrow(new InterbankExceptions.InterbankCommunicationException("timeout"));
        when(self.rollbackTxPhase(any(), any())).thenReturn(Map.of());

        service.execute(tx);

        verify(self).rollbackTxPhase(tx.transactionId(), Set.of(REMOTE_RN));
        verify(messageService).markOutboundFailed(eq(p1Key), contains("timeout"));
    }

    // =========================================================================
    // prepareTxPhase
    // =========================================================================

    @Test
    @DisplayName("prepareTxPhase: saves coordinator record, reserves, and logs outbound atomically")
    void prepareTxPhase_allYes_savesAndLogsAndReserves() throws Exception {
        Transaction tx = localMonasTx();
        stubTxSave();
        stubMonasAccounts("RSD");
        IdempotenceKey key = new IdempotenceKey(MY_RN, "prep-key");
        when(messageService.generateKey()).thenReturn(key);
        when(messageService.recordOutbound(any(), anyInt(), any(), any(), any())).thenReturn(null);

        TransactionExecutorService.Phase1Result result =
                service.prepareTxPhase(tx, Set.of(REMOTE_RN));

        assertThat(result.vote().vote()).isEqualTo(TransactionVote.Vote.YES);
        assertThat(result.keys()).containsKey(REMOTE_RN);
        assertThat(result.envelopes()).containsKey(REMOTE_RN);
        verify(txRepo).save(any(InterbankTransaction.class));
        verify(reservationApplier).reserveMonas(eq(ACCT_B), eq(BigDecimal.valueOf(100)));
        verify(messageService).recordOutbound(eq(key), eq(REMOTE_RN), eq(MessageType.NEW_TX), any(), any());
    }

    @Test
    @DisplayName("prepareTxPhase: violations found → NO vote, no recordOutbound calls")
    void prepareTxPhase_violations_noVoteAndNoOutbound() throws Exception {
        Transaction tx = unbalancedTx();
        stubTxSave();

        TransactionExecutorService.Phase1Result result =
                service.prepareTxPhase(tx, Set.of(REMOTE_RN));

        assertThat(result.vote().vote()).isEqualTo(TransactionVote.Vote.NO);
        assertThat(result.keys()).isEmpty();
        assertThat(result.envelopes()).isEmpty();
        verify(messageService, never()).recordOutbound(any(), anyInt(), any(), any(), any());
    }

    // =========================================================================
    // prepareLocal
    // =========================================================================

    @Test
    @DisplayName("prepareLocal: balanced MONAS tx → YES + reserveMonas called for credit posting")
    void prepareLocal_balanced_yesVote_reservesMonas() {
        stubMonasAccounts("RSD");

        TransactionVote vote = service.prepareLocal(localMonasTx());

        assertThat(vote.vote()).isEqualTo(TransactionVote.Vote.YES);
        verify(reservationApplier).reserveMonas(eq(ACCT_B), eq(BigDecimal.valueOf(100)));
        verify(reservationApplier, never()).reserveMonas(eq(ACCT_A), any());
    }

    @Test
    @DisplayName("prepareLocal: unbalanced postings → NO + UNBALANCED_TX, no repo calls")
    void prepareLocal_unbalanced_noVote() {
        TransactionVote vote = service.prepareLocal(unbalancedTx());

        assertThat(vote.vote()).isEqualTo(TransactionVote.Vote.NO);
        assertThat(vote.reasons()).extracting(NoVoteReason::reason)
                .containsExactly(NoVoteReason.Reason.UNBALANCED_TX);
        verifyNoInteractions(accountRepository, reservationApplier);
    }

    @Test
    @DisplayName("prepareLocal: account not found → NO + NO_SUCH_ACCOUNT")
    void prepareLocal_accountNotFound_noVote() {
        when(accountRepository.findByAccountNumber(any())).thenReturn(Optional.empty());

        TransactionVote vote = service.prepareLocal(localMonasTx());

        assertThat(vote.vote()).isEqualTo(TransactionVote.Vote.NO);
        assertThat(vote.reasons()).anySatisfy(r ->
                assertThat(r.reason()).isEqualTo(NoVoteReason.Reason.NO_SUCH_ACCOUNT));
    }

    @Test
    @DisplayName("prepareLocal: account INACTIVE → NO + NO_SUCH_ACCOUNT")
    void prepareLocal_accountInactive_noVote() {
        Account inactive = buildAccount(ACCT_A, "RSD", BigDecimal.ZERO, AccountStatus.INACTIVE);
        when(accountRepository.findByAccountNumber(ACCT_A)).thenReturn(Optional.of(inactive));
        when(accountRepository.findByAccountNumber(ACCT_B))
                .thenReturn(Optional.of(buildAccount(ACCT_B, "RSD", BigDecimal.valueOf(500), AccountStatus.ACTIVE)));

        TransactionVote vote = service.prepareLocal(localMonasTx());

        assertThat(vote.vote()).isEqualTo(TransactionVote.Vote.NO);
        assertThat(vote.reasons()).anySatisfy(r ->
                assertThat(r.reason()).isEqualTo(NoVoteReason.Reason.NO_SUCH_ACCOUNT));
    }

    @Test
    @DisplayName("prepareLocal: MONAS credit currency mismatch → NO + UNACCEPTABLE_ASSET")
    void prepareLocal_currencyMismatch_noVote() {
        // Posting is RSD but account ACCT_B is EUR
        when(accountRepository.findByAccountNumber(ACCT_A))
                .thenReturn(Optional.of(buildAccount(ACCT_A, "RSD", BigDecimal.ZERO, AccountStatus.ACTIVE)));
        when(accountRepository.findByAccountNumber(ACCT_B))
                .thenReturn(Optional.of(buildAccount(ACCT_B, "EUR", BigDecimal.valueOf(500), AccountStatus.ACTIVE)));

        TransactionVote vote = service.prepareLocal(localMonasTx()); // posting currency = RSD

        assertThat(vote.vote()).isEqualTo(TransactionVote.Vote.NO);
        assertThat(vote.reasons()).anySatisfy(r ->
                assertThat(r.reason()).isEqualTo(NoVoteReason.Reason.UNACCEPTABLE_ASSET));
    }

    @Test
    @DisplayName("prepareLocal: MONAS credit insufficient balance → NO + INSUFFICIENT_ASSET")
    void prepareLocal_insufficientBalance_noVote() {
        // ACCT_B has only 50, but credit posting requires 100
        when(accountRepository.findByAccountNumber(ACCT_A))
                .thenReturn(Optional.of(buildAccount(ACCT_A, "RSD", BigDecimal.ZERO, AccountStatus.ACTIVE)));
        when(accountRepository.findByAccountNumber(ACCT_B))
                .thenReturn(Optional.of(buildAccount(ACCT_B, "RSD", BigDecimal.valueOf(50), AccountStatus.ACTIVE)));

        TransactionVote vote = service.prepareLocal(localMonasTx());

        assertThat(vote.vote()).isEqualTo(TransactionVote.Vote.NO);
        assertThat(vote.reasons()).anySatisfy(r ->
                assertThat(r.reason()).isEqualTo(NoVoteReason.Reason.INSUFFICIENT_ASSET));
    }

    @Test
    @DisplayName("prepareLocal: MONAS on Person account → NO + UNACCEPTABLE_ASSET")
    void prepareLocal_monasOnPerson_noVote() {
        // Debit goes to a Person TxAccount — invalid for MONAS
        Transaction tx = new Transaction(List.of(
                new Posting(new TxAccount.Person(new ForeignBankId(MY_RN, "99")),
                        BigDecimal.valueOf(100), new Asset.Monas(new MonetaryAsset(CurrencyCode.RSD))),
                new Posting(new TxAccount.Account(ACCT_B),
                        BigDecimal.valueOf(-100), new Asset.Monas(new MonetaryAsset(CurrencyCode.RSD)))
        ), new ForeignBankId(MY_RN, "tx-bad"), null, null, null, null);
        when(accountRepository.findByAccountNumber(ACCT_B))
                .thenReturn(Optional.of(buildAccount(ACCT_B, "RSD", BigDecimal.valueOf(500), AccountStatus.ACTIVE)));

        TransactionVote vote = service.prepareLocal(tx);

        assertThat(vote.vote()).isEqualTo(TransactionVote.Vote.NO);
        assertThat(vote.reasons()).anySatisfy(r ->
                assertThat(r.reason()).isEqualTo(NoVoteReason.Reason.UNACCEPTABLE_ASSET));
    }

    @Test
    @DisplayName("prepareLocal: STOCK on Account → NO + UNACCEPTABLE_ASSET")
    void prepareLocal_stockOnAccount_noVote() {
        Transaction tx = new Transaction(List.of(
                new Posting(new TxAccount.Account(ACCT_A),
                        BigDecimal.valueOf(10), new Asset.Stock(new StockDescription("AAPL"))),
                new Posting(new TxAccount.Account(ACCT_B),
                        BigDecimal.valueOf(-10), new Asset.Stock(new StockDescription("AAPL")))
        ), new ForeignBankId(MY_RN, "tx-stock-acct"), null, null, null, null);
        TransactionVote vote = service.prepareLocal(tx);

        assertThat(vote.vote()).isEqualTo(TransactionVote.Vote.NO);
        assertThat(vote.reasons()).anySatisfy(r ->
                assertThat(r.reason()).isEqualTo(NoVoteReason.Reason.UNACCEPTABLE_ASSET));
    }

    @Test
    @DisplayName("prepareLocal: OPTION posting → NO + OPTION_NEGOTIATION_NOT_FOUND")
    void prepareLocal_optionPosting_noVote() {
        OptionDescription opt = new OptionDescription(
                new ForeignBankId(MY_RN, "neg-1"), new StockDescription("AAPL"),
                new MonetaryValue(CurrencyCode.USD, BigDecimal.valueOf(150)), null, BigDecimal.valueOf(5));
        Transaction tx = new Transaction(List.of(
                new Posting(new TxAccount.Option(new ForeignBankId(MY_RN, "neg-1")),
                        BigDecimal.valueOf(5), new Asset.OptionAsset(opt)),
                new Posting(new TxAccount.Option(new ForeignBankId(MY_RN, "neg-1")),
                        BigDecimal.valueOf(-5), new Asset.OptionAsset(opt))
        ), new ForeignBankId(MY_RN, "tx-opt"), null, null, null, null);

        TransactionVote vote = service.prepareLocal(tx);

        assertThat(vote.vote()).isEqualTo(TransactionVote.Vote.NO);
        assertThat(vote.reasons()).allSatisfy(r ->
                assertThat(r.reason()).isEqualTo(NoVoteReason.Reason.OPTION_NEGOTIATION_NOT_FOUND));
    }

    @Test
    @DisplayName("prepareLocal: ticker not found → NO + NO_SUCH_ASSET")
    void prepareLocal_tickerNotFound_noVote() {
        when(listingRepository.findByTicker(any())).thenReturn(Optional.empty());

        TransactionVote vote = service.prepareLocal(localStockTx());

        assertThat(vote.vote()).isEqualTo(TransactionVote.Vote.NO);
        assertThat(vote.reasons()).anySatisfy(r ->
                assertThat(r.reason()).isEqualTo(NoVoteReason.Reason.NO_SUCH_ASSET));
    }

    @Test
    @DisplayName("prepareLocal: STOCK credit — portfolio not found → NO + NO_SUCH_ASSET")
    void prepareLocal_portfolioMissing_noVote() {
        when(listingRepository.findByTicker("AAPL")).thenReturn(Optional.of(buildListing(1L, "AAPL")));
        when(portfolioRepository.findByUserIdAndUserRoleAndListingIdForUpdate(anyLong(), any(), anyLong()))
                .thenReturn(Optional.empty());

        TransactionVote vote = service.prepareLocal(localStockTx());

        assertThat(vote.vote()).isEqualTo(TransactionVote.Vote.NO);
        assertThat(vote.reasons()).anySatisfy(r ->
                assertThat(r.reason()).isEqualTo(NoVoteReason.Reason.NO_SUCH_ASSET));
    }

    @Test
    @DisplayName("prepareLocal: STOCK credit — insufficient quantity → NO + INSUFFICIENT_ASSET")
    void prepareLocal_insufficientStock_noVote() {
        when(listingRepository.findByTicker("AAPL")).thenReturn(Optional.of(buildListing(1L, "AAPL")));
        when(portfolioRepository.findByUserIdAndUserRoleAndListingIdForUpdate(anyLong(), any(), anyLong()))
                .thenReturn(Optional.of(buildPortfolio(3))); // needs 10, has 3

        TransactionVote vote = service.prepareLocal(localStockTx());

        assertThat(vote.vote()).isEqualTo(TransactionVote.Vote.NO);
        assertThat(vote.reasons()).anySatisfy(r ->
                assertThat(r.reason()).isEqualTo(NoVoteReason.Reason.INSUFFICIENT_ASSET));
    }

    @Test
    @DisplayName("prepareLocal: STOCK YES → reserveStock called with correct userId and listingId")
    void prepareLocal_stockYes_reservesStock() {
        when(listingRepository.findByTicker("AAPL")).thenReturn(Optional.of(buildListing(7L, "AAPL")));
        when(portfolioRepository.findByUserIdAndUserRoleAndListingIdForUpdate(anyLong(), any(), anyLong()))
                .thenReturn(Optional.of(buildPortfolio(20))); // has 20, needs 10

        TransactionVote vote = service.prepareLocal(localStockTx());

        assertThat(vote.vote()).isEqualTo(TransactionVote.Vote.YES);
        // credit posting: person 42 gives 10 AAPL (listingId=7)
        verify(reservationApplier).reserveStock(eq(42L), eq("CLIENT"), eq(7L), eq(10));
    }

    @Test
    @DisplayName("prepareLocal: remote postings skipped — accountRepository never called for remote account")
    void prepareLocal_remotePostingsSkipped() {
        when(accountRepository.findByAccountNumber(MY_RN + "999001"))
                .thenReturn(Optional.of(buildAccount(MY_RN + "999001", "EUR", BigDecimal.ZERO, AccountStatus.ACTIVE)));

        service.prepareLocal(mixedMonasTx());

        verify(accountRepository, never()).findByAccountNumber(ACCT_REMOTE);
    }

    @Test
    @DisplayName("prepareLocal: multiple violations are all returned together")
    void prepareLocal_multipleViolations_allReturned() {
        // Both accounts not found → two NO_SUCH_ACCOUNT violations
        when(accountRepository.findByAccountNumber(any())).thenReturn(Optional.empty());

        TransactionVote vote = service.prepareLocal(localMonasTx());

        assertThat(vote.vote()).isEqualTo(TransactionVote.Vote.NO);
        assertThat(vote.reasons()).hasSizeGreaterThanOrEqualTo(1);
    }

    // =========================================================================
    // commitLocal
    // =========================================================================

    @Test
    @DisplayName("commitLocal: MONAS debit → commitMonas(isDebit=true); MONAS credit → commitMonas(isDebit=false)")
    void commitLocal_monasPostings_callsCommitMonasCorrectly() throws Exception {
        Transaction tx = localMonasTx();
        InterbankTransaction ibt = savedIbt(tx, InterbankTransactionStatus.PREPARING);
        when(txRepo.findByTransactionRoutingNumberAndTransactionIdString(anyInt(), any()))
                .thenReturn(Optional.of(ibt));
        when(accountRepository.findForUpdateByAccountNumber(ACCT_A))
                .thenReturn(Optional.of(buildAccount(ACCT_A, "RSD", BigDecimal.ZERO, AccountStatus.ACTIVE)));
        when(accountRepository.findForUpdateByAccountNumber(ACCT_B))
                .thenReturn(Optional.of(buildAccount(ACCT_B, "RSD", BigDecimal.valueOf(500), AccountStatus.ACTIVE)));
        when(currencyConversionService.convert(any(), eq("RSD"), eq("RSD")))
                .thenReturn(BigDecimal.valueOf(100));
        stubTxSave();

        service.commitLocal(tx.transactionId());

        verify(reservationApplier).commitMonas(eq(ACCT_A), eq(BigDecimal.valueOf(100)), eq(true));
        verify(reservationApplier).commitMonas(eq(ACCT_B), eq(BigDecimal.valueOf(100)), eq(false));
    }

    @Test
    @DisplayName("commitLocal: currency conversion applied when posting currency differs from account currency")
    void commitLocal_currencyConversionApplied() throws Exception {
        Transaction tx = localMonasTxEur();
        InterbankTransaction ibt = savedIbt(tx, InterbankTransactionStatus.PREPARING);
        when(txRepo.findByTransactionRoutingNumberAndTransactionIdString(anyInt(), any()))
                .thenReturn(Optional.of(ibt));
        when(accountRepository.findForUpdateByAccountNumber(ACCT_A))
                .thenReturn(Optional.of(buildAccount(ACCT_A, "RSD", BigDecimal.ZERO, AccountStatus.ACTIVE)));
        when(accountRepository.findForUpdateByAccountNumber(ACCT_B))
                .thenReturn(Optional.of(buildAccount(ACCT_B, "RSD", BigDecimal.valueOf(50000), AccountStatus.ACTIVE)));
        when(currencyConversionService.convert(eq(BigDecimal.valueOf(100)), eq("EUR"), eq("RSD")))
                .thenReturn(BigDecimal.valueOf(11700));
        stubTxSave();

        service.commitLocal(tx.transactionId());

        verify(currencyConversionService, times(2)).convert(eq(BigDecimal.valueOf(100)), eq("EUR"), eq("RSD"));
        verify(reservationApplier).commitMonas(eq(ACCT_B), eq(BigDecimal.valueOf(11700)), eq(false));
    }

    @Test
    @DisplayName("commitLocal: status set to COMMITTED with timestamp")
    void commitLocal_statusSetToCommitted() throws Exception {
        Transaction tx = localMonasTx();
        InterbankTransaction ibt = savedIbt(tx, InterbankTransactionStatus.PREPARING);
        when(txRepo.findByTransactionRoutingNumberAndTransactionIdString(anyInt(), any()))
                .thenReturn(Optional.of(ibt));
        when(accountRepository.findForUpdateByAccountNumber(any()))
                .thenReturn(Optional.of(buildAccount(ACCT_A, "RSD", BigDecimal.ZERO, AccountStatus.ACTIVE)));
        when(currencyConversionService.convert(any(), any(), any())).thenReturn(BigDecimal.valueOf(100));
        stubTxSave();

        service.commitLocal(tx.transactionId());

        assertThat(ibt.getStatus()).isEqualTo(InterbankTransactionStatus.COMMITTED);
        assertThat(ibt.getCommittedAt()).isNotNull();
        assertThat(ibt.getLastActivityAt()).isNotNull();
        verify(txRepo).save(ibt);
    }

    @Test
    @DisplayName("commitLocal: second call is a no-op when already COMMITTED (idempotent)")
    void commitLocal_idempotent_alreadyCommitted() throws Exception {
        Transaction tx = localMonasTx();
        InterbankTransaction ibt = savedIbt(tx, InterbankTransactionStatus.COMMITTED);
        when(txRepo.findByTransactionRoutingNumberAndTransactionIdString(anyInt(), any()))
                .thenReturn(Optional.of(ibt));

        service.commitLocal(tx.transactionId());

        verifyNoInteractions(accountRepository, reservationApplier);
        verify(txRepo, never()).save(any());
    }

    @Test
    @DisplayName("commitLocal: throws InterbankProtocolException when transaction is ROLLED_BACK")
    void commitLocal_throwsOnRolledBack() throws Exception {
        Transaction tx = localMonasTx();
        InterbankTransaction ibt = savedIbt(tx, InterbankTransactionStatus.ROLLED_BACK);
        when(txRepo.findByTransactionRoutingNumberAndTransactionIdString(anyInt(), any()))
                .thenReturn(Optional.of(ibt));

        assertThatThrownBy(() -> service.commitLocal(tx.transactionId()))
                .isInstanceOf(InterbankExceptions.InterbankProtocolException.class);
    }

    @Test
    @DisplayName("commitLocal: STOCK debit → commitStock(isDebit=true); credit → commitStock(isDebit=false)")
    void commitLocal_stockPostings_callsCommitStockCorrectly() throws Exception {
        Transaction tx = localStockTx();
        InterbankTransaction ibt = savedIbt(tx, InterbankTransactionStatus.PREPARING);
        when(txRepo.findByTransactionRoutingNumberAndTransactionIdString(anyInt(), any()))
                .thenReturn(Optional.of(ibt));
        when(listingRepository.findByTicker("AAPL")).thenReturn(Optional.of(buildListing(7L, "AAPL")));
        stubTxSave();

        service.commitLocal(tx.transactionId());

        verify(reservationApplier).commitStock(eq(99L), eq("CLIENT"), eq(7L), eq(10), eq(true));
        verify(reservationApplier).commitStock(eq(42L), eq("CLIENT"), eq(7L), eq(10), eq(false));
    }

    @Test
    @DisplayName("commitLocal: STOCK listing not found → throws InterbankProtocolException")
    void commitLocal_stockListingNotFound_throws() throws Exception {
        Transaction tx = localStockTx();
        InterbankTransaction ibt = savedIbt(tx, InterbankTransactionStatus.PREPARING);
        when(txRepo.findByTransactionRoutingNumberAndTransactionIdString(anyInt(), any()))
                .thenReturn(Optional.of(ibt));
        when(listingRepository.findByTicker(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.commitLocal(tx.transactionId()))
                .isInstanceOf(InterbankExceptions.InterbankProtocolException.class);
    }

    // =========================================================================
    // rollbackLocal
    // =========================================================================

    @Test
    @DisplayName("rollbackLocal: only local CREDIT postings released; debit postings skipped")
    void rollbackLocal_onlyCreditPostingsReleased() throws Exception {
        Transaction tx = localMonasTx();
        InterbankTransaction ibt = savedIbt(tx, InterbankTransactionStatus.PREPARING);
        when(txRepo.findByTransactionRoutingNumberAndTransactionIdString(anyInt(), any()))
                .thenReturn(Optional.of(ibt));
        when(accountRepository.findForUpdateByAccountNumber(ACCT_B))
                .thenReturn(Optional.of(buildAccount(ACCT_B, "RSD", BigDecimal.valueOf(500), AccountStatus.ACTIVE)));
        when(currencyConversionService.convert(any(), any(), any())).thenReturn(BigDecimal.valueOf(100));
        stubTxSave();

        service.rollbackLocal(tx.transactionId());

        verify(reservationApplier).releaseMonas(eq(ACCT_B), eq(BigDecimal.valueOf(100)));
        verify(reservationApplier, never()).releaseMonas(eq(ACCT_A), any());
        verify(accountRepository, never()).findForUpdateByAccountNumber(ACCT_A);
    }

    @Test
    @DisplayName("rollbackLocal: releaseMonas called with currency-converted amount")
    void rollbackLocal_releaseMonasWithConvertedAmount() throws Exception {
        Transaction tx = localMonasTxEur();
        InterbankTransaction ibt = savedIbt(tx, InterbankTransactionStatus.PREPARING);
        when(txRepo.findByTransactionRoutingNumberAndTransactionIdString(anyInt(), any()))
                .thenReturn(Optional.of(ibt));
        when(accountRepository.findForUpdateByAccountNumber(ACCT_B))
                .thenReturn(Optional.of(buildAccount(ACCT_B, "RSD", BigDecimal.valueOf(50000), AccountStatus.ACTIVE)));
        when(currencyConversionService.convert(eq(BigDecimal.valueOf(100)), eq("EUR"), eq("RSD")))
                .thenReturn(BigDecimal.valueOf(11700));
        stubTxSave();

        service.rollbackLocal(tx.transactionId());

        verify(reservationApplier).releaseMonas(eq(ACCT_B), eq(BigDecimal.valueOf(11700)));
    }

    @Test
    @DisplayName("rollbackLocal: status set to ROLLED_BACK with timestamp")
    void rollbackLocal_statusSetToRolledBack() throws Exception {
        Transaction tx = localMonasTx();
        InterbankTransaction ibt = savedIbt(tx, InterbankTransactionStatus.PREPARING);
        when(txRepo.findByTransactionRoutingNumberAndTransactionIdString(anyInt(), any()))
                .thenReturn(Optional.of(ibt));
        when(accountRepository.findForUpdateByAccountNumber(ACCT_B))
                .thenReturn(Optional.of(buildAccount(ACCT_B, "RSD", BigDecimal.valueOf(500), AccountStatus.ACTIVE)));
        when(currencyConversionService.convert(any(), any(), any())).thenReturn(BigDecimal.valueOf(100));
        stubTxSave();

        service.rollbackLocal(tx.transactionId());

        assertThat(ibt.getStatus()).isEqualTo(InterbankTransactionStatus.ROLLED_BACK);
        assertThat(ibt.getRolledBackAt()).isNotNull();
        verify(txRepo).save(ibt);
    }

    @Test
    @DisplayName("rollbackLocal: second call is a no-op when already ROLLED_BACK (idempotent)")
    void rollbackLocal_idempotent_alreadyRolledBack() throws Exception {
        Transaction tx = localMonasTx();
        InterbankTransaction ibt = savedIbt(tx, InterbankTransactionStatus.ROLLED_BACK);
        when(txRepo.findByTransactionRoutingNumberAndTransactionIdString(anyInt(), any()))
                .thenReturn(Optional.of(ibt));

        service.rollbackLocal(tx.transactionId());

        verifyNoInteractions(accountRepository, reservationApplier);
        verify(txRepo, never()).save(any());
    }

    @Test
    @DisplayName("rollbackLocal: also a no-op when already COMMITTED (idempotent)")
    void rollbackLocal_idempotent_alreadyCommitted() throws Exception {
        Transaction tx = localMonasTx();
        InterbankTransaction ibt = savedIbt(tx, InterbankTransactionStatus.COMMITTED);
        when(txRepo.findByTransactionRoutingNumberAndTransactionIdString(anyInt(), any()))
                .thenReturn(Optional.of(ibt));

        service.rollbackLocal(tx.transactionId());

        verifyNoInteractions(accountRepository, reservationApplier);
        verify(txRepo, never()).save(any());
    }

    @Test
    @DisplayName("rollbackLocal: STOCK credit posting → releaseStock called; debit posting skipped")
    void rollbackLocal_stockCreditPosting_callsReleaseStock() throws Exception {
        Transaction tx = localStockTx();
        InterbankTransaction ibt = savedIbt(tx, InterbankTransactionStatus.PREPARING);
        when(txRepo.findByTransactionRoutingNumberAndTransactionIdString(anyInt(), any()))
                .thenReturn(Optional.of(ibt));
        when(listingRepository.findByTicker("AAPL")).thenReturn(Optional.of(buildListing(7L, "AAPL")));
        stubTxSave();

        service.rollbackLocal(tx.transactionId());

        verify(reservationApplier).releaseStock(eq(42L), eq("CLIENT"), eq(7L), eq(10));
        verify(reservationApplier, never()).releaseStock(eq(99L), any(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("rollbackLocal: STOCK debit-only posting — releaseStock never called")
    void rollbackLocal_stockDebitPosting_skipped() throws Exception {
        Transaction tx = new Transaction(List.of(
                new Posting(new TxAccount.Person(new ForeignBankId(MY_RN, "77")),
                        BigDecimal.valueOf(5), new Asset.Stock(new StockDescription("AAPL"))),
                new Posting(new TxAccount.Person(new ForeignBankId(MY_RN, "88")),
                        BigDecimal.valueOf(-5), new Asset.Stock(new StockDescription("AAPL")))
        ), new ForeignBankId(MY_RN, "stock-debit-only"), null, null, null, null);
        InterbankTransaction ibt = savedIbt(tx, InterbankTransactionStatus.PREPARING);
        when(txRepo.findByTransactionRoutingNumberAndTransactionIdString(anyInt(), any()))
                .thenReturn(Optional.of(ibt));
        when(listingRepository.findByTicker("AAPL")).thenReturn(Optional.of(buildListing(7L, "AAPL")));
        stubTxSave();

        service.rollbackLocal(tx.transactionId());

        verify(reservationApplier, never()).releaseStock(eq(77L), any(), anyLong(), anyInt());
        verify(reservationApplier).releaseStock(eq(88L), eq("CLIENT"), eq(7L), eq(5));
    }

    @Test
    @DisplayName("rollbackLocal: STOCK listing not found → throws InterbankProtocolException")
    void rollbackLocal_stockListingNotFound_throws() throws Exception {
        Transaction tx = localStockTx();
        InterbankTransaction ibt = savedIbt(tx, InterbankTransactionStatus.PREPARING);
        when(txRepo.findByTransactionRoutingNumberAndTransactionIdString(anyInt(), any()))
                .thenReturn(Optional.of(ibt));
        when(listingRepository.findByTicker(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rollbackLocal(tx.transactionId()))
                .isInstanceOf(InterbankExceptions.InterbankProtocolException.class);
    }

    // =========================================================================
    // handleNewTx
    // =========================================================================

    @Test
    @DisplayName("handleNewTx: clean tx → saves RECIPIENT record, returns YES, records inbound response")
    void handleNewTx_cleanTx_yesVoteAndSavesRecipient() throws Exception {
        Transaction tx = localMonasTx();
        IdempotenceKey key = new IdempotenceKey(REMOTE_RN, "k1");
        when(messageService.findCachedResponse(key)).thenReturn(Optional.empty());
        stubMonasAccounts("RSD");
        stubTxSave();

        TransactionVote vote = service.handleNewTx(tx, key);

        assertThat(vote.vote()).isEqualTo(TransactionVote.Vote.YES);
        ArgumentCaptor<InterbankTransaction> cap = ArgumentCaptor.forClass(InterbankTransaction.class);
        verify(txRepo).save(cap.capture());
        assertThat(cap.getValue().getRole())
                .isEqualTo(InterbankTransaction.InterbankTransactionRole.RECIPIENT);
        assertThat(cap.getValue().getStatus())
                .isEqualTo(InterbankTransactionStatus.PREPARED);
        verify(messageService).recordInboundResponse(
                eq(key), eq(MessageType.NEW_TX), any(), eq(200), any(), any());
    }

    @Test
    @DisplayName("handleNewTx: violation tx → returns NO vote and still records inbound response")
    void handleNewTx_violation_noVoteStillRecorded() throws Exception {
        Transaction tx = unbalancedTx();
        IdempotenceKey key = new IdempotenceKey(REMOTE_RN, "k2");
        when(messageService.findCachedResponse(key)).thenReturn(Optional.empty());
        stubTxSave();

        TransactionVote vote = service.handleNewTx(tx, key);

        assertThat(vote.vote()).isEqualTo(TransactionVote.Vote.NO);
        verify(messageService).recordInboundResponse(
                eq(key), eq(MessageType.NEW_TX), any(), eq(200), any(), any());
    }

    @Test
    @DisplayName("handleNewTx: second call with same key returns cached vote without any DB writes")
    void handleNewTx_idempotent_returnsCachedVote() throws Exception {
        Transaction tx = localMonasTx();
        IdempotenceKey key = new IdempotenceKey(REMOTE_RN, "k3");
        String cachedJson = objectMapper.writeValueAsString(yesVote());
        when(messageService.findCachedResponse(key)).thenReturn(Optional.of(cachedJson));

        TransactionVote vote = service.handleNewTx(tx, key);

        assertThat(vote.vote()).isEqualTo(TransactionVote.Vote.YES);
        verify(txRepo, never()).save(any());
        verifyNoInteractions(accountRepository, reservationApplier);
    }

    @Test
    @DisplayName("handleNewTx: YES vote response body is persisted as JSON containing YES")
    void handleNewTx_yesVote_responseBodyContainsYes() throws Exception {
        Transaction tx = localMonasTx();
        IdempotenceKey key = new IdempotenceKey(REMOTE_RN, "k4");
        when(messageService.findCachedResponse(key)).thenReturn(Optional.empty());
        stubMonasAccounts("RSD");
        stubTxSave();

        service.handleNewTx(tx, key);

        ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageService).recordInboundResponse(
                any(), any(), any(), anyInt(), responseCaptor.capture(), any());
        assertThat(responseCaptor.getValue()).containsIgnoringCase("YES");
    }

    // =========================================================================
    // handleCommitTx
    // =========================================================================

    @Test
    @DisplayName("handleCommitTx: commits locally and records 204 inbound response")
    void handleCommitTx_commitsAndRecords204() throws Exception {
        Transaction tx = localMonasTx();
        IdempotenceKey key = new IdempotenceKey(REMOTE_RN, "ck1");
        when(messageService.findCachedResponse(key)).thenReturn(Optional.empty());

        InterbankTransaction ibt = savedIbt(tx, InterbankTransactionStatus.PREPARING);
        when(txRepo.findByTransactionRoutingNumberAndTransactionIdString(anyInt(), any()))
                .thenReturn(Optional.of(ibt));
        when(accountRepository.findForUpdateByAccountNumber(ACCT_A))
                .thenReturn(Optional.of(buildAccount(ACCT_A, "RSD", BigDecimal.ZERO, AccountStatus.ACTIVE)));
        when(accountRepository.findForUpdateByAccountNumber(ACCT_B))
                .thenReturn(Optional.of(buildAccount(ACCT_B, "RSD", BigDecimal.valueOf(500), AccountStatus.ACTIVE)));
        when(currencyConversionService.convert(any(), any(), any())).thenReturn(BigDecimal.valueOf(100));
        stubTxSave();

        service.handleCommitTx(new CommitTransaction(tx.transactionId()), key);

        assertThat(ibt.getStatus()).isEqualTo(InterbankTransactionStatus.COMMITTED);
        verify(messageService).recordInboundResponse(
                eq(key), eq(MessageType.COMMIT_TX), any(), eq(204), any(), any());
    }

    @Test
    @DisplayName("handleCommitTx: second call with same key is a no-op (idempotent)")
    void handleCommitTx_idempotent() {
        IdempotenceKey key = new IdempotenceKey(REMOTE_RN, "ck2");
        when(messageService.findCachedResponse(key)).thenReturn(Optional.of("cached"));

        service.handleCommitTx(new CommitTransaction(new ForeignBankId(REMOTE_RN, "x")), key);

        verifyNoInteractions(txRepo, reservationApplier, accountRepository);
    }

    // =========================================================================
    // handleRollbackTx
    // =========================================================================

    @Test
    @DisplayName("handleRollbackTx: rolls back locally and records 204 inbound response")
    void handleRollbackTx_rollsBackAndRecords204() throws Exception {
        Transaction tx = localMonasTx();
        IdempotenceKey key = new IdempotenceKey(REMOTE_RN, "rk1");
        when(messageService.findCachedResponse(key)).thenReturn(Optional.empty());

        InterbankTransaction ibt = savedIbt(tx, InterbankTransactionStatus.PREPARING);
        when(txRepo.findByTransactionRoutingNumberAndTransactionIdString(anyInt(), any()))
                .thenReturn(Optional.of(ibt));
        when(accountRepository.findForUpdateByAccountNumber(ACCT_B))
                .thenReturn(Optional.of(buildAccount(ACCT_B, "RSD", BigDecimal.valueOf(500), AccountStatus.ACTIVE)));
        when(currencyConversionService.convert(any(), any(), any())).thenReturn(BigDecimal.valueOf(100));
        stubTxSave();

        service.handleRollbackTx(new RollbackTransaction(tx.transactionId()), key);

        assertThat(ibt.getStatus()).isEqualTo(InterbankTransactionStatus.ROLLED_BACK);
        verify(messageService).recordInboundResponse(
                eq(key), eq(MessageType.ROLLBACK_TX), any(), eq(204), any(), any());
    }

    @Test
    @DisplayName("handleRollbackTx: second call with same key is a no-op (idempotent)")
    void handleRollbackTx_idempotent() {
        IdempotenceKey key = new IdempotenceKey(REMOTE_RN, "rk2");
        when(messageService.findCachedResponse(key)).thenReturn(Optional.of("cached"));

        service.handleRollbackTx(new RollbackTransaction(new ForeignBankId(REMOTE_RN, "x")), key);

        verifyNoInteractions(txRepo, reservationApplier, accountRepository);
    }

    // =========================================================================
    // Helpers — transactions
    // =========================================================================

    /** Balanced local MONAS: 100 RSD debit from ACCT_A, 100 RSD credit from ACCT_B. */
    private Transaction localMonasTx() {
        return new Transaction(List.of(
                new Posting(new TxAccount.Account(ACCT_A), BigDecimal.valueOf(100),
                        new Asset.Monas(new MonetaryAsset(CurrencyCode.RSD))),
                new Posting(new TxAccount.Account(ACCT_B), BigDecimal.valueOf(-100),
                        new Asset.Monas(new MonetaryAsset(CurrencyCode.RSD)))
        ), new ForeignBankId(MY_RN, "local-monas-1"), null, null, null, null);
    }

    /** Same as localMonasTx but posting currency is EUR. */
    private Transaction localMonasTxEur() {
        return new Transaction(List.of(
                new Posting(new TxAccount.Account(ACCT_A), BigDecimal.valueOf(100),
                        new Asset.Monas(new MonetaryAsset(CurrencyCode.EUR))),
                new Posting(new TxAccount.Account(ACCT_B), BigDecimal.valueOf(-100),
                        new Asset.Monas(new MonetaryAsset(CurrencyCode.EUR)))
        ), new ForeignBankId(MY_RN, "local-monas-eur"), null, null, null, null);
    }

    /** Unbalanced: single posting, sum != 0. */
    private Transaction unbalancedTx() {
        return new Transaction(List.of(
                new Posting(new TxAccount.Account(ACCT_A), BigDecimal.valueOf(50),
                        new Asset.Monas(new MonetaryAsset(CurrencyCode.RSD)))
        ), new ForeignBankId(MY_RN, "unbalanced-1"), null, null, null, null);
    }

    /** Local STOCK: 10 AAPL, person-42 gives (credit), person-99 receives (debit). */
    private Transaction localStockTx() {
        return new Transaction(List.of(
                new Posting(new TxAccount.Person(new ForeignBankId(MY_RN, "99")),
                        BigDecimal.valueOf(10), new Asset.Stock(new StockDescription("AAPL"))),
                new Posting(new TxAccount.Person(new ForeignBankId(MY_RN, "42")),
                        BigDecimal.valueOf(-10), new Asset.Stock(new StockDescription("AAPL")))
        ), new ForeignBankId(MY_RN, "local-stock-1"), null, null, null, null);
    }

    /** One local posting (222999001) + one remote posting (111900001). */
    private Transaction mixedMonasTx() {
        return new Transaction(List.of(
                new Posting(new TxAccount.Account(MY_RN + "999001"), BigDecimal.valueOf(100),
                        new Asset.Monas(new MonetaryAsset(CurrencyCode.EUR))),
                new Posting(new TxAccount.Account(ACCT_REMOTE), BigDecimal.valueOf(-100),
                        new Asset.Monas(new MonetaryAsset(CurrencyCode.EUR)))
        ), new ForeignBankId(MY_RN, "mixed-tx-1"), null, null, null, null);
    }

    // =========================================================================
    // Helpers — Phase1Result factory
    // =========================================================================

    private TransactionExecutorService.Phase1Result phase1Yes(IdempotenceKey key, Transaction tx) {
        Message<Transaction> env = new Message<>(key, MessageType.NEW_TX, tx);
        return new TransactionExecutorService.Phase1Result(
                yesVote(), Map.of(REMOTE_RN, key), Map.of(REMOTE_RN, env));
    }

    // =========================================================================
    // Helpers — model builders
    // =========================================================================

    private Account buildAccount(String number, String currencyCode,
            BigDecimal availableBalance, AccountStatus status) {
        Currency ccy = new Currency();
        ccy.setCode(currencyCode);
        Account a = new Account();
        a.setAccountNumber(number);
        a.setStatus(status);
        a.setAvailableBalance(availableBalance);
        a.setCurrency(ccy);
        return a;
    }

    private Listing buildListing(Long id, String ticker) {
        Listing l = new Listing();
        l.setId(id);
        l.setTicker(ticker);
        return l;
    }

    /** availableQuantity = quantity (reservedQuantity defaults to 0). */
    private Portfolio buildPortfolio(int quantity) {
        Portfolio p = new Portfolio();
        p.setQuantity(quantity);
        p.setReservedQuantity(0);
        return p;
    }

    private InterbankTransaction savedIbt(Transaction tx, InterbankTransactionStatus status)
            throws JsonProcessingException {
        InterbankTransaction ibt = new InterbankTransaction();
        ibt.setTransactionRoutingNumber(tx.transactionId().routingNumber());
        ibt.setTransactionIdString(tx.transactionId().id());
        ibt.setStatus(status);
        ibt.setRole(InterbankTransaction.InterbankTransactionRole.INITIATOR);
        ibt.setTransactionBody(objectMapper.writeValueAsString(tx));
        ibt.setRetryCount(0);
        return ibt;
    }

    // =========================================================================
    // Helpers — stubs
    // =========================================================================

    /** Stubs both local accounts for a standard RSD MONAS validation. */
    private void stubMonasAccounts(String currency) {
        when(accountRepository.findByAccountNumber(ACCT_A))
                .thenReturn(Optional.of(buildAccount(ACCT_A, currency, BigDecimal.ZERO, AccountStatus.ACTIVE)));
        when(accountRepository.findByAccountNumber(ACCT_B))
                .thenReturn(Optional.of(buildAccount(ACCT_B, currency, BigDecimal.valueOf(500), AccountStatus.ACTIVE)));
    }

    private void stubTxSave() {
        when(txRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private TransactionVote yesVote() {
        return new TransactionVote(TransactionVote.Vote.YES, List.of());
    }

    private TransactionVote noVote() {
        return new TransactionVote(TransactionVote.Vote.NO,
                List.of(new NoVoteReason(NoVoteReason.Reason.INSUFFICIENT_ASSET, null)));
    }
}
