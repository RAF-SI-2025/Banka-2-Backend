package rs.raf.banka2_bek.margin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import rs.raf.banka2_bek.margin.dto.MarginAccountCheckDto;
import rs.raf.banka2_bek.margin.model.MarginAccount;
import rs.raf.banka2_bek.margin.model.MarginAccountStatus;

import java.util.List;

@Repository
public interface MarginAccountRepository extends JpaRepository<MarginAccount, Long> {

    /**
     * Pronalazi sve margin racune za datog korisnika
     */
    List<MarginAccount> findByUserId(Long userId);

    /**
     * Pronalazi sve margin racune sa datim statusom
     */
    List<MarginAccount> findByStatus(MarginAccountStatus status);

    /**
     * Pronalazi margin racun vezan za dati obicni racun
     */
    List<MarginAccount> findByAccountId(Long accountId);

    /**
     * Pronalazi sve margin racune koji treba da se blokiraju, i vraca podatke racuna + email od vlasnika racuna
     */
    @Query(value = """
                 SELECT\s
                     account.id AS marginAccountId,
                     client.email AS ownerEmail,
                     account.maintenance_margin AS maintenanceMargin,
                     account.initial_margin AS initialMargin
                 FROM margin_accounts account
                 JOIN clients client ON account.user_id = client.id
                 WHERE account.status = :active
                   AND account.maintenance_margin > account.initial_margin
            \s""", nativeQuery = true)
    List<MarginAccountCheckDto> findAccountsForMarginCheck(
            @Param("active") String active
    );

    /**
     * Postavlja status racuna koji treba da se blokiraju u "BLOCKED"
     */
    @Modifying
    @Query(value = "UPDATE margin_accounts SET status = :blocked WHERE maintenance_margin > initial_margin", nativeQuery = true)
    void blockAccountsWhereMaintenanceExceedsInitial(@Param("blocked") String blocked);
}
