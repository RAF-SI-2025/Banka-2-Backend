package rs.raf.banka2_bek.assistant.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import rs.raf.banka2_bek.assistant.model.AssistantMessage;

import java.util.List;

@Repository
public interface AssistantMessageRepository extends JpaRepository<AssistantMessage, Long> {

    List<AssistantMessage> findByConversationIdOrderByIdAsc(Long conversationId);

    long countByConversationId(Long conversationId);

    void deleteByConversationId(Long conversationId);
}
