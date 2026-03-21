package rs.raf.banka2_bek.stock.service;

import org.springframework.data.domain.Page;
import rs.raf.banka2_bek.stock.dto.ListingDailyPriceDto;
import rs.raf.banka2_bek.stock.dto.ListingDto;

import java.util.List;

public interface ListingService {

    /**
     * Vraca stranicu hartija od vrednosti filtrirano po tipu.
     * Tipovi: STOCK, FUTURES, FOREX
     * Klijenti vide samo STOCK i FUTURES.
     * Aktuari vide sve.
     */
    Page<ListingDto> getListings(String type, String search, int page, int size);

    /**
     * Vraca detalje za jednu hartiju po ID-ju.
     * Ukljucuje izvedene podatke (marketCap, maintenanceMargin, initialMarginCost).
     */
    ListingDto getListingById(Long id);

    /**
     * Vraca istorijske cene za hartiju za dati period.
     * Period: DAY, WEEK, MONTH, YEAR, FIVE_YEARS, ALL
     */
    List<ListingDailyPriceDto> getListingHistory(Long listingId, String period);

    /**
     * Osvezava cene hartija iz eksternog API-ja.
     * Poziva se:
     * 1. Automatski svakih 15 minuta (@Scheduled)
     * 2. Rucno od strane korisnika (dugme "Osvezi")
     * 3. Nakon izvrsavanja operacije
     */
    void refreshPrices();

    /**
     * Ucitava pocetne podatke o hartijama (seed/bootstrap).
     * Moze koristiti:
     * - AlphaVantage API za akcije
     * - Dummy CSV podatke za futures
     * - ExchangeRate API za forex (vec postoji u exchange modulu)
     */
    void loadInitialData();
}
