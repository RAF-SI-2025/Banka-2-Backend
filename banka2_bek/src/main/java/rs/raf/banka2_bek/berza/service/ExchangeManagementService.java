package rs.raf.banka2_bek.berza.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.berza.dto.ExchangeDto;
import rs.raf.banka2_bek.berza.model.Exchange;
import rs.raf.banka2_bek.berza.repository.ExchangeRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * Servis za upravljanje berzama i proveru radnog vremena.
 *
 * Specifikacija: Celina 3 - Berza
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeManagementService {

    private final ExchangeRepository exchangeRepository;

    /**
     * Proverava da li je berza trenutno otvorena.
     * Uzima u obzir vikende, praznike i radno vreme u lokalnoj vremenskoj zoni berze.
     */
    public boolean isExchangeOpen(String acronym) {
        Exchange exchange = exchangeRepository.findByAcronym(acronym)
                .orElseThrow(() -> new RuntimeException("Exchange not found: " + acronym));
        if (exchange.isTestMode()) {
            return true;
        }
        ZonedDateTime nowZ = nowInExchangeZone(exchange);
        if (isNonTradingDay(nowZ, exchange)) {
            return false;
        }
        LocalTime now = nowZ.toLocalTime();
        LocalTime open = exchange.getOpenTime();
        LocalTime close = exchange.getCloseTime();
        return isWithinTradingHours(now, open, close);
    }

    /**
     * Izdvojeno radi unit testova (mock trenutnog vremena u zoni berze).
     */
    ZonedDateTime nowInExchangeZone(Exchange exchange) {
        return ZonedDateTime.now(ZoneId.of(exchange.getTimeZone()));
    }

    /**
     * Proverava da li je dati datum vikend ili praznik za berzu.
     */
    private boolean isNonTradingDay(ZonedDateTime dateTime, Exchange exchange) {
        DayOfWeek dow = dateTime.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return true;
        }
        return exchange.getHolidays() != null && exchange.getHolidays().contains(dateTime.toLocalDate());
    }

    private static boolean isWithinTradingHours(LocalTime now, LocalTime open, LocalTime close) {
        if (!open.isAfter(close)) {
            return !now.isBefore(open) && !now.isAfter(close);
        }
        return !now.isBefore(open) || !now.isAfter(close);
    }

    /**
     * Vraca listu svih aktivnih berzi sa computed poljima.
     */
    public List<ExchangeDto> getAllExchanges() {
        return exchangeRepository.findByActiveTrue().stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Vraca detalje jedne berze po skracenici.
     */
    public ExchangeDto getByAcronym(String acronym) {
        Exchange exchange = exchangeRepository.findByAcronym(acronym)
                .orElseThrow(() -> new RuntimeException("Exchange not found: " + acronym));
        return toDto(exchange);
    }

    /**
     * Ukljucuje/iskljucuje test mode za berzu.
     */
    @Transactional
    public void setTestMode(String acronym, boolean enabled) {
        Exchange exchange = exchangeRepository.findByAcronym(acronym)
                .orElseThrow(() -> new RuntimeException("Exchange not found: " + acronym));
        exchange.setTestMode(enabled);
        exchangeRepository.save(exchange);
        log.info("Test mode for exchange {} set to {}", acronym, enabled);
    }

    /**
     * Proverava da li je berza u after-hours periodu (posle regularnog closeTime, do postMarketCloseTime).
     * Bez postMarketCloseTime nema after-hours prozora. Vikend i praznici: uvek false.
     * Test mode ne menja after-hours proveru (može biti i true i false po satu).
     *
     * Spec Celina 3 §404 trazi 4h prozor; za nase 6 berzi seedovani
     * postMarketCloseTime je vec close+4h (NYSE 16:00→20:00, LSE 16:30→20:30,
     * BELEX 15:00→19:00, ...). Za striktnu spec-only proveru bez oslonca na
     * postMarketCloseTime, koristi {@link #isWithinPostCloseWindow(String, int)}.
     */
    public boolean isAfterHours(String acronym) {
        Exchange exchange = exchangeRepository.findByAcronym(acronym)
                .orElseThrow(() -> new RuntimeException("Exchange not found: " + acronym));
        LocalTime postEnd = exchange.getPostMarketCloseTime();
        if (postEnd == null) {
            return false;
        }
        ZonedDateTime nowZ = nowInExchangeZone(exchange);
        if (isNonTradingDay(nowZ, exchange)) {
            return false;
        }
        LocalTime now = nowZ.toLocalTime();
        LocalTime close = exchange.getCloseTime();
        if (close == null || !postEnd.isAfter(close)) {
            return false;
        }
        return now.isAfter(close) && now.isBefore(postEnd);
    }

    /**
     * Opciono.3 — generalizovana spec-konformna provera: trenutno vreme
     * u prozoru {@code (closeTime, closeTime + hours)} bez oslonca na
     * exchange.postMarketCloseTime. Spec Celina 3 §404 trazi 4h prozor.
     *
     * Vikend/praznici: uvek false. Test mode ne menja.
     *
     * @param acronym  exchange skracenica (NYSE, NASDAQ, ...)
     * @param hours    duzina prozora u satima (>= 1)
     */
    public boolean isWithinPostCloseWindow(String acronym, int hours) {
        if (hours <= 0) return false;
        Exchange exchange = exchangeRepository.findByAcronym(acronym)
                .orElseThrow(() -> new RuntimeException("Exchange not found: " + acronym));
        LocalTime close = exchange.getCloseTime();
        if (close == null) return false;

        ZonedDateTime nowZ = nowInExchangeZone(exchange);
        if (isNonTradingDay(nowZ, exchange)) return false;

        LocalTime now = nowZ.toLocalTime();
        LocalTime windowEnd = close.plusHours(hours);

        // Cross-midnight handling: ako close+hours wrap-uje preko ponoci
        // (sto za nase 6 berzi nije slucaj), tretiramo kao ne-after-hours
        // posle ponoci radi pojednostavljenja.
        if (windowEnd.isBefore(close)) {
            return now.isAfter(close);
        }

        return now.isAfter(close) && now.isBefore(windowEnd);
    }

    /**
     * Racuna kada se berza sledeci put otvara (ISO 8601 string).
     * Uzima u obzir vikende i praznike — preskace neradne dane dok ne nadje prvi radni dan.
     */
    private String calculateNextOpenTime(Exchange exchange) {
        LocalTime openTime = exchange.getOpenTime();
        if (openTime == null) {
            return null;
        }

        ZoneId zone = ZoneId.of(exchange.getTimeZone());
        ZonedDateTime nowZ = nowInExchangeZone(exchange);
        LocalTime now = nowZ.toLocalTime();

        LocalDate candidate;
        if (!isNonTradingDay(nowZ, exchange) && now.isBefore(openTime)) {
            // Radni dan pre otvaranja — berza se otvara danas
            candidate = nowZ.toLocalDate();
        } else {
            // Kreni od sutra i nadji prvi radni dan koji nije praznik
            candidate = nowZ.toLocalDate().plusDays(1);
        }

        // Preskoci vikende i praznike (max 365 dana kao sigurnosni limit)
        int safetyCounter = 0;
        while (safetyCounter < 365) {
            DayOfWeek dow = candidate.getDayOfWeek();
            boolean isWeekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
            boolean isHoliday = exchange.getHolidays() != null && exchange.getHolidays().contains(candidate);
            if (!isWeekend && !isHoliday) {
                break;
            }
            candidate = candidate.plusDays(1);
            safetyCounter++;
        }

        ZonedDateTime nextOpen = candidate.atTime(openTime).atZone(zone);
        return nextOpen.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    /**
     * Vraca praznike za berzu po skracenici.
     */
    public Set<LocalDate> getHolidays(String acronym) {
        Exchange exchange = exchangeRepository.findByAcronym(acronym)
                .orElseThrow(() -> new RuntimeException("Exchange not found: " + acronym));
        return exchange.getHolidays();
    }

    /**
     * Postavlja praznike za berzu (zamenjuje postojece).
     */
    @Transactional
    public void setHolidays(String acronym, Set<LocalDate> holidays) {
        Exchange exchange = exchangeRepository.findByAcronym(acronym)
                .orElseThrow(() -> new RuntimeException("Exchange not found: " + acronym));
        exchange.getHolidays().clear();
        exchange.getHolidays().addAll(holidays);
        exchangeRepository.save(exchange);
        log.info("Set {} holidays for exchange {}", holidays.size(), acronym);
    }

    /**
     * Dodaje praznik za berzu.
     */
    @Transactional
    public void addHoliday(String acronym, LocalDate date) {
        Exchange exchange = exchangeRepository.findByAcronym(acronym)
                .orElseThrow(() -> new RuntimeException("Exchange not found: " + acronym));
        exchange.getHolidays().add(date);
        exchangeRepository.save(exchange);
        log.info("Added holiday {} for exchange {}", date, acronym);
    }

    /**
     * Uklanja praznik za berzu.
     */
    @Transactional
    public void removeHoliday(String acronym, LocalDate date) {
        Exchange exchange = exchangeRepository.findByAcronym(acronym)
                .orElseThrow(() -> new RuntimeException("Exchange not found: " + acronym));
        exchange.getHolidays().remove(date);
        exchangeRepository.save(exchange);
        log.info("Removed holiday {} for exchange {}", date, acronym);
    }

    // ── Helper metode ───────────────────────────────────────────────────────────

    private ExchangeDto toDto(Exchange exchange) {
        boolean open = isExchangeOpen(exchange.getAcronym());
        String currentLocalTime;
        try {
            currentLocalTime = ZonedDateTime.now(ZoneId.of(exchange.getTimeZone()))
                    .toLocalTime().toString();
        } catch (Exception e) {
            currentLocalTime = LocalTime.now().toString();
        }

        return ExchangeDto.builder()
                .id(exchange.getId())
                .name(exchange.getName())
                .acronym(exchange.getAcronym())
                .micCode(exchange.getMicCode())
                .country(exchange.getCountry())
                .currency(exchange.getCurrency())
                .timeZone(exchange.getTimeZone())
                .openTime(exchange.getOpenTime() != null ? exchange.getOpenTime().toString() : null)
                .closeTime(exchange.getCloseTime() != null ? exchange.getCloseTime().toString() : null)
                .preMarketOpenTime(exchange.getPreMarketOpenTime() != null ? exchange.getPreMarketOpenTime().toString() : null)
                .postMarketCloseTime(exchange.getPostMarketCloseTime() != null ? exchange.getPostMarketCloseTime().toString() : null)
                .testMode(exchange.isTestMode())
                .active(exchange.isActive())
                .isCurrentlyOpen(open)
                .currentLocalTime(currentLocalTime)
                .nextOpenTime(open ? null : calculateNextOpenTime(exchange))
                .holidays(exchange.getHolidays())
                .build();
    }
}
