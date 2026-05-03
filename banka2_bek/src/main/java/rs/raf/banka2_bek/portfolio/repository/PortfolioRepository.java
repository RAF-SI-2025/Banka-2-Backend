package rs.raf.banka2_bek.portfolio.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import rs.raf.banka2_bek.portfolio.model.Portfolio;

import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {

    /**
     * Vraca sve portfolios za (userId, userRole). Koristiti ovu varijantu
     * umesto {@link #findByUserId(Long)} kad je poznata uloga, posto
     * clients.id i employees.id imaju preklapajuce prostore.
     */
    List<Portfolio> findByUserIdAndUserRole(Long userId, String userRole);

    /**
     * @deprecated Moze vratiti portfolios za vise vlasnika razlicitih uloga
     * ako se ID preklapa. Koristiti {@link #findByUserIdAndUserRole(Long, String)}.
     */
    @Deprecated
    List<Portfolio> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Portfolio p WHERE p.id = :id")
    Optional<Portfolio> findByIdForUpdate(@Param("id") Long id);

    Optional<Portfolio> findByUserIdAndUserRoleAndListingId(Long userId, String userRole, Long listingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Portfolio p WHERE p.userId = :userId AND p.userRole = :userRole AND p.listingId = :listingId")
    Optional<Portfolio> findByUserIdAndUserRoleAndListingIdForUpdate(@Param("userId") Long userId,
                                                                    @Param("userRole") String userRole,
                                                                    @Param("listingId") Long listingId);

    /**
     * @deprecated Koristiti {@link #findByUserIdAndUserRoleAndListingIdForUpdate(Long, String, Long)}.
     */
    @Deprecated
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Portfolio p WHERE p.userId = :userId AND p.listingId = :listingId")
    Optional<Portfolio> findByUserIdAndListingIdForUpdate(@Param("userId") Long userId,
                                                          @Param("listingId") Long listingId);
}
