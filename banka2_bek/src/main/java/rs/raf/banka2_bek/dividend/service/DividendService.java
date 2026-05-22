package rs.raf.banka2_bek.dividend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountCategory;
import rs.raf.banka2_bek.account.model.AccountStatus;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.auth.util.UserContext;
import rs.raf.banka2_bek.auth.util.UserResolver;
import rs.raf.banka2_bek.dividend.dto.DividendPayoutDto;
import rs.raf.banka2_bek.dividend.model.DividendPayout;
import rs.raf.banka2_bek.dividend.repository.DividendPayoutRepository;
import rs.raf.banka2_bek.investmentfund.model.ClientFundTransaction;
import rs.raf.banka2_bek.investmentfund.service.FundDividendService;
import rs.raf.banka2_bek.order.service.CurrencyConversionService;
import rs.raf.banka2_bek.portfolio.model.Portfolio;
import rs.raf.banka2_bek.portfolio.repository.PortfolioRepository;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.repository.ListingRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
// ============================================================
// TODO [B9 - Isplata dividendi na akcije | Nosilac: Djordje Zlatanovic]
//
// Servis koji sadrzi svu poslovnu logiku za kvartalnu isplatu dividendi.
//
// ZAVISNOSTI ZA INJEKTOVANJE (@RequiredArgsConstructor):
//   - DividendPayoutRepository dividendPayoutRepository
//   - PortfolioRepository portfolioRepository          (iz paketa rs.raf.banka2_bek.portfolio)
//   - AccountRepository accountRepository              (iz paketa rs.raf.banka2_bek.account)
//   - UserResolver userResolver                        (iz paketa rs.raf.banka2_bek.auth.util)
//   - CurrencyConversionService currencyConversionService (za fallback konverziju u RSD)
//   - @Value("${bank.registration-number}") String bankRegistrationNumber
//
// IMPLEMENTIRATI (metode koje klasa treba da ima):
//
//   public void processQuarterlyDividends(LocalDate paymentDate)
//       — Glavna metoda koju poziva DividendScheduler.
//         Algoritam:
//         1. Ucitaj sve Portfolio zapise gde quantity > 0 i listingType == STOCK.
//         2. Grupisaj po (ownerId, ownerType, stockListingId).
//         3. Za svaku grupu:
//            a. Idempotentnost: pozovi dividendPayoutRepository.findByStockListingIdAndPaymentDate
//               — ako zapis vec postoji, preskoci (restart-safe scheduler).
//            b. Ucitaj tekucu cenu iz listing.price (Listing entitet, paket `stock` / `berza`).
//               Listing.dividendYield je godisnji prinos (npr. 0.02 = 2%).
//               kvartalniPrinos = listing.getDividendYield() / 4
//            c. grossAmount = quantity * price * kvartalniPrinos
//            d. Odredi da li je vlasnik EMPLOYEE (aktuar koji drzi u ime banke):
//               ownerType.equals("EMPLOYEE") => taxExempt = true, tax = 0
//               inace => tax = grossAmount * 0.15 (15% porez na kapitalnu dobit)
//            e. netAmount = grossAmount - tax
//            f. Odredi ciljni racun (u valuti listinga):
//               i.  Pokusaj da nadjes account koji ima istu valutu kao listing i pripada vlasniku
//                   (za CLIENT: account.getClient().getId() == ownerId;
//                    za EMPLOYEE: bankinin BANK_TRADING racun za tu valutu)
//               ii. Fallback 1: default racun vlasnika u toj valuti
//               iii.Fallback 2: konvertuj u RSD i knjizi na default RSD racun (koristeci CurrencyConversionService)
//            g. Knjizi na racun: account.balance += netAmount, account.availableBalance += netAmount
//               (koristeci @Transactional per-payout unutar ovog metoda — ne jedan single-Tx za sve!)
//            h. Sacuvaj DividendPayout entitet.
//         4. Logovati svaku isplacenu i svaku preskocenu isplatu.
//
//   @Transactional
//   public DividendPayout payDividendForOwner(Portfolio portfolio, LocalDate paymentDate)
//       — Transakciona metoda za jednu isplatu (poziva je processQuarterlyDividends per-portfolio).
//         Odvajanjem od processQuarterlyDividends osiguravamo da @Transactional prode kroz
//         Spring AOP proxy (intra-class pozivi bi bili ignorisani — pratiti SavingsScheduler/
//         SavingsDepositProcessor sablon).
//
//   @Transactional(readOnly = true)
//   public List<DividendPayoutDto> getMyDividendHistory()
//       — Vraca istoriju dividendi za ulogovanog klijenta ili zaposlenog.
//         Koristiti userResolver.resolveCurrent() za ownerId i ownerType.
//         Mapirati DividendPayout -> DividendPayoutDto.
//
//   @Transactional(readOnly = true)
//   public List<DividendPayoutDto> getDividendHistoryByPosition(Long portfolioId)
//       — Vraca istoriju dividendi za konkretnu Portfolio poziciju.
//         Proveriti da li ulogovani korisnik poseduje tu Portfolio poziciju
//         (AccessDeniedException ako ne).
//         Koristiti dividendPayoutRepository.findByOwnerIdAndOwnerTypeAndStockListingId.
//
// Konvencija: pratiti paket `savings` (SavingsDepositService + SavingsDepositProcessor) kao sablon.
// Spec: Zadaci_Backend.pdf, zadatak B9.
// ============================================================

@Service
@RequiredArgsConstructor
@Slf4j
public class DividendService {
    private final DividendPayoutRepository dividendPayoutRepository;
    private final PortfolioRepository portfolioRepository;
    private final AccountRepository accountRepository;
    private final ListingRepository listingRepository;
    private final UserResolver userResolver;
    private final CurrencyConversionService currencyConversionService;
    private final FundDividendService fundDividendService;

    @Value("${bank.registration-number:22200022}")
    private String bankRegistrationNumber;

    public void processQuarterlyDividends(LocalDate paymentDate) {
        log.info("Započinjem proces isplate kvartalnih dividendi za datum: {}", paymentDate);

        // 1. Učitaj sve Portfolio zapise gde je kolicina > 0 i tip je STOCK
        List<Portfolio> eligiblePortfolios = portfolioRepository.findAll().stream()
                .filter(p -> p.getQuantity() != null && p.getQuantity() > 0)
                .filter(p -> "STOCK".equalsIgnoreCase(p.getListingType()))
                .collect(Collectors.toList());

        // 2. Grupiši po jedinstvenom ključu (ownerId, ownerType, stockListingId)
        Map<String, List<Portfolio>> grouped = eligiblePortfolios.stream()
                .collect(Collectors.groupingBy(p -> p.getUserId() + "_" + p.getUserRole() + "_" + p.getListingId()));

        int processedCount = 0;
        for (Map.Entry<String, List<Portfolio>> entry : grouped.entrySet()) {
            try {
                // Pošto je kombinacija jedinstvena, uzimamo prvi (i jedini) portfolio iz grupe
                Portfolio portfolio = entry.getValue().get(0);

                // Provera idempotentnosti
                List<DividendPayout> existing = dividendPayoutRepository
                        .findByStockListingIdAndPaymentDate(portfolio.getListingId(), paymentDate);

                boolean alreadyPaid = existing.stream().anyMatch(ep ->
                        ep.getOwnerId().equals(portfolio.getUserId()) && ep.getOwnerType().equalsIgnoreCase(portfolio.getUserRole()));

                if (alreadyPaid) {
                    log.info("Dividenda za Listing ID: {} i Korisnika: {} je već isplaćena. Preskačem.",
                            portfolio.getListingId(), portfolio.getUserId());
                    continue;
                }

                // Pozivamo odvojenu transakcionu metodu za svaku isplatu pojedinačno
                payDividendForOwnerProxy(portfolio, paymentDate);
                processedCount++;

            } catch (Exception e) {
                log.error("Greška prilikom obrade dividende za ključ {}: {}", entry.getKey(), e.getMessage(), e);
            }
        }
        log.info("Završen proces isplate dividendi. Ukupno uspešno procesirano: {}", processedCount);
    }

    @Transactional
    public void payDividendForOwnerProxy(Portfolio portfolio, LocalDate paymentDate) {
        // Učitavamo listing da dobijemo trenutnu cenu i yield
        Listing listing = listingRepository.findById(portfolio.getListingId())
                .orElseThrow(() -> new RuntimeException("Listing nije pronađen za ID: " + portfolio.getListingId()));

        BigDecimal dividendYield = listing.getDividendYield() != null ? listing.getDividendYield() : BigDecimal.ZERO;
        BigDecimal quarterlyYieldRate = dividendYield.divide(BigDecimal.valueOf(4), 6, RoundingMode.HALF_UP);
        BigDecimal price = listing.getPrice() != null ? listing.getPrice() : BigDecimal.ZERO;

        // Računanje bruto iznosa
        BigDecimal quantity = BigDecimal.valueOf(portfolio.getQuantity());
        BigDecimal grossAmount = quantity.multiply(price).multiply(quarterlyYieldRate).setScale(4, RoundingMode.HALF_UP);

        if (grossAmount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Bruto iznos dividende za ticker {} je 0. Preskačem kreiranje zapisa.", portfolio.getListingTicker());
            return;
        }

        // Određivanje poreza (EMPLOYEE / Aktuar banke je izuzet od poreza)
        boolean isTaxExempt = "EMPLOYEE".equalsIgnoreCase(portfolio.getUserRole());
        BigDecimal tax = isTaxExempt ? BigDecimal.ZERO : grossAmount.multiply(new BigDecimal("0.15")).setScale(4, RoundingMode.HALF_UP);
        BigDecimal netAmount = grossAmount.subtract(tax).setScale(4, RoundingMode.HALF_UP);

        // Određivanje valute. Za fondove se B11 grana posebno obrađuje: sredstva se knjiže na račun fonda
        // i dalje se obrađuju kroz FundDividendService (reinvestiranje ili raspodela klijentima).
        String currencyCode = listing.getBaseCurrency() != null ? listing.getBaseCurrency() : "USD"; // Fallback na USD ako nema valute

        if ("FUND".equalsIgnoreCase(portfolio.getUserRole())) {
            BigDecimal amountForFund = grossAmount;
            if (!"RSD".equalsIgnoreCase(currencyCode)) {
                amountForFund = currencyConversionService.convert(grossAmount, currencyCode, "RSD");
            }

            ClientFundTransaction fundTx = fundDividendService.creditDividendToFund(
                    portfolio.getUserId(),
                    portfolio.getListingId(),
                    amountForFund);

            DividendPayout payout = DividendPayout.builder()
                    .ownerId(portfolio.getUserId())
                    .ownerType(portfolio.getUserRole().toUpperCase())
                    .stockListingId(portfolio.getListingId())
                    .stockTicker(portfolio.getListingTicker())
                    .quantity(portfolio.getQuantity())
                    .priceOnDate(price)
                    .dividendYieldRate(quarterlyYieldRate)
                    .grossAmount(amountForFund)
                    .tax(BigDecimal.ZERO)
                    .netAmount(amountForFund)
                    .creditedAccountId(fundTx.getSourceAccountId())
                    .currencyCode("RSD")
                    .paymentDate(paymentDate)
                    .taxExempt(true)
                    .build();

            dividendPayoutRepository.save(payout);
            log.info("B11: dividenda za ticker {} uplaćena fondu #{}, iznos={} RSD",
                    portfolio.getListingTicker(), portfolio.getUserId(), amountForFund);
            return;
        }

        Account targetAccount = resolveTargetAccount(portfolio, currencyCode);

        // Ako je primenjen Fallback 2 (konverzija u RSD), moramo prebaciti neto iznos u RSD
        BigDecimal finalAmountToCredit = netAmount;
        if ("RSD".equals(targetAccount.getCurrency().getCode()) && !"RSD".equals(currencyCode)) {
            finalAmountToCredit = currencyConversionService.convert(netAmount, currencyCode, "RSD");
        }

        // Knjiženje sredstava na račun
        targetAccount.setBalance(targetAccount.getBalance().add(finalAmountToCredit));
        targetAccount.setAvailableBalance(targetAccount.getAvailableBalance().add(finalAmountToCredit));
        accountRepository.save(targetAccount);

        // Čuvanje zapisa o isplati
        DividendPayout payout = DividendPayout.builder()
                .ownerId(portfolio.getUserId())
                .ownerType(portfolio.getUserRole().toUpperCase())
                .stockListingId(portfolio.getListingId())
                .stockTicker(portfolio.getListingTicker())
                .quantity(portfolio.getQuantity())
                .priceOnDate(price)
                .dividendYieldRate(quarterlyYieldRate)
                .grossAmount(grossAmount)
                .tax(tax)
                .netAmount(netAmount)
                .creditedAccountId(targetAccount.getId())
                .currencyCode(targetAccount.getCurrency().getCode())
                .paymentDate(paymentDate)
                .taxExempt(isTaxExempt)
                .build();

        dividendPayoutRepository.save(payout);
        log.info("Uspešno isplaćena dividenda za ticker: {}, Korisnik ID: {}, Neto iznos: {} {}",
                portfolio.getListingTicker(), portfolio.getUserId(), finalAmountToCredit, targetAccount.getCurrency().getCode());
    }

    private Account resolveTargetAccount(Portfolio portfolio, String currencyCode) {
        if ("EMPLOYEE".equalsIgnoreCase(portfolio.getUserRole())) {
            // Za zaposlene (banku) - tražimo BANK_TRADING račun u toj valuti
            return accountRepository.findFirstByAccountCategoryAndCurrency_Code(AccountCategory.BANK_TRADING, currencyCode)
                    .orElseGet(() -> accountRepository.findFirstByAccountCategoryAndCurrency_Code(AccountCategory.BANK_TRADING, "RSD")
                            .orElseThrow(() -> new RuntimeException("Nije pronađen ni podrazumevani BANK_TRADING RSD račun za banku!")));
        } else {
            // Za klijente
            List<Account> clientAccounts = accountRepository.findByClientId(portfolio.getUserId());

            // Korak 1: Nađi račun u istoj valuti koji je aktivan
            Optional<Account> primaryMatch = clientAccounts.stream()
                    .filter(a -> a.getCurrency().getCode().equalsIgnoreCase(currencyCode))
                    .filter(a -> a.getStatus() == AccountStatus.ACTIVE)
                    .findFirst();
            if (primaryMatch.isPresent()) return primaryMatch.get();

            // Fallback 1: Prvi aktivni račun u istoj valuti (već pokriveno korakom 1, ali ako ima više uzmi bilo koji aktivan)
            // Fallback 2: Konverzija u domacu valutu (RSD) i knjiženje na aktivni RSD račun
            return clientAccounts.stream()
                    .filter(a -> "RSD".equalsIgnoreCase(a.getCurrency().getCode()))
                    .filter(a -> a.getStatus() == AccountStatus.ACTIVE)
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Klijent nema adekvatan račun u valuti " + currencyCode + " niti podrazumevani aktivni RSD račun!"));
        }
    }

    @Transactional(readOnly = true)
    public List<DividendPayoutDto> getMyDividendHistory() {
        UserContext context = userResolver.resolveCurrent();
        List<DividendPayout> payouts = dividendPayoutRepository
                .findByOwnerIdAndOwnerTypeOrderByPaymentDateDesc(context.userId(), context.userRole());
        return payouts.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DividendPayoutDto> getDividendHistoryByPosition(Long portfolioId) {
        UserContext context = userResolver.resolveCurrent();
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new RuntimeException("Portfolio pozicija nije pronađena."));

        // Sigurnosna provera vlasništva nad pozicijom
        if (!portfolio.getUserId().equals(context.userId()) || !portfolio.getUserRole().equalsIgnoreCase(context.userRole())) {
            throw new AccessDeniedException("Nemate pravo pristupa istoriji dividendi za ovaj portfolio.");
        }

        List<DividendPayout> payouts = dividendPayoutRepository
                .findByOwnerIdAndOwnerTypeAndStockListingId(context.userId(), context.userRole(), portfolio.getListingId());
        return payouts.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<DividendPayoutDto> getAdminDividendHistory(LocalDate from, LocalDate to, Pageable pageable) {
        if (from != null && to != null) {
            List<DividendPayout> list = dividendPayoutRepository.findByPaymentDateBetween(from, to);
            // Ručna paginacija za opseg pošto repozitorijum vraća listu
            return new org.springframework.data.domain.PageImpl<>(
                    list.stream().map(this::mapToDto).collect(Collectors.toList()), pageable, list.size());
        }
        return dividendPayoutRepository.findAllByOrderByPaymentDateDesc(pageable).map(this::mapToDto);
    }

    private DividendPayoutDto mapToDto(DividendPayout p) {
        return DividendPayoutDto.builder()
                .id(p.getId())
                .ownerId(p.getOwnerId())
                .ownerType(p.getOwnerType())
                .stockListingId(p.getStockListingId())
                .stockTicker(p.getStockTicker())
                .quantity(p.getQuantity())
                .priceOnDate(p.getPriceOnDate())
                .dividendYieldRate(p.getDividendYieldRate())
                .grossAmount(p.getGrossAmount())
                .tax(p.getTax())
                .netAmount(p.getNetAmount())
                .creditedAccountId(p.getCreditedAccountId())
                .currencyCode(p.getCurrencyCode())
                .paymentDate(p.getPaymentDate())
                .taxExempt(p.getTaxExempt())
                .createdAt(p.getCreatedAt())
                .build();
    }
}