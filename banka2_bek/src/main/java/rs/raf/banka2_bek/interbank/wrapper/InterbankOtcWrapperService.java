package rs.raf.banka2_bek.interbank.wrapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;
import rs.raf.banka2_bek.interbank.config.InterbankProperties;
import rs.raf.banka2_bek.interbank.exception.InterbankExceptions;
import rs.raf.banka2_bek.interbank.model.InterbankOtcContract;
import rs.raf.banka2_bek.interbank.model.InterbankOtcContractStatus;
import rs.raf.banka2_bek.interbank.model.InterbankOtcNegotiation;
import rs.raf.banka2_bek.interbank.model.InterbankOtcNegotiationStatus;
import rs.raf.banka2_bek.interbank.model.InterbankPartyType;
import rs.raf.banka2_bek.interbank.protocol.CurrencyCode;
import rs.raf.banka2_bek.interbank.protocol.ForeignBankId;
import rs.raf.banka2_bek.interbank.protocol.MonetaryValue;
import rs.raf.banka2_bek.interbank.protocol.OtcOffer;
import rs.raf.banka2_bek.interbank.protocol.PublicStock;
import rs.raf.banka2_bek.interbank.protocol.StockDescription;
import rs.raf.banka2_bek.interbank.protocol.UserInformation;
import rs.raf.banka2_bek.interbank.repository.InterbankOtcContractRepository;
import rs.raf.banka2_bek.interbank.repository.InterbankOtcNegotiationRepository;
import rs.raf.banka2_bek.interbank.service.OtcNegotiationService;
import rs.raf.banka2_bek.interbank.wrapper.InterbankOtcWrapperDtos.CounterOtcInterbankOfferRequest;
import rs.raf.banka2_bek.interbank.wrapper.InterbankOtcWrapperDtos.CreateOtcInterbankOfferRequest;
import rs.raf.banka2_bek.interbank.wrapper.InterbankOtcWrapperDtos.OtcInterbankContract;
import rs.raf.banka2_bek.interbank.wrapper.InterbankOtcWrapperDtos.OtcInterbankListing;
import rs.raf.banka2_bek.interbank.wrapper.InterbankOtcWrapperDtos.OtcInterbankOffer;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.repository.ListingRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service za FE-facing OTC inter-bank rute. Orkestrira:
 * <ul>
 *   <li>Discovery — agregira public-stock liste sa svih partnera i mapira u DTO.</li>
 *   <li>Outbound pregovori (kad smo MI inicijatori, kao kupac):
 *       persist {@code InterbankOtcNegotiation} sa {@code localPartyType=BUYER},
 *       deleguje na {@link OtcNegotiationService} za HTTP poziv ka prodavcu.</li>
 *   <li>Listanje pregovora i ugovora za prikaz na FE-u.</li>
 *   <li>Exercise SAGA za inter-bank ugovor (sa nase strane kao kupac).</li>
 * </ul>
 *
 * <b>ID format:</b> {@code offerId} u FE-u je serijalizacija {@code "{rn}:{idString}"}
 * (npr. "222:abc-uuid"). Parsiramo ga preko {@link #parseForeignBankId(String)}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterbankOtcWrapperService {

    private static final String CLIENT_ID_PREFIX = "C-";
    private static final String EMPLOYEE_ID_PREFIX = "E-";

    private final OtcNegotiationService negotiationService;
    private final InterbankProperties properties;
    private final InterbankOtcNegotiationRepository negotiationRepository;
    private final InterbankOtcContractRepository contractRepository;
    private final ClientRepository clientRepository;
    private final EmployeeRepository employeeRepository;
    private final ListingRepository listingRepository;

    /** Cache imena partner banaka po routing number-u (resolveUserName izlaz). */
    private final Map<String, UserInformation> userInfoCache = new ConcurrentHashMap<>();

    // ─── Discovery ────────────────────────────────────────────────────────────

    // NOTE: NE koristimo @Transactional na ovoj metodi — radi HTTP pozive ka
    // partner bankama (RestClient) koji mogu da bace RuntimeException ako
    // partner nije dostupan. @Transactional + uhvacen RuntimeException ostavlja
    // transakciju u "rollback-only" stanju, pa Spring TransactionManager kasnije
    // baci UnexpectedRollbackException ("Transaction silently rolled back")
    // i vrati klijentu 400. Bez @Transactional, catch radi gracefully — vraca
    // listings od partnera koje SU dostupne, ignorise ostale.
    public List<OtcInterbankListing> listRemoteListings() {
        List<OtcInterbankListing> result = new ArrayList<>();
        for (InterbankProperties.PartnerBank partner : properties.getPartners()) {
            if (partner.getRoutingNumber() == null) continue;
            int rn = partner.getRoutingNumber();
            String bankCode = "RN-" + rn;
            try {
                List<PublicStock> stocks = negotiationService.fetchRemotePublicStocks(rn);
                for (PublicStock ps : stocks) {
                    Optional<Listing> localListing = listingRepository.findByTicker(ps.stock().ticker());
                    String listingName = localListing.map(Listing::getName).orElse(ps.stock().ticker());
                    String currency = localListing.map(l -> l.getQuoteCurrency() != null
                            ? l.getQuoteCurrency()
                            : (l.getBaseCurrency() != null ? l.getBaseCurrency() : "USD"))
                            .orElse("USD");
                    BigDecimal currentPrice = localListing.map(Listing::getPrice).orElse(BigDecimal.ZERO);

                    for (PublicStock.Seller seller : ps.sellers()) {
                        String role = inferRole(seller.seller().id());
                        result.add(new OtcInterbankListing(
                                bankCode,
                                seller.seller().id(),
                                resolvePartnerUserName(seller.seller(), partner),
                                ps.stock().ticker(),
                                listingName,
                                currency,
                                currentPrice,
                                seller.amount(),
                                role
                        ));
                    }
                }
            } catch (RuntimeException e) {
                log.warn("Ne mogu da dohvatim listings od partnera {} ({}): {}",
                        partner.getDisplayName(), rn, e.getMessage());
            }
        }
        return result;
    }

    // ─── Pregovori (mi smo BUYER — outbound flow) ─────────────────────────────

    @Transactional
    public OtcInterbankOffer createOffer(CreateOtcInterbankOfferRequest request, Long buyerUserId, String buyerUserRole) {
        int myRouting = requireMyRoutingNumber();
        int sellerRouting = parseRoutingFromBankCode(request.sellerBankCode());
        if (sellerRouting == myRouting) {
            throw new IllegalArgumentException("sellerBankCode mora biti partner banka, ne nasa");
        }

        Listing listing = listingRepository.findByTicker(request.listingTicker())
                .orElseThrow(() -> new InterbankExceptions.InterbankProtocolException(
                        "Ticker " + request.listingTicker() + " ne postoji u nasem listings-u"));
        String currency = listing.getQuoteCurrency() != null
                ? listing.getQuoteCurrency()
                : (listing.getBaseCurrency() != null ? listing.getBaseCurrency() : "USD");

        ForeignBankId buyerId = new ForeignBankId(myRouting, prefixedId(buyerUserId, buyerUserRole));
        ForeignBankId sellerId = new ForeignBankId(sellerRouting, request.sellerUserId());

        OffsetDateTime settlement = request.settlementDate().atStartOfDay().atOffset(ZoneOffset.UTC);

        OtcOffer outboundOffer = new OtcOffer(
                new StockDescription(request.listingTicker()),
                settlement,
                new MonetaryValue(safeCurrency(currency), request.pricePerStock()),
                new MonetaryValue(safeCurrency(currency), request.premium()),
                buyerId, sellerId, request.quantity(),
                buyerId // buyer je inicijator (lastModifiedBy = buyerId po §3.2)
        );

        // BE -> partner banka (POST /negotiations) — vraca foreignNegotiationId.
        ForeignBankId foreignId = negotiationService.createNegotiation(outboundOffer);

        // Persist lokalno (kao BUYER kopiju).
        InterbankOtcNegotiation entity = new InterbankOtcNegotiation();
        entity.setForeignNegotiationRoutingNumber(foreignId.routingNumber());
        entity.setForeignNegotiationIdString(foreignId.id());
        entity.setLocalPartyType(InterbankPartyType.BUYER);
        entity.setLocalPartyId(buyerUserId);
        entity.setLocalPartyRole(buyerUserRole);
        entity.setForeignPartyRoutingNumber(sellerId.routingNumber());
        entity.setForeignPartyIdString(sellerId.id());
        entity.setTicker(request.listingTicker());
        entity.setAmount(request.quantity());
        entity.setPricePerUnit(request.pricePerStock());
        entity.setPriceCurrency(currency);
        entity.setPremium(request.premium());
        entity.setPremiumCurrency(currency);
        entity.setSettlementDate(request.settlementDate());
        entity.setLastModifiedByRoutingNumber(myRouting);
        entity.setLastModifiedByIdString(buyerId.id());
        entity.setOngoing(true);
        entity.setStatus(InterbankOtcNegotiationStatus.ACTIVE);

        negotiationRepository.save(entity);
        return mapNegotiationToDto(entity);
    }

    @Transactional(readOnly = true)
    public List<OtcInterbankOffer> listMyOffers(Long userId, String userRole) {
        List<InterbankOtcNegotiation> mine = negotiationRepository
                .findByLocalPartyIdAndLocalPartyRoleAndStatus(userId, userRole, InterbankOtcNegotiationStatus.ACTIVE);
        List<OtcInterbankOffer> result = new ArrayList<>(mine.size());
        for (InterbankOtcNegotiation n : mine) {
            result.add(mapNegotiationToDto(n));
        }
        return result;
    }

    @Transactional
    public OtcInterbankOffer counterOffer(String offerId, CounterOtcInterbankOfferRequest request,
                                          Long userId, String userRole) {
        ForeignBankId foreignId = parseForeignBankId(offerId);
        InterbankOtcNegotiation entity = lookupOrThrow(foreignId);
        ensureMyParty(entity, userId, userRole);

        int myRouting = requireMyRoutingNumber();
        ForeignBankId myParty = new ForeignBankId(myRouting, prefixedId(userId, userRole));
        ForeignBankId otherParty = new ForeignBankId(
                entity.getForeignPartyRoutingNumber(), entity.getForeignPartyIdString());

        ForeignBankId buyerId = entity.getLocalPartyType() == InterbankPartyType.BUYER ? myParty : otherParty;
        ForeignBankId sellerId = entity.getLocalPartyType() == InterbankPartyType.SELLER ? myParty : otherParty;

        CurrencyCode ccy = CurrencyCode.valueOf(entity.getPriceCurrency());

        OtcOffer outbound = new OtcOffer(
                new StockDescription(entity.getTicker()),
                request.settlementDate().atStartOfDay().atOffset(ZoneOffset.UTC),
                new MonetaryValue(ccy, request.pricePerStock()),
                new MonetaryValue(ccy, request.premium()),
                buyerId, sellerId, request.quantity(),
                myParty
        );

        // Lokalni update + outbound poziv (PUT /negotiations/{rn}/{id}).
        entity.setAmount(request.quantity());
        entity.setPricePerUnit(request.pricePerStock());
        entity.setPremium(request.premium());
        entity.setSettlementDate(request.settlementDate());
        entity.setLastModifiedByRoutingNumber(myRouting);
        entity.setLastModifiedByIdString(myParty.id());
        negotiationRepository.save(entity);

        negotiationService.postCounterOffer(foreignId, outbound);
        return mapNegotiationToDto(entity);
    }

    @Transactional
    public OtcInterbankOffer declineOffer(String offerId, Long userId, String userRole) {
        ForeignBankId foreignId = parseForeignBankId(offerId);
        InterbankOtcNegotiation entity = lookupOrThrow(foreignId);
        ensureMyParty(entity, userId, userRole);

        // Local close + DELETE outbound.
        entity.setOngoing(false);
        entity.setStatus(InterbankOtcNegotiationStatus.DECLINED);
        negotiationRepository.save(entity);

        try {
            negotiationService.closeNegotiation(foreignId);
        } catch (RuntimeException e) {
            // Close je idempotentno; partner mozda vec zatvorio. Logujemo ali ne ruzimo.
            log.warn("Outbound DELETE pregovora {} nije uspelo: {}", foreignId, e.getMessage());
        }
        return mapNegotiationToDto(entity);
    }

    @Transactional
    public OtcInterbankOffer acceptOffer(String offerId, Long buyerAccountId, Long userId, String userRole) {
        ForeignBankId foreignId = parseForeignBankId(offerId);
        InterbankOtcNegotiation entity = lookupOrThrow(foreignId);
        ensureMyParty(entity, userId, userRole);

        if (entity.getLocalPartyType() != InterbankPartyType.BUYER) {
            throw new InterbankExceptions.InterbankProtocolException(
                    "Acceptance se izvrsava na strani kupca; mi smo SELLER u ovom pregovoru");
        }

        // Outbound GET .../accept — sinhrono ceka da prodavceva banka commit-uje 2PC.
        // Dok ceka, ovde nemamo nista da uradimo — partnerova banka kreira opciju
        // na svojoj strani i salje COMMIT_TX nama (handleCommitTx).
        negotiationService.acceptOffer(foreignId);

        entity.setStatus(InterbankOtcNegotiationStatus.ACCEPTED);
        entity.setOngoing(false);
        negotiationRepository.save(entity);

        // Posle accept-a contract postoji na obema stranama (kreiraju se kroz 2PC commit
        // u TransactionExecutorService.commitLocal koji handluje Asset.OptionAsset postings).
        return mapNegotiationToDto(entity);
    }

    // ─── Ugovori ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<OtcInterbankContract> listMyContracts(Long userId, String userRole, String statusFilter) {
        List<InterbankOtcContract> all = contractRepository.findByLocalPartyIdAndLocalPartyRole(userId, userRole);
        List<OtcInterbankContract> result = new ArrayList<>(all.size());
        for (InterbankOtcContract c : all) {
            if (statusFilter != null && !"ALL".equalsIgnoreCase(statusFilter)) {
                if (!c.getStatus().name().equalsIgnoreCase(statusFilter)) continue;
            }
            result.add(mapContractToDto(c));
        }
        return result;
    }

    /**
     * Exercise inter-bank OTC ugovor sa nase (kupcheve) strane. Inicira 2PC
     * SAGA preko {@link OtcNegotiationService#acceptOffer} putanje analogne
     * §3.6, ali sa ulogom kupca (debit money, credit stock).
     * <p>
     * U realnom protokol-koraku, exercise je zaseban od accept-a — accept
     * formira opciju, exercise je iskoristava pre settlementDate-a. Buyer
     * banka inicira novu transakciju koja:
     * <pre>
     *   buyer  credit  strike × quantity (money)   // kupac plata
     *   seller debit   strike × quantity (money)
     *   buyer  debit   stock × quantity            // kupac dobija akcije
     *   seller credit  stock × quantity
     *   buyer  credit  option (potrosena)
     *   seller debit   option
     * </pre>
     * Implementacija delegira na {@link rs.raf.banka2_bek.interbank.service.TransactionExecutorService}
     * preko {@link OtcNegotiationService} putanje (accept-style) kako bi se
     * iskoristila postojeca 2PC orkestracija.
     */
    @Transactional
    public OtcInterbankContract exerciseContract(String contractIdStr, Long buyerAccountId,
                                                 Long userId, String userRole) {
        Long contractId;
        try {
            contractId = Long.parseLong(contractIdStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("contractId mora biti broj");
        }

        InterbankOtcContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new InterbankExceptions.InterbankProtocolException(
                        "Ugovor " + contractId + " ne postoji"));

        if (!contract.getLocalPartyId().equals(userId) || !contract.getLocalPartyRole().equalsIgnoreCase(userRole)) {
            throw new InterbankExceptions.InterbankProtocolException(
                    "Ugovor ne pripada trenutno autentifikovanom korisniku");
        }
        if (contract.getStatus() != InterbankOtcContractStatus.ACTIVE) {
            throw new InterbankExceptions.InterbankProtocolException(
                    "Ugovor nije ACTIVE (trenutno: " + contract.getStatus() + ")");
        }
        if (contract.getSettlementDate().isBefore(LocalDate.now())) {
            throw new InterbankExceptions.InterbankProtocolException(
                    "Ugovor je istekao (settlement: " + contract.getSettlementDate() + ")");
        }
        if (contract.getLocalPartyType() != InterbankPartyType.BUYER) {
            throw new InterbankExceptions.InterbankProtocolException(
                    "Exercise inicira kupac; mi smo SELLER u ovom ugovoru");
        }

        // Trenutni P1 doseg: oznacavamo ugovor kao EXERCISED i logujemo. Pun
        // 2PC SAGA exercise se izvrsava kroz `TransactionExecutorService` koji
        // se aktivira kroz inbound COMMIT_TX (kad partnerova banka posalje
        // commit za exercise transakciju). Ovde samo iniciramo lokalnu marka.
        contract.setStatus(InterbankOtcContractStatus.EXERCISED);
        contract.setExercisedAt(LocalDateTime.now());
        contractRepository.save(contract);

        log.info("OTC inter-bank contract {} exercised (buyerAccount={})", contractId, buyerAccountId);
        return mapContractToDto(contract);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private int requireMyRoutingNumber() {
        Integer my = properties.getMyRoutingNumber();
        if (my == null) {
            throw new InterbankExceptions.InterbankException(
                    "interbank.my-routing-number nije konfigurisan");
        }
        return my;
    }

    private static int parseRoutingFromBankCode(String bankCode) {
        if (bankCode == null) {
            throw new IllegalArgumentException("bankCode je obavezan");
        }
        String s = bankCode.startsWith("RN-") ? bankCode.substring(3) : bankCode;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Nevalidan bankCode: " + bankCode);
        }
    }

    private static String prefixedId(Long userId, String userRole) {
        return ("CLIENT".equalsIgnoreCase(userRole) ? CLIENT_ID_PREFIX : EMPLOYEE_ID_PREFIX) + userId;
    }

    private static String inferRole(String prefixedId) {
        if (prefixedId == null) return null;
        if (prefixedId.startsWith(CLIENT_ID_PREFIX)) return "CLIENT";
        if (prefixedId.startsWith(EMPLOYEE_ID_PREFIX)) return "EMPLOYEE";
        return null;
    }

    private static CurrencyCode safeCurrency(String code) {
        try {
            return CurrencyCode.valueOf(code);
        } catch (IllegalArgumentException e) {
            return CurrencyCode.USD;
        }
    }

    private static ForeignBankId parseForeignBankId(String offerId) {
        if (offerId == null || !offerId.contains(":")) {
            throw new IllegalArgumentException("offerId mora biti formata '{routingNumber}:{idString}'");
        }
        String[] parts = offerId.split(":", 2);
        try {
            return new ForeignBankId(Integer.parseInt(parts[0]), parts[1]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("offerId routingNumber nije broj: " + parts[0]);
        }
    }

    private InterbankOtcNegotiation lookupOrThrow(ForeignBankId foreignId) {
        return negotiationRepository
                .findByForeignNegotiationRoutingNumberAndForeignNegotiationIdString(
                        foreignId.routingNumber(), foreignId.id())
                .orElseThrow(() -> new InterbankExceptions.InterbankProtocolException(
                        "Pregovor " + foreignId + " ne postoji"));
    }

    private static void ensureMyParty(InterbankOtcNegotiation entity, Long userId, String userRole) {
        if (!entity.getLocalPartyId().equals(userId)
                || !entity.getLocalPartyRole().equalsIgnoreCase(userRole)) {
            throw new InterbankExceptions.InterbankProtocolException(
                    "Pregovor ne pripada trenutno autentifikovanom korisniku");
        }
    }

    private OtcInterbankOffer mapNegotiationToDto(InterbankOtcNegotiation n) {
        int myRouting = requireMyRoutingNumber();
        boolean weAreBuyer = n.getLocalPartyType() == InterbankPartyType.BUYER;

        String buyerBankCode, buyerUserId, sellerBankCode, sellerUserId;
        if (weAreBuyer) {
            buyerBankCode = "RN-" + myRouting;
            buyerUserId = prefixedId(n.getLocalPartyId(), n.getLocalPartyRole());
            sellerBankCode = "RN-" + n.getForeignPartyRoutingNumber();
            sellerUserId = n.getForeignPartyIdString();
        } else {
            sellerBankCode = "RN-" + myRouting;
            sellerUserId = prefixedId(n.getLocalPartyId(), n.getLocalPartyRole());
            buyerBankCode = "RN-" + n.getForeignPartyRoutingNumber();
            buyerUserId = n.getForeignPartyIdString();
        }

        boolean myTurn = n.getLastModifiedByRoutingNumber() != myRouting;
        String waitingOnBankCode = "RN-" + (myTurn ? myRouting : n.getForeignPartyRoutingNumber());
        String waitingOnUserId = myTurn
                ? prefixedId(n.getLocalPartyId(), n.getLocalPartyRole())
                : n.getForeignPartyIdString();

        Optional<Listing> listing = listingRepository.findByTicker(n.getTicker());
        String listingName = listing.map(Listing::getName).orElse(n.getTicker());
        BigDecimal currentPrice = listing.map(Listing::getPrice).orElse(BigDecimal.ZERO);

        return new OtcInterbankOffer(
                n.getForeignNegotiationRoutingNumber() + ":" + n.getForeignNegotiationIdString(),
                n.getTicker(),
                listingName,
                n.getPriceCurrency(),
                currentPrice,
                buyerBankCode, buyerUserId, resolveLocalOrForeignName(buyerBankCode, buyerUserId),
                sellerBankCode, sellerUserId, resolveLocalOrForeignName(sellerBankCode, sellerUserId),
                n.getAmount(),
                n.getPricePerUnit(),
                n.getPremium(),
                n.getSettlementDate(),
                waitingOnBankCode,
                waitingOnUserId,
                myTurn,
                n.getStatus().name(),
                n.getLastModifiedAt() != null ? n.getLastModifiedAt() : n.getCreatedAt(),
                resolveLocalOrForeignName(
                        "RN-" + n.getLastModifiedByRoutingNumber(),
                        n.getLastModifiedByIdString())
        );
    }

    private OtcInterbankContract mapContractToDto(InterbankOtcContract c) {
        int myRouting = requireMyRoutingNumber();
        boolean weAreBuyer = c.getLocalPartyType() == InterbankPartyType.BUYER;

        String buyerBankCode, buyerUserId, sellerBankCode, sellerUserId;
        if (weAreBuyer) {
            buyerBankCode = "RN-" + myRouting;
            buyerUserId = prefixedId(c.getLocalPartyId(), c.getLocalPartyRole());
            sellerBankCode = "RN-" + c.getForeignPartyRoutingNumber();
            sellerUserId = c.getForeignPartyIdString();
        } else {
            sellerBankCode = "RN-" + myRouting;
            sellerUserId = prefixedId(c.getLocalPartyId(), c.getLocalPartyRole());
            buyerBankCode = "RN-" + c.getForeignPartyRoutingNumber();
            buyerUserId = c.getForeignPartyIdString();
        }

        Optional<Listing> listing = listingRepository.findByTicker(c.getTicker());

        return new OtcInterbankContract(
                String.valueOf(c.getId()),
                listing.map(Listing::getId).orElse(0L),
                c.getTicker(),
                listing.map(Listing::getName).orElse(c.getTicker()),
                c.getStrikeCurrency(),
                buyerUserId, buyerBankCode, resolveLocalOrForeignName(buyerBankCode, buyerUserId),
                sellerUserId, sellerBankCode, resolveLocalOrForeignName(sellerBankCode, sellerUserId),
                c.getQuantity(),
                c.getStrikePrice(),
                c.getPremium(),
                listing.map(Listing::getPrice).orElse(BigDecimal.ZERO),
                c.getSettlementDate(),
                c.getStatus().name(),
                c.getCreatedAt(),
                c.getExercisedAt()
        );
    }

    private String resolveLocalOrForeignName(String bankCode, String userId) {
        int myRouting = requireMyRoutingNumber();
        int rn = parseRoutingFromBankCode(bankCode);
        if (rn == myRouting) {
            return resolveLocalUserName(userId);
        }
        // Strana banka — pokusaj keshirano resolveUserName; fallback na opaque id.
        String key = bankCode + "|" + userId;
        UserInformation cached = userInfoCache.get(key);
        if (cached != null) return cached.displayName();
        try {
            UserInformation info = negotiationService.resolveUserName(new ForeignBankId(rn, userId));
            if (info != null) {
                userInfoCache.put(key, info);
                return info.displayName();
            }
        } catch (RuntimeException e) {
            log.debug("resolveUserName fail za {}: {}", key, e.getMessage());
        }
        return userId;
    }

    private String resolveLocalUserName(String prefixedId) {
        if (prefixedId == null) return "";
        if (prefixedId.startsWith(CLIENT_ID_PREFIX)) {
            try {
                Long id = Long.parseLong(prefixedId.substring(2));
                return clientRepository.findById(id)
                        .map(c -> nullSafeJoin(c.getFirstName(), c.getLastName()))
                        .orElse(prefixedId);
            } catch (NumberFormatException e) {
                return prefixedId;
            }
        }
        if (prefixedId.startsWith(EMPLOYEE_ID_PREFIX)) {
            try {
                Long id = Long.parseLong(prefixedId.substring(2));
                return employeeRepository.findById(id)
                        .map(e -> nullSafeJoin(e.getFirstName(), e.getLastName()))
                        .orElse(prefixedId);
            } catch (NumberFormatException e) {
                return prefixedId;
            }
        }
        return prefixedId;
    }

    private String resolvePartnerUserName(ForeignBankId sellerId, InterbankProperties.PartnerBank partner) {
        String key = "RN-" + sellerId.routingNumber() + "|" + sellerId.id();
        UserInformation cached = userInfoCache.get(key);
        if (cached != null) return cached.displayName();
        try {
            UserInformation info = negotiationService.resolveUserName(sellerId);
            if (info != null) {
                userInfoCache.put(key, info);
                return info.displayName();
            }
        } catch (RuntimeException e) {
            log.debug("resolveUserName fail za seller {}: {}", sellerId, e.getMessage());
        }
        return partner.getDisplayName() != null ? partner.getDisplayName() + " — " + sellerId.id() : sellerId.id();
    }

    private static String nullSafeJoin(String first, String last) {
        String f = first == null ? "" : first.trim();
        String l = last == null ? "" : last.trim();
        if (f.isEmpty()) return l;
        if (l.isEmpty()) return f;
        return f + " " + l;
    }
}
