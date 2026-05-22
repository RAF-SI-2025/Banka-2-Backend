package rs.raf.banka2_bek.investmentfund;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountCategory;
import rs.raf.banka2_bek.account.model.AccountStatus;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.auth.util.UserRole;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.investmentfund.model.ClientFundPosition;
import rs.raf.banka2_bek.investmentfund.model.ClientFundTransaction;
import rs.raf.banka2_bek.investmentfund.model.ClientFundTransactionStatus;
import rs.raf.banka2_bek.investmentfund.model.InvestmentFund;
import rs.raf.banka2_bek.investmentfund.repository.ClientFundPositionRepository;
import rs.raf.banka2_bek.investmentfund.repository.ClientFundTransactionRepository;
import rs.raf.banka2_bek.investmentfund.repository.InvestmentFundRepository;
import rs.raf.banka2_bek.investmentfund.scheduler.FundValueSnapshotScheduler;
import rs.raf.banka2_bek.investmentfund.service.FundDividendService;
import rs.raf.banka2_bek.order.model.Order;
import rs.raf.banka2_bek.order.model.OrderDirection;
import rs.raf.banka2_bek.order.model.OrderStatus;
import rs.raf.banka2_bek.order.model.OrderType;
import rs.raf.banka2_bek.order.repository.OrderRepository;
import rs.raf.banka2_bek.order.service.CurrencyConversionService;
import rs.raf.banka2_bek.order.service.FundReservationService;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.model.ListingType;
import rs.raf.banka2_bek.stock.repository.ListingRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundDividendServiceTest {

    @Mock
    private InvestmentFundRepository investmentFundRepository;

    @Mock
    private ClientFundTransactionRepository clientFundTransactionRepository;

    @Mock
    private ClientFundPositionRepository clientFundPositionRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private ListingRepository listingRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private FundReservationService fundReservationService;

    @Mock
    private CurrencyConversionService currencyConversionService;

    @Mock
    private FundValueSnapshotScheduler fundValueSnapshotScheduler;

    @InjectMocks
    private FundDividendService service;

    @Test
    @DisplayName("creditDividendToFund credits fund account and saves DIVIDEND_INFLOW transaction")
    void creditDividendToFund_creditsCorrectAmount() {
        InvestmentFund fund = activeFund(1L, 100L);
        Account fundAccount = fundAccount(100L, "1000.0000", "1000.0000");

        when(investmentFundRepository.findById(1L)).thenReturn(Optional.of(fund));
        when(accountRepository.findForUpdateById(100L)).thenReturn(Optional.of(fundAccount));
        when(clientFundTransactionRepository.save(any(ClientFundTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ClientFundTransaction result = service.creditDividendToFund(
                1L,
                10L,
                new BigDecimal("250.0000")
        );

        assertBigDecimalEquals("1250.0000", fundAccount.getBalance());
        assertBigDecimalEquals("1250.0000", fundAccount.getAvailableBalance());

        ArgumentCaptor<ClientFundTransaction> txCaptor =
                ArgumentCaptor.forClass(ClientFundTransaction.class);

        verify(clientFundTransactionRepository).save(txCaptor.capture());

        ClientFundTransaction saved = txCaptor.getValue();

        assertEquals(1L, saved.getFundId());
        assertEquals(1L, saved.getUserId());
        assertEquals(UserRole.FUND, saved.getUserRole());
        assertTrue(saved.isInflow());
        assertEquals(ClientFundTransactionStatus.DIVIDEND_INFLOW, saved.getStatus());
        assertBigDecimalEquals("250.0000", saved.getAmountRsd());
        assertEquals(100L, saved.getSourceAccountId());
        assertTrue(saved.getFailureReason().contains("listingId=10"));

        assertSame(saved, result);

        verify(accountRepository).save(fundAccount);
        verify(fundValueSnapshotScheduler).snapshotFundIfMissing(fund);
    }

    @Test
    @DisplayName("creditDividendToFund throws when fund does not exist")
    void creditDividendToFund_fundNotFound_throwsException() {
        when(investmentFundRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> service.creditDividendToFund(999L, 10L, new BigDecimal("100.0000"))
        );

        verifyNoInteractions(accountRepository);
        verifyNoInteractions(clientFundTransactionRepository);
    }

    @Test
    @DisplayName("listPendingDividends returns repository results for DIVIDEND_INFLOW status")
    void listPendingDividends_returnsPendingOnly() {
        ClientFundTransaction pending = dividendTx(
                1L,
                10L,
                "300.0000",
                ClientFundTransactionStatus.DIVIDEND_INFLOW
        );

        when(clientFundTransactionRepository.findByFundIdAndStatusOrderByCreatedAtAsc(
                1L,
                ClientFundTransactionStatus.DIVIDEND_INFLOW
        )).thenReturn(List.of(pending));

        List<ClientFundTransaction> result = service.listPendingDividends(1L);

        assertEquals(1, result.size());
        assertSame(pending, result.get(0));

        verify(clientFundTransactionRepository).findByFundIdAndStatusOrderByCreatedAtAsc(
                1L,
                ClientFundTransactionStatus.DIVIDEND_INFLOW
        );
    }

    @Test
    @DisplayName("reinvestDividends creates MARKET BUY order when dividend is sufficient")
    void reinvestDividends_sufficientCash_placesOrder() {
        InvestmentFund fund = activeFund(1L, 100L);
        Account fundAccount = fundAccount(100L, "1000.0000", "1000.0000");

        ClientFundTransaction pendingDividend = dividendTx(
                1L,
                10L,
                "900.0000",
                ClientFundTransactionStatus.DIVIDEND_INFLOW
        );

        Listing listing = stockListing(10L, "AAPL", "200.0000");

        when(investmentFundRepository.findById(1L)).thenReturn(Optional.of(fund));
        when(accountRepository.findForUpdateById(100L)).thenReturn(Optional.of(fundAccount));
        when(clientFundTransactionRepository.findByFundIdAndStatusOrderByCreatedAtAsc(
                1L,
                ClientFundTransactionStatus.DIVIDEND_INFLOW
        )).thenReturn(List.of(pendingDividend));
        when(listingRepository.findById(10L)).thenReturn(Optional.of(listing));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(700L);
            return order;
        });
        when(clientFundTransactionRepository.save(any(ClientFundTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<Order> createdOrders = service.reinvestDividends(1L);

        assertEquals(1, createdOrders.size());

        Order order = createdOrders.get(0);

        assertEquals(700L, order.getId());
        assertEquals(1L, order.getUserId());
        assertEquals(UserRole.FUND, order.getUserRole());
        assertEquals(1L, order.getFundId());
        assertEquals(listing, order.getListing());
        assertEquals(OrderDirection.BUY, order.getDirection());
        assertEquals(OrderType.MARKET, order.getOrderType());
        assertEquals(OrderStatus.APPROVED, order.getStatus());
        assertEquals(4, order.getQuantity());
        assertEquals(4, order.getRemainingPortions());
        assertBigDecimalEquals("800.0000", order.getReservedAmount());

        assertEquals(ClientFundTransactionStatus.DIVIDEND_REINVESTED, pendingDividend.getStatus());
        assertNotNull(pendingDividend.getCompletedAt());
        assertTrue(pendingDividend.getFailureReason().contains("orderId=700"));

        verify(orderRepository).save(any(Order.class));
        verify(fundReservationService).reserveForBuy(order, fundAccount);
        verify(clientFundTransactionRepository).save(pendingDividend);
        verify(fundValueSnapshotScheduler).snapshotFundIfMissing(fund);
    }

    @Test
    @DisplayName("reinvestDividends does not create order when cash is below one share price")
    void reinvestDividends_insufficientCash_noOrderPlaced() {
        InvestmentFund fund = activeFund(1L, 100L);
        Account fundAccount = fundAccount(100L, "100.0000", "100.0000");

        ClientFundTransaction pendingDividend = dividendTx(
                1L,
                10L,
                "100.0000",
                ClientFundTransactionStatus.DIVIDEND_INFLOW
        );

        Listing listing = stockListing(10L, "AAPL", "200.0000");

        when(investmentFundRepository.findById(1L)).thenReturn(Optional.of(fund));
        when(accountRepository.findForUpdateById(100L)).thenReturn(Optional.of(fundAccount));
        when(clientFundTransactionRepository.findByFundIdAndStatusOrderByCreatedAtAsc(
                1L,
                ClientFundTransactionStatus.DIVIDEND_INFLOW
        )).thenReturn(List.of(pendingDividend));
        when(listingRepository.findById(10L)).thenReturn(Optional.of(listing));

        List<Order> result = service.reinvestDividends(1L);

        assertTrue(result.isEmpty());
        assertEquals(ClientFundTransactionStatus.DIVIDEND_INFLOW, pendingDividend.getStatus());

        verifyNoInteractions(orderRepository);
        verifyNoInteractions(fundReservationService);
        verify(clientFundTransactionRepository, never()).save(any(ClientFundTransaction.class));
        verify(fundValueSnapshotScheduler).snapshotFundIfMissing(fund);
    }

    @Test
    @DisplayName("reinvestDividends does nothing when there are no pending dividends")
    void reinvestDividends_noPendingDividends_doesNothing() {
        InvestmentFund fund = activeFund(1L, 100L);
        Account fundAccount = fundAccount(100L, "1000.0000", "1000.0000");

        when(investmentFundRepository.findById(1L)).thenReturn(Optional.of(fund));
        when(accountRepository.findForUpdateById(100L)).thenReturn(Optional.of(fundAccount));
        when(clientFundTransactionRepository.findByFundIdAndStatusOrderByCreatedAtAsc(
                1L,
                ClientFundTransactionStatus.DIVIDEND_INFLOW
        )).thenReturn(List.of());

        List<Order> result = service.reinvestDividends(1L);

        assertTrue(result.isEmpty());

        verifyNoInteractions(listingRepository);
        verifyNoInteractions(orderRepository);
        verifyNoInteractions(fundReservationService);
        verify(fundValueSnapshotScheduler, never()).snapshotFundIfMissing(fund);
    }

    @Test
    @DisplayName("distributeDividendsToClients distributes dividends proportionally by totalInvested")
    void distributeDividendsToClients_distributesSrazmerno() {
        InvestmentFund fund = activeFund(1L, 100L);
        Account fundAccount = fundAccount(100L, "2000.0000", "2000.0000");

        Account clientOneAccount = clientAccount(201L, "0.0000", "0.0000");
        Account clientTwoAccount = clientAccount(202L, "0.0000", "0.0000");

        ClientFundTransaction pendingDividend = dividendTx(
                1L,
                10L,
                "1000.0000",
                ClientFundTransactionStatus.DIVIDEND_INFLOW
        );

        ClientFundPosition positionOne = position(1L, 1L, 11L, "700.0000");
        ClientFundPosition positionTwo = position(2L, 1L, 22L, "300.0000");

        when(investmentFundRepository.findById(1L)).thenReturn(Optional.of(fund));
        when(accountRepository.findForUpdateById(100L)).thenReturn(Optional.of(fundAccount));
        when(clientFundTransactionRepository.findByFundIdAndStatusOrderByCreatedAtAsc(
                1L,
                ClientFundTransactionStatus.DIVIDEND_INFLOW
        )).thenReturn(List.of(pendingDividend));
        when(clientFundPositionRepository.findByFundId(1L)).thenReturn(List.of(positionOne, positionTwo));

        when(accountRepository.findByClientIdAndStatusOrderByAvailableBalanceDesc(11L, AccountStatus.ACTIVE))
                .thenReturn(List.of(clientOneAccount));
        when(accountRepository.findByClientIdAndStatusOrderByAvailableBalanceDesc(22L, AccountStatus.ACTIVE))
                .thenReturn(List.of(clientTwoAccount));

        when(accountRepository.findForUpdateById(201L)).thenReturn(Optional.of(clientOneAccount));
        when(accountRepository.findForUpdateById(202L)).thenReturn(Optional.of(clientTwoAccount));

        when(clientFundTransactionRepository.save(any(ClientFundTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<ClientFundTransaction> distributions = service.distributeDividendsToClients(1L);

        assertEquals(2, distributions.size());

        assertBigDecimalEquals("1000.0000", fundAccount.getBalance());
        assertBigDecimalEquals("1000.0000", fundAccount.getAvailableBalance());

        assertBigDecimalEquals("700.0000", clientOneAccount.getBalance());
        assertBigDecimalEquals("700.0000", clientOneAccount.getAvailableBalance());

        assertBigDecimalEquals("300.0000", clientTwoAccount.getBalance());
        assertBigDecimalEquals("300.0000", clientTwoAccount.getAvailableBalance());

        assertEquals(ClientFundTransactionStatus.DIVIDEND_DISTRIBUTED, distributions.get(0).getStatus());
        assertEquals(ClientFundTransactionStatus.DIVIDEND_DISTRIBUTED, distributions.get(1).getStatus());
        assertEquals(ClientFundTransactionStatus.DIVIDEND_DISTRIBUTED, pendingDividend.getStatus());

        verify(fundValueSnapshotScheduler).snapshotFundIfMissing(fund);
    }

    @Test
    @DisplayName("distributeDividendsToClients is idempotent when dividend is already distributed")
    void distributeDividendsToClients_alreadyDistributed_isIdempotent() {
        InvestmentFund fund = activeFund(1L, 100L);
        Account fundAccount = fundAccount(100L, "1000.0000", "1000.0000");

        when(investmentFundRepository.findById(1L)).thenReturn(Optional.of(fund));
        when(accountRepository.findForUpdateById(100L)).thenReturn(Optional.of(fundAccount));
        when(clientFundTransactionRepository.findByFundIdAndStatusOrderByCreatedAtAsc(
                1L,
                ClientFundTransactionStatus.DIVIDEND_INFLOW
        )).thenReturn(List.of());

        List<ClientFundTransaction> result = service.distributeDividendsToClients(1L);

        assertTrue(result.isEmpty());
        assertBigDecimalEquals("1000.0000", fundAccount.getBalance());
        assertBigDecimalEquals("1000.0000", fundAccount.getAvailableBalance());

        verifyNoInteractions(clientFundPositionRepository);
        verify(clientFundTransactionRepository, never()).save(any(ClientFundTransaction.class));
        verify(accountRepository, never()).save(any(Account.class));
        verify(fundValueSnapshotScheduler, never()).snapshotFundIfMissing(fund);
    }

    @Test
    @DisplayName("distributeDividendsToClients skips when fund has no client positions")
    void distributeDividendsToClients_noPositions_skips() {
        InvestmentFund fund = activeFund(1L, 100L);
        Account fundAccount = fundAccount(100L, "1000.0000", "1000.0000");

        ClientFundTransaction pendingDividend = dividendTx(
                1L,
                10L,
                "1000.0000",
                ClientFundTransactionStatus.DIVIDEND_INFLOW
        );

        when(investmentFundRepository.findById(1L)).thenReturn(Optional.of(fund));
        when(accountRepository.findForUpdateById(100L)).thenReturn(Optional.of(fundAccount));
        when(clientFundTransactionRepository.findByFundIdAndStatusOrderByCreatedAtAsc(
                1L,
                ClientFundTransactionStatus.DIVIDEND_INFLOW
        )).thenReturn(List.of(pendingDividend));
        when(clientFundPositionRepository.findByFundId(1L)).thenReturn(List.of());

        List<ClientFundTransaction> result = service.distributeDividendsToClients(1L);

        assertTrue(result.isEmpty());
        assertEquals(ClientFundTransactionStatus.DIVIDEND_INFLOW, pendingDividend.getStatus());
        assertBigDecimalEquals("1000.0000", fundAccount.getBalance());

        verify(clientFundTransactionRepository, never()).save(any(ClientFundTransaction.class));
        verify(fundValueSnapshotScheduler, never()).snapshotFundIfMissing(fund);
    }

    @Test
    @DisplayName("scheduledDividendProcessing continues with other funds if one fund fails")
    void scheduledDividendProcessing_exceptionInOneFund_continuesOthers() {
        InvestmentFund firstFund = activeFund(1L, 101L);
        InvestmentFund secondFund = activeFund(2L, 102L);

        FundDividendService spyService = spy(service);

        when(investmentFundRepository.findByActiveTrueOrderByNameAsc())
                .thenReturn(List.of(firstFund, secondFund));

        doThrow(new RuntimeException("boom")).when(spyService).reinvestDividends(1L);
        doReturn(List.of()).when(spyService).reinvestDividends(2L);

        spyService.scheduledDividendProcessing();

        verify(spyService).reinvestDividends(1L);
        verify(spyService).reinvestDividends(2L);
    }

    private InvestmentFund activeFund(Long fundId, Long accountId) {
        InvestmentFund fund = new InvestmentFund();
        fund.setId(fundId);
        fund.setName("Test Fund " + fundId);
        fund.setAccountId(accountId);
        fund.setManagerEmployeeId(1L);
        fund.setActive(true);
        return fund;
    }

    private Account fundAccount(Long id, String balance, String availableBalance) {
        return Account.builder()
                .id(id)
                .accountNumber("FUND-" + id)
                .currency(rsdCurrency())
                .balance(new BigDecimal(balance))
                .availableBalance(new BigDecimal(availableBalance))
                .reservedAmount(BigDecimal.ZERO)
                .status(AccountStatus.ACTIVE)
                .accountCategory(AccountCategory.FUND)
                .build();
    }

    private Account clientAccount(Long id, String balance, String availableBalance) {
        return Account.builder()
                .id(id)
                .accountNumber("CLIENT-" + id)
                .currency(rsdCurrency())
                .balance(new BigDecimal(balance))
                .availableBalance(new BigDecimal(availableBalance))
                .reservedAmount(BigDecimal.ZERO)
                .status(AccountStatus.ACTIVE)
                .accountCategory(AccountCategory.CLIENT)
                .build();
    }

    private Currency rsdCurrency() {
        Currency currency = new Currency();
        currency.setCode("RSD");
        currency.setName("Serbian dinar");
        currency.setSymbol("RSD");
        currency.setCountry("Serbia");
        currency.setActive(true);
        return currency;
    }

    private ClientFundTransaction dividendTx(
            Long fundId,
            Long listingId,
            String amount,
            ClientFundTransactionStatus status
    ) {
        ClientFundTransaction tx = new ClientFundTransaction();
        tx.setId(300L + listingId);
        tx.setFundId(fundId);
        tx.setUserId(fundId);
        tx.setUserRole(UserRole.FUND);
        tx.setAmountRsd(new BigDecimal(amount));
        tx.setSourceAccountId(100L);
        tx.setInflow(true);
        tx.setStatus(status);
        tx.setFailureReason("DIVIDEND_INFLOW listingId=" + listingId);
        return tx;
    }

    private Listing stockListing(Long id, String ticker, String price) {
        Listing listing = new Listing();
        listing.setId(id);
        listing.setTicker(ticker);
        listing.setName(ticker + " stock");
        listing.setListingType(ListingType.STOCK);
        listing.setExchangeAcronym("BELEX");
        listing.setQuoteCurrency("RSD");
        listing.setPrice(new BigDecimal(price));
        listing.setAsk(new BigDecimal(price));
        return listing;
    }

    private ClientFundPosition position(Long id, Long fundId, Long clientId, String totalInvested) {
        ClientFundPosition position = new ClientFundPosition();
        position.setId(id);
        position.setFundId(fundId);
        position.setUserId(clientId);
        position.setUserRole(UserRole.CLIENT);
        position.setTotalInvested(new BigDecimal(totalInvested));
        return position;
    }

    private void assertBigDecimalEquals(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}