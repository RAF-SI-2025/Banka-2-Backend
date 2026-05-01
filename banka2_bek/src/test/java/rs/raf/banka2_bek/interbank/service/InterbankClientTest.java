package rs.raf.banka2_bek.interbank.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;
import rs.raf.banka2_bek.interbank.config.InterbankProperties;
import rs.raf.banka2_bek.interbank.exception.InterbankExceptions.InterbankAuthException;
import rs.raf.banka2_bek.interbank.exception.InterbankExceptions.InterbankCommunicationException;
import rs.raf.banka2_bek.interbank.protocol.IdempotenceKey;
import rs.raf.banka2_bek.interbank.protocol.Message;
import rs.raf.banka2_bek.interbank.protocol.MessageType;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit testovi za InterbankClient (T5).
 * Koristi WireMock kao simulirani partner server.
 */
@ExtendWith(MockitoExtension.class)
class InterbankClientTest {

    private static WireMockServer wireMockServer;

    @Mock
    private BankRoutingService bankRoutingService;

    @Mock
    private InterbankMessageService messageService;

    private InterbankClient interbankClient;

    // Dummy klasa za tip odgovora u testovima
    record TestResponse(String result) {}

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();

        // Pravi RestClient koji će ići na WireMock
        RestClient restClient = RestClient.builder()
                .baseUrl("http://localhost:" + wireMockServer.port())
                .build();

        interbankClient = new InterbankClient(
                mock(InterbankProperties.class),
                bankRoutingService,
                messageService,
                restClient,
                new ObjectMapper()
        );

        // routing prefix "111" mapira ka WireMock serveru
        InterbankProperties.PartnerBank partner = new InterbankProperties.PartnerBank();
        partner.setBaseUrl("http://localhost:" + wireMockServer.port());
        partner.setOutboundToken("secret-token");

        when(bankRoutingService.resolvePartnerByRouting(111)).thenReturn(Optional.of(partner));
    }

    // -------------------------------------------------------------------------
    // sendMessage – 200 OK
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("200 OK → vraća deserijalizovano telo i beleži SENT u audit logu")
    void sendMessage_200OK_returnBodyAndMarksSent() {
        stubFor(post(urlEqualTo("/interbank"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"result\":\"ok\"}")));

        Message<String> envelope = buildEnvelope("idem-001");

        TestResponse response = interbankClient.sendMessage(111, MessageType.NEW_TX, envelope, TestResponse.class);

        assertNotNull(response);
        assertEquals("ok", response.result());
        verify(messageService).recordOutbound(any(), anyInt(), any(), any());
        verify(messageService).markOutboundSent(any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // sendMessage – 202 Accepted
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("202 Accepted → vraća null, NE baca exception, ostaje PENDING")
    void sendMessage_202Accepted_returnsNullAndStaysPending() {
        stubFor(post(urlEqualTo("/interbank"))
                .willReturn(aResponse().withStatus(202)));

        Message<String> envelope = buildEnvelope("idem-202");

        TestResponse response = interbankClient.sendMessage(111, MessageType.NEW_TX, envelope, TestResponse.class);

        assertNull(response);
        // markOutboundSent se NE sme pozvati
        verify(messageService, never()).markOutboundSent(any(), any(), any());
        // markOutboundFailed se NE sme pozvati
        verify(messageService, never()).markOutboundFailed(any(), any());
    }

    // -------------------------------------------------------------------------
    // sendMessage – 204 No Content
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("204 No Content → vraća null i beleži SENT")
    void sendMessage_204NoContent_returnsNullAndMarksSent() {
        stubFor(post(urlEqualTo("/interbank"))
                .willReturn(aResponse().withStatus(204)));

        Message<String> envelope = buildEnvelope("idem-204");

        TestResponse response = interbankClient.sendMessage(111, MessageType.COMMIT_TX, envelope, TestResponse.class);

        assertNull(response);
    }

    // -------------------------------------------------------------------------
    // sendMessage – 401 Unauthorized
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("401 → baca InterbankAuthException i beleži FAILED")
    void sendMessage_401_throwsAuthException() {
        stubFor(post(urlEqualTo("/interbank"))
                .willReturn(aResponse().withStatus(401)));

        Message<String> envelope = buildEnvelope("idem-401");

        assertThrows(InterbankAuthException.class, () ->
                interbankClient.sendMessage(111, MessageType.NEW_TX, envelope, TestResponse.class));

        verify(messageService).markOutboundFailed(any(), any());
    }

    // -------------------------------------------------------------------------
    // sendMessage – 500 Server Error
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("500 → baca InterbankCommunicationException i beleži FAILED")
    void sendMessage_500_throwsCommunicationException() {
        stubFor(post(urlEqualTo("/interbank"))
                .willReturn(aResponse().withStatus(500)));

        Message<String> envelope = buildEnvelope("idem-500");

        assertThrows(InterbankCommunicationException.class, () ->
                interbankClient.sendMessage(111, MessageType.NEW_TX, envelope, TestResponse.class));

        verify(messageService).markOutboundFailed(any(), any());
    }

    // -------------------------------------------------------------------------
    // sendMessage – X-Api-Key header
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("X-Api-Key header se uvek šalje sa ispravnom vrednošću")
    void sendMessage_alwaysIncludesAuthHeader() {
        stubFor(post(urlEqualTo("/interbank"))
                .withHeader("X-Api-Key", equalTo("secret-token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"result\":\"ok\"}")));

        Message<String> envelope = buildEnvelope("idem-auth");

        // WireMock vraća 404 ako header nije ispravan – a mi očekujemo 200
        assertDoesNotThrow(() ->
                interbankClient.sendMessage(111, MessageType.NEW_TX, envelope, TestResponse.class));
    }

    // -------------------------------------------------------------------------
    // sendMessage – audit log
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("recordOutbound se uvek poziva PRE HTTP poziva")
    void sendMessage_recordOutboundCalledBeforeHttpCall() {
        stubFor(post(urlEqualTo("/interbank"))
                .willReturn(aResponse().withStatus(200).withBody("{\"result\":\"ok\"}")));

        Message<String> envelope = buildEnvelope("idem-audit");

        interbankClient.sendMessage(111, MessageType.NEW_TX, envelope, TestResponse.class);

        // Redosled: recordOutbound → (HTTP) → markOutboundSent
        var inOrder = inOrder(messageService);
        inOrder.verify(messageService).recordOutbound(any(), anyInt(), any(), any());
        inOrder.verify(messageService).markOutboundSent(any(), anyInt(), any());
    }

    // -------------------------------------------------------------------------
    // fetchPublicStocks
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("fetchPublicStocks – 200 vraća listu; 401 baca AuthException")
    void fetchPublicStocks_authError_throwsAuthException() {
        stubFor(get(urlEqualTo("/public-stock"))
                .willReturn(aResponse().withStatus(401)));

        assertThrows(InterbankAuthException.class, () ->
                interbankClient.fetchPublicStocks(111));
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private Message<String> buildEnvelope(String locallyGeneratedKey) {
        IdempotenceKey key = new IdempotenceKey(111, locallyGeneratedKey);
        return new Message<>(key, MessageType.NEW_TX, "test-payload");
    }
}