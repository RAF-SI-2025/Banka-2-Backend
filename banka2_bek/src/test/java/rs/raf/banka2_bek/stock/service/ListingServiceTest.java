package rs.raf.banka2_bek.stock.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.repository.ListingRepository;
import rs.raf.banka2_bek.stock.service.implementation.ListingServiceImpl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class ListingServiceTest {

    @Mock
    private ListingRepository listingRepository;

    @InjectMocks
    private ListingServiceImpl listingService;

    @Test
    void testRefreshPrices() {
        // 1. Priprema podataka
        Listing mockListing = new Listing();
        mockListing.setPrice(BigDecimal.valueOf(100.00));
        mockListing.setVolume(1000L);
        mockListing.setLastRefresh(LocalDateTime.now().minusHours(1));

        when(listingRepository.findAll()).thenReturn(List.of(mockListing));

        // 2. Izvršavanje tvoje metode
        listingService.refreshPrices();

        // 3. Provera (Asserti)
        assertNotEquals(BigDecimal.valueOf(100.00), mockListing.getPrice());
        assertNotNull(mockListing.getAsk());
        assertNotNull(mockListing.getBid());
        assertTrue(mockListing.getLastRefresh().isAfter(LocalDateTime.now().minusMinutes(1)));

        verify(listingRepository, times(1)).saveAll(any());
    }
    @Test
    void testScheduledRefreshCallsBusinessLogic() {
        // Pravimo spy nad već injektovanim servisom
        ListingServiceImpl serviceSpy = spy(listingService);

        // Pozivamo scheduled metodu na spy objektu
        serviceSpy.scheduledRefresh();

        // Verifikujemo da je unutar scheduled metode pozvana biznis logika
        verify(serviceSpy, times(1)).refreshPrices();
    }
}
