package rs.raf.banka2_bek.portfolio.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;
import rs.raf.banka2_bek.portfolio.dto.PortfolioItemDto;
import rs.raf.banka2_bek.portfolio.dto.PortfolioSummaryDto;
import rs.raf.banka2_bek.portfolio.model.Portfolio;
import rs.raf.banka2_bek.portfolio.repository.PortfolioRepository;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.model.ListingType;
import rs.raf.banka2_bek.stock.repository.ListingRepository;
import rs.raf.banka2_bek.tax.model.TaxRecord;
import rs.raf.banka2_bek.tax.repository.TaxRecordRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Dodatni testovi za {@link PortfolioService} koji pokrivaju grane
 * koje nisu dotaknute u {@link PortfolioServiceTest}:
 * - employee path kroz {@code getCurrentUserId()}
 * - {@code isEmployee()} true grana (ROLE_ADMIN i ROLE_EMPLOYEE)
 * - {@code taxRecord.isPresent()} grana (plaćeno, dugovanje veće, dugovanje manje)
 * - {@code setPublicQuantity} kada je {@code avgPrice == 0}
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PortfolioServiceCoverageTest {

    @Mock private PortfolioRepository portfolioRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private TaxRecordRepository taxRecordRepository;

    @InjectMocks
    private PortfolioService portfolioService;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void authAsEmployee(String email, Long employeeId, String role) {
        Employee emp = new Employee();
        emp.setId(employeeId);
        emp.setEmail(email);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        email, null,
                        List.of(new SimpleGrantedAuthority(role))));
        lenient().when(clientRepository.findByEmail(email)).thenReturn(Optional.empty());
        lenient().when(employeeRepository.findByEmail(email)).thenReturn(Optional.of(emp));
    }

    private void authAsClient(String email, Long clientId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null, List.of()));
        // Neither client nor employee needed for stubbing here — caller stubs below.
        lenient().when(clientRepository.findByEmail(email))
                .thenReturn(Optional.of(
                        rs.raf.banka2_bek.client.model.Client.builder()
                                .id(clientId).email(email).firstName("C").lastName("L").build()));
    }

    private Portfolio portfolio(Long id, Long userId, Long listingId, int qty, BigDecimal avg) {
        Portfolio p = new Portfolio();
        p.setId(id);
        p.setUserId(userId);
        p.setListingId(listingId);
        p.setListingTicker("T");
        p.setListingName("Name");
        p.setListingType("STOCK");
        p.setQuantity(qty);
        p.setAverageBuyPrice(avg);
        p.setPublicQuantity(0);
        p.setLastModified(LocalDateTime.now());
        return p;
    }

    private Listing listing(Long id, BigDecimal price) {
        Listing l = new Listing();
        l.setId(id);
        l.setPrice(price);
        l.setListingType(ListingType.STOCK);
        return l;
    }

    // ─── getCurrentUserId — employee path (L50–52) ──────────────────────────

    @Test
    @DisplayName("getMyPortfolio radi za employee-a (clients.findByEmail empty)")
    void employeeUserResolvedViaEmployeeRepo() {
        authAsEmployee("emp@banka.rs", 7L, "ROLE_EMPLOYEE");
        when(portfolioRepository.findByUserId(7L)).thenReturn(Collections.emptyList());

        List<PortfolioItemDto> items = portfolioService.getMyPortfolio();

        assertThat(items).isEmpty();
    }

    // ─── getSummary — isEmployee() true granu (L134) + taxRecord branches (L136) ──

    @Test
    @DisplayName("getSummary — employee sa TaxRecord-om (taxOwed > paid, remaining > 0)")
    void employeeSummaryWithTaxRecordRemainingPositive() {
        authAsEmployee("emp@banka.rs", 7L, "ROLE_ADMIN");
        Portfolio p = portfolio(1L, 7L, 10L, 10, new BigDecimal("100.00"));
        when(portfolioRepository.findByUserId(7L)).thenReturn(List.of(p));
        when(listingRepository.findById(10L)).thenReturn(Optional.of(listing(10L, new BigDecimal("120.00"))));

        TaxRecord rec = TaxRecord.builder()
                .userId(7L)
                .userName("E")
                .userType("EMPLOYEE")
                .taxOwed(new BigDecimal("30.00"))
                .taxPaid(new BigDecimal("10.00"))
                .build();
        when(taxRecordRepository.findByUserIdAndUserType(7L, "EMPLOYEE"))
                .thenReturn(Optional.of(rec));

        PortfolioSummaryDto s = portfolioService.getSummary();

        assertThat(s.getPaidTaxThisYear()).isEqualByComparingTo("10.00");
        assertThat(s.getUnpaidTaxThisMonth()).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("getSummary — TaxRecord remaining <= 0 daje unpaid=0")
    void clientSummaryWithTaxRecordFullyPaid() {
        authAsClient("cl@test.com", 3L);
        Portfolio p = portfolio(1L, 3L, 10L, 10, new BigDecimal("100.00"));
        when(portfolioRepository.findByUserId(3L)).thenReturn(List.of(p));
        when(listingRepository.findById(10L)).thenReturn(Optional.of(listing(10L, new BigDecimal("120.00"))));

        TaxRecord rec = TaxRecord.builder()
                .userId(3L)
                .userName("C")
                .userType("CLIENT")
                .taxOwed(new BigDecimal("10.00"))
                .taxPaid(new BigDecimal("30.00")) // paid > owed → remaining negative
                .build();
        when(taxRecordRepository.findByUserIdAndUserType(3L, "CLIENT"))
                .thenReturn(Optional.of(rec));

        PortfolioSummaryDto s = portfolioService.getSummary();

        assertThat(s.getPaidTaxThisYear()).isEqualByComparingTo("30.00");
        assertThat(s.getUnpaidTaxThisMonth()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("getSummary — TaxRecord sa null taxPaid i null taxOwed")
    void clientSummaryWithNullTaxFields() {
        authAsClient("cl@test.com", 3L);
        when(portfolioRepository.findByUserId(3L)).thenReturn(Collections.emptyList());

        TaxRecord rec = new TaxRecord();
        rec.setUserId(3L);
        rec.setUserType("CLIENT");
        rec.setTaxPaid(null);
        rec.setTaxOwed(null);
        when(taxRecordRepository.findByUserIdAndUserType(3L, "CLIENT"))
                .thenReturn(Optional.of(rec));

        PortfolioSummaryDto s = portfolioService.getSummary();

        assertThat(s.getPaidTaxThisYear()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(s.getUnpaidTaxThisMonth()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ─── setPublicQuantity — avgPrice == 0 branch (L191) ─────────────────────

    @Test
    @DisplayName("setPublicQuantity — avgPrice=0 ne racuna profitPercent")
    void setPublicQuantityWithZeroAvgPrice() {
        authAsClient("cl@test.com", 1L);
        Portfolio p = portfolio(1L, 1L, 10L, 100, BigDecimal.ZERO);
        when(portfolioRepository.findById(1L)).thenReturn(Optional.of(p));
        when(listingRepository.findById(10L)).thenReturn(Optional.of(listing(10L, new BigDecimal("50.00"))));

        PortfolioItemDto dto = portfolioService.setPublicQuantity(1L, 10);

        assertThat(dto.getProfitPercent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(dto.getPublicQuantity()).isEqualTo(10);
    }
}
