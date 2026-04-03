package rs.raf.banka2_bek.tax.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import rs.raf.banka2_bek.auth.config.GlobalExceptionHandler;
import rs.raf.banka2_bek.tax.dto.TaxRecordDto;
import rs.raf.banka2_bek.tax.service.TaxService;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class TaxControllerTest {

    private MockMvc mockMvc;
    @Mock
    private TaxService taxService;

    @InjectMocks
    private TaxController taxController;

    private TaxRecordDto testRecord;
    private TaxRecordDto testRecord2;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(taxController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        testRecord = new TaxRecordDto(
                1L, 10L, "Marko Markovic", "CLIENT",
                new BigDecimal("50000.0000"),
                new BigDecimal("7500.0000"),
                new BigDecimal("2000.0000"),
                "RSD"
        );

        testRecord2 = new TaxRecordDto(
                2L, 20L, "Ana Jovanovic", "EMPLOYEE",
                new BigDecimal("120000.0000"),
                new BigDecimal("18000.0000"),
                new BigDecimal("5000.0000"),
                "RSD"
        );
    }

    // ══════════════════════════════════════════════════════════════════
    //  GET /tax
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /tax - 200 OK with all records")
    void getTaxRecords_returnsAll() throws Exception {
        when(taxService.getTaxRecords(null, null)).thenReturn(List.of(testRecord, testRecord2));

        mockMvc.perform(get("/tax")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].userName").value("Marko Markovic"))
                .andExpect(jsonPath("$[0].userType").value("CLIENT"))
                .andExpect(jsonPath("$[0].totalProfit").value(50000.0))
                .andExpect(jsonPath("$[0].taxOwed").value(7500.0))
                .andExpect(jsonPath("$[1].userName").value("Ana Jovanovic"))
                .andExpect(jsonPath("$[1].userType").value("EMPLOYEE"));

        verify(taxService).getTaxRecords(null, null);
    }

    @Test
    @DisplayName("GET /tax?userType=CLIENT - 200 OK filtered by userType")
    void getTaxRecords_filteredByUserType() throws Exception {
        when(taxService.getTaxRecords(null, "CLIENT")).thenReturn(List.of(testRecord));

        mockMvc.perform(get("/tax")
                        .param("userType", "CLIENT")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].userType").value("CLIENT"));

        verify(taxService).getTaxRecords(null, "CLIENT");
    }

    @Test
    @DisplayName("GET /tax?name=Marko - 200 OK filtered by name")
    void getTaxRecords_filteredByName() throws Exception {
        when(taxService.getTaxRecords("Marko", null)).thenReturn(List.of(testRecord));

        mockMvc.perform(get("/tax")
                        .param("name", "Marko")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].userName").value("Marko Markovic"));

        verify(taxService).getTaxRecords("Marko", null);
    }

    @Test
    @DisplayName("GET /tax?name=Marko&userType=CLIENT - 200 OK filtered by both")
    void getTaxRecords_filteredByBoth() throws Exception {
        when(taxService.getTaxRecords("Marko", "CLIENT")).thenReturn(List.of(testRecord));

        mockMvc.perform(get("/tax")
                        .param("name", "Marko")
                        .param("userType", "CLIENT")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        verify(taxService).getTaxRecords("Marko", "CLIENT");
    }

    @Test
    @DisplayName("GET /tax - 200 OK with empty list")
    void getTaxRecords_empty() throws Exception {
        when(taxService.getTaxRecords(null, null)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/tax")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ══════════════════════════════════════════════════════════════════
    //  GET /tax/my
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /tax/my - 200 OK with user's tax record")
    void getMyTaxRecord_returnsRecord() throws Exception {
        when(taxService.getMyTaxRecord("marko@banka.rs")).thenReturn(testRecord);

        mockMvc.perform(get("/tax/my")
                        .principal(createAuth("marko@banka.rs"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.userId").value(10))
                .andExpect(jsonPath("$.userName").value("Marko Markovic"))
                .andExpect(jsonPath("$.userType").value("CLIENT"))
                .andExpect(jsonPath("$.totalProfit").value(50000.0))
                .andExpect(jsonPath("$.taxOwed").value(7500.0))
                .andExpect(jsonPath("$.taxPaid").value(2000.0))
                .andExpect(jsonPath("$.currency").value("RSD"));

        verify(taxService).getMyTaxRecord("marko@banka.rs");
    }

    @Test
    @DisplayName("GET /tax/my - 200 OK with empty record for new user")
    void getMyTaxRecord_emptyForNewUser() throws Exception {
        TaxRecordDto emptyRecord = new TaxRecordDto(
                null, 0L, "Nepoznat", "CLIENT",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "RSD"
        );
        when(taxService.getMyTaxRecord("novi@banka.rs")).thenReturn(emptyRecord);

        mockMvc.perform(get("/tax/my")
                        .principal(createAuth("novi@banka.rs"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProfit").value(0))
                .andExpect(jsonPath("$.taxOwed").value(0))
                .andExpect(jsonPath("$.taxPaid").value(0));
    }

    @Test
    @DisplayName("GET /tax/my - 200 OK for employee user")
    void getMyTaxRecord_employeeUser() throws Exception {
        when(taxService.getMyTaxRecord("ana@banka.rs")).thenReturn(testRecord2);

        mockMvc.perform(get("/tax/my")
                        .principal(createAuth("ana@banka.rs"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userType").value("EMPLOYEE"))
                .andExpect(jsonPath("$.userName").value("Ana Jovanovic"));
    }

    // ══════════════════════════════════════════════════════════════════
    //  POST /tax/calculate
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("POST /tax/calculate - 200 OK triggers calculation")
    void triggerCalculation_returnsOk() throws Exception {
        doNothing().when(taxService).calculateTaxForAllUsers();

        mockMvc.perform(post("/tax/calculate")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(taxService).calculateTaxForAllUsers();
    }

    @Test
    @DisplayName("POST /tax/calculate - 400 when service throws")
    void triggerCalculation_serviceThrows() throws Exception {
        doThrow(new RuntimeException("Calculation failed"))
                .when(taxService).calculateTaxForAllUsers();

        mockMvc.perform(post("/tax/calculate")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Calculation failed"));
    }

    // ──────────────────────────────────────────────────────────────────
    //  Helper
    // ──────────────────────────────────────────────────────────────────

    private Authentication createAuth(String email) {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn(email);
        return auth;
    }
}
