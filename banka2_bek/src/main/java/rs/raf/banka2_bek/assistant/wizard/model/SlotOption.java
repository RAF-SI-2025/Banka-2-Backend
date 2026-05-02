package rs.raf.banka2_bek.assistant.wizard.model;

/**
 * One option in a CHOICE-type slot. FE renders as button:
 * <pre>
 *   [ {label}        ]
 *     {hint}
 * </pre>
 *
 * @param value back-end value sent in slot-fill request (account number, ticker, fund id, ...)
 * @param label primary user-facing text (e.g. "Tekući RSD — 220...678 (1.250 RSD)")
 * @param hint  optional secondary text shown smaller (e.g. "Dovoljno sredstava za placanje")
 */
public record SlotOption(String value, String label, String hint) {
    public static SlotOption of(String value, String label) {
        return new SlotOption(value, label, null);
    }
}
