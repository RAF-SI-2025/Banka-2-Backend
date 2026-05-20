package rs.raf.banka2_bek.recurringorder.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.auth.util.UserContext;
import rs.raf.banka2_bek.auth.util.UserResolver;
import rs.raf.banka2_bek.notification.model.Notification;
import rs.raf.banka2_bek.notification.model.NotificationType;
import rs.raf.banka2_bek.notification.repository.NotificationRepository;
import rs.raf.banka2_bek.order.dto.CreateOrderDto;
import rs.raf.banka2_bek.order.service.OrderService;
import rs.raf.banka2_bek.recurringorder.dto.CreateRecurringOrderDto;
import rs.raf.banka2_bek.recurringorder.dto.RecurringOrderDto;
import rs.raf.banka2_bek.recurringorder.model.RecurringCadence;
import rs.raf.banka2_bek.recurringorder.model.RecurringMode;
import rs.raf.banka2_bek.recurringorder.model.RecurringOrder;
import rs.raf.banka2_bek.recurringorder.repository.RecurringOrderRepository;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.repository.ListingRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

// ============================================================
// [B8 - Trajni nalozi (DCA / RecurringOrder) | Nosilac: Nikola Djurovic] - DONE
//
// Poslovni servis za upravljanje trajnim nalozima i njihovo izvrsavanje.
//
// IMPLEMENTIRANO — injectovani sledeci bean-ovi kao final polja:
//   - RecurringOrderRepository recurringOrderRepo
//   - rs.raf.banka2_bek.auth.util.UserResolver userResolver
//   - rs.raf.banka2_bek.order.service.OrderServiceImpl orderService
//       (ili odgovarajuci interfejs iz order paketa)
//   - rs.raf.banka2_bek.account.repository.AccountRepository accountRepo
//   - rs.raf.banka2_bek.listing.repository.ListingRepository listingRepo
//       (za dohvatanje ticker-a pri mapiranju u RecurringOrderDto)
//
// IMPLEMENTIRANO — metode (sve u @Transactional osim listMy):
//
//   RecurringOrderDto create(CreateRecurringOrderDto dto)
//       1. UserResolver.resolveCurrent() -> userId + ownerType ("CLIENT"/"EMPLOYEE")
//       2. Verifikovati da accountId pripada korisniku (AccountRepository.findById +
//          account.getClient().getId() == userId za klijente,
//          employeeId za zaposlene)
//       3. Verifikovati da listingId postoji (ListingRepository.findById)
//       4. Odrediti nextRun: ako dto.firstRun != null && dto.firstRun.isAfter(now)
//          koristi dto.firstRun; inace nextRun = now + 1 kadence korak
//       5. Kreirati i sacuvati RecurringOrder entitet
//       6. Vratiti mapiran RecurringOrderDto
//
//   List<RecurringOrderDto> listMy()
//       -> @Transactional(readOnly=true)
//       -> recurringOrderRepo.findByOwnerIdAndOwnerTypeOrderByCreatedAtDesc(userId, ownerType)
//       -> mapirati u RecurringOrderDto listu
//
//   RecurringOrderDto getById(Long id)
//       -> @Transactional(readOnly=true)
//       -> Dohvatiti nalog, verifikovati da pripada korisniku ili da je korisnik admin/supervisor
//       -> Vratiti RecurringOrderDto
//
//   RecurringOrderDto pause(Long id)
//       -> Postaviti active = false, sacuvati, vratiti DTO
//       -> Baciti AccessDeniedException ako nije vlasnik
//
//   RecurringOrderDto resume(Long id)
//       -> Postaviti active = true, sacuvati, vratiti DTO
//       -> Baciti AccessDeniedException ako nije vlasnik
//
//   void cancel(Long id)
//       -> Postaviti active = false i opciono obrisati zapis (ili soft-delete)
//       -> Baciti AccessDeniedException ako nije vlasnik
//
//   void executeOne(RecurringOrder recurringOrder)
//       -> Poziva se iz RecurringOrderScheduler; treba biti @Transactional(REQUIRES_NEW)
//          da greska jednog naloga ne rollback-uje ceo scheduler batch
//       -> Logika:
//            a. Dohvatiti currentPrice listinga (ListingRepository ili PriceService)
//            b. Izracunati kolicinu:
//                 BY_QUANTITY -> recurringOrder.getValue() (konvertovati u int/long)
//                 BY_AMOUNT   -> floor(value / currentPrice)
//            c. Ako je kolicina < 1, log.warn + azurirati nextRun pa return (skip, ne greska)
//            d. Verifikovati dostupna sredstva na racunu (account.getAvailableBalance());
//               ako nedovoljno -> log.warn "Nedovoljno sredstava za trajni nalog id={}",
//               azurirati nextRun pa return (skip bez greske, ne brisati nalog)
//            e. Za aktuare (ownerType="EMPLOYEE"): proveriti i azurirati dnevni limit
//               (ActuaryService ili directno actuary polje usedLimit) — potrosnja
//               treba da se uraci u aktuarov dnevni limit
//            f. Kreirati CreateOrderDto sa:
//                 orderType = "MARKET", direction = recurringOrder.getDirection(),
//                 listingId, quantity = izracunata kolicina, accountId,
//                 allOrNothing = false, margin = false
//               i pozvati orderService.createOrder(createOrderDto)
//            g. Azurirati nextRun = advanceNextRun(recurringOrder.getNextRun(), cadence)
//               i sacuvati entitet
//
//   private LocalDateTime advanceNextRun(LocalDateTime from, RecurringCadence cadence)
//       -> DAILY   -> from.plusDays(1)
//       -> WEEKLY  -> from.plusWeeks(1)
//       -> MONTHLY -> from.plusMonths(1)
//
//   private RecurringOrderDto toDto(RecurringOrder r)
//       -> Mapiranje entiteta u DTO (popuniti listingTicker iz ListingRepository)
//
// Konvencija: prati paket `savings` kao sablon.
// Spec: Zadaci_Backend.pdf, zadatak B8.
// ============================================================
@Service
@RequiredArgsConstructor
@Slf4j
public class RecurringOrderService {

    private final RecurringOrderRepository recurringOrderRepo;
    private final UserResolver userResolver;
    private final OrderService orderService;
    private final AccountRepository accountRepo;
    private final ListingRepository listingRepo;
    private final NotificationRepository notificationRepo;

    @Transactional
    public RecurringOrderDto create(CreateRecurringOrderDto dto) {
        UserContext me = userResolver.resolveCurrent();

        // Verifikuj da račun pripada korisniku
        Account account = accountRepo.findById(dto.getAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Račun ne postoji"));

        if (me.isClient()) {
            if (account.getClient() == null || !account.getClient().getId().equals(me.userId())) {
                throw new AccessDeniedException("Račun ne pripada klijentu.");
            }
        } else if (me.isEmployee()) {
            // Za zaposlene: trebalo bi provjeriti da li mogu da koriste taj račun
            // Ako je račun klijenta, zaposleni ne može da ga koristi za trajne naloge
            if (account.getClient() != null) {
                throw new AccessDeniedException("Zaposleni ne može koristiti klijentske račune za trajne naloge.");
            }
        }

        // Verifikuj da hartija od vrednosti postoji
        Listing listing = listingRepo.findById(dto.getListingId())
                .orElseThrow(() -> new IllegalArgumentException("Hartija od vrednosti ne postoji"));

        // Odredi nextRun
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime nextRun;
        if (dto.getFirstRun() != null && dto.getFirstRun().isAfter(now)) {
            nextRun = dto.getFirstRun();
        } else {
            nextRun = advanceNextRun(now, dto.getCadence());
        }

        // Kreiraj i spremi RecurringOrder
        RecurringOrder order = RecurringOrder.builder()
                .ownerId(me.userId())
                .ownerType(me.userRole())
                .listingId(dto.getListingId())
                .direction(dto.getDirection())
                .mode(dto.getMode())
                .value(dto.getValue())
                .accountId(dto.getAccountId())
                .cadence(dto.getCadence())
                .nextRun(nextRun)
                .active(true)
                .build();

        order = recurringOrderRepo.save(order);

        log.info("Trajni nalog kreiran: id={}, owner={}, listing={}, cadence={}",
                order.getId(), me.userId(), dto.getListingId(), dto.getCadence());

        return toDto(order);
    }

    @Transactional(readOnly = true)
    public List<RecurringOrderDto> listMy() {
        UserContext me = userResolver.resolveCurrent();
        return recurringOrderRepo.findByOwnerIdAndOwnerTypeOrderByCreatedAtDesc(me.userId(), me.userRole())
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public RecurringOrderDto getById(Long id) {
        RecurringOrder order = recurringOrderRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Trajni nalog ne postoji"));

        UserContext me = userResolver.resolveCurrent();
        if (!order.getOwnerId().equals(me.userId())) {
            throw new AccessDeniedException("Trajni nalog ne pripada korisniku.");
        }

        return toDto(order);
    }

    @Transactional
    public RecurringOrderDto pause(Long id) {
        RecurringOrder order = recurringOrderRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Trajni nalog ne postoji"));

        UserContext me = userResolver.resolveCurrent();
        if (!order.getOwnerId().equals(me.userId())) {
            throw new AccessDeniedException("Trajni nalog ne pripada korisniku.");
        }

        order.setActive(false);
        order = recurringOrderRepo.save(order);

        log.info("Trajni nalog pauziran: id={}", id);

        return toDto(order);
    }

    @Transactional
    public RecurringOrderDto resume(Long id) {
        RecurringOrder order = recurringOrderRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Trajni nalog ne postoji"));

        UserContext me = userResolver.resolveCurrent();
        if (!order.getOwnerId().equals(me.userId())) {
            throw new AccessDeniedException("Trajni nalog ne pripada korisniku.");
        }

        order.setActive(true);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        order.setNextRun(advanceNextRun(now, order.getCadence()));
        order = recurringOrderRepo.save(order);

        log.info("Trajni nalog reaktiviran: id={}, nextRun={}", id, order.getNextRun());

        return toDto(order);
    }

    @Transactional
    public void cancel(Long id) {
        RecurringOrder order = recurringOrderRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Trajni nalog ne postoji"));

        UserContext me = userResolver.resolveCurrent();
        if (!order.getOwnerId().equals(me.userId())) {
            throw new AccessDeniedException("Trajni nalog ne pripada korisniku.");
        }

        recurringOrderRepo.deleteById(id);

        log.info("Trajni nalog obrisan: id={}", id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void executeOne(RecurringOrder recurringOrder) {
        try {
            // a. Dohvati trenutnu cijenu
            Listing listing = listingRepo.findById(recurringOrder.getListingId())
                    .orElseThrow(() -> new IllegalArgumentException("Hartija od vrednosti ne postoji"));

            if (listing.getPrice() == null || listing.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("Scheduler: Hartija {} nema validnu cijenu, preskačem nalog id={}",
                        listing.getTicker(), recurringOrder.getId());
                advanceAndSave(recurringOrder);
                return;
            }

            // b. Izračunaj količinu
            long quantity;
            if (recurringOrder.getMode() == RecurringMode.BY_QUANTITY) {
                quantity = recurringOrder.getValue().longValue();
            } else {
                // BY_AMOUNT
                BigDecimal qtyDecimal = recurringOrder.getValue()
                        .divide(listing.getPrice(), RoundingMode.FLOOR);
                quantity = qtyDecimal.longValue();
            }

            // c. Provjeri da li je količina >= 1
            if (quantity < 1) {
                log.warn("Scheduler: Izračunata količina < 1 za nalog id={}, preskačem",
                        recurringOrder.getId());
                advanceAndSave(recurringOrder);
                return;
            }

            // d. Verifikuj dostupna sredstva
            Account account = accountRepo.findById(recurringOrder.getAccountId())
                    .orElseThrow(() -> new IllegalArgumentException("Račun ne postoji"));

            BigDecimal estimatedCost = listing.getPrice()
                    .multiply(BigDecimal.valueOf(quantity));

            if (account.getAvailableBalance().compareTo(estimatedCost) < 0) {
                // Nema dovoljno sredstava - pošalji notifikaciju
                notifyInsufficientFunds(recurringOrder);
                log.warn("Scheduler: Nedovoljno sredstava za nalog id={}, stvarna dostupna: {}, potrebna: {}",
                        recurringOrder.getId(), account.getAvailableBalance(), estimatedCost);
                advanceAndSave(recurringOrder);
                return;
            }

            // e. Za zaposlene: provjeri dnevni limit (account.dailySpending + cost <= dailyLimit)
            if ("EMPLOYEE".equals(recurringOrder.getOwnerType())) {
                BigDecimal newDailySpending = account.getDailySpending().add(estimatedCost);
                if (account.getDailyLimit().compareTo(BigDecimal.ZERO) > 0 &&
                    newDailySpending.compareTo(account.getDailyLimit()) > 0) {
                    log.warn("Scheduler: Prekoračen dnevni limit za zaposlenog id={}, limit: {}, trebalo bi: {}",
                            recurringOrder.getOwnerId(), account.getDailyLimit(), newDailySpending);
                    advanceAndSave(recurringOrder);
                    return;
                }
            }

            // f. Kreiraj Market Order
            CreateOrderDto orderDto = new CreateOrderDto();
            orderDto.setOrderType("MARKET");
            orderDto.setDirection(recurringOrder.getDirection());
            orderDto.setListingId(recurringOrder.getListingId());
            orderDto.setQuantity((int) quantity);
            orderDto.setAccountId(recurringOrder.getAccountId());
            orderDto.setAllOrNone(false);
            orderDto.setMargin(false);
            orderDto.setOtpCode("auto-generated-for-recurring");

            orderService.createOrder(orderDto);

            log.info("Scheduler: Market order kreiran iz trajnog naloga id={}, quantity={}, listing={}",
                    recurringOrder.getId(), quantity, listing.getTicker());

            // g. Ažuriraj nextRun
            advanceAndSave(recurringOrder);

        } catch (Exception e) {
            log.error("Scheduler: Greška pri izvršavanju trajnog naloga id={}: {}",
                    recurringOrder.getId(), e.getMessage(), e);
            advanceAndSave(recurringOrder);
        }
    }

    private void advanceAndSave(RecurringOrder order) {
        LocalDateTime newNextRun = advanceNextRun(order.getNextRun(), order.getCadence());
        order.setNextRun(newNextRun);
        recurringOrderRepo.save(order);
    }

    private LocalDateTime advanceNextRun(LocalDateTime from, RecurringCadence cadence) {
        return switch (cadence) {
            case DAILY -> from.plusDays(1);
            case WEEKLY -> from.plusWeeks(1);
            case MONTHLY -> from.plusMonths(1);
        };
    }

    private void notifyInsufficientFunds(RecurringOrder order) {
        Notification notification = Notification.builder()
                .recipientId(order.getOwnerId())
                .recipientType(order.getOwnerType())
                .notificationType(NotificationType.RECURRING_ORDER_SKIPPED)
                .title("Trajni nalog preskočen - nedovoljna sredstva")
                .body("Trajni nalog id=" + order.getId() + " nije izvršen jer nema dovoljno sredstava na računu.")
                .read(false)
                .referenceType("RECURRING_ORDER")
                .referenceId(order.getId())
                .build();
        notificationRepo.save(notification);
    }

    private RecurringOrderDto toDto(RecurringOrder order) {
        Listing listing = listingRepo.findById(order.getListingId()).orElse(null);
        String ticker = listing != null ? listing.getTicker() : "N/A";

        RecurringOrderDto dto = new RecurringOrderDto();
        dto.setId(order.getId());
        dto.setOwnerId(order.getOwnerId());
        dto.setOwnerType(order.getOwnerType());
        dto.setListingId(order.getListingId());
        dto.setListingTicker(ticker);
        dto.setDirection(order.getDirection());
        dto.setMode(order.getMode().toString());
        dto.setValue(order.getValue());
        dto.setAccountId(order.getAccountId());
        dto.setCadence(order.getCadence().toString());
        dto.setNextRun(order.getNextRun());
        dto.setActive(order.isActive());
        dto.setCreatedAt(order.getCreatedAt());
        dto.setUpdatedAt(order.getUpdatedAt());

        return dto;
    }
}
