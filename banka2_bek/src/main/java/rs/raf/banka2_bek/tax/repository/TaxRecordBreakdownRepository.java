package rs.raf.banka2_bek.tax.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import rs.raf.banka2_bek.tax.model.TaxRecordBreakdown;

import java.util.List;

@Repository
public interface TaxRecordBreakdownRepository extends JpaRepository<TaxRecordBreakdown, Long> {

    List<TaxRecordBreakdown> findByTaxRecordIdOrderByTaxOwedDesc(Long taxRecordId);

    void deleteByTaxRecordId(Long taxRecordId);
}
