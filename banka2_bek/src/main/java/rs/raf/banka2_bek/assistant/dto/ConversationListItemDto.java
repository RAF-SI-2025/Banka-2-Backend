package rs.raf.banka2_bek.assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationListItemDto {
    private UUID conversationUuid;
    private String title;
    private long messageCount;
    private LocalDateTime updatedAt;
}
