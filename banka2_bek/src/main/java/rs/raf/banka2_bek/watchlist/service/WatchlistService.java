package rs.raf.banka2_bek.watchlist.service;

// ============================================================
// TODO [B6 - Watchlist | Nosilac: L. Draskovic]
//
// Servis koji implementira svu biznis logiku za liste pracenih hartija.
// Koristiti @RequiredArgsConstructor za injekciju zavisnosti i
// @Slf4j za logovanje, kao sto radi SavingsDepositService.
//
// ZAVISNOSTI (polja, @RequiredArgsConstructor):
//   - WatchlistRepository watchlistRepo
//   - WatchlistItemRepository itemRepo
//   - UserResolver userResolver        -- za identifikaciju tekuceg korisnika (JWT)
//   - ListingRepository listingRepo    -- za dohvatanje aktuelnih podataka o hartiji
//     (paket: rs.raf.banka2_bek.securities.repository ili slicno -- proveriti aktuelni paket)
//
// IMPLEMENTIRATI (metode, sve @Transactional osim read-only):
//
//   WatchlistDto createWatchlist(CreateWatchlistDto dto)
//     -- resolvuje tekuceg korisnika (CLIENT/EMPLOYEE), odredjuje ownerType,
//        proverava duplikat naziva (existsByOwnerIdAndOwnerTypeAndName),
//        kreira i snima novi Watchlist, vraca WatchlistDto.
//
//   List<WatchlistDto> listMyWatchlists()
//     -- @Transactional(readOnly=true)
//        vraca sve liste tekuceg korisnika (findByOwnerIdAndOwnerTypeOrderByCreatedAtAsc),
//        mapira na WatchlistDto sa itemCount iz itemRepo.countByWatchlistId(id).
//
//   WatchlistDto renameWatchlist(Long watchlistId, CreateWatchlistDto dto)
//     -- pronalazi listu i proverava vlasnistvo (findByIdAndOwnerIdAndOwnerType),
//        proverava da novi naziv nije duplikat,
//        setuje novo ime, snima, vraca WatchlistDto.
//
//   void deleteWatchlist(Long watchlistId)
//     -- proverava vlasnistvo, poziva itemRepo.deleteAllByWatchlistId,
//        zatim watchlistRepo.delete.
//
//   WatchlistItemDto addItem(Long watchlistId, Long listingId)
//     -- proverava vlasnistvo liste,
//        proverava da hartija postoji u listings tabeli (listingRepo.findById),
//        proverava duplikat (existsByWatchlistIdAndListingId -- baca IllegalArgumentException ako vec postoji),
//        kreira WatchlistItem, dohvata live podatke (cena, dnevna promena, obim)
//        i vraca WatchlistItemDto sa svim poljima popunjenim.
//
//   void removeItem(Long watchlistId, Long listingId)
//     -- proverava vlasnistvo liste,
//        proverava da stavka postoji (findByWatchlistIdAndListingId -- baca 404 ako ne postoji),
//        poziva itemRepo.deleteByWatchlistIdAndListingId.
//
//   List<WatchlistItemDto> listItems(Long watchlistId, String securityTypeFilter)
//     -- @Transactional(readOnly=true)
//        proverava vlasnistvo liste,
//        dohvata sve stavke (findByWatchlistIdOrderByAddedAtAsc),
//        za svaku stavku dohvata listing iz listingRepo i puni live podatke,
//        ako securityTypeFilter nije null, filtrira po listing.type (STOCK/FOREX/FUTURE/OPTION),
//        vraca List<WatchlistItemDto>.
//
// POMOCNE METODE (private):
//   - Watchlist resolveOwnedWatchlist(Long watchlistId, Long ownerId, WatchlistOwnerType ownerType)
//       -- findByIdAndOwnerIdAndOwnerType, baca IllegalArgumentException("Lista ne postoji ili nije vasa.") ako nije pronadjena
//   - WatchlistOwnerType resolveOwnerType(UserContext me)
//       -- vraca WatchlistOwnerType.CLIENT ako me.isClient(), inace WatchlistOwnerType.EMPLOYEE
//   - WatchlistItemDto toItemDto(WatchlistItem item, Listing listing)
//       -- mapira WatchlistItem + Listing na WatchlistItemDto sa live podacima
//
// Konvencija: pratiti paket `savings` kao sablon (SavingsDepositService).
// Spec: Zadaci_Backend.pdf, zadatak B6.
// ============================================================

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.auth.util.UserContext;
import rs.raf.banka2_bek.auth.util.UserResolver;
import rs.raf.banka2_bek.stock.dto.ListingDto;
import rs.raf.banka2_bek.stock.service.ListingService;
import rs.raf.banka2_bek.watchlist.dto.CreateWatchlistDto;
import rs.raf.banka2_bek.watchlist.dto.WatchlistDto;
import rs.raf.banka2_bek.watchlist.dto.WatchlistItemDto;
import rs.raf.banka2_bek.watchlist.model.Watchlist;
import rs.raf.banka2_bek.watchlist.model.WatchlistItem;
import rs.raf.banka2_bek.watchlist.model.WatchlistOwnerType;
import rs.raf.banka2_bek.watchlist.repository.WatchlistItemRepository;
import rs.raf.banka2_bek.watchlist.repository.WatchlistRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WatchlistService {

    private final WatchlistRepository watchlistRepo;
    private final WatchlistItemRepository itemRepo;
    private final UserResolver userResolver;
    private final ListingService listingService;

    @Transactional
    public WatchlistDto createWatchlist(CreateWatchlistDto dto) {
        UserContext me = userResolver.resolveCurrent();
        WatchlistOwnerType ownerType = resolveOwnerType(me);
        if (watchlistRepo.existsByOwnerIdAndOwnerTypeAndName(me.userId(), ownerType, dto.getName())) {
            throw new IllegalArgumentException("Lista sa imenom vec postoji: " + dto.getName());
        }
        Watchlist wl = Watchlist.builder()
                .ownerId(me.userId())
                .ownerType(ownerType)
                .name(dto.getName())
                .build();
        Watchlist saved = watchlistRepo.save(wl);
        return WatchlistDto.builder()
                .id(saved.getId())
                .ownerId(saved.getOwnerId())
                .ownerType(saved.getOwnerType().name())
                .name(saved.getName())
                .itemCount(0)
                .createdAt(saved.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<WatchlistDto> listMyWatchlists() {
        UserContext me = userResolver.resolveCurrent();
        WatchlistOwnerType ownerType = resolveOwnerType(me);
        List<Watchlist> lists = watchlistRepo.findByOwnerIdAndOwnerTypeOrderByCreatedAtAsc(me.userId(), ownerType);
        return lists.stream().map(w -> {
            int count = itemRepo.findByWatchlistIdOrderByAddedAtAsc(w.getId()).size();
            return WatchlistDto.builder()
                    .id(w.getId())
                    .ownerId(w.getOwnerId())
                    .ownerType(w.getOwnerType().name())
                    .name(w.getName())
                    .itemCount(count)
                    .createdAt(w.getCreatedAt())
                    .build();
        }).collect(Collectors.toList());
    }

    @Transactional
    public WatchlistDto renameWatchlist(Long watchlistId, CreateWatchlistDto dto) {
        UserContext me = userResolver.resolveCurrent();
        WatchlistOwnerType ownerType = resolveOwnerType(me);
        Watchlist wl = watchlistRepo.findByIdAndOwnerIdAndOwnerType(watchlistId, me.userId(), ownerType)
                .orElseThrow(() -> new IllegalArgumentException("Lista ne postoji ili nije vasa."));
        if (watchlistRepo.existsByOwnerIdAndOwnerTypeAndName(me.userId(), ownerType, dto.getName())) {
            throw new IllegalArgumentException("Lista sa imenom vec postoji: " + dto.getName());
        }
        wl.setName(dto.getName());
        Watchlist saved = watchlistRepo.save(wl);
        int count = itemRepo.findByWatchlistIdOrderByAddedAtAsc(saved.getId()).size();
        return WatchlistDto.builder()
                .id(saved.getId())
                .ownerId(saved.getOwnerId())
                .ownerType(saved.getOwnerType().name())
                .name(saved.getName())
                .itemCount(count)
                .createdAt(saved.getCreatedAt())
                .build();
    }

    @Transactional
    public void deleteWatchlist(Long watchlistId) {
        UserContext me = userResolver.resolveCurrent();
        WatchlistOwnerType ownerType = resolveOwnerType(me);
        Watchlist wl = watchlistRepo.findByIdAndOwnerIdAndOwnerType(watchlistId, me.userId(), ownerType)
                .orElseThrow(() -> new IllegalArgumentException("Lista ne postoji ili nije vasa."));
        itemRepo.deleteAllByWatchlistId(wl.getId());
        watchlistRepo.delete(wl);
    }

    @Transactional
    public WatchlistItemDto addItem(Long watchlistId, Long listingId) {
        UserContext me = userResolver.resolveCurrent();
        WatchlistOwnerType ownerType = resolveOwnerType(me);
        Watchlist wl = watchlistRepo.findByIdAndOwnerIdAndOwnerType(watchlistId, me.userId(), ownerType)
                .orElseThrow(() -> new IllegalArgumentException("Lista ne postoji ili nije vasa."));
        if (itemRepo.existsByWatchlistIdAndListingId(wl.getId(), listingId)) {
            throw new IllegalArgumentException("Hartija je vec na listi.");
        }
        // validate listing exists and fetch live data
        ListingDto listing = listingService.getListingById(listingId);

        WatchlistItem item = WatchlistItem.builder()
                .watchlistId(wl.getId())
                .listingId(listingId)
                .build();
        WatchlistItem saved = itemRepo.save(item);
        return toItemDto(saved, listing);
    }

    @Transactional
    public void removeItem(Long watchlistId, Long listingId) {
        UserContext me = userResolver.resolveCurrent();
        WatchlistOwnerType ownerType = resolveOwnerType(me);
        Watchlist wl = watchlistRepo.findByIdAndOwnerIdAndOwnerType(watchlistId, me.userId(), ownerType)
                .orElseThrow(() -> new IllegalArgumentException("Lista ne postoji ili nije vasa."));
        itemRepo.findByWatchlistIdAndListingId(wl.getId(), listingId)
                .orElseThrow(() -> new IllegalArgumentException("Stavka nije pronadjena."));
        itemRepo.deleteByWatchlistIdAndListingId(wl.getId(), listingId);
    }

    @Transactional(readOnly = true)
    public List<WatchlistItemDto> listItems(Long watchlistId, String securityTypeFilter) {
        UserContext me = userResolver.resolveCurrent();
        WatchlistOwnerType ownerType = resolveOwnerType(me);
        Watchlist wl = watchlistRepo.findByIdAndOwnerIdAndOwnerType(watchlistId, me.userId(), ownerType)
                .orElseThrow(() -> new IllegalArgumentException("Lista ne postoji ili nije vasa."));
        List<WatchlistItem> items = itemRepo.findByWatchlistIdOrderByAddedAtAsc(wl.getId());
        return items.stream().map(item -> {
                    ListingDto listing = listingService.getListingById(item.getListingId());
                    return toItemDto(item, listing);
                }).filter(dto -> securityTypeFilter == null || securityTypeFilter.isBlank() || securityTypeFilter.equalsIgnoreCase(dto.getSecurityType()))
                .collect(Collectors.toList());
    }

    private WatchlistOwnerType resolveOwnerType(UserContext me) {
        return me.isClient() ? WatchlistOwnerType.CLIENT : WatchlistOwnerType.EMPLOYEE;
    }

    private WatchlistItemDto toItemDto(WatchlistItem item, ListingDto listing) {
        return WatchlistItemDto.builder()
                .id(item.getId())
                .watchlistId(item.getWatchlistId())
                .listingId(item.getListingId())
                .ticker(listing.getTicker())
                .listingName(listing.getName())
                .securityType(listing.getListingType())
                .exchangeName(listing.getExchangeAcronym())
                .currentPrice(listing.getPrice())
                .dailyChange(listing.getChangePercent())
                .volume(listing.getVolume())
                .addedAt(item.getAddedAt())
                .build();
    }
}