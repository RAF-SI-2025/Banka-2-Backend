package rs.raf.banka2_bek.investmentfund.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rs.raf.banka2_bek.investmentfund.model.FundValueSnapshot;

import java.time.LocalDate;
import java.util.List;

public interface FundValueSnapshotRepository extends JpaRepository<FundValueSnapshot, Long> {

    List<FundValueSnapshot> findByFundIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
            Long fundId, LocalDate from, LocalDate to);

    boolean existsByFundIdAndSnapshotDate(Long fundId, LocalDate snapshotDate);
}
