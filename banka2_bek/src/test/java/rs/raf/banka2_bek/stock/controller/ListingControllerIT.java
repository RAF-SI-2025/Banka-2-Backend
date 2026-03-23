package rs.raf.banka2_bek.stock.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import rs.raf.banka2_bek.stock.service.ListingService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ListingControllerIT {

    @Mock
    private ListingService listingService;

    @InjectMocks
    private ListingController listingController;

    @Test
    void testRefreshPricesEndpointCall() {
        // 1. Izvršavanje metode kontrolera direktno
        ResponseEntity<Void> response = listingController.refreshPrices();

        // 2. Provera da li je servis pozvan tačno jednom
        verify(listingService, times(1)).refreshPrices();

        // 3. Provera status koda (očekujemo 200 OK)
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}