package rs.raf.banka2_bek.order.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import rs.raf.banka2_bek.order.dto.CreateOrderDto;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OrderValidationService")
class OrderValidationServiceTest {

    private final OrderValidationService service = new OrderValidationService();

    private CreateOrderDto validMarketBuyDto() {
        CreateOrderDto dto = new CreateOrderDto();
        dto.setListingId(1L);
        dto.setOrderType("MARKET");
        dto.setDirection("BUY");
        dto.setQuantity(10);
        dto.setContractSize(1);
        dto.setAccountId(100L);
        return dto;
    }

    @Nested
    @DisplayName("orderType i direction")
    class OrderTypeAndDirection {

        @Test
        @DisplayName("nevalidan orderType baca grešku")
        void invalidOrderType() {
            CreateOrderDto dto = validMarketBuyDto();
            dto.setOrderType("INVALID");

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.validate(dto));
            assertEquals("Invalid order type or direction", ex.getMessage());
        }

        @Test
        @DisplayName("null orderType baca grešku")
        void nullOrderType() {
            CreateOrderDto dto = validMarketBuyDto();
            dto.setOrderType(null);

            assertThrows(IllegalArgumentException.class, () -> service.validate(dto));
        }

        @Test
        @DisplayName("nevalidan direction baca grešku")
        void invalidDirection() {
            CreateOrderDto dto = validMarketBuyDto();
            dto.setDirection("INVALID");

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.validate(dto));
            assertEquals("Invalid order type or direction", ex.getMessage());
        }

        @Test
        @DisplayName("null direction baca grešku")
        void nullDirection() {
            CreateOrderDto dto = validMarketBuyDto();
            dto.setDirection(null);

            assertThrows(IllegalArgumentException.class, () -> service.validate(dto));
        }
    }

    @Nested
    @DisplayName("quantity i contractSize")
    class QuantityAndContractSize {

        @Test
        @DisplayName("quantity = 0 baca grešku")
        void zeroQuantity() {
            CreateOrderDto dto = validMarketBuyDto();
            dto.setQuantity(0);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.validate(dto));
            assertEquals("Quantity and contractSize must be > 0", ex.getMessage());
        }

        @Test
        @DisplayName("quantity negativan baca grešku")
        void negativeQuantity() {
            CreateOrderDto dto = validMarketBuyDto();
            dto.setQuantity(-5);

            assertThrows(IllegalArgumentException.class, () -> service.validate(dto));
        }

        @Test
        @DisplayName("contractSize = 0 baca grešku")
        void zeroContractSize() {
            CreateOrderDto dto = validMarketBuyDto();
            dto.setContractSize(0);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.validate(dto));
            assertEquals("Quantity and contractSize must be > 0", ex.getMessage());
        }

        @Test
        @DisplayName("null quantity baca grešku")
        void nullQuantity() {
            CreateOrderDto dto = validMarketBuyDto();
            dto.setQuantity(null);

            assertThrows(IllegalArgumentException.class, () -> service.validate(dto));
        }

        @Test
        @DisplayName("null contractSize baca grešku")
        void nullContractSize() {
            CreateOrderDto dto = validMarketBuyDto();
            dto.setContractSize(null);

            assertThrows(IllegalArgumentException.class, () -> service.validate(dto));
        }
    }

    @Nested
    @DisplayName("LIMIT i STOP_LIMIT — limitValue")
    class LimitValue {

        @Test
        @DisplayName("LIMIT bez limitValue baca grešku")
        void limitOrderWithoutLimitValue() {
            CreateOrderDto dto = validMarketBuyDto();
            dto.setOrderType("LIMIT");
            dto.setLimitValue(null);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.validate(dto));
            assertEquals("Limit value is required for LIMIT and STOP_LIMIT orders", ex.getMessage());
        }

        @Test
        @DisplayName("LIMIT sa limitValue = 0 baca grešku")
        void limitOrderWithZeroLimitValue() {
            CreateOrderDto dto = validMarketBuyDto();
            dto.setOrderType("LIMIT");
            dto.setLimitValue(BigDecimal.ZERO);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.validate(dto));
            assertEquals("Limit value is required for LIMIT and STOP_LIMIT orders", ex.getMessage());
        }

        @Test
        @DisplayName("STOP_LIMIT bez limitValue baca grešku")
        void stopLimitOrderWithoutLimitValue() {
            CreateOrderDto dto = validMarketBuyDto();
            dto.setOrderType("STOP_LIMIT");
            dto.setLimitValue(null);
            dto.setStopValue(new BigDecimal("150"));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.validate(dto));
            assertEquals("Limit value is required for LIMIT and STOP_LIMIT orders", ex.getMessage());
        }

        @Test
        @DisplayName("MARKET ignoriše limitValue — prolazi bez nje")
        void marketOrderIgnoresLimitValue() {
            CreateOrderDto dto = validMarketBuyDto();
            dto.setLimitValue(null);

            assertDoesNotThrow(() -> service.validate(dto));
        }
    }

    @Nested
    @DisplayName("STOP i STOP_LIMIT — stopValue")
    class StopValue {

        @Test
        @DisplayName("STOP bez stopValue baca grešku")
        void stopOrderWithoutStopValue() {
            CreateOrderDto dto = validMarketBuyDto();
            dto.setOrderType("STOP");
            dto.setStopValue(null);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.validate(dto));
            assertEquals("Stop value is required for STOP and STOP_LIMIT orders", ex.getMessage());
        }

        @Test
        @DisplayName("STOP sa stopValue = 0 baca grešku")
        void stopOrderWithZeroStopValue() {
            CreateOrderDto dto = validMarketBuyDto();
            dto.setOrderType("STOP");
            dto.setStopValue(BigDecimal.ZERO);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.validate(dto));
            assertEquals("Stop value is required for STOP and STOP_LIMIT orders", ex.getMessage());
        }

        @Test
        @DisplayName("STOP_LIMIT bez stopValue baca grešku")
        void stopLimitOrderWithoutStopValue() {
            CreateOrderDto dto = validMarketBuyDto();
            dto.setOrderType("STOP_LIMIT");
            dto.setLimitValue(new BigDecimal("100"));
            dto.setStopValue(null);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.validate(dto));
            assertEquals("Stop value is required for STOP and STOP_LIMIT orders", ex.getMessage());
        }
    }

    @Nested
    @DisplayName("accountId")
    class AccountId {

        @Test
        @DisplayName("null accountId baca grešku")
        void nullAccountId() {
            CreateOrderDto dto = validMarketBuyDto();
            dto.setAccountId(null);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.validate(dto));
            assertEquals("Account ID is required", ex.getMessage());
        }
    }

    @Nested
    @DisplayName("validni slučajevi")
    class ValidCases {

        @Test
        @DisplayName("MARKET BUY — prolazi validaciju")
        void validMarketBuy() {
            assertDoesNotThrow(() -> service.validate(validMarketBuyDto()));
        }

        @Test
        @DisplayName("LIMIT BUY sa limitValue — prolazi validaciju")
        void validLimitBuy() {
            CreateOrderDto dto = validMarketBuyDto();
            dto.setOrderType("LIMIT");
            dto.setLimitValue(new BigDecimal("150.00"));

            assertDoesNotThrow(() -> service.validate(dto));
        }

        @Test
        @DisplayName("STOP SELL sa stopValue — prolazi validaciju")
        void validStopSell() {
            CreateOrderDto dto = validMarketBuyDto();
            dto.setOrderType("STOP");
            dto.setDirection("SELL");
            dto.setStopValue(new BigDecimal("140.00"));

            assertDoesNotThrow(() -> service.validate(dto));
        }

        @Test
        @DisplayName("STOP_LIMIT sa oba vrednosti — prolazi validaciju")
        void validStopLimit() {
            CreateOrderDto dto = validMarketBuyDto();
            dto.setOrderType("STOP_LIMIT");
            dto.setLimitValue(new BigDecimal("150.00"));
            dto.setStopValue(new BigDecimal("145.00"));

            assertDoesNotThrow(() -> service.validate(dto));
        }
    }
}
