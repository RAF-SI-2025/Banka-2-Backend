package rs.raf.banka2_bek.stock.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.model.ListingType;
import rs.raf.banka2_bek.stock.repository.ListingDailyPriceInfoRepository;
import rs.raf.banka2_bek.stock.repository.ListingRepository;
import rs.raf.banka2_bek.stock.service.implementation.ListingServiceImpl;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListingServiceImplTest {

    @Mock private ListingRepository listingRepository;
    @Mock private ListingDailyPriceInfoRepository dailyPriceRepository;

    @InjectMocks
    private ListingServiceImpl listingService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void mockAsEmployee() {
        mockWithRole("ROLE_EMPLOYEE");
    }

    private void mockAsClient() {
        mockWithRole("ROLE_CLIENT");
    }

    private void mockWithRole(String role) {
        Authentication auth = mock(Authentication.class);
        lenient().when(auth.getAuthorities()).thenAnswer(inv ->
                List.of(new SimpleGrantedAuthority(role)));
        SecurityContext ctx = mock(SecurityContext.class);
        lenient().when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
    }

    private Listing listing(String ticker, String name, ListingType type) {
        Listing l = new Listing();
        l.setId(1L);
        l.setTicker(ticker);
        l.setName(name);
        l.setListingType(type);
        l.setPrice(BigDecimal.valueOf(100));
        l.setPriceChange(BigDecimal.valueOf(2));
        return l;
    }

    @Nested
    @DisplayName("getListings - validacija tipa")
    class TypeValidation {

        @Test
        @DisplayName("baca IllegalArgumentException za nepoznat tip")
        void invalidType_throwsIllegalArgument() {
            mockAsEmployee();
            assertThatThrownBy(() -> listingService.getListings("INVALID", null, 0, 20))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("INVALID");
        }

        @Test
        @DisplayName("baca IllegalArgumentException za null tip")
        void nullType_throwsIllegalArgument() {
            mockAsEmployee();
            assertThatThrownBy(() -> listingService.getListings(null, null, 0, 20))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("prihvata mala slova u tipu")
        void lowercaseType_accepted() {
            mockAsEmployee();
            Page<Listing> page = new PageImpl<>(List.of(listing("AAPL", "Apple", ListingType.STOCK)));
            when(listingRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

            var result = listingService.getListings("stock", null, 0, 20);

            assertThat(result.getContent()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("getListings - CLIENT vs FOREX")
    class ClientForexRestriction {

        @Test
        @DisplayName("klijent sa FOREX tipom dobija IllegalStateException")
        void client_requestingForex_throwsForbidden() {
            mockAsClient();
            assertThatThrownBy(() -> listingService.getListings("FOREX", null, 0, 20))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("FOREX");
        }

        @Test
        @DisplayName("klijent moze da vidi STOCK")
        void client_requestingStock_ok() {
            mockAsClient();
            Page<Listing> page = new PageImpl<>(List.of(listing("AAPL", "Apple", ListingType.STOCK)));
            when(listingRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

            var result = listingService.getListings("STOCK", null, 0, 20);

            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("klijent moze da vidi FUTURES")
        void client_requestingFutures_ok() {
            mockAsClient();
            Page<Listing> page = new PageImpl<>(List.of(listing("CLJ26", "Crude Oil", ListingType.FUTURES)));
            when(listingRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

            var result = listingService.getListings("FUTURES", null, 0, 20);

            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("zaposleni moze da vidi FOREX")
        void employee_requestingForex_ok() {
            mockAsEmployee();
            Page<Listing> page = new PageImpl<>(List.of(listing("EUR/USD", "Euro/Dollar", ListingType.FOREX)));
            when(listingRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

            var result = listingService.getListings("FOREX", null, 0, 20);

            assertThat(result.getContent()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("getListings - paginacija i pretraga")
    class PaginationAndSearch {

        @Test
        @DisplayName("vraca paginiran rezultat")
        void returnsPaginatedResults() {
            mockAsEmployee();
            Page<Listing> page = new PageImpl<>(
                    List.of(listing("AAPL", "Apple", ListingType.STOCK)),
                    org.springframework.data.domain.PageRequest.of(0, 20),
                    1
            );
            when(listingRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

            var result = listingService.getListings("STOCK", null, 0, 20);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getTicker()).isEqualTo("AAPL");
        }

        @Test
        @DisplayName("prosledjuje search u Specification")
        void searchIsPassedToRepository() {
            mockAsEmployee();
            when(listingRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(Page.empty());

            listingService.getListings("STOCK", "AAPL", 0, 20);

            verify(listingRepository, times(1))
                    .findAll(any(Specification.class), any(Pageable.class));
        }

        @Test
        @DisplayName("mapira Listing u ListingDto sa izvedenim podacima")
        void mapsToDto_withDerivedFields() {
            mockAsEmployee();
            Listing l = listing("MSFT", "Microsoft", ListingType.STOCK);
            l.setOutstandingShares(1_000_000L);
            Page<Listing> page = new PageImpl<>(List.of(l));
            when(listingRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

            var result = listingService.getListings("STOCK", null, 0, 20);

            var dto = result.getContent().get(0);
            assertThat(dto.getTicker()).isEqualTo("MSFT");
            assertThat(dto.getMarketCap()).isNotNull();
            assertThat(dto.getChangePercent()).isNotNull();
            assertThat(dto.getMaintenanceMargin()).isNotNull();
            assertThat(dto.getInitialMarginCost()).isNotNull();
        }
    }
}
