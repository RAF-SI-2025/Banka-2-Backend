package rs.raf.banka2_bek.interbank.service;

import org.springframework.stereotype.Service;

/**
 * Inter-bank OTC pregovori — outbound i inbound logiku dodaju T2 i T3.
 *
 * INTER-BANK OTC NEGOTIATION ENTITETI — sema polja (mapiranje na protokol §3 i §2.3):
 *
 * InterbankOtcNegotiation -> tabela interbank_otc_negotiations
 *   id                                                    : Long, PK
 *   negotiationRoutingNumber + negotiationOpaqueId        : ForeignBankId autoritativnog
 *                                                           pregovora (obicno banka prodavca)
 *   stockTicker                                           : string — StockDescription.ticker
 *   settlementInstant                                     : Instant (UTC) — ISO-8601 settlementDate
 *   pricePerUnitAmount + pricePerUnitCurrency             : MonetaryValue pricePerUnit
 *   premiumAmount + premiumCurrency                       : MonetaryValue premium
 *   amount                                                : int — broj akcija
 *   buyerRoutingNumber + buyerOpaqueId                    : buyerId
 *   sellerRoutingNumber + sellerOpaqueId                  : sellerId
 *   lastModifiedByRoutingNumber + lastModifiedByOpaqueId  : lastModifiedBy
 *   ongoing                                               : boolean — protokol isOngoing
 *   updatedAt                                             : poslednja izmena ponude
 *
 * InterbankOtcContract -> tabela interbank_otc_contracts
 *   id                                                    : Long, PK
 *   negotiation                                           : FK na InterbankOtcNegotiation
 *   negotiationRoutingNumber + negotiationOpaqueId        : kopija negotiationId
 *                                                           (OptionDescription.negotiationId)
 *   stockTicker, pricePerUnit*, settlementInstant, shareAmount
 *   premiumAmount + premiumCurrency                       : snapshot u trenutku sklapanja
 *   status                                                : ACTIVE | EXERCISED | EXPIRED | CANCELLED
 *   createdAt
 *
 * Intra-bank OTC entiteti se ne diraju — ovi zapisi su samo za medjubankarski sloj
 * sa neprozirnim ID-jevima.
 */
@Service
public class OtcNegotiationService {

    // TODO T2 outbound: outbound metode — slanje OTC poruka drugoj banci
    //                   (POST /negotiations, PUT /negotiations/{rn}/{id} kontraponuda,
    //                   DELETE close, GET accept).
    //                   Injektovati InterbankOtcNegotiationRepository i
    //                   InterbankOtcContractRepository ovde kada krenete sa
    //                   implementacijom (preporuceno: @RequiredArgsConstructor).
    //
    // TODO T3 inbound: inbound endpoint dispatch — kreiranje pregovora na nasoj strani
    //                  kao prodavac, validacija turn-a (lastModifiedBy != trenutni
    //                  posiljalac), close, accept (formira 4-postavku po §3.6 protokola
    //                  i prosledjuje T1 transaction executoru).
}
