package rs.raf.banka2_bek.interbank.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import rs.raf.banka2_bek.interbank.config.InterbankProperties;
import rs.raf.banka2_bek.interbank.exception.InterbankExceptions;
import rs.raf.banka2_bek.interbank.protocol.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Unit tests for InterbankClient.sendMessage (§2.9-§2.11).
 * Uses MockRestServiceServer to control HTTP responses without a live server.
 */
@ExtendWith(MockitoExtension.class)
class InterbankClientTest {

    @Mock
    private InterbankProperties interbankProperties;

    @Mock
    private BankRoutingService bankRoutingService;

    private ObjectMapper objectMapper;
    private MockRestServiceServer mockServer;
    private InterbankClient client;

    private static final int REMOTE_RN = 111;
    private static final String BASE_URL = "http://bank1:8080";
    private static final String OUT_TOKEN = "outToken1";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        client = new InterbankClient(interbankProperties, bankRoutingService, objectMapper, restClient);

        InterbankProperties.PartnerBank partner = new InterbankProperties.PartnerBank();
        partner.setRoutingNumber(REMOTE_RN);
        partner.setBaseUrl(BASE_URL);
        partner.setOutboundToken(OUT_TOKEN);
        partner.setInboundToken("inToken1");

        lenient().when(bankRoutingService.resolvePartnerByRouting(REMOTE_RN)).thenReturn(Optional.of(partner));
    }

    // -------------------------------------------------------------------------
    // sendMessage — happy paths
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("sendMessage 200 deserializes response body into responseType")
    void sendMessage_200_deserializesBody() throws Exception {
        TransactionVote expectedVote = new TransactionVote(TransactionVote.Vote.YES, List.of());
        String responseJson = objectMapper.writeValueAsString(expectedVote);

        mockServer.expect(requestTo(BASE_URL + "/interbank"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("X-Api-Key", OUT_TOKEN))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        Message<Transaction> envelope = buildEnvelope();
        TransactionVote result = client.sendMessage(REMOTE_RN, MessageType.NEW_TX, envelope, TransactionVote.class);

        assertThat(result).isNotNull();
        assertThat(result.vote()).isEqualTo(TransactionVote.Vote.YES);
        mockServer.verify();
    }

    @Test
    @DisplayName("sendMessage with Void.class returns null regardless of response body")
    void sendMessage_voidClass_returnsNull() {
        mockServer.expect(requestTo(BASE_URL + "/interbank"))
                .andRespond(withNoContent());

        Message<CommitTransaction> envelope = buildCommitEnvelope();
        Void result = client.sendMessage(REMOTE_RN, MessageType.COMMIT_TX, envelope, Void.class);

        assertThat(result).isNull();
        mockServer.verify();
    }

    @Test
    @DisplayName("sendMessage 202 returns null (scheduler will retry)")
    void sendMessage_202_returnsNull() throws Exception {
        mockServer.expect(requestTo(BASE_URL + "/interbank"))
                .andRespond(withStatus(HttpStatus.ACCEPTED).body("").contentType(MediaType.APPLICATION_JSON));

        Message<Transaction> envelope = buildEnvelope();
        TransactionVote result = client.sendMessage(REMOTE_RN, MessageType.NEW_TX, envelope, TransactionVote.class);

        assertThat(result).isNull();
        mockServer.verify();
    }

    // -------------------------------------------------------------------------
    // sendMessage — error paths
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("sendMessage 401 throws InterbankAuthException")
    void sendMessage_401_throwsAuthException() {
        mockServer.expect(requestTo(BASE_URL + "/interbank"))
                .andRespond(withUnauthorizedRequest());

        Message<Transaction> envelope = buildEnvelope();

        assertThatThrownBy(() -> client.sendMessage(REMOTE_RN, MessageType.NEW_TX, envelope, TransactionVote.class))
                .isInstanceOf(InterbankExceptions.InterbankAuthException.class);
        mockServer.verify();
    }

    @Test
    @DisplayName("sendMessage 500 throws InterbankCommunicationException")
    void sendMessage_500_throwsCommunicationException() {
        mockServer.expect(requestTo(BASE_URL + "/interbank"))
                .andRespond(withServerError());

        Message<Transaction> envelope = buildEnvelope();

        assertThatThrownBy(() -> client.sendMessage(REMOTE_RN, MessageType.NEW_TX, envelope, TransactionVote.class))
                .isInstanceOf(InterbankExceptions.InterbankCommunicationException.class);
        mockServer.verify();
    }

    @Test
    @DisplayName("sendMessage 400 throws InterbankCommunicationException")
    void sendMessage_400_throwsCommunicationException() {
        mockServer.expect(requestTo(BASE_URL + "/interbank"))
                .andRespond(withBadRequest());

        Message<Transaction> envelope = buildEnvelope();

        assertThatThrownBy(() -> client.sendMessage(REMOTE_RN, MessageType.NEW_TX, envelope, TransactionVote.class))
                .isInstanceOf(InterbankExceptions.InterbankCommunicationException.class);
        mockServer.verify();
    }

    @Test
    @DisplayName("sendMessage throws InterbankProtocolException when routing number is unknown")
    void sendMessage_unknownRouting_throwsProtocolException() {
        when(bankRoutingService.resolvePartnerByRouting(999)).thenReturn(Optional.empty());

        Message<Transaction> envelope = buildEnvelope();

        assertThatThrownBy(() -> client.sendMessage(999, MessageType.NEW_TX, envelope, TransactionVote.class))
                .isInstanceOf(InterbankExceptions.InterbankProtocolException.class);
    }

    // -------------------------------------------------------------------------
    // §3.1 fetchPublicStocks
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("fetchPublicStocks 200 deserializes List<PublicStock>")
    void fetchPublicStocks_200_returnsList() throws Exception {
        PublicStock stock = new PublicStock(
                new StockDescription("AAPL"),
                List.of(new PublicStock.Seller(new ForeignBankId(REMOTE_RN, "client1"), new BigDecimal("100"))));
        String json = objectMapper.writeValueAsString(List.of(stock));

        mockServer.expect(requestTo(BASE_URL + "/public-stock"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Api-Key", OUT_TOKEN))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        List<PublicStock> result = client.fetchPublicStocks(REMOTE_RN);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).stock().ticker()).isEqualTo("AAPL");
        assertThat(result.get(0).sellers()).hasSize(1);
        mockServer.verify();
    }

    @Test
    @DisplayName("fetchPublicStocks unknown routing throws InterbankProtocolException")
    void fetchPublicStocks_unknownRouting_throws() {
        when(bankRoutingService.resolvePartnerByRouting(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> client.fetchPublicStocks(999))
                .isInstanceOf(InterbankExceptions.InterbankProtocolException.class);
    }

    @Test
    @DisplayName("fetchPublicStocks 401 throws InterbankAuthException")
    void fetchPublicStocks_401_throws() {
        mockServer.expect(requestTo(BASE_URL + "/public-stock"))
                .andRespond(withUnauthorizedRequest());

        assertThatThrownBy(() -> client.fetchPublicStocks(REMOTE_RN))
                .isInstanceOf(InterbankExceptions.InterbankAuthException.class);
        mockServer.verify();
    }

    // -------------------------------------------------------------------------
    // §3.2 postNegotiation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("postNegotiation 200 returns ForeignBankId from body")
    void postNegotiation_200_returnsForeignBankId() throws Exception {
        ForeignBankId expected = new ForeignBankId(REMOTE_RN, "neg-uuid-1");
        String json = objectMapper.writeValueAsString(expected);

        mockServer.expect(requestTo(BASE_URL + "/negotiations"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Api-Key", OUT_TOKEN))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        ForeignBankId result = client.postNegotiation(REMOTE_RN, buildOffer());

        assertThat(result).isEqualTo(expected);
        mockServer.verify();
    }

    @Test
    @DisplayName("postNegotiation unknown routing throws InterbankProtocolException")
    void postNegotiation_unknownRouting_throws() {
        when(bankRoutingService.resolvePartnerByRouting(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> client.postNegotiation(999, buildOffer()))
                .isInstanceOf(InterbankExceptions.InterbankProtocolException.class);
    }

    @Test
    @DisplayName("postNegotiation 500 throws InterbankCommunicationException")
    void postNegotiation_500_throws() {
        mockServer.expect(requestTo(BASE_URL + "/negotiations"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.postNegotiation(REMOTE_RN, buildOffer()))
                .isInstanceOf(InterbankExceptions.InterbankCommunicationException.class);
        mockServer.verify();
    }

    // -------------------------------------------------------------------------
    // §3.3 putCounterOffer
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("putCounterOffer 204 succeeds without body")
    void putCounterOffer_204_succeeds() {
        ForeignBankId negId = new ForeignBankId(REMOTE_RN, "neg-uuid-2");

        mockServer.expect(requestTo(BASE_URL + "/negotiations/" + REMOTE_RN + "/neg-uuid-2"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("X-Api-Key", OUT_TOKEN))
                .andRespond(withNoContent());

        client.putCounterOffer(negId, buildOffer());
        mockServer.verify();
    }

    @Test
    @DisplayName("putCounterOffer 401 throws InterbankAuthException")
    void putCounterOffer_401_throws() {
        ForeignBankId negId = new ForeignBankId(REMOTE_RN, "neg-uuid-3");

        mockServer.expect(requestTo(BASE_URL + "/negotiations/" + REMOTE_RN + "/neg-uuid-3"))
                .andRespond(withUnauthorizedRequest());

        assertThatThrownBy(() -> client.putCounterOffer(negId, buildOffer()))
                .isInstanceOf(InterbankExceptions.InterbankAuthException.class);
        mockServer.verify();
    }

    // -------------------------------------------------------------------------
    // §3.4 getNegotiation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getNegotiation 200 deserializes OtcNegotiation body")
    void getNegotiation_200_returnsBody() throws Exception {
        ForeignBankId negId = new ForeignBankId(REMOTE_RN, "neg-uuid-4");
        OtcNegotiation negotiation = new OtcNegotiation(
                new StockDescription("MSFT"),
                OffsetDateTime.parse("2026-12-31T00:00:00Z"),
                new MonetaryValue(CurrencyCode.USD, new BigDecimal("250.00")),
                new MonetaryValue(CurrencyCode.USD, new BigDecimal("5.00")),
                new ForeignBankId(222, "buyer1"),
                new ForeignBankId(REMOTE_RN, "seller1"),
                new BigDecimal("10"),
                new ForeignBankId(222, "buyer1"),
                true);
        String json = objectMapper.writeValueAsString(negotiation);

        mockServer.expect(requestTo(BASE_URL + "/negotiations/" + REMOTE_RN + "/neg-uuid-4"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Api-Key", OUT_TOKEN))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        OtcNegotiation result = client.getNegotiation(negId);

        assertThat(result).isNotNull();
        assertThat(result.stock().ticker()).isEqualTo("MSFT");
        assertThat(result.isOngoing()).isTrue();
        mockServer.verify();
    }

    @Test
    @DisplayName("getNegotiation unknown routing throws InterbankProtocolException")
    void getNegotiation_unknownRouting_throws() {
        when(bankRoutingService.resolvePartnerByRouting(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> client.getNegotiation(new ForeignBankId(999, "x")))
                .isInstanceOf(InterbankExceptions.InterbankProtocolException.class);
    }

    // -------------------------------------------------------------------------
    // §3.5 deleteNegotiation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("deleteNegotiation 204 succeeds")
    void deleteNegotiation_204_succeeds() {
        ForeignBankId negId = new ForeignBankId(REMOTE_RN, "neg-uuid-5");

        mockServer.expect(requestTo(BASE_URL + "/negotiations/" + REMOTE_RN + "/neg-uuid-5"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("X-Api-Key", OUT_TOKEN))
                .andRespond(withNoContent());

        client.deleteNegotiation(negId);
        mockServer.verify();
    }

    @Test
    @DisplayName("deleteNegotiation 500 throws InterbankCommunicationException")
    void deleteNegotiation_500_throws() {
        ForeignBankId negId = new ForeignBankId(REMOTE_RN, "neg-uuid-6");

        mockServer.expect(requestTo(BASE_URL + "/negotiations/" + REMOTE_RN + "/neg-uuid-6"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.deleteNegotiation(negId))
                .isInstanceOf(InterbankExceptions.InterbankCommunicationException.class);
        mockServer.verify();
    }

    // -------------------------------------------------------------------------
    // §3.6 acceptNegotiation (synchronous — waits for COMMITTED)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("acceptNegotiation 200 succeeds (partner reports COMMITTED)")
    void acceptNegotiation_200_succeeds() {
        ForeignBankId negId = new ForeignBankId(REMOTE_RN, "neg-uuid-7");

        mockServer.expect(requestTo(BASE_URL + "/negotiations/" + REMOTE_RN + "/neg-uuid-7/accept"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Api-Key", OUT_TOKEN))
                .andRespond(withSuccess());

        client.acceptNegotiation(negId);
        mockServer.verify();
    }

    @Test
    @DisplayName("acceptNegotiation 500 throws InterbankCommunicationException")
    void acceptNegotiation_500_throws() {
        ForeignBankId negId = new ForeignBankId(REMOTE_RN, "neg-uuid-8");

        mockServer.expect(requestTo(BASE_URL + "/negotiations/" + REMOTE_RN + "/neg-uuid-8/accept"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.acceptNegotiation(negId))
                .isInstanceOf(InterbankExceptions.InterbankCommunicationException.class);
        mockServer.verify();
    }

    // -------------------------------------------------------------------------
    // §3.7 getUserInfo
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getUserInfo 200 deserializes UserInformation body")
    void getUserInfo_200_returnsBody() throws Exception {
        ForeignBankId userId = new ForeignBankId(REMOTE_RN, "user-1");
        UserInformation info = new UserInformation("Banka 1 d.o.o.", "Marko Markovic");
        String json = objectMapper.writeValueAsString(info);

        mockServer.expect(requestTo(BASE_URL + "/user/" + REMOTE_RN + "/user-1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Api-Key", OUT_TOKEN))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        UserInformation result = client.getUserInfo(userId);

        assertThat(result).isNotNull();
        assertThat(result.bankDisplayName()).isEqualTo("Banka 1 d.o.o.");
        assertThat(result.displayName()).isEqualTo("Marko Markovic");
        mockServer.verify();
    }

    @Test
    @DisplayName("getUserInfo unknown routing throws InterbankProtocolException")
    void getUserInfo_unknownRouting_throws() {
        when(bankRoutingService.resolvePartnerByRouting(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> client.getUserInfo(new ForeignBankId(999, "u")))
                .isInstanceOf(InterbankExceptions.InterbankProtocolException.class);
    }

    private OtcOffer buildOffer() {
        return new OtcOffer(
                new StockDescription("AAPL"),
                OffsetDateTime.parse("2026-12-31T00:00:00Z"),
                new MonetaryValue(CurrencyCode.USD, new BigDecimal("180.00")),
                new MonetaryValue(CurrencyCode.USD, new BigDecimal("3.50")),
                new ForeignBankId(222, "buyer1"),
                new ForeignBankId(REMOTE_RN, "seller1"),
                new BigDecimal("5"),
                new ForeignBankId(222, "buyer1"));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Message<Transaction> buildEnvelope() {
        IdempotenceKey key = new IdempotenceKey(222, "deadbeef01234567deadbeef01234567deadbeef01234567deadbeef01234567");
        ForeignBankId txId = new ForeignBankId(222, "uuid-1");
        Transaction tx = new Transaction(List.of(), txId, "test", null, null, null);
        return new Message<>(key, MessageType.NEW_TX, tx);
    }

    private Message<CommitTransaction> buildCommitEnvelope() {
        IdempotenceKey key = new IdempotenceKey(222, "deadbeef01234567deadbeef01234567deadbeef01234567deadbeef01234567");
        ForeignBankId txId = new ForeignBankId(222, "uuid-1");
        return new Message<>(key, MessageType.COMMIT_TX, new CommitTransaction(txId));
    }
}
