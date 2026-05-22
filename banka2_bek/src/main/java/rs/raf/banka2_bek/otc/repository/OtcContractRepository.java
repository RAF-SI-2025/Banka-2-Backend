package rs.raf.banka2_bek.otc.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import rs.raf.banka2_bek.otc.model.OtcContract;
import rs.raf.banka2_bek.otc.model.OtcContractStatus;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface OtcContractRepository extends JpaRepository<OtcContract, Long> {

    @Query("SELECT c FROM OtcContract c WHERE " +
           "((c.buyerId = :userId AND c.buyerRole = :userRole) " +
           " OR (c.sellerId = :userId AND c.sellerRole = :userRole)) " +
           "ORDER BY c.createdAt DESC")
    List<OtcContract> findAllForUser(@Param("userId") Long userId,
                                     @Param("userRole") String userRole);

    @Query("SELECT c FROM OtcContract c WHERE " +
           "((c.buyerId = :userId AND c.buyerRole = :userRole) " +
           " OR (c.sellerId = :userId AND c.sellerRole = :userRole)) " +
           "AND c.status = :status ORDER BY c.createdAt DESC")
    List<OtcContract> findByUserAndStatus(@Param("userId") Long userId,
                                          @Param("userRole") String userRole,
                                          @Param("status") OtcContractStatus status);

    /**
     * Ukupna kolicina akcija na aktivnim (nekoriscenim i nesistekllim) ugovorima
     * za jednog prodavca i jedan listing. Koristi se za rezervaciju publicQuantity.
     */
    @Query("SELECT COALESCE(SUM(c.quantity), 0) FROM OtcContract c " +
           "WHERE c.sellerId = :sellerId AND c.sellerRole = :sellerRole " +
           "AND c.listing.id = :listingId " +
           "AND c.status = rs.raf.banka2_bek.otc.model.OtcContractStatus.ACTIVE")
    int sumActiveReservedByListing(@Param("sellerId") Long sellerId,
                                   @Param("sellerRole") String sellerRole,
                                   @Param("listingId") Long listingId);

    @Query("SELECT c FROM OtcContract c WHERE c.status = rs.raf.banka2_bek.otc.model.OtcContractStatus.ACTIVE " +
           "AND c.settlementDate < :today")
    List<OtcContract> findExpiredActive(@Param("today") LocalDate today);

    @Query("SELECT c FROM OtcContract c WHERE c.status = rs.raf.banka2_bek.otc.model.OtcContractStatus.ACTIVE " +
           "AND c.settlementDate = :targetDate")
    List<OtcContract> findActiveExpiringOn(@Param("targetDate") LocalDate targetDate);
}
