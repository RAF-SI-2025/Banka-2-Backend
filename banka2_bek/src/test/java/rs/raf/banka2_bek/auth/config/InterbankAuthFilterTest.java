package rs.raf.banka2_bek.auth.config;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import rs.raf.banka2_bek.interbank.config.InterbankProperties;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class InterbankAuthFilterTest {

    @Mock
    private InterbankProperties props;

    @InjectMocks
    private InterbankAuthFilter filter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain filterChain;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = new MockFilterChain();
    }

    @Test
    void testNonInterbankPath_skipsFilter() throws Exception {
        request.setRequestURI("/some-other-path");

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(200, response.getStatus());
    }

    @Test
    void testInvalidApiKey_returns401() throws Exception {
        request.setRequestURI("/interbank/message");
        request.addHeader("X-Api-Key", "wrong-token");

        when(props.findByApiKey("wrong-token")).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(401, response.getStatus());
    }

    @Test
    void testValidApiKey_setsAuthentication() throws Exception {
        request.setRequestURI("/interbank/message");
        request.addHeader("X-Api-Key", "valid-token");

        InterbankProperties.PartnerBank partner = new InterbankProperties.PartnerBank();
        partner.setRoutingNumber(111);

        when(props.findByApiKey("valid-token")).thenReturn(Optional.of(partner));

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(200, response.getStatus());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
