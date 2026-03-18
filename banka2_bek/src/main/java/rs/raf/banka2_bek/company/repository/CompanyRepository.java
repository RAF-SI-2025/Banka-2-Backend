package rs.raf.banka2_bek.company.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import rs.raf.banka2_bek.company.model.Company;

import java.util.Optional;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {

    // Pronalaženje firme po PIB-u (Tax ID)
    Optional<Company> findByTaxNumber(String taxNumber);

    // Pronalaženje firme po matičnom broju (Registration Number)
    Optional<Company> findByRegistrationNumber(String registrationNumber);

    // Provera da li firma sa tim PIB-om već postoji (korisno pri validaciji)
    boolean existsByTaxNumber(String taxNumber);
}
