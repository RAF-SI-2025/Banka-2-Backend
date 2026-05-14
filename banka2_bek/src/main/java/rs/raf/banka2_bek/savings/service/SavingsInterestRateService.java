package rs.raf.banka2_bek.savings.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.currency.repository.CurrencyRepository;
import rs.raf.banka2_bek.savings.dto.SavingsRateDto;
import rs.raf.banka2_bek.savings.dto.UpsertSavingsRateDto;
import rs.raf.banka2_bek.savings.entity.SavingsInterestRate;
import rs.raf.banka2_bek.savings.mapper.SavingsMapper;
import rs.raf.banka2_bek.savings.repository.SavingsInterestRateRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SavingsInterestRateService {

    private final SavingsInterestRateRepository rateRepo;
    private final CurrencyRepository currencyRepo;
    private final SavingsMapper mapper;

    @Transactional(readOnly = true)
    public Optional<SavingsInterestRate> findActive(Long currencyId, Integer termMonths) {
        return rateRepo.findActive(currencyId, termMonths);
    }

    @Transactional(readOnly = true)
    public List<SavingsRateDto> listActive(String currencyCode) {
        List<SavingsInterestRate> rates = currencyCode != null && !currencyCode.isBlank()
                ? rateRepo.findActiveByCurrencyCode(currencyCode.toUpperCase())
                : rateRepo.findAllActive();
        return rates.stream().map(mapper::toRateDto).toList();
    }

    @Transactional(readOnly = true)
    public List<SavingsRateDto> listAll() {
        return rateRepo.findAll().stream().map(mapper::toRateDto).toList();
    }

    @Transactional
    public SavingsRateDto upsert(UpsertSavingsRateDto dto) {
        Currency currency = currencyRepo.findByCode(dto.getCurrencyCode().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Valuta ne postoji: " + dto.getCurrencyCode()));

        rateRepo.findActive(currency.getId(), dto.getTermMonths()).ifPresent(existing -> {
            existing.setActive(false);
            rateRepo.save(existing);
        });

        SavingsInterestRate newRate = SavingsInterestRate.builder()
                .currency(currency)
                .termMonths(dto.getTermMonths())
                .annualRate(dto.getAnnualRate())
                .active(true)
                .effectiveFrom(LocalDate.now())
                .build();
        return mapper.toRateDto(rateRepo.save(newRate));
    }
}
