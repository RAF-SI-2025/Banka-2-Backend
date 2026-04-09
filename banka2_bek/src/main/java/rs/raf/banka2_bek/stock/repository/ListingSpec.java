package rs.raf.banka2_bek.stock.repository;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.model.ListingType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class ListingSpec {

    private ListingSpec() {}

    /**
     * Filtrira listinge po tipu, pretrazi, exchange prefixu, rasponu cena i settlement date-u.
     */
    public static Specification<Listing> withFilters(
            ListingType type,
            String search,
            String exchangePrefix,
            BigDecimal priceMin,
            BigDecimal priceMax,
            LocalDate settlementDateFrom,
            LocalDate settlementDateTo) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("listingType"), type));

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                Predicate tickerMatch = cb.like(cb.lower(root.get("ticker")), pattern);
                Predicate nameMatch = cb.like(cb.lower(root.get("name")), pattern);
                predicates.add(cb.or(tickerMatch, nameMatch));
            }

            if (exchangePrefix != null && !exchangePrefix.isBlank()) {
                String prefixPattern = exchangePrefix.toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("exchangeAcronym")), prefixPattern));
            }

            if (priceMin != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), priceMin));
            }

            if (priceMax != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), priceMax));
            }

            if (settlementDateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("settlementDate"), settlementDateFrom));
            }

            if (settlementDateTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("settlementDate"), settlementDateTo));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Filtrira listinge po tipu i opcionalnoj pretrazi po ticker-u ili imenu.
     * Pretraga je case-insensitive i partial match.
     */
    public static Specification<Listing> byTypeAndSearch(ListingType type, String search) {
        return withFilters(type, search, null, null, null, null, null);
    }
}
