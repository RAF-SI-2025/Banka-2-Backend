package rs.raf.banka2_bek.berza.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.berza.model.Exchange;
import rs.raf.banka2_bek.berza.repository.ExchangeRepository;

import java.time.LocalTime;
import java.util.List;

/**
 * Seed komponenta koja popunjava tabelu berzi pri pokretanju aplikacije.
 * Pokrece se samo ako je tabela prazna (count == 0).
 * Dodaje 6 berzi: NYSE, NASDAQ, CME, LSE, XETRA, BELEX.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExchangeSeedData implements ApplicationRunner {

    private final ExchangeRepository exchangeRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (exchangeRepository.count() > 0) {
            log.info("Exchange seed data already exists, skipping.");
            return;
        }

        List<Exchange> exchanges = List.of(
                Exchange.builder()
                        .name("New York Stock Exchange").acronym("NYSE").micCode("XNYS")
                        .country("US").currency("USD").timeZone("America/New_York")
                        .openTime(LocalTime.of(9, 30)).closeTime(LocalTime.of(16, 0))
                        .preMarketOpenTime(LocalTime.of(4, 0)).postMarketCloseTime(LocalTime.of(20, 0))
                        .testMode(true)
                        .build(),
                Exchange.builder()
                        .name("NASDAQ").acronym("NASDAQ").micCode("XNAS")
                        .country("US").currency("USD").timeZone("America/New_York")
                        .openTime(LocalTime.of(9, 30)).closeTime(LocalTime.of(16, 0))
                        .preMarketOpenTime(LocalTime.of(4, 0)).postMarketCloseTime(LocalTime.of(20, 0))
                        .testMode(true)
                        .build(),
                Exchange.builder()
                        .name("Chicago Mercantile Exchange").acronym("CME").micCode("XCME")
                        .country("US").currency("USD").timeZone("America/Chicago")
                        .openTime(LocalTime.of(8, 30)).closeTime(LocalTime.of(15, 0))
                        .postMarketCloseTime(LocalTime.of(19, 0))
                        .testMode(true)
                        .build(),
                Exchange.builder()
                        .name("London Stock Exchange").acronym("LSE").micCode("XLON")
                        .country("GB").currency("GBP").timeZone("Europe/London")
                        .openTime(LocalTime.of(8, 0)).closeTime(LocalTime.of(16, 30))
                        // Opciono.3 — close+4h = 20:30, spec Celina 3 §404
                        .postMarketCloseTime(LocalTime.of(20, 30))
                        .testMode(true)
                        .build(),
                Exchange.builder()
                        .name("Deutsche Börse XETRA").acronym("XETRA").micCode("XETR")
                        .country("DE").currency("EUR").timeZone("Europe/Berlin")
                        .openTime(LocalTime.of(9, 0)).closeTime(LocalTime.of(17, 30))
                        .postMarketCloseTime(LocalTime.of(21, 30))
                        .testMode(true)
                        .build(),
                Exchange.builder()
                        .name("Belgrade Stock Exchange").acronym("BELEX").micCode("XBEL")
                        .country("RS").currency("RSD").timeZone("Europe/Belgrade")
                        .openTime(LocalTime.of(9, 0)).closeTime(LocalTime.of(15, 0))
                        .postMarketCloseTime(LocalTime.of(19, 0))
                        .testMode(true)
                        .build()
        );

        exchangeRepository.saveAll(exchanges);
        log.info("Seeded {} exchanges.", exchanges.size());
    }
}
