package rs.raf.banka2_bek.assistant.agentic.model;

/**
 * Status agentic action-a kroz lifecycle.
 *
 * <pre>
 *  PENDING ──confirm──▶ EXECUTED  (uspesno izvrseno)
 *     │   ──confirm──▶ FAILED    (BE service rejected — npr. nedovoljno sredstava)
 *     │   ──reject───▶ REJECTED  (user kliknuo Odbaci)
 *     │   ──TTL───────▶ EXPIRED   (auto, scheduler 5min)
 * </pre>
 *
 * Final statusi (REJECTED/EXPIRED/EXECUTED/FAILED) su immutable — ne moze
 * se confirm-ovati action koji vec nije PENDING.
 */
public enum AgentActionStatus {
    PENDING,
    EXECUTED,
    FAILED,
    REJECTED,
    EXPIRED
}
