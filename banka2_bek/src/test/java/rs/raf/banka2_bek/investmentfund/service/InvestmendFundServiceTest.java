package rs.raf.banka2_bek.investmentfund.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import rs.raf.banka2_bek.investmentfund.dto.InvestmentFundDtos.FundPerformancePointDto;
import rs.raf.banka2_bek.investmentfund.model.FundValueSnapshot;
import rs.raf.banka2_bek.investmentfund.repository.FundValueSnapshotRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InvestmentFundServiceTest {
    @Mock
    private FundValueSnapshotRepository fundValueSnapshotRepository;

    @InjectMocks
    private rs.raf.banka2_bek.investmentfund.service.InvestmentFundService investmentFundService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetPerformanceDayGranularity() {
        LocalDate start = LocalDate.of(2023, 1, 1);
        List<FundValueSnapshot> snapshots = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            snapshots.add(new FundValueSnapshot((long) i, 1L, start.plusDays(i), BigDecimal.valueOf(100 + i), BigDecimal.valueOf(50), BigDecimal.valueOf(80), BigDecimal.valueOf(20 + i)));
        }
        when(fundValueSnapshotRepository.findByFundIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(1L, start, start.plusDays(4))).thenReturn(snapshots);
        var result = investmentFundService.getPerformance(1L, start, start.plusDays(4), rs.raf.banka2_bek.investmentfund.dto.InvestmentFundDtos.Granularity.DAY);
        assertEquals(5, result.size());
        assertEquals(start, result.get(0).getDate());
        assertEquals(BigDecimal.valueOf(100), result.get(0).getFundValue());
        assertEquals(BigDecimal.valueOf(20), result.get(0).getProfit());
    }

    @Test
    void testGetPerformanceWeekGranularity() {
        LocalDate start = LocalDate.of(2023, 1, 1);
        List<FundValueSnapshot> snapshots = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            snapshots.add(new FundValueSnapshot((long) i, 1L, start.plusDays(i), BigDecimal.valueOf(100 + i), BigDecimal.valueOf(50), BigDecimal.valueOf(80), BigDecimal.valueOf(20 + i)));
        }
        when(fundValueSnapshotRepository.findByFundIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(1L, start, start.plusDays(14))).thenReturn(snapshots);
        var result = investmentFundService.getPerformance(1L, start, start.plusDays(14), rs.raf.banka2_bek.investmentfund.dto.InvestmentFundDtos.Granularity.WEEK);
        assertTrue(result.size() >= 2 && result.size() <= 3); // 2 or 3 weeks
    }

    @Test
    void testGetPerformanceMonthGranularity() {
        LocalDate start = LocalDate.of(2023, 1, 1);
        List<FundValueSnapshot> snapshots = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            snapshots.add(new FundValueSnapshot((long) i, 1L, start.plusDays(i), BigDecimal.valueOf(100 + i), BigDecimal.valueOf(50), BigDecimal.valueOf(80), BigDecimal.valueOf(20 + i)));
        }
        when(fundValueSnapshotRepository.findByFundIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(1L, start, start.plusDays(59))).thenReturn(snapshots);
        var result = investmentFundService.getPerformance(1L, start, start.plusDays(59), rs.raf.banka2_bek.investmentfund.dto.InvestmentFundDtos.Granularity.MONTH);
        assertTrue(result.size() >= 2 && result.size() <= 3); // 2 or 3 months
    }

    @Test
    void testGetPerformanceQuarterGranularity() {
        LocalDate start = LocalDate.of(2023, 1, 1);
        List<FundValueSnapshot> snapshots = new ArrayList<>();
        for (int i = 0; i < 180; i++) {
            snapshots.add(new FundValueSnapshot((long) i, 1L, start.plusDays(i), BigDecimal.valueOf(100 + i), BigDecimal.valueOf(50), BigDecimal.valueOf(80), BigDecimal.valueOf(20 + i)));
        }
        when(fundValueSnapshotRepository.findByFundIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(1L, start, start.plusDays(179))).thenReturn(snapshots);
        var result = investmentFundService.getPerformance(1L, start, start.plusDays(179), rs.raf.banka2_bek.investmentfund.dto.InvestmentFundDtos.Granularity.QUARTER);
        assertTrue(result.size() >= 2 && result.size() <= 3); // 2 or 3 quarters
    }

    @Test
    void testGetPerformanceYearGranularity() {
        LocalDate start = LocalDate.of(2020, 1, 1);
        List<FundValueSnapshot> snapshots = new ArrayList<>();
        for (int i = 0; i < 730; i++) {
            snapshots.add(new FundValueSnapshot((long) i, 1L, start.plusDays(i), BigDecimal.valueOf(100 + i), BigDecimal.valueOf(50), BigDecimal.valueOf(80), BigDecimal.valueOf(20 + i)));
        }
        when(fundValueSnapshotRepository.findByFundIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(1L, start, start.plusDays(729))).thenReturn(snapshots);
        var result = investmentFundService.getPerformance(1L, start, start.plusDays(729), rs.raf.banka2_bek.investmentfund.dto.InvestmentFundDtos.Granularity.YEAR);
        assertTrue(result.size() >= 2 && result.size() <= 3); // 2 or 3 years
    }

    @Test
    void testGetPerformanceMonthIntegration() {
        LocalDate start = LocalDate.of(2023, 1, 1);
        List<FundValueSnapshot> snapshots = new ArrayList<>();
        for (int i = 0; i < 90; i++) {
            snapshots.add(new FundValueSnapshot((long) i, 1L, start.plusDays(i), BigDecimal.valueOf(100 + i), BigDecimal.valueOf(50), BigDecimal.valueOf(80), BigDecimal.valueOf(20 + i)));
        }
        when(fundValueSnapshotRepository.findByFundIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(1L, start, start.plusDays(89))).thenReturn(snapshots);
        var result = investmentFundService.getPerformance(1L, start, start.plusDays(89), rs.raf.banka2_bek.investmentfund.dto.InvestmentFundDtos.Granularity.MONTH);
        assertEquals(3, result.size());
    }
}
