package rs.raf.banka2_bek.account.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import rs.raf.banka2_bek.account.model.Account;

import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    // Pomoćna metoda ako ti ikada zatreba pretraga po broju računa
    Optional<Account> findByAccountNumber(String accountNumber);
}
