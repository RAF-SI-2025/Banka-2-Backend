package rs.raf.banka2_bek.payment.controller;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import rs.raf.banka2_bek.auth.config.GlobalExceptionHandler;
import rs.raf.banka2_bek.otp.service.OtpService;
import rs.raf.banka2_bek.payment.dto.CreatePaymentRequestDto;
import rs.raf.banka2_bek.payment.dto.PaymentDirection;
import rs.raf.banka2_bek.payment.dto.PaymentListItemDto;
import rs.raf.banka2_bek.payment.dto.PaymentResponseDto;
import rs.raf.banka2_bek.payment.model.PaymentCode;
import rs.raf.banka2_bek.payment.model.PaymentStatus;
import rs.raf.banka2_bek.payment.service.PaymentService;
import rs.raf.banka2_bek.transaction.dto.TransactionListItemDto;
import rs.raf.banka2_bek.transaction.dto.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
@DisplayName("PaymentController coverage tests")
class PaymentControllerCoverageTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final Authentication auth = new UsernamePasswordAuthenticationToken("client@example.com", null);

    @Mock
    private PaymentService paymentService;

    @Mock
    private OtpService otpService;

    @InjectMocks
    private PaymentController paymentController;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        SimpleModule pageModule = new SimpleModule();
        pageModule.addSerializer(Page.class, new JsonSerializer<Page>() {
            @Override
            public void serialize(Page value, JsonGenerator gen, SerializerProvider serializers) throws java.io.IOException {
                gen.writeStartObject();
                gen.writeObjectField("content", value.getContent());
                gen.writeNumberField("totalElements", value.getTotalElements());
                gen.writeNumberField("totalPages", value.getTotalPages());
                gen.writeNumberField("number", value.getNumber());
                gen.writeNumberField("size", value.getSize());
                gen.writeEndObject();
            }
        });
        mapper.registerModule(pageModule);
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(mapper);

        mockMvc = MockMvcBuilders
                .standaloneSetup(paymentController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(converter, new org.springframework.http.converter.ByteArrayHttpMessageConverter())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private CreatePaymentRequestDto validDto() {
        CreatePaymentRequestDto dto = new CreatePaymentRequestDto();
        dto.setFromAccount("1234567890");
        dto.setToAccount("0987654321");
        dto.setAmount(new BigDecimal("100.00"));
        dto.setPaymentCode(PaymentCode.CODE_289);
        dto.setReferenceNumber("REF-1");
        dto.setDescription("Test payment");
        dto.setRecipientName("John Doe");
        dto.setOtpCode("123456");
        return dto;
    }

    private PaymentResponseDto sampleResponse() {
        return PaymentResponseDto.builder()
                .id(1L)
                .orderNumber("ORD-1")
                .fromAccount("1234567890")
                .toAccount("0987654321")
                .amount(new BigDecimal("100.00"))
                .fee(BigDecimal.ZERO)
                .currency("RSD")
                .paymentCode("OTHER")
                .referenceNumber("REF-1")
                .description("Test")
                .recipientName("John Doe")
                .direction(PaymentDirection.OUTGOING)
                .status(PaymentStatus.COMPLETED)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ---------- POST /payments ----------

    @Test
    @DisplayName("POST /payments — 201 Created kad je OTP validan")
    void createPayment_success() throws Exception {
        when(otpService.verify(eq("client@example.com"), eq("123456")))
                .thenReturn(Map.of("verified", true));
        when(paymentService.createPayment(any())).thenReturn(sampleResponse());

        mockMvc.perform(post("/payments")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validDto())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));

        verify(paymentService).createPayment(any());
    }

    @Test
    @DisplayName("POST /payments — 403 Forbidden kad OTP nije verified")
    void createPayment_otpNotVerified_returns403() throws Exception {
        when(otpService.verify(anyString(), anyString()))
                .thenReturn(Map.of("verified", false));

        mockMvc.perform(post("/payments")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validDto())))
                .andExpect(status().isForbidden());

        verify(paymentService, never()).createPayment(any());
    }

    @Test
    @DisplayName("POST /payments — 401 Unauthorized kad nema authentication")
    void createPayment_noAuth_returns401() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validDto())))
                .andExpect(status().isUnauthorized());

        verify(paymentService, never()).createPayment(any());
    }

    @Test
    @DisplayName("POST /payments — 400 Bad Request kad je DTO nevalidan (prazan fromAccount)")
    void createPayment_invalidDto_returns400() throws Exception {
        CreatePaymentRequestDto bad = validDto();
        bad.setFromAccount("");

        mockMvc.perform(post("/payments")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /payments — 400 kad amount nije zadat")
    void createPayment_nullAmount_returns400() throws Exception {
        CreatePaymentRequestDto bad = validDto();
        bad.setAmount(null);

        mockMvc.perform(post("/payments")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }

    // ---------- GET /payments ----------

    @Test
    @DisplayName("GET /payments — 200 sa paginated rezultatima")
    void getPayments_success() throws Exception {
        Page<PaymentListItemDto> page = new PageImpl<>(Collections.emptyList());
        when(paymentService.getPayments(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(page);

        mockMvc.perform(get("/payments"))
                .andExpect(status().isOk());

        verify(paymentService).getPayments(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("GET /payments — prosledjuje sve filter parametre")
    void getPayments_withAllFilters() throws Exception {
        Page<PaymentListItemDto> page = new PageImpl<>(Collections.emptyList());
        when(paymentService.getPayments(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(page);

        mockMvc.perform(get("/payments")
                        .param("fromDate", "2026-01-01")
                        .param("toDate", "2026-12-31")
                        .param("accountNumber", "1234567890")
                        .param("minAmount", "10")
                        .param("maxAmount", "1000")
                        .param("status", "COMPLETED"))
                .andExpect(status().isOk());
    }

    // ---------- GET /payments/{paymentId} ----------

    @Test
    @DisplayName("GET /payments/{id} — 200 sa payment detaljima")
    void getPaymentById_success() throws Exception {
        when(paymentService.getPaymentById(1L)).thenReturn(sampleResponse());

        mockMvc.perform(get("/payments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    // ---------- GET /payments/{id}/receipt ----------

    @Test
    @DisplayName("GET /payments/{id}/receipt — 200 PDF bytes")
    void getPaymentReceipt_success() throws Exception {
        byte[] pdf = new byte[] {1, 2, 3, 4};
        when(paymentService.getPaymentReceipt(7L)).thenReturn(pdf);

        mockMvc.perform(get("/payments/7/receipt"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"transaction-receipt-7.pdf\""));
    }

    // ---------- GET /payments/history ----------

    @Test
    @DisplayName("GET /payments/history — 200 bez filtera")
    void getPaymentHistory_success() throws Exception {
        Page<TransactionListItemDto> page = new PageImpl<>(Collections.emptyList());
        when(paymentService.getPaymentHistory(any(), any(), any(), any(), any(), any()))
                .thenReturn(page);

        mockMvc.perform(get("/payments/history"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /payments/history — 200 sa svim filterima")
    void getPaymentHistory_withFilters() throws Exception {
        Page<TransactionListItemDto> page = new PageImpl<>(Collections.emptyList());
        when(paymentService.getPaymentHistory(any(), any(), any(), any(), any(), any()))
                .thenReturn(page);

        mockMvc.perform(get("/payments/history")
                        .param("fromDate", "2026-01-01")
                        .param("toDate", "2026-12-31")
                        .param("minAmount", "10")
                        .param("maxAmount", "1000")
                        .param("type", "PAYMENT"))
                .andExpect(status().isOk());
    }

    // ---------- POST /payments/request-otp ----------

    @Test
    @DisplayName("POST /payments/request-otp — 200 i poziva otpService.generateAndSend")
    void requestOtp_success() throws Exception {
        mockMvc.perform(post("/payments/request-otp").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(true));

        verify(otpService).generateAndSend("client@example.com");
    }

    @Test
    @DisplayName("POST /payments/request-otp — 401 kad nema auth")
    void requestOtp_noAuth_returns401() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(post("/payments/request-otp"))
                .andExpect(status().isUnauthorized());

        verify(otpService, never()).generateAndSend(anyString());
    }

    // ---------- POST /payments/request-otp-email ----------

    @Test
    @DisplayName("POST /payments/request-otp-email — 200 poziva generateAndSendViaEmail")
    void requestOtpViaEmail_success() throws Exception {
        mockMvc.perform(post("/payments/request-otp-email").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(true));

        verify(otpService).generateAndSendViaEmail("client@example.com");
    }

    @Test
    @DisplayName("POST /payments/request-otp-email — 401 kad nema auth")
    void requestOtpViaEmail_noAuth_returns401() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(post("/payments/request-otp-email"))
                .andExpect(status().isUnauthorized());

        verify(otpService, never()).generateAndSendViaEmail(anyString());
    }

    // ---------- GET /payments/my-otp ----------

    @Test
    @DisplayName("GET /payments/my-otp — 200 vraca active OTP mapu")
    void getMyOtp_success() throws Exception {
        when(otpService.getActiveOtp("client@example.com"))
                .thenReturn(Map.of("code", "123456", "active", true));

        mockMvc.perform(get("/payments/my-otp").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("123456"));
    }

    @Test
    @DisplayName("GET /payments/my-otp — 401 kad nema auth")
    void getMyOtp_noAuth_returns401() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/payments/my-otp"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- POST /payments/verify ----------

    @Test
    @DisplayName("POST /payments/verify — 200 sa verifikacijom")
    void verifyPayment_success() throws Exception {
        when(otpService.verify(eq("client@example.com"), eq("123456")))
                .thenReturn(Map.of("verified", true));

        mockMvc.perform(post("/payments/verify")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true));
    }

    @Test
    @DisplayName("POST /payments/verify — 200 sa default kodom kad nije prosledjen")
    void verifyPayment_noCode_usesDefault() throws Exception {
        when(otpService.verify(eq("client@example.com"), eq("")))
                .thenReturn(Map.of("verified", false));

        mockMvc.perform(post("/payments/verify")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(false));
    }

    @Test
    @DisplayName("POST /payments/verify — 401 kad nema auth")
    void verifyPayment_noAuth_returns401() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(post("/payments/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"123456\"}"))
                .andExpect(status().isUnauthorized());
    }
}
