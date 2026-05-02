package rs.raf.banka2_bek.assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Snimak FE konteksta — koja stranica, kratak ljudski opis, poslednje akcije.
 * Salje ga FE u POST /assistant/chat zajedno sa user porukom.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageContextDto {
    private String route;
    private String pageName;
    private String uiSummary;
    private List<String> lastActions;
}
