package rs.raf.banka2_bek.tax.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountStatus;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.auth.model.User;
import rs.raf.banka2_bek.auth.repository.UserRepository;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;
import rs.raf.banka2_bek.order.model.Order;
import rs.raf.banka2_bek.order.model.OrderDirection;
import rs.raf.banka2_bek.order.model.OrderStatus;
import rs.raf.banka2_bek.order.repository.OrderRepository;
import rs.raf.banka2_bek.order.service.CurrencyConversionService;
import rs.raf.banka2_bek.otc.repository.OtcContractRepository;
import rs.raf.banka2_bek.stock.model.Listing;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Additional coverage for TaxService targeting uncovered branches:
 * - collectTaxFromUser (CLIENT debit path, null stateAccount, no RSD account, insufficient funds)
 * - resolveOrderCurrency fallback (null listing, blank currency, exception)
 * - convertToRsd (null amount, zero amount, conversion exception fallback)
 * - resolveUserName fallback ("Zaposleni #", "Klijent #")
 * - previouslyPaid null branch on update
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TaxServiceCoverage")
class TaxServiceCoverageTest {

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

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(taxService, "bankRegistrationNumber", "BANK-REG");
        ReflectionTestUtils.setField(taxService, "stateRegistrationNumber", "STATE-REG");
        when(taxRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private Listing listing(Long id, String quote) {
        Listing l = new Listing();
        l.setId(id);
        l.setQuoteCurrency(quote);
        // Porez se obracunava samo na STOCK (Celina 3 spec)
        l.setListingType(rs.raf.banka2_bek.stock.model.ListingType.STOCK);
        return l;
    }

    private Order order(Long userId, String role, Listing listing, OrderDirection dir,
                        String price, int qty) {
        Order o = new Order();
        o.setId((long) (Math.random() * 100000));
        o.setUserId(userId);
        o.setUserRole(role);
        o.setDirection(dir);
        o.setPricePerUnit(new BigDecimal(price));
        o.setQuantity(qty);
        o.setContractSize(1);
        o.setDone(true);
        o.setStatus(OrderStatus.DONE);
        o.setListing(listing);
        return o;
    }

    private Account rsdAccount(String balance) {
        Currency rsd = Currency.builder().code("RSD").build();
        return Account.builder()
                .id((long) (Math.random() * 1000000))
                .currency(rsd)
                .balance(new BigDecimal(balance))
                .availableBalance(new BigDecimal(balance))
                .status(AccountStatus.ACTIVE)
                .build();
    }

    private Account foreignAccount(String code, String balance) {
        Currency c = Currency.builder().code(code).build();
        return Account.builder()
                .id((long) (Math.random() * 1000000))
                .currency(c)
                .balance(new BigDecimal(balance))
                .availableBalance(new BigDecimal(balance))
                .status(AccountStatus.ACTIVE)
                .build();
    }

    // ─── collectTaxFromUser paths ───────────────────────────────────────────────

    @Test
    @DisplayName("CLIENT tax is debited from RSD account and credited to state account")
    void debitClientRsdAccount() {
        Listing l = listing(1L, "RSD");
        Order buy = order(1L, "CLIENT", l, OrderDirection.BUY, "100", 1);
        Order sell = order(1L, "CLIENT", l, OrderDirection.SELL, "1100", 1);

        when(orderRepository.findByIsDoneTrue()).thenReturn(List.of(buy, sell));
        when(userRepository.findById(1L)).thenReturn(Optional.of(
                new User("Marko", "P", "m@t.com", "p", true, "CLIENT")));
        when(taxRecordRepository.findByUserIdAndUserType(1L, "CLIENT")).thenReturn(Optional.empty());

        Account state = rsdAccount("0");
        when(accountRepository.findBankAccountByCurrency("STATE-REG", "RSD"))
                .thenReturn(Optional.of(state));

        Account clientRsd = rsdAccount("10000");
        when(accountRepository.findByClientIdAndStatusOrderByAvailableBalanceDesc(1L, AccountStatus.ACTIVE))
                .thenReturn(List.of(clientRsd));

        taxService.calculateTaxForAllUsers();

        // profit = 1000 RSD, tax = 150
        ArgumentCaptor<TaxRecord> recCap = ArgumentCaptor.forClass(TaxRecord.class);
        verify(taxRecordRepository).save(recCap.capture());
        assertThat(recCap.getValue().getTaxOwed()).isEqualByComparingTo("150.0000");
        assertThat(recCap.getValue().getTaxPaid()).isEqualByComparingTo("150.0000");
        assertThat(clientRsd.getBalance()).isEqualByComparingTo("9850.0000");
        assertThat(state.getBalance()).isEqualByComparingTo("150.0000");
        verify(accountRepository, times(2)).save(any(Account.class));
    }

    @Test
    @DisplayName("CLIENT with no RSD account: tax record saved but taxPaid stays 0")
    void clientWithoutRsdAccount() {
        Listing l = listing(1L, "RSD");
        Order sell = order(1L, "CLIENT", l, OrderDirection.SELL, "1000", 1);

        when(orderRepository.findByIsDoneTrue()).thenReturn(List.of(sell));
        when(userRepository.findById(1L)).thenReturn(Optional.of(
                new User("M", "P", "m@t.com", "p", true, "CLIENT")));
        when(taxRecordRepository.findByUserIdAndUserType(1L, "CLIENT")).thenReturn(Optional.empty());

        Account state = rsdAccount("0");
        when(accountRepository.findBankAccountByCurrency("STATE-REG", "RSD"))
                .thenReturn(Optional.of(state));
        // samo EUR racun
        when(accountRepository.findByClientIdAndStatusOrderByAvailableBalanceDesc(1L, AccountStatus.ACTIVE))
                .thenReturn(List.of(foreignAccount("EUR", "5000")));

        taxService.calculateTaxForAllUsers();

        ArgumentCaptor<TaxRecord> rc = ArgumentCaptor.forClass(TaxRecord.class);
        verify(taxRecordRepository).save(rc.capture());
        assertThat(rc.getValue().getTaxOwed()).isEqualByComparingTo("150.0000");
        assertThat(rc.getValue().getTaxPaid()).isEqualByComparingTo(BigDecimal.ZERO);
        // state account nije azuriran (ne uplacen porez)
        assertThat(state.getBalance()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("CLIENT with RSD account but insufficient funds: no debit")
    void clientInsufficientFunds() {
        Listing l = listing(1L, "RSD");
        Order sell = order(1L, "CLIENT", l, OrderDirection.SELL, "10000", 1);

        when(orderRepository.findByIsDoneTrue()).thenReturn(List.of(sell));
        when(userRepository.findById(1L)).thenReturn(Optional.of(
                new User("M", "P", "m@t.com", "p", true, "CLIENT")));
        when(taxRecordRepository.findByUserIdAndUserType(1L, "CLIENT")).thenReturn(Optional.empty());

        Account state = rsdAccount("0");
        when(accountRepository.findBankAccountByCurrency("STATE-REG", "RSD"))
                .thenReturn(Optional.of(state));
        // RSD racun ali balance premalen (tax=1500, balance=100)
        when(accountRepository.findByClientIdAndStatusOrderByAvailableBalanceDesc(1L, AccountStatus.ACTIVE))
                .thenReturn(List.of(rsdAccount("100")));

        taxService.calculateTaxForAllUsers();

        ArgumentCaptor<TaxRecord> rc = ArgumentCaptor.forClass(TaxRecord.class);
        verify(taxRecordRepository).save(rc.capture());
        assertThat(rc.getValue().getTaxPaid()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("null state account: tax record saved, collection skipped with warning")
    void nullStateAccount() {
        Listing l = listing(1L, "RSD");
        Order sell = order(1L, "CLIENT", l, OrderDirection.SELL, "1000", 1);

        when(orderRepository.findByIsDoneTrue()).thenReturn(List.of(sell));
        when(userRepository.findById(1L)).thenReturn(Optional.of(
                new User("M", "P", "m@t.com", "p", true, "CLIENT")));
        when(taxRecordRepository.findByUserIdAndUserType(1L, "CLIENT")).thenReturn(Optional.empty());

        when(accountRepository.findBankAccountByCurrency("STATE-REG", "RSD"))
                .thenReturn(Optional.empty());
        when(accountRepository.findByClientIdAndStatusOrderByAvailableBalanceDesc(1L, AccountStatus.ACTIVE))
                .thenReturn(List.of(rsdAccount("100000")));

        taxService.calculateTaxForAllUsers();

        ArgumentCaptor<TaxRecord> rc = ArgumentCaptor.forClass(TaxRecord.class);
        verify(taxRecordRepository).save(rc.capture());
        assertThat(rc.getValue().getTaxPaid()).isEqualByComparingTo(BigDecimal.ZERO);
        // accountRepository.save za racune NE sme biti pozvan
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    @DisplayName("EMPLOYEE tax short-circuits to collected=true (internal bookkeeping)")
    void employeeTaxCollectedInternally() {
        Listing l = listing(1L, "RSD");
        Order buy = order(5L, "EMPLOYEE", l, OrderDirection.BUY, "100", 1);
        Order sell = order(5L, "EMPLOYEE", l, OrderDirection.SELL, "500", 1);

        when(orderRepository.findByIsDoneTrue()).thenReturn(List.of(buy, sell));
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(
                Employee.builder().id(5L).firstName("Ana").lastName("Anic").build()));
        when(taxRecordRepository.findByUserIdAndUserType(5L, "EMPLOYEE")).thenReturn(Optional.empty());
        when(accountRepository.findBankAccountByCurrency("STATE-REG", "RSD"))
                .thenReturn(Optional.of(rsdAccount("0")));

        taxService.calculateTaxForAllUsers();

        ArgumentCaptor<TaxRecord> rc = ArgumentCaptor.forClass(TaxRecord.class);
        verify(taxRecordRepository).save(rc.capture());
        // profit=400, tax=60, taxPaid=60 jer zaposleni automatski "collected"
        assertThat(rc.getValue().getTaxOwed()).isEqualByComparingTo("60.0000");
        assertThat(rc.getValue().getTaxPaid()).isEqualByComparingTo("60.0000");
        verify(accountRepository, never()).findByClientIdAndStatusOrderByAvailableBalanceDesc(any(), any());
    }

    @Test
    @DisplayName("updates existing record with previously paid tax - only incremental charge")
    void incrementalTaxOnExistingRecord() {
        Listing l = listing(1L, "RSD");
        Order buy = order(1L, "CLIENT", l, OrderDirection.BUY, "100", 1);
        Order sell = order(1L, "CLIENT", l, OrderDirection.SELL, "1100", 1);

        TaxRecord existing = TaxRecord.builder()
                .id(7L).userId(1L).userType("CLIENT")
                .totalProfit(new BigDecimal("500"))
                .taxOwed(new BigDecimal("75"))
                .taxPaid(new BigDecimal("75")) // already paid
                .currency("RSD")
                .build();

        when(orderRepository.findByIsDoneTrue()).thenReturn(List.of(buy, sell));
        when(userRepository.findById(1L)).thenReturn(Optional.of(
                new User("M", "P", "m@t.com", "p", true, "CLIENT")));
        when(taxRecordRepository.findByUserIdAndUserType(1L, "CLIENT"))
                .thenReturn(Optional.of(existing));

        Account state = rsdAccount("0");
        when(accountRepository.findBankAccountByCurrency("STATE-REG", "RSD"))
                .thenReturn(Optional.of(state));
        Account clientRsd = rsdAccount("10000");
        when(accountRepository.findByClientIdAndStatusOrderByAvailableBalanceDesc(1L, AccountStatus.ACTIVE))
                .thenReturn(List.of(clientRsd));

        taxService.calculateTaxForAllUsers();

        // novi tax = 150, placeno 75 => unpaid = 75
        assertThat(clientRsd.getBalance()).isEqualByComparingTo("9925.0000");
        assertThat(state.getBalance()).isEqualByComparingTo("75.0000");
        assertThat(existing.getTaxPaid()).isEqualByComparingTo("150.0000");
    }

    @Test
    @DisplayName("existing record with null taxPaid treated as zero")
    void existingRecordNullTaxPaid() {
        Listing l = listing(1L, "RSD");
        Order sell = order(1L, "CLIENT", l, OrderDirection.SELL, "1000", 1);

        TaxRecord existing = TaxRecord.builder()
                .id(8L).userId(1L).userType("CLIENT")
                .taxPaid(null)
                .currency("RSD")
                .build();

        when(orderRepository.findByIsDoneTrue()).thenReturn(List.of(sell));
        when(userRepository.findById(1L)).thenReturn(Optional.of(
                new User("M", "P", "m@t.com", "p", true, "CLIENT")));
        when(taxRecordRepository.findByUserIdAndUserType(1L, "CLIENT"))
                .thenReturn(Optional.of(existing));
        when(accountRepository.findBankAccountByCurrency("STATE-REG", "RSD"))
                .thenReturn(Optional.of(rsdAccount("0")));
        when(accountRepository.findByClientIdAndStatusOrderByAvailableBalanceDesc(1L, AccountStatus.ACTIVE))
                .thenReturn(List.of(rsdAccount("100000")));

        taxService.calculateTaxForAllUsers();

        assertThat(existing.getTaxPaid()).isEqualByComparingTo("150.0000");
    }

    // ─── resolveOrderCurrency branches ──────────────────────────────────────────

    @Test
    @DisplayName("order with null listing quote currency falls back to RSD")
    void nullQuoteCurrencyFallback() {
        Listing l = new Listing();
        l.setId(1L);
        l.setQuoteCurrency(null); // explicit null
        l.setListingType(rs.raf.banka2_bek.stock.model.ListingType.STOCK);
        l.setExchangeAcronym("BELEX"); // BELEX → RSD, izbegava USD konverziju
        Order sell = order(1L, "CLIENT", l, OrderDirection.SELL, "500", 1);

        when(orderRepository.findByIsDoneTrue()).thenReturn(List.of(sell));
        when(userRepository.findById(1L)).thenReturn(Optional.of(
                new User("M", "P", "m@t.com", "p", true, "CLIENT")));
        when(taxRecordRepository.findByUserIdAndUserType(1L, "CLIENT")).thenReturn(Optional.empty());

        taxService.calculateTaxForAllUsers();

        // treba da koristi RSD (bez konverzije)
        verify(currencyConversionService, never()).convert(any(), any(), any());
    }

    @Test
    @DisplayName("order with blank listing quote currency falls back to RSD")
    void blankQuoteCurrencyFallback() {
        Listing l = new Listing();
        l.setId(1L);
        l.setQuoteCurrency("   ");
        l.setListingType(rs.raf.banka2_bek.stock.model.ListingType.STOCK);
        l.setExchangeAcronym("BELEX");
        Order sell = order(1L, "CLIENT", l, OrderDirection.SELL, "500", 1);

        when(orderRepository.findByIsDoneTrue()).thenReturn(List.of(sell));
        when(userRepository.findById(1L)).thenReturn(Optional.of(
                new User("M", "P", "m@t.com", "p", true, "CLIENT")));
        when(taxRecordRepository.findByUserIdAndUserType(1L, "CLIENT")).thenReturn(Optional.empty());

        taxService.calculateTaxForAllUsers();

        verify(currencyConversionService, never()).convert(any(), any(), any());
    }

    // ─── convertToRsd branches ──────────────────────────────────────────────────

    @Test
    @DisplayName("conversion exception falls back to raw amount")
    void conversionExceptionFallback() {
        Listing l = listing(1L, "USD");
        Order buy = order(1L, "CLIENT", l, OrderDirection.BUY, "100", 1);
        Order sell = order(1L, "CLIENT", l, OrderDirection.SELL, "300", 1);

        when(orderRepository.findByIsDoneTrue()).thenReturn(List.of(buy, sell));
        when(userRepository.findById(1L)).thenReturn(Optional.of(
                new User("M", "P", "m@t.com", "p", true, "CLIENT")));
        when(taxRecordRepository.findByUserIdAndUserType(1L, "CLIENT")).thenReturn(Optional.empty());
        when(currencyConversionService.convert(any(), eq("USD"), eq("RSD")))
                .thenThrow(new RuntimeException("rate unavailable"));

        taxService.calculateTaxForAllUsers();

        ArgumentCaptor<TaxRecord> rc = ArgumentCaptor.forClass(TaxRecord.class);
        verify(taxRecordRepository).save(rc.capture());
        // fallback na raw iznos: profit = 200 (ne konvertovano)
        assertThat(rc.getValue().getTotalProfit()).isEqualByComparingTo("200");
    }

    @Test
    @DisplayName("zero profit in foreign currency short-circuits conversion")
    void zeroProfitSkipsConversion() {
        Listing l = listing(1L, "USD");
        Order buy = order(1L, "CLIENT", l, OrderDirection.BUY, "100", 1);
        Order sell = order(1L, "CLIENT", l, OrderDirection.SELL, "100", 1);

        when(orderRepository.findByIsDoneTrue()).thenReturn(List.of(buy, sell));
        when(userRepository.findById(1L)).thenReturn(Optional.of(
                new User("M", "P", "m@t.com", "p", true, "CLIENT")));
        when(taxRecordRepository.findByUserIdAndUserType(1L, "CLIENT")).thenReturn(Optional.empty());

        taxService.calculateTaxForAllUsers();

        // convertToRsd sa amount=0 treba da vrati ZERO bez poziva servisa
        verify(currencyConversionService, never()).convert(any(), any(), any());
        ArgumentCaptor<TaxRecord> rc = ArgumentCaptor.forClass(TaxRecord.class);
        verify(taxRecordRepository).save(rc.capture());
        assertThat(rc.getValue().getTotalProfit()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(rc.getValue().getTaxOwed()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ─── resolveUserName fallbacks ──────────────────────────────────────────────

    @Test
    @DisplayName("unknown CLIENT user id falls back to 'Klijent #id' label")
    void unknownClientUserNameFallback() {
        Listing l = listing(1L, "RSD");
        Order sell = order(99L, "CLIENT", l, OrderDirection.SELL, "100", 1);

        when(orderRepository.findByIsDoneTrue()).thenReturn(List.of(sell));
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        when(taxRecordRepository.findByUserIdAndUserType(99L, "CLIENT")).thenReturn(Optional.empty());

        taxService.calculateTaxForAllUsers();

        ArgumentCaptor<TaxRecord> rc = ArgumentCaptor.forClass(TaxRecord.class);
        verify(taxRecordRepository).save(rc.capture());
        assertThat(rc.getValue().getUserName()).isEqualTo("Klijent #99");
    }

    @Test
    @DisplayName("unknown EMPLOYEE user id falls back to 'Zaposleni #id' label")
    void unknownEmployeeUserNameFallback() {
        Listing l = listing(1L, "RSD");
        Order sell = order(42L, "EMPLOYEE", l, OrderDirection.SELL, "100", 1);

        when(orderRepository.findByIsDoneTrue()).thenReturn(List.of(sell));
        when(employeeRepository.findById(42L)).thenReturn(Optional.empty());
        when(taxRecordRepository.findByUserIdAndUserType(42L, "EMPLOYEE")).thenReturn(Optional.empty());

        taxService.calculateTaxForAllUsers();

        ArgumentCaptor<TaxRecord> rc = ArgumentCaptor.forClass(TaxRecord.class);
        verify(taxRecordRepository).save(rc.capture());
        assertThat(rc.getValue().getUserName()).isEqualTo("Zaposleni #42");
    }

    // ─── getTaxRecords explicit filter path ─────────────────────────────────────

    @Test
    @DisplayName("explicit non-blank filters pass through to repository")
    void explicitFiltersPassThrough() {
        when(taxRecordRepository.findByFilters("Marko", "CLIENT"))
                .thenReturn(Collections.emptyList());

        List<TaxRecordDto> out = taxService.getTaxRecords("Marko", "CLIENT");

        assertThat(out).isEmpty();
        verify(taxRecordRepository).findByFilters("Marko", "CLIENT");
    }

    @Test
    @DisplayName("null filters normalized to null")
    void nullFiltersNormalized() {
        when(taxRecordRepository.findByFilters(null, null))
                .thenReturn(Collections.emptyList());

        taxService.getTaxRecords(null, null);

        verify(taxRecordRepository).findByFilters(null, null);
    }

    // ─── getMyTaxRecord client without record (covers orElseGet branch L83-84) ──

    @Test
    @DisplayName("getMyTaxRecord: CLIENT without record returns empty DTO with full name")
    void getMyTaxRecord_clientWithoutRecord_returnsEmptyDto() {
        User user = new User("Marko", "Petrovic", "marko@x.com", "p", true, "CLIENT");
        user.setId(77L);
        when(employeeRepository.findByEmail("marko@x.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("marko@x.com")).thenReturn(Optional.of(user));
        when(taxRecordRepository.findByUserIdAndUserType(77L, "CLIENT")).thenReturn(Optional.empty());

        TaxRecordDto dto = taxService.getMyTaxRecord("marko@x.com");

        assertThat(dto.getUserId()).isEqualTo(77L);
        assertThat(dto.getUserName()).isEqualTo("Marko Petrovic");
        assertThat(dto.getUserType()).isEqualTo("CLIENT");
        assertThat(dto.getTaxOwed()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ─── convertToRsd: amount == null short-circuit (L254) ─────────────────────

    @Test
    @DisplayName("convertToRsd reflectively: null amount returns ZERO")
    void convertToRsd_nullAmount_returnsZero() throws Exception {
        java.lang.reflect.Method m = TaxService.class.getDeclaredMethod(
                "convertToRsd", BigDecimal.class, String.class);
        m.setAccessible(true);
        Object out = m.invoke(taxService, (BigDecimal) null, "USD");
        assertThat((BigDecimal) out).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("convertToRsd reflectively: null fromCurrency returns amount as-is")
    void convertToRsd_nullFromCurrency_returnsAmount() throws Exception {
        java.lang.reflect.Method m = TaxService.class.getDeclaredMethod(
                "convertToRsd", BigDecimal.class, String.class);
        m.setAccessible(true);
        Object out = m.invoke(taxService, new BigDecimal("123.45"), (String) null);
        assertThat((BigDecimal) out).isEqualByComparingTo("123.45");
        verify(currencyConversionService, never()).convert(any(), any(), any());
    }

    // ─── resolveOrderCurrency: listing.getQuoteCurrency() throws (L243) ─────────

    @Test
    @DisplayName("resolveOrderCurrency reflectively: getQuoteCurrency throws → fallback RSD")
    void resolveOrderCurrency_listingThrows_fallsBackToRsd() throws Exception {
        Listing throwingListing = mock(Listing.class);
        when(throwingListing.getQuoteCurrency()).thenThrow(new RuntimeException("lazy init boom"));

        Order o = new Order();
        o.setListing(throwingListing);

        java.lang.reflect.Method m = TaxService.class.getDeclaredMethod(
                "resolveOrderCurrency", Order.class);
        m.setAccessible(true);
        Object out = m.invoke(taxService, o);

        assertThat((String) out).isEqualTo("RSD");
    }

    // ─── resolveOrderCurrency: order.getListing() == null branch ────────────────

    @Test
    @DisplayName("resolveOrderCurrency reflectively: null listing → fallback RSD")
    void resolveOrderCurrency_nullListing_fallsBackToRsd() throws Exception {
        Order o = new Order();
        o.setListing(null);

        java.lang.reflect.Method m = TaxService.class.getDeclaredMethod(
                "resolveOrderCurrency", Order.class);
        m.setAccessible(true);
        Object out = m.invoke(taxService, o);

        assertThat((String) out).isEqualTo("RSD");
    }
}
