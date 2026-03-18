package rs.raf.banka2_bek.account.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountStatus;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {
    List<Account> findByClientIdAndStatusOrderByAvailableBalanceDesc(Long clientId, AccountStatus status);

    Optional<Account> findByAccountNumber(String accountNumber);
}
