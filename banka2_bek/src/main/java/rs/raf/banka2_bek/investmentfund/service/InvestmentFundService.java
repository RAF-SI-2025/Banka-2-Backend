package rs.raf.banka2_bek.investmentfund.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.account.model.*;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.account.util.AccountNumberUtils;
import rs.raf.banka2_bek.actuary.model.ActuaryType;
import rs.raf.banka2_bek.actuary.repository.ActuaryInfoRepository;
import rs.raf.banka2_bek.auth.util.UserRole;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.company.model.Company;
import rs.raf.banka2_bek.company.repository.CompanyRepository;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.currency.repository.CurrencyRepository;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;
import jakarta.persistence.EntityNotFoundException;
import rs.raf.banka2_bek.investmentfund.dto.InvestmentFundDtos.*;
import rs.raf.banka2_bek.investmentfund.mapper.InvestmentFundMapper;
import rs.raf.banka2_bek.investmentfund.model.*;
import rs.raf.banka2_bek.investmentfund.repository.*;
import rs.raf.banka2_bek.order.exception.InsufficientFundsException;
import rs.raf.banka2_bek.order.service.CurrencyConversionService;
import rs.raf.banka2_bek.portfolio.model.Portfolio;
import rs.raf.banka2_bek.portfolio.repository.PortfolioRepository;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.repository.ListingRepository;
import rs.raf.banka2_bek.stock.util.ListingCurrencyResolver;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvestmentFundService {

    private static final String RSD = "RSD";
    private static final BigDecimal FX_FEE_RATE = new BigDecimal("0.01");
    private static final int MONEY_SCALE = 4;

    private final FundValueSnapshotRepository fundValueSnapshotRepository;
    private final InvestmentFundRepository investmentFundRepository;
    private final ClientFundPositionRepository clientFundPositionRepository;
    private final ClientFundTransactionRepository clientFundTransactionRepository;
    private final ClientRepository clientRepository;
    private final AccountRepository accountRepository;
    private final CurrencyRepository currencyRepository;
    private final CompanyRepository companyRepository;
    private final EmployeeRepository employeeRepository;
    private final ActuaryInfoRepository actuaryInfoRepository;
    private final PortfolioRepository portfolioRepository;
    private final ListingRepository listingRepository;
    private final FundValueCalculator fundValueCalculator;
    private final FundLiquidationService fundLiquidationService;
    private final CurrencyConversionService currencyConversionService;
    private final rs.raf.banka2_bek.investmentfund.scheduler.FundValueSnapshotScheduler fundValueSnapshotScheduler;

    /**
     * T12 — fallback strategija za "Banka kao klijent fonda" (Celina 4 (Nova) §4406-4435).
     *
     * Email pod kojim seed.sql kreira "Banka 2 d.o.o." klijenta. Po default-u
     * "banka2.doo@banka.rs" (vidi application.properties + seed.sql).
     * InvestmentFundService.listBankPositions koristi ga da resolvuje
     * `clients.id` u runtime (jer client_id je auto-generisan i ne mozemo ga
     * forsirati na konstantu).
     */
    @Value("${bank.owner-client-email:banka2.doo@banka.rs}")
    private String bankOwnerClientEmail;

    @Transactional
    public InvestmentFundDetailDto createFund(CreateFundDto dto, Long supervisorId) {
        if (investmentFundRepository.findByName(dto.getName()).isPresent()) {
            throw new IllegalArgumentException("Fund with name '" + dto.getName() + "' already exists.");
        }

        actuaryInfoRepository.findByEmployeeId(supervisorId)
                .filter(a -> a.getActuaryType() == ActuaryType.SUPERVISOR)
                .orElseThrow(() -> new IllegalStateException("Only supervisors can create funds."));

        Employee supervisor = employeeRepository.findById(supervisorId)
                .orElseThrow(() -> new IllegalArgumentException("Employee #" + supervisorId + " not found."));

        Currency rsd = currencyRepository.findByCode("RSD")
                .orElseThrow(() -> new IllegalStateException("RSD currency not found."));

        // Fond accountu pripada NASOJ banci (is_bank=true), ne drzavi
        // (is_state=true) — Celina 2 §73-78 razdvaja banku od drzave.
        Company bankCompany = companyRepository.findByIsBankTrue()
                .or(companyRepository::findByIsStateTrue) // backward-compat dok seed/testovi ne postave is_bank
                .orElseThrow(() -> new IllegalStateException("Bank company not found."));

        String accountNumber;
        do {
            accountNumber = AccountNumberUtils.generate(AccountType.BUSINESS, null, true);
        } while (accountRepository.existsByAccountNumber(accountNumber));

        Account fundAccount = Account.builder()
                .accountNumber(accountNumber)
                .accountType(AccountType.BUSINESS)
                .accountSubtype(null)
                .currency(rsd)
                .company(bankCompany)
                .employee(supervisor)
                .balance(BigDecimal.ZERO)
                .availableBalance(BigDecimal.ZERO)
                .reservedAmount(BigDecimal.ZERO)
                .accountCategory(AccountCategory.FUND)
                .dailyLimit(BigDecimal.ZERO)
                .monthlyLimit(BigDecimal.ZERO)
                .dailySpending(BigDecimal.ZERO)
                .monthlySpending(BigDecimal.ZERO)
                .maintenanceFee(BigDecimal.ZERO)
                .status(AccountStatus.ACTIVE)
                .name("Fund: " + dto.getName())
                .createdAt(LocalDateTime.now())
                .build();
        fundAccount = accountRepository.save(fundAccount);

        InvestmentFund fund = new InvestmentFund();
        fund.setName(dto.getName());
        fund.setDescription(dto.getDescription());
        fund.setMinimumContribution(dto.getMinimumContribution());
        fund.setManagerEmployeeId(supervisorId);
        fund.setAccountId(fundAccount.getId());
        fund.setCreatedAt(LocalDateTime.now());
        fund.setInceptionDate(LocalDate.now());
        fund.setActive(true);
        fund = investmentFundRepository.save(fund);

        FundValueSnapshot initialSnapshot = new FundValueSnapshot();
        initialSnapshot.setFundId(fund.getId());
        initialSnapshot.setSnapshotDate(LocalDate.now());
        initialSnapshot.setFundValue(BigDecimal.ZERO);
        initialSnapshot.setLiquidAmount(BigDecimal.ZERO);
        initialSnapshot.setInvestedTotal(BigDecimal.ZERO);
        initialSnapshot.setProfit(BigDecimal.ZERO);
        fundValueSnapshotRepository.save(initialSnapshot);

        log.info("Fund '{}' created, account #{}", fund.getName(), fundAccount.getId());
        return InvestmentFundMapper.toDetailDto(fund, fundAccount, BigDecimal.ZERO, BigDecimal.ZERO,
                Collections.emptyList(), Collections.emptyList(),
                supervisor.getFirstName() + " " + supervisor.getLastName());
    }

    public List<InvestmentFundSummaryDto> listDiscovery(String searchQuery, String sortField, String sortDirection) {
        return listDiscovery(searchQuery, sortField, sortDirection,
                null, null, null, null, null, null);
    }

    public List<InvestmentFundSummaryDto> listDiscovery(String searchQuery, String sortField, String sortDirection,
                                                         BigDecimal minContribution, BigDecimal maxContribution,
                                                         BigDecimal minFundValue, BigDecimal maxFundValue,
                                                         BigDecimal minProfit, BigDecimal maxProfit) {
        List<InvestmentFund> funds = investmentFundRepository.findByActiveTrueOrderByNameAsc();

        Stream<InvestmentFund> stream = funds.stream();
        if (searchQuery != null && !searchQuery.isBlank()) {
            String q = searchQuery.toLowerCase();
            stream = stream.filter(f ->
                    (f.getName() != null && f.getName().toLowerCase().contains(q)) ||
                            (f.getDescription() != null && f.getDescription().toLowerCase().contains(q)));
        }
        if (minContribution != null) {
            stream = stream.filter(f -> f.getMinimumContribution() != null
                    && f.getMinimumContribution().compareTo(minContribution) >= 0);
        }
        if (maxContribution != null) {
            stream = stream.filter(f -> f.getMinimumContribution() != null
                    && f.getMinimumContribution().compareTo(maxContribution) <= 0);
        }

        List<InvestmentFundSummaryDto> result = stream.map(f -> {
            BigDecimal fundValue = safeCompute(() -> fundValueCalculator.computeFundValue(f), BigDecimal.ZERO);
            BigDecimal profit = safeCompute(() -> fundValueCalculator.computeProfit(f), BigDecimal.ZERO);
            String managerName = employeeRepository.findById(f.getManagerEmployeeId())
                    .map(e -> e.getFirstName() + " " + e.getLastName())
                    .orElse("N/A");
            return InvestmentFundMapper.toSummaryDto(f, fundValue, profit, managerName);
        }).collect(Collectors.toCollection(ArrayList::new));

        // Numericki filteri po izvedenim poljima primenjeni nakon mapiranja (DTO ima vrednosti).
        if (minFundValue != null) {
            result.removeIf(d -> d.getFundValue() == null || d.getFundValue().compareTo(minFundValue) < 0);
        }
        if (maxFundValue != null) {
            result.removeIf(d -> d.getFundValue() == null || d.getFundValue().compareTo(maxFundValue) > 0);
        }
        if (minProfit != null) {
            result.removeIf(d -> d.getProfit() == null || d.getProfit().compareTo(minProfit) < 0);
        }
        if (maxProfit != null) {
            result.removeIf(d -> d.getProfit() == null || d.getProfit().compareTo(maxProfit) > 0);
        }

        Comparator<InvestmentFundSummaryDto> comparator = buildComparator(sortField);
        if ("DESC".equalsIgnoreCase(sortDirection)) {
            comparator = comparator.reversed();
        }
        result.sort(comparator);
        return result;
    }

    public InvestmentFundDetailDto getFundDetails(Long fundId) {
        InvestmentFund fund = investmentFundRepository.findById(fundId)
                .orElseThrow(() -> new EntityNotFoundException("Fund #" + fundId + " not found."));

        Account account = accountRepository.findById(fund.getAccountId())
                .orElseThrow(() -> new EntityNotFoundException("Fund account not found."));

        BigDecimal fundValue = safeCompute(() -> fundValueCalculator.computeFundValue(fund), BigDecimal.ZERO);
        BigDecimal profit = safeCompute(() -> fundValueCalculator.computeProfit(fund), BigDecimal.ZERO);

        List<Portfolio> portfolios = portfolioRepository.findByUserIdAndUserRole(fund.getId(), UserRole.FUND);
        List<FundHoldingDto> holdings = portfolios.stream()
                .filter(p -> p.getQuantity() != null && p.getQuantity() > 0)
                .map(p -> {
                    Listing listing = listingRepository.findById(p.getListingId()).orElse(null);
                    BigDecimal currentPrice = listing != null ? listing.getPrice() : BigDecimal.ZERO;
                    BigDecimal change = listing != null ? listing.getPriceChange() : BigDecimal.ZERO;
                    Long volume = listing != null ? listing.getVolume() : 0L;
                    return new FundHoldingDto(
                            p.getListingId(),
                            p.getListingTicker(),
                            p.getListingName(),
                            p.getQuantity(),
                            currentPrice,
                            change,
                            volume,
                            p.getAverageBuyPrice(),
                            p.getLastModified() != null ? p.getLastModified().toLocalDate() : null);
                })
                .toList();

        LocalDate from = LocalDate.now().minusDays(30);
        LocalDate to = LocalDate.now();
        List<FundPerformancePointDto> performance = fundValueSnapshotRepository
                .findByFundIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(fund.getId(), from, to)
                .stream()
                .map(s -> new FundPerformancePointDto(s.getSnapshotDate(), s.getFundValue(), s.getProfit()))
                .toList();

        String managerName = employeeRepository.findById(fund.getManagerEmployeeId())
                .map(e -> e.getFirstName() + " " + e.getLastName())
                .orElse("N/A");

        return InvestmentFundMapper.toDetailDto(fund, account, fundValue, profit, holdings, performance, managerName);
    }

    /**
     * P11 — Spec Celina 4 (Nova) §3592-3629: "Performanse fonda: tabela ili
     * grafikon (mesecni, kvartalni ili godisnji prikaz)".
     *
     * Implementacija (kad bude):
     *  - FundValueSnapshot tabela vec snima dnevno (FundValueSnapshotScheduler 23:45)
     *  - Ovde agregiramo po granularity parametru:
     *      - DAY    -> sve tacke izmedju [from, to]
     *      - WEEK   -> grupisi po ISO sedmici, uzmi poslednju vrednost
     *      - MONTH  -> grupisi po YYYY-MM, uzmi poslednju vrednost
     *      - QUARTER-> grupisi po (YYYY, ceil(month/3)), poslednja vrednost
     *      - YEAR   -> grupisi po YYYY, poslednja vrednost
     *  - Vrati listu FundPerformancePointDto sortiranu po datumu ASC.
     *
     * FE FundDetailsPage ima toggle Day/Week/Month/Quarter/Year — ovde
     * dodati granularity parametar kad bude.
     */
    public List<FundPerformancePointDto> getPerformance(Long fundId, LocalDate from, LocalDate to, Granularity granularity) {
        List<FundValueSnapshot> snapshots = fundValueSnapshotRepository.findByFundIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(fundId, from, to);
        if (snapshots.isEmpty()) return List.of();
        List<FundPerformancePointDto> result = new ArrayList<>();
        switch (granularity) {
            case DAY -> {
                for (FundValueSnapshot snap : snapshots) {
                    result.add(new FundPerformancePointDto(snap.getSnapshotDate(), snap.getFundValue(), snap.getProfit()));
                }
            }
            case WEEK -> {
                Map<String, FundValueSnapshot> lastOfWeek = new LinkedHashMap<>();
                for (FundValueSnapshot snap : snapshots) {
                    String key = snap.getSnapshotDate().getYear() + "-W" + snap.getSnapshotDate().get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);
                    lastOfWeek.put(key, snap);
                }
                for (FundValueSnapshot snap : lastOfWeek.values()) {
                    result.add(new FundPerformancePointDto(snap.getSnapshotDate(), snap.getFundValue(), snap.getProfit()));
                }
            }
            case MONTH -> {
                Map<String, FundValueSnapshot> lastOfMonth = new LinkedHashMap<>();
                for (FundValueSnapshot snap : snapshots) {
                    String key = snap.getSnapshotDate().getYear() + "-" + snap.getSnapshotDate().getMonthValue();
                    lastOfMonth.put(key, snap);
                }
                for (FundValueSnapshot snap : lastOfMonth.values()) {
                    result.add(new FundPerformancePointDto(snap.getSnapshotDate(), snap.getFundValue(), snap.getProfit()));
                }
            }
            case QUARTER -> {
                Map<String, FundValueSnapshot> lastOfQuarter = new LinkedHashMap<>();
                for (FundValueSnapshot snap : snapshots) {
                    int quarter = (snap.getSnapshotDate().getMonthValue() - 1) / 3 + 1;
                    String key = snap.getSnapshotDate().getYear() + "-Q" + quarter;
                    lastOfQuarter.put(key, snap);
                }
                for (FundValueSnapshot snap : lastOfQuarter.values()) {
                    result.add(new FundPerformancePointDto(snap.getSnapshotDate(), snap.getFundValue(), snap.getProfit()));
                }
            }
            case YEAR -> {
                Map<Integer, FundValueSnapshot> lastOfYear = new LinkedHashMap<>();
                for (FundValueSnapshot snap : snapshots) {
                    int year = snap.getSnapshotDate().getYear();
                    lastOfYear.put(year, snap);
                }
                for (FundValueSnapshot snap : lastOfYear.values()) {
                    result.add(new FundPerformancePointDto(snap.getSnapshotDate(), snap.getFundValue(), snap.getProfit()));
                }
            }
        }
        result.sort(java.util.Comparator.comparing(FundPerformancePointDto::getDate));
        return result;
    }

    /**
     * T8 — Celina 4 (Nova), Investicioni fondovi / ClientFundInvestment.
     *
     * Uplata uvek zavrsava kao RSD priliv na racun fonda. Klijent moze da
     * uplati sa svog racuna, a supervizor uplacuje u ime banke sa bankinog
     * racuna. Banka se u ClientFundPosition modelu vodi kao poseban klijent
     * seed-ovan preko bank.owner-client-email, sto je T12 konvencija.
     */
    @Transactional
    public ClientFundPositionDto invest(Long fundId, InvestFundDto dto, Long userId, String userRole) {
        if (dto == null || dto.getAmount() == null || dto.getAmount().signum() <= 0) {
            throw new IllegalArgumentException("Iznos uplate mora biti pozitivan.");
        }

        InvestmentFund fund = findActiveFund(fundId);
        Account sourceAccount = accountRepository.findForUpdateById(dto.getSourceAccountId())
                .orElseThrow(() -> new EntityNotFoundException("Izvorni racun ne postoji: " + dto.getSourceAccountId()));
        Account fundAccount = accountRepository.findForUpdateById(fund.getAccountId())
                .orElseThrow(() -> new EntityNotFoundException("Racun fonda ne postoji: " + fund.getAccountId()));

        if (Objects.equals(sourceAccount.getId(), fundAccount.getId())) {
            throw new IllegalArgumentException("Izvorni racun ne moze biti isti kao racun fonda.");
        }

        String actorRole = normalizeUserRole(userRole);
        ensureAccountCanBeUsed(sourceAccount, userId, actorRole, "uplate");
        InvestorIdentity investor = resolveInvestorIdentity(userId, actorRole);

        InvestmentAmounts amounts = calculateInvestmentAmounts(dto, sourceAccount, actorRole);
        if (amounts.amountRsd().compareTo(nullToZero(fund.getMinimumContribution())) < 0) {
            throw new IllegalArgumentException("Iznos uplate mora biti najmanje "
                    + fund.getMinimumContribution() + " RSD.");
        }

        BigDecimal totalDebit = amounts.debitAmount().add(amounts.fxCommission()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        if (nullToZero(sourceAccount.getAvailableBalance()).compareTo(totalDebit) < 0) {
            throw new InsufficientFundsException("Nedovoljno sredstava na racunu "
                    + sourceAccount.getAccountNumber() + ". Potrebno: " + totalDebit + " "
                    + sourceAccount.getCurrency().getCode());
        }

        ClientFundTransaction tx = new ClientFundTransaction();
        tx.setFundId(fund.getId());
        tx.setUserId(investor.userId());
        tx.setUserRole(investor.userRole());
        tx.setAmountRsd(amounts.amountRsd());
        tx.setSourceAccountId(sourceAccount.getId());
        tx.setInflow(true);
        tx.setStatus(ClientFundTransactionStatus.PENDING);
        tx.setCreatedAt(LocalDateTime.now());
        tx = clientFundTransactionRepository.save(tx);

        debit(sourceAccount, totalDebit);
        credit(fundAccount, amounts.amountRsd());
        creditBankFxCommission(sourceAccount.getCurrency().getCode(), amounts.fxCommission(), sourceAccount.getId());

        tx.setStatus(ClientFundTransactionStatus.COMPLETED);
        tx.setCompletedAt(LocalDateTime.now());
        clientFundTransactionRepository.save(tx);

        ClientFundPosition position = upsertPosition(fund.getId(), investor, amounts.amountRsd());
        log.info("T8 invest completed: fund={}, investor={}#{}, amountRsd={}, sourceAccount={}",
                fund.getId(), investor.userRole(), investor.userId(), amounts.amountRsd(), sourceAccount.getId());

        // Bag prijavljen 10.05.2026: FundDetailsPage performance graf prazan jer
        // fund_value_snapshots nema red za danas. Posle uspesne uplate, zovem
        // idempotentni helper koji garantuje 1 tacku grafa za novu vrednost.
        fundValueSnapshotScheduler.snapshotFundIfMissing(fund);

        BigDecimal fundValue = safeCompute(() -> fundValueCalculator.computeFundValue(fund), BigDecimal.ZERO);
        BigDecimal sumInvested = clientFundPositionRepository.findByFundId(fund.getId()).stream()
                .map(ClientFundPosition::getTotalInvested)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return toClientFundPositionDto(
                position,
                fund.getName(),
                resolveUserName(investor.userId(), investor.userRole()),
                fundValue,
                sumInvested);
    }

    /**
     * T8 — Celina 4 (Nova), ClientFundRedemption.
     *
     * Isplata se evidentira kao RSD odliv iz fonda. Ako fond ima dovoljno
     * raspolozivog cash-a, novac se isplacuje odmah. Ako nema, transakcija
     * ostaje PENDING i poziva se T9 FundLiquidationService da proda hartije
     * fonda i kasnije kroz onFillCompleted FIFO razresi pending isplate.
     */
    @Transactional
    public ClientFundTransactionDto withdraw(Long fundId, WithdrawFundDto dto, Long userId, String userRole) {
        if (dto == null) {
            throw new IllegalArgumentException("Podaci za isplatu su obavezni.");
        }

        InvestmentFund fund = findActiveFund(fundId);
        Account fundAccount = accountRepository.findForUpdateById(fund.getAccountId())
                .orElseThrow(() -> new EntityNotFoundException("Racun fonda ne postoji: " + fund.getAccountId()));
        Account destinationAccount = accountRepository.findForUpdateById(dto.getDestinationAccountId())
                .orElseThrow(() -> new EntityNotFoundException("Racun za isplatu ne postoji: " + dto.getDestinationAccountId()));

        if (Objects.equals(destinationAccount.getId(), fundAccount.getId())) {
            throw new IllegalArgumentException("Racun za isplatu ne moze biti isti kao racun fonda.");
        }

        String actorRole = normalizeUserRole(userRole);
        ensureAccountCanBeUsed(destinationAccount, userId, actorRole, "isplate");
        InvestorIdentity investor = resolveInvestorIdentity(userId, actorRole);

        ClientFundPosition position = clientFundPositionRepository
                .findByFundIdAndUserIdAndUserRole(fund.getId(), investor.userId(), investor.userRole())
                .orElseThrow(() -> new IllegalArgumentException("Nemate poziciju u fondu " + fund.getName() + "."));

        BigDecimal amountRsd = dto.getAmount() == null
                ? nullToZero(position.getTotalInvested())
                : dto.getAmount().setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        if (amountRsd.signum() <= 0) {
            throw new IllegalArgumentException("Iznos isplate mora biti pozitivan.");
        }
        if (nullToZero(position.getTotalInvested()).compareTo(amountRsd) < 0) {
            throw new IllegalArgumentException("Trazeni iznos je veci od pozicije u fondu.");
        }

        ClientFundTransaction tx = new ClientFundTransaction();
        tx.setFundId(fund.getId());
        tx.setUserId(investor.userId());
        tx.setUserRole(investor.userRole());
        tx.setAmountRsd(amountRsd);
        tx.setSourceAccountId(destinationAccount.getId());
        tx.setInflow(false);
        tx.setStatus(ClientFundTransactionStatus.PENDING);
        tx.setCreatedAt(LocalDateTime.now());
        tx = clientFundTransactionRepository.save(tx);

        decreasePosition(position, amountRsd);

        BigDecimal availableCash = nullToZero(fundAccount.getAvailableBalance());
        if (availableCash.compareTo(amountRsd) >= 0) {
            executePayout(tx, fundAccount, destinationAccount, actorRole);
            tx = clientFundTransactionRepository.save(tx);
            log.info("T8 withdraw completed immediately: fund={}, investor={}#{}, amountRsd={}",
                    fund.getId(), investor.userRole(), investor.userId(), amountRsd);
        } else {
            BigDecimal shortfall = amountRsd.subtract(availableCash).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            tx.setFailureReason("Nedovoljno likvidnih sredstava; pokrenuta automatska likvidacija hartija.");
            tx = clientFundTransactionRepository.save(tx);
            sendPushNotification(investor.userId(), "Isplata iz fonda " + fund.getName()
                    + " je primljena i bice zavrsena nakon automatske likvidacije hartija.");
            fundLiquidationService.liquidateFor(fund.getId(), shortfall);
            log.info("T8 withdraw pending: fund={}, investor={}#{}, amountRsd={}, shortfall={}",
                    fund.getId(), investor.userRole(), investor.userId(), amountRsd, shortfall);
        }

        // Bag 10.05.2026 — vidi #invest hook iznad (snapshot za danas).
        fundValueSnapshotScheduler.snapshotFundIfMissing(fund);

        return toClientFundTransactionDto(tx, fund.getName());
    }

    public List<ClientFundTransactionDto> listTransactions(Long fundId, Long requesterId, String requesterRole) {
        InvestmentFund fund = investmentFundRepository.findById(fundId)
                .orElseThrow(() -> new EntityNotFoundException("Fund #" + fundId + " not found."));
        String role = normalizeUserRole(requesterRole);
        List<ClientFundTransaction> transactions = clientFundTransactionRepository.findByFundIdOrderByCreatedAtDesc(fundId);

        if (isEmployeeActor(role)) {
            ensureSupervisor(requesterId);
            return transactions.stream()
                    .map(tx -> toClientFundTransactionDto(tx, fund.getName()))
                    .toList();
        }

        if (UserRole.isClient(role)) {
            return transactions.stream()
                    .filter(tx -> Objects.equals(tx.getUserId(), requesterId) && UserRole.CLIENT.equals(tx.getUserRole()))
                    .map(tx -> toClientFundTransactionDto(tx, fund.getName()))
                    .toList();
        }

        throw new AccessDeniedException("Nemate pravo pregleda transakcija fonda.");
    }

    /**
     * T12 — Spec Celina 4 (Nova) "Moj portfolio -> Moji fondovi page".
     *
     * Vraca sve pozicije (ClientFundPosition) za autentifikovanog korisnika
     * sa popunjenim izvedenim poljima (currentValue, percentOfFund, profit, userName).
     *
     * Batch-optimizovano:
     *  - fondovi se ucitavaju jednim findAllById pozivom
     *  - po fundId-ju jednom racuna fundValue + sumTotalInvested (izbegne N+1
     *    u toClientFundPositionDto kada korisnik ima pozicije u vise fondova)
     *  - userName se resolvuje jednom (svi pozicije ovog korisnika dele isti userId/userRole).
     */
    public List<ClientFundPositionDto> listMyPositions(Long userId, String userRole) {
        if (userId == null || userRole == null || userRole.isBlank()) {
            return List.of();
        }
        List<ClientFundPosition> positions =
                clientFundPositionRepository.findByUserIdAndUserRole(userId, userRole);
        if (positions.isEmpty()) {
            return List.of();
        }
        List<Long> fundIds = positions.stream().map(ClientFundPosition::getFundId).distinct().toList();
        Map<Long, InvestmentFund> fundsById = investmentFundRepository.findAllById(fundIds).stream()
                .collect(Collectors.toMap(InvestmentFund::getId, f -> f));

        // Pre-compute fundValue + sumTotalInvested per fundId — kasnije se mapper
        // poziva N puta (po jednom za svaku poziciju) ali bez novih DB poziva.
        Map<Long, BigDecimal> fundValueById = new HashMap<>();
        Map<Long, BigDecimal> sumInvestedById = new HashMap<>();
        for (Long fundId : fundIds) {
            InvestmentFund fund = fundsById.get(fundId);
            if (fund == null) {
                fundValueById.put(fundId, BigDecimal.ZERO);
                sumInvestedById.put(fundId, BigDecimal.ZERO);
                continue;
            }
            fundValueById.put(fundId, safeCompute(() -> fundValueCalculator.computeFundValue(fund), BigDecimal.ZERO));
            BigDecimal sumInvested = clientFundPositionRepository.findByFundId(fundId).stream()
                    .map(ClientFundPosition::getTotalInvested)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            sumInvestedById.put(fundId, sumInvested);
        }

        String userName = resolveUserName(userId, userRole);

        return positions.stream()
                .map(p -> toClientFundPositionDto(
                        p,
                        Optional.ofNullable(fundsById.get(p.getFundId()))
                                .map(InvestmentFund::getName).orElse(null),
                        userName,
                        fundValueById.getOrDefault(p.getFundId(), BigDecimal.ZERO),
                        sumInvestedById.getOrDefault(p.getFundId(), BigDecimal.ZERO)))
                .toList();
    }

    /**
     * T12 — Spec Celina 4 (Nova) §4406-4435 (Napomena 1+2): Banka kao klijent fonda.
     *
     * Vraca sve pozicije gde je vlasnik klijent koji predstavlja banku
     * (userRole='CLIENT', userId == bank.owner-client-id). Koristi se
     * iz Profit Banke portala "Pozicije u fondovima" tab.
     *
     * Resolvovanje banka client_id-ja:
     *  1) lookup u clients tabeli po email-u iz `bank.owner-client-email`
     *     property-ja (default "banka2.doo@banka.rs")
     *  2) ako klijent ne postoji — vrati prazan list (Profit Banke FE
     *     renderuje "Banka nema pozicije" placeholder)
     *
     * Razlog za email-based resolvovanje umesto fixed ID-ja: clients.id
     * je AUTO_INCREMENT pa ne mozemo seed-ovati eksplicitan ID bez
     * konflikta. Email je jedinstven (uk_clients_email constraint) i
     * stabilan kroz re-seed.
     */
    public List<ClientFundPositionDto> listBankPositions() {
        Long bankClientId = clientRepository.findByEmail(bankOwnerClientEmail)
                .map(c -> c.getId())
                .orElse(null);
        if (bankClientId == null) {
            // Banka klijent nije seed-ovan — graceful fallback (Profit Banke
            // FE prikazuje "Banka nema pozicije" placeholder umesto greske).
            log.warn("Bank owner client (email={}) not found — returning empty bank positions list. "
                            + "Add seed entry or set bank.owner-client-email to a valid client.",
                    bankOwnerClientEmail);
            return List.of();
        }
        // userRole je uvek "CLIENT" za bankine pozicije (Napomena 2: "Klijent
        // je klijent koji je vlasnik banke" — banka se ponasa kao obican CLIENT).
        return listMyPositions(bankClientId, "CLIENT");
    }

    /**
     * Mapper iz domena (ClientFundPosition) u FE DTO sa popunjenim izvedenim poljima.
     *
     * Pre-compute pristup: caller (listMyPositions/listBankPositions) racuna
     * fundValue + sumInvested po fundId-ju jednom i prosledjuje ovde, izbegavajuci
     * N+1 kada korisnik ima pozicije u vise fondova.
     *
     * currentValue = fundValue * (position.totalInvested / sumInvested)
     * percentOfFund = position.totalInvested / sumInvested * 100
     * profit = currentValue - position.totalInvested
     *
     * Edge case: ako sumInvested == 0 (svi su povukli ili tek kreiran fond),
     * vraca 0 da izbegne deljenje s nulom.
     */
    private ClientFundPositionDto toClientFundPositionDto(
            ClientFundPosition position,
            String fundName,
            String userName,
            BigDecimal fundValue,
            BigDecimal sumInvested) {
        ClientFundPositionDto dto = new ClientFundPositionDto();
        dto.setId(position.getId());
        dto.setFundId(position.getFundId());
        dto.setFundName(fundName);
        dto.setUserId(position.getUserId());
        dto.setUserRole(position.getUserRole());
        dto.setUserName(userName);
        BigDecimal totalInvested = position.getTotalInvested() != null
                ? position.getTotalInvested()
                : BigDecimal.ZERO;
        dto.setTotalInvested(totalInvested);

        BigDecimal currentValue = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal percentOfFund = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal safeFundValue = fundValue != null ? fundValue : BigDecimal.ZERO;
        BigDecimal safeSumInvested = sumInvested != null ? sumInvested : BigDecimal.ZERO;
        if (safeSumInvested.signum() > 0) {
            currentValue = safeFundValue.multiply(totalInvested)
                    .divide(safeSumInvested, MONEY_SCALE, RoundingMode.HALF_UP);
            percentOfFund = totalInvested.multiply(new BigDecimal("100"))
                    .divide(safeSumInvested, MONEY_SCALE, RoundingMode.HALF_UP);
        }
        dto.setCurrentValue(currentValue);
        dto.setPercentOfFund(percentOfFund);
        dto.setProfit(currentValue.subtract(totalInvested).setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        dto.setLastModifiedAt(position.getLastModifiedAt());
        return dto;
    }

    private String resolveUserName(Long userId, String userRole) {
        if (userId == null || userRole == null) return null;
        if (UserRole.CLIENT.equalsIgnoreCase(userRole)) {
            return clientRepository.findById(userId)
                    .map(c -> joinName(c.getFirstName(), c.getLastName()))
                    .orElse(null);
        }
        if (UserRole.isEmployee(userRole) || UserRole.FUND.equalsIgnoreCase(userRole)) {
            return employeeRepository.findById(userId)
                    .map(e -> joinName(e.getFirstName(), e.getLastName()))
                    .orElse(null);
        }
        return null;
    }

    private static String joinName(String first, String last) {
        String f = first != null ? first.trim() : "";
        String l = last != null ? last.trim() : "";
        String full = (f + " " + l).trim();
        return full.isEmpty() ? null : full;
    }

    private InvestmentFund findActiveFund(Long fundId) {
        InvestmentFund fund = investmentFundRepository.findById(fundId)
                .orElseThrow(() -> new EntityNotFoundException("Fund #" + fundId + " not found."));
        if (!fund.isActive()) {
            throw new IllegalStateException("Fond " + fund.getName() + " nije aktivan.");
        }
        return fund;
    }

    private InvestmentAmounts calculateInvestmentAmounts(InvestFundDto dto, Account sourceAccount, String actorRole) {
        String sourceCurrency = sourceAccount.getCurrency().getCode().toUpperCase(Locale.ROOT);
        String requestedCurrency = normalizeCurrency(dto.getCurrency(), sourceCurrency);
        BigDecimal inputAmount = dto.getAmount().setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        boolean chargeFx = UserRole.isClient(actorRole) && !RSD.equals(sourceCurrency);

        if (RSD.equals(requestedCurrency)) {
            BigDecimal debitAmount;
            BigDecimal commission;
            if (RSD.equals(sourceCurrency)) {
                debitAmount = inputAmount;
                commission = BigDecimal.ZERO;
            } else {
                CurrencyConversionService.ConversionResult conversion = currencyConversionService
                        .convertForPurchase(inputAmount, RSD, sourceCurrency, chargeFx);
                debitAmount = conversion.amount();
                commission = conversion.commission();
            }
            return new InvestmentAmounts(inputAmount, debitAmount, commission);
        }

        if (requestedCurrency.equals(sourceCurrency)) {
            BigDecimal amountRsd = currencyConversionService.convert(inputAmount, sourceCurrency, RSD);
            BigDecimal commission = chargeFx
                    ? inputAmount.multiply(FX_FEE_RATE).setScale(MONEY_SCALE, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            return new InvestmentAmounts(amountRsd, inputAmount, commission);
        }

        throw new IllegalArgumentException("Valuta uplate mora biti RSD ili valuta izvornog racuna ("
                + sourceCurrency + ").");
    }

    private void executePayout(ClientFundTransaction tx, Account fundAccount, Account destinationAccount, String actorRole) {
        BigDecimal amountRsd = tx.getAmountRsd().setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        if (nullToZero(fundAccount.getAvailableBalance()).compareTo(amountRsd) < 0) {
            throw new InsufficientFundsException("Fond nema dovoljno likvidnih RSD sredstava za isplatu.");
        }

        debit(fundAccount, amountRsd);

        String destinationCurrency = destinationAccount.getCurrency().getCode().toUpperCase(Locale.ROOT);
        BigDecimal grossCredit = RSD.equals(destinationCurrency)
                ? amountRsd
                : currencyConversionService.convert(amountRsd, RSD, destinationCurrency);
        BigDecimal fxFee = (!RSD.equals(destinationCurrency) && UserRole.isClient(actorRole))
                ? grossCredit.multiply(FX_FEE_RATE).setScale(MONEY_SCALE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal netCredit = grossCredit.subtract(fxFee).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        credit(destinationAccount, netCredit);
        creditBankFxCommission(destinationCurrency, fxFee, destinationAccount.getId());

        tx.setStatus(ClientFundTransactionStatus.COMPLETED);
        tx.setCompletedAt(LocalDateTime.now());
        tx.setFailureReason(null);
    }

    private ClientFundPosition upsertPosition(Long fundId, InvestorIdentity investor, BigDecimal amountRsd) {
        ClientFundPosition position = clientFundPositionRepository
                .findByFundIdAndUserIdAndUserRole(fundId, investor.userId(), investor.userRole())
                .orElseGet(() -> {
                    ClientFundPosition p = new ClientFundPosition();
                    p.setFundId(fundId);
                    p.setUserId(investor.userId());
                    p.setUserRole(investor.userRole());
                    p.setTotalInvested(BigDecimal.ZERO);
                    return p;
                });
        position.setTotalInvested(nullToZero(position.getTotalInvested()).add(amountRsd).setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        position.setLastModifiedAt(LocalDateTime.now());
        return clientFundPositionRepository.save(position);
    }

    private void decreasePosition(ClientFundPosition position, BigDecimal amountRsd) {
        BigDecimal remaining = nullToZero(position.getTotalInvested()).subtract(amountRsd)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        if (remaining.signum() <= 0) {
            clientFundPositionRepository.delete(position);
            return;
        }
        position.setTotalInvested(remaining);
        position.setLastModifiedAt(LocalDateTime.now());
        clientFundPositionRepository.save(position);
    }

    private void ensureAccountCanBeUsed(Account account, Long actorId, String actorRole, String operationName) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Racun " + account.getAccountNumber() + " nije aktivan.");
        }
        if (UserRole.isClient(actorRole)) {
            if (account.getClient() == null || !Objects.equals(account.getClient().getId(), actorId)) {
                throw new AccessDeniedException("Racun " + account.getAccountNumber()
                        + " ne pripada ulogovanom klijentu.");
            }
            return;
        }
        if (isEmployeeActor(actorRole)) {
            ensureSupervisor(actorId);
            if (account.getCompany() == null || account.getAccountCategory() != AccountCategory.BANK_TRADING) {
                throw new AccessDeniedException("Supervizor za " + operationName
                        + " u ime banke mora izabrati bankin trading racun.");
            }
            return;
        }
        throw new AccessDeniedException("Nepodrzana uloga za operaciju fonda: " + actorRole);
    }

    private InvestorIdentity resolveInvestorIdentity(Long actorId, String actorRole) {
        if (UserRole.isClient(actorRole)) {
            clientRepository.findById(actorId)
                    .orElseThrow(() -> new EntityNotFoundException("Klijent ne postoji: " + actorId));
            return new InvestorIdentity(actorId, UserRole.CLIENT);
        }
        if (isEmployeeActor(actorRole)) {
            ensureSupervisor(actorId);
            Long bankClientId = resolveBankOwnerClientId();
            return new InvestorIdentity(bankClientId, UserRole.CLIENT);
        }
        throw new AccessDeniedException("Nepodrzana uloga za investicione fondove: " + actorRole);
    }

    private Long resolveBankOwnerClientId() {
        Client bankClient = clientRepository.findByEmail(bankOwnerClientEmail)
                .orElseThrow(() -> new IllegalStateException("Banka kao klijent nije seed-ovana: " + bankOwnerClientEmail));
        return bankClient.getId();
    }

    private void ensureSupervisor(Long employeeId) {
        actuaryInfoRepository.findByEmployeeId(employeeId)
                .filter(a -> a.getActuaryType() == ActuaryType.SUPERVISOR)
                .orElseThrow(() -> new AccessDeniedException("Samo supervizor moze da ulaze/povlaci novac u ime banke."));
    }

    private void debit(Account account, BigDecimal amount) {
        BigDecimal scaled = amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        account.setBalance(nullToZero(account.getBalance()).subtract(scaled));
        account.setAvailableBalance(nullToZero(account.getAvailableBalance()).subtract(scaled));
        accountRepository.save(account);
    }

    private void credit(Account account, BigDecimal amount) {
        BigDecimal scaled = amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        account.setBalance(nullToZero(account.getBalance()).add(scaled));
        account.setAvailableBalance(nullToZero(account.getAvailableBalance()).add(scaled));
        accountRepository.save(account);
    }

    private void creditBankFxCommission(String currencyCode, BigDecimal commission, Long accountToSkipId) {
        if (commission == null || commission.signum() <= 0) {
            return;
        }
        Account bankAccount = accountRepository.findFirstByAccountCategoryAndCurrency_Code(
                        AccountCategory.BANK_TRADING, currencyCode)
                .orElseThrow(() -> new IllegalStateException("Bankin racun za FX proviziju ne postoji u valuti " + currencyCode));
        if (Objects.equals(bankAccount.getId(), accountToSkipId)) {
            return;
        }
        credit(bankAccount, commission);
    }

    private String normalizeUserRole(String userRole) {
        if (userRole == null || userRole.isBlank()) {
            throw new AccessDeniedException("Uloga korisnika nije poznata.");
        }
        String role = userRole.toUpperCase(Locale.ROOT);
        if (role.startsWith("ROLE_")) {
            role = role.substring("ROLE_".length());
        }
        if (UserRole.ADMIN.equals(role) || UserRole.SUPERVISOR.equals(role)) {
            return UserRole.EMPLOYEE;
        }
        return role;
    }

    private boolean isEmployeeActor(String role) {
        return UserRole.isEmployee(role) || UserRole.isAdmin(role) || UserRole.SUPERVISOR.equals(role);
    }

    private String normalizeCurrency(String currency, String fallback) {
        return (currency == null || currency.isBlank() ? fallback : currency).toUpperCase(Locale.ROOT);
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private ClientFundTransactionDto toClientFundTransactionDto(ClientFundTransaction tx, String fundName) {
        Account account = tx.getSourceAccountId() == null ? null
                : accountRepository.findById(tx.getSourceAccountId()).orElse(null);
        return new ClientFundTransactionDto(
                tx.getId(),
                tx.getFundId(),
                fundName,
                tx.getUserId(),
                resolveDisplayName(tx.getUserId(), tx.getUserRole()),
                tx.getAmountRsd(),
                account != null ? account.getAccountNumber() : null,
                tx.isInflow(),
                tx.getStatus() != null ? tx.getStatus().name() : null,
                tx.getCreatedAt(),
                tx.getCompletedAt(),
                tx.getFailureReason());
    }

    private String resolveDisplayName(Long userId, String userRole) {
        if (UserRole.isClient(userRole)) {
            return clientRepository.findById(userId)
                    .map(c -> c.getFirstName() + " " + c.getLastName())
                    .orElse("Klijent #" + userId);
        }
        return employeeRepository.findById(userId)
                .map(e -> e.getFirstName() + " " + e.getLastName())
                .orElse("Zaposleni #" + userId);
    }

    private void sendPushNotification(Long userId, String message) {
        log.info("[PUSH NOTIFICATION] userId={}: {}", userId, message);
    }

    private record InvestorIdentity(Long userId, String userRole) {}

    private record InvestmentAmounts(BigDecimal amountRsd, BigDecimal debitAmount, BigDecimal fxCommission) {}

    @Transactional
    public int reassignFundManager(Long oldSupervisorId, Long newAdminId) {
        if (oldSupervisorId == null || newAdminId == null) return 0;
        if (oldSupervisorId.equals(newAdminId)) return 0;
        int reassigned = investmentFundRepository.reassignManager(oldSupervisorId, newAdminId);
        if (reassigned > 0) {
            log.info("InvestmentFund manager reassigned: {} fund(s) from employee #{} to employee #{}",
                    reassigned, oldSupervisorId, newAdminId);
        }
        return reassigned;
    }

    /**
     * Ad-hoc prebacivanje vlasnistva pojedinacnog fonda na drugog supervizora.
     * Razlikuje se od bulk {@link #reassignFundManager(Long, Long)} po tome sto
     * koristi konkretan {@code fundId} umesto da prebaci sve fondove starog
     * managera. Pozivac (admin) moze ovo da uradi i kada stari manager jos uvek
     * ima isSupervisor permisiju (rucna intervencija).
     *
     * Validacije:
     * - fond mora postojati ({@link EntityNotFoundException} inace)
     * - novi manager mora postojati i biti supervizor ({@link IllegalArgumentException})
     * - novi manager mora biti razlicit od trenutnog (no-op vraca prethodno stanje)
     *
     * @return azurirani fund detail
     */
    @Transactional
    public InvestmentFundDetailDto reassignSingleFundManager(Long fundId, Long newManagerEmployeeId) {
        if (fundId == null) {
            throw new IllegalArgumentException("Fund id must not be null");
        }
        if (newManagerEmployeeId == null) {
            throw new IllegalArgumentException("New manager employee id must not be null");
        }

        InvestmentFund fund = investmentFundRepository.findById(fundId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Investment fund #" + fundId + " not found"));

        // novi manager mora biti aktivan supervizor (Celina 4 §324)
        actuaryInfoRepository.findByEmployeeId(newManagerEmployeeId)
                .filter(a -> a.getActuaryType() == ActuaryType.SUPERVISOR)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Employee #" + newManagerEmployeeId + " is not a supervisor; "
                                + "fund manager must be a supervisor."));

        Long oldManagerId = fund.getManagerEmployeeId();
        if (newManagerEmployeeId.equals(oldManagerId)) {
            log.info("reassignSingleFundManager no-op: fund #{} already managed by employee #{}",
                    fundId, newManagerEmployeeId);
            return getFundDetails(fundId);
        }

        fund.setManagerEmployeeId(newManagerEmployeeId);
        investmentFundRepository.save(fund);

        log.info("InvestmentFund #{} manager reassigned from employee #{} to employee #{} (single-fund)",
                fundId, oldManagerId, newManagerEmployeeId);

        return getFundDetails(fundId);
    }

    private Comparator<InvestmentFundSummaryDto> buildComparator(String sortField) {
        if (sortField == null) return Comparator.comparing(InvestmentFundSummaryDto::getName,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        return switch (sortField.toLowerCase()) {
            case "fundvalue" -> Comparator.comparing(InvestmentFundSummaryDto::getFundValue,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "profit" -> Comparator.comparing(InvestmentFundSummaryDto::getProfit,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "minimumcontribution" -> Comparator.comparing(InvestmentFundSummaryDto::getMinimumContribution,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "inceptiondate" -> Comparator.comparing(InvestmentFundSummaryDto::getInceptionDate,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(InvestmentFundSummaryDto::getName,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        };
    }

    private <T> T safeCompute(java.util.function.Supplier<T> supplier, T fallback) {
        try {
            return supplier.get();
        } catch (Exception e) {
            log.warn("FundValueCalculator error: {}", e.getMessage());
            return fallback;
        }
    }
}