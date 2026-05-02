package rs.raf.banka2_bek.interbank.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import rs.raf.banka2_bek.interbank.config.InterbankProperties;
import rs.raf.banka2_bek.interbank.exception.InterbankExceptions;
import rs.raf.banka2_bek.interbank.protocol.Message;
import rs.raf.banka2_bek.interbank.protocol.MessageType;

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

    public java.util.List<rs.raf.banka2_bek.interbank.protocol.PublicStock> fetchPublicStocks(int routingNumber) {
        // TODO: §3.1 GET /public-stock
        throw new UnsupportedOperationException("TODO: implementirati InterbankClient.fetchPublicStocks");
    }

    public rs.raf.banka2_bek.interbank.protocol.ForeignBankId postNegotiation(
            int routingNumber, rs.raf.banka2_bek.interbank.protocol.OtcOffer offer) {
        // TODO: §3.2 POST /negotiations
        throw new UnsupportedOperationException("TODO: implementirati InterbankClient.postNegotiation");
    }

    public void putCounterOffer(rs.raf.banka2_bek.interbank.protocol.ForeignBankId negotiationId,
                                 rs.raf.banka2_bek.interbank.protocol.OtcOffer offer) {
        // TODO: §3.3 PUT /negotiations/{rn}/{id}
        throw new UnsupportedOperationException("TODO: implementirati InterbankClient.putCounterOffer");
    }

    public rs.raf.banka2_bek.interbank.protocol.OtcNegotiation getNegotiation(
            rs.raf.banka2_bek.interbank.protocol.ForeignBankId negotiationId) {
        // TODO: §3.4 GET /negotiations/{rn}/{id}
        throw new UnsupportedOperationException("TODO: implementirati InterbankClient.getNegotiation");
    }

    public void deleteNegotiation(rs.raf.banka2_bek.interbank.protocol.ForeignBankId negotiationId) {
        // TODO: §3.5 DELETE /negotiations/{rn}/{id}
        throw new UnsupportedOperationException("TODO: implementirati InterbankClient.deleteNegotiation");
    }

    public void acceptNegotiation(rs.raf.banka2_bek.interbank.protocol.ForeignBankId negotiationId) {
        // TODO: §3.6 GET /negotiations/{rn}/{id}/accept (sinhrono — ceka COMMITTED)
        throw new UnsupportedOperationException("TODO: implementirati InterbankClient.acceptNegotiation");
    }

    public rs.raf.banka2_bek.interbank.protocol.UserInformation getUserInfo(
            rs.raf.banka2_bek.interbank.protocol.ForeignBankId userId) {
        // TODO: §3.7 GET /user/{rn}/{id}
        throw new UnsupportedOperationException("TODO: implementirati InterbankClient.getUserInfo");
    }
}
