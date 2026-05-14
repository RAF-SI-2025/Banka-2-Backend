package rs.raf.banka2_bek.tax.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountStatus;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.auth.model.User;
import rs.raf.banka2_bek.auth.repository.UserRepository;
import rs.raf.banka2_bek.auth.util.UserRole;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;
import rs.raf.banka2_bek.order.model.Order;
import rs.raf.banka2_bek.order.model.OrderDirection;
import rs.raf.banka2_bek.order.repository.OrderRepository;
import rs.raf.banka2_bek.order.service.CurrencyConversionService;
import rs.raf.banka2_bek.otc.model.OtcContract;
import rs.raf.banka2_bek.otc.model.OtcContractStatus;
import rs.raf.banka2_bek.otc.repository.OtcContractRepository;
import rs.raf.banka2_bek.stock.model.ListingType;
import rs.raf.banka2_bek.stock.util.ListingCurrencyResolver;
import rs.raf.banka2_bek.tax.dto.TaxBreakdownItemDto;
import rs.raf.banka2_bek.tax.dto.TaxRecordDto;
import rs.raf.banka2_bek.tax.model.TaxRecord;
import rs.raf.banka2_bek.tax.model.TaxRecordBreakdown;
import rs.raf.banka2_bek.tax.repository.TaxRecordBreakdownRepository;
import rs.raf.banka2_bek.tax.repository.TaxRecordRepository;
import rs.raf.banka2_bek.tax.util.TaxConstants;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaxService {


    private static final Set<ListingType> TAXABLE_LISTING_TYPES =
            EnumSet.of(ListingType.STOCK, ListingType.FOREX, ListingType.FUTURES);

    private final TaxRecordRepository taxRecordRepository;
    private final TaxRecordBreakdownRepository taxRecordBreakdownRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final AccountRepository accountRepository;
    private final CurrencyConversionService currencyConversionService;
    private final OtcContractRepository otcContractRepository;

    @Value("${bank.registration-number}")
    private String bankRegistrationNumber;

    @Value("${state.registration-number}")
    private String stateRegistrationNumber;

    /**
     * Vraca filtrirane tax recorde za admin/employee portal.
     */
    public List<TaxRecordDto> getTaxRecords(String name, String userType) {
        List<TaxRecord> records = taxRecordRepository.findByFilters(
                (name != null && !name.isBlank()) ? name : null,
                (userType != null && !userType.isBlank()) ? userType : null
        );
        return records.stream().map(this::toDto).collect(Collectors.toList());
    }

    /**
     * Vraca tax record za konkretnog korisnika (autentifikovanog).
     */
    public TaxRecordDto getMyTaxRecord(String email) {
        // Probaj kao employee
        Optional<Employee> empOpt = employeeRepository.findByEmail(email);
        if (empOpt.isPresent()) {
            Employee emp = empOpt.get();
            Optional<TaxRecord> record = taxRecordRepository.findByUserIdAndUserType(emp.getId(), UserRole.EMPLOYEE);
            return record.map(this::toDto).orElseGet(() -> emptyDto(emp.getId(),
                    emp.getFirstName() + " " + emp.getLastName(), UserRole.EMPLOYEE));
        }

        // Probaj kao client (User entity)
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            Optional<TaxRecord> record = taxRecordRepository.findByUserIdAndUserType(user.getId(), UserRole.CLIENT);
            return record.map(this::toDto).orElseGet(() -> emptyDto(user.getId(),
                    user.getFirstName() + " " + user.getLastName(), UserRole.CLIENT));
        }

        return emptyDto(0L, "Nepoznat", UserRole.CLIENT);
    }

    /**
     * Pokrece obracun i naplatu poreza za sve korisnike koji imaju ordere.
     *
     * Spec (Celina 3 — Porez): porez na kapitalnu dobit prilikom prodaje
     * akcija "preko berze i OTC trgovinom". Profesorovo pojasnjenje
     * (RAF Discord, 2026-04-26): porez se obracunava i za FOREX (slicno
     * kao stock) i opciono za FUTURES (komplicirano kod isteka jer fizicki
     * dospeva roba, ali u nasem sistemu ne hendlamo dospece — tretiramo
     * ga kao stock). Zato OrderRepository.findByIsDoneTrue() ulazi u
     * obracun za sve trgovacke tipove (STOCK, FOREX, FUTURES). OPCIJE se
     * ne kupuju kroz Order entitet, ne ulaze ovde.
     *
     * Za svakog korisnika: totalProfit = sum(SELL value - BUY cost) po listingu,
     * konvertovano u RSD po srednjem kursu (bez provizije) — spec, Napomena 2.
     * Porez = 15% * totalProfit ako je pozitivan, inace 0.
     * Neplaceni deo se skida sa korisnikovog RSD racuna i ide na drzavni RSD racun.
     *
     * OTC trgovina (Celina 4): EXERCISED ugovor tretiramo kao prodaju akcija po
     * strikePrice za prodavca i kao kupovinu po strikePrice za kupca; dodatno
     * primljena/placena premija ulazi u sell/buy stranu kao realizovani prihod
     * odnosno trosak vezan za listing. Intra-bank OTC pokriva samo akcije.
     *
     * FUND orderi (Celina 4 - Nova): orderi sa fundId != null se preskacu jer
     * fondovi ne ulaze u licnu kapitalnu dobit supervizora.
     */
    @Transactional
    public void calculateTaxForAllUsers() {
        LocalDateTime now = LocalDateTime.now();
        List<Order> allDoneOrders = orderRepository.findByIsDoneTrue().stream()
                .filter(o -> o.getListing() != null
                        && TAXABLE_LISTING_TYPES.contains(o.getListing().getListingType())
                        && o.getFundId() == null)  // Preskoci fond-ordere
                .collect(Collectors.toList());

        // Note: PUT exercise opcija i EXERCISED inter-bank OTC ugovori se trenutno
        // ne ukljucuju eksplicitno u tax obracun. Intra-bank OTC trgovina ulazi
        // kroz Order entitet (vidi grupisanje ispod). Inter-bank OTC tax obracun
        // svaka banka radi nezavisno za domace korisnike (partner banka radi svoj).

        // Grupisemo ordere po userId + userRole
        Map<String, List<Order>> grouped = allDoneOrders.stream()
                .collect(Collectors.groupingBy(o -> o.getUserId() + ":" + o.getUserRole()));

        // OTC: ucitaj sve EXERCISED ugovore — svaki utice na dva korisnika
        // (kupca i prodavca), pa ne mozemo direktno groupingBy.
        List<OtcContract> exercisedContracts = otcContractRepository.findAll().stream()
                .filter(c -> c.getStatus() == OtcContractStatus.EXERCISED
                        && c.getListing() != null
                        && c.getListing().getListingType() == ListingType.STOCK)
                .collect(Collectors.toList());

        // userKey -> listingId -> akumulirana vrednost
        Map<String, Map<Long, BigDecimal>> otcSellByUser = new HashMap<>();
        Map<String, Map<Long, BigDecimal>> otcBuyByUser = new HashMap<>();
        Map<Long, String> otcListingCurrency = new HashMap<>();
        Set<String> otcUserKeys = new HashSet<>();

        for (OtcContract c : exercisedContracts) {
            Long listingId = c.getListing().getId();
            otcListingCurrency.putIfAbsent(listingId,
                    ListingCurrencyResolver.resolveSafe(c.getListing(), "RSD"));

            BigDecimal qty = BigDecimal.valueOf(c.getQuantity());
            BigDecimal strikeTotal = c.getStrikePrice().multiply(qty);
            BigDecimal premium = c.getPremium() != null ? c.getPremium() : BigDecimal.ZERO;

            String sellerKey = c.getSellerId() + ":" + c.getSellerRole();
            String buyerKey = c.getBuyerId() + ":" + c.getBuyerRole();
            otcUserKeys.add(sellerKey);
            otcUserKeys.add(buyerKey);

            otcSellByUser.computeIfAbsent(sellerKey, k -> new HashMap<>())
                    .merge(listingId, strikeTotal.add(premium), BigDecimal::add);
            otcBuyByUser.computeIfAbsent(buyerKey, k -> new HashMap<>())
                    .merge(listingId, strikeTotal.add(premium), BigDecimal::add);
        }

        // Pronadji drzavni RSD racun (racun Republike Srbije za uplatu poreza)
        Account stateAccount = accountRepository
                .findBankAccountByCurrency(stateRegistrationNumber, "RSD")
                .orElse(null);

        Set<String> allKeys = new HashSet<>(grouped.keySet());
        allKeys.addAll(otcUserKeys);

        for (String key : allKeys) {
            String[] parts = key.split(":");
            Long userId = Long.parseLong(parts[0]);
            String userRole = parts[1];
            List<Order> userOrders = grouped.getOrDefault(key, List.of());

            // Racunamo profit per-asset: za svaki listing posebno racunamo sell - buy
            // pa sabiramo samo pozitivne profite (kapitalna dobit).
            // S80: Svi iznosi se konvertuju u RSD pre agregacije, jer orderi mogu
            // biti u razlicitim valutama (USD, EUR, RSD...).
            Map<Long, BigDecimal> buyByListing = new HashMap<>();
            Map<Long, BigDecimal> sellByListing = new HashMap<>();
            Map<Long, String> currencyByListing = new HashMap<>();

            for (Order order : userOrders) {
                Long listingId = order.getListing().getId();
                BigDecimal orderValue = order.getPricePerUnit()
                        .multiply(BigDecimal.valueOf(order.getQuantity()))
                        .multiply(BigDecimal.valueOf(order.getContractSize()));

                currencyByListing.putIfAbsent(listingId, resolveOrderCurrency(order));

                if (order.getDirection() == OrderDirection.SELL) {
                    sellByListing.merge(listingId, orderValue, BigDecimal::add);
                } else {
                    buyByListing.merge(listingId, orderValue, BigDecimal::add);
                }
            }

            // OTC EXERCISED kontribucije za ovog korisnika.
            otcSellByUser.getOrDefault(key, Map.of())
                    .forEach((listingId, value) -> {
                        sellByListing.merge(listingId, value, BigDecimal::add);
                        currencyByListing.putIfAbsent(listingId,
                                otcListingCurrency.getOrDefault(listingId, "RSD"));
                    });
            otcBuyByUser.getOrDefault(key, Map.of())
                    .forEach((listingId, value) -> {
                        buyByListing.merge(listingId, value, BigDecimal::add);
                        currencyByListing.putIfAbsent(listingId,
                                otcListingCurrency.getOrDefault(listingId, "RSD"));
                    });

            // Za svaki listing: profit = sell - buy, konvertuj u RSD, akumuliraj.
            // NET dobit/gubitak se racuna preko svih listinga; porez je 0 ako je total <= 0.
            // P2.4 — biljezimo per-listing breakdown za prikaz/audit.
            //
            // Spec §517 + bug prijavljen 12.05.2026 (tim screenshot tax_record_breakdowns):
            // pre fix-a, listings sa SAMO buy (bez sell) su davali profit = 0 - buy = -buy
            // sto je laznja indikacija "gubitka" — porez se po spec-u racuna SAMO na
            // REALIZOVANU dobit (kad je prodaja izvrsena). Skupljac SVIH bought listings
            // u breakdown-u je davao haosno "sve negativno" stanje. Sad ukljucujemo
            // samo listings sa sell > 0 (realizovani trgovni dogadjaj). Sva nepotrosenih
            // pozicija ostaju u portfolio-u kao unrealized — bez tax efekta.
            BigDecimal totalProfit = BigDecimal.ZERO;
            Set<Long> realizedListings = new HashSet<>(sellByListing.keySet());
            // Akumuliraj per-listing breakdown stavke pre nego sto saznamo TaxRecord ID-jeve
            java.util.List<PerListingProfit> perListingProfits = new java.util.ArrayList<>();
            for (Long listingId : realizedListings) {
                BigDecimal sell = sellByListing.getOrDefault(listingId, BigDecimal.ZERO);
                BigDecimal buy = buyByListing.getOrDefault(listingId, BigDecimal.ZERO);
                BigDecimal assetProfit = sell.subtract(buy);
                String listingCurrency = currencyByListing.getOrDefault(listingId, "RSD");
                BigDecimal profitInRsd = convertToRsd(assetProfit, listingCurrency);
                totalProfit = totalProfit.add(profitInRsd);
                perListingProfits.add(new PerListingProfit(
                        listingId, listingCurrency, assetProfit, profitInRsd));
            }
            BigDecimal taxOwed = totalProfit.compareTo(BigDecimal.ZERO) > 0
                    ? totalProfit.multiply(TaxConstants.TAX_RATE).setScale(4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            String userName = resolveUserName(userId, userRole);
            String userType = UserRole.isEmployee(userRole) ? UserRole.EMPLOYEE : UserRole.CLIENT;

            TaxRecord record = taxRecordRepository.findByUserIdAndUserType(userId, userType)
                    .orElse(TaxRecord.builder()
                            .userId(userId)
                            .userType(userType)
                            .currency("RSD")
                            .taxPaid(BigDecimal.ZERO)
                            .build());

            record.setUserName(userName);
            record.setTotalProfit(totalProfit);
            record.setTaxOwed(taxOwed);
            record.setCalculatedAt(now);

            // Naplati neplaceni porez sa korisnikovog racuna
            BigDecimal previouslyPaid = record.getTaxPaid() != null ? record.getTaxPaid() : BigDecimal.ZERO;
            BigDecimal unpaidTax = taxOwed.subtract(previouslyPaid);

            if (unpaidTax.compareTo(BigDecimal.ZERO) > 0) {
                boolean collected = collectTaxFromUser(userId, userType, unpaidTax, stateAccount);
                if (collected) {
                    record.setTaxPaid(taxOwed);
                    log.info("Tax collected from user {} ({}): {} RSD", userName, userType, unpaidTax);
                } else {
                    log.warn("Could not collect tax from user {} ({}): no RSD account or insufficient funds",
                            userName, userType);
                }
            }

            taxRecordRepository.save(record);

            // P2.4 — perzistiraj per-listing breakdown stavke. Brisemo
            // postojeci breakdown pa ga regenerisemo iz svezih agregata.
            // record.getId() moze biti null ako mock save() ne vraca generated
            // ID — u tom slucaju preskacemo breakdown (regression-safe).
            if (record.getId() != null) {
                taxRecordBreakdownRepository.deleteByTaxRecordId(record.getId());
                for (PerListingProfit p : perListingProfits) {
                    if (p.listingId() == null) continue;
                    BigDecimal listingTaxOwed = p.profitRsd().compareTo(BigDecimal.ZERO) > 0
                            ? p.profitRsd().multiply(TaxConstants.TAX_RATE)
                                    .setScale(4, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    String ticker = listingTickerCache.computeIfAbsent(p.listingId(),
                            id -> resolveTicker(p.listingId()));
                    TaxRecordBreakdown breakdown = TaxRecordBreakdown.builder()
                            .taxRecord(record)
                            .listingId(p.listingId())
                            .ticker(ticker != null ? ticker : "?" + p.listingId())
                            .listingCurrency(p.listingCurrency() != null ? p.listingCurrency() : "RSD")
                            .profitNative(p.profitNative())
                            .profitRsd(p.profitRsd())
                            .taxOwed(listingTaxOwed)
                            .calculatedAt(now)
                            .build();
                    taxRecordBreakdownRepository.save(breakdown);
                }
            }
        }
    }

    /**
     * P2.4 — vraca per-listing breakdown stavke za TaxRecord. Vraca
     * praznu listu ako TaxRecord ne postoji ili jos nije izracunat.
     */
    public List<TaxBreakdownItemDto> getTaxBreakdownForUser(Long userId, String userType) {
        Optional<TaxRecord> recordOpt = taxRecordRepository.findByUserIdAndUserType(userId, userType);
        if (recordOpt.isEmpty()) return List.of();
        return taxRecordBreakdownRepository
                .findByTaxRecordIdOrderByTaxOwedDesc(recordOpt.get().getId())
                .stream()
                .map(b -> new TaxBreakdownItemDto(
                        b.getListingId(),
                        b.getTicker(),
                        b.getListingCurrency(),
                        b.getProfitNative(),
                        b.getProfitRsd(),
                        b.getTaxOwed()))
                .collect(Collectors.toList());
    }

    /** Privremeni kes ticker-a per listingId tokom calculateTax run-a. */
    private final Map<Long, String> listingTickerCache = new HashMap<>();

    private String resolveTicker(Long listingId) {
        if (listingId == null) return null;
        try {
            List<Order> orders = orderRepository.findByIsDoneTrue();
            if (orders == null) return null;
            return orders.stream()
                    .filter(o -> o != null && o.getListing() != null
                            && listingId.equals(o.getListing().getId()))
                    .map(o -> o.getListing().getTicker())
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.debug("resolveTicker failed for listing {}: {}", listingId, e.getMessage());
            return null;
        }
    }

    /** Internal helper za prenos profita per-listing izmedju petlji. */
    private record PerListingProfit(Long listingId, String listingCurrency,
                                    BigDecimal profitNative, BigDecimal profitRsd) {}

    /**
     * Skida porez sa korisnikovog RSD racuna i prebacuje na drzavni racun.
     * Vraca true ako je naplata uspela.
     */
    private boolean collectTaxFromUser(Long userId, String userType, BigDecimal amount, Account stateAccount) {
        if (stateAccount == null) {
            log.warn("State RSD account not found, skipping tax collection");
            return false;
        }

        // Pronadji korisnikov RSD racun
        List<Account> userAccounts;
        if (UserRole.isClient(userType)) {
            userAccounts = accountRepository.findByClientIdAndStatusOrderByAvailableBalanceDesc(
                    userId, AccountStatus.ACTIVE);
        } else {
            // Za zaposlene: koriste bankin racun — porez se interno prebacuje
            // Zaposleni trguju sa bankinih racuna, porez se samo belezi
            return true;
        }

        // Nadji RSD racun sa dovoljno sredstava
        Optional<Account> rsdAccount = userAccounts.stream()
                .filter(a -> "RSD".equals(a.getCurrency().getCode()))
                .filter(a -> a.getBalance().compareTo(amount) >= 0)
                .findFirst();

        if (rsdAccount.isEmpty()) {
            return false;
        }

        Account userAccount = rsdAccount.get();
        userAccount.setBalance(userAccount.getBalance().subtract(amount));
        userAccount.setAvailableBalance(userAccount.getAvailableBalance().subtract(amount));
        accountRepository.save(userAccount);

        stateAccount.setBalance(stateAccount.getBalance().add(amount));
        stateAccount.setAvailableBalance(stateAccount.getAvailableBalance().add(amount));
        accountRepository.save(stateAccount);

        return true;
    }

    /**
     * Resolve-uje ISO kod valute za listing ordera. Tax modul koristi RSD
     * kao fallback (sve se svodi na RSD pri obracunu poreza), sto je
     * razlicito od order flow-a koji padne na USD.
     *
     * @see ListingCurrencyResolver#resolveSafe(rs.raf.banka2_bek.stock.model.Listing, String)
     */
    private String resolveOrderCurrency(Order order) {
        if (order == null || order.getListing() == null) {
            return "RSD";
        }
        return ListingCurrencyResolver.resolveSafe(order.getListing(), "RSD");
    }

    /**
     * Konvertuje iznos u RSD. Ako je vec u RSD, vraca isti iznos.
     * Koristi CurrencyConversionService (srednji kurs, bez provizije) — S80.
     */
    private BigDecimal convertToRsd(BigDecimal amount, String fromCurrency) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        if (fromCurrency == null || "RSD".equalsIgnoreCase(fromCurrency)) {
            return amount;
        }
        try {
            return currencyConversionService.convert(amount, fromCurrency, "RSD");
        } catch (Exception e) {
            log.warn("Currency conversion {} -> RSD failed, using raw amount: {}", fromCurrency, e.getMessage());
            return amount;
        }
    }

    private String resolveUserName(Long userId, String userRole) {
        if (UserRole.isEmployee(userRole)) {
            return employeeRepository.findById(userId)
                    .map(e -> e.getFirstName() + " " + e.getLastName())
                    .orElse("Zaposleni #" + userId);
        }
        return userRepository.findById(userId)
                .map(u -> u.getFirstName() + " " + u.getLastName())
                .orElse("Klijent #" + userId);
    }

    private TaxRecordDto toDto(TaxRecord record) {
        return new TaxRecordDto(
                record.getId(),
                record.getUserId(),
                record.getUserName(),
                record.getUserType(),
                record.getTotalProfit(),
                record.getTaxOwed(),
                record.getTaxPaid(),
                record.getCurrency()
        );
    }

    private TaxRecordDto emptyDto(Long userId, String userName, String userType) {
        return new TaxRecordDto(null, userId, userName, userType,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "RSD");
    }
}
