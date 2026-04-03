package rs.raf.banka2_bek.berza.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.berza.dto.ExchangeDto;
import rs.raf.banka2_bek.berza.model.Exchange;
import rs.raf.banka2_bek.berza.repository.ExchangeRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Extended tests for ExchangeManagementService covering:
 * - getAllExchanges
 * - getByAcronym
 * - setTestMode (toggle)
 * - isExchangeOpen with holidays
 * - isAfterHours additional edge cases
 * - toDto computed fields
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExchangeManagementService - Extended")
class ExchangeManagementServiceExtendedTest {

    private static final ZoneId NY = ZoneId.of("America/New_York");

    @Mock
    private ExchangeRepository exchangeRepository;

    private ExchangeManagementService service;

    @BeforeEach
    void setUp() {
        service = Mockito.spy(new ExchangeManagementService(exchangeRepository));
    }

    private Exchange nyse() {
        return Exchange.builder()
                .id(1L)
                .name("New York Stock Exchange")
                .acronym("NYSE")
                .micCode("XNYS")
                .country("US")
                .currency("USD")
                .timeZone("America/New_York")
                .openTime(LocalTime.of(9, 30))
                .closeTime(LocalTime.of(16, 0))
                .testMode(false)
                .active(true)
                .build();
    }

    private Exchange belex() {
        return Exchange.builder()
                .id(2L)
                .name("Belgrade Stock Exchange")
                .acronym("BELEX")
                .micCode("XBEL")
                .country("RS")
                .currency("RSD")
                .timeZone("Europe/Belgrade")
                .openTime(LocalTime.of(10, 0))
                .closeTime(LocalTime.of(14, 0))
                .testMode(false)
                .active(true)
                .build();
    }

    // ─── getAllExchanges ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAllExchanges")
    class GetAllExchanges {

        @Test
        @DisplayName("returns list of active exchanges as DTOs")
        void returnsActiveDtos() {
            Exchange nyse = nyse();
            Exchange belex = belex();

            when(exchangeRepository.findByActiveTrue()).thenReturn(List.of(nyse, belex));
            when(exchangeRepository.findByAcronym("NYSE")).thenReturn(Optional.of(nyse));
            when(exchangeRepository.findByAcronym("BELEX")).thenReturn(Optional.of(belex));
            // Mock times so isExchangeOpen can work
            doReturn(ZonedDateTime.of(2026, 3, 30, 10, 0, 0, 0, NY))
                    .when(service).nowInExchangeZone(any());

            List<ExchangeDto> result = service.getAllExchanges();

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getAcronym()).isEqualTo("NYSE");
            assertThat(result.get(1).getAcronym()).isEqualTo("BELEX");
        }

        @Test
        @DisplayName("returns empty list when no active exchanges")
        void noActiveExchanges() {
            when(exchangeRepository.findByActiveTrue()).thenReturn(Collections.emptyList());

            List<ExchangeDto> result = service.getAllExchanges();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("DTO contains computed isCurrentlyOpen field")
        void dtoContainsIsCurrentlyOpen() {
            Exchange nyse = nyse();
            when(exchangeRepository.findByActiveTrue()).thenReturn(List.of(nyse));
            when(exchangeRepository.findByAcronym("NYSE")).thenReturn(Optional.of(nyse));
            // Mock as during trading hours on a weekday
            doReturn(ZonedDateTime.of(2026, 3, 30, 12, 0, 0, 0, NY))
                    .when(service).nowInExchangeZone(any());

            List<ExchangeDto> result = service.getAllExchanges();

            assertThat(result.get(0).isCurrentlyOpen()).isTrue();
            assertThat(result.get(0).getNextOpenTime()).isNull(); // null when currently open
        }

        @Test
        @DisplayName("DTO contains nextOpenTime when closed")
        void dtoContainsNextOpenTime() {
            Exchange nyse = nyse();
            when(exchangeRepository.findByActiveTrue()).thenReturn(List.of(nyse));
            when(exchangeRepository.findByAcronym("NYSE")).thenReturn(Optional.of(nyse));
            // Mock as Saturday (closed)
            doReturn(ZonedDateTime.of(2026, 3, 28, 12, 0, 0, 0, NY))
                    .when(service).nowInExchangeZone(any());

            List<ExchangeDto> result = service.getAllExchanges();

            assertThat(result.get(0).isCurrentlyOpen()).isFalse();
            assertThat(result.get(0).getNextOpenTime()).isNotNull();
        }
    }

    // ─── getByAcronym ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getByAcronym")
    class GetByAcronym {

        @Test
        @DisplayName("returns DTO for existing exchange")
        void existingExchange() {
            Exchange nyse = nyse();
            when(exchangeRepository.findByAcronym("NYSE")).thenReturn(Optional.of(nyse));
            doReturn(ZonedDateTime.of(2026, 3, 30, 12, 0, 0, 0, NY))
                    .when(service).nowInExchangeZone(any());

            ExchangeDto dto = service.getByAcronym("NYSE");

            assertThat(dto.getAcronym()).isEqualTo("NYSE");
            assertThat(dto.getName()).isEqualTo("New York Stock Exchange");
            assertThat(dto.getCountry()).isEqualTo("US");
            assertThat(dto.getCurrency()).isEqualTo("USD");
        }

        @Test
        @DisplayName("throws when exchange not found")
        void notFound() {
            when(exchangeRepository.findByAcronym("FAKE")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getByAcronym("FAKE"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Exchange not found");
        }
    }

    // ─── setTestMode ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("setTestMode")
    class SetTestMode {

        @Test
        @DisplayName("enables test mode")
        void enableTestMode() {
            Exchange nyse = nyse();
            when(exchangeRepository.findByAcronym("NYSE")).thenReturn(Optional.of(nyse));

            service.setTestMode("NYSE", true);

            assertThat(nyse.isTestMode()).isTrue();
            verify(exchangeRepository).save(nyse);
        }

        @Test
        @DisplayName("disables test mode")
        void disableTestMode() {
            Exchange nyse = nyse();
            nyse.setTestMode(true);
            when(exchangeRepository.findByAcronym("NYSE")).thenReturn(Optional.of(nyse));

            service.setTestMode("NYSE", false);

            assertThat(nyse.isTestMode()).isFalse();
            verify(exchangeRepository).save(nyse);
        }

        @Test
        @DisplayName("throws when exchange not found")
        void notFound() {
            when(exchangeRepository.findByAcronym("FAKE")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.setTestMode("FAKE", true))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    // ─── isExchangeOpen - holidays ──────────────────────────────────────────────

    @Nested
    @DisplayName("isExchangeOpen - holidays")
    class Holidays {

        @Test
        @DisplayName("exchange closed on holiday even during trading hours")
        void closedOnHoliday() {
            Exchange nyse = nyse();
            nyse.setHolidays(Set.of(LocalDate.of(2026, 7, 4))); // July 4th
            when(exchangeRepository.findByAcronym("NYSE")).thenReturn(Optional.of(nyse));
            // July 4, 2026 is a Saturday, so use a different date that's a weekday
            // Let's use a custom holiday on a Monday
            nyse.setHolidays(Set.of(LocalDate.of(2026, 3, 30)));
            doReturn(ZonedDateTime.of(2026, 3, 30, 12, 0, 0, 0, NY))
                    .when(service).nowInExchangeZone(any());

            assertThat(service.isExchangeOpen("NYSE")).isFalse();
        }

        @Test
        @DisplayName("exchange open on non-holiday weekday during hours")
        void openOnNonHoliday() {
            Exchange nyse = nyse();
            nyse.setHolidays(Set.of(LocalDate.of(2026, 12, 25))); // Christmas
            when(exchangeRepository.findByAcronym("NYSE")).thenReturn(Optional.of(nyse));
            doReturn(ZonedDateTime.of(2026, 3, 30, 12, 0, 0, 0, NY))
                    .when(service).nowInExchangeZone(any());

            assertThat(service.isExchangeOpen("NYSE")).isTrue();
        }

        @Test
        @DisplayName("test mode overrides holiday closure")
        void testModeOverridesHoliday() {
            Exchange nyse = nyse();
            nyse.setTestMode(true);
            nyse.setHolidays(Set.of(LocalDate.of(2026, 3, 30)));
            when(exchangeRepository.findByAcronym("NYSE")).thenReturn(Optional.of(nyse));

            assertThat(service.isExchangeOpen("NYSE")).isTrue();
        }
    }

    // ─── isAfterHours - extended ────────────────────────────────────────────────

    @Nested
    @DisplayName("isAfterHours - extended")
    class AfterHoursExtended {

        @Test
        @DisplayName("holiday during after-hours window returns false")
        void holidayDuringAfterHours() {
            Exchange nyse = Exchange.builder()
                    .id(1L).name("NYSE").acronym("NYSE").micCode("XNYS")
                    .country("US").currency("USD").timeZone("America/New_York")
                    .openTime(LocalTime.of(9, 30)).closeTime(LocalTime.of(16, 0))
                    .postMarketCloseTime(LocalTime.of(20, 0))
                    .testMode(false).active(true)
                    .holidays(Set.of(LocalDate.of(2026, 3, 30)))
                    .build();

            when(exchangeRepository.findByAcronym("NYSE")).thenReturn(Optional.of(nyse));
            doReturn(ZonedDateTime.of(2026, 3, 30, 18, 0, 0, 0, NY))
                    .when(service).nowInExchangeZone(any());

            assertThat(service.isAfterHours("NYSE")).isFalse();
        }

        @Test
        @DisplayName("null closeTime returns false for after-hours")
        void nullCloseTimeReturnsFalse() {
            Exchange ex = Exchange.builder()
                    .id(1L).name("Bad").acronym("BAD").micCode("XBAD")
                    .country("US").currency("USD").timeZone("America/New_York")
                    .openTime(LocalTime.of(9, 30)).closeTime(null)
                    .postMarketCloseTime(LocalTime.of(20, 0))
                    .testMode(false).active(true).build();

            when(exchangeRepository.findByAcronym("BAD")).thenReturn(Optional.of(ex));
            doReturn(ZonedDateTime.of(2026, 3, 30, 18, 0, 0, 0, NY))
                    .when(service).nowInExchangeZone(any());

            assertThat(service.isAfterHours("BAD")).isFalse();
        }
    }

    // ─── DTO mapping ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("DTO mapping")
    class DtoMapping {

        @Test
        @DisplayName("DTO contains all exchange fields")
        void allFieldsMapped() {
            Exchange nyse = Exchange.builder()
                    .id(1L).name("NYSE").acronym("NYSE").micCode("XNYS")
                    .country("US").currency("USD").timeZone("America/New_York")
                    .openTime(LocalTime.of(9, 30)).closeTime(LocalTime.of(16, 0))
                    .preMarketOpenTime(LocalTime.of(4, 0))
                    .postMarketCloseTime(LocalTime.of(20, 0))
                    .testMode(true).active(true).build();

            when(exchangeRepository.findByActiveTrue()).thenReturn(List.of(nyse));
            when(exchangeRepository.findByAcronym("NYSE")).thenReturn(Optional.of(nyse));

            List<ExchangeDto> result = service.getAllExchanges();

            ExchangeDto dto = result.get(0);
            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getName()).isEqualTo("NYSE");
            assertThat(dto.getMicCode()).isEqualTo("XNYS");
            assertThat(dto.getOpenTime()).isEqualTo("09:30");
            assertThat(dto.getCloseTime()).isEqualTo("16:00");
            assertThat(dto.getPreMarketOpenTime()).isEqualTo("04:00");
            assertThat(dto.getPostMarketCloseTime()).isEqualTo("20:00");
            assertThat(dto.isTestMode()).isTrue();
            assertThat(dto.isActive()).isTrue();
            assertThat(dto.getCurrentLocalTime()).isNotNull();
        }

        @Test
        @DisplayName("DTO handles null pre/post market times")
        void nullPrePostMarket() {
            Exchange nyse = nyse(); // no pre/post market times
            when(exchangeRepository.findByActiveTrue()).thenReturn(List.of(nyse));
            when(exchangeRepository.findByAcronym("NYSE")).thenReturn(Optional.of(nyse));
            doReturn(ZonedDateTime.of(2026, 3, 30, 12, 0, 0, 0, NY))
                    .when(service).nowInExchangeZone(any());

            List<ExchangeDto> result = service.getAllExchanges();

            ExchangeDto dto = result.get(0);
            assertThat(dto.getPreMarketOpenTime()).isNull();
            assertThat(dto.getPostMarketCloseTime()).isNull();
        }
    }
}
