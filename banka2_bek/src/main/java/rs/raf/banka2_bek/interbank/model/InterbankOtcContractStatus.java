package rs.raf.banka2_bek.interbank.model;

/**
 * T12 — Stanje inter-bank OTC opcionog ugovora.
 *
 * Spec ref: Protokol §2.7.2 Options; Celina 5 (Nova) — Izvrsavanje kupoprodaje
 * (SAGA pattern).
 *
 * Ugovor se kreira kada druga strana prihvati (§3.6) — premium se odmah
 * placa kupcu->prodavcu, hartije se rezervisu kod prodavca. Ugovor je
 * EXERCISED kad kupac iskoristi opciju pre `settlementDate`-a, EXPIRED
 * inace (rezervacija hartija se vraca prodavcu).
 */
public enum InterbankOtcContractStatus {
    /** Ugovor vazeci, kupac jos nije iskoristio opciju, settlementDate nije prosao. */
    ACTIVE,
    /** Kupac iskoristio opciju i transakcija je commitovana. Vidi protokol §2.7.2. */
    EXERCISED,
    /** Settlement datum prosao bez iskoriscenja — rezervacija se oslobadja. */
    EXPIRED
}
