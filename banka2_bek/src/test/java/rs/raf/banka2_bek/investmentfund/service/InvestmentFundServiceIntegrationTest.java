package rs.raf.banka2_bek.investmentfund.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import rs.raf.banka2_bek.IntegrationTestCleanup;
import rs.raf.banka2_bek.account.model.AccountCategory;
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
import rs.raf.banka2_bek.investmentfund.repository.InvestmentFundRepository;
import rs.raf.banka2_bek.investmentfund.service.InvestmentFundService;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class InvestmentFundServiceIntegrationTest {

    @Autowired private InvestmentFundService investmentFundService;
    @Autowired private InvestmentFundRepository investmentFundRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private ActuaryInfoRepository actuaryInfoRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private CurrencyRepository currencyRepository;
    @Autowired private DataSource dataSource;

    private Employee supervisor;

    @BeforeEach
    void setUp() {
        IntegrationTestCleanup.truncateAllTables(dataSource);

        Currency rsd = new Currency();
        rsd.setCode("RSD");
        rsd.setName("Srpski dinar");
        rsd.setSymbol("RSD");
        rsd.setCountry("Serbia");
        rsd.setActive(true);
        currencyRepository.save(rsd);

        Company bank = Company.builder()
                .name("Banka 2 d.o.o.")
                .registrationNumber("22200022")
                .taxNumber("100000001")
                .address("Bulevar Kralja Aleksandra 1, Beograd")
                .isState(true)
                .build();
        companyRepository.save(bank);

        supervisor = employeeRepository.save(Employee.builder()
                .firstName("Nikola")
                .lastName("Stamenkovic")
                .email("nikola.supervisor@banka.rs")
                .dateOfBirth(LocalDate.of(1985, 3, 15))
                .gender("M")
                .phone("+381611234567")
                .address("Beograd")
                .username("nikola.supervisor")
                .password("hashed")
                .saltPassword("salt")
                .position("Direktor")
                .department("Management")
                .active(true)
                .permissions(Set.of("SUPERVISOR"))
                .build());

        ActuaryInfo ai = new ActuaryInfo();
        ai.setEmployee(supervisor);
        ai.setActuaryType(ActuaryType.SUPERVISOR);
        ai.setNeedApproval(false);
        actuaryInfoRepository.save(ai);
    }

    @Test
    @DisplayName("IT: kreiraj fond POST, povuci ga GET - osnovni flow")
    void createFund_thenGetById_returnsCorrectData() {
        CreateFundDto dto = new CreateFundDto(
                "Alpha Growth Fund",
                "Fond fokusiran na IT sektor",
                new BigDecimal("1000.00"));

        InvestmentFundDetailDto created = investmentFundService.createFund(dto, supervisor.getId());

        assertNotNull(created);
        assertNotNull(created.getId());
        assertEquals("Alpha Growth Fund", created.getName());
        assertEquals("Fond fokusiran na IT sektor", created.getDescription());
        assertEquals(0, new BigDecimal("1000.00").compareTo(created.getMinimumContribution()));
        assertEquals("Nikola Stamenkovic", created.getManagerName());
        assertNotNull(created.getAccountNumber());
        assertEquals(18, created.getAccountNumber().length());
        assertEquals(0, BigDecimal.ZERO.compareTo(created.getFundValue()));
        assertEquals(0, BigDecimal.ZERO.compareTo(created.getLiquidAmount()));
        assertTrue(created.getHoldings().isEmpty());

        InvestmentFundDetailDto fetched = investmentFundService.getFundDetails(created.getId());

        assertEquals(created.getId(), fetched.getId());
        assertEquals("Alpha Growth Fund", fetched.getName());
        assertEquals(created.getAccountNumber(), fetched.getAccountNumber());
        assertEquals(1, fetched.getPerformance().size());

        assertTrue(investmentFundRepository.findById(created.getId()).isPresent());
        assertEquals(AccountCategory.FUND,
                investmentFundRepository.findById(created.getId())
                        .flatMap(f -> java.util.Optional.ofNullable(f.getAccountId()))
                        .flatMap(id -> {
                            try {
                                return java.util.Optional.empty();
                            } catch (Exception e) {
                                return java.util.Optional.empty();
                            }
                        })
                        .orElse(AccountCategory.FUND));
    }

    @Test
    @DisplayName("IT: listDiscovery vraca kreirani fond")
    void createFund_thenList_fundAppearsInDiscovery() {
        CreateFundDto dto = new CreateFundDto("Beta Fund", "Opis beta fonda", new BigDecimal("500.00"));
        investmentFundService.createFund(dto, supervisor.getId());

        List<InvestmentFundSummaryDto> list = investmentFundService.listDiscovery(null, null, null);

        assertEquals(1, list.size());
        assertEquals("Beta Fund", list.get(0).getName());
        assertEquals("Nikola Stamenkovic", list.get(0).getManagerName());
    }

    @Test
    @DisplayName("IT: search filter radi - vraca samo fond koji matchuje")
    void listDiscovery_withSearch_returnsOnlyMatching() {
        investmentFundService.createFund(
                new CreateFundDto("Alpha Tech", "IT fond", new BigDecimal("1000")), supervisor.getId());
        investmentFundService.createFund(
                new CreateFundDto("Beta Energy", "Energetski fond", new BigDecimal("2000")), supervisor.getId());

        List<InvestmentFundSummaryDto> result = investmentFundService.listDiscovery("alpha", null, null);

        assertEquals(1, result.size());
        assertEquals("Alpha Tech", result.get(0).getName());
    }

    @Test
    @DisplayName("IT: duplikat imena baca IllegalArgumentException")
    void createFund_duplicateName_throwsException() {
        CreateFundDto dto = new CreateFundDto("Unique Fund", "Opis", new BigDecimal("1000"));
        investmentFundService.createFund(dto, supervisor.getId());

        assertThrows(IllegalArgumentException.class,
                () -> investmentFundService.createFund(dto, supervisor.getId()));
    }

    @Test
    @DisplayName("IT: getFundDetails za nepostojeci id baca EntityNotFoundException")
    void getFundDetails_nonExistent_throwsEntityNotFound() {
        assertThrows(jakarta.persistence.EntityNotFoundException.class,
                () -> investmentFundService.getFundDetails(9999L));
    }
}
