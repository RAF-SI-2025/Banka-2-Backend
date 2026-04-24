package rs.raf.banka2_bek.loan.controller;

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
import org.springframework.security.core.userdetails.User;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import rs.raf.banka2_bek.auth.config.GlobalExceptionHandler;
import rs.raf.banka2_bek.loan.dto.LoanRequestResponseDto;
import rs.raf.banka2_bek.loan.dto.LoanResponseDto;
import rs.raf.banka2_bek.loan.service.LoanService;

import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
@DisplayName("LoanController coverage tests")
class LoanControllerCoverageTest {

    private MockMvc mockMvc;

    @Mock private LoanService loanService;

    @InjectMocks private LoanController loanController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(loanController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        // Clear prvo da bi se obezbedila izolacija od prethodnih testova
        // (CI filesystem test order moze dovesti do leakovanog SecurityContext-a).
        SecurityContextHolder.clearContext();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("klijent@example.com", null));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ---------- POST /loans/{id}/early-repayment ----------

    @Test
    @DisplayName("POST /loans/1/early-repayment - 200 OK")
    void earlyRepayment_success() throws Exception {
        LoanResponseDto dto = LoanResponseDto.builder()
                .id(1L)
                .loanNumber("LN-1")
                .amount(new BigDecimal("100000"))
                .remainingDebt(BigDecimal.ZERO)
                .status("PAID_OFF")
                .build();
        when(loanService.earlyRepayment(eq(1L), anyString())).thenReturn(dto);

        mockMvc.perform(post("/loans/1/early-repayment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("PAID_OFF"));

        verify(loanService).earlyRepayment(eq(1L), eq("klijent@example.com"));
    }

    @Test
    @DisplayName("POST /loans/1/early-repayment sa UserDetails principal-om - 200 OK")
    void earlyRepayment_withUserDetailsPrincipal_success() throws Exception {
        // Covers the UserDetails branch in getEmail().
        // Clear + set da bi se zamenio principal postavljen u @BeforeEach.
        SecurityContextHolder.clearContext();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        User.withUsername("ud@example.com").password("x").authorities("USER").build(),
                        null));

        LoanResponseDto dto = LoanResponseDto.builder().id(1L).status("PAID_OFF").build();
        when(loanService.earlyRepayment(eq(1L), eq("ud@example.com"))).thenReturn(dto);

        mockMvc.perform(post("/loans/1/early-repayment"))
                .andExpect(status().isOk());

        verify(loanService).earlyRepayment(eq(1L), eq("ud@example.com"));
    }

    @Test
    @DisplayName("POST /loans/999/early-repayment - 404 kad kredit ne postoji")
    void earlyRepayment_notFound_returns404() throws Exception {
        when(loanService.earlyRepayment(eq(999L), anyString()))
                .thenThrow(new EntityNotFoundException("Kredit nije pronadjen"));

        mockMvc.perform(post("/loans/999/early-repayment"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Kredit nije pronadjen"));
    }

    @Test
    @DisplayName("POST /loans/1/early-repayment - 400 kad je kredit vec otplacen")
    void earlyRepayment_alreadyPaid_returns400() throws Exception {
        when(loanService.earlyRepayment(eq(1L), anyString()))
                .thenThrow(new IllegalArgumentException("Kredit je vec otplacen"));

        mockMvc.perform(post("/loans/1/early-repayment"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Kredit je vec otplacen"));
    }

    // ---------- GET /loans/requests/my ----------

    @Test
    @DisplayName("GET /loans/requests/my - 200 OK sa listom")
    void getMyLoanRequests_success() throws Exception {
        LoanRequestResponseDto req = LoanRequestResponseDto.builder()
                .id(1L)
                .loanType("CASH")
                .amount(new BigDecimal("200000"))
                .status("PENDING")
                .build();
        when(loanService.getMyLoanRequests("klijent@example.com")).thenReturn(List.of(req));

        mockMvc.perform(get("/loans/requests/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("GET /loans/requests/my - 200 OK prazna lista")
    void getMyLoanRequests_empty() throws Exception {
        when(loanService.getMyLoanRequests(anyString())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/loans/requests/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
