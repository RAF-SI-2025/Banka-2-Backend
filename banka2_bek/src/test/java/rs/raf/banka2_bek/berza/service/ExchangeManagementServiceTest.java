package rs.raf.banka2_bek.berza.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.berza.model.Exchange;
import rs.raf.banka2_bek.berza.repository.ExchangeRepository;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExchangeManagementServiceTest {

    private static final ZoneId NY = ZoneId.of("America/New_York");

    @Mock
    private ExchangeRepository exchangeRepository;

    private ExchangeManagementService service;

    @BeforeEach
    void setUp() {
        service = Mockito.spy(new ExchangeManagementService(exchangeRepository));
    }

    private Exchange nyseNormalHours() {
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

    @Test
    void isExchangeOpen_whenExchangeMissing_throws() {
        when(exchangeRepository.findByAcronym("UNKNOWN")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.isExchangeOpen("UNKNOWN"));

        assertTrue(ex.getMessage().contains("Exchange not found"));
    }

    @Test
    void isExchangeOpen_whenTestMode_returnsTrueRegardlessOfTime() {
        Exchange ex = nyseNormalHours();
        ex.setTestMode(true);
        when(exchangeRepository.findByAcronym("NYSE")).thenReturn(Optional.of(ex));

        assertTrue(service.isExchangeOpen("NYSE"));
    }

    @Test
    void isExchangeOpen_onSaturday_returnsFalse() {
        Exchange ex = nyseNormalHours();
        when(exchangeRepository.findByAcronym("NYSE")).thenReturn(Optional.of(ex));
        doReturn(ZonedDateTime.of(2026, 3, 28, 12, 0, 0, 0, NY))
                .when(service).nowInExchangeZone(any());

        assertFalse(service.isExchangeOpen("NYSE"));
    }

    @Test
    void isExchangeOpen_onSunday_returnsFalse() {
        Exchange ex = nyseNormalHours();
        when(exchangeRepository.findByAcronym("NYSE")).thenReturn(Optional.of(ex));
        doReturn(ZonedDateTime.of(2026, 3, 29, 12, 0, 0, 0, NY))
                .when(service).nowInExchangeZone(any());

        assertFalse(service.isExchangeOpen("NYSE"));
    }

    @Test
    void isExchangeOpen_weekdayWithinOpenClose_returnsTrue() {
        Exchange ex = nyseNormalHours();
        when(exchangeRepository.findByAcronym("NYSE")).thenReturn(Optional.of(ex));
        doReturn(ZonedDateTime.of(2026, 3, 30, 10, 0, 0, 0, NY))
                .when(service).nowInExchangeZone(any());

        assertTrue(service.isExchangeOpen("NYSE"));
    }

    @Test
    void isExchangeOpen_weekdayAtOpenBoundary_returnsTrue() {
        Exchange ex = nyseNormalHours();
        when(exchangeRepository.findByAcronym("NYSE")).thenReturn(Optional.of(ex));
        doReturn(ZonedDateTime.of(2026, 3, 30, 9, 30, 0, 0, NY))
                .when(service).nowInExchangeZone(any());

        assertTrue(service.isExchangeOpen("NYSE"));
    }

    @Test
    void isExchangeOpen_weekdayAtCloseBoundary_returnsTrue() {
        Exchange ex = nyseNormalHours();
        when(exchangeRepository.findByAcronym("NYSE")).thenReturn(Optional.of(ex));
        doReturn(ZonedDateTime.of(2026, 3, 30, 16, 0, 0, 0, NY))
                .when(service).nowInExchangeZone(any());

        assertTrue(service.isExchangeOpen("NYSE"));
    }

    @Test
    void isExchangeOpen_weekdayBeforeOpen_returnsFalse() {
        Exchange ex = nyseNormalHours();
        when(exchangeRepository.findByAcronym("NYSE")).thenReturn(Optional.of(ex));
        doReturn(ZonedDateTime.of(2026, 3, 30, 9, 29, 59, 0, NY))
                .when(service).nowInExchangeZone(any());

        assertFalse(service.isExchangeOpen("NYSE"));
    }

    @Test
    void isExchangeOpen_weekdayAfterClose_returnsFalse() {
        Exchange ex = nyseNormalHours();
        when(exchangeRepository.findByAcronym("NYSE")).thenReturn(Optional.of(ex));
        doReturn(ZonedDateTime.of(2026, 3, 30, 16, 0, 0, 1, NY))
                .when(service).nowInExchangeZone(any());

        assertFalse(service.isExchangeOpen("NYSE"));
    }

    @Test
    void isExchangeOpen_overnightSession_midnight_returnsTrue() {
        Exchange ex = Exchange.builder()
                .id(2L)
                .name("Overnight")
                .acronym("ON")
                .micCode("XON")
                .country("US")
                .currency("USD")
                .timeZone("America/New_York")
                .openTime(LocalTime.of(22, 0))
                .closeTime(LocalTime.of(6, 0))
                .testMode(false)
                .active(true)
                .build();
        when(exchangeRepository.findByAcronym("ON")).thenReturn(Optional.of(ex));
        doReturn(ZonedDateTime.of(2026, 3, 30, 23, 0, 0, 0, NY))
                .when(service).nowInExchangeZone(any());

        assertTrue(service.isExchangeOpen("ON"));
    }

    @Test
    void isExchangeOpen_overnightSession_earlyMorning_returnsTrue() {
        Exchange ex = Exchange.builder()
                .id(2L)
                .name("Overnight")
                .acronym("ON")
                .micCode("XON")
                .country("US")
                .currency("USD")
                .timeZone("America/New_York")
                .openTime(LocalTime.of(22, 0))
                .closeTime(LocalTime.of(6, 0))
                .testMode(false)
                .active(true)
                .build();
        when(exchangeRepository.findByAcronym("ON")).thenReturn(Optional.of(ex));
        doReturn(ZonedDateTime.of(2026, 3, 30, 5, 30, 0, 0, NY))
                .when(service).nowInExchangeZone(any());

        assertTrue(service.isExchangeOpen("ON"));
    }

    @Test
    void isExchangeOpen_overnightSession_midday_returnsFalse() {
        Exchange ex = Exchange.builder()
                .id(2L)
                .name("Overnight")
                .acronym("ON")
                .micCode("XON")
                .country("US")
                .currency("USD")
                .timeZone("America/New_York")
                .openTime(LocalTime.of(22, 0))
                .closeTime(LocalTime.of(6, 0))
                .testMode(false)
                .active(true)
                .build();
        when(exchangeRepository.findByAcronym("ON")).thenReturn(Optional.of(ex));
        doReturn(ZonedDateTime.of(2026, 3, 30, 12, 0, 0, 0, NY))
                .when(service).nowInExchangeZone(any());

        assertFalse(service.isExchangeOpen("ON"));
    }

    private Exchange nyseWithPostMarket() {
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
                .postMarketCloseTime(LocalTime.of(20, 0))
                .testMode(false)
                .active(true)
                .build();
    }

    @Test
    void isAfterHours_whenExchangeMissing_throws() {
        when(exchangeRepository.findByAcronym("UNKNOWN")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.isAfterHours("UNKNOWN"));

        assertTrue(ex.getMessage().contains("Exchange not found"));
    }

    @Test
    void isAfterHours_whenNoPostMarketCloseTime_returnsFalse() {
        Exchange ex = nyseNormalHours();
        when(exchangeRepository.findByAcronym("NYSE")).thenReturn(Optional.of(ex));

        assertFalse(service.isAfterHours("NYSE"));
    }

    @Test
    void isAfterHours_whenTestMode_stillTrueInAfterHoursWindow() {
        Exchange ex = nyseWithPostMarket();
        ex.setTestMode(true);
        when(exchangeRepository.findByAcronym("NYSE")).thenReturn(Optional.of(ex));
        doReturn(ZonedDateTime.of(2026, 3, 30, 18, 0, 0, 0, NY))
                .when(service).nowInExchangeZone(any());

        assertTrue(service.isAfterHours("NYSE"));
    }

    @Test
    void isAfterHours_whenTestMode_stillFalseOutsideAfterHoursWindow() {
        Exchange ex = nyseWithPostMarket();
        ex.setTestMode(true);
        when(exchangeRepository.findByAcronym("NYSE")).thenReturn(Optional.of(ex));
        doReturn(ZonedDateTime.of(2026, 3, 30, 10, 0, 0, 0, NY))
                .when(service).nowInExchangeZone(any());

        assertFalse(service.isAfterHours("NYSE"));
    }

    @Test
    void isAfterHours_weekend_returnsFalseEvenIfTimeInWindow() {
        Exchange ex = nyseWithPostMarket();
        when(exchangeRepository.findByAcronym("NYSE")).thenReturn(Optional.of(ex));
        doReturn(ZonedDateTime.of(2026, 3, 28, 18, 0, 0, 0, NY))
                .when(service).nowInExchangeZone(any());

        assertFalse(service.isAfterHours("NYSE"));
    }

    @Test
    void isAfterHours_duringRegularSession_returnsFalse() {
        Exchange ex = nyseWithPostMarket();
        when(exchangeRepository.findByAcronym("NYSE")).thenReturn(Optional.of(ex));
        doReturn(ZonedDateTime.of(2026, 3, 30, 10, 0, 0, 0, NY))
                .when(service).nowInExchangeZone(any());

        assertFalse(service.isAfterHours("NYSE"));
    }

    @Test
    void isAfterHours_atRegularClose_returnsFalse() {
        Exchange ex = nyseWithPostMarket();
        when(exchangeRepository.findByAcronym("NYSE")).thenReturn(Optional.of(ex));
        doReturn(ZonedDateTime.of(2026, 3, 30, 16, 0, 0, 0, NY))
                .when(service).nowInExchangeZone(any());

        assertFalse(service.isAfterHours("NYSE"));
    }

    @Test
    void isAfterHours_afterCloseBeforePostEnd_returnsTrue() {
        Exchange ex = nyseWithPostMarket();
        when(exchangeRepository.findByAcronym("NYSE")).thenReturn(Optional.of(ex));
        doReturn(ZonedDateTime.of(2026, 3, 30, 18, 0, 0, 0, NY))
                .when(service).nowInExchangeZone(any());

        assertTrue(service.isAfterHours("NYSE"));
    }

    @Test
    void isAfterHours_immediatelyAfterClose_returnsTrue() {
        Exchange ex = nyseWithPostMarket();
        when(exchangeRepository.findByAcronym("NYSE")).thenReturn(Optional.of(ex));
        doReturn(ZonedDateTime.of(2026, 3, 30, 16, 0, 0, 1, NY))
                .when(service).nowInExchangeZone(any());

        assertTrue(service.isAfterHours("NYSE"));
    }

    @Test
    void isAfterHours_atPostMarketClose_returnsFalse() {
        Exchange ex = nyseWithPostMarket();
        when(exchangeRepository.findByAcronym("NYSE")).thenReturn(Optional.of(ex));
        doReturn(ZonedDateTime.of(2026, 3, 30, 20, 0, 0, 0, NY))
                .when(service).nowInExchangeZone(any());

        assertFalse(service.isAfterHours("NYSE"));
    }

    @Test
    void isAfterHours_afterPostMarketClose_returnsFalse() {
        Exchange ex = nyseWithPostMarket();
        when(exchangeRepository.findByAcronym("NYSE")).thenReturn(Optional.of(ex));
        doReturn(ZonedDateTime.of(2026, 3, 30, 20, 0, 0, 1, NY))
                .when(service).nowInExchangeZone(any());

        assertFalse(service.isAfterHours("NYSE"));
    }

    @Test
    void isAfterHours_whenPostMarketNotAfterClose_returnsFalse() {
        Exchange ex = Exchange.builder()
                .id(3L)
                .name("Bad data")
                .acronym("BAD")
                .micCode("XBAD")
                .country("US")
                .currency("USD")
                .timeZone("America/New_York")
                .openTime(LocalTime.of(9, 30))
                .closeTime(LocalTime.of(16, 0))
                .postMarketCloseTime(LocalTime.of(15, 0))
                .testMode(false)
                .active(true)
                .build();
        when(exchangeRepository.findByAcronym("BAD")).thenReturn(Optional.of(ex));
        doReturn(ZonedDateTime.of(2026, 3, 30, 15, 30, 0, 0, NY))
                .when(service).nowInExchangeZone(any());

        assertFalse(service.isAfterHours("BAD"));
    }

    // -------------------------------------------------------------------
    // Opciono.3 — isWithinPostCloseWindow (spec Celina 3 §404 4h prozor)
    // -------------------------------------------------------------------

    @Test
    void isWithinPostCloseWindow_4hWindow_atOnePastClose_returnsTrue() {
        Exchange ex = nyseNormalHours(); // close 16:00, bez postMarketCloseTime
        when(exchangeRepository.findByAcronym("NYSE")).thenReturn(Optional.of(ex));
        doReturn(ZonedDateTime.of(2026, 3, 30, 17, 0, 0, 0, NY))
                .when(service).nowInExchangeZone(any());

        assertTrue(service.isWithinPostCloseWindow("NYSE", 4));
    }

    @Test
    void isWithinPostCloseWindow_4hWindow_atFourHoursAfterClose_returnsFalse() {
        Exchange ex = nyseNormalHours(); // close 16:00
        when(exchangeRepository.findByAcronym("NYSE")).thenReturn(Optional.of(ex));
        doReturn(ZonedDateTime.of(2026, 3, 30, 20, 0, 0, 0, NY))
                .when(service).nowInExchangeZone(any());

        assertFalse(service.isWithinPostCloseWindow("NYSE", 4));
    }

    @Test
    void isWithinPostCloseWindow_4hWindow_atRegularSession_returnsFalse() {
        Exchange ex = nyseNormalHours();
        when(exchangeRepository.findByAcronym("NYSE")).thenReturn(Optional.of(ex));
        doReturn(ZonedDateTime.of(2026, 3, 30, 12, 0, 0, 0, NY))
                .when(service).nowInExchangeZone(any());

        assertFalse(service.isWithinPostCloseWindow("NYSE", 4));
    }

    @Test
    void isWithinPostCloseWindow_4hWindow_onWeekend_returnsFalse() {
        Exchange ex = nyseNormalHours();
        when(exchangeRepository.findByAcronym("NYSE")).thenReturn(Optional.of(ex));
        doReturn(ZonedDateTime.of(2026, 3, 28, 18, 0, 0, 0, NY)) // Saturday
                .when(service).nowInExchangeZone(any());

        assertFalse(service.isWithinPostCloseWindow("NYSE", 4));
    }

    @Test
    void isWithinPostCloseWindow_zeroHours_alwaysFalse() {
        // Bez stub-a — kratko-spojeni return za hours <= 0 ne dotice repo
        assertFalse(service.isWithinPostCloseWindow("NYSE", 0));
        assertFalse(service.isWithinPostCloseWindow("NYSE", -1));
    }

    @Test
    void isWithinPostCloseWindow_2hWindow_atTwoPastClose_returnsFalse() {
        Exchange ex = nyseNormalHours(); // close 16:00
        when(exchangeRepository.findByAcronym("NYSE")).thenReturn(Optional.of(ex));
        doReturn(ZonedDateTime.of(2026, 3, 30, 18, 0, 0, 0, NY))
                .when(service).nowInExchangeZone(any());

        // 2h prozor (16:00-18:00) — u 18:00 je vec van prozora
        assertFalse(service.isWithinPostCloseWindow("NYSE", 2));
    }

    @Test
    void isWithinPostCloseWindow_2hWindow_atOneHourPastClose_returnsTrue() {
        Exchange ex = nyseNormalHours(); // close 16:00
        when(exchangeRepository.findByAcronym("NYSE")).thenReturn(Optional.of(ex));
        doReturn(ZonedDateTime.of(2026, 3, 30, 17, 0, 0, 0, NY))
                .when(service).nowInExchangeZone(any());

        assertTrue(service.isWithinPostCloseWindow("NYSE", 2));
    }
}
