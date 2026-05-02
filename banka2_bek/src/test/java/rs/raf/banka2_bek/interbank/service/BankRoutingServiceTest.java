package rs.raf.banka2_bek.interbank.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.interbank.config.InterbankProperties;
import rs.raf.banka2_bek.interbank.exception.InterbankExceptions;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for BankRoutingService (§2.1 Bank identification).
 */
@ExtendWith(MockitoExtension.class)
class BankRoutingServiceTest {

    @Mock
    private InterbankProperties properties;

    @InjectMocks
    private BankRoutingService service;

    private InterbankProperties.PartnerBank partner111;
    private InterbankProperties.PartnerBank partner333;

    @BeforeEach
    void setUp() {
        partner111 = new InterbankProperties.PartnerBank();
        partner111.setRoutingNumber(111);
        partner111.setDisplayName("Banka 1");
        partner111.setBaseUrl("http://bank1:8080");
        partner111.setOutboundToken("outToken1");
        partner111.setInboundToken("inToken1");

        partner333 = new InterbankProperties.PartnerBank();
        partner333.setRoutingNumber(333);
        partner333.setDisplayName("Banka 3");
        partner333.setBaseUrl("http://bank3:8080");
        partner333.setOutboundToken("outToken3");
        partner333.setInboundToken("inToken3");
    }

    // -------------------------------------------------------------------------
    // myRoutingNumber
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("myRoutingNumber returns configured value")
    void myRoutingNumber_configured_returnsValue() {
        when(properties.getMyRoutingNumber()).thenReturn(222);

        assertThat(service.myRoutingNumber()).isEqualTo(222);
    }

    @Test
    @DisplayName("myRoutingNumber throws when not configured")
    void myRoutingNumber_null_throws() {
        when(properties.getMyRoutingNumber()).thenReturn(null);

        assertThatThrownBy(() -> service.myRoutingNumber())
                .isInstanceOf(InterbankExceptions.InterbankProtocolException.class);
    }

    // -------------------------------------------------------------------------
    // parseRoutingNumber
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("parseRoutingNumber extracts first 3 digits")
    void parseRoutingNumber_valid_returnsFirstThree() {
        assertThat(service.parseRoutingNumber("222123456789")).isEqualTo(222);
    }

    @Test
    @DisplayName("parseRoutingNumber accepts exactly 3-char input")
    void parseRoutingNumber_exactlyThreeChars_works() {
        assertThat(service.parseRoutingNumber("111")).isEqualTo(111);
    }

    @Test
    @DisplayName("parseRoutingNumber throws on null")
    void parseRoutingNumber_null_throws() {
        assertThatThrownBy(() -> service.parseRoutingNumber(null))
                .isInstanceOf(InterbankExceptions.InterbankProtocolException.class);
    }

    @Test
    @DisplayName("parseRoutingNumber throws on blank string")
    void parseRoutingNumber_blank_throws() {
        assertThatThrownBy(() -> service.parseRoutingNumber("   "))
                .isInstanceOf(InterbankExceptions.InterbankProtocolException.class);
    }

    @Test
    @DisplayName("parseRoutingNumber throws when fewer than 3 characters")
    void parseRoutingNumber_tooShort_throws() {
        assertThatThrownBy(() -> service.parseRoutingNumber("22"))
                .isInstanceOf(InterbankExceptions.InterbankProtocolException.class);
    }

    @Test
    @DisplayName("parseRoutingNumber throws when first 3 chars are not numeric")
    void parseRoutingNumber_nonNumericPrefix_throws() {
        assertThatThrownBy(() -> service.parseRoutingNumber("ABC123"))
                .isInstanceOf(InterbankExceptions.InterbankProtocolException.class);
    }

    // -------------------------------------------------------------------------
    // isLocalAccount
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("isLocalAccount returns true when prefix matches myRoutingNumber")
    void isLocalAccount_sameRouting_returnsTrue() {
        when(properties.getMyRoutingNumber()).thenReturn(222);

        assertThat(service.isLocalAccount("222999888")).isTrue();
    }

    @Test
    @DisplayName("isLocalAccount returns false when prefix differs")
    void isLocalAccount_differentRouting_returnsFalse() {
        when(properties.getMyRoutingNumber()).thenReturn(222);

        assertThat(service.isLocalAccount("111999888")).isFalse();
    }

    // -------------------------------------------------------------------------
    // resolvePartner
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("resolvePartner finds partner matching account prefix")
    void resolvePartner_knownPrefix_returnsPartner() {
        when(properties.getPartners()).thenReturn(List.of(partner111, partner333));

        Optional<InterbankProperties.PartnerBank> result = service.resolvePartner("111000001");

        assertThat(result).isPresent();
        assertThat(result.get().getRoutingNumber()).isEqualTo(111);
    }

    @Test
    @DisplayName("resolvePartner returns empty for unknown prefix")
    void resolvePartner_unknownPrefix_returnsEmpty() {
        when(properties.getPartners()).thenReturn(List.of(partner111, partner333));

        assertThat(service.resolvePartner("999000001")).isEmpty();
    }

    // -------------------------------------------------------------------------
    // resolvePartnerByRouting
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("resolvePartnerByRouting returns matching partner")
    void resolvePartnerByRouting_knownRouting_returnsPartner() {
        when(properties.getPartners()).thenReturn(List.of(partner111, partner333));

        Optional<InterbankProperties.PartnerBank> result = service.resolvePartnerByRouting(333);

        assertThat(result).isPresent();
        assertThat(result.get().getDisplayName()).isEqualTo("Banka 3");
    }

    @Test
    @DisplayName("resolvePartnerByRouting returns empty for unknown routing")
    void resolvePartnerByRouting_unknownRouting_returnsEmpty() {
        when(properties.getPartners()).thenReturn(List.of(partner111, partner333));

        assertThat(service.resolvePartnerByRouting(999)).isEmpty();
    }

    @Test
    @DisplayName("resolvePartnerByRouting returns empty when partner list is empty")
    void resolvePartnerByRouting_emptyPartnerList_returnsEmpty() {
        when(properties.getPartners()).thenReturn(List.of());

        assertThat(service.resolvePartnerByRouting(111)).isEmpty();
    }
}
