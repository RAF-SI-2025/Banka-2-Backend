package rs.raf.banka2_bek.stock.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import rs.raf.banka2_bek.stock.model.ListingDailyPriceInfo;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ListingDailyPriceInfoRepository extends JpaRepository<ListingDailyPriceInfo, Long> {

    // TODO: Za grafik promene cene
    List<ListingDailyPriceInfo> findByListingIdAndDateBetweenOrderByDateAsc(
            Long listingId, LocalDate from, LocalDate to);

    List<ListingDailyPriceInfo> findByListingIdOrderByDateDesc(Long listingId);

    List<ListingDailyPriceInfo> findByListingIdAndDate(Long listingId, LocalDate date);

    List<ListingDailyPriceInfo> findByListingIdAndDateAfterOrderByDateDesc(Long listingId, LocalDate date);
}
