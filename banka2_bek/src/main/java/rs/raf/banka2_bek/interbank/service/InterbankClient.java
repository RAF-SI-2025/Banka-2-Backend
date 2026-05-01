package rs.raf.banka2_bek.interbank.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import rs.raf.banka2_bek.interbank.config.InterbankProperties;
import rs.raf.banka2_bek.interbank.exception.InterbankExceptions.InterbankAuthException;
import rs.raf.banka2_bek.interbank.exception.InterbankExceptions.InterbankCommunicationException;
import rs.raf.banka2_bek.interbank.protocol.*;

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
@Slf4j
@Service
public class InterbankClient {

    private final InterbankProperties properties;
    private final BankRoutingService routing;
    private final InterbankMessageService messageService;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public InterbankClient(InterbankProperties properties,
                           BankRoutingService routing,
                           InterbankMessageService messageService,
                           RestClient restClient,
                           ObjectMapper objectMapper) {
        this.properties = properties;
        this.routing = routing;
        this.messageService = messageService;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    // =========================================================================
    // §2.11 – Generički send (NEW_TX / COMMIT_TX / ROLLBACK_TX)
    // =========================================================================

    public <Req, Resp> Resp sendMessage(int targetRoutingNumber,
                                        MessageType type,
                                        Message<Req> envelope,
                                        Class<Resp> responseType) {

        InterbankProperties.PartnerBank partner = resolvePartnerOrThrow(targetRoutingNumber);
        String url = partner.getBaseUrl() + "/interbank";
        IdempotenceKey key = envelope.idempotenceKey();

        String bodyJson = serializeQuietly(envelope);

        messageService.recordOutbound(
                key,
                targetRoutingNumber,
                toServiceMessageType(type),
                bodyJson
        );

        log.info("[InterbankClient] Šaljem {} → routing={} key={}/{}",
                type, targetRoutingNumber,
                key.routingNumber(), key.locallyGeneratedKey());

        try {
            ResponseEntity<Resp> response = restClient.post()
                    .uri(url)
                    .headers(h -> h.set("X-Api-Key", partner.getOutboundToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(envelope)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        if (res.getStatusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED)) {
                            throw new InterbankAuthException(
                                    "Partner odbio naš token za routing=" + targetRoutingNumber);
                        }
                        throw new InterbankCommunicationException(
                                "4xx greška od routing=" + targetRoutingNumber);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new InterbankCommunicationException(
                                "5xx greška od routing=" + targetRoutingNumber);
                    })
                    .toEntity(responseType);

            int statusCode = response.getStatusCode().value();

            if (statusCode == 202) {
                log.info("[InterbankClient] 202 Accepted – PENDING. key={}/{}",
                        key.routingNumber(), key.locallyGeneratedKey());
                return null;
            }

            if (statusCode == 200) {
                messageService.markOutboundSent(key, statusCode, serializeQuietly(response.getBody()));
                return response.getBody();
            }

            if (statusCode == 204) {
                messageService.markOutboundSent(key, statusCode, null);
            }

            return null;

        } catch (InterbankAuthException | InterbankCommunicationException e) {
            messageService.markOutboundFailed(key, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.warn("[InterbankClient] Network greška → routing={}: {}", targetRoutingNumber, e.getMessage());
            messageService.markOutboundFailed(key, e.getMessage());
            throw new InterbankCommunicationException(
                    "Network greška ka routing=" + targetRoutingNumber + ": " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // §3.1 – Lista javnih akcija
    // =========================================================================

    public List<PublicStock> fetchPublicStocks(int routingNumber) {
        InterbankProperties.PartnerBank partner = resolvePartnerOrThrow(routingNumber);
        String url = partner.getBaseUrl() + "/public-stock";

        try {
            return  RestClient.builder()
                    .baseUrl(url)
                    .defaultHeader("X-Api-Key", partner.getOutboundToken())
                    .build()
                    .get()
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        if (res.getStatusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED)) {
                            throw new InterbankAuthException("401 pri fetchPublicStocks routing=" + routingNumber);
                        }
                        throw new InterbankCommunicationException("4xx greška pri fetchPublicStocks routing=" + routingNumber);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new InterbankCommunicationException("5xx greška pri fetchPublicStocks routing=" + routingNumber);
                    })
                    .body(new ParameterizedTypeReference<>() {});

        } catch (InterbankAuthException | InterbankCommunicationException e) {
            throw e;
        } catch (Exception e) {
            throw new InterbankCommunicationException("Network greška pri fetchPublicStocks routing=" + routingNumber, e);
        }
    }

    // =========================================================================
    // §3.2 – Kreiranje pregovora
    // =========================================================================

    public ForeignBankId postNegotiation(int routingNumber, OtcOffer offer) {
        InterbankProperties.PartnerBank partner = resolvePartnerOrThrow(routingNumber);
        String url = partner.getBaseUrl() + "/negotiations";

        try {
            return RestClient.builder()
                    .baseUrl(url)
                    .defaultHeader("X-Api-Key", partner.getOutboundToken())
                    .build()
                    .post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(offer)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        if (res.getStatusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED)) {
                            throw new InterbankAuthException("401 pri postNegotiation routing=" + routingNumber);
                        }
                        throw new InterbankCommunicationException("4xx greška pri postNegotiation routing=" + routingNumber);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new InterbankCommunicationException("5xx greška pri postNegotiation routing=" + routingNumber);
                    })
                    .body(ForeignBankId.class);

        } catch (InterbankAuthException | InterbankCommunicationException e) {
            throw e;
        } catch (Exception e) {
            throw new InterbankCommunicationException("Network greška pri postNegotiation routing=" + routingNumber, e);
        }
    }

    // =========================================================================
    // §3.3 – Kontraponuda
    // =========================================================================

    public void putCounterOffer(ForeignBankId negotiationId, OtcOffer offer) {
        InterbankProperties.PartnerBank partner = resolvePartnerOrThrow(negotiationId.routingNumber());
        String url = buildNegotiationUrl(partner.getBaseUrl(), negotiationId);

        try {
            RestClient.builder()
                    .baseUrl(url)
                    .defaultHeader("X-Api-Key", partner.getOutboundToken())
                    .build()
                    .put()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(offer)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        if (res.getStatusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED)) {
                            throw new InterbankAuthException("401 pri putCounterOffer " + negotiationId);
                        }
                        throw new InterbankCommunicationException("4xx greška pri putCounterOffer " + negotiationId);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new InterbankCommunicationException("5xx greška pri putCounterOffer " + negotiationId);
                    })
                    .toBodilessEntity();

        } catch (InterbankAuthException | InterbankCommunicationException e) {
            throw e;
        } catch (Exception e) {
            throw new InterbankCommunicationException("Network greška pri putCounterOffer " + negotiationId, e);
        }
    }

    // =========================================================================
    // §3.4 – Čitanje pregovora
    // =========================================================================

    public OtcNegotiation getNegotiation(ForeignBankId negotiationId) {
        InterbankProperties.PartnerBank partner = resolvePartnerOrThrow(negotiationId.routingNumber());
        String url = buildNegotiationUrl(partner.getBaseUrl(), negotiationId);

        try {
            return  RestClient.builder()
                    .baseUrl(url)
                    .defaultHeader("X-Api-Key", partner.getOutboundToken())
                    .build()
                    .get()
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        if (res.getStatusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED)) {
                            throw new InterbankAuthException("401 pri getNegotiation " + negotiationId);
                        }
                        throw new InterbankCommunicationException("4xx greška pri getNegotiation " + negotiationId);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new InterbankCommunicationException("5xx greška pri getNegotiation " + negotiationId);
                    })
                    .body(OtcNegotiation.class);

        } catch (InterbankAuthException | InterbankCommunicationException e) {
            throw e;
        } catch (Exception e) {
            throw new InterbankCommunicationException("Network greška pri getNegotiation " + negotiationId, e);
        }
    }

    // =========================================================================
    // §3.5 – Zatvaranje pregovora
    // =========================================================================

    public void deleteNegotiation(ForeignBankId negotiationId) {
        InterbankProperties.PartnerBank partner = resolvePartnerOrThrow(negotiationId.routingNumber());
        String url = buildNegotiationUrl(partner.getBaseUrl(), negotiationId);

        try {
            RestClient.builder()
                    .baseUrl(url)
                    .defaultHeader("X-Api-Key", partner.getOutboundToken())
                    .build()
                    .delete()
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        if (res.getStatusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED)) {
                            throw new InterbankAuthException("401 pri deleteNegotiation " + negotiationId);
                        }
                        throw new InterbankCommunicationException("4xx greška pri deleteNegotiation " + negotiationId);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new InterbankCommunicationException("5xx greška pri deleteNegotiation " + negotiationId);
                    })
                    .toBodilessEntity();

        } catch (InterbankAuthException | InterbankCommunicationException e) {
            throw e;
        } catch (Exception e) {
            throw new InterbankCommunicationException("Network greška pri deleteNegotiation " + negotiationId, e);
        }
    }

    // =========================================================================
    // §3.6 – Prihvatanje ponude (sinhrono čeka COMMITTED)
    // =========================================================================

    public void acceptNegotiation(ForeignBankId negotiationId) {
        InterbankProperties.PartnerBank partner = resolvePartnerOrThrow(negotiationId.routingNumber());
        String url = buildNegotiationUrl(partner.getBaseUrl(), negotiationId) + "/accept";

        log.info("[InterbankClient] acceptNegotiation – čekam COMMITTED. negotiationId={}", negotiationId);

        try {
            RestClient.builder()
                    .baseUrl(url)
                    .defaultHeader("X-Api-Key", partner.getOutboundToken())
                    .build()
                    .get()
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        if (res.getStatusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED)) {
                            throw new InterbankAuthException("401 pri acceptNegotiation " + negotiationId);
                        }
                        throw new InterbankCommunicationException("4xx greška pri acceptNegotiation " + negotiationId);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new InterbankCommunicationException("5xx greška pri acceptNegotiation " + negotiationId);
                    })
                    .toBodilessEntity();

        } catch (InterbankAuthException | InterbankCommunicationException e) {
            throw e;
        } catch (Exception e) {
            throw new InterbankCommunicationException("Network greška pri acceptNegotiation " + negotiationId, e);
        }
    }

    // =========================================================================
    // §3.7 – Informacije o korisniku
    // =========================================================================

    public UserInformation getUserInfo(ForeignBankId userId) {
        InterbankProperties.PartnerBank partner = resolvePartnerOrThrow(userId.routingNumber());
        String url = partner.getBaseUrl() + "/user/" + userId.routingNumber() + "/" + userId.id();

        try {
            return RestClient.builder()
                    .baseUrl(url)
                    .defaultHeader("X-Api-Key", partner.getOutboundToken())
                    .build()
                    .get()
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        if (res.getStatusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED)) {
                            throw new InterbankAuthException("401 pri getUserInfo userId=" + userId);
                        }
                        throw new InterbankCommunicationException("4xx greška pri getUserInfo userId=" + userId);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new InterbankCommunicationException("5xx greška pri getUserInfo userId=" + userId);
                    })
                    .body(UserInformation.class);

        } catch (InterbankAuthException | InterbankCommunicationException e) {
            throw e;
        } catch (Exception e) {
            throw new InterbankCommunicationException("Network greška pri getUserInfo userId=" + userId, e);
        }
    }

    // =========================================================================
    // Interni helperi
    // =========================================================================

    private InterbankProperties.PartnerBank resolvePartnerOrThrow(int routingNumber) {
        return routing.resolvePartnerByRouting(routingNumber)
                .orElseThrow(() -> new InterbankCommunicationException(
                        "Nepoznat routing number: " + routingNumber + " – proveri konfiguraciju partnera"));
    }

    private String buildNegotiationUrl(String baseUrl, ForeignBankId negotiationId) {
        return baseUrl + "/negotiations/" + negotiationId.routingNumber() + "/" + negotiationId.id();
    }

    private InterbankMessageService.MessageType toServiceMessageType(MessageType type) {
        return switch (type) {
            case NEW_TX -> InterbankMessageService.MessageType.NEW_TX;
            case COMMIT_TX -> InterbankMessageService.MessageType.COMMIT_TX;
            case ROLLBACK_TX -> InterbankMessageService.MessageType.ROLLBACK_TX;
        };
    }

    private String serializeQuietly(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("[InterbankClient] Serijalizacija nije uspela: {}", e.getMessage());
            return null;
        }
    }
}