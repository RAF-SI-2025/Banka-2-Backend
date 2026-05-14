package rs.raf.banka2_bek.otc.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountStatus;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.auth.util.UserContext;
import rs.raf.banka2_bek.auth.util.UserResolver;
import rs.raf.banka2_bek.auth.util.UserRole;
import rs.raf.banka2_bek.order.exception.InsufficientFundsException;
import rs.raf.banka2_bek.order.service.CurrencyConversionService;
import rs.raf.banka2_bek.otc.dto.CounterOtcOfferDto;
import rs.raf.banka2_bek.otc.dto.CreateOtcOfferDto;
import rs.raf.banka2_bek.otc.dto.OtcContractDto;
import rs.raf.banka2_bek.otc.dto.OtcListingDto;
import rs.raf.banka2_bek.otc.dto.OtcOfferDto;
import rs.raf.banka2_bek.otc.mapper.OtcMapper;
import rs.raf.banka2_bek.otc.model.OtcContract;
import rs.raf.banka2_bek.otc.model.OtcContractStatus;
import rs.raf.banka2_bek.otc.model.OtcOffer;
import rs.raf.banka2_bek.otc.model.OtcOfferStatus;
import rs.raf.banka2_bek.otc.repository.OtcContractRepository;
import rs.raf.banka2_bek.otc.repository.OtcOfferRepository;
import rs.raf.banka2_bek.portfolio.model.Portfolio;
import rs.raf.banka2_bek.portfolio.repository.PortfolioRepository;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.model.ListingType;
import rs.raf.banka2_bek.stock.repository.ListingRepository;
import rs.raf.banka2_bek.stock.util.ListingCurrencyResolver;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Servis za OTC (Over-the-Counter) trgovinu unutar iste banke.
 *
 * Spec: Celina 4 - OTC Trgovina. Trguju se samo akcije koje je prodavac
 * prebacio na "javni rezim" (Portfolio.publicQuantity). Kupac i prodavac
 * pregovaraju o kolicini, strike ceni, premiji i settlementDate; kada
 * se dogovor postigne, kupac placa premiju i dobija opcioni ugovor.
 * Do settlementDate-a kupac moze iskoristiti ugovor (kupovina akcija po
 * strike ceni). Inace ugovor istice.
 *
 * Napomena: za generaciju 2024/25 radimo samo intra-bank OTC — nema SAGA
 * pattern-a jer obe strane imaju racune u istoj banci.
 */
@Slf4j
@Service
public class OtcService {

    private final OtcOfferRepository offerRepository;
    private final OtcContractRepository contractRepository;
    private final PortfolioRepository portfolioRepository;
    private final ListingRepository listingRepository;
    private final AccountRepository accountRepository;
    private final CurrencyConversionService currencyConversionService;
    private final UserResolver userResolver;
    private final String bankRegistrationNumber;

    public OtcService(OtcOfferRepository offerRepository,
                      OtcContractRepository contractRepository,
                      PortfolioRepository portfolioRepository,
                      ListingRepository listingRepository,
                      AccountRepository accountRepository,
                      CurrencyConversionService currencyConversionService,
                      UserResolver userResolver,
                      @Value("${bank.registration-number}") String bankRegistrationNumber) {
        this.offerRepository = offerRepository;
        this.contractRepository = contractRepository;
        this.portfolioRepository = portfolioRepository;
        this.listingRepository = listingRepository;
        this.accountRepository = accountRepository;
        this.currencyConversionService = currencyConversionService;
        this.userResolver = userResolver;
        this.bankRegistrationNumber = bankRegistrationNumber;
    }

    // ────────────────────────── Discovery ──────────────────────────

    /**
     * Lista akcija koje drugi korisnici trenutno nude na OTC — osnov za
     * "Portal: OTC Trgovina". Prikazuje se kolicina koja je JOS
     * raspolozi­va (publicQuantity - aktivna OTC rezervacija).
     */
    public List<OtcListingDto> listDiscoveryListings() {
        UserContext me = resolveCurrentUser();
        ensureOtcAccess(me);
        // P3 — Klijenti vide samo ponude klijenata, supervizori samo supervizora.
        // Spec Celina 4 (Nova) §822-826 + Celina 5 (Nova) §840-848.
        boolean meIsClient = UserRole.isClient(me.userRole());
        List<Portfolio> publicPortfolios = portfolioRepository.findAll().stream()
                .filter(p -> p.getPublicQuantity() != null && p.getPublicQuantity() > 0)
                .filter(p -> !(p.getUserId().equals(me.userId())
                        && me.userRole().equals(p.getUserRole())))
                .filter(p -> meIsClient ? UserRole.isClient(p.getUserRole())
                                        : UserRole.isEmployee(p.getUserRole()))
                .toList();
        return publicPortfolios.stream()
                .map(this::toListingDto)
                .filter(dto -> dto.getAvailablePublicQuantity() > 0)
                .sorted(Comparator.comparing(OtcListingDto::getListingTicker))
                .toList();
    }

    /**
     * Moje sopstvene javne akcije — portfolio item-i tekuceg korisnika
     * gde je publicQuantity > 0. Razliciti od {@link #listDiscoveryListings}
     * koji eksplicitno filtrira `me.userId()` (Discovery prikazuje samo tude
     * akcije za pravljenje ponuda). Ovaj endpoint daje user-u vidljivost
     * tome STA JE on objavio za druge — UX bag prijavljen 10.05.2026 vece-7
     * ("ne vidim svoje akcije da su javne").
     */
    public List<OtcListingDto> listMyPublicListings() {
        UserContext me = resolveCurrentUser();
        ensureOtcAccess(me);
        return portfolioRepository.findByUserIdAndUserRole(me.userId(), me.userRole()).stream()
                .filter(p -> p.getPublicQuantity() != null && p.getPublicQuantity() > 0)
                .map(this::toListingDto)
                .sorted(Comparator.comparing(OtcListingDto::getListingTicker))
                .toList();
    }

    // ────────────────────────── Offers ──────────────────────────

    public List<OtcOfferDto> listMyActiveOffers() {
        UserContext me = resolveCurrentUser();
        ensureOtcAccess(me);
        List<OtcOffer> offers = offerRepository.findActiveForUser(me.userId(), me.userRole());
        return offers.stream().map(o -> mapOffer(o, me.userId())).toList();
    }

    @Transactional
    public OtcOfferDto createOffer(CreateOtcOfferDto dto) {
        UserContext me = resolveCurrentUser();
        ensureOtcAccess(me);
        Listing listing = listingRepository.findById(dto.getListingId())
                .orElseThrow(() -> new EntityNotFoundException("Listing ne postoji: " + dto.getListingId()));
        if (listing.getListingType() != ListingType.STOCK) {
            throw new IllegalArgumentException("OTC je dozvoljen samo za akcije.");
        }
        ensureSettlementInFuture(dto.getSettlementDate());

        String sellerRole = resolveUserRole(dto.getSellerId());
        if (me.userId().equals(dto.getSellerId()) && me.userRole().equals(sellerRole)) {
            throw new IllegalArgumentException("Ne mozete napraviti OTC ponudu sami sebi.");
        }
        ensureSameRoleParticipants(me.userRole(), sellerRole);

        Portfolio sellerPortfolio = portfolioRepository
                .findByUserIdAndUserRole(dto.getSellerId(), sellerRole).stream()
                .filter(p -> p.getListingId().equals(listing.getId()))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException(
                        "Prodavac nema akcije " + listing.getTicker() + " u portfoliju."));

        int available = availablePublicQty(sellerPortfolio);
        if (available < dto.getQuantity()) {
            throw new IllegalArgumentException(
                    "Prodavac javno nudi " + available + " akcija, a ponuda trazi " + dto.getQuantity() + ".");
        }

        OtcOffer offer = new OtcOffer();
        offer.setBuyerId(me.userId());
        offer.setBuyerRole(me.userRole());
        offer.setSellerId(dto.getSellerId());
        offer.setSellerRole(sellerRole);
        offer.setListing(listing);
        offer.setQuantity(dto.getQuantity());
        offer.setPricePerStock(dto.getPricePerStock());
        offer.setPremium(dto.getPremium());
        offer.setSettlementDate(dto.getSettlementDate());
        offer.setLastModifiedById(me.userId());
        offer.setLastModifiedByName(resolveUserName(me.userId(), me.userRole()));
        offer.setWaitingOnUserId(dto.getSellerId());
        offer.setStatus(OtcOfferStatus.ACTIVE);

        return mapOffer(offerRepository.save(offer), me.userId());
    }

    @Transactional
    public OtcOfferDto counterOffer(Long offerId, CounterOtcOfferDto dto) {
        UserContext me = resolveCurrentUser();
        ensureOtcAccess(me);
        OtcOffer offer = loadActiveOfferForParticipant(offerId, me);
        ensureSameRoleParticipants(offer.getBuyerRole(), offer.getSellerRole());

        boolean isBuyer = offer.getBuyerId().equals(me.userId())
                && offer.getBuyerRole().equals(me.userRole());
        boolean isSeller = offer.getSellerId().equals(me.userId())
                && offer.getSellerRole().equals(me.userRole());
        if (offer.getQuantity() > 0 && !isBuyer && !isSeller) {
            throw new AccessDeniedException("Niste ucesnik u ovoj ponudi.");
        }

        // Prodavac mora i dalje da ima dovoljno javnih akcija za predlozenu kolicinu
        if (offer.getSellerId() != null) {
            Portfolio sp = portfolioRepository
                    .findByUserIdAndUserRole(offer.getSellerId(), offer.getSellerRole()).stream()
                    .filter(p -> p.getListingId().equals(offer.getListing().getId()))
                    .findFirst()
                    .orElseThrow(() -> new EntityNotFoundException("Prodavac vise nema ove akcije."));
            int avail = availablePublicQty(sp);
            if (avail < dto.getQuantity()) {
                throw new IllegalArgumentException(
                        "Prodavac javno nudi samo " + avail + " akcija.");
            }
        }

        ensureSettlementInFuture(dto.getSettlementDate());
        offer.setQuantity(dto.getQuantity());
        offer.setPricePerStock(dto.getPricePerStock());
        offer.setPremium(dto.getPremium());
        offer.setSettlementDate(dto.getSettlementDate());
        offer.setLastModifiedById(me.userId());
        offer.setLastModifiedByName(resolveUserName(me.userId(), me.userRole()));
        offer.setWaitingOnUserId(me.userId().equals(offer.getBuyerId())
                ? offer.getSellerId() : offer.getBuyerId());

        return mapOffer(offerRepository.save(offer), me.userId());
    }

    @Transactional
    public OtcOfferDto declineOffer(Long offerId) {
        UserContext me = resolveCurrentUser();
        ensureOtcAccess(me);
        OtcOffer offer = loadActiveOfferForParticipant(offerId, me);
        offer.setStatus(OtcOfferStatus.DECLINED);
        offer.setLastModifiedById(me.userId());
        offer.setLastModifiedByName(resolveUserName(me.userId(), me.userRole()));
        return mapOffer(offerRepository.save(offer), me.userId());
    }

    /**
     * Prihvatanje ponude — moguce je samo kad je {@code waitingOnUserId == me}.
     * Kreira opcioni ugovor, placa premiju prodavcu (sa eventualnom menjacnickom
     * konverzijom ako buyerAccount nije u valuti listinga).
     */
    @Transactional
    public OtcOfferDto acceptOffer(Long offerId, Long buyerAccountId) {
        UserContext me = resolveCurrentUser();
        ensureOtcAccess(me);
        OtcOffer offer = loadActiveOfferForParticipant(offerId, me);
        ensureSameRoleParticipants(offer.getBuyerRole(), offer.getSellerRole());

        if (!me.userId().equals(offer.getWaitingOnUserId())) {
            throw new IllegalStateException("Nije na vama red da odgovorite na ovu ponudu.");
        }

        Portfolio sp = portfolioRepository
                .findByUserIdAndUserRole(offer.getSellerId(), offer.getSellerRole()).stream()
                .filter(p -> p.getListingId().equals(offer.getListing().getId()))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Prodavac nema akcije za ovu ponudu."));
        int avail = availablePublicQty(sp);
        if (avail < offer.getQuantity()) {
            throw new IllegalArgumentException(
                    "Prodavac vise ne nudi dovoljno javnih akcija (preostalo: " + avail + ").");
        }

        // Iznos premije transferuje buyer -> seller. Buyer racun je izabran pri
        // prihvatanju; seller dobija na racun u valuti listinga (fallback: prvi aktivan).
        String listingCurrency = resolveListingCurrency(offer.getListing());
        Account buyerAccount = resolveBuyerAccount(offer.getBuyerId(), offer.getBuyerRole(), buyerAccountId, listingCurrency);
        Account sellerAccount = resolveSellerAccount(offer.getSellerId(), offer.getSellerRole(), listingCurrency);

        transferPremium(buyerAccount, sellerAccount, offer.getPremium(), listingCurrency,
                UserRole.isClient(offer.getBuyerRole()));

        // Kreiraj ugovor
        OtcContract contract = new OtcContract();
        contract.setSourceOfferId(offer.getId());
        contract.setBuyerId(offer.getBuyerId());
        contract.setBuyerRole(offer.getBuyerRole());
        contract.setSellerId(offer.getSellerId());
        contract.setSellerRole(offer.getSellerRole());
        contract.setListing(offer.getListing());
        contract.setQuantity(offer.getQuantity());
        contract.setStrikePrice(offer.getPricePerStock());
        contract.setPremium(offer.getPremium());
        contract.setSettlementDate(offer.getSettlementDate());
        contract.setStatus(OtcContractStatus.ACTIVE);
        contractRepository.save(contract);

        offer.setStatus(OtcOfferStatus.ACCEPTED);
        offer.setLastModifiedById(me.userId());
        offer.setLastModifiedByName(resolveUserName(me.userId(), me.userRole()));
        offerRepository.save(offer);

        log.info("OTC offer #{} accepted by {} — contract #{} created", offer.getId(), me.userId(), contract.getId());
        return mapOffer(offer, me.userId());
    }

    // ────────────────────────── Contracts ──────────────────────────

    public List<OtcContractDto> listMyContracts(String statusFilter) {
        UserContext me = resolveCurrentUser();
        ensureOtcAccess(me);
        List<OtcContract> contracts;
        if (statusFilter == null || statusFilter.isBlank() || "ALL".equalsIgnoreCase(statusFilter)) {
            contracts = contractRepository.findAllForUser(me.userId(), me.userRole());
        } else {
            OtcContractStatus status;
            try {
                status = OtcContractStatus.valueOf(statusFilter.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Nepoznat status: " + statusFilter);
            }
            contracts = contractRepository.findByUserAndStatus(me.userId(), me.userRole(), status);
        }
        return contracts.stream().map(this::toContractDto).toList();
    }

    /**
     * Iskoriscavanje opcionog ugovora — kupac placa strike*qty, seller predaje
     * akcije. Moguce samo pre settlementDate-a i samo od strane kupca.
     */
    @Transactional
    public OtcContractDto exerciseContract(Long contractId, Long buyerAccountId) {
        UserContext me = resolveCurrentUser();
        ensureOtcAccess(me);
        OtcContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new EntityNotFoundException("OTC ugovor ne postoji: " + contractId));

        if (!contract.getBuyerId().equals(me.userId())
                || !contract.getBuyerRole().equals(me.userRole())) {
            throw new AccessDeniedException("Samo kupac moze iskoristiti ugovor.");
        }
        if (contract.getStatus() != OtcContractStatus.ACTIVE) {
            throw new IllegalStateException("Ugovor nije aktivan (status=" + contract.getStatus() + ").");
        }
        if (contract.getSettlementDate().isBefore(LocalDate.now())) {
            throw new IllegalStateException("Settlement datum je prosao — ugovor je istekao.");
        }

        BigDecimal totalCost = contract.getStrikePrice().multiply(BigDecimal.valueOf(contract.getQuantity()))
                .setScale(4, RoundingMode.HALF_UP);

        String listingCurrency = resolveListingCurrency(contract.getListing());
        Account buyerAccount = resolveBuyerAccount(contract.getBuyerId(), contract.getBuyerRole(), buyerAccountId, listingCurrency);
        Account sellerAccount = resolveSellerAccount(contract.getSellerId(), contract.getSellerRole(), listingCurrency);

        // 1. Transfer cene akcija buyer -> seller
        transferPremium(buyerAccount, sellerAccount, totalCost, listingCurrency,
                UserRole.isClient(contract.getBuyerRole()));

        // 2. Prebaci akcije: umanji seller portfolio, uvecaj buyer portfolio
        Portfolio sellerPortfolio = portfolioRepository.findByUserIdAndUserRoleAndListingIdForUpdate(
                        contract.getSellerId(), contract.getSellerRole(), contract.getListing().getId())
                .orElseThrow(() -> new IllegalStateException("Prodavac vise nema ovu hartiju u portfoliju."));
        if (sellerPortfolio.getQuantity() < contract.getQuantity()) {
            throw new IllegalStateException("Prodavac nema dovoljno akcija za izvrsavanje ugovora.");
        }
        sellerPortfolio.setQuantity(sellerPortfolio.getQuantity() - contract.getQuantity());
        Integer currentPublic = sellerPortfolio.getPublicQuantity();
        int newPublic = Math.max(0, (currentPublic != null ? currentPublic : 0) - contract.getQuantity());
        sellerPortfolio.setPublicQuantity(newPublic);
        if (sellerPortfolio.getQuantity() <= 0) {
            portfolioRepository.delete(sellerPortfolio);
        } else {
            portfolioRepository.save(sellerPortfolio);
        }

        Optional<Portfolio> existingBuyer = portfolioRepository.findByUserIdAndUserRoleAndListingIdForUpdate(
                contract.getBuyerId(), contract.getBuyerRole(), contract.getListing().getId());
        if (existingBuyer.isPresent()) {
            Portfolio bp = existingBuyer.get();
            int oldQty = bp.getQuantity();
            BigDecimal oldTotal = bp.getAverageBuyPrice().multiply(BigDecimal.valueOf(oldQty));
            BigDecimal addTotal = contract.getStrikePrice().multiply(BigDecimal.valueOf(contract.getQuantity()));
            int newQty = oldQty + contract.getQuantity();
            BigDecimal newAvg = oldTotal.add(addTotal)
                    .divide(BigDecimal.valueOf(newQty), 4, RoundingMode.HALF_UP);
            bp.setQuantity(newQty);
            bp.setAverageBuyPrice(newAvg);
            portfolioRepository.save(bp);
        } else {
            Portfolio bp = new Portfolio();
            bp.setUserId(contract.getBuyerId());
            bp.setUserRole(contract.getBuyerRole());
            bp.setListingId(contract.getListing().getId());
            bp.setListingTicker(contract.getListing().getTicker());
            bp.setListingName(contract.getListing().getName());
            bp.setListingType(contract.getListing().getListingType().name());
            bp.setQuantity(contract.getQuantity());
            bp.setAverageBuyPrice(contract.getStrikePrice());
            bp.setPublicQuantity(0);
            portfolioRepository.save(bp);
        }

        // 3. Mark contract exercised
        contract.setStatus(OtcContractStatus.EXERCISED);
        contract.setExercisedAt(LocalDateTime.now());
        contractRepository.save(contract);

        log.info("OTC contract #{} exercised by buyer {} — {} x {} @ strike {}",
                contract.getId(), contract.getBuyerId(), contract.getQuantity(),
                contract.getListing().getTicker(), contract.getStrikePrice());
        return toContractDto(contract);
    }

    /**
     * Automatsko markiranje isteklih aktivnih ugovora kao EXPIRED.
     * Cist bookkeeping — publicQuantity prodavca se automatski vraca
     * jer availablePublicQty koristi samo ACTIVE ugovore.
     */
    @Transactional
    public int expireSettledContracts() {
        List<OtcContract> expired = contractRepository.findExpiredActive(LocalDate.now());
        for (OtcContract c : expired) {
            c.setStatus(OtcContractStatus.EXPIRED);
            contractRepository.save(c);
        }
        return expired.size();
    }

    // ────────────────────────── Helpers ──────────────────────────

    /**
     * P1 — Spec Celina 4 (Nova) §145-148: OTC dozvoljen samo SUPERVIZORIMA
     * (od zaposlenih) i KLIJENTIMA. Agenti su eksplicitno iskljuceni.
     * Spring SecurityConfig vec hvata role; ovaj poziv je defense-in-depth
     * za slucaj da neko zaobidje filter (npr. test sa @WithMockUser).
     */
    private void ensureOtcAccess(UserContext user) {
        String role = user.userRole();
        if (UserRole.isClient(role)) {
            return;
        }
        // Za zaposlene: dozvoljeni samo supervizori (admini su uvek supervizori).
        // Agent koji nema SUPERVISOR niti ADMIN authority dobija 403.
        if (UserRole.isEmployee(role)) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null) {
                throw new AccessDeniedException("Niste autentifikovani.");
            }
            boolean isSupervisor = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch(a -> "ADMIN".equals(a)
                            || "SUPERVISOR".equals(a)
                            || "ROLE_ADMIN".equals(a)
                            || "ROLE_SUPERVISOR".equals(a));
            if (!isSupervisor) {
                throw new AccessDeniedException(
                        "OTC je dozvoljen samo supervizorima i klijentima (po Celini 4 Nova).");
            }
            return;
        }
        throw new AccessDeniedException("Nepoznata uloga ne moze pristupiti OTC-u.");
    }

    /**
     * P2 — Spec Celina 4 (Nova) §822-826: "Komuniciraju 2 klijenta ili 2 supervizora".
     * Klijent nikad ne sklapa ugovor sa supervizorom — strane moraju biti iste role.
     */
    private void ensureSameRoleParticipants(String roleA, String roleB) {
        boolean bothClients = UserRole.isClient(roleA) && UserRole.isClient(roleB);
        boolean bothEmployees = UserRole.isEmployee(roleA) && UserRole.isEmployee(roleB);
        if (!bothClients && !bothEmployees) {
            throw new IllegalArgumentException(
                    "OTC trgovina je dozvoljena samo izmedju ucesnika iste role "
                            + "(klijent-klijent ili supervizor-supervizor).");
        }
    }

    private void ensureSettlementInFuture(LocalDate settlementDate) {
        if (settlementDate == null) {
            throw new IllegalArgumentException("Settlement datum je obavezan.");
        }
        if (!settlementDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "Settlement datum mora biti u buducnosti (zadato: " + settlementDate + ").");
        }
    }

    private int availablePublicQty(Portfolio portfolio) {
        Integer publicQtyRaw = portfolio.getPublicQuantity();
        int publicQty = publicQtyRaw != null ? publicQtyRaw : 0;
        int reserved = contractRepository.sumActiveReservedByListing(
                portfolio.getUserId(), portfolio.getUserRole(), portfolio.getListingId());
        return Math.max(0, publicQty - reserved);
    }

    private OtcOffer loadActiveOfferForParticipant(Long offerId, UserContext me) {
        OtcOffer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new EntityNotFoundException("OTC ponuda ne postoji: " + offerId));
        boolean isBuyer = offer.getBuyerId().equals(me.userId())
                && offer.getBuyerRole().equals(me.userRole());
        boolean isSeller = offer.getSellerId().equals(me.userId())
                && offer.getSellerRole().equals(me.userRole());
        if (!isBuyer && !isSeller) {
            throw new AccessDeniedException("Niste ucesnik u ovoj ponudi.");
        }
        if (offer.getStatus() != OtcOfferStatus.ACTIVE) {
            throw new IllegalStateException("Ponuda vise nije aktivna (status=" + offer.getStatus() + ").");
        }
        return offer;
    }

    private OtcListingDto toListingDto(Portfolio portfolio) {
        Listing listing = listingRepository.findById(portfolio.getListingId())
                .orElse(null);
        if (listing == null) {
            return null;
        }
        String listingCurrency = resolveListingCurrency(listing);
        String sellerRole = resolveUserRole(portfolio.getUserId());
        return new OtcListingDto(
                portfolio.getId(),
                listing.getId(),
                listing.getTicker(),
                listing.getName(),
                listing.getExchangeAcronym(),
                listingCurrency,
                listing.getPrice(),
                portfolio.getPublicQuantity(),
                availablePublicQty(portfolio),
                portfolio.getUserId(),
                sellerRole,
                resolveUserName(portfolio.getUserId(), sellerRole));
    }

    private OtcOfferDto mapOffer(OtcOffer offer, Long viewerUserId) {
        String buyerName = resolveUserName(offer.getBuyerId(), offer.getBuyerRole());
        String sellerName = resolveUserName(offer.getSellerId(), offer.getSellerRole());
        String currency = resolveListingCurrency(offer.getListing());
        return OtcMapper.toDto(offer, buyerName, sellerName, currency, viewerUserId);
    }

    private OtcContractDto toContractDto(OtcContract contract) {
        String buyerName = resolveUserName(contract.getBuyerId(), contract.getBuyerRole());
        String sellerName = resolveUserName(contract.getSellerId(), contract.getSellerRole());
        String currency = resolveListingCurrency(contract.getListing());
        BigDecimal currentPrice = contract.getListing() != null ? contract.getListing().getPrice() : null;
        return OtcMapper.toDto(contract, buyerName, sellerName, currency, currentPrice);
    }

    private void transferPremium(Account from, Account to, BigDecimal amountInListingCurrency,
                                 String listingCurrency, boolean chargeFxCommission) {
        BigDecimal fromAmount;
        String fromCcy = from.getCurrency().getCode();
        if (fromCcy.equals(listingCurrency)) {
            fromAmount = amountInListingCurrency;
        } else {
            CurrencyConversionService.ConversionResult conv = currencyConversionService
                    .convertForPurchase(amountInListingCurrency, listingCurrency, fromCcy, chargeFxCommission);
            fromAmount = conv.amount();
        }

        if (from.getAvailableBalance().compareTo(fromAmount) < 0) {
            throw new InsufficientFundsException(
                    "Nedovoljno sredstava na racunu " + from.getAccountNumber()
                            + ": potrebno " + fromAmount + " " + fromCcy);
        }

        from.setBalance(from.getBalance().subtract(fromAmount));
        from.setAvailableBalance(from.getAvailableBalance().subtract(fromAmount));
        accountRepository.save(from);

        BigDecimal toAmount;
        String toCcy = to.getCurrency().getCode();
        if (toCcy.equals(listingCurrency)) {
            toAmount = amountInListingCurrency;
        } else {
            // Prodavcev racun dobija konvertovan iznos po srednjem kursu (bez komisije —
            // banka je vec uzela deo pri buyer debitu ako je chargeFxCommission=true)
            toAmount = currencyConversionService.convert(amountInListingCurrency, listingCurrency, toCcy);
        }
        to.setBalance(to.getBalance().add(toAmount));
        to.setAvailableBalance(to.getAvailableBalance().add(toAmount));
        accountRepository.save(to);
    }

    private Account resolveBuyerAccount(Long buyerId, String buyerRole, Long requestedAccountId,
                                        String listingCurrency) {
        if (requestedAccountId != null) {
            Account account = accountRepository.findForUpdateById(requestedAccountId)
                    .orElseThrow(() -> new EntityNotFoundException("Racun ne postoji: " + requestedAccountId));
            verifyAccountOwnership(account, buyerId, buyerRole);
            return account;
        }
        return findDefaultAccount(buyerId, buyerRole, listingCurrency);
    }

    private Account resolveSellerAccount(Long sellerId, String sellerRole, String listingCurrency) {
        return findDefaultAccount(sellerId, sellerRole, listingCurrency);
    }

    /**
     * Najbolji podrazumevani racun za korisnika — prvo racun u datoj valuti,
     * inace prvi aktivan sa najvecim raspolozivim balansom. Za zaposlene
     * koristi bankin racun.
     */
    private Account findDefaultAccount(Long userId, String role, String preferredCurrency) {
        if (UserRole.isClient(role)) {
            List<Account> accounts = accountRepository.findByClientIdAndStatusOrderByAvailableBalanceDesc(
                    userId, AccountStatus.ACTIVE);
            return accounts.stream()
                    .filter(a -> preferredCurrency.equals(a.getCurrency().getCode()))
                    .findFirst()
                    .or(() -> accounts.stream().findFirst())
                    .map(a -> accountRepository.findForUpdateById(a.getId()).orElse(a))
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Korisnik #" + userId + " nema aktivan racun."));
        }
        // EMPLOYEE - koristi bankin racun (supervizor/agent trguje sa bankinih racuna)
        return accountRepository.findBankAccountForUpdateByCurrency(bankRegistrationNumber, preferredCurrency)
                .or(() -> accountRepository.findBankAccountForUpdateByCurrency(bankRegistrationNumber, "USD"))
                .orElseThrow(() -> new EntityNotFoundException(
                        "Bankin racun u " + preferredCurrency + " ne postoji."));
    }

    private void verifyAccountOwnership(Account account, Long userId, String role) {
        if (UserRole.isClient(role)) {
            if (account.getClient() == null || !userId.equals(account.getClient().getId())) {
                throw new AccessDeniedException("Racun " + account.getAccountNumber()
                        + " ne pripada korisniku.");
            }
        }
        // Za EMPLOYEE — pretpostavka je da je racun bankin; ne proveravamo vlasnistvo striktno.
    }

    private String resolveListingCurrency(Listing listing) {
        return ListingCurrencyResolver.resolve(listing);
    }

    private UserContext resolveCurrentUser() {
        return userResolver.resolveCurrent();
    }

    private String resolveUserName(Long userId, String role) {
        return userResolver.resolveName(userId, role);
    }

    private String resolveUserRole(Long userId) {
        return userResolver.resolveRole(userId);
    }
}
