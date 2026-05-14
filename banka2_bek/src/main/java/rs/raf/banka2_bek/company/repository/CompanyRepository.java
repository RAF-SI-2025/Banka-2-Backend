package rs.raf.banka2_bek.company.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rs.raf.banka2_bek.company.model.Company;

import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    Optional<Company> findByTaxNumber(String taxNumber);
    Optional<Company> findByRegistrationNumber(String registrationNumber);
    boolean existsByTaxNumber(String taxNumber);
    boolean existsByRegistrationNumber(String registrationNumber);
    Optional<Company> findByIsStateTrue();
    Optional<Company> findByIsBankTrue();
}
