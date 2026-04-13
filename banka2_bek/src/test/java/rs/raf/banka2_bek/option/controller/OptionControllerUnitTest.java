package rs.raf.banka2_bek.option.controller;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import rs.raf.banka2_bek.auth.config.GlobalExceptionHandler;
import rs.raf.banka2_bek.option.dto.OptionChainDto;
import rs.raf.banka2_bek.option.dto.OptionDto;
import rs.raf.banka2_bek.option.service.OptionGeneratorService;
import rs.raf.banka2_bek.option.service.OptionService;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OptionController — unit tests")
class OptionControllerUnitTest {

    private MockMvc mockMvc;
    private UsernamePasswordAuthenticationToken auth;

    @Mock
    private OptionService optionService;

    @Mock
    private OptionGeneratorService optionGeneratorService;

    @InjectMocks
    private OptionController optionController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(optionController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        auth = new UsernamePasswordAuthenticationToken("actuary@example.com", null);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ========== GET /options ==========

    @Test
    @DisplayName("GET /options?stockListingId=1 returns option chain")
    void getOptionsForStock_returnsOk() throws Exception {
        OptionChainDto chain = new OptionChainDto();
        chain.setCurrentStockPrice(new BigDecimal("150.00"));
        chain.setCalls(List.of());
        chain.setPuts(List.of());

        when(optionService.getOptionsForStock(1L)).thenReturn(List.of(chain));

        mockMvc.perform(get("/options").param("stockListingId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currentStockPrice").value(150.00));

        verify(optionService).getOptionsForStock(1L);
    }

    @Test
    @DisplayName("GET /options returns empty list when no options")
    void getOptionsForStock_emptyList() throws Exception {
        when(optionService.getOptionsForStock(anyLong())).thenReturn(List.of());

        mockMvc.perform(get("/options").param("stockListingId", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /options propagates EntityNotFoundException as 404")
    void getOptionsForStock_notFound() throws Exception {
        when(optionService.getOptionsForStock(anyLong()))
                .thenThrow(new EntityNotFoundException("Listing not found"));

        mockMvc.perform(get("/options").param("stockListingId", "999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Listing not found"));
    }

    // ========== GET /options/{id} ==========

    @Test
    @DisplayName("GET /options/{id} returns option dto")
    void getOptionById_returnsOk() throws Exception {
        OptionDto dto = new OptionDto();
        dto.setId(7L);
        dto.setTicker("AAPL260402C00185000");
        dto.setOptionType("CALL");
        dto.setStrikePrice(new BigDecimal("185.00"));
        dto.setInTheMoney(true);

        when(optionService.getOptionById(7L)).thenReturn(dto);

        mockMvc.perform(get("/options/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.ticker").value("AAPL260402C00185000"))
                .andExpect(jsonPath("$.inTheMoney").value(true));

        verify(optionService).getOptionById(7L);
    }

    @Test
    @DisplayName("GET /options/{id} returns 404 when missing")
    void getOptionById_notFound() throws Exception {
        when(optionService.getOptionById(999L))
                .thenThrow(new EntityNotFoundException("Option id: 999 not found."));

        mockMvc.perform(get("/options/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Option id: 999 not found."));
    }

    // ========== POST /options/{id}/exercise ==========

    @Test
    @DisplayName("POST /options/{id}/exercise — success returns 200 with message")
    void exerciseOption_success() throws Exception {
        doNothing().when(optionService).exerciseOption(eq(5L), eq("actuary@example.com"));

        mockMvc.perform(post("/options/5/exercise").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Opcija uspesno izvrsena."));

        verify(optionService).exerciseOption(5L, "actuary@example.com");
    }

    @Test
    @DisplayName("POST /options/{id}/exercise — 404 when option missing")
    void exerciseOption_notFound() throws Exception {
        doThrow(new EntityNotFoundException("Option id: 999 not found."))
                .when(optionService).exerciseOption(eq(999L), anyString());

        mockMvc.perform(post("/options/999/exercise").principal(auth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Option id: 999 not found."));
    }

    @Test
    @DisplayName("POST /options/{id}/exercise — 400 when not in the money")
    void exerciseOption_notItm() throws Exception {
        doThrow(new IllegalArgumentException("Opcija nije in-the-money."))
                .when(optionService).exerciseOption(anyLong(), anyString());

        mockMvc.perform(post("/options/5/exercise").principal(auth))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Opcija nije in-the-money."));
    }

    @Test
    @DisplayName("POST /options/{id}/exercise — 400 when expired")
    void exerciseOption_expired() throws Exception {
        doThrow(new IllegalArgumentException("Opcija je istekla."))
                .when(optionService).exerciseOption(anyLong(), anyString());

        mockMvc.perform(post("/options/5/exercise").principal(auth))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Opcija je istekla."));
    }

    @Test
    @DisplayName("POST /options/{id}/exercise — 403 when user is not authorized actuary")
    void exerciseOption_forbidden_notActuary() throws Exception {
        doThrow(new IllegalStateException("Korisnik nije aktivan aktuar."))
                .when(optionService).exerciseOption(anyLong(), anyString());

        mockMvc.perform(post("/options/5/exercise").principal(auth))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Korisnik nije aktivan aktuar."));
    }

    @Test
    @DisplayName("POST /options/{id}/exercise — RuntimeException propagates to 400")
    void exerciseOption_runtimeException() throws Exception {
        doThrow(new RuntimeException("Neocekivana greska."))
                .when(optionService).exerciseOption(anyLong(), anyString());

        mockMvc.perform(post("/options/5/exercise").principal(auth))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Neocekivana greska."));
    }

    // ========== POST /options/generate ==========

    @Test
    @DisplayName("POST /options/generate — success returns 200 with message")
    void generateOptions_success() throws Exception {
        doNothing().when(optionGeneratorService).generateAllOptions();

        mockMvc.perform(post("/options/generate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Opcije uspesno generisane za sve akcije."));

        verify(optionGeneratorService).generateAllOptions();
    }

    @Test
    @DisplayName("POST /options/generate — propagates service exception")
    void generateOptions_serviceException() throws Exception {
        doThrow(new RuntimeException("Alpha Vantage API limit reached."))
                .when(optionGeneratorService).generateAllOptions();

        mockMvc.perform(post("/options/generate"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Alpha Vantage API limit reached."));
    }
}
