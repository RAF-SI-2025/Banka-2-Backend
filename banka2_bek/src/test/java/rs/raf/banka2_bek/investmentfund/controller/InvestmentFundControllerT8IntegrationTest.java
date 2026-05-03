package rs.raf.banka2_bek.investmentfund.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import rs.raf.banka2_bek.auth.util.UserContext;
import rs.raf.banka2_bek.auth.util.UserResolver;
import rs.raf.banka2_bek.auth.util.UserRole;
import rs.raf.banka2_bek.investmentfund.controller.exception_handler.InvestmentFundExceptionHandler;
import rs.raf.banka2_bek.investmentfund.dto.InvestmentFundDtos.ClientFundPositionDto;
import rs.raf.banka2_bek.investmentfund.dto.InvestmentFundDtos.ClientFundTransactionDto;
import rs.raf.banka2_bek.investmentfund.dto.InvestmentFundDtos.InvestFundDto;
import rs.raf.banka2_bek.investmentfund.dto.InvestmentFundDtos.WithdrawFundDto;
import rs.raf.banka2_bek.investmentfund.service.InvestmentFundService;

import java.math.BigDecimal;
import java.util.Map;

import static org.hamcrest.Matchers.comparesEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class InvestmentFundControllerT8IntegrationTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private InvestmentFundService investmentFundService;
    @Mock private UserResolver userResolver;

    @InjectMocks
    private InvestmentFundController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new InvestmentFundExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("T8 INTEGRATION: POST /funds/{id}/invest prosledjuje request servisu i vraca poziciju")
    void investEndpoint_returnsPosition() throws Exception {
        Long fundId = 1L;
        Long supervisorId = 5L;

        when(userResolver.resolveCurrent()).thenReturn(new UserContext(supervisorId, UserRole.EMPLOYEE));

        ClientFundPositionDto response = new ClientFundPositionDto();
        response.setId(1L);
        response.setFundId(fundId);
        response.setFundName("Banka 2 Stable Income");
        response.setUserId(5L);
        response.setUserRole(UserRole.CLIENT);
        response.setUserName("Banka 2 d.o.o.");
        response.setTotalInvested(new BigDecimal("10000.0000"));

        when(investmentFundService.invest(eq(fundId), any(InvestFundDto.class), eq(supervisorId), eq(UserRole.EMPLOYEE)))
                .thenReturn(response);

        mockMvc.perform(post("/funds/{id}/invest", fundId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amount", 10000,
                                "currency", "RSD",
                                "sourceAccountId", 14
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.fundId").value(1))
                .andExpect(jsonPath("$.fundName").value("Banka 2 Stable Income"))
                .andExpect(jsonPath("$.userRole").value(UserRole.CLIENT))
                .andExpect(jsonPath("$.totalInvested", comparesEqualTo(10000.0000)));

        ArgumentCaptor<InvestFundDto> dtoCaptor = ArgumentCaptor.forClass(InvestFundDto.class);
        verify(investmentFundService).invest(eq(fundId), dtoCaptor.capture(), eq(supervisorId), eq(UserRole.EMPLOYEE));

        InvestFundDto dto = dtoCaptor.getValue();
        assertEquals(0, new BigDecimal("10000").compareTo(dto.getAmount()));
        assertEquals("RSD", dto.getCurrency());
        assertEquals(14L, dto.getSourceAccountId());
    }

    @Test
    @DisplayName("T8 INTEGRATION: POST /funds/{id}/withdraw prosledjuje request servisu i vraca COMPLETED tx")
    void withdrawEndpoint_returnsCompletedTransaction() throws Exception {
        Long fundId = 1L;
        Long supervisorId = 5L;

        when(userResolver.resolveCurrent()).thenReturn(new UserContext(supervisorId, UserRole.EMPLOYEE));

        ClientFundTransactionDto response = new ClientFundTransactionDto(
                2L,
                fundId,
                "Banka 2 Stable Income",
                5L,
                "Banka 2 d.o.o.",
                new BigDecimal("5000.0000"),
                "222000100000000120",
                false,
                "COMPLETED",
                null,
                null,
                null
        );

        when(investmentFundService.withdraw(eq(fundId), any(WithdrawFundDto.class), eq(supervisorId), eq(UserRole.EMPLOYEE)))
                .thenReturn(response);

        mockMvc.perform(post("/funds/{id}/withdraw", fundId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amount", 5000,
                                "destinationAccountId", 14
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.fundId").value(1))
                .andExpect(jsonPath("$.fundName").value("Banka 2 Stable Income"))
                .andExpect(jsonPath("$.amountRsd", comparesEqualTo(5000.0000)))
                .andExpect(jsonPath("$.inflow").value(false))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        ArgumentCaptor<WithdrawFundDto> dtoCaptor = ArgumentCaptor.forClass(WithdrawFundDto.class);
        verify(investmentFundService).withdraw(eq(fundId), dtoCaptor.capture(), eq(supervisorId), eq(UserRole.EMPLOYEE));

        WithdrawFundDto dto = dtoCaptor.getValue();
        assertEquals(0, new BigDecimal("5000").compareTo(dto.getAmount()));
        assertEquals(14L, dto.getDestinationAccountId());
    }

    @Test
    @DisplayName("T8 INTEGRATION: POST /funds/{id}/withdraw moze da vrati PENDING kada servis pokrene T9 likvidaciju")
    void withdrawEndpoint_returnsPendingTransaction() throws Exception {
        Long fundId = 1L;
        Long clientId = 10L;

        when(userResolver.resolveCurrent()).thenReturn(new UserContext(clientId, UserRole.CLIENT));

        ClientFundTransactionDto response = new ClientFundTransactionDto(
                3L,
                fundId,
                "Banka 2 Stable Income",
                clientId,
                "Stefan Jovanovic",
                new BigDecimal("5000.0000"),
                "222000112345678911",
                false,
                "PENDING",
                null,
                null,
                "Nedovoljno likvidnih sredstava; pokrenuta automatska likvidacija hartija."
        );

        when(investmentFundService.withdraw(eq(fundId), any(WithdrawFundDto.class), eq(clientId), eq(UserRole.CLIENT)))
                .thenReturn(response);

        mockMvc.perform(post("/funds/{id}/withdraw", fundId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amount", 5000,
                                "destinationAccountId", 1
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.inflow").value(false))
                .andExpect(jsonPath("$.failureReason").exists());
    }

    @Test
    @DisplayName("T8 INTEGRATION: POST /funds/{id}/invest radi i za CLIENT korisnika")
    void investEndpoint_clientUser_returnsPositionAndPassesClientContext() throws Exception {
        Long fundId = 1L;
        Long clientId = 10L;

        when(userResolver.resolveCurrent()).thenReturn(new UserContext(clientId, UserRole.CLIENT));

        ClientFundPositionDto response = new ClientFundPositionDto();
        response.setId(10L);
        response.setFundId(fundId);
        response.setFundName("Banka 2 Stable Income");
        response.setUserId(clientId);
        response.setUserRole(UserRole.CLIENT);
        response.setUserName("Stefan Jovanovic");
        response.setTotalInvested(new BigDecimal("15000.0000"));

        when(investmentFundService.invest(eq(fundId), any(InvestFundDto.class), eq(clientId), eq(UserRole.CLIENT)))
                .thenReturn(response);

        mockMvc.perform(post("/funds/{id}/invest", fundId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amount", 15000,
                                "currency", "RSD",
                                "sourceAccountId", 1
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.fundId").value(1))
                .andExpect(jsonPath("$.userId").value(10))
                .andExpect(jsonPath("$.userRole").value(UserRole.CLIENT))
                .andExpect(jsonPath("$.totalInvested", comparesEqualTo(15000.0000)));

        ArgumentCaptor<InvestFundDto> dtoCaptor = ArgumentCaptor.forClass(InvestFundDto.class);
        verify(investmentFundService).invest(eq(fundId), dtoCaptor.capture(), eq(clientId), eq(UserRole.CLIENT));

        InvestFundDto dto = dtoCaptor.getValue();
        assertEquals(0, new BigDecimal("15000").compareTo(dto.getAmount()));
        assertEquals("RSD", dto.getCurrency());
        assertEquals(1L, dto.getSourceAccountId());
    }

    @Test
    @DisplayName("T8 INTEGRATION: POST /funds/{id}/invest vraca 400 kada servis odbije minimalni ulog")
    void investEndpoint_whenServiceRejectsMinimumContribution_returnsBadRequest() throws Exception {
        Long fundId = 1L;
        Long clientId = 10L;

        when(userResolver.resolveCurrent()).thenReturn(new UserContext(clientId, UserRole.CLIENT));

        when(investmentFundService.invest(eq(fundId), any(InvestFundDto.class), eq(clientId), eq(UserRole.CLIENT)))
                .thenThrow(new IllegalArgumentException("Iznos uplate mora biti najmanje 1000 RSD."));

        mockMvc.perform(post("/funds/{id}/invest", fundId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amount", 500,
                                "currency", "RSD",
                                "sourceAccountId", 1
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Iznos uplate mora biti najmanje 1000 RSD."));
    }

    @Test
    @DisplayName("T8 INTEGRATION: POST /funds/{id}/invest prosledjuje EUR valutu servisu")
    void investEndpoint_preservesForeignCurrencyPayload() throws Exception {
        Long fundId = 1L;
        Long clientId = 10L;

        when(userResolver.resolveCurrent()).thenReturn(new UserContext(clientId, UserRole.CLIENT));

        ClientFundPositionDto response = new ClientFundPositionDto();
        response.setId(11L);
        response.setFundId(fundId);
        response.setFundName("Banka 2 Stable Income");
        response.setUserId(clientId);
        response.setUserRole(UserRole.CLIENT);
        response.setUserName("Stefan Jovanovic");
        response.setTotalInvested(new BigDecimal("11700.0000"));

        when(investmentFundService.invest(eq(fundId), any(InvestFundDto.class), eq(clientId), eq(UserRole.CLIENT)))
                .thenReturn(response);

        mockMvc.perform(post("/funds/{id}/invest", fundId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amount", 100,
                                "currency", "EUR",
                                "sourceAccountId", 3
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.totalInvested", comparesEqualTo(11700.0000)));

        ArgumentCaptor<InvestFundDto> dtoCaptor = ArgumentCaptor.forClass(InvestFundDto.class);
        verify(investmentFundService).invest(eq(fundId), dtoCaptor.capture(), eq(clientId), eq(UserRole.CLIENT));

        InvestFundDto dto = dtoCaptor.getValue();
        assertEquals(0, new BigDecimal("100").compareTo(dto.getAmount()));
        assertEquals("EUR", dto.getCurrency());
        assertEquals(3L, dto.getSourceAccountId());
    }

    @Test
    @DisplayName("T8 INTEGRATION: POST /funds/{id}/withdraw vraca 400 kada korisnik nema poziciju")
    void withdrawEndpoint_whenServiceRejectsMissingPosition_returnsBadRequest() throws Exception {
        Long fundId = 1L;
        Long clientId = 10L;

        when(userResolver.resolveCurrent()).thenReturn(new UserContext(clientId, UserRole.CLIENT));

        when(investmentFundService.withdraw(eq(fundId), any(WithdrawFundDto.class), eq(clientId), eq(UserRole.CLIENT)))
                .thenThrow(new IllegalArgumentException("Nemate poziciju u fondu Banka 2 Stable Income."));

        mockMvc.perform(post("/funds/{id}/withdraw", fundId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amount", 5000,
                                "destinationAccountId", 1
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Nemate poziciju u fondu Banka 2 Stable Income."));
    }

    @Test
    @DisplayName("T8 INTEGRATION: POST /funds/{id}/withdraw vraca 400 kada je trazeni iznos veci od pozicije")
    void withdrawEndpoint_whenAmountGreaterThanPosition_returnsBadRequest() throws Exception {
        Long fundId = 1L;
        Long clientId = 10L;

        when(userResolver.resolveCurrent()).thenReturn(new UserContext(clientId, UserRole.CLIENT));

        when(investmentFundService.withdraw(eq(fundId), any(WithdrawFundDto.class), eq(clientId), eq(UserRole.CLIENT)))
                .thenThrow(new IllegalArgumentException("Trazeni iznos je veci od pozicije u fondu."));

        mockMvc.perform(post("/funds/{id}/withdraw", fundId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amount", 999999,
                                "destinationAccountId", 1
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Trazeni iznos je veci od pozicije u fondu."));
    }
}