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

@Service
@RequiredArgsConstructor
public class InterbankClient {

    private final InterbankProperties interbankProperties;
    private final BankRoutingService bankRoutingService;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

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
        // §3.7 path je per-partner konfigurabilan: spec default je "/user/{rn}/{id}",
        // ali Tim 1 ima override "/interbank/user/{rn}/{id}" (path collision sa FE rutom).
        String pathTemplate = partnerBank.getUserInfoPath();
        if (pathTemplate == null || pathTemplate.isBlank()) {
            pathTemplate = "/user/{rn}/{id}";
        }
        try {
            return restClient
                    .get()
                    .uri(partnerBank.getBaseUrl() + pathTemplate,
                            userId.routingNumber(), userId.id())
                    .header("X-Api-Key", partnerBank.getOutboundToken())
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            (req, res) -> {
                                if (res.getStatusCode().value() == 401)
                                    throw new InterbankExceptions.InterbankAuthException(
                                            "Invalid API key for routing " + userId.routingNumber() + ".");
                                // §3.7 — 404 nije fatalno, samo nedostatak imena. Bacamo poseban tip
                                // da caller moze graceful fallback (prikazi opaque id u UI-u).
                                if (res.getStatusCode().value() == 404)
                                    throw new InterbankExceptions.InterbankUserNotFoundException(
                                            "User " + userId + " not found at partner bank");
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
