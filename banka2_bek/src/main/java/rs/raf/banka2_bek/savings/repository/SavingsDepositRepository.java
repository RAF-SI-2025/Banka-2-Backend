package rs.raf.banka2_bek.savings.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rs.raf.banka2_bek.savings.entity.SavingsDeposit;
import rs.raf.banka2_bek.savings.entity.SavingsDepositStatus;

import java.time.LocalDate;
import java.util.List;

public interface SavingsDepositRepository extends JpaRepository<SavingsDeposit, Long> {

    List<SavingsDeposit> findByClientIdOrderByCreatedAtDesc(Long clientId);

    List<SavingsDeposit> findByStatusAndNextInterestPaymentDateLessThanEqual(
        SavingsDepositStatus status, LocalDate date);

    List<SavingsDeposit> findByStatusAndMaturityDateLessThanEqual(
        SavingsDepositStatus status, LocalDate date);

    @Query("""
        SELECT d FROM SavingsDeposit d
        WHERE (:status IS NULL OR d.status = :status)
          AND (:clientId IS NULL OR d.clientId = :clientId)
        ORDER BY d.createdAt DESC
        """)
    Page<SavingsDeposit> adminFindAll(
        @Param("status") SavingsDepositStatus status,
        @Param("clientId") Long clientId,
        Pageable pageable);
}
