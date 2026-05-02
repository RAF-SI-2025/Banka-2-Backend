package rs.raf.banka2_bek.investmentfund.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.investmentfund.dto.InvestmentFundDtos.*;
import rs.raf.banka2_bek.investmentfund.model.FundValueSnapshot;
import rs.raf.banka2_bek.investmentfund.repository.FundValueSnapshotRepository;
import rs.raf.banka2_bek.investmentfund.model.ClientFundPosition;
import rs.raf.banka2_bek.investmentfund.model.InvestmentFund;
import rs.raf.banka2_bek.investmentfund.repository.ClientFundPositionRepository;
import rs.raf.banka2_bek.investmentfund.repository.InvestmentFundRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/*
================================================================================
 TODO — CORE SERVICE ZA INVESTICIONE FONDOVE
 Zaduzen: BE tim
 Spec referenca: Celina 4, linije 160-351
--------------------------------------------------------------------------------
 API:
  1. createFund(CreateFundDto, Long supervisorId)
     - Validacija: supervizor (po permisiji); name unique
     - Kreiraj RSD bankin racun (AccountService.createFundAccount)
     - Upise InvestmentFund sa managerEmployeeId=supervisorId, accountId=novi
     - Inicijalni FundValueSnapshot sa vrednoscu=0
     - Vrati InvestmentFundDetailDto

  2. listDiscovery(String searchQuery, String sortField, String sortDirection)
     - Vraca sve aktivne fondove + računa fundValue/profit za svaki
     - Sortiranje/filter kako spec zahteva (Celina 4 linija 302)

  3. getFundDetails(Long fundId)
     - fundValue = account.balance + sum(portfolio.quantity * listing.price konvertovano u RSD)
     - profit = fundValue - sum(positions.totalInvested)
     - Holdings iz Portfolio sa userRole=FUND, userId=fundId
     - Performance iz FundValueSnapshot (poslednjih 30 dana default)

  4. invest(Long fundId, InvestFundDto dto, Long userId, String userRole)
     - Validacija: amount >= fund.minimumContribution
     - Ako klijent: FX komisija 1% ako konverzija; ako supervizor (banka): 0%
     - Transfer sa sourceAccountId na fund.accountId
     - Kreiraj ClientFundTransaction sa status=PENDING, potom COMPLETED
     - Upsert ClientFundPosition (ili kreiraj novu)
     - Vrati ClientFundPositionDto

  5. withdraw(Long fundId, WithdrawFundDto dto, Long userId, String userRole)
     - Ako amount null: povuci punu poziciju
     - Validacija: position.totalInvested >= amount
     - Ako fund.account.balance >= amount: odmah isplata
     - Ako fund.account.balance < amount: scheduler ce prodati hartije,
       ostaje status=PENDING, klijent dobija notifikaciju
     - Kreiraj ClientFundTransaction
     - Smanji position.totalInvested, ako <=0 obrisi ili active=false
     - Vrati ClientFundTransactionDto

  6. listMyPositions(Long userId, String userRole)
     - Vrati ClientFundPositionDto za svaku poziciju koju ima taj korisnik
     - Ukljucuje derived fields (currentValue, percentOfFund, profit)

  7. reassignFundManager(Long oldSupervisorId, Long newAdminId)
     - Poziva se iz ActuaryService.removeIsSupervisorPermission
     - InvestmentFundRepository.reassignManager(oldId, newId)
     - Audit log: "Fund X reassigned from supervisor A to admin B"

 KORISTI:
  FundValueCalculator (za derived vrednosti)
  FundLiquidationService (za auto-sell kad je likvidnost nedovoljna)
  CurrencyConversionService (za konverziju u RSD)
  AccountRepository, PortfolioRepository, ListingRepository
  ClientFundPositionRepository, ClientFundTransactionRepository
================================================================================
*/
@Slf4j
@Service
@RequiredArgsConstructor
public class InvestmentFundService {

    private final FundValueSnapshotRepository fundValueSnapshotRepository;

    private final InvestmentFundRepository investmentFundRepository;
    // T12 (P9 + listMyPositions): repoze za pozicije + lookup klijenta-vlasnika banke.
    // Ostale zavisnosti (FundValueCalculator, CurrencyConversionService,
    // ClientFundTransactionRepository, FundLiquidationService, ...) ce dodati T7-T9.
    private final ClientFundPositionRepository clientFundPositionRepository;
    private final ClientRepository clientRepository;

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
        throw new UnsupportedOperationException("TODO");
    }

    public List<InvestmentFundSummaryDto> listDiscovery(String searchQuery, String sortField, String sortDirection) {
        throw new UnsupportedOperationException("TODO");
    }

    public InvestmentFundDetailDto getFundDetails(Long fundId) {
        throw new UnsupportedOperationException("TODO");
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
     * P7 — Spec Celina 4 (Nova) §4338-4391 (Napomena 4): Provizija pri konverziji.
     *  - Klijent uplacuje sa svog racuna -> ako je sourceAccount.currency != RSD,
     *    primenjuje se 1% FX komisija (CurrencyConversionService.convertForPurchase
     *    sa chargeFx=true).
     *  - Supervizor uplacuje sa bankinog racuna -> 0% komisija (chargeFx=false).
     *
     * Zatim:
     *  - tx = new ClientFundTransaction(status=PENDING, isInflow=true)
     *  - debit sourceAccount, credit fund.account (RSD)
     *  - tx.status = COMPLETED, save
     *  - upsert ClientFundPosition: totalInvested += amount
     *  - vrati ClientFundPositionDto sa derived %ofFund i currentValue
     */
    @Transactional
    public ClientFundPositionDto invest(Long fundId, InvestFundDto dto, Long userId, String userRole) {
        throw new UnsupportedOperationException(
                "TODO P7: implementirati invest — vidi javadoc iznad. "
                + "Klijent: 1% FX komisija; Supervizor (banka): 0%.");
    }

    /**
     * P7 — Spec Celina 4 (Nova) §4338-4391 (Napomena 4): isto pravilo kao za invest,
     * ali u suprotnom smeru. Plus P4 likvidacija ako fond nema dovoljno cash-a.
     *
     * Logika:
     *  if (amount == null) amount = position.totalInvested  // pun withdraw
     *  if (amount > position.totalInvested) -> 400 BadRequest
     *  if (fund.account.balanceRsd >= amount):
     *      odmah debit fund.account, credit user account
     *      (chargeFx = userRole == CLIENT pri konverziji RSD -> targetCurrency)
     *      tx.status = COMPLETED
     *  else:
     *      tx.status = PENDING
     *      fundLiquidationService.liquidateFor(fundId, amount - balance)
     *      (vidi P4 — auto-prodaja hartija)
     *  position.totalInvested -= amount
     *  if (position.totalInvested <= 0) brisi/active=false
     */
    @Transactional
    public ClientFundTransactionDto withdraw(Long fundId, WithdrawFundDto dto, Long userId, String userRole) {
        throw new UnsupportedOperationException(
                "TODO P7+P4: implementirati withdraw — vidi javadoc iznad. "
                + "Klijent: 1% FX komisija u konverziji RSD -> account.currency; "
                + "Supervizor (banka): 0% komisija. Ako fond nema cash-a, "
                + "tx ostaje PENDING dok FundLiquidationService ne proda hartije.");
    }

    /**
     * T12 — Spec Celina 4 (Nova) "Moj portfolio -> Moji fondovi page".
     *
     * Vraca sve pozicije (ClientFundPosition) za autentifikovanog korisnika.
     * Pozicija se identifikuje (userId, userRole) parom — supervizor moze
     * imati pozicije i kao CLIENT (privatne) i preko bankine `ownerClientId`
     * (kroz listBankPositions, ne ovde).
     *
     * Izvedena polja (currentValue, percentOfFund, profit) ostaju null:
     *  - zavise od FundValueCalculator-a koji T7+T10 jos pisu (FundValueSnapshot
     *    + ListingRepository + CurrencyConversionService)
     *  - kad budu dostupni, dopuni `toClientFundPositionDto` mapper
     *    (umesto null, popuni iz cached snapshot-a ili racunaj live)
     *
     * Koristi se iz InvestmentFundController.GET /funds/my-positions.
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
        // Batch-load fondova da izbegnemo N+1 lookup za fundName u DTO-u.
        List<Long> fundIds = positions.stream().map(ClientFundPosition::getFundId).distinct().toList();
        Map<Long, String> fundIdToName = investmentFundRepository.findAllById(fundIds).stream()
                .collect(Collectors.toMap(InvestmentFund::getId, InvestmentFund::getName));
        return positions.stream()
                .map(p -> toClientFundPositionDto(p, fundIdToName.get(p.getFundId())))
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
     * T12 — privatni mapper iz domena (ClientFundPosition) u FE DTO.
     * Polja `currentValue`, `percentOfFund`, `profit` ostaju null jer
     * zavise od FundValueCalculator-a koji T7+T10 jos pisu. Kad budu
     * dostupni, prosiri ovde (npr. inject FundValueCalculator i racunaj
     * iz live snapshot-a ili poslednjeg FundValueSnapshot reda).
     */
    private ClientFundPositionDto toClientFundPositionDto(ClientFundPosition position, String fundName) {
        ClientFundPositionDto dto = new ClientFundPositionDto();
        dto.setId(position.getId());
        dto.setFundId(position.getFundId());
        dto.setFundName(fundName);
        dto.setUserId(position.getUserId());
        dto.setUserRole(position.getUserRole());
        // userName: T7+T8 ce dopuniti rezolvujuci po (userId, userRole) iz
        // clients ili employees tabele. Trenutno ostaje null.
        dto.setUserName(null);
        dto.setTotalInvested(position.getTotalInvested());
        // Izvedena polja — null dok FundValueCalculator (T7+T10) ne bude gotov.
        dto.setCurrentValue(null);
        dto.setPercentOfFund(null);
        dto.setProfit(null);
        dto.setLastModifiedAt(position.getLastModifiedAt());
        return dto;
    }

    /**
     * P5 — Spec Celina 4 (Nova) §3797-3879: kad admin ukloni isSupervisor
     * permisiju supervizoru koji upravlja fondovima, vlasnistvo svih tih
     * fondova prebacuje se na admina koji je izvrsio uklanjanje.
     *
     * Vraca broj prebacenih fondova (>= 0). Ako oldSupervisor nema fondova,
     * 0 — bezbedno za pozivanje na svaku permisiju-update operaciju.
     *
     * Pozivati iz:
     *   - EmployeeService / AdminEmployeeService kad permisije menjaju
     *   - Direktno iz nekog manualnog supervisor-portala dugmeta (ako postoji)
     */
    @Transactional
    public int reassignFundManager(Long oldSupervisorId, Long newAdminId) {
        if (oldSupervisorId == null || newAdminId == null) {
            return 0;
        }
        if (oldSupervisorId.equals(newAdminId)) {
            return 0;
        }
        int reassigned = investmentFundRepository.reassignManager(oldSupervisorId, newAdminId);
        if (reassigned > 0) {
            log.info("InvestmentFund manager reassigned: {} fund(s) from employee #{} to employee #{}",
                    reassigned, oldSupervisorId, newAdminId);
        }
        return reassigned;
    }
}
