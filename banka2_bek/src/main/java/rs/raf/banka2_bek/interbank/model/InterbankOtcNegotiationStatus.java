package rs.raf.banka2_bek.interbank.model;

/**
 * T12 — Stanje inter-bank OTC pregovora.
 *
 * Spec ref: Protokol §3 OTC negotiation; Celina 5 (Nova) — Pregovaranje.
 *
 * `isOngoing` polje na entitetu je derivat statusa: true iff status == ACTIVE.
 * Drzimo ga kao odvojenu kolonu radi protokol §3.4 odgovora (`OtcNegotiation`
 * tip nosi `isOngoing` boolean) i da se izbegnu enum-prefix mismatch-evi
 * kad partner banka chita state preko GET /negotiations/{rn}/{id}.
 */
public enum InterbankOtcNegotiationStatus {
    /** Pregovor u toku — jedna strana ceka odgovor druge. */
    ACTIVE,
    /** Druga strana je prihvatila — kreiran je InterbankOtcContract. */
    ACCEPTED,
    /** Strana koja nije bila na potezu odustala (DELETE /negotiations, §3.5). */
    DECLINED,
    /** Pregovor zatvoren bez sklapanja ugovora (npr. timeout, admin akcija). */
    CLOSED
}
