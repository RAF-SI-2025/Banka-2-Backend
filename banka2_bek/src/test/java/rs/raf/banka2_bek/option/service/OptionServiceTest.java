package rs.raf.banka2_bek.option.service;

import jakarta.persistence.EntityNotFoundException;
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
import rs.raf.banka2_bek.option.model.Option;
import rs.raf.banka2_bek.option.model.OptionType;
import rs.raf.banka2_bek.option.repository.OptionRepository;
import rs.raf.banka2_bek.portfolio.repository.PortfolioRepository;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.model.ListingType;
import rs.raf.banka2_bek.stock.repository.ListingRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OptionServiceTest {

    @Mock
    private OptionRepository optionRepository;

    @Mock
    private ListingRepository listingRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private ActuaryInfoRepository actuaryInfoRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PortfolioRepository portfolioRepository;

    @InjectMocks
    private OptionService optionService;

    @Test
    void exerciseOption_throwsWhenUserIsNotActuaryOrAdmin() {
        Employee employee = buildEmployee(10L, "employee@test.com", true, Set.of("VIEW_STOCKS"));

        when(employeeRepository.findByEmail("employee@test.com")).thenReturn(Optional.of(employee));
        when(actuaryInfoRepository.findByEmployeeId(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> optionService.exerciseOption(1L, "employee@test.com"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Samo aktuar");
    }

    @Test
    void exerciseOption_throwsWhenEmployeeIsInactive() {
        Employee employee = buildEmployee(11L, "inactive.agent@test.com", false, Set.of("AGENT"));

        when(employeeRepository.findByEmail("inactive.agent@test.com")).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> optionService.exerciseOption(1L, "inactive.agent@test.com"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("aktivan aktuar");
    }

    @Test
    void exerciseOption_throwsWhenOptionMissing() {
        mockAuthorizedActuary("agent@test.com", 12L);
        when(optionRepository.findById(55L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> optionService.exerciseOption(55L, "agent@test.com"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Option id: 55 not found.");
    }

    @Test
    void exerciseOption_throwsWhenOptionExpired() {
        mockAuthorizedActuary("agent@test.com", 12L);

        Option option = buildOption(
                1L,
                OptionType.CALL,
                new BigDecimal("210.00"),
                new BigDecimal("180.00"),
                LocalDate.now().minusDays(1),
                3
        );

        when(optionRepository.findById(1L)).thenReturn(Optional.of(option));

        assertThatThrownBy(() -> optionService.exerciseOption(1L, "agent@test.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("istekla");
    }

    @Test
    void exerciseOption_throwsWhenCallOptionIsNotInTheMoney() {
        mockAuthorizedActuary("agent@test.com", 12L);

        Option option = buildOption(
                1L,
                OptionType.CALL,
                new BigDecimal("170.00"),
                new BigDecimal("180.00"),
                LocalDate.now().plusDays(5),
                3
        );

        when(optionRepository.findById(1L)).thenReturn(Optional.of(option));

        assertThatThrownBy(() -> optionService.exerciseOption(1L, "agent@test.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("in-the-money");
    }

    @Test
    void exerciseOption_throwsWhenOpenInterestIsZero() {
        mockAuthorizedActuary("agent@test.com", 12L);

        Option option = buildOption(
                1L,
                OptionType.PUT,
                new BigDecimal("150.00"),
                new BigDecimal("180.00"),
                LocalDate.now().plusDays(5),
                0
        );

        when(optionRepository.findById(1L)).thenReturn(Optional.of(option));

        assertThatThrownBy(() -> optionService.exerciseOption(1L, "agent@test.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nema otvorenih ugovora");
    }

    @Test
    void exerciseOption_decrementsOpenInterestWhenValid() {
        mockAuthorizedActuary("agent@test.com", 12L);

        Option option = buildOption(
                1L,
                OptionType.CALL,
                new BigDecimal("210.00"),
                new BigDecimal("180.00"),
                LocalDate.now().plusDays(5),
                4
        );

        when(optionRepository.findById(1L)).thenReturn(Optional.of(option));

        // Stub bank account (Company ID=3, USD currency) needed by getBankAccount()
        Company bankCompany = new Company();
        bankCompany.setId(3L);
        Currency usd = new Currency();
        usd.setCode("USD");
        Account bankAccount = new Account();
        bankAccount.setCompany(bankCompany);
        bankAccount.setCurrency(usd);
        bankAccount.setBalance(new BigDecimal("10000000.00"));
        bankAccount.setAvailableBalance(new BigDecimal("10000000.00"));
        when(accountRepository.findAll()).thenReturn(java.util.List.of(bankAccount));
        when(portfolioRepository.findByUserId(12L)).thenReturn(java.util.Collections.emptyList());

        optionService.exerciseOption(1L, "agent@test.com");

        verify(optionRepository).save(option);
        assertThat(option.getOpenInterest()).isEqualTo(3);
    }

    private void mockAuthorizedActuary(String email, Long employeeId) {
        Employee employee = buildEmployee(employeeId, email, true, Set.of("AGENT"));
        ActuaryInfo actuaryInfo = new ActuaryInfo();
        actuaryInfo.setId(100L);
        actuaryInfo.setEmployee(employee);
        actuaryInfo.setActuaryType(ActuaryType.AGENT);
        actuaryInfo.setNeedApproval(false);

        when(employeeRepository.findByEmail(email)).thenReturn(Optional.of(employee));
        when(actuaryInfoRepository.findByEmployeeId(employeeId)).thenReturn(Optional.of(actuaryInfo));
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

    private Option buildOption(Long id,
                               OptionType optionType,
                               BigDecimal currentStockPrice,
                               BigDecimal strikePrice,
                               LocalDate settlementDate,
                               int openInterest) {

        Listing listing = new Listing();
        listing.setId(55L);
        listing.setTicker("AAPL");
        listing.setName("Apple Inc.");
        listing.setListingType(ListingType.STOCK);
        listing.setPrice(currentStockPrice);

        Option option = new Option();
        option.setId(id);
        option.setTicker("AAPL260402C00185000");
        option.setStockListing(listing);
        option.setOptionType(optionType);
        option.setStrikePrice(strikePrice);
        option.setSettlementDate(settlementDate);
        option.setOpenInterest(openInterest);
        option.setContractSize(100);
        return option;
    }
}