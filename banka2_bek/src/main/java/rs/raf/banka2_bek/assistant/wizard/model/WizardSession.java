package rs.raf.banka2_bek.assistant.wizard.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * In-memory wizard session — holds collected slot values across multiple
 * user-agent turns until all slots are filled and AgentAction can be created.
 *
 * Stored in {@code WizardService}'s Caffeine cache with 10-minute TTL.
 */
@Getter
public class WizardSession {
    private final UUID wizardId;
    private final Long userId;
    private final String userRole;
    private final UUID conversationUuid;
    private final WizardTemplate template;
    private final Map<String, Object> filledSlots = new LinkedHashMap<>();
    /**
     * Phase 4.5+ cosmetic: za svaki popunjen slot pamtimo human-readable label
     * koji ide u "Previous selections" panel. Za CHOICE slot je to
     * {@code SlotOption.label} (npr. "Tekuci RSD — 222...8912"); za TEXT/NUMBER
     * je to sirov user input.
     */
    private final Map<String, String> filledLabels = new LinkedHashMap<>();
    @Setter
    private int currentSlotIndex;
    @Setter
    private Instant lastTouched;
    private final Instant createdAt;

    public WizardSession(Long userId, String userRole, UUID conversationUuid, WizardTemplate template) {
        this.wizardId = UUID.randomUUID();
        this.userId = userId;
        this.userRole = userRole;
        this.conversationUuid = conversationUuid;
        this.template = template;
        this.currentSlotIndex = 0;
        this.lastTouched = Instant.now();
        this.createdAt = this.lastTouched;
    }

    public SlotDefinition currentSlot() {
        if (currentSlotIndex >= template.slots().size()) return null;
        return template.slots().get(currentSlotIndex);
    }

    public boolean isComplete() {
        return currentSlotIndex >= template.slots().size();
    }

    public void advance() {
        currentSlotIndex++;
        lastTouched = Instant.now();
    }

    public void recordSlot(String name, Object value) {
        filledSlots.put(name, value);
        lastTouched = Instant.now();
    }

    /**
     * Cosmetic: pamti label uz raw value tako da "Previous selections" panel
     * prikaze human-readable string ("Milica Nikolic" umesto "222...8913").
     */
    public void recordSlotWithLabel(String name, Object value, String label) {
        filledSlots.put(name, value);
        if (label != null && !label.isBlank()) filledLabels.put(name, label);
        lastTouched = Instant.now();
    }

    public String labelFor(String slotName) {
        return filledLabels.get(slotName);
    }

    public boolean ownedBy(Long userId, String userRole) {
        return this.userId.equals(userId) && this.userRole.equalsIgnoreCase(userRole);
    }
}
