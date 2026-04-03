package rs.raf.banka2_bek.berza.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import rs.raf.banka2_bek.auth.config.GlobalExceptionHandler;
import rs.raf.banka2_bek.berza.dto.ExchangeDto;
import rs.raf.banka2_bek.berza.service.ExchangeManagementService;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class ExchangeManagementControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private ExchangeManagementService exchangeManagementService;

    @InjectMocks
    private ExchangeManagementController exchangeManagementController;

    private ExchangeDto testExchange;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(exchangeManagementController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        testExchange = ExchangeDto.builder()
                .id(1L)
                .name("New York Stock Exchange")
                .acronym("NYSE")
                .micCode("XNYS")
                .country("USA")
                .currency("USD")
                .timeZone("America/New_York")
                .openTime("09:30:00")
                .closeTime("16:00:00")
                .preMarketOpenTime("04:00:00")
                .postMarketCloseTime("20:00:00")
                .testMode(false)
                .active(true)
                .isCurrentlyOpen(true)
                .currentLocalTime("12:30:00")
                .nextOpenTime(null)
                .holidays(Set.of(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 25)))
                .build();
    }

    // ══════════════════════════════════════════════════════════════════
    //  GET /exchanges
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /exchanges - 200 OK with list of exchanges")
    void getAllExchanges_returnsList() throws Exception {
        ExchangeDto secondExchange = ExchangeDto.builder()
                .id(2L)
                .name("Belgrade Stock Exchange")
                .acronym("BELEX")
                .country("Serbia")
                .currency("RSD")
                .active(true)
                .build();

        when(exchangeManagementService.getAllExchanges()).thenReturn(List.of(testExchange, secondExchange));

        mockMvc.perform(get("/exchanges")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].acronym").value("NYSE"))
                .andExpect(jsonPath("$[0].name").value("New York Stock Exchange"))
                .andExpect(jsonPath("$[0].country").value("USA"))
                .andExpect(jsonPath("$[0].currency").value("USD"))
                .andExpect(jsonPath("$[1].acronym").value("BELEX"));

        verify(exchangeManagementService).getAllExchanges();
    }

    @Test
    @DisplayName("GET /exchanges - 200 OK with empty list")
    void getAllExchanges_returnsEmptyList() throws Exception {
        when(exchangeManagementService.getAllExchanges()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/exchanges")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ══════════════════════════════════════════════════════════════════
    //  GET /exchanges/{acronym}
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /exchanges/NYSE - 200 OK with exchange details")
    void getByAcronym_returnsExchange() throws Exception {
        when(exchangeManagementService.getByAcronym("NYSE")).thenReturn(testExchange);

        mockMvc.perform(get("/exchanges/NYSE")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.acronym").value("NYSE"))
                .andExpect(jsonPath("$.name").value("New York Stock Exchange"))
                .andExpect(jsonPath("$.micCode").value("XNYS"))
                .andExpect(jsonPath("$.openTime").value("09:30:00"))
                .andExpect(jsonPath("$.closeTime").value("16:00:00"))
                .andExpect(jsonPath("$.testMode").value(false))
                .andExpect(jsonPath("$.active").value(true));

        verify(exchangeManagementService).getByAcronym("NYSE");
    }

    @Test
    @DisplayName("GET /exchanges/INVALID - 400 when exchange not found")
    void getByAcronym_notFound() throws Exception {
        when(exchangeManagementService.getByAcronym("INVALID"))
                .thenThrow(new RuntimeException("Exchange not found: INVALID"));

        mockMvc.perform(get("/exchanges/INVALID")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Exchange not found: INVALID"));
    }

    // ══════════════════════════════════════════════════════════════════
    //  PATCH /exchanges/{acronym}/test-mode
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PATCH /exchanges/NYSE/test-mode - 200 OK enable test mode")
    void setTestMode_enable_returnsOk() throws Exception {
        doNothing().when(exchangeManagementService).setTestMode("NYSE", true);

        String payload = """
                {
                  "enabled": true
                }
                """;

        mockMvc.perform(patch("/exchanges/NYSE/test-mode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Test mode set to true for NYSE"));

        verify(exchangeManagementService).setTestMode("NYSE", true);
    }

    @Test
    @DisplayName("PATCH /exchanges/NYSE/test-mode - 200 OK disable test mode")
    void setTestMode_disable_returnsOk() throws Exception {
        doNothing().when(exchangeManagementService).setTestMode("NYSE", false);

        String payload = """
                {
                  "enabled": false
                }
                """;

        mockMvc.perform(patch("/exchanges/NYSE/test-mode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Test mode set to false for NYSE"));

        verify(exchangeManagementService).setTestMode("NYSE", false);
    }

    @Test
    @DisplayName("PATCH /exchanges/NYSE/test-mode - defaults to false when enabled key missing")
    void setTestMode_missingEnabledKey_defaultsFalse() throws Exception {
        doNothing().when(exchangeManagementService).setTestMode("NYSE", false);

        String payload = """
                {}
                """;

        mockMvc.perform(patch("/exchanges/NYSE/test-mode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Test mode set to false for NYSE"));

        verify(exchangeManagementService).setTestMode("NYSE", false);
    }

    @Test
    @DisplayName("PATCH /exchanges/INVALID/test-mode - 400 when exchange not found")
    void setTestMode_notFound() throws Exception {
        doThrow(new RuntimeException("Exchange not found: INVALID"))
                .when(exchangeManagementService).setTestMode("INVALID", true);

        String payload = """
                {
                  "enabled": true
                }
                """;

        mockMvc.perform(patch("/exchanges/INVALID/test-mode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Exchange not found: INVALID"));
    }

    // ══════════════════════════════════════════════════════════════════
    //  GET /exchanges/{acronym}/holidays
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /exchanges/NYSE/holidays - 200 OK with holidays")
    void getHolidays_returnsList() throws Exception {
        Set<LocalDate> holidays = Set.of(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 25)
        );
        when(exchangeManagementService.getHolidays("NYSE")).thenReturn(holidays);

        mockMvc.perform(get("/exchanges/NYSE/holidays")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        verify(exchangeManagementService).getHolidays("NYSE");
    }

    @Test
    @DisplayName("GET /exchanges/NYSE/holidays - 200 OK with empty set")
    void getHolidays_returnsEmpty() throws Exception {
        when(exchangeManagementService.getHolidays("NYSE")).thenReturn(Collections.emptySet());

        mockMvc.perform(get("/exchanges/NYSE/holidays")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /exchanges/INVALID/holidays - 400 when exchange not found")
    void getHolidays_notFound() throws Exception {
        when(exchangeManagementService.getHolidays("INVALID"))
                .thenThrow(new RuntimeException("Exchange not found: INVALID"));

        mockMvc.perform(get("/exchanges/INVALID/holidays")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Exchange not found: INVALID"));
    }

    // ══════════════════════════════════════════════════════════════════
    //  PUT /exchanges/{acronym}/holidays
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PUT /exchanges/NYSE/holidays - 200 OK sets holidays")
    void setHolidays_returnsOk() throws Exception {
        doNothing().when(exchangeManagementService).setHolidays(eq("NYSE"), any());

        String payload = """
                ["2026-01-01", "2026-07-04", "2026-12-25"]
                """;

        mockMvc.perform(put("/exchanges/NYSE/holidays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Set 3 holidays for NYSE"));

        verify(exchangeManagementService).setHolidays(eq("NYSE"), any());
    }

    @Test
    @DisplayName("PUT /exchanges/NYSE/holidays - 200 OK with empty set")
    void setHolidays_empty_returnsOk() throws Exception {
        doNothing().when(exchangeManagementService).setHolidays(eq("NYSE"), any());

        String payload = """
                []
                """;

        mockMvc.perform(put("/exchanges/NYSE/holidays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Set 0 holidays for NYSE"));
    }

    @Test
    @DisplayName("PUT /exchanges/INVALID/holidays - 400 when exchange not found")
    void setHolidays_notFound() throws Exception {
        doThrow(new RuntimeException("Exchange not found: INVALID"))
                .when(exchangeManagementService).setHolidays(eq("INVALID"), any());

        String payload = """
                ["2026-01-01"]
                """;

        mockMvc.perform(put("/exchanges/INVALID/holidays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Exchange not found: INVALID"));
    }

    // ══════════════════════════════════════════════════════════════════
    //  POST /exchanges/{acronym}/holidays
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("POST /exchanges/NYSE/holidays - 200 OK adds holiday")
    void addHoliday_returnsOk() throws Exception {
        doNothing().when(exchangeManagementService).addHoliday("NYSE", LocalDate.of(2026, 7, 4));

        String payload = """
                {
                  "date": "2026-07-04"
                }
                """;

        mockMvc.perform(post("/exchanges/NYSE/holidays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Added holiday 2026-07-04 for NYSE"));

        verify(exchangeManagementService).addHoliday("NYSE", LocalDate.of(2026, 7, 4));
    }

    @Test
    @DisplayName("POST /exchanges/INVALID/holidays - 400 when exchange not found")
    void addHoliday_notFound() throws Exception {
        doThrow(new RuntimeException("Exchange not found: INVALID"))
                .when(exchangeManagementService).addHoliday(eq("INVALID"), any());

        String payload = """
                {
                  "date": "2026-07-04"
                }
                """;

        mockMvc.perform(post("/exchanges/INVALID/holidays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Exchange not found: INVALID"));
    }

    // ══════════════════════════════════════════════════════════════════
    //  DELETE /exchanges/{acronym}/holidays/{date}
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("DELETE /exchanges/NYSE/holidays/2026-07-04 - 200 OK removes holiday")
    void removeHoliday_returnsOk() throws Exception {
        doNothing().when(exchangeManagementService).removeHoliday("NYSE", LocalDate.of(2026, 7, 4));

        mockMvc.perform(delete("/exchanges/NYSE/holidays/2026-07-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Removed holiday 2026-07-04 for NYSE"));

        verify(exchangeManagementService).removeHoliday("NYSE", LocalDate.of(2026, 7, 4));
    }

    @Test
    @DisplayName("DELETE /exchanges/INVALID/holidays/2026-07-04 - 400 when exchange not found")
    void removeHoliday_notFound() throws Exception {
        doThrow(new RuntimeException("Exchange not found: INVALID"))
                .when(exchangeManagementService).removeHoliday(eq("INVALID"), any());

        mockMvc.perform(delete("/exchanges/INVALID/holidays/2026-07-04"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Exchange not found: INVALID"));
    }
}
