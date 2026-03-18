package rs.raf.banka2_bek.payment.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rs.raf.banka2_bek.payment.model.Payment;
import rs.raf.banka2_bek.payment.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Query("""
           select p from Payment p
           where (p.fromAccount.user.id = :clientId
                  or p.toAccountNumber in (select a.accountNumber from Account a where a.user.id = :clientId))
           """)
    Page<Payment> findByUserAccounts(@Param("clientId") Long clientId, Pageable pageable);

    @Query("""
           select p from Payment p
           where (p.fromAccount.user.id = :clientId
                  or p.toAccountNumber in (select a.accountNumber from Account a where a.user.id = :clientId))
             and (:fromDate is null or p.createdAt >= :fromDate)
             and (:toDate is null or p.createdAt <= :toDate)
             and (:minAmount is null or p.amount >= :minAmount)
             and (:maxAmount is null or p.amount <= :maxAmount)
             and (:status is null or p.status = :status)
           """)
    Page<Payment> findByUserAccountsWithFilters(
            @Param("clientId") Long clientId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            @Param("minAmount") BigDecimal minAmount,
            @Param("maxAmount") BigDecimal maxAmount,
            @Param("status") PaymentStatus status,
            Pageable pageable
    );
}
