package rs.raf.banka2_bek.employee.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rs.raf.banka2_bek.employee.model.ActivationToken;
import rs.raf.banka2_bek.employee.model.Employee;

import java.util.Optional;

public interface ActivationTokenRepository extends JpaRepository<ActivationToken, Long> {

    Optional<ActivationToken> findByToken(String token);

    @Modifying
    @Query("UPDATE ActivationToken t SET t.invalidated = true WHERE t.employee = :employee AND t.used = false AND t.invalidated = false")
    void invalidateAllActiveTokensForEmployee(@Param("employee") Employee employee);
}
