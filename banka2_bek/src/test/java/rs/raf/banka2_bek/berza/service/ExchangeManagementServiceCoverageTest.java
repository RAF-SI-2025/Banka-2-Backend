package rs.raf.banka2_bek.berza.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Coverage-focused tests targeting previously missed branches in ExchangeManagementService:
 * - calculateNextOpenTime (null openTime, holiday skipping, weekend skipping, today-before-open)
 * - overnight trading hours (open > close wrap-around) — Forex-style
 * - holiday CRUD (get/set/add/remove)
 * - isExchangeOpen exception path
 * - toDto fallback catch for invalid timezone
 * - BELEX / LSE / XETRA timezones
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExchangeManagementService - Coverage")
class ExchangeManagementServiceCoverageTest {

    private static final ZoneId NY = ZoneId.of("America/New_York");
    private static final ZoneId BG = ZoneId.of("Europe/Belgrade");
    private static final ZoneId LON = ZoneId.of("Europe/London");

    @Mock
    private ExchangeRepository exchangeRepository;

    private ExchangeManagementService service;

    @BeforeEach
    void setUp() {
        service = Mockito.spy(new ExchangeManagementService(exchangeRepository));
        // Lenient by default to avoid UnnecessaryStubbing across branches
        Mockito.lenient().when(exchangeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private Exchange nyse() {
        Exchange e = Exchange.builder()
                .id(1L)
                .name("New York Stock Exchange")
                .acronym("NYSE")
                .micCode("XNYS")
                .country("US")
                .currency("USD")
                .timeZone("America/New_York")
                .openTime(LocalTime.of(9, 30))
                .closeTime(LocalTime.of(16, 0))
                .preMarketOpenTime(LocalTime.of(4, 0))
                .postMarketCloseTime(LocalTime.of(20, 0))
                .testMode(false)
                .active(true)
                .holidays(new HashSet<>())
                .build();
        return e;
    }

    private Exchange belex() {
        return Exchange.builder()
                .id(2L)
                .name("Belgrade Stock Exchange")
                .acronym("BELEX")
                .country("RS")
                .currency("RSD")
                .timeZone("Europe/Belgrade")
                .openTime(LocalTime.of(9, 30))
                .closeTime(LocalTime.of(14, 0))
                .testMode(false)
                .active(true)
                .holidays(new HashSet<>())
                .build();
    }

    private Exchange forexOvernight() {
        return Exchange.builder()
                .id(3L)
                .name("Forex")
                .acronym("FX")
                .country("XX")
                .currency("USD")
                .timeZone("UTC")
                .openTime(LocalTime.of(22, 0))
                .closeTime(LocalTime.of(6, 0))
                .testMode(false)
                .active(true)
                .holidays(new HashSet<>())
                .build();
    }

    // ─── isExchangeOpen: exception + overnight ──────────────────────────────────

    @Test
    @DisplayName("isExchangeOpen throws when exchange not found")
    void isExchangeOpen_notFound() {
        when(exchangeRepository.findByAcronym("NOPE")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.isExchangeOpen("NOPE"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Exchange not found");
    }

    @Test
    @DisplayName("isExchangeOpen: overnight wrap — before midnight after open → OPEN")
    void overnight_beforeMidnight_open() {
        Exchange fx = forexOvernight();
        when(exchangeRepository.findByAcronym("FX")).thenReturn(Optional.of(fx));
        doReturn(ZonedDateTime.of(2026, 3, 30, 23, 0, 0, 0, ZoneId.of("UTC")))
                .when(service).nowInExchangeZone(any());
        assertThat(service.isExchangeOpen("FX")).isTrue();
    }

    @Test
    @DisplayName("isExchangeOpen: overnight wrap — after midnight before close → OPEN")
    void overnight_afterMidnight_open() {
        Exchange fx = forexOvernight();
        when(exchangeRepository.findByAcronym("FX")).thenReturn(Optional.of(fx));
        doReturn(ZonedDateTime.of(2026, 3, 31, 3, 0, 0, 0, ZoneId.of("UTC")))
                .when(service).nowInExchangeZone(any());
        assertThat(service.isExchangeOpen("FX")).isTrue();
    }

    @Test
    @DisplayName("isExchangeOpen: overnight wrap — midday between close and open → CLOSED")
    void overnight_midday_closed() {
        Exchange fx = forexOvernight();
        when(exchangeRepository.findByAcronym("FX")).thenReturn(Optional.of(fx));
        doReturn(ZonedDateTime.of(2026, 3, 30, 12, 0, 0, 0, ZoneId.of("UTC")))
                .when(service).nowInExchangeZone(any());
        assertThat(service.isExchangeOpen("FX")).isFalse();
    }

    @Test
    @DisplayName("isExchangeOpen: BELEX during trading hours (Europe/Belgrade)")
    void belex_open() {
        Exchange b = belex();
        when(exchangeRepository.findByAcronym("BELEX")).thenReturn(Optional.of(b));
        // Monday
        doReturn(ZonedDateTime.of(2026, 3, 30, 11, 0, 0, 0, BG))
                .when(service).nowInExchangeZone(any());
        assertThat(service.isExchangeOpen("BELEX")).isTrue();
    }

    @Test
    @DisplayName("isExchangeOpen: BELEX after 14:00 CLOSED")
    void belex_closed_after_hours() {
        Exchange b = belex();
        when(exchangeRepository.findByAcronym("BELEX")).thenReturn(Optional.of(b));
        doReturn(ZonedDateTime.of(2026, 3, 30, 14, 0, 0, 1, BG))
                .when(service).nowInExchangeZone(any());
        assertThat(service.isExchangeOpen("BELEX")).isFalse();
    }

    // ─── Holiday CRUD ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("getHolidays returns exchange holidays")
    void getHolidays_ok() {
        Exchange n = nyse();
        n.getHolidays().add(LocalDate.of(2026, 7, 4));
        when(exchangeRepository.findByAcronym("NYSE")).thenReturn(Optional.of(n));
        Set<LocalDate> holidays = service.getHolidays("NYSE");
        assertThat(holidays).containsExactly(LocalDate.of(2026, 7, 4));
    }

    @Test
    @DisplayName("getHolidays throws when exchange not found")
    void getHolidays_notFound() {
        when(exchangeRepository.findByAcronym("NOPE")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getHolidays("NOPE"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("setHolidays replaces existing holiday set")
    void setHolidays_replaces() {
        Exchange n = nyse();
        n.getHolidays().add(LocalDate.of(2026, 1, 1));
        when(exchangeRepository.findByAcronym("NYSE")).thenReturn(Optional.of(n));

        Set<LocalDate> newSet = Set.of(LocalDate.of(2026, 12, 25), LocalDate.of(2026, 7, 4));
        service.setHolidays("NYSE", newSet);

        assertThat(n.getHolidays()).containsExactlyInAnyOrderElementsOf(newSet);
        verify(exchangeRepository).save(n);
    }

    @Test
    @DisplayName("setHolidays throws when exchange not found")
    void setHolidays_notFound() {
        when(exchangeRepository.findByAcronym("NOPE")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.setHolidays("NOPE", Set.of()))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("addHoliday adds a date and saves")
    void addHoliday_ok() {
        Exchange n = nyse();
        when(exchangeRepository.findByAcronym("NYSE")).thenReturn(Optional.of(n));

        service.addHoliday("NYSE", LocalDate.of(2026, 12, 25));

        assertThat(n.getHolidays()).contains(LocalDate.of(2026, 12, 25));
        verify(exchangeRepository).save(n);
    }

    @Test
    @DisplayName("addHoliday throws when exchange not found")
    void addHoliday_notFound() {
        when(exchangeRepository.findByAcronym("NOPE")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.addHoliday("NOPE", LocalDate.now()))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("removeHoliday removes a date and saves")
    void removeHoliday_ok() {
        Exchange n = nyse();
        n.getHolidays().add(LocalDate.of(2026, 7, 4));
        n.getHolidays().add(LocalDate.of(2026, 12, 25));
        when(exchangeRepository.findByAcronym("NYSE")).thenReturn(Optional.of(n));

        service.removeHoliday("NYSE", LocalDate.of(2026, 7, 4));

        assertThat(n.getHolidays())
                .doesNotContain(LocalDate.of(2026, 7, 4))
                .contains(LocalDate.of(2026, 12, 25));
        verify(exchangeRepository).save(n);
    }

    @Test
    @DisplayName("removeHoliday throws when exchange not found")
    void removeHoliday_notFound() {
        when(exchangeRepository.findByAcronym("NOPE")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.removeHoliday("NOPE", LocalDate.now()))
                .isInstanceOf(RuntimeException.class);
    }

    // ─── calculateNextOpenTime (covered via toDto when closed) ──────────────────

    @Test
    @DisplayName("nextOpenTime: weekday before open → today")
    void nextOpen_todayBeforeOpen() {
        Exchange n = nyse();
        when(exchangeRepository.findByActiveTrue()).thenReturn(List.of(n));
        when(exchangeRepository.findByAcronym("NYSE")).thenReturn(Optional.of(n));
        // Monday 2026-03-30 at 08:00 (before 09:30 open)
        doReturn(ZonedDateTime.of(2026, 3, 30, 8, 0, 0, 0, NY))
                .when(service).nowInExchangeZone(any());

        List<ExchangeDto> list = service.getAllExchanges();
        assertThat(list).hasSize(1);
        ExchangeDto dto = list.get(0);
        assertThat(dto.isCurrentlyOpen()).isFalse();
        assertThat(dto.getNextOpenTime()).isNotNull().contains("2026-03-30T09:30");
    }

    @Test
    @DisplayName("nextOpenTime: Friday after close → skips weekend to Monday")
    void nextOpen_skipsWeekend() {
        Exchange n = nyse();
        when(exchangeRepository.findByActiveTrue()).thenReturn(List.of(n));
        when(exchangeRepository.findByAcronym("NYSE")).thenReturn(Optional.of(n));
        // Friday 2026-03-27 at 17:00 (after close)
        doReturn(ZonedDateTime.of(2026, 3, 27, 17, 0, 0, 0, NY))
                .when(service).nowInExchangeZone(any());

        List<ExchangeDto> list = service.getAllExchanges();
        ExchangeDto dto = list.get(0);
        assertThat(dto.getNextOpenTime()).isNotNull().contains("2026-03-30T09:30");
    }

    @Test
    @DisplayName("nextOpenTime: skips holiday on next day")
    void nextOpen_skipsHoliday() {
        Exchange n = nyse();
        // Tuesday is a holiday
        n.getHolidays().add(LocalDate.of(2026, 3, 31));
        when(exchangeRepository.findByActiveTrue()).thenReturn(List.of(n));
        when(exchangeRepository.findByAcronym("NYSE")).thenReturn(Optional.of(n));
        // Monday 17:00 (after close) → next candidate Tue, but Tue is holiday → Wed
        doReturn(ZonedDateTime.of(2026, 3, 30, 17, 0, 0, 0, NY))
                .when(service).nowInExchangeZone(any());

        List<ExchangeDto> list = service.getAllExchanges();
        ExchangeDto dto = list.get(0);
        assertThat(dto.getNextOpenTime()).contains("2026-04-01T09:30");
    }

    @Test
    @DisplayName("nextOpenTime: null openTime returns null")
    void nextOpen_nullOpenTime() {
        Exchange n = nyse();
        n.setOpenTime(null);
        when(exchangeRepository.findByActiveTrue()).thenReturn(List.of(n));
        when(exchangeRepository.findByAcronym("NYSE")).thenReturn(Optional.of(n));
        // Saturday — closed, isCurrentlyOpen=false branch triggers calculateNextOpenTime
        doReturn(ZonedDateTime.of(2026, 3, 28, 12, 0, 0, 0, NY))
                .when(service).nowInExchangeZone(any());

        List<ExchangeDto> list = service.getAllExchanges();
        assertThat(list.get(0).getNextOpenTime()).isNull();
    }

    @Test
    @DisplayName("nextOpenTime: currently on holiday → goes to tomorrow branch")
    void nextOpen_fromHoliday() {
        Exchange n = nyse();
        n.getHolidays().add(LocalDate.of(2026, 3, 30)); // today is holiday
        when(exchangeRepository.findByActiveTrue()).thenReturn(List.of(n));
        when(exchangeRepository.findByAcronym("NYSE")).thenReturn(Optional.of(n));
        doReturn(ZonedDateTime.of(2026, 3, 30, 8, 0, 0, 0, NY))
                .when(service).nowInExchangeZone(any());

        List<ExchangeDto> list = service.getAllExchanges();
        ExchangeDto dto = list.get(0);
        assertThat(dto.isCurrentlyOpen()).isFalse();
        assertThat(dto.getNextOpenTime()).contains("2026-03-31T09:30");
    }

    // ─── toDto fallback on invalid timezone ─────────────────────────────────────

    @Test
    @DisplayName("toDto catches invalid timezone and falls back to local time")
    void toDto_invalidTimezone_fallback() {
        // Build an exchange with valid TZ for isExchangeOpen (spy override will be used),
        // but setting invalid TZ will cause ZoneId.of() to throw in toDto's inline block.
        Exchange bad = nyse();
        bad.setTimeZone("Not/AZone");
        bad.setTestMode(true); // so isExchangeOpen returns true without TZ lookup
        when(exchangeRepository.findByActiveTrue()).thenReturn(List.of(bad));
        when(exchangeRepository.findByAcronym("NYSE")).thenReturn(Optional.of(bad));

        List<ExchangeDto> list = service.getAllExchanges();
        // Falls through catch — currentLocalTime should be non-null (LocalTime.now())
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getCurrentLocalTime()).isNotNull();
        assertThat(list.get(0).isCurrentlyOpen()).isTrue();
    }
}
