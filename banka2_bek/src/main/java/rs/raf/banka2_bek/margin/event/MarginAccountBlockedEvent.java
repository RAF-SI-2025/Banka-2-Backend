package rs.raf.banka2_bek.margin.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MarginAccountBlockedEvent {
    private String email;
    private String maintenanceMargin;
    private String initialMargin;
    private String deficit;
}
