package rs.raf.banka2_bek.order.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.order.model.Order;
import rs.raf.banka2_bek.order.model.OrderDirection;
import rs.raf.banka2_bek.order.model.OrderStatus;
import rs.raf.banka2_bek.order.model.OrderType;
import rs.raf.banka2_bek.order.repository.OrderRepository;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.repository.ListingRepository;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StopOrderActivationServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ListingRepository listingRepository;

    @InjectMocks
    private StopOrderActivationService stopOrderActivationService;

    // 1. TEST: STOP SELL - Uspešna aktivacija
    @Test
    void testCheckAndActivate_StopSell_Success() {
        Listing stock = new Listing();
        stock.setId(2L);
        stock.setPrice(new BigDecimal("95.00")); // Cena je pala na 95

        Order order = new Order();
        order.setId(20L);
        order.setOrderType(OrderType.STOP);
        order.setDirection(OrderDirection.SELL);
        order.setStopValue(new BigDecimal("100.00")); // Stop na 100 (aktiviraj ako je <= 100)
        order.setListing(stock);
        order.setStatus(OrderStatus.APPROVED);

        when(orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED)).thenReturn(List.of(order));
        when(listingRepository.findById(2L)).thenReturn(Optional.of(stock));

        stopOrderActivationService.checkAndActivateStopOrders();

        assertEquals(OrderType.MARKET, order.getOrderType());
        verify(orderRepository, times(1)).save(order);
    }

    // 2. TEST: STOP_LIMIT BUY - Konverzija u LIMIT
    @Test
    void testCheckAndActivate_StopLimitBuy_ToLimit() {
        Listing stock = new Listing();
        stock.setId(3L);
        stock.setPrice(new BigDecimal("210.00"));

        Order order = new Order();
        order.setId(30L);
        order.setOrderType(OrderType.STOP_LIMIT);
        order.setDirection(OrderDirection.BUY);
        order.setStopValue(new BigDecimal("200.00"));
        order.setLimitValue(new BigDecimal("205.00")); // Limit koji treba da se postavi
        order.setListing(stock);
        order.setStatus(OrderStatus.APPROVED);

        when(orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED)).thenReturn(List.of(order));
        when(listingRepository.findById(3L)).thenReturn(Optional.of(stock));

        stopOrderActivationService.checkAndActivateStopOrders();

        assertEquals(OrderType.LIMIT, order.getOrderType());
        assertEquals(new BigDecimal("205.00"), order.getPricePerUnit()); // Postavlja se limitValue
        verify(orderRepository, times(1)).save(order);
    }

    // 3. TEST: Nalog se ne aktivira jer uslov nije ispunjen
    @Test
    void testCheckAndActivate_ConditionNotMet_NoAction() {
        Listing stock = new Listing();
        stock.setId(4L);
        stock.setPrice(new BigDecimal("140.00")); // Cena je 140

        Order order = new Order();
        order.setId(40L);
        order.setOrderType(OrderType.STOP);
        order.setDirection(OrderDirection.BUY);
        order.setStopValue(new BigDecimal("150.00")); // Čeka 150, ali je tek na 140
        order.setListing(stock);
        order.setStatus(OrderStatus.APPROVED);

        when(orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED)).thenReturn(List.of(order));
        when(listingRepository.findById(4L)).thenReturn(Optional.of(stock));

        stopOrderActivationService.checkAndActivateStopOrders();

        // Tip mora ostati STOP, a save se ne sme pozvati za ovaj nalog
        assertEquals(OrderType.STOP, order.getOrderType());
        verify(orderRepository, never()).save(order);
    }

    // 4. TEST: Edge case - Listing ne postoji u bazi
    @Test
    void testCheckAndActivate_ListingNotFound_Skip() {
        Order order = new Order();
        order.setId(50L);
        order.setListing(new Listing()); // Prazan listing
        order.getListing().setId(999L);
        order.setOrderType(OrderType.STOP);
        order.setStatus(OrderStatus.APPROVED);

        when(orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED)).thenReturn(List.of(order));
        when(listingRepository.findById(999L)).thenReturn(Optional.empty());

        stopOrderActivationService.checkAndActivateStopOrders();

        // Proveravamo da save nije pozvan jer je listing null
        verify(orderRepository, never()).save(any(Order.class));
    }

    // ================================================================
    // 5. STOP BUY: currentPrice >= stopValue → activate to MARKET
    // ================================================================
    @Test
    void testCheckAndActivate_StopBuy_PriceExceedsStop_ActivatesToMarket() {
        Listing stock = new Listing();
        stock.setId(5L);
        stock.setPrice(new BigDecimal("160.00")); // Price rose above stop

        Order order = new Order();
        order.setId(60L);
        order.setOrderType(OrderType.STOP);
        order.setDirection(OrderDirection.BUY);
        order.setStopValue(new BigDecimal("150.00")); // Stop at 150, price is 160
        order.setListing(stock);
        order.setStatus(OrderStatus.APPROVED);

        when(orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED)).thenReturn(List.of(order));
        when(listingRepository.findById(5L)).thenReturn(Optional.of(stock));

        stopOrderActivationService.checkAndActivateStopOrders();

        assertEquals(OrderType.MARKET, order.getOrderType());
        assertEquals(new BigDecimal("160.00"), order.getPricePerUnit());
        assertNotNull(order.getLastModification());
        verify(orderRepository, times(1)).save(order);
    }

    // ================================================================
    // 6. STOP BUY: price exactly equals stop value → should activate
    // ================================================================
    @Test
    void testCheckAndActivate_StopBuy_PriceEqualsStop_Activates() {
        Listing stock = new Listing();
        stock.setId(6L);
        stock.setPrice(new BigDecimal("150.00"));

        Order order = new Order();
        order.setId(70L);
        order.setOrderType(OrderType.STOP);
        order.setDirection(OrderDirection.BUY);
        order.setStopValue(new BigDecimal("150.00"));
        order.setListing(stock);
        order.setStatus(OrderStatus.APPROVED);

        when(orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED)).thenReturn(List.of(order));
        when(listingRepository.findById(6L)).thenReturn(Optional.of(stock));

        stopOrderActivationService.checkAndActivateStopOrders();

        assertEquals(OrderType.MARKET, order.getOrderType());
        verify(orderRepository, times(1)).save(order);
    }

    // ================================================================
    // 7. STOP_LIMIT SELL: activation converts to LIMIT with limitValue as price
    // ================================================================
    @Test
    void testCheckAndActivate_StopLimitSell_ActivatesToLimit() {
        Listing stock = new Listing();
        stock.setId(7L);
        stock.setPrice(new BigDecimal("80.00")); // Price dropped below stop

        Order order = new Order();
        order.setId(80L);
        order.setOrderType(OrderType.STOP_LIMIT);
        order.setDirection(OrderDirection.SELL);
        order.setStopValue(new BigDecimal("90.00")); // Stop at 90
        order.setLimitValue(new BigDecimal("85.00")); // Limit to set
        order.setListing(stock);
        order.setStatus(OrderStatus.APPROVED);

        when(orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED)).thenReturn(List.of(order));
        when(listingRepository.findById(7L)).thenReturn(Optional.of(stock));

        stopOrderActivationService.checkAndActivateStopOrders();

        assertEquals(OrderType.LIMIT, order.getOrderType());
        assertEquals(new BigDecimal("85.00"), order.getPricePerUnit());
        assertNotNull(order.getLastModification());
        verify(orderRepository, times(1)).save(order);
    }

    // ================================================================
    // 8. Listing price is null → skip (no activation, no save)
    // ================================================================
    @Test
    void testCheckAndActivate_ListingPriceNull_Skip() {
        Listing stock = new Listing();
        stock.setId(8L);
        stock.setPrice(null);

        Order order = new Order();
        order.setId(90L);
        order.setOrderType(OrderType.STOP);
        order.setDirection(OrderDirection.BUY);
        order.setStopValue(new BigDecimal("100.00"));
        order.setListing(stock);
        order.setStatus(OrderStatus.APPROVED);

        when(orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED)).thenReturn(List.of(order));
        when(listingRepository.findById(8L)).thenReturn(Optional.of(stock));

        stopOrderActivationService.checkAndActivateStopOrders();

        assertEquals(OrderType.STOP, order.getOrderType());
        verify(orderRepository, never()).save(any(Order.class));
    }

    // ================================================================
    // 9. Listing price is zero → skip
    // ================================================================
    @Test
    void testCheckAndActivate_ListingPriceZero_Skip() {
        Listing stock = new Listing();
        stock.setId(9L);
        stock.setPrice(BigDecimal.ZERO);

        Order order = new Order();
        order.setId(100L);
        order.setOrderType(OrderType.STOP);
        order.setDirection(OrderDirection.SELL);
        order.setStopValue(new BigDecimal("50.00"));
        order.setListing(stock);
        order.setStatus(OrderStatus.APPROVED);

        when(orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED)).thenReturn(List.of(order));
        when(listingRepository.findById(9L)).thenReturn(Optional.of(stock));

        stopOrderActivationService.checkAndActivateStopOrders();

        assertEquals(OrderType.STOP, order.getOrderType());
        verify(orderRepository, never()).save(any(Order.class));
    }

    // ================================================================
    // 10. Listing price is negative → skip
    // ================================================================
    @Test
    void testCheckAndActivate_ListingPriceNegative_Skip() {
        Listing stock = new Listing();
        stock.setId(10L);
        stock.setPrice(new BigDecimal("-5.00"));

        Order order = new Order();
        order.setId(110L);
        order.setOrderType(OrderType.STOP);
        order.setDirection(OrderDirection.BUY);
        order.setStopValue(new BigDecimal("100.00"));
        order.setListing(stock);
        order.setStatus(OrderStatus.APPROVED);

        when(orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED)).thenReturn(List.of(order));
        when(listingRepository.findById(10L)).thenReturn(Optional.of(stock));

        stopOrderActivationService.checkAndActivateStopOrders();

        assertEquals(OrderType.STOP, order.getOrderType());
        verify(orderRepository, never()).save(any(Order.class));
    }

    // ================================================================
    // 11. Order with null stopValue → skip
    // ================================================================
    @Test
    void testCheckAndActivate_NullStopValue_Skip() {
        Listing stock = new Listing();
        stock.setId(11L);
        stock.setPrice(new BigDecimal("200.00"));

        Order order = new Order();
        order.setId(120L);
        order.setOrderType(OrderType.STOP);
        order.setDirection(OrderDirection.BUY);
        order.setStopValue(null); // Missing stop value
        order.setListing(stock);
        order.setStatus(OrderStatus.APPROVED);

        when(orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED)).thenReturn(List.of(order));
        when(listingRepository.findById(11L)).thenReturn(Optional.of(stock));

        stopOrderActivationService.checkAndActivateStopOrders();

        assertEquals(OrderType.STOP, order.getOrderType());
        verify(orderRepository, never()).save(any(Order.class));
    }

    // ================================================================
    // 12. Empty list of stop orders → no processing, no save
    // ================================================================
    @Test
    void testCheckAndActivate_NoStopOrders_NoAction() {
        when(orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED))
                .thenReturn(Collections.emptyList());

        stopOrderActivationService.checkAndActivateStopOrders();

        verify(listingRepository, never()).findById(anyLong());
        verify(orderRepository, never()).save(any(Order.class));
    }

    // ================================================================
    // 13. Non-stop orders filtered out (MARKET and LIMIT should be ignored)
    // ================================================================
    @Test
    void testCheckAndActivate_NonStopOrdersFiltered() {
        Order marketOrder = new Order();
        marketOrder.setId(130L);
        marketOrder.setOrderType(OrderType.MARKET);
        marketOrder.setDirection(OrderDirection.BUY);
        marketOrder.setStatus(OrderStatus.APPROVED);

        Order limitOrder = new Order();
        limitOrder.setId(131L);
        limitOrder.setOrderType(OrderType.LIMIT);
        limitOrder.setDirection(OrderDirection.SELL);
        limitOrder.setStatus(OrderStatus.APPROVED);

        when(orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED))
                .thenReturn(List.of(marketOrder, limitOrder));

        stopOrderActivationService.checkAndActivateStopOrders();

        // No listings should be looked up since both orders are filtered out
        verify(listingRepository, never()).findById(anyLong());
        verify(orderRepository, never()).save(any(Order.class));
    }

    // ================================================================
    // 14. Multiple orders — mix of activatable and non-activatable
    // ================================================================
    @Test
    void testCheckAndActivate_MultipleOrders_OnlyMatchingActivated() {
        Listing stock1 = new Listing();
        stock1.setId(14L);
        stock1.setPrice(new BigDecimal("200.00"));

        Listing stock2 = new Listing();
        stock2.setId(15L);
        stock2.setPrice(new BigDecimal("50.00"));

        // Should activate: BUY stop at 150, price is 200
        Order shouldActivate = new Order();
        shouldActivate.setId(140L);
        shouldActivate.setOrderType(OrderType.STOP);
        shouldActivate.setDirection(OrderDirection.BUY);
        shouldActivate.setStopValue(new BigDecimal("150.00"));
        shouldActivate.setListing(stock1);
        shouldActivate.setStatus(OrderStatus.APPROVED);

        // Should NOT activate: BUY stop at 100, price is 50
        Order shouldNotActivate = new Order();
        shouldNotActivate.setId(141L);
        shouldNotActivate.setOrderType(OrderType.STOP);
        shouldNotActivate.setDirection(OrderDirection.BUY);
        shouldNotActivate.setStopValue(new BigDecimal("100.00"));
        shouldNotActivate.setListing(stock2);
        shouldNotActivate.setStatus(OrderStatus.APPROVED);

        when(orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED))
                .thenReturn(List.of(shouldActivate, shouldNotActivate));
        when(listingRepository.findById(14L)).thenReturn(Optional.of(stock1));
        when(listingRepository.findById(15L)).thenReturn(Optional.of(stock2));

        stopOrderActivationService.checkAndActivateStopOrders();

        assertEquals(OrderType.MARKET, shouldActivate.getOrderType());
        assertEquals(OrderType.STOP, shouldNotActivate.getOrderType());
        verify(orderRepository, times(1)).save(shouldActivate);
        verify(orderRepository, never()).save(shouldNotActivate);
    }

    // ================================================================
    // 15. Exception during processing one order doesn't stop others
    // ================================================================
    @Test
    void testCheckAndActivate_ExceptionOnOneOrder_ContinuesProcessing() {
        Listing goodStock = new Listing();
        goodStock.setId(16L);
        goodStock.setPrice(new BigDecimal("200.00"));

        // First order: listing lookup throws exception
        Order badOrder = new Order();
        badOrder.setId(150L);
        badOrder.setOrderType(OrderType.STOP);
        badOrder.setDirection(OrderDirection.BUY);
        badOrder.setStopValue(new BigDecimal("100.00"));
        Listing badListing = new Listing();
        badListing.setId(999L);
        badOrder.setListing(badListing);
        badOrder.setStatus(OrderStatus.APPROVED);

        // Second order: should activate normally
        Order goodOrder = new Order();
        goodOrder.setId(151L);
        goodOrder.setOrderType(OrderType.STOP);
        goodOrder.setDirection(OrderDirection.BUY);
        goodOrder.setStopValue(new BigDecimal("150.00"));
        goodOrder.setListing(goodStock);
        goodOrder.setStatus(OrderStatus.APPROVED);

        when(orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED))
                .thenReturn(List.of(badOrder, goodOrder));
        when(listingRepository.findById(999L)).thenThrow(new RuntimeException("DB error"));
        when(listingRepository.findById(16L)).thenReturn(Optional.of(goodStock));

        stopOrderActivationService.checkAndActivateStopOrders();

        // Good order should still be activated despite bad order throwing
        assertEquals(OrderType.MARKET, goodOrder.getOrderType());
        verify(orderRepository, times(1)).save(goodOrder);
    }
}