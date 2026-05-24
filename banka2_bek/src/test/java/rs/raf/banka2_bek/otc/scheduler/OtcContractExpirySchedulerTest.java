package rs.raf.banka2_bek.otc.scheduler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.notification.model.NotificationType;
import rs.raf.banka2_bek.notification.service.NotificationService;
import rs.raf.banka2_bek.otc.model.OtcContract;
import rs.raf.banka2_bek.otc.repository.OtcContractRepository;
import rs.raf.banka2_bek.otc.service.OtcService;
import rs.raf.banka2_bek.stock.model.Listing;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtcContractExpirySchedulerTest {

    @Mock private OtcService otcService;
    @Mock private OtcContractRepository otcContractRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private OtcContractExpiryScheduler scheduler;

    // -----------------------------------------------------------------------
    // expireContracts
    // -----------------------------------------------------------------------

    @Test
    void expireContracts_delegatesToOtcService() {
        when(otcService.expireSettledContracts()).thenReturn(0);

        scheduler.expireContracts();

        verify(otcService).expireSettledContracts();
    }

    @Test
    void expireContracts_logsWhenContractsExpired() {
        when(otcService.expireSettledContracts()).thenReturn(3);

        scheduler.expireContracts();

        verify(otcService).expireSettledContracts();
    }

    // -----------------------------------------------------------------------
    // notifyExpiringContracts
    // -----------------------------------------------------------------------

    @Test
    void notifyExpiringContracts_sendsNotificationToBothParties() {
        Listing listing = mock(Listing.class);
        when(listing.getTicker()).thenReturn("AAPL");

        OtcContract contract = mock(OtcContract.class);
        when(contract.getId()).thenReturn(1L);
        when(contract.getBuyerId()).thenReturn(10L);
        when(contract.getBuyerRole()).thenReturn("CLIENT");
        when(contract.getSellerId()).thenReturn(20L);
        when(contract.getSellerRole()).thenReturn("EMPLOYEE");
        when(contract.getListing()).thenReturn(listing);
        when(contract.getSettlementDate()).thenReturn(LocalDate.now().plusDays(3));

        when(otcContractRepository.findActiveExpiringOn(any(LocalDate.class)))
                .thenReturn(List.of(contract));

        scheduler.notifyExpiringContracts();

        verify(notificationService).notify(
                eq(10L), eq("CLIENT"),
                eq(NotificationType.OTC_CONTRACT_EXPIRING),
                anyString(), anyString(), eq("OTC_CONTRACT"), eq(1L)
        );
        verify(notificationService).notify(
                eq(20L), eq("EMPLOYEE"),
                eq(NotificationType.OTC_CONTRACT_EXPIRING),
                anyString(), anyString(), eq("OTC_CONTRACT"), eq(1L)
        );
    }

    @Test
    void notifyExpiringContracts_doesNothingWhenNoContractsExpiring() {
        when(otcContractRepository.findActiveExpiringOn(any(LocalDate.class)))
                .thenReturn(List.of());

        scheduler.notifyExpiringContracts();

        verify(notificationService, never()).notify(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void notifyExpiringContracts_continuesIfBuyerNotificationFails() {
        Listing listing = mock(Listing.class);
        when(listing.getTicker()).thenReturn("MSFT");

        OtcContract contract = mock(OtcContract.class);
        when(contract.getId()).thenReturn(5L);
        when(contract.getBuyerId()).thenReturn(10L);
        when(contract.getBuyerRole()).thenReturn("CLIENT");
        when(contract.getSellerId()).thenReturn(20L);
        when(contract.getSellerRole()).thenReturn("EMPLOYEE");
        when(contract.getListing()).thenReturn(listing);
        when(contract.getSettlementDate()).thenReturn(LocalDate.now().plusDays(3));

        when(otcContractRepository.findActiveExpiringOn(any())).thenReturn(List.of(contract));
        doThrow(new RuntimeException("bus down"))
                .when(notificationService).notify(eq(10L), any(), any(), any(), any(), any(), any());

        scheduler.notifyExpiringContracts();

        verify(notificationService).notify(
                eq(20L), eq("EMPLOYEE"),
                eq(NotificationType.OTC_CONTRACT_EXPIRING),
                anyString(), anyString(), eq("OTC_CONTRACT"), eq(5L)
        );
    }

    @Test
    void notifyExpiringContracts_queriesCorrectDate() {
        when(otcContractRepository.findActiveExpiringOn(any())).thenReturn(List.of());

        scheduler.notifyExpiringContracts();

        verify(otcContractRepository).findActiveExpiringOn(LocalDate.now().plusDays(3));
    }
}
