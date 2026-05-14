package rs.raf.banka2_bek.investmentfund.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.investmentfund.model.InvestmentFund;

import java.util.List;
import java.util.Optional;

public interface InvestmentFundRepository extends JpaRepository<InvestmentFund, Long> {

    Optional<InvestmentFund> findByName(String name);

    List<InvestmentFund> findByManagerEmployeeId(Long managerEmployeeId);

    List<InvestmentFund> findByActiveTrueOrderByNameAsc();

    @Modifying
    @Transactional
    @Query("update InvestmentFund f set f.managerEmployeeId = :newManagerId " +
           "where f.managerEmployeeId = :oldManagerId")
    int reassignManager(Long oldManagerId, Long newManagerId);
}
