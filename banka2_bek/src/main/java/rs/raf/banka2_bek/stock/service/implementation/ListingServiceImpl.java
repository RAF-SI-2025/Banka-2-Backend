package rs.raf.banka2_bek.stock.service.implementation;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.BadRequestException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import rs.raf.banka2_bek.stock.dto.ListingDailyPriceDto;
import rs.raf.banka2_bek.stock.dto.ListingDto;
import rs.raf.banka2_bek.stock.mapper.ListingMapper;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.model.ListingDailyPriceInfo;
import rs.raf.banka2_bek.stock.model.ListingType;
import rs.raf.banka2_bek.stock.repository.ListingDailyPriceInfoRepository;
import rs.raf.banka2_bek.stock.repository.ListingRepository;
import rs.raf.banka2_bek.stock.repository.ListingSpec;
import rs.raf.banka2_bek.stock.service.ListingService;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ListingServiceImpl implements ListingService {

    private final ListingRepository listingRepository;
    private final ListingDailyPriceInfoRepository dailyPriceRepository;

    @Override
    public Page<ListingDto> getListings(String type, String search, int page, int size) {
        ListingType listingType;
        try {
            listingType = ListingType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Nepoznat tip hartije: " + type);
        }

        if (listingType == ListingType.FOREX && isClient()) {
            throw new IllegalStateException("Klijenti nemaju pristup FOREX hartijama.");
        }

        var pageable = PageRequest.of(page, size, Sort.by("ticker").ascending());
        return listingRepository
                .findAll(ListingSpec.byTypeAndSearch(listingType, search), pageable)
                .map(ListingMapper::toDto);
    }

    private boolean isClient() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_CLIENT".equals(a.getAuthority()));
    }

    @Override
    public ListingDto getListingById(Long id) {

        Optional<Listing> listingOptional = listingRepository.findById(id);

        if (listingOptional.isEmpty()) throw new EntityNotFoundException("Listing id: " + id + " not found.");

        Listing listing = listingOptional.get();

        if (isClient() && listing.getListingType() == ListingType.FOREX)
            throw new IllegalStateException("Klijenti nemaju pristup FOREX hartijama.");

        return ListingMapper.toDto(listing);
    }

    @Override
    public List<ListingDailyPriceDto> getListingHistory(Long listingId, String period) {

        if (!listingRepository.existsById(listingId))
            throw new EntityNotFoundException("Listing id: " + listingId + " not found.");

        LocalDate now = LocalDate.now();

        List<ListingDailyPriceInfo> dailyPrices;

        if ("DAY".equalsIgnoreCase(period)) dailyPrices = dailyPriceRepository.findByListingIdAndDate(listingId, now);

        else if ("WEEK".equalsIgnoreCase(period))
            dailyPrices = dailyPriceRepository.findByListingIdAndDateAfterOrderByDateDesc(
                    listingId, now.minusDays(7)
            );

        else if ("MONTH".equalsIgnoreCase(period))
            dailyPrices = dailyPriceRepository.findByListingIdAndDateAfterOrderByDateDesc(
                    listingId, now.minusMonths(1)
            );

        else if ("YEAR".equalsIgnoreCase(period))
            dailyPrices = dailyPriceRepository.findByListingIdAndDateAfterOrderByDateDesc(
                    listingId, now.minusYears(1)
            );

        else if ("FIVE_YEARS".equalsIgnoreCase(period))
            dailyPrices = dailyPriceRepository.findByListingIdAndDateAfterOrderByDateDesc(
                    listingId, now.minusYears(5)
            );

        else if ("ALL".equalsIgnoreCase(period))
            dailyPrices = dailyPriceRepository.findByListingIdOrderByDateDesc(listingId);

        else throw new IllegalArgumentException("Period može biti: DAY, WEEK, MONTH, YEAR, FIVE_YEARS, ALL");

        return dailyPrices.stream().map(ListingMapper::toDailyPriceDto).toList();
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
