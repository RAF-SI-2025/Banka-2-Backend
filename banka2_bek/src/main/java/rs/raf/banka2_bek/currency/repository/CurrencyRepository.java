package rs.raf.banka2_bek.currency.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import rs.raf.banka2_bek.currency.model.Currency;

import java.util.Optional;


@Repository
public interface CurrencyRepository extends JpaRepository<Currency, Long> {

}
