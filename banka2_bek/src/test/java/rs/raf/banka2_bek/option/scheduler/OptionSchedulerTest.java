package rs.raf.banka2_bek.option.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.option.model.Option;
import rs.raf.banka2_bek.option.model.OptionType;
import rs.raf.banka2_bek.option.repository.OptionRepository;
import rs.raf.banka2_bek.option.service.BlackScholesService;
import rs.raf.banka2_bek.option.service.OptionGeneratorService;
import rs.raf.banka2_bek.stock.model.Listing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OptionSchedulerTest {

    @Mock
    private OptionRepository optionRepository;

    @Mock
    private OptionGeneratorService optionGeneratorService;

    @Mock
    private BlackScholesService blackScholesService;

    @InjectMocks
    private OptionScheduler optionScheduler;

    private Option buildOption(Long id, OptionType type, BigDecimal strikePrice,
                               BigDecimal stockPrice, LocalDate settlementDate) {
        Listing listing = new Listing();
        listing.setId(id);
        listing.setPrice(stockPrice);

        Option option = new Option();
        option.setId(id);
        option.setOptionType(type);
        option.setStrikePrice(strikePrice);
        option.setImpliedVolatility(0.25);
        option.setStockListing(listing);
        option.setSettlementDate(settlementDate);
        option.setPrice(BigDecimal.TEN);
        option.setAsk(BigDecimal.valueOf(10.50));
        option.setBid(BigDecimal.valueOf(9.50));
        option.setTicker("TEST" + id);
        return option;
    }

    @Nested
    @DisplayName("dailyOptionMaintenance")
    class DailyOptionMaintenance {

        @Test
        @DisplayName("calls all three maintenance methods in sequence")
        void callsAllThreeMethods() {
            when(optionRepository.findBySettlementDateBefore(any())).thenReturn(Collections.emptyList());
            when(optionRepository.findAll()).thenReturn(Collections.emptyList());

            optionScheduler.dailyOptionMaintenance();

            verify(optionRepository).findBySettlementDateBefore(any());
            verify(optionGeneratorService).generateAllOptions();
            verify(optionRepository).findAll();
        }

        @Test
        @DisplayName("continues to generate and recalculate even if cleanup throws exception")
        void continuesAfterCleanupException() {
            when(optionRepository.findBySettlementDateBefore(any()))
                    .thenThrow(new RuntimeException("Cleanup error"));
            when(optionRepository.findAll()).thenReturn(Collections.emptyList());

            assertThatNoException().isThrownBy(() -> optionScheduler.dailyOptionMaintenance());

            verify(optionGeneratorService).generateAllOptions();
            verify(optionRepository).findAll();
        }

        @Test
        @DisplayName("continues to recalculate even if generation throws exception")
        void continuesAfterGenerationException() {
            when(optionRepository.findBySettlementDateBefore(any())).thenReturn(Collections.emptyList());
            doThrow(new RuntimeException("Generation error")).when(optionGeneratorService).generateAllOptions();
            when(optionRepository.findAll()).thenReturn(Collections.emptyList());

            assertThatNoException().isThrownBy(() -> optionScheduler.dailyOptionMaintenance());

            verify(optionRepository).findAll();
        }

        @Test
        @DisplayName("does not propagate exception from recalculation")
        void doesNotPropagateRecalculationException() {
            when(optionRepository.findBySettlementDateBefore(any())).thenReturn(Collections.emptyList());
            when(optionRepository.findAll()).thenThrow(new RuntimeException("Recalc error"));

            assertThatNoException().isThrownBy(() -> optionScheduler.dailyOptionMaintenance());
        }
    }

    @Nested
    @DisplayName("cleanupExpiredOptions")
    class CleanupExpiredOptions {

        @Test
        @DisplayName("deletes expired options when they exist")
        void deletesExpiredOptions() {
            Option expired = buildOption(1L, OptionType.CALL, BigDecimal.valueOf(100),
                    BigDecimal.valueOf(110), LocalDate.now().minusDays(1));
            when(optionRepository.findBySettlementDateBefore(any())).thenReturn(List.of(expired));

            // Access via dailyOptionMaintenance since cleanupExpiredOptions is protected
            when(optionRepository.findAll()).thenReturn(Collections.emptyList());

            optionScheduler.dailyOptionMaintenance();

            verify(optionRepository).deleteBySettlementDateBefore(any());
        }

        @Test
        @DisplayName("does not call delete when no expired options")
        void doesNotDeleteWhenNoExpired() {
            when(optionRepository.findBySettlementDateBefore(any())).thenReturn(Collections.emptyList());
            when(optionRepository.findAll()).thenReturn(Collections.emptyList());

            optionScheduler.dailyOptionMaintenance();

            verify(optionRepository, never()).deleteBySettlementDateBefore(any());
        }
    }

    @Nested
    @DisplayName("recalculatePrices")
    class RecalculatePrices {

        @Test
        @DisplayName("recalculates CALL option price using Black-Scholes")
        void recalculatesCallOption() {
            Option callOption = buildOption(1L, OptionType.CALL, BigDecimal.valueOf(100),
                    BigDecimal.valueOf(110), LocalDate.now().plusDays(30));

            when(optionRepository.findBySettlementDateBefore(any())).thenReturn(Collections.emptyList());
            when(optionRepository.findAll()).thenReturn(List.of(callOption));
            when(blackScholesService.calculateCallPrice(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(BigDecimal.valueOf(15.5000));

            optionScheduler.dailyOptionMaintenance();

            verify(blackScholesService).calculateCallPrice(
                    eq(110.0), eq(100.0), anyDouble(), eq(0.25));
            verify(optionRepository).saveAll(anyList());
        }

        @Test
        @DisplayName("recalculates PUT option price using Black-Scholes")
        void recalculatesPutOption() {
            Option putOption = buildOption(1L, OptionType.PUT, BigDecimal.valueOf(150),
                    BigDecimal.valueOf(140), LocalDate.now().plusDays(60));

            when(optionRepository.findBySettlementDateBefore(any())).thenReturn(Collections.emptyList());
            when(optionRepository.findAll()).thenReturn(List.of(putOption));
            when(blackScholesService.calculatePutPrice(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(BigDecimal.valueOf(12.3400));

            optionScheduler.dailyOptionMaintenance();

            verify(blackScholesService).calculatePutPrice(
                    eq(140.0), eq(150.0), anyDouble(), eq(0.25));
        }

        @Test
        @DisplayName("skips option with null stock price")
        void skipsOptionWithNullStockPrice() {
            Option option = buildOption(1L, OptionType.CALL, BigDecimal.valueOf(100),
                    null, LocalDate.now().plusDays(30));
            option.getStockListing().setPrice(null);

            when(optionRepository.findBySettlementDateBefore(any())).thenReturn(Collections.emptyList());
            when(optionRepository.findAll()).thenReturn(List.of(option));

            optionScheduler.dailyOptionMaintenance();

            verify(blackScholesService, never()).calculateCallPrice(anyDouble(), anyDouble(), anyDouble(), anyDouble());
            verify(blackScholesService, never()).calculatePutPrice(anyDouble(), anyDouble(), anyDouble(), anyDouble());
        }

        @Test
        @DisplayName("skips option with settlement date today or in the past (daysToExpiry <= 0)")
        void skipsExpiredOption() {
            Option option = buildOption(1L, OptionType.CALL, BigDecimal.valueOf(100),
                    BigDecimal.valueOf(110), LocalDate.now());

            when(optionRepository.findBySettlementDateBefore(any())).thenReturn(Collections.emptyList());
            when(optionRepository.findAll()).thenReturn(List.of(option));

            optionScheduler.dailyOptionMaintenance();

            verify(blackScholesService, never()).calculateCallPrice(anyDouble(), anyDouble(), anyDouble(), anyDouble());
        }

        @Test
        @DisplayName("updates ask to price * 1.05 and bid to price * 0.95")
        void updatesAskAndBid() {
            Option option = buildOption(1L, OptionType.CALL, BigDecimal.valueOf(100),
                    BigDecimal.valueOf(110), LocalDate.now().plusDays(30));
            BigDecimal newPrice = BigDecimal.valueOf(20.0000);

            when(optionRepository.findBySettlementDateBefore(any())).thenReturn(Collections.emptyList());
            when(optionRepository.findAll()).thenReturn(List.of(option));
            when(blackScholesService.calculateCallPrice(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(newPrice);

            optionScheduler.dailyOptionMaintenance();

            // ask = 20 * 1.05 = 21.0000, bid = 20 * 0.95 = 19.0000
            org.assertj.core.api.Assertions.assertThat(option.getPrice()).isEqualByComparingTo(newPrice);
            org.assertj.core.api.Assertions.assertThat(option.getAsk()).isEqualByComparingTo(new BigDecimal("21.0000"));
            org.assertj.core.api.Assertions.assertThat(option.getBid()).isEqualByComparingTo(new BigDecimal("19.0000"));
        }

        @Test
        @DisplayName("handles empty option list without error")
        void handlesEmptyList() {
            when(optionRepository.findBySettlementDateBefore(any())).thenReturn(Collections.emptyList());
            when(optionRepository.findAll()).thenReturn(Collections.emptyList());

            assertThatNoException().isThrownBy(() -> optionScheduler.dailyOptionMaintenance());

            verify(optionRepository).saveAll(Collections.emptyList());
        }
    }
}
