package rs.raf.banka2_bek.interbank.model;

/**
 * T12 — Spec ref: Protokol §3 OTC negotiation, Celina 5 (Nova) "OTC Trgovina".
 *
 * U inter-bank OTC pregovoru, sa nase strane (lokalne banke) ucestvuje
 * jedna od dve uloge: kupac ili prodavac. Druga strana je u partnerskoj
 * banci i identifikuje se kompozitnim ForeignBankId-jem.
 *
 * Pravilo turne (§3.3):
 *   - turn je buyer ako lastModifiedBy != buyerId
 *   - turn je seller ako lastModifiedBy != sellerId
 * Autoritativna kopija pregovora je uvek kod prodavceve banke (§3.2).
 */
public enum InterbankPartyType {
    /** Mi smo kupac opcije; prodavac je u partnerskoj banci. */
    BUYER,
    /** Mi smo prodavac (autoritativni vlasnik pregovora); kupac je u partnerskoj banci. */
    SELLER
}
