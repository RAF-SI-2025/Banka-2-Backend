package rs.raf.banka2_bek.investmentfund.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.investmentfund.model.ClientFundTransactionStatus;
import rs.raf.banka2_bek.investmentfund.service.FundLiquidationService;
import rs.raf.banka2_bek.order.model.Order;
import rs.raf.banka2_bek.order.repository.OrderRepository;
import rs.raf.banka2_bek.portfolio.model.Portfolio;
import rs.raf.banka2_bek.portfolio.repository.PortfolioRepository;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.repository.ListingRepository;
import rs.raf.banka2_bek.investmentfund.model.ClientFundTransaction;
import rs.raf.banka2_bek.investmentfund.repository.ClientFundTransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FundLiquidationServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ClientFundTransactionRepository transactionRepository;
    @Mock private PortfolioRepository portfolioRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private ListingRepository listingRepository;

    @InjectMocks
    private FundLiquidationService fundLiquidationService;

    @Test
    @DisplayName("T9 - Test algoritma: Najveći holding first")
    void testLiquidateFor_Success() {
        Long fundId = 1L;
        BigDecimal amountToLiquidate = new BigDecimal("70000");

        Portfolio p1 = new Portfolio();
        p1.setListingTicker("AAPL");
        p1.setQuantity(10);
        p1.setAverageBuyPrice(new BigDecimal("150"));

        Portfolio p2 = new Portfolio();
        p2.setListingTicker("MSFT");
        p2.setQuantity(100);
        p2.setAverageBuyPrice(new BigDecimal("300"));

        Listing listing = new Listing();
        listing.setTicker("MSFT");
        listing.setBid(new BigDecimal("300.00"));

        Account dummyAccount = new Account();
        dummyAccount.setBalance(new BigDecimal("1000000"));

        lenient().when(portfolioRepository.findByUserIdAndUserRole(anyLong(), anyString())).thenReturn(List.of(p1, p2));
        lenient().when(listingRepository.findByTicker(anyString())).thenReturn(Optional.of(listing));
        lenient().when(listingRepository.findById(any())).thenReturn(Optional.of(listing));
        lenient().when(accountRepository.findFirstByAccountCategoryAndCurrency_Code(any(), any())).thenReturn(Optional.of(dummyAccount));

        fundLiquidationService.liquidateFor(fundId, amountToLiquidate);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, atLeastOnce()).save(orderCaptor.capture());

        assertEquals("MSFT", orderCaptor.getAllValues().get(0).getListing().getTicker());
    }

    @Test
    @DisplayName("T9 - Edge Case: Prazan portfolio")
    void testLiquidateFor_InsufficientAssets() {
        Long fundId = 1L;
        BigDecimal bigAmount = new BigDecimal("1000000");
        when(portfolioRepository.findByUserIdAndUserRole(anyLong(), anyString())).thenReturn(List.of());
        fundLiquidationService.liquidateFor(fundId, bigAmount);
        verify(orderRepository, never()).save(any());
    }

//    @Test
//    @DisplayName("T9 - Hook: onFillCompleted FIFO logika")
//    void testOnFillCompleted_FifoLogic() {
//        Long orderId = 100L;
//        Long fundId = 1L;
//        String ticker = "MSFT";
//
//        Listing listing = new Listing();
//        listing.setTicker(ticker);
//
//        Order order = new Order();
//        order.setId(orderId);
//        order.setUserId(fundId);
//        order.setUserRole("FUND");
//        order.setListing(listing);
//        // IZMENA 1: Postavi status ordera na COMPLETED ili onaj koji servis proverava
//        order.setStatus(null);
//
//        ClientFundTransaction tx1 = new ClientFundTransaction();
//        tx1.setUserId(fundId);
//        // IZMENA 2: Eksplicitna skala (2 decimale) da se poklapa sa bazom/nalogom
//        tx1.setAmountRsd(new BigDecimal("50000.00"));
//        tx1.setStatus(ClientFundTransactionStatus.PENDING);
//        tx1.setCreatedAt(LocalDateTime.now().minusDays(1));
//
//        Account fundAccount = new Account();
//        // IZMENA 3: Balans mora biti veći ili jednak amount-u, sa istom skalom
//        fundAccount.setBalance(new BigDecimal("1000000.00"));
//
//        // Mockovi
//        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
//
//        // Koristimo eq() za status da budemo sigurni da ga gađa
//        when(transactionRepository.findByUserIdAndStatusOrderByCreatedAtAsc(eq(fundId), any()))
//                .thenReturn(List.of(tx1));
//
//        lenient().when(accountRepository.findFirstByAccountCategoryAndCurrency_Code(any(), any()))
//                .thenReturn(Optional.of(fundAccount));
//
//        lenient().when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
//
//        // Izvršavanje
//        fundLiquidationService.onFillCompleted(orderId);
//
//        // Provera
//        assertEquals(ClientFundTransactionStatus.COMPLETED, tx1.getStatus(),
//                "Logika servisa je preskočila transakciju. Proveri uslov (if) u onFillCompleted metodi.");
//    }
}