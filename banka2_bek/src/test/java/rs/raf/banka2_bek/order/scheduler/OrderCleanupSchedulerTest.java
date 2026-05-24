package rs.raf.banka2_bek.order.scheduler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.notification.model.NotificationType;
import rs.raf.banka2_bek.notification.service.NotificationService;
import rs.raf.banka2_bek.order.model.Order;
import rs.raf.banka2_bek.order.model.OrderStatus;
import rs.raf.banka2_bek.order.repository.OrderRepository;
import rs.raf.banka2_bek.stock.model.Listing;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OrderCleanupSchedulerTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private OrderCleanupScheduler orderCleanupScheduler;

    @Test
    void cleanupExpiredOrders_shouldDeclineOrderWithPassedSettlementDate() {
        Order order = mock(Order.class);
        Listing listing = mock(Listing.class);
        when(listing.getSettlementDate()).thenReturn(LocalDate.now().minusDays(1));
        when(listing.getTicker()).thenReturn("CLM24");
        when(order.getListing()).thenReturn(listing);
        when(orderRepository.findActiveNonDone()).thenReturn(List.of(order));

        orderCleanupScheduler.cleanupExpiredOrders();

        verify(order).setStatus(OrderStatus.DECLINED);
        verify(order).setApprovedBy("SYSTEM - Settlement date expired");
        verify(orderRepository).save(order);
    }

    @Test
    void cleanupExpiredOrders_shouldNotDeclineOrderWithFutureSettlement() {
        Order order = mock(Order.class);
        Listing listing = mock(Listing.class);
        when(listing.getSettlementDate()).thenReturn(LocalDate.now().plusDays(10));
        when(order.getListing()).thenReturn(listing);
        when(orderRepository.findActiveNonDone()).thenReturn(List.of(order));

        orderCleanupScheduler.cleanupExpiredOrders();

        verify(order, never()).setStatus(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void cleanupExpiredOrders_shouldSkipOrderWithNullSettlementDate() {
        Order order = mock(Order.class);
        Listing listing = mock(Listing.class);
        when(listing.getSettlementDate()).thenReturn(null);
        when(order.getListing()).thenReturn(listing);
        when(orderRepository.findActiveNonDone()).thenReturn(List.of(order));

        orderCleanupScheduler.cleanupExpiredOrders();

        verify(order, never()).setStatus(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void cleanupExpiredOrders_shouldDoNothing_whenNoCandidates() {
        when(orderRepository.findActiveNonDone()).thenReturn(List.of());

        orderCleanupScheduler.cleanupExpiredOrders();

        verify(orderRepository, never()).save(any());
    }

    @Test
    void cleanupExpiredOrders_shouldSendOrderCancelledNotification_whenOrderExpired() {
        Order order = mock(Order.class);
        Listing listing = mock(Listing.class);
        when(listing.getSettlementDate()).thenReturn(LocalDate.now().minusDays(1));
        when(listing.getTicker()).thenReturn("CLM24");
        when(order.getListing()).thenReturn(listing);
        when(order.getUserId()).thenReturn(7L);
        when(order.getUserRole()).thenReturn("CLIENT");
        when(order.getId()).thenReturn(99L);
        when(orderRepository.findActiveNonDone()).thenReturn(List.of(order));

        orderCleanupScheduler.cleanupExpiredOrders();

        verify(notificationService).notify(
                eq(7L),
                eq("CLIENT"),
                eq(NotificationType.ORDER_CANCELLED),
                anyString(),
                anyString(),
                eq("ORDER"),
                eq(99L)
        );
    }

    @Test
    void cleanupExpiredOrders_shouldContinueEvenWhenNotificationFails() {
        Order order = mock(Order.class);
        Listing listing = mock(Listing.class);
        when(listing.getSettlementDate()).thenReturn(LocalDate.now().minusDays(1));
        when(listing.getTicker()).thenReturn("CLM24");
        when(order.getListing()).thenReturn(listing);
        when(order.getUserId()).thenReturn(7L);
        when(order.getUserRole()).thenReturn("CLIENT");
        when(order.getId()).thenReturn(99L);
        when(orderRepository.findActiveNonDone()).thenReturn(List.of(order));
        doThrow(new RuntimeException("notification failure")).when(notificationService)
                .notify(any(), any(), any(), any(), any(), any(), any());

        orderCleanupScheduler.cleanupExpiredOrders();

        verify(order).setStatus(OrderStatus.DECLINED);
        verify(orderRepository).save(order);
    }
}
