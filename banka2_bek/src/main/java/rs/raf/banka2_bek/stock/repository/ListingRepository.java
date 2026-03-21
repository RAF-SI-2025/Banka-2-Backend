package rs.raf.banka2_bek.stock.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.model.ListingType;

import java.util.Optional;

@Repository
public interface ListingRepository extends JpaRepository<Listing, Long> {

    Optional<Listing> findByTicker(String ticker);

    Page<Listing> findByListingType(ListingType listingType, Pageable pageable);

    // TODO: Dodati query metode za filtriranje:
    // - findByListingTypeAndTickerContainingIgnoreCase(type, ticker, pageable)
    // - findByListingTypeAndExchangeAcronym(type, exchange, pageable)
    // - findByListingTypeAndPriceBetween(type, minPrice, maxPrice, pageable)
    // - findByListingTypeAndSettlementDateAfter(type, date, pageable) - za futures
}
