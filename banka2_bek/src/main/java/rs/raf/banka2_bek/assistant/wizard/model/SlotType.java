package rs.raf.banka2_bek.assistant.wizard.model;

/**
 * Type of slot input — determines how FE renders the prompt.
 *
 * <ul>
 *   <li>{@link #CHOICE} — single-choice from a list of options (renders as buttons)</li>
 *   <li>{@link #TEXT} — free-form text input</li>
 *   <li>{@link #NUMBER} — numeric input (BigDecimal/integer parsed serverside)</li>
 *   <li>{@link #CONFIRM} — Da/Ne confirmation</li>
 * </ul>
 */
public enum SlotType {
    CHOICE,
    TEXT,
    NUMBER,
    CONFIRM
}
