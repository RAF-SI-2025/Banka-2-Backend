package rs.raf.banka2_bek.investmentfund.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rs.raf.banka2_bek.investmentfund.model.ClientFundPosition;

import java.util.List;
import java.util.Optional;

public interface ClientFundPositionRepository extends JpaRepository<ClientFundPosition, Long> {

    Optional<ClientFundPosition> findByFundIdAndUserIdAndUserRole(Long fundId, Long userId, String userRole);

    List<ClientFundPosition> findByFundId(Long fundId);

    List<ClientFundPosition> findByUserIdAndUserRole(Long userId, String userRole);
}
