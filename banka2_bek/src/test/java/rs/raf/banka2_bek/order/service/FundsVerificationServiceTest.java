package rs.raf.banka2_bek.order.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.order.dto.CreateOrderDto;
import rs.raf.banka2_bek.order.model.OrderDirection;
import rs.raf.banka2_bek.order.model.OrderType;
import rs.raf.banka2_bek.order.repository.OrderRepository;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.model.ListingType;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FundsVerificationService")
class FundsVerificationServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private OrderRepository orderRepository;

    @InjectMocks
    private FundsVerificationService service;

    private Account accountWithBalance(BigDecimal balance, BigDecimal availableBalance) {
        Account acc = new Account();
        acc.setBalance(balance);
        acc.setAvailableBalance(availableBalance);
        return acc;
    }

    private Listing stockListing(BigDecimal price) {
        Listing l = new Listing();
        l.setId(1L);
        l.setListingType(ListingType.STOCK);
        l.setPrice(price);
        return l;
    }

    private Listing futuresListing(BigDecimal price, int contractSize) {
        Listing l = new Listing();
        l.setId(1L);
        l.setListingType(ListingType.FUTURES);
        l.setPrice(price);
        l.setContractSize(contractSize);
        return l;
    }

    private CreateOrderDto buyDto(Long accountId) {
        CreateOrderDto dto = new CreateOrderDto();
        dto.setAccountId(accountId);
        dto.setQuantity(10);
        dto.setContractSize(1);
        dto.setDirection("BUY");
        dto.setMargin(false);
        return dto;
    }

    private CreateOrderDto sellDto(Long accountId, int quantity) {
        CreateOrderDto dto = new CreateOrderDto();
        dto.setAccountId(accountId);
        dto.setQuantity(quantity);
        dto.setContractSize(1);
        dto.setDirection("SELL");
        dto.setMargin(false);
        return dto;
    }

    @Nested
    @DisplayName("BUY — provera sredstava")
    class BuyFundsCheck {

        @Test
        @DisplayName("MARKET BUY — dovoljno sredstava (sa provizijom 14%) → prolazi")
        void marketBuyWithSufficientFunds() {
            // approximatePrice=100, commission=min(14,7)=7, required=107
            BigDecimal approxPrice = new BigDecimal("100");
            Account account = accountWithBalance(new BigDecimal("200"), new BigDecimal("200"));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

            assertDoesNotThrow(() ->
                    service.verify(buyDto(1L), 1L, approxPrice, stockListing(new BigDecimal("10")), OrderType.MARKET, OrderDirection.BUY));
        }

        @Test
        @DisplayName("MARKET BUY — nedovoljno sredstava → Insufficient funds")
        void marketBuyWithInsufficientFunds() {
            // approximatePrice=100, commission=7, required=107, balance=100
            BigDecimal approxPrice = new BigDecimal("100");
            Account account = accountWithBalance(new BigDecimal("100"), new BigDecimal("100"));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    service.verify(buyDto(1L), 1L, approxPrice, stockListing(new BigDecimal("10")), OrderType.MARKET, OrderDirection.BUY));
            assertEquals("Insufficient funds", ex.getMessage());
        }

        @Test
        @DisplayName("MARKET BUY — provizija je min(14%,7) — veća cena, limit 7")
        void marketBuyCommissionCappedAt7() {
            // approxPrice=1000, 14%=140, capped=7, required=1007
            BigDecimal approxPrice = new BigDecimal("1000");
            Account account = accountWithBalance(new BigDecimal("1010"), new BigDecimal("1010"));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

            assertDoesNotThrow(() ->
                    service.verify(buyDto(1L), 1L, approxPrice, stockListing(new BigDecimal("100")), OrderType.MARKET, OrderDirection.BUY));
        }

        @Test
        @DisplayName("LIMIT BUY — provizija je min(24%,12)")
        void limitBuyCommission() {
            // approxPrice=100, 24%=24, capped=12, required=112
            BigDecimal approxPrice = new BigDecimal("100");
            Account account = accountWithBalance(new BigDecimal("115"), new BigDecimal("115"));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

            assertDoesNotThrow(() ->
                    service.verify(buyDto(1L), 1L, approxPrice, stockListing(new BigDecimal("10")), OrderType.LIMIT, OrderDirection.BUY));
        }

        @Test
        @DisplayName("LIMIT BUY — nedovoljno sa provizijom → Insufficient funds")
        void limitBuyInsufficientWithCommission() {
            // approxPrice=100, commission=12, required=112, balance=110
            BigDecimal approxPrice = new BigDecimal("100");
            Account account = accountWithBalance(new BigDecimal("110"), new BigDecimal("110"));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    service.verify(buyDto(1L), 1L, approxPrice, stockListing(new BigDecimal("10")), OrderType.LIMIT, OrderDirection.BUY));
            assertEquals("Insufficient funds", ex.getMessage());
        }

        @Test
        @DisplayName("STOP BUY — nema unapred provizije, samo approximatePrice")
        void stopBuyNoCommission() {
            // approxPrice=100, commission=0, required=100, balance=100
            BigDecimal approxPrice = new BigDecimal("100");
            Account account = accountWithBalance(new BigDecimal("100"), new BigDecimal("100"));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

            assertDoesNotThrow(() ->
                    service.verify(buyDto(1L), 1L, approxPrice, stockListing(new BigDecimal("10")), OrderType.STOP, OrderDirection.BUY));
        }

        @Test
        @DisplayName("STOP_LIMIT BUY — nema unapred provizije")
        void stopLimitBuyNoCommission() {
            BigDecimal approxPrice = new BigDecimal("100");
            Account account = accountWithBalance(new BigDecimal("100"), new BigDecimal("100"));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

            assertDoesNotThrow(() ->
                    service.verify(buyDto(1L), 1L, approxPrice, stockListing(new BigDecimal("10")), OrderType.STOP_LIMIT, OrderDirection.BUY));
        }
    }

    @Nested
    @DisplayName("SELL — provera portfolija")
    class SellPortfolioCheck {

        @Test
        @DisplayName("SELL sa dovoljno hartija u portfoliju → prolazi")
        void sellWithSufficientPortfolio() {
            CreateOrderDto dto = sellDto(1L, 5);
            when(accountRepository.findById(1L)).thenReturn(Optional.of(accountWithBalance(BigDecimal.ZERO, BigDecimal.ZERO)));
            when(orderRepository.getNetPortfolioQuantity(1L, 1L)).thenReturn(10);

            assertDoesNotThrow(() ->
                    service.verify(dto, 1L, new BigDecimal("500"), stockListing(new BigDecimal("100")), OrderType.MARKET, OrderDirection.SELL));
        }

        @Test
        @DisplayName("SELL sa tačno toliko hartija → prolazi")
        void sellWithExactPortfolio() {
            CreateOrderDto dto = sellDto(1L, 10);
            when(accountRepository.findById(1L)).thenReturn(Optional.of(accountWithBalance(BigDecimal.ZERO, BigDecimal.ZERO)));
            when(orderRepository.getNetPortfolioQuantity(1L, 1L)).thenReturn(10);

            assertDoesNotThrow(() ->
                    service.verify(dto, 1L, new BigDecimal("1000"), stockListing(new BigDecimal("100")), OrderType.MARKET, OrderDirection.SELL));
        }

        @Test
        @DisplayName("SELL bez dovoljno hartija → Insufficient securities")
        void sellWithInsufficientPortfolio() {
            CreateOrderDto dto = sellDto(1L, 15);
            when(accountRepository.findById(1L)).thenReturn(Optional.of(accountWithBalance(BigDecimal.ZERO, BigDecimal.ZERO)));
            when(orderRepository.getNetPortfolioQuantity(1L, 1L)).thenReturn(10);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    service.verify(dto, 1L, new BigDecimal("1500"), stockListing(new BigDecimal("100")), OrderType.MARKET, OrderDirection.SELL));
            assertEquals("Insufficient securities", ex.getMessage());
        }

        @Test
        @DisplayName("SELL sa praznim portfolijem → Insufficient securities")
        void sellWithEmptyPortfolio() {
            CreateOrderDto dto = sellDto(1L, 1);
            when(accountRepository.findById(1L)).thenReturn(Optional.of(accountWithBalance(BigDecimal.ZERO, BigDecimal.ZERO)));
            when(orderRepository.getNetPortfolioQuantity(1L, 1L)).thenReturn(0);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    service.verify(dto, 1L, new BigDecimal("100"), stockListing(new BigDecimal("100")), OrderType.MARKET, OrderDirection.SELL));
            assertEquals("Insufficient securities", ex.getMessage());
        }
    }

    @Nested
    @DisplayName("Margin order — precheck")
    class MarginCheck {

        @Test
        @DisplayName("STOCK margin — balance > initialMarginCost → prolazi")
        void stockMarginBalanceSufficient() {
            // price=100, maintenanceMargin=50% * 100=50, initialMarginCost=50*1.1=55
            CreateOrderDto dto = buyDto(1L);
            dto.setMargin(true);
            Account acc = accountWithBalance(new BigDecimal("100"), new BigDecimal("0"));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(acc));

            assertDoesNotThrow(() ->
                    service.verify(dto, 1L, new BigDecimal("0"), stockListing(new BigDecimal("100")), OrderType.MARKET, OrderDirection.BUY));
        }

        @Test
        @DisplayName("STOCK margin — availableBalance (kredit) > initialMarginCost → prolazi")
        void stockMarginCreditSufficient() {
            // price=100, initialMarginCost=55, balance=0, credit=100
            CreateOrderDto dto = buyDto(1L);
            dto.setMargin(true);
            Account acc = accountWithBalance(new BigDecimal("0"), new BigDecimal("100"));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(acc));

            assertDoesNotThrow(() ->
                    service.verify(dto, 1L, new BigDecimal("0"), stockListing(new BigDecimal("100")), OrderType.MARKET, OrderDirection.BUY));
        }

        @Test
        @DisplayName("STOCK margin — ni balance ni kredit nisu dovoljni → Insufficient funds for margin order")
        void stockMarginBothInsufficient() {
            // price=100, initialMarginCost=55, balance=30, credit=30
            CreateOrderDto dto = buyDto(1L);
            dto.setMargin(true);
            Account acc = accountWithBalance(new BigDecimal("30"), new BigDecimal("30"));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(acc));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    service.verify(dto, 1L, new BigDecimal("0"), stockListing(new BigDecimal("100")), OrderType.MARKET, OrderDirection.BUY));
            assertEquals("Insufficient funds for margin order", ex.getMessage());
        }

        @Test
        @DisplayName("FUTURES margin — contractSize * price * 10% → ispravan izračun")
        void futuresMarginCalculation() {
            // contractSize=10, price=100, maintenanceMargin=100, initialMarginCost=110
            CreateOrderDto dto = buyDto(1L);
            dto.setMargin(true);
            Account acc = accountWithBalance(new BigDecimal("200"), new BigDecimal("0"));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(acc));

            assertDoesNotThrow(() ->
                    service.verify(dto, 1L, new BigDecimal("0"), futuresListing(new BigDecimal("100"), 10), OrderType.MARKET, OrderDirection.BUY));
        }

        @Test
        @DisplayName("FUTURES margin — nedovoljno → Insufficient funds for margin order")
        void futuresMarginInsufficient() {
            // contractSize=10, price=100, maintenanceMargin=100, initialMarginCost=110, balance=50
            CreateOrderDto dto = buyDto(1L);
            dto.setMargin(true);
            Account acc = accountWithBalance(new BigDecimal("50"), new BigDecimal("50"));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(acc));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    service.verify(dto, 1L, new BigDecimal("0"), futuresListing(new BigDecimal("100"), 10), OrderType.MARKET, OrderDirection.BUY));
            assertEquals("Insufficient funds for margin order", ex.getMessage());
        }
    }
}
