package rs.raf.banka2_bek.tax.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.auth.model.User;
import rs.raf.banka2_bek.auth.repository.UserRepository;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;
import rs.raf.banka2_bek.order.model.Order;
import rs.raf.banka2_bek.order.model.OrderDirection;
import rs.raf.banka2_bek.order.model.OrderStatus;
import rs.raf.banka2_bek.order.repository.OrderRepository;
import rs.raf.banka2_bek.order.service.CurrencyConversionService;
import rs.raf.banka2_bek.otc.repository.OtcContractRepository;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.model.ListingType;
import rs.raf.banka2_bek.tax.dto.TaxRecordDto;
import rs.raf.banka2_bek.tax.model.TaxRecord;
import rs.raf.banka2_bek.tax.repository.TaxRecordBreakdownRepository;
import rs.raf.banka2_bek.tax.repository.TaxRecordRepository;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for TaxService covering:
 * - calculateTaxForAllUsers (15% on profits, no tax on loss)
 * - getTaxRecords with filters
 * - getMyTaxRecord for employee and client
 * - Mixed buy/sell orders
 * - Update existing tax record
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TaxService")
class TaxServiceTest {

    @Mock private TaxRecordRepository taxRecordRepository;
    @Mock private TaxRecordBreakdownRepository taxRecordBreakdownRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private UserRepository userRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private CurrencyConversionService currencyConversionService;
    @Mock private OtcContractRepository otcContractRepository;

    @InjectMocks
    private TaxService taxService;

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private Order buildOrder(Long userId, String role, OrderDirection dir,
                              BigDecimal pricePerUnit, int qty, int contractSize) {
        Order o = new Order();
        o.setId((long) (Math.random() * 10000));
        o.setUserId(userId);
        o.setUserRole(role);
        o.setDirection(dir);
        o.setPricePerUnit(pricePerUnit);
        o.setQuantity(qty);
        o.setContractSize(contractSize);
        o.setDone(true);
        o.setStatus(OrderStatus.DONE);
        Listing listing = new Listing();
        // Spec (Celina 3 - Porez): porez se racuna samo na prodaju AKCIJA
        listing.setListingType(ListingType.STOCK);
        // Osnovni test-orderi se vode u RSD da bi izbegli konverziju —
        // testovi koji se bave FX-om eksplicitno setuju drugaciju valutu.
        listing.setQuoteCurrency("RSD");
        o.setListing(listing);
        return o;
    }

    // ─── calculateTaxForAllUsers ────────────────────────────────────────────────

    @Nested
    @DisplayName("calculateTaxForAllUsers")
    class CalculateTaxForAll {

        @Test
        @DisplayName("15% tax on net profit when sell > buy")
        void taxOnPositiveProfit() {
            Order buy = buildOrder(1L, "CLIENT", OrderDirection.BUY, new BigDecimal("100"), 10, 1);
            Order sell = buildOrder(1L, "CLIENT", OrderDirection.SELL, new BigDecimal("150"), 10, 1);

            when(orderRepository.findByIsDoneTrue()).thenReturn(List.of(buy, sell));
            when(userRepository.findById(1L)).thenReturn(Optional.of(
                    new User("Marko", "Petrovic", "marko@test.com", "pass", true, "CLIENT")));
            when(taxRecordRepository.findByUserIdAndUserType(1L, "CLIENT")).thenReturn(Optional.empty());
            when(taxRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            taxService.calculateTaxForAllUsers();

            ArgumentCaptor<TaxRecord> captor = ArgumentCaptor.forClass(TaxRecord.class);
            verify(taxRecordRepository).save(captor.capture());
            TaxRecord record = captor.getValue();

            // profit = sell(1500) - buy(1000) = 500
            assertThat(record.getTotalProfit()).isEqualByComparingTo(new BigDecimal("500"));
            // tax = 500 * 0.15 = 75
            assertThat(record.getTaxOwed()).isEqualByComparingTo(new BigDecimal("75.0000"));
            assertThat(record.getUserType()).isEqualTo("CLIENT");
        }

        @Test
        @DisplayName("no tax when loss (sell < buy)")
        void noTaxOnLoss() {
            Order buy = buildOrder(1L, "CLIENT", OrderDirection.BUY, new BigDecimal("200"), 10, 1);
            Order sell = buildOrder(1L, "CLIENT", OrderDirection.SELL, new BigDecimal("100"), 10, 1);

            when(orderRepository.findByIsDoneTrue()).thenReturn(List.of(buy, sell));
            when(userRepository.findById(1L)).thenReturn(Optional.of(
                    new User("Marko", "Petrovic", "marko@test.com", "pass", true, "CLIENT")));
            when(taxRecordRepository.findByUserIdAndUserType(1L, "CLIENT")).thenReturn(Optional.empty());
            when(taxRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            taxService.calculateTaxForAllUsers();

            ArgumentCaptor<TaxRecord> captor = ArgumentCaptor.forClass(TaxRecord.class);
            verify(taxRecordRepository).save(captor.capture());
            TaxRecord record = captor.getValue();

            // profit = sell(1000) - buy(2000) = -1000
            assertThat(record.getTotalProfit()).isNegative();
            assertThat(record.getTaxOwed()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("no tax when break even")
        void noTaxOnBreakEven() {
            Order buy = buildOrder(1L, "CLIENT", OrderDirection.BUY, new BigDecimal("100"), 10, 1);
            Order sell = buildOrder(1L, "CLIENT", OrderDirection.SELL, new BigDecimal("100"), 10, 1);

            when(orderRepository.findByIsDoneTrue()).thenReturn(List.of(buy, sell));
            when(userRepository.findById(1L)).thenReturn(Optional.of(
                    new User("Marko", "Petrovic", "marko@test.com", "pass", true, "CLIENT")));
            when(taxRecordRepository.findByUserIdAndUserType(1L, "CLIENT")).thenReturn(Optional.empty());
            when(taxRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            taxService.calculateTaxForAllUsers();

            ArgumentCaptor<TaxRecord> captor = ArgumentCaptor.forClass(TaxRecord.class);
            verify(taxRecordRepository).save(captor.capture());

            assertThat(captor.getValue().getTaxOwed()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("EMPLOYEE orders create EMPLOYEE type tax record")
        void employeeOrders() {
            Order sell = buildOrder(5L, "EMPLOYEE", OrderDirection.SELL, new BigDecimal("200"), 10, 1);

            when(orderRepository.findByIsDoneTrue()).thenReturn(List.of(sell));
            when(employeeRepository.findById(5L)).thenReturn(Optional.of(
                    Employee.builder().id(5L).firstName("Ana").lastName("Anic").build()));
            when(taxRecordRepository.findByUserIdAndUserType(5L, "EMPLOYEE")).thenReturn(Optional.empty());
            when(taxRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            taxService.calculateTaxForAllUsers();

            ArgumentCaptor<TaxRecord> captor = ArgumentCaptor.forClass(TaxRecord.class);
            verify(taxRecordRepository).save(captor.capture());

            assertThat(captor.getValue().getUserType()).isEqualTo("EMPLOYEE");
            assertThat(captor.getValue().getUserName()).isEqualTo("Ana Anic");
        }

        @Test
        @DisplayName("updates existing tax record instead of creating new one")
        void updatesExistingRecord() {
            Order sell = buildOrder(1L, "CLIENT", OrderDirection.SELL, new BigDecimal("300"), 5, 1);

            TaxRecord existingRecord = TaxRecord.builder()
                    .id(10L)
                    .userId(1L)
                    .userType("CLIENT")
                    .totalProfit(new BigDecimal("100"))
                    .taxOwed(new BigDecimal("15"))
                    .taxPaid(BigDecimal.ZERO)
                    .currency("RSD")
                    .build();

            when(orderRepository.findByIsDoneTrue()).thenReturn(List.of(sell));
            when(userRepository.findById(1L)).thenReturn(Optional.of(
                    new User("Marko", "P", "m@t.com", "p", true, "CLIENT")));
            when(taxRecordRepository.findByUserIdAndUserType(1L, "CLIENT")).thenReturn(Optional.of(existingRecord));
            when(taxRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            taxService.calculateTaxForAllUsers();

            verify(taxRecordRepository).save(existingRecord);
            // profit = 1500 (only sells, no buys)
            assertThat(existingRecord.getTotalProfit()).isEqualByComparingTo(new BigDecimal("1500"));
        }

        @Test
        @DisplayName("only done orders are processed")
        void onlyDoneOrders() {
            Order doneOrder = buildOrder(1L, "CLIENT", OrderDirection.SELL, new BigDecimal("100"), 10, 1);
            doneOrder.setDone(true);

            // findByIsDoneTrue returns only done orders — pending orders are filtered by the repository
            when(orderRepository.findByIsDoneTrue()).thenReturn(List.of(doneOrder));
            when(userRepository.findById(1L)).thenReturn(Optional.of(
                    new User("M", "P", "m@t.com", "p", true, "CLIENT")));
            when(taxRecordRepository.findByUserIdAndUserType(1L, "CLIENT")).thenReturn(Optional.empty());
            when(taxRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            taxService.calculateTaxForAllUsers();

            ArgumentCaptor<TaxRecord> captor = ArgumentCaptor.forClass(TaxRecord.class);
            verify(taxRecordRepository).save(captor.capture());

            // Only the done sell order counted, no buy -> profit = 1000
            assertThat(captor.getValue().getTotalProfit()).isEqualByComparingTo(new BigDecimal("1000"));
        }

        @Test
        @DisplayName("no orders means no tax records saved")
        void noOrders() {
            when(orderRepository.findByIsDoneTrue()).thenReturn(Collections.emptyList());

            taxService.calculateTaxForAllUsers();

            verify(taxRecordRepository, never()).save(any());
        }

        @Test
        @DisplayName("multiple users are processed independently")
        void multipleUsers() {
            Order user1Sell = buildOrder(1L, "CLIENT", OrderDirection.SELL, new BigDecimal("100"), 10, 1);
            Order user2Buy = buildOrder(2L, "EMPLOYEE", OrderDirection.BUY, new BigDecimal("50"), 20, 1);

            when(orderRepository.findByIsDoneTrue()).thenReturn(List.of(user1Sell, user2Buy));
            when(userRepository.findById(1L)).thenReturn(Optional.of(
                    new User("A", "B", "a@b.com", "p", true, "CLIENT")));
            when(employeeRepository.findById(2L)).thenReturn(Optional.of(
                    Employee.builder().id(2L).firstName("C").lastName("D").build()));
            when(taxRecordRepository.findByUserIdAndUserType(any(), any())).thenReturn(Optional.empty());
            when(taxRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            taxService.calculateTaxForAllUsers();

            verify(taxRecordRepository, times(2)).save(any(TaxRecord.class));
        }

        @Test
        @DisplayName("contractSize multiplies into total value")
        void contractSizeMultiplied() {
            // price=10, qty=5, contractSize=100 -> orderValue = 10*5*100 = 5000
            Order sell = buildOrder(1L, "CLIENT", OrderDirection.SELL, new BigDecimal("10"), 5, 100);

            when(orderRepository.findByIsDoneTrue()).thenReturn(List.of(sell));
            when(userRepository.findById(1L)).thenReturn(Optional.of(
                    new User("A", "B", "a@b.com", "p", true, "CLIENT")));
            when(taxRecordRepository.findByUserIdAndUserType(1L, "CLIENT")).thenReturn(Optional.empty());
            when(taxRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            taxService.calculateTaxForAllUsers();

            ArgumentCaptor<TaxRecord> captor = ArgumentCaptor.forClass(TaxRecord.class);
            verify(taxRecordRepository).save(captor.capture());

            assertThat(captor.getValue().getTotalProfit()).isEqualByComparingTo(new BigDecimal("5000"));
        }
    }

    // ─── Currency conversion (S80) ──────────────────────────────────────────────

    @Nested
    @DisplayName("CurrencyConversion (S80)")
    class CurrencyConversion {

        private Listing listingWithCurrency(Long id, String quoteCurrency) {
            Listing l = new Listing();
            l.setId(id);
            l.setQuoteCurrency(quoteCurrency);
            // Porez se racuna samo za STOCK (Celina 3 spec)
            l.setListingType(ListingType.STOCK);
            return l;
        }

        private Order buildOrderWithListing(Long userId, Listing listing, OrderDirection dir,
                                            BigDecimal pricePerUnit, int qty) {
            Order o = new Order();
            o.setId((long) (Math.random() * 10000));
            o.setUserId(userId);
            o.setUserRole("CLIENT");
            o.setDirection(dir);
            o.setPricePerUnit(pricePerUnit);
            o.setQuantity(qty);
            o.setContractSize(1);
            o.setDone(true);
            o.setStatus(OrderStatus.DONE);
            o.setListing(listing);
            return o;
        }

        @Test
        @DisplayName("Porez se agregira u RSD iz mix valuta (S80)")
        void calculateTax_convertsMixedCurrencyProfitToRsd() {
            // user 1: 100 USD profit iz AAPL (BUY@100 x1, SELL@200 x1 => 100 USD)
            Listing aapl = listingWithCurrency(1L, "USD");
            Order aaplBuy = buildOrderWithListing(1L, aapl, OrderDirection.BUY, new BigDecimal("100"), 1);
            Order aaplSell = buildOrderWithListing(1L, aapl, OrderDirection.SELL, new BigDecimal("200"), 1);

            // user 1: 5000 RSD profit iz XYZ (BUY@1000 x1, SELL@6000 x1 => 5000 RSD)
            Listing xyz = listingWithCurrency(2L, "RSD");
            Order xyzBuy = buildOrderWithListing(1L, xyz, OrderDirection.BUY, new BigDecimal("1000"), 1);
            Order xyzSell = buildOrderWithListing(1L, xyz, OrderDirection.SELL, new BigDecimal("6000"), 1);

            when(orderRepository.findByIsDoneTrue())
                    .thenReturn(List.of(aaplBuy, aaplSell, xyzBuy, xyzSell));
            when(userRepository.findById(1L)).thenReturn(Optional.of(
                    new User("Marko", "Petrovic", "marko@test.com", "pass", true, "CLIENT")));
            when(taxRecordRepository.findByUserIdAndUserType(1L, "CLIENT")).thenReturn(Optional.empty());
            when(taxRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Mock: 100 USD -> 10920 RSD (srednji kurs ~109.20)
            when(currencyConversionService.convert(new BigDecimal("100"), "USD", "RSD"))
                    .thenReturn(new BigDecimal("10920"));

            taxService.calculateTaxForAllUsers();

            ArgumentCaptor<TaxRecord> captor = ArgumentCaptor.forClass(TaxRecord.class);
            verify(taxRecordRepository).save(captor.capture());
            TaxRecord record = captor.getValue();

            // totalProfit (u RSD) = 10920 (iz USD) + 5000 (RSD) = 15920
            assertThat(record.getTotalProfit()).isEqualByComparingTo(new BigDecimal("15920"));
            // taxOwed = 0.15 * 15920 = 2388
            assertThat(record.getTaxOwed()).isEqualByComparingTo(new BigDecimal("2388"));
            assertThat(record.getCurrency()).isEqualTo("RSD");

            // Verifikuj da je CurrencyConversionService pozvan za USD (ne i za RSD)
            verify(currencyConversionService).convert(new BigDecimal("100"), "USD", "RSD");
            verify(currencyConversionService, never()).convert(any(), eq("RSD"), eq("RSD"));
        }
    }

    // ─── getTaxRecords ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getTaxRecords")
    class GetTaxRecords {

        @Test
        @DisplayName("returns filtered records")
        void filteredRecords() {
            TaxRecord record = TaxRecord.builder()
                    .id(1L).userId(1L).userName("Marko P").userType("CLIENT")
                    .totalProfit(new BigDecimal("500")).taxOwed(new BigDecimal("75"))
                    .taxPaid(BigDecimal.ZERO).currency("RSD").build();

            when(taxRecordRepository.findByFilters("Marko", "CLIENT")).thenReturn(List.of(record));

            List<TaxRecordDto> result = taxService.getTaxRecords("Marko", "CLIENT");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getUserName()).isEqualTo("Marko P");
        }

        @Test
        @DisplayName("returns empty list when no records match")
        void noRecords() {
            when(taxRecordRepository.findByFilters(any(), any())).thenReturn(Collections.emptyList());

            List<TaxRecordDto> result = taxService.getTaxRecords("NonExistent", null);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("null and blank filters are converted to null for query")
        void nullAndBlankFilters() {
            when(taxRecordRepository.findByFilters(null, null)).thenReturn(Collections.emptyList());

            taxService.getTaxRecords("", "  ");

            verify(taxRecordRepository).findByFilters(null, null);
        }
    }

    // ─── getMyTaxRecord ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getMyTaxRecord")
    class GetMyTaxRecord {

        @Test
        @DisplayName("returns existing employee tax record")
        void employeeWithRecord() {
            Employee emp = Employee.builder().id(5L).firstName("Ana").lastName("Anic").build();
            emp.setEmail("ana@banka.rs");
            TaxRecord record = TaxRecord.builder()
                    .id(1L).userId(5L).userName("Ana Anic").userType("EMPLOYEE")
                    .totalProfit(new BigDecimal("1000")).taxOwed(new BigDecimal("150"))
                    .taxPaid(BigDecimal.ZERO).currency("RSD").build();

            when(employeeRepository.findByEmail("ana@banka.rs")).thenReturn(Optional.of(emp));
            when(taxRecordRepository.findByUserIdAndUserType(5L, "EMPLOYEE")).thenReturn(Optional.of(record));

            TaxRecordDto dto = taxService.getMyTaxRecord("ana@banka.rs");

            assertThat(dto.getUserName()).isEqualTo("Ana Anic");
            assertThat(dto.getTaxOwed()).isEqualByComparingTo(new BigDecimal("150"));
        }

        @Test
        @DisplayName("returns empty DTO when employee has no record")
        void employeeWithoutRecord() {
            Employee emp = Employee.builder().id(5L).firstName("Ana").lastName("Anic").build();
            emp.setEmail("ana@banka.rs");

            when(employeeRepository.findByEmail("ana@banka.rs")).thenReturn(Optional.of(emp));
            when(taxRecordRepository.findByUserIdAndUserType(5L, "EMPLOYEE")).thenReturn(Optional.empty());

            TaxRecordDto dto = taxService.getMyTaxRecord("ana@banka.rs");

            assertThat(dto.getUserId()).isEqualTo(5L);
            assertThat(dto.getUserType()).isEqualTo("EMPLOYEE");
            assertThat(dto.getTotalProfit()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(dto.getTaxOwed()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("returns existing client tax record")
        void clientWithRecord() {
            User user = new User("Marko", "Petrovic", "marko@test.com", "pass", true, "CLIENT");
            user.setId(10L);
            TaxRecord record = TaxRecord.builder()
                    .id(2L).userId(10L).userName("Marko Petrovic").userType("CLIENT")
                    .totalProfit(new BigDecimal("2000")).taxOwed(new BigDecimal("300"))
                    .taxPaid(new BigDecimal("50")).currency("RSD").build();

            when(employeeRepository.findByEmail("marko@test.com")).thenReturn(Optional.empty());
            when(userRepository.findByEmail("marko@test.com")).thenReturn(Optional.of(user));
            when(taxRecordRepository.findByUserIdAndUserType(10L, "CLIENT")).thenReturn(Optional.of(record));

            TaxRecordDto dto = taxService.getMyTaxRecord("marko@test.com");

            assertThat(dto.getUserName()).isEqualTo("Marko Petrovic");
            assertThat(dto.getTaxPaid()).isEqualByComparingTo(new BigDecimal("50"));
        }

        @Test
        @DisplayName("returns empty DTO when email matches neither employee nor client")
        void unknownEmail() {
            when(employeeRepository.findByEmail("nobody@test.com")).thenReturn(Optional.empty());
            when(userRepository.findByEmail("nobody@test.com")).thenReturn(Optional.empty());

            TaxRecordDto dto = taxService.getMyTaxRecord("nobody@test.com");

            assertThat(dto.getUserId()).isEqualTo(0L);
            assertThat(dto.getUserName()).isEqualTo("Nepoznat");
        }
    }
}
