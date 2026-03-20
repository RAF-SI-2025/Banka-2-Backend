package rs.raf.banka2_bek.card.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import rs.raf.banka2_bek.card.model.CardRequest;

public interface CardRequestRepository extends JpaRepository<CardRequest, Long> {
    Page<CardRequest> findByStatus(String status, Pageable pageable);
    Page<CardRequest> findByClientEmail(String clientEmail, Pageable pageable);
}
