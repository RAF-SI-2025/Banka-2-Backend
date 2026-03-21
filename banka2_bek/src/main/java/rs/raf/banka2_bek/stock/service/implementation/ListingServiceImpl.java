package rs.raf.banka2_bek.stock.service.implementation;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import rs.raf.banka2_bek.stock.dto.ListingDailyPriceDto;
import rs.raf.banka2_bek.stock.dto.ListingDto;
import rs.raf.banka2_bek.stock.model.ListingType;
import rs.raf.banka2_bek.stock.repository.ListingDailyPriceInfoRepository;
import rs.raf.banka2_bek.stock.repository.ListingRepository;
import rs.raf.banka2_bek.stock.service.ListingService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListingServiceImpl implements ListingService {

    private final ListingRepository listingRepository;
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
    public void refreshPrices() {
        // TODO: Implementirati osvezavanje cena
        // OPCIJA 1: AlphaVantage API (besplatan, 5 poziva/min)
        //   - GET https://www.alphavantage.co/query?function=GLOBAL_QUOTE&symbol={ticker}&apikey={key}
        //   - Azurira price, ask, bid, volume, priceChange za akcije
        //
        // OPCIJA 2: Dummy osvezavanje
        //   - Za svaki listing, dodaj random +/- 1-3% na price
        //   - Azuriraj ask = price * 1.001, bid = price * 0.999
        //   - Postavi lastRefresh na LocalDateTime.now()
        //
        // NAPOMENA: Ovo se moze pozivati @Scheduled(fixedRate = 900000) za 15 min
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
