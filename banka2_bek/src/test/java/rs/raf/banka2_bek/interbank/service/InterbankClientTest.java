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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import rs.raf.banka2_bek.interbank.config.InterbankProperties;
import rs.raf.banka2_bek.interbank.exception.InterbankExceptions;
import rs.raf.banka2_bek.interbank.protocol.*;

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
    // OTC stub methods — all throw UnsupportedOperationException
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("fetchPublicStocks throws UnsupportedOperationException (TODO)")
    void fetchPublicStocks_throws() {
        assertThatThrownBy(() -> client.fetchPublicStocks(REMOTE_RN))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("postNegotiation throws UnsupportedOperationException (TODO)")
    void postNegotiation_throws() {
        assertThatThrownBy(() -> client.postNegotiation(REMOTE_RN, null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("putCounterOffer throws UnsupportedOperationException (TODO)")
    void putCounterOffer_throws() {
        assertThatThrownBy(() -> client.putCounterOffer(null, null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("getNegotiation throws UnsupportedOperationException (TODO)")
    void getNegotiation_throws() {
        assertThatThrownBy(() -> client.getNegotiation(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("deleteNegotiation throws UnsupportedOperationException (TODO)")
    void deleteNegotiation_throws() {
        assertThatThrownBy(() -> client.deleteNegotiation(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("acceptNegotiation throws UnsupportedOperationException (TODO)")
    void acceptNegotiation_throws() {
        assertThatThrownBy(() -> client.acceptNegotiation(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("getUserInfo throws UnsupportedOperationException (TODO)")
    void getUserInfo_throws() {
        assertThatThrownBy(() -> client.getUserInfo(null))
                .isInstanceOf(UnsupportedOperationException.class);
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
