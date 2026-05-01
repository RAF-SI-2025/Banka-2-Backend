package rs.raf.banka2_bek.interbank.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.interbank.config.InterbankProperties;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BankRoutingServiceTest {

    @Mock
    private InterbankProperties properties;

    @InjectMocks
    private BankRoutingService bankRoutingService;

    @Test
    void testMyRoutingNumber_success() {
        when(properties.getMyRoutingNumber()).thenReturn(222);

        int result = bankRoutingService.myRoutingNumber();

        assertEquals(222, result);
    }

    @Test
    void testMyRoutingNumber_null_throwsException() {
        when(properties.getMyRoutingNumber()).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> bankRoutingService.myRoutingNumber());
    }

    @Test
    void testParseRoutingNumber_success() {
        int result = bankRoutingService.parseRoutingNumber("222123456");

        assertEquals(222, result);
    }

    @Test
    void testParseRoutingNumber_null_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> bankRoutingService.parseRoutingNumber(null));
    }

    @Test
    void testParseRoutingNumber_tooShort_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> bankRoutingService.parseRoutingNumber("12"));
    }

    @Test
    void testParseRoutingNumber_invalidFormat_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> bankRoutingService.parseRoutingNumber("AB1234"));
    }

    @Test
    void testIsLocalAccount_true() {
        when(properties.getMyRoutingNumber()).thenReturn(222);

        boolean result = bankRoutingService.isLocalAccount("222123456");

        assertTrue(result);
    }

    @Test
    void testIsLocalAccount_false() {
        when(properties.getMyRoutingNumber()).thenReturn(222);

        boolean result = bankRoutingService.isLocalAccount("111123456");

        assertFalse(result);
    }

    @Test
    void testResolvePartnerByRouting_found() {
        InterbankProperties.PartnerBank partner = new InterbankProperties.PartnerBank();
        partner.setRoutingNumber(111);

        when(properties.getPartners()).thenReturn(List.of(partner));

        Optional<InterbankProperties.PartnerBank> result = bankRoutingService.resolvePartnerByRouting(111);

        assertTrue(result.isPresent());
        assertEquals(111, result.get().getRoutingNumber());
    }

    @Test
    void testResolvePartnerByRouting_notFound() {
        when(properties.getPartners()).thenReturn(List.of());

        Optional<InterbankProperties.PartnerBank> result = bankRoutingService.resolvePartnerByRouting(999);

        assertTrue(result.isEmpty());
    }

    @Test
    void testResolvePartner_found() {
        InterbankProperties.PartnerBank partner = new InterbankProperties.PartnerBank();
        partner.setRoutingNumber(111);

        when(properties.getPartners()).thenReturn(List.of(partner));

        Optional<InterbankProperties.PartnerBank> result = bankRoutingService.resolvePartner("111123456");

        assertTrue(result.isPresent());
        assertEquals(111, result.get().getRoutingNumber());
    }

    @Test
    void testResolvePartner_notFound() {
        when(properties.getPartners()).thenReturn(List.of());

        Optional<InterbankProperties.PartnerBank> result = bankRoutingService.resolvePartner("999123456");

        assertTrue(result.isEmpty());
    }
}
