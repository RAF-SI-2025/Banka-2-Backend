package rs.raf.banka2_bek.assistant.wizard.dto;

import lombok.Data;

@Data
public class SlotSelectRequestDto {
    /** Slot name being filled — must match wizard's currentSlot. */
    private String slotName;
    /** Selected value (option.value for CHOICE, raw input for TEXT/NUMBER, "YES"/"NO" for CONFIRM). */
    private String value;
}
