package rs.raf.banka2_bek.assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import rs.raf.banka2_bek.assistant.model.AssistantMessageRole;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageDto {
    private Long id;
    private AssistantMessageRole role;
    private String content;
    private String pageRoute;
    private String pageName;
    private LocalDateTime createdAt;
}
