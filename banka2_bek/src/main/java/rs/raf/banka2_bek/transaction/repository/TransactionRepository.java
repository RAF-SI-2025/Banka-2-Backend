package rs.raf.banka2_bek.transaction.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rs.raf.banka2_bek.payment.model.PaymentStatus;
import rs.raf.banka2_bek.transaction.dto.TransactionType;
import rs.raf.banka2_bek.transaction.model.Transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Page<Transaction> findByAccountUserId(Long clientId, Pageable pageable);

    @Query("""
           select t from Transaction t
           join t.payment p
           where t.account.user.id = :clientId
             and (:fromDate is null or t.createdAt >= :fromDate)
             and (:toDate is null or t.createdAt <= :toDate)
             and (:minAmount is null or p.amount >= :minAmount)
             and (:maxAmount is null or p.amount <= :maxAmount)
             and (:status is null or p.status = :status)
           """)
    Page<Transaction> findPaymentTransactionsByAccountUserIdAndFilters(
            @Param("clientId") Long clientId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            @Param("minAmount") BigDecimal minAmount,
            @Param("maxAmount") BigDecimal maxAmount,
            @Param("status") TransactionType type,
            Pageable pageable
    );
}
