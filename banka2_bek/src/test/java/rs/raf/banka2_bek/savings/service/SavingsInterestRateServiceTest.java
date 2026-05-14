package rs.raf.banka2_bek.savings.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.currency.repository.CurrencyRepository;
import rs.raf.banka2_bek.savings.dto.SavingsRateDto;
import rs.raf.banka2_bek.savings.dto.UpsertSavingsRateDto;
import rs.raf.banka2_bek.savings.entity.SavingsInterestRate;
import rs.raf.banka2_bek.savings.mapper.SavingsMapper;
import rs.raf.banka2_bek.savings.repository.SavingsInterestRateRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SavingsInterestRateServiceTest {

    @Mock SavingsInterestRateRepository rateRepo;
    @Mock CurrencyRepository currencyRepo;
    @Mock SavingsMapper mapper;
    @InjectMocks SavingsInterestRateService service;

    private Currency rsd() {
        Currency c = new Currency();
        c.setId(1L);
        c.setCode("RSD");
        return c;
    }

    @Test
    void findActive_returnsRate() {
        SavingsInterestRate rate = SavingsInterestRate.builder()
                .id(1L).currency(rsd()).termMonths(12).annualRate(new BigDecimal("4.00"))
                .active(true).effectiveFrom(LocalDate.now()).build();
        when(rateRepo.findActive(1L, 12)).thenReturn(Optional.of(rate));

        Optional<SavingsInterestRate> result = service.findActive(1L, 12);
        assertThat(result).isPresent();
        assertThat(result.get().getAnnualRate()).isEqualByComparingTo("4.00");
    }

    @Test
    void findActive_empty() {
        when(rateRepo.findActive(99L, 7)).thenReturn(Optional.empty());
        assertThat(service.findActive(99L, 7)).isEmpty();
    }

    @Test
    void listActive_byCurrencyCode_filters() {
        SavingsInterestRate r = SavingsInterestRate.builder()
                .id(1L).currency(rsd()).termMonths(12).annualRate(new BigDecimal("4.00"))
                .active(true).effectiveFrom(LocalDate.now()).build();
        SavingsRateDto dto = SavingsRateDto.builder().id(1L).currencyCode("RSD").termMonths(12).build();
        when(rateRepo.findActiveByCurrencyCode("RSD")).thenReturn(List.of(r));
        when(mapper.toRateDto(r)).thenReturn(dto);

        List<SavingsRateDto> result = service.listActive("RSD");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCurrencyCode()).isEqualTo("RSD");
    }

    @Test
    void listActive_noCurrency_returnsAll() {
        when(rateRepo.findAllActive()).thenReturn(List.of());
        assertThat(service.listActive(null)).isEmpty();
    }

    @Test
    void listAll_delegatesToRepo() {
        when(rateRepo.findAll()).thenReturn(List.of());
        assertThat(service.listAll()).isEmpty();
        verify(rateRepo).findAll();
    }

    @Test
    void upsert_deactivatesExistingAndCreatesNew() {
        Currency c = rsd();
        SavingsInterestRate existing = SavingsInterestRate.builder()
                .id(1L).currency(c).termMonths(12).annualRate(new BigDecimal("3.50"))
                .active(true).effectiveFrom(LocalDate.now().minusMonths(2)).build();

        UpsertSavingsRateDto dto = new UpsertSavingsRateDto();
        dto.setCurrencyCode("RSD");
        dto.setTermMonths(12);
        dto.setAnnualRate(new BigDecimal("4.25"));

        when(currencyRepo.findByCode("RSD")).thenReturn(Optional.of(c));
        when(rateRepo.findActive(1L, 12)).thenReturn(Optional.of(existing));
        when(rateRepo.save(any(SavingsInterestRate.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toRateDto(any(SavingsInterestRate.class))).thenAnswer(inv -> {
            SavingsInterestRate r = inv.getArgument(0);
            return SavingsRateDto.builder().annualRate(r.getAnnualRate()).build();
        });

        SavingsRateDto result = service.upsert(dto);

        assertThat(existing.getActive()).isFalse();
        assertThat(result.getAnnualRate()).isEqualByComparingTo("4.25");
        // existing (deactivated) + new rate = 2 saves
        verify(rateRepo, times(2)).save(any(SavingsInterestRate.class));
    }

    @Test
    void upsert_noExistingRate_createsNew() {
        Currency c = rsd();
        UpsertSavingsRateDto dto = new UpsertSavingsRateDto();
        dto.setCurrencyCode("RSD");
        dto.setTermMonths(6);
        dto.setAnnualRate(new BigDecimal("3.00"));

        when(currencyRepo.findByCode("RSD")).thenReturn(Optional.of(c));
        when(rateRepo.findActive(1L, 6)).thenReturn(Optional.empty());
        when(rateRepo.save(any(SavingsInterestRate.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toRateDto(any(SavingsInterestRate.class))).thenAnswer(inv -> {
            SavingsInterestRate r = inv.getArgument(0);
            return SavingsRateDto.builder().annualRate(r.getAnnualRate()).termMonths(r.getTermMonths()).build();
        });

        SavingsRateDto result = service.upsert(dto);

        assertThat(result.getAnnualRate()).isEqualByComparingTo("3.00");
        // only new rate saved (no existing to deactivate)
        verify(rateRepo, times(1)).save(any(SavingsInterestRate.class));
    }

    @Test
    void upsert_currencyNotFound_throws() {
        UpsertSavingsRateDto dto = new UpsertSavingsRateDto();
        dto.setCurrencyCode("XYZ");
        dto.setTermMonths(12);
        dto.setAnnualRate(new BigDecimal("4.00"));
        when(currencyRepo.findByCode("XYZ")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upsert(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Valuta ne postoji");
    }
}
