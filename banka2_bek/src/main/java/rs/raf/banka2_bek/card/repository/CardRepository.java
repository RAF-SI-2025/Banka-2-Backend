package rs.raf.banka2_bek.card.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rs.raf.banka2_bek.card.model.Card;

import java.util.List;
import java.util.Optional;

public interface CardRepository extends JpaRepository<Card, Long> {
    List<Card> findByClientId(Long clientId);
    List<Card> findByAccountId(Long accountId);
    Optional<Card> findByCardNumber(String cardNumber);
    long countByAccountIdAndStatusNot(Long accountId, rs.raf.banka2_bek.card.model.CardStatus status);
    long countByAccountIdAndClientIdAndStatusNot(Long accountId, Long clientId, rs.raf.banka2_bek.card.model.CardStatus status);

    /**
     * Aktivne (non-DEACTIVATED) kartice za dati (account, client) par.
     * P2.3 — koristi se za alokaciju slota 1/2 pre kreiranja nove kartice.
     */
    List<Card> findByAccountIdAndClientIdAndStatusNot(
            Long accountId, Long clientId, rs.raf.banka2_bek.card.model.CardStatus status);

    /**
     * Aktivne (non-DEACTIVATED) kartice po accountId — koristi se za
     * alokaciju slota za licne racune (svi klijenti dele isti slot prostor).
     */
    List<Card> findByAccountIdAndStatusNot(
            Long accountId, rs.raf.banka2_bek.card.model.CardStatus status);
}
