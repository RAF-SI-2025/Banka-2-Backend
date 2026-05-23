package rs.raf.banka2_bek.watchlist;

// ============================================================
// TODO [B6 - Watchlist | Nosilac: L. Draskovic]
//
// Unit testovi za WatchlistService. Koristiti Mockito strict stubs
// i JUnit 5, isto kao SavingsDepositServiceTest.
//
// SETUP (anotacije na klasi):
//   @ExtendWith(MockitoExtension.class)
//
// MOCK-ovati (@Mock):
//   - WatchlistRepository watchlistRepo
//   - WatchlistItemRepository itemRepo
//   - UserResolver userResolver
//   - ListingRepository listingRepo   (ili odgovarajuci servis za listing podatke)
//
// INJECT (@InjectMocks):
//   - WatchlistService watchlistService
//
// IMPLEMENTIRATI (@Test metode -- min 10 test slucajeva):
//
//   createWatchlist_success_clientOwner()
//     -- userResolver vraca CLIENT UserContext, naziv nije duplikat,
//        watchlistRepo.save vraca Watchlist sa id=1L,
//        ocekivano: vraca WatchlistDto sa name="Moje akcije", ownerId=42L, ownerType="CLIENT"
//
//   createWatchlist_throwsIfDuplicateName()
//     -- existsByOwnerIdAndOwnerTypeAndName vraca true,
//        ocekivano: baca IllegalArgumentException sa porukom koja sadrzi ime liste
//
//   listMyWatchlists_returnsEmptyListWhenNoWatchlists()
//     -- findByOwnerIdAndOwnerTypeOrderByCreatedAtAsc vraca empty list,
//        ocekivano: vraca praznu listu, ne baca exception
//
//   renameWatchlist_success()
//     -- findByIdAndOwnerIdAndOwnerType vraca Watchlist, naziv nije duplikat,
//        ocekivano: watchlistRepo.save pozvan sa azuriranim imenom, vraca dto sa novim imenom
//
//   renameWatchlist_throwsIfDuplicateName()
//     -- existsByOwnerIdAndOwnerTypeAndName vraca true za novi naziv,
//        ocekivano: baca IllegalArgumentException
//
//   renameWatchlist_throwsIfWatchlistNotFound()
//     -- findByIdAndOwnerIdAndOwnerType vraca Optional.empty(),
//        ocekivano: baca IllegalArgumentException("Lista ne postoji ili nije vasa.")
//
//   deleteWatchlist_success()
//     -- findByIdAndOwnerIdAndOwnerType vraca Watchlist,
//        ocekivano: itemRepo.deleteAllByWatchlistId pozvan, watchlistRepo.delete pozvan
//
//   addItem_success()
//     -- lista postoji i pripada korisniku, listing postoji u listingRepo,
//        stavka jos ne postoji (existsByWatchlistIdAndListingId vraca false),
//        itemRepo.save vraca WatchlistItem, listing ima currentPrice=150.0, dailyChange=+1.5%,
//        ocekivano: vraca WatchlistItemDto sa ispravnim live podacima
//
//   addItem_throwsIfAlreadyOnList()
//     -- existsByWatchlistIdAndListingId vraca true,
//        ocekivano: baca IllegalArgumentException("Hartija je vec na listi.")
//
//   removeItem_success()
//     -- stavka postoji (findByWatchlistIdAndListingId vraca WatchlistItem),
//        ocekivano: itemRepo.deleteByWatchlistIdAndListingId pozvan jednom
//
//   listItems_filterBySecurityType_returnsOnlyMatchingItems()
//     -- lista ima 3 stavke: 2 STOCK i 1 FOREX,
//        poziv sa securityTypeFilter="STOCK",
//        ocekivano: vraca listu sa 2 elementa, oba tipa STOCK
//
// Konvencija: pratiti SavingsDepositServiceTest kao sablon.
// Spec: Zadaci_Backend.pdf, zadatak B6.
// ============================================================

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.auth.util.UserContext;
import rs.raf.banka2_bek.auth.util.UserRole;
import rs.raf.banka2_bek.stock.dto.ListingDto;
import rs.raf.banka2_bek.stock.service.ListingService;
import rs.raf.banka2_bek.watchlist.dto.CreateWatchlistDto;
import rs.raf.banka2_bek.watchlist.dto.WatchlistItemDto;
import rs.raf.banka2_bek.watchlist.dto.WatchlistDto;
import rs.raf.banka2_bek.watchlist.model.Watchlist;
import rs.raf.banka2_bek.watchlist.model.WatchlistItem;
import rs.raf.banka2_bek.watchlist.model.WatchlistOwnerType;
import rs.raf.banka2_bek.watchlist.repository.WatchlistItemRepository;
import rs.raf.banka2_bek.watchlist.repository.WatchlistRepository;
import rs.raf.banka2_bek.watchlist.service.WatchlistService;
import rs.raf.banka2_bek.auth.util.UserResolver;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WatchlistServiceTest {

    @InjectMocks
    private WatchlistService watchlistService;

    @Mock private WatchlistRepository watchlistRepo;
    @Mock private WatchlistItemRepository itemRepo;
    @Mock private UserResolver userResolver;
    @Mock private ListingService listingService;

    private final UserContext clientCtx = new UserContext(42L, UserRole.CLIENT);

    @BeforeEach
    void setUp() {
        // nothing
    }

    @Test
    @DisplayName("createWatchlist_success_clientOwner")
    void createWatchlist_success_clientOwner() {
        when(userResolver.resolveCurrent()).thenReturn(clientCtx);
        when(watchlistRepo.existsByOwnerIdAndOwnerTypeAndName(42L, WatchlistOwnerType.CLIENT, "Moje akcije"))
                .thenReturn(false);

        Watchlist saved = Watchlist.builder()
                .id(1L)
                .ownerId(42L)
                .ownerType(WatchlistOwnerType.CLIENT)
                .name("Moje akcije")
                .createdAt(LocalDateTime.now())
                .build();
        when(watchlistRepo.save(any(Watchlist.class))).thenReturn(saved);

        CreateWatchlistDto dto = CreateWatchlistDto.builder().name("Moje akcije").build();
        WatchlistDto res = watchlistService.createWatchlist(dto);

        assertEquals(1L, res.getId());
        assertEquals(42L, res.getOwnerId());
        assertEquals("CLIENT", res.getOwnerType());
        assertEquals("Moje akcije", res.getName());
        assertEquals(0, res.getItemCount());
    }

    @Test
    @DisplayName("createWatchlist_throwsIfDuplicateName")
    void createWatchlist_throwsIfDuplicateName() {
        when(userResolver.resolveCurrent()).thenReturn(clientCtx);
        when(watchlistRepo.existsByOwnerIdAndOwnerTypeAndName(42L, WatchlistOwnerType.CLIENT, "X"))
                .thenReturn(true);

        CreateWatchlistDto dto = CreateWatchlistDto.builder().name("X").build();
        assertThatThrownBy(() -> watchlistService.createWatchlist(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("X");
    }

    @Test
    @DisplayName("listMyWatchlists_returnsEmptyListWhenNoWatchlists")
    void listMyWatchlists_returnsEmptyListWhenNoWatchlists() {
        when(userResolver.resolveCurrent()).thenReturn(clientCtx);
        when(watchlistRepo.findByOwnerIdAndOwnerTypeOrderByCreatedAtAsc(42L, WatchlistOwnerType.CLIENT))
                .thenReturn(Collections.emptyList());

        List<WatchlistDto> res = watchlistService.listMyWatchlists();
        assertThat(res).isEmpty();
    }

    @Test
    @DisplayName("renameWatchlist_success")
    void renameWatchlist_success() {
        when(userResolver.resolveCurrent()).thenReturn(clientCtx);
        Watchlist existing = Watchlist.builder()
                .id(5L)
                .ownerId(42L)
                .ownerType(WatchlistOwnerType.CLIENT)
                .name("Old")
                .createdAt(LocalDateTime.now())
                .build();
        when(watchlistRepo.findByIdAndOwnerIdAndOwnerType(5L, 42L, WatchlistOwnerType.CLIENT))
                .thenReturn(Optional.of(existing));
        when(watchlistRepo.existsByOwnerIdAndOwnerTypeAndName(42L, WatchlistOwnerType.CLIENT, "New"))
                .thenReturn(false);
        Watchlist saved = Watchlist.builder()
                .id(5L)
                .ownerId(42L)
                .ownerType(WatchlistOwnerType.CLIENT)
                .name("New")
                .createdAt(existing.getCreatedAt())
                .build();
        when(watchlistRepo.save(any(Watchlist.class))).thenReturn(saved);
        when(itemRepo.findByWatchlistIdOrderByAddedAtAsc(5L)).thenReturn(Arrays.asList(new WatchlistItem(), new WatchlistItem()));

        CreateWatchlistDto dto = CreateWatchlistDto.builder().name("New").build();
        WatchlistDto res = watchlistService.renameWatchlist(5L, dto);

        assertEquals("New", res.getName());
        assertEquals(2, res.getItemCount());
    }

    @Test
    @DisplayName("renameWatchlist_throwsIfDuplicateName")
    void renameWatchlist_throwsIfDuplicateName() {
        when(userResolver.resolveCurrent()).thenReturn(clientCtx);
        Watchlist existing = Watchlist.builder().id(6L).ownerId(42L).ownerType(WatchlistOwnerType.CLIENT).name("A").createdAt(LocalDateTime.now()).build();
        when(watchlistRepo.findByIdAndOwnerIdAndOwnerType(6L, 42L, WatchlistOwnerType.CLIENT)).thenReturn(Optional.of(existing));
        when(watchlistRepo.existsByOwnerIdAndOwnerTypeAndName(42L, WatchlistOwnerType.CLIENT, "B")).thenReturn(true);

        CreateWatchlistDto dto = CreateWatchlistDto.builder().name("B").build();
        assertThatThrownBy(() -> watchlistService.renameWatchlist(6L, dto))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("renameWatchlist_throwsIfWatchlistNotFound")
    void renameWatchlist_throwsIfWatchlistNotFound() {
        when(userResolver.resolveCurrent()).thenReturn(clientCtx);
        when(watchlistRepo.findByIdAndOwnerIdAndOwnerType(7L, 42L, WatchlistOwnerType.CLIENT)).thenReturn(Optional.empty());

        CreateWatchlistDto dto = CreateWatchlistDto.builder().name("Whatever").build();
        assertThatThrownBy(() -> watchlistService.renameWatchlist(7L, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Lista ne postoji");
    }

    @Test
    @DisplayName("deleteWatchlist_success")
    void deleteWatchlist_success() {
        when(userResolver.resolveCurrent()).thenReturn(clientCtx);
        Watchlist existing = Watchlist.builder().id(8L).ownerId(42L).ownerType(WatchlistOwnerType.CLIENT).name("X").createdAt(LocalDateTime.now()).build();
        when(watchlistRepo.findByIdAndOwnerIdAndOwnerType(8L, 42L, WatchlistOwnerType.CLIENT)).thenReturn(Optional.of(existing));

        watchlistService.deleteWatchlist(8L);

        verify(itemRepo).deleteAllByWatchlistId(8L);
        verify(watchlistRepo).delete(existing);
    }

    @Test
    @DisplayName("addItem_success")
    void addItem_success() {
        when(userResolver.resolveCurrent()).thenReturn(clientCtx);
        Watchlist existing = Watchlist.builder().id(9L).ownerId(42L).ownerType(WatchlistOwnerType.CLIENT).name("L").createdAt(LocalDateTime.now()).build();
        when(watchlistRepo.findByIdAndOwnerIdAndOwnerType(9L, 42L, WatchlistOwnerType.CLIENT)).thenReturn(Optional.of(existing));
        when(itemRepo.existsByWatchlistIdAndListingId(9L, 10L)).thenReturn(false);

        ListingDto listing = new ListingDto();
        listing.setId(10L);
        listing.setTicker("AAPL");
        listing.setName("Apple Inc.");
        listing.setExchangeAcronym("NASDAQ");
        listing.setListingType("STOCK");
        listing.setPrice(new BigDecimal("150.25"));
        listing.setChangePercent(new BigDecimal("1.5"));
        listing.setVolume(1234567L);

        when(listingService.getListingById(10L)).thenReturn(listing);

        WatchlistItem savedItem = WatchlistItem.builder().id(11L).watchlistId(9L).listingId(10L).addedAt(LocalDateTime.now()).build();
        when(itemRepo.save(any(WatchlistItem.class))).thenReturn(savedItem);

        WatchlistItemDto dto = watchlistService.addItem(9L, 10L);

        assertEquals(11L, dto.getId());
        assertEquals(9L, dto.getWatchlistId());
        assertEquals(10L, dto.getListingId());
        assertEquals("AAPL", dto.getTicker());
        assertEquals("Apple Inc.", dto.getListingName());
        assertEquals("STOCK", dto.getSecurityType());
        assertEquals(new BigDecimal("150.25"), dto.getCurrentPrice());
    }

    @Test
    @DisplayName("addItem_throwsIfAlreadyOnList")
    void addItem_throwsIfAlreadyOnList() {
        when(userResolver.resolveCurrent()).thenReturn(clientCtx);
        Watchlist existing = Watchlist.builder().id(12L).ownerId(42L).ownerType(WatchlistOwnerType.CLIENT).name("L").createdAt(LocalDateTime.now()).build();
        when(watchlistRepo.findByIdAndOwnerIdAndOwnerType(12L, 42L, WatchlistOwnerType.CLIENT)).thenReturn(Optional.of(existing));
        when(itemRepo.existsByWatchlistIdAndListingId(12L, 20L)).thenReturn(true);

        assertThatThrownBy(() -> watchlistService.addItem(12L, 20L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Hartija je vec na listi");
    }

    @Test
    @DisplayName("removeItem_success")
    void removeItem_success() {
        when(userResolver.resolveCurrent()).thenReturn(clientCtx);
        Watchlist existing = Watchlist.builder().id(13L).ownerId(42L).ownerType(WatchlistOwnerType.CLIENT).name("L").createdAt(LocalDateTime.now()).build();
        when(watchlistRepo.findByIdAndOwnerIdAndOwnerType(13L, 42L, WatchlistOwnerType.CLIENT)).thenReturn(Optional.of(existing));
        WatchlistItem item = WatchlistItem.builder().id(21L).watchlistId(13L).listingId(30L).addedAt(LocalDateTime.now()).build();
        when(itemRepo.findByWatchlistIdAndListingId(13L, 30L)).thenReturn(Optional.of(item));

        watchlistService.removeItem(13L, 30L);

        verify(itemRepo).deleteByWatchlistIdAndListingId(13L, 30L);
    }

    @Test
    @DisplayName("listItems_filterBySecurityType_returnsOnlyMatchingItems")
    void listItems_filterBySecurityType_returnsOnlyMatchingItems() {
        when(userResolver.resolveCurrent()).thenReturn(clientCtx);
        Watchlist existing = Watchlist.builder().id(14L).ownerId(42L).ownerType(WatchlistOwnerType.CLIENT).name("L").createdAt(LocalDateTime.now()).build();
        when(watchlistRepo.findByIdAndOwnerIdAndOwnerType(14L, 42L, WatchlistOwnerType.CLIENT)).thenReturn(Optional.of(existing));

        WatchlistItem it1 = WatchlistItem.builder().id(31L).watchlistId(14L).listingId(101L).addedAt(LocalDateTime.now()).build();
        WatchlistItem it2 = WatchlistItem.builder().id(32L).watchlistId(14L).listingId(102L).addedAt(LocalDateTime.now()).build();
        WatchlistItem it3 = WatchlistItem.builder().id(33L).watchlistId(14L).listingId(103L).addedAt(LocalDateTime.now()).build();
        when(itemRepo.findByWatchlistIdOrderByAddedAtAsc(14L)).thenReturn(Arrays.asList(it1, it2, it3));

        ListingDto l1 = new ListingDto(); l1.setId(101L); l1.setListingType("STOCK"); l1.setTicker("T1"); l1.setName("N1"); l1.setPrice(new BigDecimal("10")); l1.setChangePercent(new BigDecimal("1")); l1.setVolume(100L);
        ListingDto l2 = new ListingDto(); l2.setId(102L); l2.setListingType("STOCK"); l2.setTicker("T2"); l2.setName("N2"); l2.setPrice(new BigDecimal("20")); l2.setChangePercent(new BigDecimal("2")); l2.setVolume(200L);
        ListingDto l3 = new ListingDto(); l3.setId(103L); l3.setListingType("FOREX"); l3.setTicker("T3"); l3.setName("N3"); l3.setPrice(new BigDecimal("30")); l3.setChangePercent(new BigDecimal("3")); l3.setVolume(300L);

        when(listingService.getListingById(101L)).thenReturn(l1);
        when(listingService.getListingById(102L)).thenReturn(l2);
        when(listingService.getListingById(103L)).thenReturn(l3);

        List<WatchlistItemDto> res = watchlistService.listItems(14L, "STOCK");
        assertEquals(2, res.size());
        assertThat(res).allMatch(d -> "STOCK".equalsIgnoreCase(d.getSecurityType()));
    }
}