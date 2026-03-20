package rs.raf.banka2_bek.account.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import rs.raf.banka2_bek.account.model.AccountRequest;

public interface AccountRequestRepository extends JpaRepository<AccountRequest, Long> {
    Page<AccountRequest> findByStatus(String status, Pageable pageable);
    Page<AccountRequest> findByClientEmail(String clientEmail, Pageable pageable);
}
