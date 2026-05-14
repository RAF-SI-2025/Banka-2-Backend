package rs.raf.banka2_bek.interbank.service;

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
import rs.raf.banka2_bek.interbank.protocol.Asset;
import rs.raf.banka2_bek.interbank.protocol.CurrencyCode;
import rs.raf.banka2_bek.interbank.protocol.ForeignBankId;
import rs.raf.banka2_bek.interbank.protocol.MonetaryAsset;
import rs.raf.banka2_bek.interbank.protocol.MonetaryValue;
import rs.raf.banka2_bek.interbank.protocol.OptionDescription;
import rs.raf.banka2_bek.interbank.protocol.OtcNegotiation;
import rs.raf.banka2_bek.interbank.protocol.OtcOffer;
import rs.raf.banka2_bek.interbank.protocol.Posting;
import rs.raf.banka2_bek.interbank.protocol.PublicStock;
import rs.raf.banka2_bek.interbank.protocol.StockDescription;
import rs.raf.banka2_bek.interbank.protocol.Transaction;
import rs.raf.banka2_bek.interbank.protocol.TxAccount;
import rs.raf.banka2_bek.interbank.protocol.UserInformation;
import rs.raf.banka2_bek.interbank.repository.InterbankOtcContractRepository;
import rs.raf.banka2_bek.interbank.repository.InterbankOtcNegotiationRepository;
import rs.raf.banka2_bek.portfolio.model.Portfolio;
import rs.raf.banka2_bek.portfolio.repository.PortfolioRepository;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Inter-bank OTC pregovor servis (protokol §3, Celina 5 (Nova) — OTC trgovina).
 * <p>
 * Implementira oba smera komunikacije:
 * <ul>
 *   <li><b>Outbound</b> — kad je nas korisnik inicijator (kupac salje ponudu prodavcu
 *       u partner banci, ili prodavac salje counter-offer kupcu u partner banci).
 *       Outbound metode (T2/T5) deleguju na {@link InterbankClient}.</li>
 *   <li><b>Inbound</b> (T3) — kad partner banka kontaktira nas. Mi smo prodavac
 *       (autoritativni vlasnik pregovora) ili kupac kojem se nudi ponuda. Inbound
 *       metode persistiraju u {@link InterbankOtcNegotiation} entitetu i pri
 *       prihvatanju kreiraju {@link InterbankOtcContract} kroz 2PC sa
 *       {@link TransactionExecutorService}-om.</li>
 * </ul>
 * <p>
 * <b>Konvencija za seller/buyer ID:</b> sopstveni id-evi koji idu u
 * {@link ForeignBankId#id()} koriste prefiks {@code C-} za klijente i
 * {@code E-} za zaposlene. Partner banka tretira string kao opaque (po
 * §2.3) ali konvencija nam dozvoljava da kasnije razresimo strane preko
 * {@link #serveUserInfo(String)}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtcNegotiationService {

    /** TTL za §3.1 outbound cache. */
    static final Duration PUBLIC_STOCK_TTL = Duration.ofMinutes(5);

    private static final String CLIENT_ID_PREFIX = "C-";
    private static final String EMPLOYEE_ID_PREFIX = "E-";

    private final InterbankClient client;
    private final InterbankProperties properties;
    private final InterbankOtcNegotiationRepository negotiationRepository;
    private final InterbankOtcContractRepository contractRepository;
    private final PortfolioRepository portfolioRepository;
    private final ClientRepository clientRepository;
    private final EmployeeRepository employeeRepository;
    private final TransactionExecutorService transactionExecutor;

    /** Cache po routing number-u partnerske banke. ConcurrentHashMap (low contention). */
    private final Map<Integer, CachedPublicStocks> publicStockCache = new ConcurrentHashMap<>();

    // ────────────────────────── outbound (T2 / T5) ────────────────────────────

    @Transactional
    public List<PublicStock> fetchRemotePublicStocks(int routingNumber) {
        Instant now = Instant.now();
        CachedPublicStocks cached = publicStockCache.get(routingNumber);
        if (cached != null && cached.isFresh(now)) {
            return cached.stocks();
        }
        List<PublicStock> stocks = client.fetchPublicStocks(routingNumber);
        publicStockCache.put(routingNumber, new CachedPublicStocks(stocks, now));
        return stocks;
    }

    @Transactional
    public ForeignBankId createNegotiation(OtcOffer offer) {
        validateOutboundOffer(offer);
        if (!offer.buyerId().equals(offer.lastModifiedBy())) {
            throw new IllegalArgumentException(
                    "Pri kreiranju pregovora lastModifiedBy mora biti buyerId.");
        }
        int sellerRouting = offer.sellerId().routingNumber();
        ForeignBankId negotiationId = client.postNegotiation(sellerRouting, offer);
        publicStockCache.remove(sellerRouting);
        log.info("OTC outbound: created negotiation {} at bank {}", negotiationId, sellerRouting);
        return negotiationId;
    }

    @Transactional
    public void postCounterOffer(ForeignBankId negotiationId, OtcOffer updated) {
        if (negotiationId == null) throw new IllegalArgumentException("negotiationId ne sme biti null");
        validateOutboundOffer(updated);
        ensureLocalParty(updated.lastModifiedBy(),
                "Counter-offer mora biti potpisan od strane korisnika nase banke");
        client.putCounterOffer(negotiationId, updated);
        log.info("OTC outbound: counter-offered negotiation {} (lastModifiedBy={})",
                negotiationId, updated.lastModifiedBy());
    }

    @Transactional
    public OtcNegotiation readNegotiation(ForeignBankId negotiationId) {
        if (negotiationId == null) throw new IllegalArgumentException("negotiationId ne sme biti null");
        return client.getNegotiation(negotiationId);
    }

    @Transactional
    public void closeNegotiation(ForeignBankId negotiationId) {
        if (negotiationId == null) throw new IllegalArgumentException("negotiationId ne sme biti null");
        client.deleteNegotiation(negotiationId);
        log.info("OTC outbound: closed negotiation {}", negotiationId);
    }

    @Transactional
    public void acceptOffer(ForeignBankId negotiationId) {
        if (negotiationId == null) throw new IllegalArgumentException("negotiationId ne sme biti null");
        client.acceptNegotiation(negotiationId);
        publicStockCache.remove(negotiationId.routingNumber());
        log.info("OTC outbound: accepted negotiation {}", negotiationId);
    }

    /**
     * Outbound §3.7 — graceful fallback ako partner banka vrati 404.
     * UI moze da prikaze opaque id umesto imena ako nema friendly mapiranja.
     */
    @Transactional
    public UserInformation resolveUserName(ForeignBankId userId) {
        if (userId == null) throw new IllegalArgumentException("userId ne sme biti null");
        try {
            return client.getUserInfo(userId);
        } catch (InterbankExceptions.InterbankUserNotFoundException notFound) {
            log.debug("Partner bank {} doesn't have user {}: {}",
                    userId.routingNumber(), userId.id(), notFound.getMessage());
            return new UserInformation("Banka " + userId.routingNumber(), userId.id());
        }
    }

    // ────────────────────────── inbound (T3) ──────────────────────────────────

    /**
     * §3.1 — vrati javne akcije za sve nase korisnike (klijenti + zaposleni).
     * Group-by ticker + lista (seller, amount). Filtriranje po roli (klijent
     * vidi klijente, aktuar vidi aktuare) radi kupceva banka — mi vracamo sve
     * jer ne znamo ko nas je query-jevao (X-Api-Key autentifikuje BANKU, ne user-a).
     * Zato kodiramo role u id-u (`C-{id}` ili `E-{id}`).
     */
    @Transactional(readOnly = true)
    public List<PublicStock> serveLocalPublicStocks() {
        int myRouting = requireMyRoutingNumber();

        // Iteriramo kroz sve portfolios sa publicQuantity > 0 i grupisemo po ticker-u.
        // Quantity koja se nudi je publicQuantity umanjen za one sto su vec rezervisani
        // u ACTIVE pregovorima (gde smo MI seller).
        Map<String, List<PublicStock.Seller>> byTicker = new HashMap<>();
        Map<String, StockDescription> stockByTicker = new HashMap<>();

        for (Portfolio p : portfolioRepository.findAll()) {
            int publicQty = p.getPublicQuantity() == null ? 0 : p.getPublicQuantity();
            if (publicQty <= 0) continue;

            BigDecimal reservedInActiveNegotiations = nullToZero(
                    negotiationRepository.sumActiveAmountForSellerAndTicker(
                            p.getUserId(), p.getUserRole(), p.getListingTicker()));
            BigDecimal available = BigDecimal.valueOf(publicQty).subtract(reservedInActiveNegotiations);
            if (available.signum() <= 0) continue;

            String prefixed = ("CLIENT".equalsIgnoreCase(p.getUserRole())
                    ? CLIENT_ID_PREFIX : EMPLOYEE_ID_PREFIX) + p.getUserId();
            ForeignBankId sellerId = new ForeignBankId(myRouting, prefixed);

            byTicker
                    .computeIfAbsent(p.getListingTicker(), k -> new ArrayList<>())
                    .add(new PublicStock.Seller(sellerId, available));
            stockByTicker.putIfAbsent(p.getListingTicker(), new StockDescription(p.getListingTicker()));
        }

        List<PublicStock> result = new ArrayList<>(byTicker.size());
        for (Map.Entry<String, List<PublicStock.Seller>> e : byTicker.entrySet()) {
            result.add(new PublicStock(stockByTicker.get(e.getKey()), e.getValue()));
        }
        return result;
    }

    /**
     * §3.2 — partner banka inicira pregovor (kupac u partner banci, prodavac
     * mi). Validacija sellerId, kreiranje lokalnog entiteta, vracanje
     * ForeignBankId{nasRouting, generisaniId} kupcu.
     */
    @Transactional
    public ForeignBankId acceptCreatedNegotiation(OtcOffer offer) {
        if (offer == null) throw new IllegalArgumentException("offer ne sme biti null");
        int myRouting = requireMyRoutingNumber();

        if (offer.sellerId() == null || offer.sellerId().routingNumber() != myRouting) {
            throw new IllegalArgumentException(
                    "Mi nismo autoritativna banka za sellerId — sellerId.routingNumber mora biti nas (" + myRouting + ")");
        }
        if (offer.buyerId() == null || offer.buyerId().routingNumber() == myRouting) {
            throw new IllegalArgumentException(
                    "buyerId mora biti iz partner banke (ne nase " + myRouting + ")");
        }
        if (offer.lastModifiedBy() == null || !offer.lastModifiedBy().equals(offer.buyerId())) {
            throw new IllegalArgumentException(
                    "Pri kreiranju pregovora lastModifiedBy mora biti buyerId.");
        }
        if (offer.stock() == null || offer.stock().ticker() == null) {
            throw new IllegalArgumentException("offer.stock.ticker je obavezan");
        }
        if (offer.amount() == null || offer.amount().signum() <= 0) {
            throw new IllegalArgumentException("amount mora biti > 0");
        }
        if (offer.pricePerUnit() == null || offer.pricePerUnit().amount() == null
                || offer.pricePerUnit().amount().signum() <= 0) {
            throw new IllegalArgumentException("pricePerUnit mora biti > 0");
        }
        if (offer.premium() == null || offer.premium().amount() == null
                || offer.premium().amount().signum() < 0) {
            throw new IllegalArgumentException("premium ne moze biti negativan");
        }
        if (offer.settlementDate() == null
                || !offer.settlementDate().isAfter(OffsetDateTime.now())) {
            throw new IllegalArgumentException("settlementDate mora biti u buducnosti");
        }

        // Resolve lokalnog seller-a (po prefiks konvenciji).
        LocalParty seller = parseLocalPartyId(offer.sellerId().id());

        // Kvota provera: seller mora imati dovoljno publicQuantity-a
        // (umanjeno za ACTIVE pregovore + ACTIVE ugovore).
        int sellerPublic = portfolioRepository
                .findByUserIdAndUserRole(seller.userId(), seller.role())
                .stream()
                .filter(p -> p.getListingTicker().equals(offer.stock().ticker()))
                .mapToInt(p -> p.getPublicQuantity() == null ? 0 : p.getPublicQuantity())
                .sum();
        BigDecimal alreadyReserved = nullToZero(
                negotiationRepository.sumActiveAmountForSellerAndTicker(
                        seller.userId(), seller.role(), offer.stock().ticker()));
        BigDecimal available = BigDecimal.valueOf(sellerPublic).subtract(alreadyReserved);
        if (available.compareTo(offer.amount()) < 0) {
            throw new InterbankExceptions.InterbankProtocolException(
                    "Seller nema dovoljno javnih akcija ("
                            + offer.stock().ticker() + " trazeno=" + offer.amount()
                            + ", raspolozivo=" + available + ")");
        }

        // Kreiraj entitet.
        InterbankOtcNegotiation entity = new InterbankOtcNegotiation();
        String generatedId = UUID.randomUUID().toString();
        entity.setForeignNegotiationRoutingNumber(myRouting);
        entity.setForeignNegotiationIdString(generatedId);
        entity.setLocalPartyType(InterbankPartyType.SELLER);
        entity.setLocalPartyId(seller.userId());
        entity.setLocalPartyRole(seller.role());
        entity.setForeignPartyRoutingNumber(offer.buyerId().routingNumber());
        entity.setForeignPartyIdString(offer.buyerId().id());
        entity.setTicker(offer.stock().ticker());
        entity.setAmount(offer.amount());
        entity.setPricePerUnit(offer.pricePerUnit().amount());
        entity.setPriceCurrency(offer.pricePerUnit().currency().name());
        entity.setPremium(offer.premium().amount());
        entity.setPremiumCurrency(offer.premium().currency().name());
        entity.setSettlementDate(offer.settlementDate().toLocalDate());
        entity.setLastModifiedByRoutingNumber(offer.lastModifiedBy().routingNumber());
        entity.setLastModifiedByIdString(offer.lastModifiedBy().id());
        entity.setOngoing(true);
        entity.setStatus(InterbankOtcNegotiationStatus.ACTIVE);

        negotiationRepository.save(entity);

        ForeignBankId result = new ForeignBankId(myRouting, generatedId);
        log.info("OTC inbound: created negotiation {} (seller={}, buyer={})",
                result, offer.sellerId(), offer.buyerId());
        return result;
    }

    /**
     * §3.3 — counter-offer od partner banke. Provera turn-a + update polja.
     */
    @Transactional
    public void receiveCounterOffer(ForeignBankId negotiationId, OtcOffer updated) {
        if (negotiationId == null) throw new IllegalArgumentException("negotiationId ne sme biti null");
        if (updated == null) throw new IllegalArgumentException("updated ne sme biti null");

        InterbankOtcNegotiation entity = lookupByNegotiationId(negotiationId);

        // §3.3 — zatvoreni pregovor: 409 Conflict (ne 400, nije malformed).
        if (!entity.isOngoing() || entity.getStatus() != InterbankOtcNegotiationStatus.ACTIVE) {
            throw new InterbankExceptions.InterbankNegotiationConflictException(
                    "Pregovor " + negotiationId + " nije aktivan (status=" + entity.getStatus() + ")");
        }

        // Pravilo turne (§3.3): turn je strana cije lastModifiedBy != lastModifiedBy entiteta.
        // Caller mora biti suprotna strana od one koja je poslednja izmenila.
        if (updated.lastModifiedBy() == null) {
            throw new IllegalArgumentException("updated.lastModifiedBy mora biti postavljen");
        }
        if (updated.lastModifiedBy().routingNumber() == entity.getLastModifiedByRoutingNumber()
                && updated.lastModifiedBy().id().equals(entity.getLastModifiedByIdString())) {
            // §3.3 — turn violation: 409 Conflict.
            throw new InterbankExceptions.InterbankNegotiationConflictException(
                    "Nije turn pozivaoca — pregovor je poslednje izmenjen od strane "
                            + entity.getLastModifiedByRoutingNumber() + ":"
                            + entity.getLastModifiedByIdString());
        }

        // Caller mora biti jedna od strana (buyer ili seller).
        ForeignBankId localPartyAsForeign = new ForeignBankId(
                requireMyRoutingNumber(),
                (entity.getLocalPartyType() == InterbankPartyType.SELLER
                        ? toForeignIdString(entity.getLocalPartyId(), entity.getLocalPartyRole()) : null));
        ForeignBankId foreignPartyAsForeign = new ForeignBankId(
                entity.getForeignPartyRoutingNumber(), entity.getForeignPartyIdString());

        boolean callerIsLocal = entity.getLocalPartyType() == InterbankPartyType.SELLER
                && updated.lastModifiedBy().equals(localPartyAsForeign);
        boolean callerIsForeign = updated.lastModifiedBy().equals(foreignPartyAsForeign);

        if (!callerIsLocal && !callerIsForeign) {
            throw new IllegalArgumentException(
                    "lastModifiedBy mora biti buyer ili seller iz pregovora");
        }

        // Update polja koja mogu da se menjaju (po §3.3: amount, pricePerUnit, premium, settlementDate).
        if (updated.amount() != null && updated.amount().signum() > 0) {
            entity.setAmount(updated.amount());
        }
        if (updated.pricePerUnit() != null && updated.pricePerUnit().amount() != null) {
            entity.setPricePerUnit(updated.pricePerUnit().amount());
            entity.setPriceCurrency(updated.pricePerUnit().currency().name());
        }
        if (updated.premium() != null && updated.premium().amount() != null) {
            entity.setPremium(updated.premium().amount());
            entity.setPremiumCurrency(updated.premium().currency().name());
        }
        if (updated.settlementDate() != null) {
            entity.setSettlementDate(updated.settlementDate().toLocalDate());
        }
        entity.setLastModifiedByRoutingNumber(updated.lastModifiedBy().routingNumber());
        entity.setLastModifiedByIdString(updated.lastModifiedBy().id());

        negotiationRepository.save(entity);
        log.info("OTC inbound: counter-offer applied on negotiation {}", negotiationId);
    }

    /**
     * §3.4 — vrati trenutno stanje pregovora kao OtcNegotiation (offer + isOngoing).
     */
    @Transactional(readOnly = true)
    public OtcNegotiation getNegotiation(ForeignBankId negotiationId) {
        if (negotiationId == null) throw new IllegalArgumentException("negotiationId ne sme biti null");
        InterbankOtcNegotiation entity = lookupByNegotiationId(negotiationId);
        return mapToProtocol(entity);
    }

    /**
     * §3.5 — bilo koja strana zatvara pregovor. isOngoing=false, status=CLOSED.
     * Idempotentno: ponovo DELETE je no-op.
     */
    @Transactional
    public void closeReceivedNegotiation(ForeignBankId negotiationId) {
        if (negotiationId == null) throw new IllegalArgumentException("negotiationId ne sme biti null");

        Optional<InterbankOtcNegotiation> opt = negotiationRepository
                .findByForeignNegotiationRoutingNumberAndForeignNegotiationIdString(
                        negotiationId.routingNumber(), negotiationId.id());
        if (opt.isEmpty()) return; // idempotent — nema entiteta, nema sta da zatvorimo

        InterbankOtcNegotiation entity = opt.get();
        if (!entity.isOngoing()) return; // vec zatvoren

        entity.setOngoing(false);
        entity.setStatus(InterbankOtcNegotiationStatus.CLOSED);
        negotiationRepository.save(entity);
        log.info("OTC inbound: closed negotiation {}", negotiationId);
    }

    /**
     * §3.6 — kupac (foreign) prihvata ponudu. Mi smo seller (autoritativni).
     * Forma transakciju (§3.6 4-posting tabela), ubaci je u
     * {@link TransactionExecutorService}, sacekaj COMMITTED, kreiraj
     * {@link InterbankOtcContract}, postavi pregovor na ACCEPTED + ongoing=false.
     * <p>
     * Po §3.6 transakcija ima 4 postinga (BigDecimal sign konvencija u nasim
     * postingima: pozitivno = debit; negativno = kredit):
     * <pre>
     *   buyer  debit  premium       (foreign account, pseudo)
     *   seller credit premium       (lokalni racun seller-a u premium valuti)
     *   buyer  debit  optionContract (foreign — buyer dobija opciju)
     *   seller credit optionContract (Option pseudo-account kod nas)
     * </pre>
     * <p>
     * Premium se prebacuje sa kupca (foreign) na seller-ov racun u nasoj
     * banci. Opcioni asset se kreira na pseudo-account-u Option kod nas
     * (autoritativni vlasnik) i debituje kupcevoj strani — predstavlja njegovo
     * pravo na exercise.
     */
    @Transactional
    public void acceptReceivedNegotiation(ForeignBankId negotiationId) {
        if (negotiationId == null) throw new IllegalArgumentException("negotiationId ne sme biti null");

        InterbankOtcNegotiation entity = lookupByNegotiationId(negotiationId);
        if (!entity.isOngoing() || entity.getStatus() != InterbankOtcNegotiationStatus.ACTIVE) {
            throw new InterbankExceptions.InterbankProtocolException(
                    "Pregovor " + negotiationId + " nije aktivan (status=" + entity.getStatus() + ")");
        }
        if (entity.getLocalPartyType() != InterbankPartyType.SELLER) {
            throw new InterbankExceptions.InterbankProtocolException(
                    "Mi nismo seller u ovom pregovoru — accept moze samo na sellerovoj banci");
        }
        // §3.6 — settlementDate validacija. Spec koristi OffsetDateTime; mi
        // persistujemo kao LocalDate (start-of-day UTC). Poredimo UTC trenutni
        // datum da izbegnemo edge case sa razlikom timezone-a izmedju banki.
        if (entity.getSettlementDate().isBefore(LocalDate.now(ZoneOffset.UTC))) {
            throw new InterbankExceptions.InterbankNegotiationConflictException(
                    "settlementDate je prosao — pregovor je istekao");
        }

        ForeignBankId myParty = new ForeignBankId(requireMyRoutingNumber(),
                toForeignIdString(entity.getLocalPartyId(), entity.getLocalPartyRole()));
        ForeignBankId buyerParty = new ForeignBankId(
                entity.getForeignPartyRoutingNumber(), entity.getForeignPartyIdString());

        // Forma 4-posting transakciju (§3.6).
        // Premium: kupac (foreign) credit, seller (local) debit.
        CurrencyCode premiumCcy = CurrencyCode.valueOf(entity.getPremiumCurrency());
        Asset premiumAsset = new Asset.Monas(new MonetaryAsset(premiumCcy));

        // Seller side — mora se odabrati seller-ov RSD/strani racun u premium valuti.
        // Po Celini 5: prebacujemo u seller-ov racun matchovan po valuti.
        String sellerAccountNumber = resolveLocalAccount(entity.getLocalPartyId(),
                entity.getLocalPartyRole(), premiumCcy.name())
                .orElseThrow(() -> new InterbankExceptions.InterbankProtocolException(
                        "Seller nema racun u valuti " + premiumCcy.name()));

        // Option asset.
        OptionDescription optDesc = new OptionDescription(
                negotiationId,
                new StockDescription(entity.getTicker()),
                new MonetaryValue(CurrencyCode.valueOf(entity.getPriceCurrency()), entity.getPricePerUnit()),
                entity.getSettlementDate().atStartOfDay().atOffset(ZoneOffset.UTC),
                entity.getAmount()
        );
        Asset optionAsset = new Asset.OptionAsset(optDesc);

        BigDecimal premium = entity.getPremium();

        // Postings: pozitivno = debit (povecava sredstva), negativno = kredit (smanjuje).
        Posting p1 = new Posting(new TxAccount.Person(buyerParty), premium.negate(), premiumAsset);
        Posting p2 = new Posting(new TxAccount.Account(sellerAccountNumber), premium, premiumAsset);
        Posting p3 = new Posting(new TxAccount.Person(buyerParty), entity.getAmount(), optionAsset);
        Posting p4 = new Posting(new TxAccount.Option(negotiationId), entity.getAmount().negate(), optionAsset);

        Transaction tx = transactionExecutor.formTransaction(
                List.of(p1, p2, p3, p4),
                "OTC accept negotiation " + negotiationId,
                null, "OTC", "Premium za opcioni ugovor"
        );

        // Pre execute: kreiraj contract entitet (pre 2PC tako da exercise post-commit
        // moze da ga nadje preko sourceNegotiationId).
        InterbankOtcContract contract = new InterbankOtcContract();
        contract.setSourceNegotiationId(entity.getId());
        contract.setLocalPartyType(InterbankPartyType.SELLER);
        contract.setLocalPartyId(entity.getLocalPartyId());
        contract.setLocalPartyRole(entity.getLocalPartyRole());
        contract.setForeignPartyRoutingNumber(entity.getForeignPartyRoutingNumber());
        contract.setForeignPartyIdString(entity.getForeignPartyIdString());
        contract.setTicker(entity.getTicker());
        contract.setQuantity(entity.getAmount());
        contract.setStrikePrice(entity.getPricePerUnit());
        contract.setStrikeCurrency(entity.getPriceCurrency());
        contract.setPremium(entity.getPremium());
        contract.setPremiumCurrency(entity.getPremiumCurrency());
        contract.setSettlementDate(entity.getSettlementDate());
        contract.setStatus(InterbankOtcContractStatus.ACTIVE);
        contract.setCreatedAt(LocalDateTime.now());
        contractRepository.save(contract);

        // Pregovor markiraj kao ACCEPTED prvo (sinhrono, pre 2PC izvrsenja).
        entity.setStatus(InterbankOtcNegotiationStatus.ACCEPTED);
        entity.setOngoing(false);
        entity.setLastModifiedByRoutingNumber(myParty.routingNumber());
        entity.setLastModifiedByIdString(myParty.id());
        negotiationRepository.save(entity);

        // Izvrsi 2PC. Ako se ne izvrsi (NO glas), izuzetak ce se propagirati gore
        // i Spring rollback-uje ceo @Transactional — pregovor se vraca u ACTIVE.
        try {
            transactionExecutor.execute(tx);
        } catch (RuntimeException e) {
            // Defenzivno — bilo koja greska 2PC-a vraca stanje.
            entity.setStatus(InterbankOtcNegotiationStatus.ACTIVE);
            entity.setOngoing(true);
            negotiationRepository.save(entity);
            contractRepository.delete(contract);
            throw e;
        }

        log.info("OTC inbound: accepted negotiation {} -> contract {}", negotiationId, contract.getId());
    }

    /**
     * §3.7 — vrati friendly ime za nas lokalni id (prefix-encoded "C-" / "E-").
     * Caller je partner banka koja prikazuje user info u svom UI-u.
     */
    @Transactional(readOnly = true)
    public UserInformation serveUserInfo(String localUserId) {
        if (localUserId == null || localUserId.isBlank()) {
            throw new InterbankExceptions.InterbankUserNotFoundException("localUserId je prazan");
        }

        LocalParty p;
        try {
            p = parseLocalPartyId(localUserId);
        } catch (IllegalArgumentException malformed) {
            // §3.7 — opaque ID koji ne razumemo se tretira kao "not found", ne kao 400.
            throw new InterbankExceptions.InterbankUserNotFoundException(
                    "Nepoznat id format: " + localUserId);
        }
        String myDisplay = properties.getMyBankDisplayName();
        if (myDisplay == null || myDisplay.isBlank()) myDisplay = "Banka 2";

        if ("CLIENT".equals(p.role())) {
            Optional<Client> cOpt = clientRepository.findById(p.userId());
            if (cOpt.isEmpty()) {
                throw new InterbankExceptions.InterbankUserNotFoundException(
                        "Klijent " + p.userId() + " ne postoji");
            }
            Client c = cOpt.get();
            return new UserInformation(myDisplay,
                    nullSafeJoin(c.getFirstName(), c.getLastName()));
        } else {
            Optional<Employee> eOpt = employeeRepository.findById(p.userId());
            if (eOpt.isEmpty()) {
                throw new InterbankExceptions.InterbankUserNotFoundException(
                        "Zaposleni " + p.userId() + " ne postoji");
            }
            Employee e = eOpt.get();
            return new UserInformation(myDisplay,
                    nullSafeJoin(e.getFirstName(), e.getLastName()));
        }
    }

    // ────────────────────────── helpers ──────────────────────────────────────

    private void validateOutboundOffer(OtcOffer offer) {
        if (offer == null) throw new IllegalArgumentException("offer ne sme biti null");
        if (offer.buyerId() == null) throw new IllegalArgumentException("offer.buyerId ne sme biti null");
        if (offer.sellerId() == null) throw new IllegalArgumentException("offer.sellerId ne sme biti null");
        if (offer.lastModifiedBy() == null) throw new IllegalArgumentException("offer.lastModifiedBy ne sme biti null");
        if (offer.stock() == null) throw new IllegalArgumentException("offer.stock ne sme biti null");
        if (offer.pricePerUnit() == null) throw new IllegalArgumentException("offer.pricePerUnit ne sme biti null");
        if (offer.premium() == null) throw new IllegalArgumentException("offer.premium ne sme biti null");
        if (offer.amount() == null) throw new IllegalArgumentException("offer.amount ne sme biti null");
        if (offer.amount().signum() <= 0) {
            throw new IllegalArgumentException("amount mora biti > 0 (zadato: " + offer.amount() + ")");
        }
        if (offer.pricePerUnit().amount() == null || offer.pricePerUnit().amount().signum() <= 0) {
            throw new IllegalArgumentException("pricePerUnit mora biti > 0");
        }
        if (offer.premium().amount() == null || offer.premium().amount().signum() < 0) {
            throw new IllegalArgumentException("premium ne moze biti negativan");
        }
        OffsetDateTime settlement = offer.settlementDate();
        if (settlement == null || !settlement.isAfter(OffsetDateTime.now())) {
            throw new IllegalArgumentException(
                    "settlementDate mora biti u buducnosti (zadato: " + settlement + ")");
        }
        if (offer.buyerId().equals(offer.sellerId())) {
            throw new IllegalArgumentException("buyer i seller ne mogu biti isto lice");
        }
    }

    private void ensureLocalParty(ForeignBankId who, String message) {
        int myRouting = requireMyRoutingNumber();
        if (who.routingNumber() != myRouting) {
            throw new IllegalArgumentException(
                    message + " (rn=" + who.routingNumber() + ", nasa=" + myRouting + ")");
        }
    }

    private int requireMyRoutingNumber() {
        Integer myRouting = properties.getMyRoutingNumber();
        if (myRouting == null) {
            throw new InterbankExceptions.InterbankException(
                    "interbank.my-routing-number nije konfigurisan u properties.");
        }
        return myRouting;
    }

    /** Public accessor — kontroleri koriste za §3.7 validaciju routingNumber-a. */
    public int requireMyRouting() {
        return requireMyRoutingNumber();
    }

    private InterbankOtcNegotiation lookupByNegotiationId(ForeignBankId negotiationId) {
        return negotiationRepository
                .findByForeignNegotiationRoutingNumberAndForeignNegotiationIdString(
                        negotiationId.routingNumber(), negotiationId.id())
                .orElseThrow(() -> new InterbankExceptions.InterbankProtocolException(
                        "Pregovor " + negotiationId + " ne postoji"));
    }

    /** Parsira "C-{id}" ili "E-{id}" u (userId, role). */
    private LocalParty parseLocalPartyId(String prefixed) {
        if (prefixed == null || prefixed.length() < 3) {
            throw new IllegalArgumentException("Lokalni party id mora biti formata C-{id} ili E-{id}");
        }
        String role;
        if (prefixed.startsWith(CLIENT_ID_PREFIX)) {
            role = "CLIENT";
        } else if (prefixed.startsWith(EMPLOYEE_ID_PREFIX)) {
            role = "EMPLOYEE";
        } else {
            throw new IllegalArgumentException(
                    "Lokalni party id mora pocinjati sa 'C-' ili 'E-' (zadato: " + prefixed + ")");
        }
        long id;
        try {
            id = Long.parseLong(prefixed.substring(2));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Lokalni party id ima nevalidnu numericku ID komponentu: " + prefixed);
        }
        return new LocalParty(id, role);
    }

    /** Suprotno od parseLocalPartyId — formira "C-{id}" / "E-{id}". */
    private static String toForeignIdString(Long userId, String role) {
        return ("CLIENT".equalsIgnoreCase(role) ? CLIENT_ID_PREFIX : EMPLOYEE_ID_PREFIX) + userId;
    }

    /**
     * Pronalazi seller-ov racun u zadatoj valuti — koristi se u §3.6 da se
     * premium credit-uje na pravi racun. Vraca prvi racun matchovan po
     * vlasniku/roli i valuti.
     */
    private Optional<String> resolveLocalAccount(Long userId, String role, String currencyCode) {
        if (!"CLIENT".equalsIgnoreCase(role)) {
            // Zaposleni nemaju klijentske racune — zauzimamo bankin trading
            // racun (kao kod intra-bank exec) ali za pojednostavljen P1
            // demo prijavljujemo da ne podrzavamo employee-as-seller na
            // inter-bank OTC-u sve dok se T11 fund-flow ne prosiri.
            return Optional.empty();
        }
        Optional<Client> cOpt = clientRepository.findById(userId);
        if (cOpt.isEmpty()) return Optional.empty();
        return cOpt.get().getAccounts().stream()
                .filter(a -> a.getCurrency() != null
                        && currencyCode.equalsIgnoreCase(a.getCurrency().getCode()))
                .map(a -> a.getAccountNumber())
                .findFirst();
    }

    private OtcNegotiation mapToProtocol(InterbankOtcNegotiation e) {
        ForeignBankId localAsForeign = new ForeignBankId(
                requireMyRoutingNumber(),
                toForeignIdString(e.getLocalPartyId(), e.getLocalPartyRole()));
        ForeignBankId foreignAsForeign = new ForeignBankId(
                e.getForeignPartyRoutingNumber(), e.getForeignPartyIdString());

        ForeignBankId buyerId, sellerId;
        if (e.getLocalPartyType() == InterbankPartyType.SELLER) {
            sellerId = localAsForeign;
            buyerId = foreignAsForeign;
        } else {
            buyerId = localAsForeign;
            sellerId = foreignAsForeign;
        }

        ForeignBankId lastModifiedBy = new ForeignBankId(
                e.getLastModifiedByRoutingNumber(), e.getLastModifiedByIdString());

        return new OtcNegotiation(
                new StockDescription(e.getTicker()),
                e.getSettlementDate().atStartOfDay().atOffset(ZoneOffset.UTC),
                new MonetaryValue(CurrencyCode.valueOf(e.getPriceCurrency()), e.getPricePerUnit()),
                new MonetaryValue(CurrencyCode.valueOf(e.getPremiumCurrency()), e.getPremium()),
                buyerId,
                sellerId,
                e.getAmount(),
                lastModifiedBy,
                e.isOngoing()
        );
    }

    private static String nullSafeJoin(String first, String last) {
        String f = first == null ? "" : first.trim();
        String l = last == null ? "" : last.trim();
        if (f.isEmpty()) return l;
        if (l.isEmpty()) return f;
        return f + " " + l;
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** Test hook — programmatic cache invalidation (paket-private). */
    void invalidatePublicStockCache(int routingNumber) {
        publicStockCache.remove(routingNumber);
    }

    /** TTL-tracked cache entry za §3.1 outbound rezultate. Package-private za testove. */
    record CachedPublicStocks(List<PublicStock> stocks, Instant fetchedAt) {
        boolean isFresh(Instant now) {
            return Duration.between(fetchedAt, now).compareTo(PUBLIC_STOCK_TTL) < 0;
        }
    }

    private record LocalParty(Long userId, String role) {}
}
