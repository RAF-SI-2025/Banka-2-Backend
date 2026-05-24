package rs.raf.banka2_bek.order.controller;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
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
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import rs.raf.banka2_bek.auth.config.GlobalExceptionHandler;
import rs.raf.banka2_bek.order.dto.OrderDto;
import rs.raf.banka2_bek.order.service.OrderService;
import rs.raf.banka2_bek.otp.service.OtpService;

import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
@DisplayName("OrderController coverage tests")
class OrderControllerCoverageTest {

    private MockMvc mockMvc;

    @Mock private OrderService orderService;
    @Mock private OtpService otpService;

    @InjectMocks private OrderController orderController;

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
                .standaloneSetup(orderController)
                .setMessageConverters(converter)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("supervisor@banka.rs", null));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private OrderDto sampleOrder(Long id, String status) {
        OrderDto dto = new OrderDto();
        dto.setId(id);
        dto.setListingId(1L);
        dto.setListingTicker("AAPL");
        dto.setOrderType("MARKET");
        dto.setDirection("BUY");
        dto.setQuantity(5);
        dto.setContractSize(1);
        dto.setPricePerUnit(new BigDecimal("150.00"));
        dto.setApproximatePrice(new BigDecimal("750.00"));
        dto.setStatus(status);
        dto.setDone(false);
        dto.setRemainingPortions(5);
        dto.setUserRole("CLIENT");
        return dto;
    }

    // ---------- GET /orders ----------

    @Test
    @DisplayName("GET /orders - 200 sa default parametrima")
    void getAllOrders_defaultParams() throws Exception {
        Page<OrderDto> page = new PageImpl<>(List.of(sampleOrder(1L, "PENDING")));
        when(orderService.getAllOrders("ALL", 0, 20)).thenReturn(page);

        mockMvc.perform(get("/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1));
    }

    @Test
    @DisplayName("GET /orders?status=PENDING&page=1&size=5 - 200")
    void getAllOrders_withFilters() throws Exception {
        Page<OrderDto> page = new PageImpl<>(Collections.emptyList());
        when(orderService.getAllOrders("PENDING", 1, 5)).thenReturn(page);

        mockMvc.perform(get("/orders")
                        .param("status", "PENDING")
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk());

        verify(orderService).getAllOrders("PENDING", 1, 5);
    }

    // ---------- GET /orders/my ----------

    @Test
    @DisplayName("GET /orders/my - 200 sa paginated")
    void getMyOrders_success() throws Exception {
        Page<OrderDto> page = new PageImpl<>(List.of(sampleOrder(2L, "DONE")));
        when(orderService.getMyOrders(eq(0), eq(20), isNull(), isNull(), isNull(), isNull())).thenReturn(page);

        mockMvc.perform(get("/orders/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(2));
    }

    // ---------- GET /orders/{id} ----------

    @Test
    @DisplayName("GET /orders/1 - 200 sa orderom")
    void getOrderById_success() throws Exception {
        when(orderService.getOrderById(1L)).thenReturn(sampleOrder(1L, "APPROVED"));

        mockMvc.perform(get("/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @DisplayName("GET /orders/999 - 404 kad order ne postoji")
    void getOrderById_notFound() throws Exception {
        when(orderService.getOrderById(999L))
                .thenThrow(new EntityNotFoundException("Order not found"));

        mockMvc.perform(get("/orders/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Order not found"));
    }

    // ---------- PATCH /orders/{id}/approve ----------

    @Test
    @DisplayName("PATCH /orders/5/approve - 200 OK")
    void approveOrder_success() throws Exception {
        OrderDto approved = sampleOrder(5L, "APPROVED");
        approved.setApprovedBy("supervisor@banka.rs");
        when(orderService.approveOrder(5L)).thenReturn(approved);

        mockMvc.perform(patch("/orders/5/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvedBy").value("supervisor@banka.rs"));

        verify(orderService).approveOrder(5L);
    }

    @Test
    @DisplayName("PATCH /orders/999/approve - 404 kad order ne postoji")
    void approveOrder_notFound() throws Exception {
        when(orderService.approveOrder(eq(999L)))
                .thenThrow(new EntityNotFoundException("Order not found"));

        mockMvc.perform(patch("/orders/999/approve"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PATCH /orders/5/approve - 400 kad nije u PENDING stanju")
    void approveOrder_invalidState() throws Exception {
        when(orderService.approveOrder(5L))
                .thenThrow(new IllegalArgumentException("Order nije u PENDING statusu"));

        mockMvc.perform(patch("/orders/5/approve"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Order nije u PENDING statusu"));
    }

    // ---------- PATCH /orders/{id}/decline ----------

    @Test
    @DisplayName("PATCH /orders/5/decline - 200 OK")
    void declineOrder_success() throws Exception {
        OrderDto declined = sampleOrder(5L, "DECLINED");
        when(orderService.declineOrder(5L)).thenReturn(declined);

        mockMvc.perform(patch("/orders/5/decline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.status").value("DECLINED"));

        verify(orderService).declineOrder(5L);
    }

    @Test
    @DisplayName("PATCH /orders/999/decline - 404 kad order ne postoji")
    void declineOrder_notFound() throws Exception {
        when(orderService.declineOrder(999L))
                .thenThrow(new EntityNotFoundException("Order not found"));

        mockMvc.perform(patch("/orders/999/decline"))
                .andExpect(status().isNotFound());
    }
}
