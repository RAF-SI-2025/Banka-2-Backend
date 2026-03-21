package rs.raf.banka2_bek.stock.mapper;

import rs.raf.banka2_bek.stock.dto.ListingDailyPriceDto;
import rs.raf.banka2_bek.stock.dto.ListingDto;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.model.ListingDailyPriceInfo;
import rs.raf.banka2_bek.stock.model.ListingType;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Centralizovano mapiranje Listing <-> ListingDto.
 * Racuna izvedena polja: changePercent, maintenanceMargin,
 * initialMarginCost, marketCap.
 */
public final class ListingMapper {

    private ListingMapper() {}

    /**
     * Mapira Listing entitet u ListingDto sa svim izvedenim poljima.
     */
    public static ListingDto toDto(Listing listing) {
        if (listing == null) return null;

        ListingDto dto = new ListingDto();
        dto.setId(listing.getId());
        dto.setTicker(listing.getTicker());
        dto.setName(listing.getName());
        dto.setExchangeAcronym(listing.getExchangeAcronym());
        dto.setListingType(listing.getListingType() != null ? listing.getListingType().name() : null);
        dto.setPrice(listing.getPrice());
        dto.setAsk(listing.getAsk());
        dto.setBid(listing.getBid());
        dto.setVolume(listing.getVolume());
        dto.setPriceChange(listing.getPriceChange());

        // Stock-specific
        dto.setOutstandingShares(listing.getOutstandingShares());
        dto.setDividendYield(listing.getDividendYield());

        // Forex-specific
        dto.setBaseCurrency(listing.getBaseCurrency());
        dto.setQuoteCurrency(listing.getQuoteCurrency());
        dto.setLiquidity(listing.getLiquidity());

        // Futures-specific
        dto.setContractSize(listing.getContractSize());
        dto.setContractUnit(listing.getContractUnit());
        dto.setSettlementDate(listing.getSettlementDate());

        // Izvedena polja
        dto.setChangePercent(calculateChangePercent(listing));
        dto.setMaintenanceMargin(calculateMaintenanceMargin(listing));
        dto.setInitialMarginCost(calculateInitialMarginCost(listing));
        dto.setMarketCap(calculateMarketCap(listing));

        return dto;
    }

    /**
     * Change Percent = (priceChange / (price - priceChange)) * 100
     * Ako je prethodna cena (price - priceChange) nula ili null, vraca null.
     */
    public static BigDecimal calculateChangePercent(Listing listing) {
        BigDecimal price = listing.getPrice();
        BigDecimal change = listing.getPriceChange();
        if (price == null || change == null) return null;

        BigDecimal previousPrice = price.subtract(change);
        if (previousPrice.compareTo(BigDecimal.ZERO) == 0) return null;

        return change.multiply(BigDecimal.valueOf(100))
                .divide(previousPrice, 2, RoundingMode.HALF_UP);
    }

    /**
     * Maintenance Margin zavisi od tipa:
     * - STOCK:   50% * price
     * - FOREX:   contractSize * price * 10%
     * - FUTURES: contractSize * price * 10%
     */
    public static BigDecimal calculateMaintenanceMargin(Listing listing) {
        BigDecimal price = listing.getPrice();
        if (price == null || listing.getListingType() == null) return null;

        switch (listing.getListingType()) {
            case STOCK:
                return price.multiply(BigDecimal.valueOf(0.5))
                        .setScale(4, RoundingMode.HALF_UP);
            case FOREX:
            case FUTURES:
                int cs = listing.getContractSize() != null ? listing.getContractSize() : 1;
                return BigDecimal.valueOf(cs).multiply(price)
                        .multiply(BigDecimal.valueOf(0.1))
                        .setScale(4, RoundingMode.HALF_UP);
            default:
                return null;
        }
    }

    /**
     * Initial Margin Cost = maintenanceMargin * 1.1
     */
    public static BigDecimal calculateInitialMarginCost(Listing listing) {
        BigDecimal mm = calculateMaintenanceMargin(listing);
        if (mm == null) return null;
        return mm.multiply(BigDecimal.valueOf(1.1))
                .setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Market Cap = outstandingShares * price (samo za STOCK)
     */
    public static BigDecimal calculateMarketCap(Listing listing) {
        if (listing.getListingType() != ListingType.STOCK) return null;
        if (listing.getOutstandingShares() == null || listing.getPrice() == null) return null;
        return BigDecimal.valueOf(listing.getOutstandingShares())
                .multiply(listing.getPrice())
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Mapira ListingDailyPriceInfo entitet u DTO.
     */
    public static ListingDailyPriceDto toDailyPriceDto(ListingDailyPriceInfo info) {
        if (info == null) return null;

        ListingDailyPriceDto dto = new ListingDailyPriceDto();
        dto.setDate(info.getDate());
        dto.setPrice(info.getPrice());
        dto.setHigh(info.getHigh());
        dto.setLow(info.getLow());
        dto.setChange(info.getChange());
        dto.setVolume(info.getVolume());
        return dto;
    }
}
