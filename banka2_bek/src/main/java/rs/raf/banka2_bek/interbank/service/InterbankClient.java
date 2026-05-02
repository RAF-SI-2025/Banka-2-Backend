package rs.raf.banka2_bek.interbank.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import rs.raf.banka2_bek.interbank.config.InterbankProperties;
import rs.raf.banka2_bek.interbank.exception.InterbankExceptions;
import rs.raf.banka2_bek.interbank.protocol.ForeignBankId;
import rs.raf.banka2_bek.interbank.protocol.Message;
import rs.raf.banka2_bek.interbank.protocol.MessageType;
import rs.raf.banka2_bek.interbank.protocol.OtcNegotiation;
import rs.raf.banka2_bek.interbank.protocol.OtcOffer;
import rs.raf.banka2_bek.interbank.protocol.PublicStock;
import rs.raf.banka2_bek.interbank.protocol.UserInformation;

import java.util.List;

/*
================================================================================
 TODO — HTTP KLIJENT ZA SLANJE PORUKA PARTNERSKIM BANKAMA (PROTOKOL §2.9-2.11)
 Zaduzen: BE tim
 Spec ref: protokol §2.9 Message exchange, §2.10 Authentication,
           §2.11 Sending messages
--------------------------------------------------------------------------------
 SVRHA:
 Apstrakcija preko HTTP poziva ka drugim bankama. Svaki servis koji salje
 (TransactionExecutorService, OtcNegotiationService, InterbankRetryScheduler)
 poziva samo ovde metod `sendMessage(...)` — klijent resolvuje URL iz
 routingNumber-a, dodaje X-Api-Key header, timeout, serializuje u JSON,
 upise u InterbankMessage audit log i vrati odgovor.

 ENDPOINT (po protokolu §2.11):
   POST {partner.baseUrl}/interbank
   Content-Type: application/json
   X-Api-Key: {partner.outboundToken}

   Body: Message<Type> (vidi interbank.protocol.Message)

   Odgovor:
     202 Accepted     — primljeno ali nije zavrseno; pošiljač retry-uje kasnije
     200 OK           — primljeno + zavrseno; body = response (npr. TransactionVote
                        za NEW_TX, ili prazno za COMMIT_TX/ROLLBACK_TX)
     204 No Content   — primljeno + zavrseno bez tela
     ostalo / network — neuspeh; retry

 OBAVEZNE METODE:

   <Req, Resp> Resp sendMessage(int targetRoutingNumber,
                                 MessageType type,
                                 Message<Req> envelope,
                                 Class<Resp> responseType);
     Generic send. responseType je TransactionVote.class za NEW_TX, Void.class
     za COMMIT_TX/ROLLBACK_TX. Vraca Resp ili baca InterbankCommunicationException
     na 4xx/5xx/timeout (NE na 202 — to je legitimno "later").

   List<PublicStock> fetchPublicStocks(int routingNumber);
     GET {baseUrl}/public-stock — vidi §3.1.

   ForeignBankId postNegotiation(int routingNumber, OtcOffer offer);
     POST {baseUrl}/negotiations — vidi §3.2.

   void putCounterOffer(ForeignBankId negotiationId, OtcOffer offer);
     PUT {baseUrl}/negotiations/{rn}/{id} — vidi §3.3.

   OtcNegotiation getNegotiation(ForeignBankId negotiationId);
     GET {baseUrl}/negotiations/{rn}/{id} — vidi §3.4.

   void deleteNegotiation(ForeignBankId negotiationId);
     DELETE {baseUrl}/negotiations/{rn}/{id} — vidi §3.5.

   void acceptNegotiation(ForeignBankId negotiationId);
     GET {baseUrl}/negotiations/{rn}/{id}/accept — vidi §3.6.
     SINHRONO: vraca tek kad je transakcija COMMITTED.

   UserInformation getUserInfo(ForeignBankId userId);
     GET {baseUrl}/user/{rn}/{id} — vidi §3.7.

 PREPORUKA IMPLEMENTACIJE:
  - Koristi Spring RestClient (sinhroni) ili WebClient (async).
  - Jedan @Bean sa connection pool-om; per-partner URL i token resolvuju
    se pri svakom pozivu kroz BankRoutingService.resolvePartnerByRouting.
  - Timeout: 10s default, konfigurabilan u application.properties.
  - **NE radi retry na ovom nivou** — retry radi InterbankRetryScheduler
    citajuci message log (§2.9 reliability).
  - 202 nije error — zabelezi i vrati neki "PENDING" sentinel, scheduler
    cita iz log-a i retry-uje.

 IDEMPOTENCY:
  - InterbankMessageService.recordOutbound(idempotenceKey, body) PRE poziva.
  - Ako request fail-uje sa mreznom greskom, idempotenceKey ostaje isti
    pri retry-u (§2.9 at-most-once preko ponavljanja kljuca).

 GRESKE:
  - InterbankCommunicationException (RuntimeException) za 4xx/5xx/timeout.
  - 401 (autenticija) -> InterbankAuthException — partner ne prihvata nas
    token; trazi rotaciju.
================================================================================
*/
@Service
@RequiredArgsConstructor
public class InterbankClient {

    private final InterbankProperties interbankProperties;
    private final BankRoutingService bankRoutingService;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    // TODO: injectovati: ObjectMapper, InterbankMessageService (audit log),
    //   RestClient (configured sa timeout-om), MeterRegistry (metrics)

    public <Req, Resp> Resp sendMessage(int targetRoutingNumber,
                                         MessageType type,
                                         Message<Req> envelope,
                                         Class<Resp> responseType) {

        InterbankProperties.PartnerBank partnerBank = bankRoutingService.resolvePartnerByRouting(targetRoutingNumber)
                .orElseThrow( () -> new InterbankExceptions.InterbankProtocolException(
                        "Target routing number " + targetRoutingNumber + " could not be resolved."
                ));

        try {
            String serializedEnvelope = objectMapper.writeValueAsString(envelope);
            ResponseEntity<String> response = restClient
                    .post()
                    .uri(partnerBank.getBaseUrl() + "/interbank")
                    .header("X-Api-Key", partnerBank.getOutboundToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(serializedEnvelope)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() ||
                            status.is5xxServerError(), (request, res) -> {
                        if (res.getStatusCode().value() == 401)
                            throw new InterbankExceptions.InterbankAuthException(
                                    "Invalid API key for routing " + targetRoutingNumber + ".");
                        throw new InterbankExceptions.InterbankCommunicationException(
                                "HTTP " + res.getStatusCode().value() + " from routing number " + targetRoutingNumber
                        );
                    })
                    .toEntity(String.class);

            int statusCode = response.getStatusCode().value();

            if (statusCode == 202 || responseType == Void.class)
                return null;

            if (statusCode == 200) {
                try {
                    return objectMapper.readValue(response.getBody(), responseType);
                } catch (JsonProcessingException e) {
                    throw new InterbankExceptions.InterbankProtocolException(
                            "Response could not be deserialized " + e.getMessage() + "."
                    );
                }
            }
            return null;
        }
        catch (JsonProcessingException e) {
            throw new InterbankExceptions.InterbankProtocolException("Envelope serialization failed with message : " + e.getMessage());
        }
    }

    /**
     * §3.1 — GET /public-stock. Vraca sve javne ponude akcija u datoj banci.
     * Cherry-pick T5 (PR #71, stasadragovic) — implementacija iz Stasa-ine HEAD strane konflikta,
     * adaptirana za shared {@code restClient} bean iz {@link InterbankProperties} stila u sendMessage.
     */
    public List<PublicStock> fetchPublicStocks(int routingNumber) {
        InterbankProperties.PartnerBank partnerBank = resolvePartner(routingNumber);
        try {
            return restClient
                    .get()
                    .uri(partnerBank.getBaseUrl() + "/public-stock")
                    .header("X-Api-Key", partnerBank.getOutboundToken())
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            (req, res) -> {
                                if (res.getStatusCode().value() == 401)
                                    throw new InterbankExceptions.InterbankAuthException(
                                            "Invalid API key for routing " + routingNumber + ".");
                                throw new InterbankExceptions.InterbankCommunicationException(
                                        "HTTP " + res.getStatusCode().value()
                                                + " from routing number " + routingNumber + " on /public-stock");
                            })
                    .body(new ParameterizedTypeReference<List<PublicStock>>() {});
        } catch (InterbankExceptions.InterbankException e) {
            throw e;
        } catch (RestClientException e) {
            throw new InterbankExceptions.InterbankCommunicationException(
                    "Network error contacting routing " + routingNumber + " on /public-stock: " + e.getMessage(), e);
        }
    }

    /**
     * §3.2 — POST /negotiations. Inicira pregovor (prvi turn — kupac salje ponudu prodavcu).
     * Telo je {@link OtcOffer}; odgovor je {@link ForeignBankId} (id pregovora kod prodavca).
     */
    public ForeignBankId postNegotiation(int routingNumber, OtcOffer offer) {
        InterbankProperties.PartnerBank partnerBank = resolvePartner(routingNumber);
        try {
            return restClient
                    .post()
                    .uri(partnerBank.getBaseUrl() + "/negotiations")
                    .header("X-Api-Key", partnerBank.getOutboundToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(offer)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            (req, res) -> {
                                if (res.getStatusCode().value() == 401)
                                    throw new InterbankExceptions.InterbankAuthException(
                                            "Invalid API key for routing " + routingNumber + ".");
                                throw new InterbankExceptions.InterbankCommunicationException(
                                        "HTTP " + res.getStatusCode().value()
                                                + " from routing number " + routingNumber + " on /negotiations POST");
                            })
                    .body(ForeignBankId.class);
        } catch (InterbankExceptions.InterbankException e) {
            throw e;
        } catch (RestClientException e) {
            throw new InterbankExceptions.InterbankCommunicationException(
                    "Network error posting negotiation to routing " + routingNumber + ": " + e.getMessage(), e);
        }
    }

    /**
     * §3.3 — PUT /negotiations/{rn}/{id}. Kontraponuda. Pozivac mora biti strana cija je tura
     * (proverava se preko {@code lastModifiedBy} polja u OtcOffer-u).
     */
    public void putCounterOffer(ForeignBankId negotiationId, OtcOffer offer) {
        InterbankProperties.PartnerBank partnerBank = resolvePartner(negotiationId.routingNumber());
        try {
            restClient
                    .put()
                    .uri(partnerBank.getBaseUrl() + "/negotiations/{rn}/{id}",
                            negotiationId.routingNumber(), negotiationId.id())
                    .header("X-Api-Key", partnerBank.getOutboundToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(offer)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            (req, res) -> {
                                if (res.getStatusCode().value() == 401)
                                    throw new InterbankExceptions.InterbankAuthException(
                                            "Invalid API key for routing " + negotiationId.routingNumber() + ".");
                                throw new InterbankExceptions.InterbankCommunicationException(
                                        "HTTP " + res.getStatusCode().value()
                                                + " from negotiation " + negotiationId + " on PUT");
                            })
                    .toBodilessEntity();
        } catch (InterbankExceptions.InterbankException e) {
            throw e;
        } catch (RestClientException e) {
            throw new InterbankExceptions.InterbankCommunicationException(
                    "Network error on counter-offer to " + negotiationId + ": " + e.getMessage(), e);
        }
    }

    /**
     * §3.4 — GET /negotiations/{rn}/{id}. Cita trenutno stanje pregovora (autoritativna kopija je
     * uvek kod prodavceve banke; kupac poziva ovaj metod periodicno za sync).
     */
    public OtcNegotiation getNegotiation(ForeignBankId negotiationId) {
        InterbankProperties.PartnerBank partnerBank = resolvePartner(negotiationId.routingNumber());
        try {
            return restClient
                    .get()
                    .uri(partnerBank.getBaseUrl() + "/negotiations/{rn}/{id}",
                            negotiationId.routingNumber(), negotiationId.id())
                    .header("X-Api-Key", partnerBank.getOutboundToken())
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            (req, res) -> {
                                if (res.getStatusCode().value() == 401)
                                    throw new InterbankExceptions.InterbankAuthException(
                                            "Invalid API key for routing " + negotiationId.routingNumber() + ".");
                                throw new InterbankExceptions.InterbankCommunicationException(
                                        "HTTP " + res.getStatusCode().value()
                                                + " from negotiation " + negotiationId + " on GET");
                            })
                    .body(OtcNegotiation.class);
        } catch (InterbankExceptions.InterbankException e) {
            throw e;
        } catch (RestClientException e) {
            throw new InterbankExceptions.InterbankCommunicationException(
                    "Network error reading negotiation " + negotiationId + ": " + e.getMessage(), e);
        }
    }

    /**
     * §3.5 — DELETE /negotiations/{rn}/{id}. Zatvara pregovor (status: ne-prihvacen).
     * Idempotentno: ponovno DELETE-ovanje istog id-a vraca 204.
     */
    public void deleteNegotiation(ForeignBankId negotiationId) {
        InterbankProperties.PartnerBank partnerBank = resolvePartner(negotiationId.routingNumber());
        try {
            restClient
                    .delete()
                    .uri(partnerBank.getBaseUrl() + "/negotiations/{rn}/{id}",
                            negotiationId.routingNumber(), negotiationId.id())
                    .header("X-Api-Key", partnerBank.getOutboundToken())
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            (req, res) -> {
                                if (res.getStatusCode().value() == 401)
                                    throw new InterbankExceptions.InterbankAuthException(
                                            "Invalid API key for routing " + negotiationId.routingNumber() + ".");
                                throw new InterbankExceptions.InterbankCommunicationException(
                                        "HTTP " + res.getStatusCode().value()
                                                + " from negotiation " + negotiationId + " on DELETE");
                            })
                    .toBodilessEntity();
        } catch (InterbankExceptions.InterbankException e) {
            throw e;
        } catch (RestClientException e) {
            throw new InterbankExceptions.InterbankCommunicationException(
                    "Network error deleting negotiation " + negotiationId + ": " + e.getMessage(), e);
        }
    }

    /**
     * §3.6 — GET /negotiations/{rn}/{id}/accept. SINHRONO ceka da partnerova banka commit-uje
     * transakciju (200 = COMMITTED). Ovo je jedini sinhroni poziv u protokolu — partnerska
     * banka drzi konekciju otvorenu dok ne zavrsi 2PC interno.
     */
    public void acceptNegotiation(ForeignBankId negotiationId) {
        InterbankProperties.PartnerBank partnerBank = resolvePartner(negotiationId.routingNumber());
        try {
            restClient
                    .get()
                    .uri(partnerBank.getBaseUrl() + "/negotiations/{rn}/{id}/accept",
                            negotiationId.routingNumber(), negotiationId.id())
                    .header("X-Api-Key", partnerBank.getOutboundToken())
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            (req, res) -> {
                                if (res.getStatusCode().value() == 401)
                                    throw new InterbankExceptions.InterbankAuthException(
                                            "Invalid API key for routing " + negotiationId.routingNumber() + ".");
                                throw new InterbankExceptions.InterbankCommunicationException(
                                        "HTTP " + res.getStatusCode().value()
                                                + " from negotiation " + negotiationId + " on accept");
                            })
                    .toBodilessEntity();
        } catch (InterbankExceptions.InterbankException e) {
            throw e;
        } catch (RestClientException e) {
            throw new InterbankExceptions.InterbankCommunicationException(
                    "Network error accepting negotiation " + negotiationId + ": " + e.getMessage(), e);
        }
    }

    /**
     * §3.7 — GET /user/{rn}/{id}. Razresavanje friendly imena za opaque foreign user id.
     * Koristi se za UI prikaz (umesto opaque id-a). 404 nije fatalno — samo nedostatak imena.
     */
    public UserInformation getUserInfo(ForeignBankId userId) {
        InterbankProperties.PartnerBank partnerBank = resolvePartner(userId.routingNumber());
        try {
            return restClient
                    .get()
                    .uri(partnerBank.getBaseUrl() + "/user/{rn}/{id}",
                            userId.routingNumber(), userId.id())
                    .header("X-Api-Key", partnerBank.getOutboundToken())
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            (req, res) -> {
                                if (res.getStatusCode().value() == 401)
                                    throw new InterbankExceptions.InterbankAuthException(
                                            "Invalid API key for routing " + userId.routingNumber() + ".");
                                throw new InterbankExceptions.InterbankCommunicationException(
                                        "HTTP " + res.getStatusCode().value()
                                                + " from user " + userId + " on GET");
                            })
                    .body(UserInformation.class);
        } catch (InterbankExceptions.InterbankException e) {
            throw e;
        } catch (RestClientException e) {
            throw new InterbankExceptions.InterbankCommunicationException(
                    "Network error fetching user info " + userId + ": " + e.getMessage(), e);
        }
    }

    private InterbankProperties.PartnerBank resolvePartner(int routingNumber) {
        return bankRoutingService.resolvePartnerByRouting(routingNumber)
                .orElseThrow(() -> new InterbankExceptions.InterbankProtocolException(
                        "Target routing number " + routingNumber + " could not be resolved."
                ));
    }
}
