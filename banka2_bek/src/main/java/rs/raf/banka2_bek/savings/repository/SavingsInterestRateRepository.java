package rs.raf.banka2_bek.savings.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rs.raf.banka2_bek.savings.entity.SavingsInterestRate;

import java.util.List;
import java.util.Optional;

public interface SavingsInterestRateRepository extends JpaRepository<SavingsInterestRate, Long> {

    @Query("""
        SELECT r FROM SavingsInterestRate r
        WHERE r.currency.id = :currencyId
          AND r.termMonths = :termMonths
          AND r.active = true
        """)
    Optional<SavingsInterestRate> findActive(@Param("currencyId") Long currencyId,
                                              @Param("termMonths") Integer termMonths);

    @Query("SELECT r FROM SavingsInterestRate r WHERE r.active = true ORDER BY r.currency.code, r.termMonths")
    List<SavingsInterestRate> findAllActive();

    @Query("SELECT r FROM SavingsInterestRate r WHERE r.currency.code = :code AND r.active = true ORDER BY r.termMonths")
    List<SavingsInterestRate> findActiveByCurrencyCode(@Param("code") String code);
}
