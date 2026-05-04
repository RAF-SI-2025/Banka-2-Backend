package rs.raf.banka2_bek.investmentfund.service;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.account.model.*;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.actuary.model.ActuaryInfo;
import rs.raf.banka2_bek.actuary.model.ActuaryType;
import rs.raf.banka2_bek.actuary.repository.ActuaryInfoRepository;
import rs.raf.banka2_bek.company.model.Company;
import rs.raf.banka2_bek.company.repository.CompanyRepository;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.currency.repository.CurrencyRepository;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;
import rs.raf.banka2_bek.investmentfund.dto.InvestmentFundDtos.*;
import rs.raf.banka2_bek.investmentfund.model.*;
import rs.raf.banka2_bek.investmentfund.repository.*;
import rs.raf.banka2_bek.investmentfund.service.FundValueCalculator;
import rs.raf.banka2_bek.investmentfund.service.InvestmentFundService;
import rs.raf.banka2_bek.order.service.CurrencyConversionService;
import rs.raf.banka2_bek.portfolio.repository.PortfolioRepository;
import rs.raf.banka2_bek.stock.repository.ListingRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvestmentFundServiceCoreTest {

    @Mock private InvestmentFundRepository investmentFundRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private ActuaryInfoRepository actuaryInfoRepository;
    @Mock private ClientFundPositionRepository clientFundPositionRepository;
    @Mock private FundValueSnapshotRepository fundValueSnapshotRepository;
    @Mock private PortfolioRepository portfolioRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private FundValueCalculator fundValueCalculator;
    @Mock private CurrencyConversionService currencyConversionService;

    @InjectMocks
    private InvestmentFundService service;

    private Employee buildSupervisor(Long id) {
        Employee e = new Employee();
        e.setId(id);
        e.setFirstName("Marko");
        e.setLastName("Petrovic");
        e.setEmail("marko@banka.rs");
        e.setActive(true);
        return e;
    }

    private ActuaryInfo buildActuaryInfo(Employee e, ActuaryType type) {
        ActuaryInfo ai = new ActuaryInfo();
        ai.setEmployee(e);
        ai.setActuaryType(type);
        return ai;
    }

    private Company buildBankCompany() {
        Company c = new Company();
        c.setId(1L);
        c.setName("Banka 2 d.o.o.");
        c.setRegistrationNumber("12345678");
        c.setTaxNumber("111222333");
        c.setAddress("Beograd");
        c.setIsState(true);
        return c;
    }

    private Currency buildRsd() {
        Currency rsd = new Currency();
        rsd.setId(1L);
        rsd.setCode("RSD");
        return rsd;
    }

    private Account buildFundAccount(Long id, String number) {
        Account a = new Account();
        a.setId(id);
        a.setAccountNumber(number);
        a.setBalance(BigDecimal.ZERO);
        a.setAvailableBalance(BigDecimal.ZERO);
        a.setAccountCategory(AccountCategory.FUND);
        a.setStatus(AccountStatus.ACTIVE);
        return a;
    }

    private InvestmentFund buildFund(Long id, String name, Long accountId, Long managerId) {
        InvestmentFund f = new InvestmentFund();
        f.setId(id);
        f.setName(name);
        f.setDescription("Opis fonda");
        f.setMinimumContribution(new BigDecimal("1000.00"));
        f.setManagerEmployeeId(managerId);
        f.setAccountId(accountId);
        f.setCreatedAt(LocalDateTime.now());
        f.setInceptionDate(LocalDate.now());
        f.setActive(true);
        return f;
    }

    @Test
    @DisplayName("createFund - happy path vraca detaljan DTO")
    void createFund_happyPath() {
        Long supervisorId = 1L;
        CreateFundDto dto = new CreateFundDto("Test Fund", "Opis", new BigDecimal("500.00"));

        Employee supervisor = buildSupervisor(supervisorId);
        ActuaryInfo actuaryInfo = buildActuaryInfo(supervisor, ActuaryType.SUPERVISOR);
        Company bank = buildBankCompany();
        Currency rsd = buildRsd();
        Account savedAccount = buildFundAccount(10L, "222000100000000012");
        InvestmentFund savedFund = buildFund(1L, dto.getName(), 10L, supervisorId);

        when(investmentFundRepository.findByName(dto.getName())).thenReturn(Optional.empty());
        when(actuaryInfoRepository.findByEmployeeId(supervisorId)).thenReturn(Optional.of(actuaryInfo));
        when(employeeRepository.findById(supervisorId)).thenReturn(Optional.of(supervisor));
        when(currencyRepository.findByCode("RSD")).thenReturn(Optional.of(rsd));
        when(companyRepository.findByIsBankTrue()).thenReturn(Optional.of(bank));
        when(accountRepository.existsByAccountNumber(anyString())).thenReturn(false);
        when(accountRepository.save(any())).thenReturn(savedAccount);
        when(investmentFundRepository.save(any())).thenReturn(savedFund);
        when(fundValueSnapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InvestmentFundDetailDto result = service.createFund(dto, supervisorId);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Test Fund", result.getName());
        assertEquals("222000100000000012", result.getAccountNumber());
        assertEquals("Marko Petrovic", result.getManagerName());
        assertEquals(BigDecimal.ZERO, result.getLiquidAmount());
        assertTrue(result.getHoldings().isEmpty());

        verify(investmentFundRepository).save(any(InvestmentFund.class));
        verify(accountRepository).save(any(Account.class));
        verify(fundValueSnapshotRepository).save(any(FundValueSnapshot.class));
    }

    @Test
    @DisplayName("createFund - duplikat imena baca IllegalArgumentException")
    void createFund_duplicateName_throwsIllegalArgument() {
        CreateFundDto dto = new CreateFundDto("Postojeci Fond", "opis", new BigDecimal("100"));
        when(investmentFundRepository.findByName("Postojeci Fond"))
                .thenReturn(Optional.of(new InvestmentFund()));

        assertThrows(IllegalArgumentException.class, () -> service.createFund(dto, 1L));
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("createFund - agent (ne supervizor) baca IllegalStateException")
    void createFund_notSupervisor_throwsIllegalState() {
        Long agentId = 2L;
        CreateFundDto dto = new CreateFundDto("Novi Fond", "opis", new BigDecimal("1000"));

        Employee agent = buildSupervisor(agentId);
        ActuaryInfo agentInfo = buildActuaryInfo(agent, ActuaryType.AGENT);

        when(investmentFundRepository.findByName(dto.getName())).thenReturn(Optional.empty());
        when(actuaryInfoRepository.findByEmployeeId(agentId)).thenReturn(Optional.of(agentInfo));

        assertThrows(IllegalStateException.class, () -> service.createFund(dto, agentId));
        verify(investmentFundRepository, never()).save(any());
    }

    @Test
    @DisplayName("createFund - ne supervizor (nema ActuaryInfo) baca IllegalStateException")
    void createFund_noActuaryInfo_throwsIllegalState() {
        Long empId = 3L;
        CreateFundDto dto = new CreateFundDto("Fund X", "opis", new BigDecimal("500"));

        when(investmentFundRepository.findByName(dto.getName())).thenReturn(Optional.empty());
        when(actuaryInfoRepository.findByEmployeeId(empId)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> service.createFund(dto, empId));
    }

    @Test
    @DisplayName("listDiscovery - vraca sve aktivne fondove")
    void listDiscovery_returnsAllActive() {
        Employee supervisor = buildSupervisor(1L);
        InvestmentFund f1 = buildFund(1L, "Alpha Fund", 10L, 1L);
        InvestmentFund f2 = buildFund(2L, "Beta Fund", 11L, 1L);

        when(investmentFundRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(f1, f2));
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(supervisor));
        when(fundValueCalculator.computeFundValue(any())).thenReturn(new BigDecimal("50000.00"));
        when(fundValueCalculator.computeProfit(any())).thenReturn(new BigDecimal("5000.00"));

        List<InvestmentFundSummaryDto> result = service.listDiscovery(null, null, null);

        assertEquals(2, result.size());
        assertEquals("Alpha Fund", result.get(0).getName());
        assertEquals(new BigDecimal("50000.00"), result.get(0).getFundValue());
        assertEquals(new BigDecimal("5000.00"), result.get(0).getProfit());
        assertEquals("Marko Petrovic", result.get(0).getManagerName());
    }

    @Test
    @DisplayName("listDiscovery - search filter vraca samo matchirajuce fondove")
    void listDiscovery_withSearch_filtersCorrectly() {
        Employee supervisor = buildSupervisor(1L);
        InvestmentFund f1 = buildFund(1L, "Alpha IT Fund", 10L, 1L);
        InvestmentFund f2 = buildFund(2L, "Beta Energija", 11L, 1L);

        when(investmentFundRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(f1, f2));
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(supervisor));
        when(fundValueCalculator.computeFundValue(any())).thenReturn(BigDecimal.ZERO);
        when(fundValueCalculator.computeProfit(any())).thenReturn(BigDecimal.ZERO);

        List<InvestmentFundSummaryDto> result = service.listDiscovery("alpha", null, null);

        assertEquals(1, result.size());
        assertEquals("Alpha IT Fund", result.get(0).getName());
    }

    @Test
    @DisplayName("listDiscovery - prazna lista kad nema aktivnih fondova")
    void listDiscovery_noFunds_returnsEmpty() {
        when(investmentFundRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of());

        List<InvestmentFundSummaryDto> result = service.listDiscovery(null, null, null);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("listDiscovery - sortiranje po fundValue DESC")
    void listDiscovery_sortByFundValueDesc() {
        Employee supervisor = buildSupervisor(1L);
        InvestmentFund f1 = buildFund(1L, "A Fund", 10L, 1L);
        InvestmentFund f2 = buildFund(2L, "B Fund", 11L, 1L);

        when(investmentFundRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(f1, f2));
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(supervisor));
        when(fundValueCalculator.computeFundValue(f1)).thenReturn(new BigDecimal("10000"));
        when(fundValueCalculator.computeFundValue(f2)).thenReturn(new BigDecimal("50000"));
        when(fundValueCalculator.computeProfit(any())).thenReturn(BigDecimal.ZERO);

        List<InvestmentFundSummaryDto> result = service.listDiscovery(null, "fundValue", "DESC");

        assertEquals("B Fund", result.get(0).getName());
        assertEquals("A Fund", result.get(1).getName());
    }

    @Test
    @DisplayName("getFundDetails - vraca kompletan detaljan DTO")
    void getFundDetails_happyPath() {
        Long fundId = 1L;
        Employee supervisor = buildSupervisor(1L);
        InvestmentFund fund = buildFund(fundId, "Test Fund", 10L, 1L);
        Account account = buildFundAccount(10L, "222000100000000012");
        account.setBalance(new BigDecimal("150000.00"));

        FundValueSnapshot snap = new FundValueSnapshot();
        snap.setFundId(fundId);
        snap.setSnapshotDate(LocalDate.now().minusDays(1));
        snap.setFundValue(new BigDecimal("145000.00"));
        snap.setProfit(new BigDecimal("5000.00"));

        when(investmentFundRepository.findById(fundId)).thenReturn(Optional.of(fund));
        when(accountRepository.findById(10L)).thenReturn(Optional.of(account));
        when(fundValueCalculator.computeFundValue(fund)).thenReturn(new BigDecimal("150000.00"));
        when(fundValueCalculator.computeProfit(fund)).thenReturn(new BigDecimal("10000.00"));
        when(portfolioRepository.findByUserIdAndUserRole(fundId, "FUND")).thenReturn(List.of());
        when(fundValueSnapshotRepository.findByFundIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
                eq(fundId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(snap));
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(supervisor));

        InvestmentFundDetailDto result = service.getFundDetails(fundId);

        assertNotNull(result);
        assertEquals(fundId, result.getId());
        assertEquals("Test Fund", result.getName());
        assertEquals(new BigDecimal("150000.00"), result.getFundValue());
        assertEquals(new BigDecimal("150000.00"), result.getLiquidAmount());
        assertEquals(new BigDecimal("10000.00"), result.getProfit());
        assertEquals("222000100000000012", result.getAccountNumber());
        assertEquals("Marko Petrovic", result.getManagerName());
        assertTrue(result.getHoldings().isEmpty());
        assertEquals(1, result.getPerformance().size());
        assertEquals(new BigDecimal("145000.00"), result.getPerformance().get(0).getFundValue());
    }

    @Test
    @DisplayName("getFundDetails - nepostojeci fond baca EntityNotFoundException")
    void getFundDetails_notFound_throwsEntityNotFound() {
        when(investmentFundRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.getFundDetails(999L));
    }

    @Test
    @DisplayName("getFundDetails - performance lista je prazna za novi fond bez snapshots")
    void getFundDetails_noSnapshots_emptyPerformance() {
        Long fundId = 2L;
        Employee supervisor = buildSupervisor(1L);
        InvestmentFund fund = buildFund(fundId, "Novi Fond", 20L, 1L);
        Account account = buildFundAccount(20L, "222000200000000011");

        when(investmentFundRepository.findById(fundId)).thenReturn(Optional.of(fund));
        when(accountRepository.findById(20L)).thenReturn(Optional.of(account));
        when(fundValueCalculator.computeFundValue(fund)).thenReturn(BigDecimal.ZERO);
        when(fundValueCalculator.computeProfit(fund)).thenReturn(BigDecimal.ZERO);
        when(portfolioRepository.findByUserIdAndUserRole(fundId, "FUND")).thenReturn(List.of());
        when(fundValueSnapshotRepository.findByFundIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
                eq(fundId), any(), any())).thenReturn(List.of());
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(supervisor));

        InvestmentFundDetailDto result = service.getFundDetails(fundId);

        assertTrue(result.getPerformance().isEmpty());
    }
}
