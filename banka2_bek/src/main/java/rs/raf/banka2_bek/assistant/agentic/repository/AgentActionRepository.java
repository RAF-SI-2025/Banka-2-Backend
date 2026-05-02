package rs.raf.banka2_bek.assistant.agentic.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import rs.raf.banka2_bek.assistant.agentic.model.AgentAction;
import rs.raf.banka2_bek.assistant.agentic.model.AgentActionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AgentActionRepository extends JpaRepository<AgentAction, Long> {

    Optional<AgentAction> findByActionUuid(String actionUuid);

    List<AgentAction> findByUserIdAndUserRoleAndStatusOrderByCreatedAtDesc(
            Long userId, String userRole, AgentActionStatus status);

    /**
     * Pronalazi sve PENDING akcije starije od cutoff datuma — kandidati za
     * EXPIRED tranziciju u scheduler-u.
     */
    @Query("SELECT a FROM AgentAction a WHERE a.status = :status AND a.expiresAt < :cutoff")
    List<AgentAction> findStalePending(@Param("status") AgentActionStatus status,
                                        @Param("cutoff") LocalDateTime cutoff);

    /**
     * Bulk-update za scheduler — markira EXPIRED sve ne-resolvovane akcije
     * sa proslim TTL-om u jednom SQL update-u.
     */
    @Modifying
    @Query("UPDATE AgentAction a SET a.status = :newStatus, a.resolvedAt = :now " +
            "WHERE a.status = :oldStatus AND a.expiresAt < :now")
    int markExpired(@Param("oldStatus") AgentActionStatus oldStatus,
                     @Param("newStatus") AgentActionStatus newStatus,
                     @Param("now") LocalDateTime now);

    /**
     * Phase 5 polish — pun audit istorijat za usera (sve resolved akcije).
     * Vraca poslednjih 50, sortirano po createdAt desc.
     */
    @Query("SELECT a FROM AgentAction a WHERE a.userId = :userId AND a.userRole = :userRole " +
            "AND a.status <> :pendingStatus ORDER BY a.createdAt DESC")
    List<AgentAction> findResolvedHistory(@Param("userId") Long userId,
                                           @Param("userRole") String userRole,
                                           @Param("pendingStatus") AgentActionStatus pendingStatus);
}
