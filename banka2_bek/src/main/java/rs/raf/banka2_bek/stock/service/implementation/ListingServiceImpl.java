package rs.raf.banka2_bek.stock.service.implementation;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import rs.raf.banka2_bek.stock.dto.ListingDailyPriceDto;
import rs.raf.banka2_bek.stock.dto.ListingDto;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.model.ListingType;
import rs.raf.banka2_bek.stock.repository.ListingDailyPriceInfoRepository;
import rs.raf.banka2_bek.stock.repository.ListingRepository;
import rs.raf.banka2_bek.stock.service.ListingService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class ListingServiceImpl implements ListingService {

    private final ListingRepository listingRepository;
    private final Random random = new Random();
    private final ListingDailyPriceInfoRepository dailyPriceRepository;

    @Override
    public Page<ListingDto> getListings(String type, String search, int page, int size) {
        // TODO: Implementirati
        // 1. Parsirati type string u ListingType enum
        // 2. Ako je search null/prazan, dohvatiti sve po tipu sa paginacijom
        // 3. Ako je search zadat, filtrirati po ticker ILI name (case-insensitive)
        // 4. Mapirati svaki Listing u ListingDto (ukljuciti izvedene podatke)
        //
        // IZVEDENI PODACI za svaki listing:
        //   - changePercent = (priceChange / (price - priceChange)) * 100
        //   - maintenanceMargin:
        //       STOCK: 50% * price
        //       FOREX: contractSize * price * 10%
        //       FUTURES: contractSize * price * 10%
        //   - initialMarginCost = maintenanceMargin * 1.1
        //   - marketCap = outstandingShares * price (samo za STOCK)
        //
        // NAPOMENA: Klijenti vide samo STOCK i FUTURES, aktuari sve.
        //           Ovu proveru raditi u controlleru ili ovde.
        throw new UnsupportedOperationException("TODO: Implementirati getListings");
    }

    @Override
    public ListingDto getListingById(Long id) {
        // TODO: Implementirati
        // 1. Naci listing po ID-ju
        // 2. Ako ne postoji, baciti exception
        // 3. Mapirati u DTO sa svim izvedenim podacima
        throw new UnsupportedOperationException("TODO: Implementirati getListingById");
    }

    @Override
    public List<ListingDailyPriceDto> getListingHistory(Long listingId, String period) {
        // TODO: Implementirati
        // 1. Na osnovu perioda izracunati fromDate:
        //    DAY = danas
        //    WEEK = danas - 7 dana
        //    MONTH = danas - 30 dana
        //    YEAR = danas - 365 dana
        //    FIVE_YEARS = danas - 5*365 dana
        //    ALL = od pocetka (LocalDate.MIN ili najraniji datum u bazi)
        // 2. Dohvatiti ListingDailyPriceInfo iz baze za dati period
        // 3. Mapirati u DTO listu
        throw new UnsupportedOperationException("TODO: Implementirati getListingHistory");
    }

    @Override
    @Transactional
    public void refreshPrices() {
        // 1. Proći kroz listinge
        List<Listing> listings = listingRepository.findAll();

        for (Listing listing : listings) {
            BigDecimal currentPrice = listing.getPrice();
            if (currentPrice == null) continue;

            // 2. Ažurirati price-related podatke (Dummy logika +/- 2%)
            double changePercent = 0.98 + (0.04 * random.nextDouble());
            BigDecimal newPrice = currentPrice.multiply(BigDecimal.valueOf(changePercent))
                    .setScale(4, RoundingMode.HALF_UP);

            // Ask/Bid (Ask uvek malo veći, Bid malo manji)
            BigDecimal newAsk = newPrice.multiply(BigDecimal.valueOf(1.002)).setScale(4, RoundingMode.HALF_UP);
            BigDecimal newBid = newPrice.multiply(BigDecimal.valueOf(0.998)).setScale(4, RoundingMode.HALF_UP);

            // Promena u odnosu na staru cenu
            BigDecimal priceChange = newPrice.subtract(currentPrice);

            // 3. Postaviti lastRefresh = now()
            listing.setPrice(newPrice);
            listing.setAsk(newAsk);
            listing.setBid(newBid);
            listing.setPriceChange(priceChange);
            listing.setLastRefresh(LocalDateTime.now());

            // Ažuriramo i volume (nasumično)
            if (listing.getVolume() != null) {
                listing.setVolume((long) (listing.getVolume() * (0.9 + (0.2 * random.nextDouble()))));
            }
        }

        // 4. Poziv endpoint-a ažurira listinge u bazi
        listingRepository.saveAll(listings);
    }
    @Scheduled(fixedRate = 900000)
    public void scheduledRefresh() {
        // Scheduler samo okida business metodu, ne sadrži logiku direktno
        this.refreshPrices();
    }

    @Override
    public void loadInitialData() {
        // TODO: Implementirati ucitavanje pocetnih podataka
        // OPCIJA 1: Ucitati iz CSV fajla (dummy podaci od generacije 2023/24)
        //   - Fajl sa akcijama: AAPL, MSFT, GOOG, AMZN, TSLA, ...
        //   - Fajl sa futures: CLJ26, SIH26, GCM26, ...
        //   - Forex parovi: koristiti vec postojece iz exchange modula
        //
        // OPCIJA 2: AlphaVantage API
        //   - Company Overview za akcije
        //   - TIME_SERIES_DAILY za istorijske cene
        //
        // OPCIJA 3: Hardcoded dummy podaci u seed.sql
        //   - INSERT INTO listings (...) VALUES (...)
        //   - Najlaksi pristup, vec imamo seed mehanizam
    }
}
