package rs.raf.banka2_bek.option.service;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.actuary.model.ActuaryInfo;
import rs.raf.banka2_bek.actuary.model.ActuaryType;
import rs.raf.banka2_bek.actuary.repository.ActuaryInfoRepository;
import rs.raf.banka2_bek.company.model.Company;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;
import rs.raf.banka2_bek.option.dto.OptionChainDto;
import rs.raf.banka2_bek.option.dto.OptionDto;
import rs.raf.banka2_bek.option.model.Option;
import rs.raf.banka2_bek.option.model.OptionType;
import rs.raf.banka2_bek.option.repository.OptionRepository;
import rs.raf.banka2_bek.portfolio.model.Portfolio;
import rs.raf.banka2_bek.portfolio.repository.PortfolioRepository;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.model.ListingType;
import rs.raf.banka2_bek.stock.repository.ListingRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Extended tests for OptionService covering:
 * - getOptionsForStock (grouping, sorting, listing not found)
 * - getOptionById
 * - exerciseOption CALL with portfolio and account operations
 * - exerciseOption PUT with share removal
 * - PUT in-the-money validation
 * - Admin user can exercise options
 * - Insufficient bank funds for CALL
 * - Insufficient shares for PUT
 * - Employee not found
 */
@ExtendWith(MockitoExtension.class)
class OptionServiceExtendedTest {

    @Mock private OptionRepository optionRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private ActuaryInfoRepository actuaryInfoRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private PortfolioRepository portfolioRepository;

    @InjectMocks
    private OptionService optionService;

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private Listing buildStockListing(Long id, BigDecimal price) {
        Listing l = new Listing();
        l.setId(id);
        l.setTicker("AAPL");
        l.setName("Apple Inc.");
        l.setListingType(ListingType.STOCK);
        l.setPrice(price);
        return l;
    }

    private Option buildOption(Long id, OptionType type, BigDecimal stockPrice,
                                BigDecimal strike, LocalDate settlement, int openInterest) {
        Listing listing = buildStockListing(55L, stockPrice);
        Option o = new Option();
        o.setId(id);
        o.setTicker("AAPL260402" + (type == OptionType.CALL ? "C" : "P") + "00185000");
        o.setStockListing(listing);
        o.setOptionType(type);
        o.setStrikePrice(strike);
        o.setSettlementDate(settlement);
        o.setOpenInterest(openInterest);
        o.setContractSize(100);
        o.setPrice(new BigDecimal("5.00"));
        o.setAsk(new BigDecimal("5.50"));
        o.setBid(new BigDecimal("4.50"));
        o.setImpliedVolatility(0.25);
        o.setVolume(1000);
        return o;
    }

    private Employee buildEmployee(Long id, String email, boolean active, Set<String> permissions) {
        return Employee.builder()
                .id(id)
                .firstName("Test")
                .lastName("Employee")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .gender("M")
                .email(email)
                .phone("0600000000")
                .address("Test adresa")
                .username(email)
                .password("password")
                .saltPassword("salt")
                .position("Agent")
                .department("Trading")
                .active(active)
                .permissions(permissions)
                .build();
    }

    private void mockAuthorizedActuary(String email, Long empId) {
        Employee emp = buildEmployee(empId, email, true, Set.of("AGENT"));
        ActuaryInfo info = new ActuaryInfo();
        info.setId(100L);
        info.setEmployee(emp);
        info.setActuaryType(ActuaryType.AGENT);
        info.setNeedApproval(false);

        when(employeeRepository.findByEmail(email)).thenReturn(Optional.of(emp));
        when(actuaryInfoRepository.findByEmployeeId(empId)).thenReturn(Optional.of(info));
    }

    private Account buildBankAccount(BigDecimal balance) {
        Currency usd = new Currency();
        usd.setId(1L);
        usd.setCode("USD");

        Company bank = new Company();
        bank.setId(3L);

        Account acc = new Account();
        acc.setId(99L);
        acc.setCompany(bank);
        acc.setCurrency(usd);
        acc.setBalance(balance);
        acc.setAvailableBalance(balance);
        return acc;
    }

    // ─── getOptionsForStock ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("getOptionsForStock")
    class GetOptionsForStock {

        @Test
        @DisplayName("throws EntityNotFoundException when listing not found")
        void listingNotFound() {
            when(listingRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> optionService.getOptionsForStock(999L))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("returns empty list when no options exist for stock")
        void noOptions() {
            Listing listing = buildStockListing(1L, new BigDecimal("150.00"));
            when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));
            when(optionRepository.findByStockListingId(1L)).thenReturn(Collections.emptyList());

            List<OptionChainDto> result = optionService.getOptionsForStock(1L);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("groups options by settlement date and separates calls and puts")
        void groupsByDateAndType() {
            Listing listing = buildStockListing(1L, new BigDecimal("150.00"));
            LocalDate date1 = LocalDate.of(2026, 4, 10);
            LocalDate date2 = LocalDate.of(2026, 5, 10);

            Option call1 = buildOption(1L, OptionType.CALL, new BigDecimal("150"), new BigDecimal("155"), date1, 10);
            call1.setStockListing(listing);
            Option put1 = buildOption(2L, OptionType.PUT, new BigDecimal("150"), new BigDecimal("145"), date1, 5);
            put1.setStockListing(listing);
            Option call2 = buildOption(3L, OptionType.CALL, new BigDecimal("150"), new BigDecimal("160"), date2, 8);
            call2.setStockListing(listing);

            when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));
            when(optionRepository.findByStockListingId(1L)).thenReturn(List.of(call1, put1, call2));

            List<OptionChainDto> result = optionService.getOptionsForStock(1L);

            assertThat(result).hasSize(2);
            // Sorted by date
            assertThat(result.get(0).getSettlementDate()).isEqualTo(date1);
            assertThat(result.get(1).getSettlementDate()).isEqualTo(date2);
            // First chain has 1 call, 1 put
            assertThat(result.get(0).getCalls()).hasSize(1);
            assertThat(result.get(0).getPuts()).hasSize(1);
            // Second chain has 1 call, 0 puts
            assertThat(result.get(1).getCalls()).hasSize(1);
            assertThat(result.get(1).getPuts()).isEmpty();
        }

        @Test
        @DisplayName("calls are sorted by strike price ascending")
        void callsSortedByStrike() {
            Listing listing = buildStockListing(1L, new BigDecimal("150.00"));
            LocalDate date = LocalDate.of(2026, 4, 10);

            Option call1 = buildOption(1L, OptionType.CALL, new BigDecimal("150"), new BigDecimal("170"), date, 10);
            call1.setStockListing(listing);
            Option call2 = buildOption(2L, OptionType.CALL, new BigDecimal("150"), new BigDecimal("155"), date, 10);
            call2.setStockListing(listing);
            Option call3 = buildOption(3L, OptionType.CALL, new BigDecimal("150"), new BigDecimal("160"), date, 10);
            call3.setStockListing(listing);

            when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));
            when(optionRepository.findByStockListingId(1L)).thenReturn(List.of(call1, call2, call3));

            List<OptionChainDto> result = optionService.getOptionsForStock(1L);

            List<OptionDto> calls = result.get(0).getCalls();
            assertThat(calls).hasSize(3);
            assertThat(calls.get(0).getStrikePrice()).isEqualByComparingTo(new BigDecimal("155"));
            assertThat(calls.get(1).getStrikePrice()).isEqualByComparingTo(new BigDecimal("160"));
            assertThat(calls.get(2).getStrikePrice()).isEqualByComparingTo(new BigDecimal("170"));
        }
    }

    // ─── getOptionById ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getOptionById")
    class GetOptionById {

        @Test
        @DisplayName("returns OptionDto for existing option")
        void existingOption() {
            Option option = buildOption(1L, OptionType.CALL, new BigDecimal("150"), new BigDecimal("145"), LocalDate.now().plusDays(5), 10);
            when(optionRepository.findById(1L)).thenReturn(Optional.of(option));

            OptionDto dto = optionService.getOptionById(1L);

            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getOptionType()).isEqualTo("CALL");
            assertThat(dto.isInTheMoney()).isTrue(); // 150 > 145 for CALL
        }

        @Test
        @DisplayName("throws EntityNotFoundException for missing option")
        void missingOption() {
            when(optionRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> optionService.getOptionById(99L))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    // ─── exerciseOption - CALL ──────────────────────────────────────────────────

    @Nested
    @DisplayName("exerciseOption - CALL")
    class ExerciseCall {

        @Test
        @DisplayName("CALL exercise deducts from bank, adds to portfolio, decrements openInterest")
        void callExerciseSuccess() {
            mockAuthorizedActuary("agent@test.com", 12L);
            Account bank = buildBankAccount(new BigDecimal("1000000.00"));

            Option option = buildOption(1L, OptionType.CALL, new BigDecimal("210.00"),
                    new BigDecimal("180.00"), LocalDate.now().plusDays(5), 4);

            when(optionRepository.findById(1L)).thenReturn(Optional.of(option));
            when(accountRepository.findAll()).thenReturn(List.of(bank));
            when(portfolioRepository.findByUserId(12L)).thenReturn(Collections.emptyList());

            optionService.exerciseOption(1L, "agent@test.com");

            // Bank debited by strike * contractSize = 180 * 100 = 18000
            assertThat(bank.getBalance()).isEqualByComparingTo(new BigDecimal("982000.00"));
            // Open interest decremented
            assertThat(option.getOpenInterest()).isEqualTo(3);
            verify(optionRepository).save(option);
            verify(portfolioRepository).save(any(Portfolio.class));
        }

        @Test
        @DisplayName("CALL exercise fails when bank has insufficient funds")
        void callExerciseInsufficientFunds() {
            mockAuthorizedActuary("agent@test.com", 12L);
            Account bank = buildBankAccount(new BigDecimal("100.00")); // Not enough

            Option option = buildOption(1L, OptionType.CALL, new BigDecimal("210.00"),
                    new BigDecimal("180.00"), LocalDate.now().plusDays(5), 4);

            when(optionRepository.findById(1L)).thenReturn(Optional.of(option));
            when(accountRepository.findAll()).thenReturn(List.of(bank));

            assertThatThrownBy(() -> optionService.exerciseOption(1L, "agent@test.com"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Nedovoljno sredstava");
        }

        @Test
        @DisplayName("CALL exercise updates existing portfolio with weighted average")
        void callExerciseUpdatesExistingPortfolio() {
            mockAuthorizedActuary("agent@test.com", 12L);
            Account bank = buildBankAccount(new BigDecimal("1000000.00"));

            Option option = buildOption(1L, OptionType.CALL, new BigDecimal("210.00"),
                    new BigDecimal("180.00"), LocalDate.now().plusDays(5), 4);

            Portfolio existingPortfolio = new Portfolio();
            existingPortfolio.setUserId(12L);
            existingPortfolio.setListingId(55L);
            existingPortfolio.setQuantity(50);
            existingPortfolio.setAverageBuyPrice(new BigDecimal("200.00"));

            when(optionRepository.findById(1L)).thenReturn(Optional.of(option));
            when(accountRepository.findAll()).thenReturn(List.of(bank));
            when(portfolioRepository.findByUserId(12L)).thenReturn(List.of(existingPortfolio));

            optionService.exerciseOption(1L, "agent@test.com");

            // quantity should be 50 + 100 = 150
            assertThat(existingPortfolio.getQuantity()).isEqualTo(150);
            verify(portfolioRepository).save(existingPortfolio);
        }
    }

    // ─── exerciseOption - PUT ───────────────────────────────────────────────────

    @Nested
    @DisplayName("exerciseOption - PUT")
    class ExercisePut {

        @Test
        @DisplayName("PUT exercise removes shares and credits bank")
        void putExerciseSuccess() {
            mockAuthorizedActuary("agent@test.com", 12L);
            Account bank = buildBankAccount(new BigDecimal("100000.00"));

            Option option = buildOption(1L, OptionType.PUT, new BigDecimal("150.00"),
                    new BigDecimal("180.00"), LocalDate.now().plusDays(5), 3);

            Portfolio portfolio = new Portfolio();
            portfolio.setUserId(12L);
            portfolio.setListingId(55L);
            portfolio.setQuantity(200);
            portfolio.setAverageBuyPrice(new BigDecimal("170.00"));

            when(optionRepository.findById(1L)).thenReturn(Optional.of(option));
            when(portfolioRepository.findByUserId(12L)).thenReturn(List.of(portfolio));
            when(accountRepository.findAll()).thenReturn(List.of(bank));

            optionService.exerciseOption(1L, "agent@test.com");

            // Portfolio reduced by contractSize (100)
            assertThat(portfolio.getQuantity()).isEqualTo(100);
            // Bank credited by strike * contractSize = 180 * 100 = 18000
            assertThat(bank.getBalance()).isEqualByComparingTo(new BigDecimal("118000.00"));
            assertThat(option.getOpenInterest()).isEqualTo(2);
        }

        @Test
        @DisplayName("PUT exercise deletes portfolio when shares reach zero")
        void putExerciseDeletesPortfolio() {
            mockAuthorizedActuary("agent@test.com", 12L);
            Account bank = buildBankAccount(new BigDecimal("100000.00"));

            Option option = buildOption(1L, OptionType.PUT, new BigDecimal("150.00"),
                    new BigDecimal("180.00"), LocalDate.now().plusDays(5), 3);

            Portfolio portfolio = new Portfolio();
            portfolio.setUserId(12L);
            portfolio.setListingId(55L);
            portfolio.setQuantity(100); // Exactly contractSize
            portfolio.setAverageBuyPrice(new BigDecimal("170.00"));

            when(optionRepository.findById(1L)).thenReturn(Optional.of(option));
            when(portfolioRepository.findByUserId(12L)).thenReturn(List.of(portfolio));
            when(accountRepository.findAll()).thenReturn(List.of(bank));

            optionService.exerciseOption(1L, "agent@test.com");

            verify(portfolioRepository).delete(portfolio);
        }

        @Test
        @DisplayName("PUT exercise fails when user has no shares")
        void putExerciseNoShares() {
            mockAuthorizedActuary("agent@test.com", 12L);

            Option option = buildOption(1L, OptionType.PUT, new BigDecimal("150.00"),
                    new BigDecimal("180.00"), LocalDate.now().plusDays(5), 3);

            Account bank = buildBankAccount(new BigDecimal("100000.00"));

            when(optionRepository.findById(1L)).thenReturn(Optional.of(option));
            when(accountRepository.findAll()).thenReturn(List.of(bank));
            when(portfolioRepository.findByUserId(12L)).thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> optionService.exerciseOption(1L, "agent@test.com"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("nema dovoljno akcija");
        }

        @Test
        @DisplayName("PUT exercise fails when user has insufficient shares")
        void putExerciseInsufficientShares() {
            mockAuthorizedActuary("agent@test.com", 12L);

            Option option = buildOption(1L, OptionType.PUT, new BigDecimal("150.00"),
                    new BigDecimal("180.00"), LocalDate.now().plusDays(5), 3);

            Account bank = buildBankAccount(new BigDecimal("100000.00"));

            Portfolio portfolio = new Portfolio();
            portfolio.setUserId(12L);
            portfolio.setListingId(55L);
            portfolio.setQuantity(50); // Less than contractSize (100)
            portfolio.setAverageBuyPrice(new BigDecimal("170.00"));

            when(optionRepository.findById(1L)).thenReturn(Optional.of(option));
            when(accountRepository.findAll()).thenReturn(List.of(bank));
            when(portfolioRepository.findByUserId(12L)).thenReturn(List.of(portfolio));

            assertThatThrownBy(() -> optionService.exerciseOption(1L, "agent@test.com"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Nedovoljno akcija");
        }

        @Test
        @DisplayName("PUT option not in-the-money throws exception")
        void putNotInTheMoney() {
            mockAuthorizedActuary("agent@test.com", 12L);

            // stock=200, strike=180 -> for PUT, need stock < strike
            Option option = buildOption(1L, OptionType.PUT, new BigDecimal("200.00"),
                    new BigDecimal("180.00"), LocalDate.now().plusDays(5), 3);

            when(optionRepository.findById(1L)).thenReturn(Optional.of(option));

            assertThatThrownBy(() -> optionService.exerciseOption(1L, "agent@test.com"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("in-the-money");
        }
    }

    // ─── Authorization ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Authorization checks")
    class Authorization {

        @Test
        @DisplayName("ADMIN employee can exercise options")
        void adminCanExercise() {
            Employee admin = buildEmployee(15L, "admin@test.com", true, Set.of("ADMIN"));
            Account bank = buildBankAccount(new BigDecimal("1000000.00"));

            Option option = buildOption(1L, OptionType.CALL, new BigDecimal("210.00"),
                    new BigDecimal("180.00"), LocalDate.now().plusDays(5), 4);

            when(employeeRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));
            // No ActuaryInfo needed for ADMIN
            when(optionRepository.findById(1L)).thenReturn(Optional.of(option));
            when(accountRepository.findAll()).thenReturn(List.of(bank));
            when(portfolioRepository.findByUserId(15L)).thenReturn(Collections.emptyList());

            optionService.exerciseOption(1L, "admin@test.com");

            verify(optionRepository).save(option);
        }

        @Test
        @DisplayName("employee not found throws AccessDeniedException")
        void employeeNotFound() {
            when(employeeRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> optionService.exerciseOption(1L, "unknown@test.com"))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }
}
