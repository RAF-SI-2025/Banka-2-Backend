package rs.raf.banka2_bek.portfolio.controller;

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
import rs.raf.banka2_bek.portfolio.dto.PortfolioItemDto;
import rs.raf.banka2_bek.portfolio.dto.PortfolioSummaryDto;
import rs.raf.banka2_bek.portfolio.service.PortfolioService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class PortfolioControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PortfolioService portfolioService;

    @InjectMocks
    private PortfolioController portfolioController;

    private PortfolioItemDto testItem;
    private PortfolioItemDto testItem2;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(portfolioController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        testItem = new PortfolioItemDto();
        testItem.setId(1L);
        testItem.setListingTicker("AAPL");
        testItem.setListingName("Apple Inc.");
        testItem.setListingType("STOCK");
        testItem.setQuantity(100);
        testItem.setAverageBuyPrice(new BigDecimal("150.00"));
        testItem.setCurrentPrice(new BigDecimal("175.50"));
        testItem.setProfit(new BigDecimal("2550.00"));
        testItem.setProfitPercent(new BigDecimal("17.00"));
        testItem.setPublicQuantity(50);
        testItem.setLastModified(LocalDateTime.of(2026, 3, 15, 10, 30));

        testItem2 = new PortfolioItemDto();
        testItem2.setId(2L);
        testItem2.setListingTicker("MSFT");
        testItem2.setListingName("Microsoft Corporation");
        testItem2.setListingType("STOCK");
        testItem2.setQuantity(50);
        testItem2.setAverageBuyPrice(new BigDecimal("300.00"));
        testItem2.setCurrentPrice(new BigDecimal("320.00"));
        testItem2.setProfit(new BigDecimal("1000.00"));
        testItem2.setProfitPercent(new BigDecimal("6.67"));
        testItem2.setPublicQuantity(0);
        testItem2.setLastModified(LocalDateTime.of(2026, 3, 16, 14, 0));
    }

    // ══════════════════════════════════════════════════════════════════
    //  GET /portfolio/my
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /portfolio/my - 200 OK with portfolio items")
    void getMyPortfolio_returnsList() throws Exception {
        when(portfolioService.getMyPortfolio()).thenReturn(List.of(testItem, testItem2));

        mockMvc.perform(get("/portfolio/my")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].listingTicker").value("AAPL"))
                .andExpect(jsonPath("$[0].listingName").value("Apple Inc."))
                .andExpect(jsonPath("$[0].listingType").value("STOCK"))
                .andExpect(jsonPath("$[0].quantity").value(100))
                .andExpect(jsonPath("$[0].averageBuyPrice").value(150.00))
                .andExpect(jsonPath("$[0].currentPrice").value(175.50))
                .andExpect(jsonPath("$[0].profit").value(2550.00))
                .andExpect(jsonPath("$[0].profitPercent").value(17.00))
                .andExpect(jsonPath("$[0].publicQuantity").value(50))
                .andExpect(jsonPath("$[1].listingTicker").value("MSFT"));

        verify(portfolioService).getMyPortfolio();
    }

    @Test
    @DisplayName("GET /portfolio/my - 200 OK with empty portfolio")
    void getMyPortfolio_returnsEmptyList() throws Exception {
        when(portfolioService.getMyPortfolio()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/portfolio/my")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /portfolio/my - 400 when user not found")
    void getMyPortfolio_userNotFound() throws Exception {
        when(portfolioService.getMyPortfolio())
                .thenThrow(new RuntimeException("Korisnik nije pronadjen: unknown@banka.rs"));

        mockMvc.perform(get("/portfolio/my")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Korisnik nije pronadjen: unknown@banka.rs"));
    }

    // ══════════════════════════════════════════════════════════════════
    //  GET /portfolio/summary
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /portfolio/summary - 200 OK with summary")
    void getSummary_returnsSummary() throws Exception {
        PortfolioSummaryDto summary = new PortfolioSummaryDto(
                new BigDecimal("33550.00"),
                new BigDecimal("3550.00"),
                BigDecimal.ZERO,
                new BigDecimal("532.50")
        );
        when(portfolioService.getSummary()).thenReturn(summary);

        mockMvc.perform(get("/portfolio/summary")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalValue").value(33550.00))
                .andExpect(jsonPath("$.totalProfit").value(3550.00))
                .andExpect(jsonPath("$.paidTaxThisYear").value(0))
                .andExpect(jsonPath("$.unpaidTaxThisMonth").value(532.50));

        verify(portfolioService).getSummary();
    }

    @Test
    @DisplayName("GET /portfolio/summary - 200 OK with zero values for empty portfolio")
    void getSummary_emptyPortfolio() throws Exception {
        PortfolioSummaryDto emptySummary = new PortfolioSummaryDto(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        );
        when(portfolioService.getSummary()).thenReturn(emptySummary);

        mockMvc.perform(get("/portfolio/summary")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalValue").value(0))
                .andExpect(jsonPath("$.totalProfit").value(0))
                .andExpect(jsonPath("$.unpaidTaxThisMonth").value(0));
    }

    @Test
    @DisplayName("GET /portfolio/summary - 400 when user not found")
    void getSummary_userNotFound() throws Exception {
        when(portfolioService.getSummary())
                .thenThrow(new RuntimeException("Korisnik nije pronadjen: unknown@banka.rs"));

        mockMvc.perform(get("/portfolio/summary")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Korisnik nije pronadjen: unknown@banka.rs"));
    }

    // ══════════════════════════════════════════════════════════════════
    //  PATCH /portfolio/{id}/public
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PATCH /portfolio/1/public - 200 OK sets public quantity")
    void setPublicQuantity_returnsUpdated() throws Exception {
        PortfolioItemDto updated = new PortfolioItemDto();
        updated.setId(1L);
        updated.setListingTicker("AAPL");
        updated.setListingName("Apple Inc.");
        updated.setListingType("STOCK");
        updated.setQuantity(100);
        updated.setAverageBuyPrice(new BigDecimal("150.00"));
        updated.setCurrentPrice(new BigDecimal("175.50"));
        updated.setProfit(new BigDecimal("2550.00"));
        updated.setProfitPercent(new BigDecimal("17.00"));
        updated.setPublicQuantity(25);

        when(portfolioService.setPublicQuantity(1L, 25)).thenReturn(updated);

        String payload = """
                {
                  "quantity": 25
                }
                """;

        mockMvc.perform(patch("/portfolio/1/public")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.publicQuantity").value(25))
                .andExpect(jsonPath("$.listingTicker").value("AAPL"));

        verify(portfolioService).setPublicQuantity(1L, 25);
    }

    @Test
    @DisplayName("PATCH /portfolio/1/public - 200 OK sets quantity to zero")
    void setPublicQuantity_setToZero() throws Exception {
        PortfolioItemDto updated = new PortfolioItemDto();
        updated.setId(1L);
        updated.setListingTicker("AAPL");
        updated.setPublicQuantity(0);

        when(portfolioService.setPublicQuantity(1L, 0)).thenReturn(updated);

        String payload = """
                {
                  "quantity": 0
                }
                """;

        mockMvc.perform(patch("/portfolio/1/public")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicQuantity").value(0));

        verify(portfolioService).setPublicQuantity(1L, 0);
    }

    @Test
    @DisplayName("PATCH /portfolio/1/public - defaults to 0 when quantity key missing")
    void setPublicQuantity_missingKey_defaultsToZero() throws Exception {
        PortfolioItemDto updated = new PortfolioItemDto();
        updated.setId(1L);
        updated.setPublicQuantity(0);

        when(portfolioService.setPublicQuantity(1L, 0)).thenReturn(updated);

        String payload = """
                {}
                """;

        mockMvc.perform(patch("/portfolio/1/public")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicQuantity").value(0));

        verify(portfolioService).setPublicQuantity(1L, 0);
    }

    @Test
    @DisplayName("PATCH /portfolio/999/public - 400 when portfolio item not found")
    void setPublicQuantity_notFound() throws Exception {
        when(portfolioService.setPublicQuantity(eq(999L), eq(10)))
                .thenThrow(new RuntimeException("Portfolio stavka nije pronadjena: 999"));

        String payload = """
                {
                  "quantity": 10
                }
                """;

        mockMvc.perform(patch("/portfolio/999/public")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Portfolio stavka nije pronadjena: 999"));
    }

    @Test
    @DisplayName("PATCH /portfolio/1/public - 400 when user doesn't own portfolio item")
    void setPublicQuantity_notOwner() throws Exception {
        when(portfolioService.setPublicQuantity(eq(1L), eq(10)))
                .thenThrow(new RuntimeException("Nemate pristup ovoj portfolio stavci."));

        String payload = """
                {
                  "quantity": 10
                }
                """;

        mockMvc.perform(patch("/portfolio/1/public")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Nemate pristup ovoj portfolio stavci."));
    }

    @Test
    @DisplayName("PATCH /portfolio/1/public - 400 when quantity exceeds total")
    void setPublicQuantity_exceedsTotal() throws Exception {
        when(portfolioService.setPublicQuantity(eq(1L), eq(999)))
                .thenThrow(new IllegalArgumentException("Javna kolicina mora biti izmedju 0 i 100"));

        String payload = """
                {
                  "quantity": 999
                }
                """;

        mockMvc.perform(patch("/portfolio/1/public")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Javna kolicina mora biti izmedju 0 i 100"));
    }

    @Test
    @DisplayName("PATCH /portfolio/1/public - 400 when quantity is negative")
    void setPublicQuantity_negative() throws Exception {
        when(portfolioService.setPublicQuantity(eq(1L), eq(-5)))
                .thenThrow(new IllegalArgumentException("Javna kolicina mora biti izmedju 0 i 100"));

        String payload = """
                {
                  "quantity": -5
                }
                """;

        mockMvc.perform(patch("/portfolio/1/public")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Javna kolicina mora biti izmedju 0 i 100"));
    }
}
