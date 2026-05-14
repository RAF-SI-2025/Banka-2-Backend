package rs.raf.banka2_bek.stock.service.implementation;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import rs.raf.banka2_bek.berza.model.Exchange;
import rs.raf.banka2_bek.berza.repository.ExchangeRepository;
import rs.raf.banka2_bek.exchange.ExchangeService;
import rs.raf.banka2_bek.exchange.dto.ExchangeRateDto;
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
import rs.raf.banka2_bek.timeseries.ListingPriceRecorder;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Slf4j
public class ListingServiceImpl implements ListingService {

    private final ListingRepository listingRepository;
    private final Random random = new Random();
    private final ListingDailyPriceInfoRepository dailyPriceRepository;
    private final RestTemplate restTemplate;
    private final ExchangeService exchangeService;
    private final ExchangeRepository exchangeRepository;
    private final ObjectProvider<ListingPriceRecorder> priceRecorderProvider;

    @Value("${stock.api.keys:demo}")
    private String stockApiKeys;

    private final AtomicInteger keyIndex = new AtomicInteger(0);

    /**
     * Round-robin API key selection across configured keys.
     */
    private String getNextApiKey() {
        String[] keys = stockApiKeys.split(",");
        int idx = keyIndex.getAndIncrement() % keys.length;
        return keys[idx].trim();
    }

    @Value("${stock.api.url:https://www.alphavantage.co/query}")
    private String stockApiUrl;

    @Override
    public Page<ListingDto> getListings(String type, String search, int page, int size) {
        return getListings(type, search, null, null, null, null, null, page, size);
    }

    @Override
    public Page<ListingDto> getListings(String type, String search,
                                        String exchangePrefix,
                                        BigDecimal priceMin, BigDecimal priceMax,
                                        LocalDate settlementDateFrom, LocalDate settlementDateTo,
                                        int page, int size) {
        ListingType listingType;
        try {
            listingType = ListingType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Nepoznat tip hartije: " + type);
        }

        if (listingType == ListingType.FOREX && isClient()) {
            throw new IllegalStateException("Klijenti nemaju pristup FOREX hartijama.");
        }

        // Validate price range
        if (priceMin != null && priceMax != null && priceMin.compareTo(priceMax) > 0) {
            throw new IllegalArgumentException("Minimalna cena ne moze biti veca od maksimalne.");
        }

        var pageable = PageRequest.of(page, size, Sort.by("ticker").ascending());
        Map<String, Boolean> testModeByAcronym = loadExchangeTestModeMap();
        return listingRepository
                .findAll(ListingSpec.withFilters(listingType, search, exchangePrefix,
                        priceMin, priceMax, settlementDateFrom, settlementDateTo), pageable)
                .map(l -> ListingMapper.toDto(l, testModeByAcronym.get(l.getExchangeAcronym())));
    }

    /**
     * Ucitava jedan put sve aktivne berze i vraca mapu acronym -> testMode,
     * da ne bismo gadjali bazu po svakom listingu.
     */
    private Map<String, Boolean> loadExchangeTestModeMap() {
        return exchangeRepository.findAll().stream()
                .collect(Collectors.toMap(
                        Exchange::getAcronym,
                        Exchange::isTestMode,
                        (a, b) -> a));
    }

    /**
     * Proverava da li je berza na kojoj se listing trguje u test modu.
     * Ako berza ne postoji u mapi (nepoznata), podrazumevano je nije u test modu.
     * Ocekivani slucaj: svaka listinga je povezana sa seed-ovanom berzom.
     */
    private boolean isListingExchangeInTestMode(Listing listing, Map<String, Boolean> testModeMap) {
        return Boolean.TRUE.equals(testModeMap.get(listing.getExchangeAcronym()));
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

        Boolean testMode = exchangeRepository.findByAcronym(listing.getExchangeAcronym())
                .map(Exchange::isTestMode)
                .orElse(null);
        return ListingMapper.toDto(listing, testMode);
    }

    @Override
    public List<ListingDailyPriceDto> getListingHistory(Long listingId, String period) {

        Listing listing = listingRepository.findById(listingId).orElse(null);

        if (listing == null)
            throw new EntityNotFoundException("Listing id: " + listingId + " not found.");

        if(listing.getListingType() == ListingType.FOREX && isClient())
            throw new IllegalStateException("Klijenti nemaju pristup FOREX hartijama.");

        LocalDate now = LocalDate.now();
        List<ListingDailyPriceInfo> dailyPrices;

        if ("DAY".equalsIgnoreCase(period)) dailyPrices = dailyPriceRepository.findByListingIdAndDate(listingId, now);

        else if ("WEEK".equalsIgnoreCase(period))
            dailyPrices = dailyPriceRepository.findByListingIdAndDateAfterOrderByDateDesc(
                    listingId, now.minusDays(7)
            );

        else if ("MONTH".equalsIgnoreCase(period))
            dailyPrices = dailyPriceRepository.findByListingIdAndDateAfterOrderByDateDesc(
                    listingId, now.minusDays(30)
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
    @Transactional
    public void refreshPrices() {
        List<Listing> listings = listingRepository.findAll();
        Map<String, Boolean> testModeByAcronym = loadExchangeTestModeMap();

        for (Listing listing : listings) {
            BigDecimal currentPrice = listing.getPrice();
            if (currentPrice == null) continue;

            BigDecimal newPrice;
            BigDecimal newAsk;
            BigDecimal newBid;
            BigDecimal priceChange;
            long newVolume;

            boolean exchangeInTestMode = isListingExchangeInTestMode(listing, testModeByAcronym);

            if (exchangeInTestMode) {
                // Test mode: simuliramo cene bez trosenja Alpha Vantage / fixer.io kljuceva
                double changePercent = 0.98 + (0.04 * random.nextDouble());
                newPrice = currentPrice.multiply(BigDecimal.valueOf(changePercent))
                        .setScale(4, RoundingMode.HALF_UP);
                newAsk = newPrice.multiply(BigDecimal.valueOf(1.002)).setScale(4, RoundingMode.HALF_UP);
                newBid = newPrice.multiply(BigDecimal.valueOf(0.998)).setScale(4, RoundingMode.HALF_UP);
                priceChange = newPrice.subtract(currentPrice);
                newVolume = listing.getVolume() != null
                        ? (long) (listing.getVolume() * (0.9 + (0.2 * random.nextDouble())))
                        : 100000L;
                log.debug("Refreshed {} in test-mode (simulation): price={}", listing.getTicker(), newPrice);
            } else if (listing.getListingType() == ListingType.STOCK) {
                // Try Alpha Vantage for stocks
                BigDecimal[] alphaResult = fetchAlphaVantagePrice(listing.getTicker());
                if (alphaResult != null) {
                    newPrice = alphaResult[0];
                    newAsk = newPrice.multiply(BigDecimal.valueOf(1.002)).setScale(4, RoundingMode.HALF_UP);
                    newBid = newPrice.multiply(BigDecimal.valueOf(0.998)).setScale(4, RoundingMode.HALF_UP);
                    priceChange = alphaResult[1]; // change from previous close
                    newVolume = alphaResult[2].longValue();
                    // Use real high/low if available
                    if (alphaResult[3] != null) newAsk = alphaResult[3]; // daily high
                    if (alphaResult[4] != null) newBid = alphaResult[4]; // daily low
                    log.info("Refreshed {} from Alpha Vantage: price={}", listing.getTicker(), newPrice);
                } else {
                    log.warn("Alpha Vantage unavailable for {}, using random simulation", listing.getTicker());
                    double changePercent = 0.98 + (0.04 * random.nextDouble());
                    newPrice = currentPrice.multiply(BigDecimal.valueOf(changePercent))
                            .setScale(4, RoundingMode.HALF_UP);
                    newAsk = newPrice.multiply(BigDecimal.valueOf(1.002)).setScale(4, RoundingMode.HALF_UP);
                    newBid = newPrice.multiply(BigDecimal.valueOf(0.998)).setScale(4, RoundingMode.HALF_UP);
                    priceChange = newPrice.subtract(currentPrice);
                    newVolume = listing.getVolume() != null
                            ? (long) (listing.getVolume() * (0.9 + (0.2 * random.nextDouble())))
                            : 100000L;
                }
            } else if (listing.getListingType() == ListingType.FOREX) {
                // Use ExchangeService (fixer.io) for forex pairs
                BigDecimal forexPrice = fetchForexPrice(listing.getBaseCurrency(), listing.getQuoteCurrency());
                if (forexPrice != null) {
                    newPrice = forexPrice;
                    newAsk = newPrice.multiply(BigDecimal.valueOf(1.002)).setScale(4, RoundingMode.HALF_UP);
                    newBid = newPrice.multiply(BigDecimal.valueOf(0.998)).setScale(4, RoundingMode.HALF_UP);
                    priceChange = newPrice.subtract(currentPrice);
                    newVolume = listing.getVolume() != null
                            ? (long) (listing.getVolume() * (0.9 + (0.2 * random.nextDouble())))
                            : 100000L;
                    log.info("Refreshed {} from fixer.io: price={}", listing.getTicker(), newPrice);
                } else {
                    log.warn("Fixer.io unavailable for {}, using random simulation", listing.getTicker());
                    double changePercent = 0.98 + (0.04 * random.nextDouble());
                    newPrice = currentPrice.multiply(BigDecimal.valueOf(changePercent))
                            .setScale(4, RoundingMode.HALF_UP);
                    newAsk = newPrice.multiply(BigDecimal.valueOf(1.002)).setScale(4, RoundingMode.HALF_UP);
                    newBid = newPrice.multiply(BigDecimal.valueOf(0.998)).setScale(4, RoundingMode.HALF_UP);
                    priceChange = newPrice.subtract(currentPrice);
                    newVolume = listing.getVolume() != null
                            ? (long) (listing.getVolume() * (0.9 + (0.2 * random.nextDouble())))
                            : 100000L;
                }
            } else {
                // FUTURES — keep random simulation (no free API)
                double changePercent = 0.98 + (0.04 * random.nextDouble());
                newPrice = currentPrice.multiply(BigDecimal.valueOf(changePercent))
                        .setScale(4, RoundingMode.HALF_UP);
                newAsk = newPrice.multiply(BigDecimal.valueOf(1.002)).setScale(4, RoundingMode.HALF_UP);
                newBid = newPrice.multiply(BigDecimal.valueOf(0.998)).setScale(4, RoundingMode.HALF_UP);
                priceChange = newPrice.subtract(currentPrice);
                newVolume = listing.getVolume() != null
                        ? (long) (listing.getVolume() * (0.9 + (0.2 * random.nextDouble())))
                        : 100000L;
            }

            listing.setPrice(newPrice);
            listing.setAsk(newAsk);
            listing.setBid(newBid);
            listing.setPriceChange(priceChange);
            listing.setLastRefresh(LocalDateTime.now());
            listing.setVolume(newVolume);

            // Save daily price snapshot for history chart
            saveDailyPriceSnapshot(listing, newPrice, newAsk, newBid, priceChange, newVolume);

            // Time-series tick u InfluxDB. ObjectProvider gracefuly vraca null
            // ako banka2.influx.enabled=false (BE i dalje radi bez Influx-a).
            // Null check za testove koji konstrukcijom rucnim ne prosledjuju provider.
            ListingPriceRecorder recorder = priceRecorderProvider != null
                    ? priceRecorderProvider.getIfAvailable() : null;
            if (recorder != null) {
                BigDecimal high = currentPrice.max(newPrice);
                BigDecimal low  = currentPrice.min(newPrice);
                recorder.recordTick(
                        listing.getTicker(),
                        listing.getExchangeAcronym(),
                        listing.getListingType() != null ? listing.getListingType().name() : "STOCK",
                        currentPrice,
                        high,
                        low,
                        newPrice,
                        newVolume,
                        newAsk,
                        newBid,
                        Instant.now()
                );
            }
        }

        listingRepository.saveAll(listings);
    }

    /**
     * Fetches real stock price from Alpha Vantage GLOBAL_QUOTE endpoint.
     * Returns [price, change, volume, high, low] or null on failure.
     * Includes 12-second delay for rate limiting (free tier: 5 calls/min, 25/day).
     */
    private BigDecimal[] fetchAlphaVantagePrice(String ticker) {
        try {
            String apiKey = getNextApiKey();
            String url = stockApiUrl + "?function=GLOBAL_QUOTE&symbol=" + ticker + "&apikey=" + apiKey;

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            if (response == null || !response.containsKey("Global Quote")) {
                return null;
            }

            @SuppressWarnings("unchecked")
            Map<String, String> quote = (Map<String, String>) response.get("Global Quote");

            if (quote == null || quote.isEmpty() || quote.get("05. price") == null) {
                return null;
            }

            BigDecimal price = new BigDecimal(quote.get("05. price")).setScale(4, RoundingMode.HALF_UP);
            BigDecimal previousClose = quote.get("08. previous close") != null
                    ? new BigDecimal(quote.get("08. previous close"))
                    : BigDecimal.ZERO;
            BigDecimal change = price.subtract(previousClose).setScale(4, RoundingMode.HALF_UP);
            BigDecimal volume = quote.get("06. volume") != null
                    ? new BigDecimal(quote.get("06. volume"))
                    : BigDecimal.valueOf(100000);
            BigDecimal high = quote.get("03. high") != null
                    ? new BigDecimal(quote.get("03. high")).setScale(4, RoundingMode.HALF_UP)
                    : null;
            BigDecimal low = quote.get("04. low") != null
                    ? new BigDecimal(quote.get("04. low")).setScale(4, RoundingMode.HALF_UP)
                    : null;

            // Rate limiting: 12-second delay between API calls (5 calls/min max)
            sleepQuietly(12000);

            return new BigDecimal[]{price, change, volume, high, low};
        } catch (Exception e) {
            log.warn("Alpha Vantage API error for {}: {}", ticker, e.getMessage());
            return null;
        }
    }

    @lombok.Generated
    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Fetches forex rate using ExchangeService (fixer.io).
     * Calculates cross rate for the given currency pair.
     */
    private BigDecimal fetchForexPrice(String baseCurrency, String quoteCurrency) {
        try {
            if (baseCurrency == null || quoteCurrency == null) return null;

            List<ExchangeRateDto> rates = exchangeService.getAllRates();

            // Rates are in format: how much foreign currency per 1 RSD
            Double baseRate = null;
            Double quoteRate = null;

            for (ExchangeRateDto rate : rates) {
                if (rate.getCurrency().equalsIgnoreCase(baseCurrency)) baseRate = rate.getRate();
                if (rate.getCurrency().equalsIgnoreCase(quoteCurrency)) quoteRate = rate.getRate();
            }

            if (baseRate == null || quoteRate == null || baseRate == 0.0) return null;

            // Cross rate: quoteCurrency/baseCurrency
            double crossRate = quoteRate / baseRate;
            return BigDecimal.valueOf(crossRate).setScale(4, RoundingMode.HALF_UP);
        } catch (Exception e) {
            log.warn("Fixer.io error for {}/{}: {}", baseCurrency, quoteCurrency, e.getMessage());
            return null;
        }
    }

    /**
     * Saves or updates the daily price snapshot for the listing history chart.
     */
    private void saveDailyPriceSnapshot(Listing listing, BigDecimal newPrice,
                                        BigDecimal high, BigDecimal low,
                                        BigDecimal priceChange, long volume) {
        LocalDate today = LocalDate.now();
        List<ListingDailyPriceInfo> existingToday = dailyPriceRepository
                .findByListingIdAndDate(listing.getId(), today);
        if (existingToday.isEmpty()) {
            ListingDailyPriceInfo dailyPrice = new ListingDailyPriceInfo();
            dailyPrice.setListing(listing);
            dailyPrice.setDate(today);
            dailyPrice.setPrice(newPrice);
            dailyPrice.setHigh(high);
            dailyPrice.setLow(low);
            dailyPrice.setChange(priceChange);
            dailyPrice.setVolume(volume);
            dailyPriceRepository.save(dailyPrice);
        } else {
            ListingDailyPriceInfo existing = existingToday.get(0);
            existing.setPrice(newPrice);
            if (high.compareTo(existing.getHigh()) > 0) existing.setHigh(high);
            if (low.compareTo(existing.getLow()) < 0) existing.setLow(low);
            existing.setChange(priceChange);
            existing.setVolume(volume);
            dailyPriceRepository.save(existing);
        }
    }
    @Scheduled(fixedRate = 900000)
    public void scheduledRefresh() {
        // Scheduler samo okida business metodu, ne sadrži logiku direktno
        this.refreshPrices();
    }

    @Override
    public void loadInitialData() {
        long count = listingRepository.count();
        if (count == 0) {
            log.warn("No listings in database. Please ensure seed data is loaded.");
        } else {
            log.info("Found {} listings in database.", count);
        }
    }
}
