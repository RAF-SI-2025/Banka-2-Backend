package rs.raf.banka2_bek.assistant.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import rs.raf.banka2_bek.assistant.model.AssistantConversation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssistantConversationRepository extends JpaRepository<AssistantConversation, Long> {

    Optional<AssistantConversation> findByConversationUuid(UUID conversationUuid);

    /**
     * EAGER fetch messages za chat flow — runChat radi na async thread-u
     * van transakcionog konteksta, pa lazy load-ovanje conv.getMessages()
     * baca {@code LazyInitializationException} pri drugoj poruci u istoj
     * konverzaciji. FETCH JOIN obezbedi da se kolekcija ucita zajedno sa
     * konverzacijom dok je transakcija jos otvorena.
     */
    @Query("SELECT DISTINCT c FROM AssistantConversation c LEFT JOIN FETCH c.messages WHERE c.conversationUuid = :uuid")
    Optional<AssistantConversation> findByConversationUuidWithMessages(@Param("uuid") UUID uuid);

    List<AssistantConversation> findByUserIdAndUserRoleAndDeletedAtIsNullOrderByUpdatedAtDesc(
            Long userId, String userRole);

    @Modifying
    @Query("UPDATE AssistantConversation c SET c.deletedAt = :now WHERE c.deletedAt IS NULL AND c.updatedAt < :cutoff")
    int softDeleteOlderThan(@Param("now") LocalDateTime now, @Param("cutoff") LocalDateTime cutoff);
}
