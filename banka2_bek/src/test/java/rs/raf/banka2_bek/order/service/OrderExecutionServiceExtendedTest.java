package rs.raf.banka2_bek.order.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.company.model.Company;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.order.model.Order;
import rs.raf.banka2_bek.order.model.OrderDirection;
import rs.raf.banka2_bek.order.model.OrderStatus;
import rs.raf.banka2_bek.order.model.OrderType;
import rs.raf.banka2_bek.order.repository.OrderRepository;
import rs.raf.banka2_bek.portfolio.model.Portfolio;
import rs.raf.banka2_bek.portfolio.repository.PortfolioRepository;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.model.ListingType;
import rs.raf.banka2_bek.stock.repository.ListingRepository;
import rs.raf.banka2_bek.transaction.model.Transaction;
import rs.raf.banka2_bek.transaction.repository.TransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Extended tests for OrderExecutionService covering:
 * - Market/Limit commission calculation
 * - Limit order price thresholds
 * - Partial execution (remainingPortions decrement)
 * - AON validation
 * - After-hours delay
 * - Settlement date expiry
 * - SELL order execution and portfolio sell
 * - Employee zero commission
 * - Existing portfolio average price update
 */
@ExtendWith(MockitoExtension.class)
class OrderExecutionServiceExtendedTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private PortfolioRepository portfolioRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private AonValidationService aonValidationService;

    @InjectMocks
    private OrderExecutionService service;

    private Listing testListing;
    private Account testAccount;
    private Account bankAccount;
    private Currency rsd;

    @BeforeEach
    void setUp() {
        testListing = new Listing();
        testListing.setId(1L);
        testListing.setTicker("AAPL");
        testListing.setName("Apple Inc");
        testListing.setListingType(ListingType.STOCK);
        testListing.setAsk(new BigDecimal("100.00"));
        testListing.setBid(new BigDecimal("95.00"));
        testListing.setVolume(10000L);

        rsd = new Currency();
        rsd.setId(1L);
        rsd.setCode("RSD");

        testAccount = new Account();
        testAccount.setId(1L);
        testAccount.setBalance(new BigDecimal("100000.00"));
        testAccount.setAvailableBalance(new BigDecimal("100000.00"));
        testAccount.setCurrency(rsd);

        Company bankCompany = new Company();
        bankCompany.setId(3L);
        bankAccount = new Account();
        bankAccount.setCompany(bankCompany);
        bankAccount.setCurrency(rsd);
        bankAccount.setBalance(new BigDecimal("500000.00"));
        bankAccount.setAvailableBalance(new BigDecimal("500000.00"));
    }

    private Order buildOrder(OrderType type, OrderDirection direction, String role) {
        Order order = new Order();
        order.setId(100L);
        order.setUserId(1L);
        order.setListing(testListing);
        order.setAccountId(1L);
        order.setOrderType(type);
        order.setDirection(direction);
        order.setQuantity(10);
        order.setRemainingPortions(10);
        order.setContractSize(1);
        order.setUserRole(role);
        order.setStatus(OrderStatus.APPROVED);
        order.setAfterHours(false);
        order.setAllOrNone(false);
        return order;
    }

    // ─── Commission Calculation ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Commission calculation")
    class CommissionCalc {

        @Test
        @DisplayName("MARKET commission = max(14% * totalPrice, $7) for small orders")
        void marketCommission_minFloor() {
            // totalPrice = 100 * 1 * 1 = 100, 14% of 100 = 14, max(14, 7) = 14
            Order order = buildOrder(OrderType.MARKET, OrderDirection.BUY, "CLIENT");
            order.setQuantity(1);
            order.setRemainingPortions(1);

            when(orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED)).thenReturn(List.of(order));
            when(listingRepository.findById(1L)).thenReturn(Optional.of(testListing));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
            when(aonValidationService.checkCanExecuteAon(any(), anyInt())).thenReturn(true);
            when(portfolioRepository.findByUserId(any())).thenReturn(Collections.emptyList());
            when(accountRepository.findAll()).thenReturn(List.of(bankAccount));

            service.executeOrders();

            // Bank account balance should increase by commission
            assertThat(bankAccount.getBalance()).isGreaterThan(new BigDecimal("500000.00"));
        }

        @Test
        @DisplayName("EMPLOYEE orders pay zero commission")
        void employeeZeroCommission() {
            Order order = buildOrder(OrderType.MARKET, OrderDirection.BUY, "EMPLOYEE");
            order.setQuantity(1);
            order.setRemainingPortions(1);

            when(orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED)).thenReturn(List.of(order));
            when(listingRepository.findById(1L)).thenReturn(Optional.of(testListing));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
            when(aonValidationService.checkCanExecuteAon(any(), anyInt())).thenReturn(true);
            when(portfolioRepository.findByUserId(any())).thenReturn(Collections.emptyList());

            BigDecimal bankBefore = bankAccount.getBalance();

            service.executeOrders();

            // Bank account should NOT be touched (no commission transaction)
            assertThat(bankAccount.getBalance()).isEqualByComparingTo(bankBefore);
        }
    }

    // ─── Limit Order Logic ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Limit order price checks")
    class LimitOrders {

        @Test
        @DisplayName("LIMIT BUY: does not execute when ask > limitValue")
        void limitBuy_askTooHigh_noExecution() {
            Order order = buildOrder(OrderType.LIMIT, OrderDirection.BUY, "CLIENT");
            order.setLimitValue(new BigDecimal("90.00")); // Ask is 100, limit is 90

            when(orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED)).thenReturn(List.of(order));
            when(listingRepository.findById(1L)).thenReturn(Optional.of(testListing));

            service.executeOrders();

            assertThat(order.isDone()).isFalse();
            assertThat(order.getRemainingPortions()).isEqualTo(10);
            verify(accountRepository, never()).save(any());
        }

        @Test
        @DisplayName("LIMIT BUY: executes when ask <= limitValue")
        void limitBuy_askOk_executes() {
            Order order = buildOrder(OrderType.LIMIT, OrderDirection.BUY, "CLIENT");
            order.setLimitValue(new BigDecimal("110.00")); // Ask is 100, limit is 110

            when(orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED)).thenReturn(List.of(order));
            when(listingRepository.findById(1L)).thenReturn(Optional.of(testListing));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
            when(aonValidationService.checkCanExecuteAon(any(), anyInt())).thenReturn(true);
            when(portfolioRepository.findByUserId(any())).thenReturn(Collections.emptyList());
            when(accountRepository.findAll()).thenReturn(List.of(bankAccount));

            service.executeOrders();

            // Order should have been processed (some portions filled)
            verify(orderRepository, atLeastOnce()).save(order);
        }

        @Test
        @DisplayName("LIMIT SELL: does not execute when bid < limitValue")
        void limitSell_bidTooLow_noExecution() {
            Order order = buildOrder(OrderType.LIMIT, OrderDirection.SELL, "CLIENT");
            order.setLimitValue(new BigDecimal("110.00")); // Bid is 95, limit is 110

            when(orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED)).thenReturn(List.of(order));
            when(listingRepository.findById(1L)).thenReturn(Optional.of(testListing));

            service.executeOrders();

            assertThat(order.isDone()).isFalse();
            verify(accountRepository, never()).save(any());
        }

        @Test
        @DisplayName("LIMIT SELL: executes when bid >= limitValue")
        void limitSell_bidOk_executes() {
            Order order = buildOrder(OrderType.LIMIT, OrderDirection.SELL, "CLIENT");
            order.setLimitValue(new BigDecimal("90.00")); // Bid is 95, limit is 90
            order.setQuantity(5);
            order.setRemainingPortions(5);

            // Must have portfolio to sell from
            Portfolio existing = new Portfolio();
            existing.setUserId(1L);
            existing.setListingId(1L);
            existing.setQuantity(100);
            existing.setAverageBuyPrice(new BigDecimal("80.00"));

            when(orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED)).thenReturn(List.of(order));
            when(listingRepository.findById(1L)).thenReturn(Optional.of(testListing));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
            when(aonValidationService.checkCanExecuteAon(any(), anyInt())).thenReturn(true);
            when(portfolioRepository.findByUserId(1L)).thenReturn(List.of(existing));
            when(accountRepository.findAll()).thenReturn(List.of(bankAccount));

            service.executeOrders();

            verify(orderRepository, atLeastOnce()).save(order);
        }
    }

    // ─── After-Hours Delay ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("After-hours delay")
    class AfterHoursDelay {

        @Test
        @DisplayName("after-hours order is skipped when delay has not passed")
        void afterHours_delayNotPassed_skipped() {
            Order order = buildOrder(OrderType.MARKET, OrderDirection.BUY, "CLIENT");
            order.setAfterHours(true);
            order.setLastModification(LocalDateTime.now()); // Just modified - delay not passed

            when(orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED)).thenReturn(List.of(order));
            lenient().when(listingRepository.findById(1L)).thenReturn(Optional.of(testListing));

            service.executeOrders();

            // Order should not be processed
            assertThat(order.getRemainingPortions()).isEqualTo(10);
            verify(accountRepository, never()).save(any());
        }

        @Test
        @DisplayName("after-hours order executes when delay has passed")
        void afterHours_delayPassed_executes() {
            Order order = buildOrder(OrderType.MARKET, OrderDirection.BUY, "CLIENT");
            order.setAfterHours(true);
            order.setLastModification(LocalDateTime.now().minusMinutes(31)); // 31 min ago > 30 min delay

            when(orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED)).thenReturn(List.of(order));
            when(listingRepository.findById(1L)).thenReturn(Optional.of(testListing));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
            when(aonValidationService.checkCanExecuteAon(any(), anyInt())).thenReturn(true);
            when(portfolioRepository.findByUserId(any())).thenReturn(Collections.emptyList());
            when(accountRepository.findAll()).thenReturn(List.of(bankAccount));

            service.executeOrders();

            verify(orderRepository, atLeastOnce()).save(order);
        }

        @Test
        @DisplayName("after-hours order with null lastModification executes")
        void afterHours_nullLastMod_executes() {
            Order order = buildOrder(OrderType.MARKET, OrderDirection.BUY, "CLIENT");
            order.setAfterHours(true);
            order.setLastModification(null);

            when(orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED)).thenReturn(List.of(order));
            when(listingRepository.findById(1L)).thenReturn(Optional.of(testListing));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
            when(aonValidationService.checkCanExecuteAon(any(), anyInt())).thenReturn(true);
            when(portfolioRepository.findByUserId(any())).thenReturn(Collections.emptyList());
            when(accountRepository.findAll()).thenReturn(List.of(bankAccount));

            service.executeOrders();

            verify(orderRepository, atLeastOnce()).save(order);
        }
    }

    // ─── Settlement Date Expiry ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Settlement date expiry")
    class SettlementDateExpiry {

        @Test
        @DisplayName("order with expired settlement date is auto-declined")
        void expiredSettlementDate_autoDeclined() {
            testListing.setSettlementDate(LocalDate.now().minusDays(1));
            Order order = buildOrder(OrderType.MARKET, OrderDirection.BUY, "CLIENT");

            when(orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED)).thenReturn(List.of(order));
            lenient().when(listingRepository.findById(1L)).thenReturn(Optional.of(testListing));

            service.executeOrders();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.DECLINED);
            assertThat(order.isDone()).isTrue();
            verify(orderRepository).save(order);
        }

        @Test
        @DisplayName("order with future settlement date processes normally")
        void futureSettlementDate_processesNormally() {
            testListing.setSettlementDate(LocalDate.now().plusDays(30));
            Order order = buildOrder(OrderType.MARKET, OrderDirection.BUY, "CLIENT");

            when(orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED)).thenReturn(List.of(order));
            when(listingRepository.findById(1L)).thenReturn(Optional.of(testListing));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
            when(aonValidationService.checkCanExecuteAon(any(), anyInt())).thenReturn(true);
            when(portfolioRepository.findByUserId(any())).thenReturn(Collections.emptyList());
            when(accountRepository.findAll()).thenReturn(List.of(bankAccount));

            service.executeOrders();

            assertThat(order.getStatus()).isNotEqualTo(OrderStatus.DECLINED);
        }

        @Test
        @DisplayName("order with null settlement date processes normally")
        void nullSettlementDate_processesNormally() {
            testListing.setSettlementDate(null);
            Order order = buildOrder(OrderType.MARKET, OrderDirection.BUY, "CLIENT");

            when(orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED)).thenReturn(List.of(order));
            when(listingRepository.findById(1L)).thenReturn(Optional.of(testListing));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
            when(aonValidationService.checkCanExecuteAon(any(), anyInt())).thenReturn(true);
            when(portfolioRepository.findByUserId(any())).thenReturn(Collections.emptyList());
            when(accountRepository.findAll()).thenReturn(List.of(bankAccount));

            service.executeOrders();

            assertThat(order.getStatus()).isNotEqualTo(OrderStatus.DECLINED);
        }
    }

    // ─── AON Validation ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AON (All-or-None) validation")
    class AonValidation {

        @Test
        @DisplayName("AON order is not executed when AON validation fails")
        void aonFails_noExecution() {
            Order order = buildOrder(OrderType.MARKET, OrderDirection.BUY, "CLIENT");
            order.setAllOrNone(true);

            when(orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED)).thenReturn(List.of(order));
            when(listingRepository.findById(1L)).thenReturn(Optional.of(testListing));
            when(aonValidationService.checkCanExecuteAon(eq(order), anyInt())).thenReturn(false);

            service.executeOrders();

            assertThat(order.getRemainingPortions()).isEqualTo(10);
            assertThat(order.isDone()).isFalse();
            verify(accountRepository, never()).findById(any());
        }

        @Test
        @DisplayName("AON order fills all quantity at once when AON validation passes")
        void aonPasses_fillsAll() {
            Order order = buildOrder(OrderType.MARKET, OrderDirection.BUY, "CLIENT");
            order.setAllOrNone(true);

            when(orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED)).thenReturn(List.of(order));
            when(listingRepository.findById(1L)).thenReturn(Optional.of(testListing));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
            when(aonValidationService.checkCanExecuteAon(eq(order), anyInt())).thenReturn(true);
            when(portfolioRepository.findByUserId(any())).thenReturn(Collections.emptyList());
            when(accountRepository.findAll()).thenReturn(List.of(bankAccount));

            service.executeOrders();

            // AON should fill the entire quantity and mark as done
            assertThat(order.getRemainingPortions()).isEqualTo(0);
            assertThat(order.isDone()).isTrue();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.DONE);
        }
    }

    // ─── Portfolio Updates ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Portfolio updates on buy/sell")
    class PortfolioUpdates {

        @Test
        @DisplayName("BUY creates new portfolio entry when none exists")
        void buyCreatesNewPortfolio() {
            Order order = buildOrder(OrderType.MARKET, OrderDirection.BUY, "CLIENT");

            when(orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED)).thenReturn(List.of(order));
            when(listingRepository.findById(1L)).thenReturn(Optional.of(testListing));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
            when(aonValidationService.checkCanExecuteAon(any(), anyInt())).thenReturn(true);
            when(portfolioRepository.findByUserId(1L)).thenReturn(Collections.emptyList());
            when(accountRepository.findAll()).thenReturn(List.of(bankAccount));

            service.executeOrders();

            ArgumentCaptor<Portfolio> captor = ArgumentCaptor.forClass(Portfolio.class);
            verify(portfolioRepository, atLeastOnce()).save(captor.capture());
            Portfolio saved = captor.getValue();
            assertThat(saved.getUserId()).isEqualTo(1L);
            assertThat(saved.getListingId()).isEqualTo(1L);
            assertThat(saved.getListingTicker()).isEqualTo("AAPL");
            assertThat(saved.getPublicQuantity()).isEqualTo(0);
        }

        @Test
        @DisplayName("BUY updates existing portfolio with weighted average")
        void buyUpdatesExistingPortfolio() {
            Portfolio existing = new Portfolio();
            existing.setUserId(1L);
            existing.setListingId(1L);
            existing.setListingTicker("AAPL");
            existing.setListingName("Apple Inc");
            existing.setListingType("STOCK");
            existing.setQuantity(10);
            existing.setAverageBuyPrice(new BigDecimal("90.00"));
            existing.setPublicQuantity(0);

            Order order = buildOrder(OrderType.MARKET, OrderDirection.BUY, "CLIENT");

            when(orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED)).thenReturn(List.of(order));
            when(listingRepository.findById(1L)).thenReturn(Optional.of(testListing));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
            when(aonValidationService.checkCanExecuteAon(any(), anyInt())).thenReturn(true);
            when(portfolioRepository.findByUserId(1L)).thenReturn(List.of(existing));
            when(accountRepository.findAll()).thenReturn(List.of(bankAccount));

            service.executeOrders();

            // Portfolio quantity should increase
            assertThat(existing.getQuantity()).isGreaterThan(10);
            verify(portfolioRepository, atLeastOnce()).save(existing);
        }

        @Test
        @DisplayName("SELL removes portfolio when quantity reaches zero")
        void sellDeletesPortfolioWhenEmpty() {
            Portfolio existing = new Portfolio();
            existing.setUserId(1L);
            existing.setListingId(1L);
            existing.setListingTicker("AAPL");
            existing.setListingName("Apple Inc");
            existing.setListingType("STOCK");
            existing.setQuantity(1); // Only 1 share
            existing.setAverageBuyPrice(new BigDecimal("80.00"));

            Order order = buildOrder(OrderType.MARKET, OrderDirection.SELL, "CLIENT");
            order.setQuantity(1);
            order.setRemainingPortions(1);

            when(orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED)).thenReturn(List.of(order));
            when(listingRepository.findById(1L)).thenReturn(Optional.of(testListing));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
            when(aonValidationService.checkCanExecuteAon(any(), anyInt())).thenReturn(true);
            when(portfolioRepository.findByUserId(1L)).thenReturn(List.of(existing));
            when(accountRepository.findAll()).thenReturn(List.of(bankAccount));

            service.executeOrders();

            verify(portfolioRepository).delete(existing);
        }
    }

    // ─── Filters: STOP/STOP_LIMIT orders are not executed ───────────────────────

    @Nested
    @DisplayName("Order type filtering")
    class OrderTypeFiltering {

        @Test
        @DisplayName("STOP orders are filtered out and not executed")
        void stopOrdersFiltered() {
            Order stopOrder = buildOrder(OrderType.STOP, OrderDirection.BUY, "CLIENT");
            stopOrder.setStopValue(new BigDecimal("110.00"));

            when(orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED)).thenReturn(List.of(stopOrder));

            service.executeOrders();

            verify(listingRepository, never()).findById(any());
            verify(accountRepository, never()).save(any());
        }

        @Test
        @DisplayName("STOP_LIMIT orders are filtered out and not executed")
        void stopLimitOrdersFiltered() {
            Order stopLimitOrder = buildOrder(OrderType.STOP_LIMIT, OrderDirection.SELL, "CLIENT");
            stopLimitOrder.setStopValue(new BigDecimal("90.00"));
            stopLimitOrder.setLimitValue(new BigDecimal("88.00"));

            when(orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED)).thenReturn(List.of(stopLimitOrder));

            service.executeOrders();

            verify(listingRepository, never()).findById(any());
        }

        @Test
        @DisplayName("empty approved orders list does nothing")
        void noApprovedOrders() {
            when(orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED)).thenReturn(Collections.emptyList());

            service.executeOrders();

            verify(listingRepository, never()).findById(any());
        }
    }

    // ─── Transaction creation ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Transaction creation")
    class TransactionCreation {

        @Test
        @DisplayName("fill creates a transaction record")
        void fillCreatesTransaction() {
            Order order = buildOrder(OrderType.MARKET, OrderDirection.BUY, "CLIENT");

            when(orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED)).thenReturn(List.of(order));
            when(listingRepository.findById(1L)).thenReturn(Optional.of(testListing));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
            when(aonValidationService.checkCanExecuteAon(any(), anyInt())).thenReturn(true);
            when(portfolioRepository.findByUserId(any())).thenReturn(Collections.emptyList());
            when(accountRepository.findAll()).thenReturn(List.of(bankAccount));

            service.executeOrders();

            // At least one fill transaction + one commission transaction
            verify(transactionRepository, atLeast(1)).save(any(Transaction.class));
        }
    }

    // ─── Error Handling ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("exception on one order does not break other orders")
        void exceptionOnOneOrder_doesNotBreakOthers() {
            Order badOrder = buildOrder(OrderType.MARKET, OrderDirection.BUY, "CLIENT");
            badOrder.setId(1L);
            Listing badListing = new Listing();
            badListing.setId(999L);
            badOrder.setListing(badListing);

            Order goodOrder = buildOrder(OrderType.MARKET, OrderDirection.BUY, "CLIENT");
            goodOrder.setId(2L);

            when(orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED))
                    .thenReturn(List.of(badOrder, goodOrder));
            when(listingRepository.findById(999L)).thenReturn(Optional.empty()); // Will throw
            when(listingRepository.findById(1L)).thenReturn(Optional.of(testListing));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
            when(aonValidationService.checkCanExecuteAon(any(), anyInt())).thenReturn(true);
            when(portfolioRepository.findByUserId(any())).thenReturn(Collections.emptyList());
            when(accountRepository.findAll()).thenReturn(List.of(bankAccount));

            // Should not throw - errors are caught per order
            service.executeOrders();

            // Good order should still be processed
            verify(orderRepository, atLeastOnce()).save(goodOrder);
        }
    }
}
