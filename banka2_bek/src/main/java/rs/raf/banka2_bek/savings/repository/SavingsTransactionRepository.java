package rs.raf.banka2_bek.savings.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rs.raf.banka2_bek.savings.entity.SavingsTransaction;

import java.util.List;

public interface SavingsTransactionRepository extends JpaRepository<SavingsTransaction, Long> {
    List<SavingsTransaction> findByDepositIdOrderByCreatedAtDesc(Long depositId);
}
