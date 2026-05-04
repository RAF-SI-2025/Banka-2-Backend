package rs.raf.banka2_bek.investmentfund.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rs.raf.banka2_bek.investmentfund.model.ClientFundTransaction;
import rs.raf.banka2_bek.investmentfund.model.ClientFundTransactionStatus;

import java.util.List;

public interface ClientFundTransactionRepository extends JpaRepository<ClientFundTransaction, Long> {

    List<ClientFundTransaction> findByFundIdOrderByCreatedAtDesc(Long fundId);

    List<ClientFundTransaction> findByUserIdAndUserRoleOrderByCreatedAtDesc(Long userId, String userRole);

    List<ClientFundTransaction> findByStatus(ClientFundTransactionStatus status);
    List<ClientFundTransaction> findByUserIdAndStatusOrderByCreatedAtAsc(Long userId, ClientFundTransactionStatus status);
}
