package rs.raf.banka2_bek.recurringorder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.auth.util.UserContext;
import rs.raf.banka2_bek.auth.util.UserResolver;
import rs.raf.banka2_bek.notification.repository.NotificationRepository;
import rs.raf.banka2_bek.order.dto.CreateOrderDto;
import rs.raf.banka2_bek.order.service.OrderService;
import rs.raf.banka2_bek.recurringorder.dto.CreateRecurringOrderDto;
import rs.raf.banka2_bek.recurringorder.model.RecurringCadence;
import rs.raf.banka2_bek.recurringorder.model.RecurringMode;
import rs.raf.banka2_bek.recurringorder.model.RecurringOrder;
import rs.raf.banka2_bek.recurringorder.repository.RecurringOrderRepository;
import rs.raf.banka2_bek.recurringorder.service.RecurringOrderService;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.repository.ListingRepository;
import rs.raf.banka2_bek.client.model.Client;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

// ============================================================
// TODO [B8 - Trajni nalozi (DCA / RecurringOrder) | Nosilac: Nikola Djurovic]
//
// Unit testovi za RecurringOrderService sa Mockito strict stubbing.
// Sablon iz projekta: SavingsDepositServiceTest, SavingsSchedulerTest.
//
// IMPLEMENTIRATI — dodati @Mock polja i @InjectMocks:
//   @Mock RecurringOrderRepository recurringOrderRepo
//   @Mock rs.raf.banka2_bek.auth.util.UserResolver userResolver
//   @Mock rs.raf.banka2_bek.order.service.OrderServiceImpl orderService
//   @Mock rs.raf.banka2_bek.account.repository.AccountRepository accountRepo
//   @Mock rs.raf.banka2_bek.listing.repository.ListingRepository listingRepo
//   @InjectMocks RecurringOrderService recurringOrderService
//
// IMPLEMENTIRATI — dodati sledece @Test metode (jedan @Test po scenariju,
// koristiti AssertJ assertThat / assertThatThrownBy kao u ostalim testovima):
//
//   create_clientCanCreateRecurringOrder()
//       -> Mock userResolver da vrati CLIENT kontekst
//       -> Mock accountRepo da vrati aktivan racun koji pripada klijentu
//       -> Mock listingRepo da vrati listing sa zadatim ID-em
//       -> Mock recurringOrderRepo.save() da vrati RecurringOrder sa id=1L
//       -> Pozvati create(dto) i assertovati da je vratio DTO sa ispravnim vrednostima
//
//   create_employeeBlockedWithoutValidAccount()
//       -> Mock userResolver da vrati EMPLOYEE kontekst
//       -> Mock accountRepo da vrati racun koji pripada drugom klijentu (ne zaposlenom)
//       -> Pozvati create(dto) i assertovati da baca AccessDeniedException ili
//          IllegalArgumentException sa porukom o vlasnistvu
//
//   create_invalidListingThrows()
//       -> Mock listingRepo.findById() da vrati Optional.empty()
//       -> assertThatThrownBy da baca IllegalArgumentException("Hartija od vrednosti ne postoji")
//
//   listMy_returnsOnlyOwnersOrders()
//       -> Mock recurringOrderRepo.findByOwnerIdAndOwnerTypeOrderByCreatedAtDesc()
//          da vrati listu sa 2 naloga
//       -> Pozvati listMy() i assertovati da su vracena tacno 2 DTO-a
//
//   pause_setsActiveToFalse()
//       -> Pripremiti RecurringOrder sa active=true
//       -> Mock recurringOrderRepo.findById() da vrati taj nalog
//       -> Mock userResolver da vrati isti userId kao ownerId
//       -> Pozvati pause(id) i assertovati da je active=false u sacuvanom entitetu
//          (koristiti ArgumentCaptor<RecurringOrder>)
//
//   pause_throwsWhenNotOwner()
//       -> Mock userResolver da vrati drugaciji userId od ownerId u nalogu
//       -> assertThatThrownBy(AccessDeniedException)
//
//   resume_setsActiveToTrueAndAdvancesNextRun()
//       -> Pripremiti pauzirani nalog (active=false, nextRun u proslosti)
//       -> Pozvati resume(id)
//       -> Assertovati da active=true i nextRun je u buducnosti (posle now)
//
//   cancel_deletesOrder()
//       -> Pozvati cancel(id)
//       -> Verifikovati da je recurringOrderRepo.delete() ili deleteById() pozvan tacno jednom
//
//   executeOne_byQuantity_createsMarketOrder()
//       -> Pripremiti RecurringOrder sa mode=BY_QUANTITY, value=5, direction="BUY"
//       -> Mock listingRepo da vrati listing sa currentPrice != null
//       -> Mock accountRepo da vrati racun sa availableBalance dovoljnim za kupovinu
//       -> Pozvati executeOne(order)
//       -> Verifikovati da je orderService.createOrder() pozvan sa quantity=5
//       -> Verifikovati da je nextRun azuriran (posle poziva advanceNextRun)
//
//   executeOne_byAmount_calculatesQuantityFromPrice()
//       -> Pripremiti nalog sa mode=BY_AMOUNT, value=1000 (EUR), direction="BUY"
//       -> Mock currentPrice = 200 -> ocekivana kolicina = floor(1000/200) = 5
//       -> Verifikovati da je orderService.createOrder() pozvan sa quantity=5
//
//   executeOne_insufficientFunds_skipsAndAdvancesNextRun()
//       -> Mock account.getAvailableBalance() = 0 (nedovoljno)
//       -> Pozvati executeOne(order)
//       -> Verifikovati da orderService.createOrder() NIJE pozvan (verify(orderService, never()))
//       -> Verifikovati da je nextRun ipak azuriran (nalog nije obrisan)
//
//   executeOne_quantityLessThanOne_skips()
//       -> BY_AMOUNT sa value=0.5, currentPrice=200 -> floor=0
//       -> Verifikovati da orderService.createOrder() NIJE pozvan
//
//   executeOne_actuarySpendingCountsTowardDailyLimit()
//       -> Mock ownerType="EMPLOYEE"
//       -> Verifikovati da se aktuarov usedLimit/dnevni limit azurira posle kreiranja ordera
//          (mock ActuaryService ili direktno actuary.usedLimit)
//
// Konvencija: pratiti SavingsDepositServiceTest / SavingsSchedulerTest kao sablon.
// Spec: Zadaci_Backend.pdf, zadatak B8.
// ============================================================
@ExtendWith(MockitoExtension.class)
public class RecurringOrderServiceTest {

    @Mock
    private RecurringOrderRepository recurringOrderRepo;

    @Mock
    private UserResolver userResolver;

    @Mock
    private OrderService orderService;

    @Mock
    private AccountRepository accountRepo;

    @Mock
    private ListingRepository listingRepo;

    @Mock
    private NotificationRepository notificationRepo;

    @InjectMocks
    private RecurringOrderService recurringOrderService;

    private UserContext clientContext;
    private UserContext employeeContext;

    @BeforeEach
    void setUp() {
        clientContext = new UserContext(1L, "CLIENT");
        employeeContext = new UserContext(2L, "EMPLOYEE");
    }

    @Test
    void create_clientCanCreateRecurringOrder() {
        // Arrange
        when(userResolver.resolveCurrent()).thenReturn(clientContext);

        Client client = new Client();
        client.setId(1L);

        Account account = new Account();
        account.setId(1L);
        account.setClient(client);
        when(accountRepo.findById(1L)).thenReturn(Optional.of(account));

        Listing listing = new Listing();
        listing.setId(1L);
        listing.setTicker("AAPL");
        listing.setPrice(new BigDecimal("150"));
        when(listingRepo.findById(1L)).thenReturn(Optional.of(listing));

        RecurringOrder savedOrder = RecurringOrder.builder()
                .id(1L)
                .ownerId(1L)
                .ownerType("CLIENT")
                .listingId(1L)
                .direction("BUY")
                .mode(RecurringMode.BY_QUANTITY)
                .value(new BigDecimal("5"))
                .accountId(1L)
                .cadence(RecurringCadence.DAILY)
                .nextRun(LocalDateTime.now(ZoneOffset.UTC).plusDays(1))
                .active(true)
                .build();
        when(recurringOrderRepo.save(any())).thenReturn(savedOrder);

        CreateRecurringOrderDto dto = new CreateRecurringOrderDto();
        dto.setListingId(1L);
        dto.setDirection("BUY");
        dto.setMode(RecurringMode.BY_QUANTITY);
        dto.setValue(new BigDecimal("5"));
        dto.setAccountId(1L);
        dto.setCadence(RecurringCadence.DAILY);

        // Act
        var result = recurringOrderService.create(dto);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getOwnerId()).isEqualTo(1L);
        assertThat(result.getOwnerType()).isEqualTo("CLIENT");
        verify(recurringOrderRepo).save(any(RecurringOrder.class));
    }

    @Test
    void create_employeeBlockedWithoutValidAccount() {
        // Arrange
        when(userResolver.resolveCurrent()).thenReturn(employeeContext);

        Client client = new Client();
        client.setId(999L);

        Account account = new Account();
        account.setId(1L);
        account.setClient(client);
        when(accountRepo.findById(1L)).thenReturn(Optional.of(account));

        CreateRecurringOrderDto dto = new CreateRecurringOrderDto();
        dto.setListingId(1L);
        dto.setDirection("BUY");
        dto.setMode(RecurringMode.BY_QUANTITY);
        dto.setValue(new BigDecimal("5"));
        dto.setAccountId(1L);
        dto.setCadence(RecurringCadence.DAILY);

        // Act & Assert
        assertThatThrownBy(() -> recurringOrderService.create(dto))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void create_invalidListingThrows() {
        // Arrange
        when(userResolver.resolveCurrent()).thenReturn(clientContext);

        Client client = new Client();
        client.setId(1L);

        Account account = new Account();
        account.setId(1L);
        account.setClient(client);
        when(accountRepo.findById(1L)).thenReturn(Optional.of(account));

        when(listingRepo.findById(999L)).thenReturn(Optional.empty());

        CreateRecurringOrderDto dto = new CreateRecurringOrderDto();
        dto.setListingId(999L);
        dto.setDirection("BUY");
        dto.setMode(RecurringMode.BY_QUANTITY);
        dto.setValue(new BigDecimal("5"));
        dto.setAccountId(1L);
        dto.setCadence(RecurringCadence.DAILY);

        // Act & Assert
        assertThatThrownBy(() -> recurringOrderService.create(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Hartija od vrednosti ne postoji");
    }

    @Test
    void pause_setsActiveToFalse() {
        // Arrange
        RecurringOrder order = RecurringOrder.builder()
                .id(1L)
                .ownerId(1L)
                .ownerType("CLIENT")
                .listingId(1L)
                .direction("BUY")
                .mode(RecurringMode.BY_QUANTITY)
                .value(new BigDecimal("5"))
                .accountId(1L)
                .cadence(RecurringCadence.DAILY)
                .active(true)
                .build();

        when(recurringOrderRepo.findById(1L)).thenReturn(Optional.of(order));
        when(userResolver.resolveCurrent()).thenReturn(clientContext);

        RecurringOrder pausedOrder = order;
        pausedOrder.setActive(false);
        when(recurringOrderRepo.save(any())).thenReturn(pausedOrder);

        Listing listing = new Listing();
        listing.setId(1L);
        listing.setTicker("AAPL");
        when(listingRepo.findById(1L)).thenReturn(Optional.of(listing));

        // Act
        var result = recurringOrderService.pause(1L);

        // Assert
        assertThat(result.isActive()).isFalse();
        ArgumentCaptor<RecurringOrder> captor = ArgumentCaptor.forClass(RecurringOrder.class);
        verify(recurringOrderRepo).save(captor.capture());
        assertThat(captor.getValue().isActive()).isFalse();
    }

    @Test
    void pause_throwsWhenNotOwner() {
        // Arrange
        RecurringOrder order = RecurringOrder.builder()
                .id(1L)
                .ownerId(999L)
                .ownerType("CLIENT")
                .build();

        when(recurringOrderRepo.findById(1L)).thenReturn(Optional.of(order));
        when(userResolver.resolveCurrent()).thenReturn(clientContext);

        // Act & Assert
        assertThatThrownBy(() -> recurringOrderService.pause(1L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void executeOne_byQuantity_createsMarketOrder() {
        // Arrange
        Listing listing = new Listing();
        listing.setId(1L);
        listing.setTicker("AAPL");
        listing.setPrice(new BigDecimal("150"));
        when(listingRepo.findById(1L)).thenReturn(Optional.of(listing));

        Account account = new Account();
        account.setId(1L);
        account.setAvailableBalance(new BigDecimal("1000"));
        account.setDailyLimit(new BigDecimal("10000"));
        account.setDailySpending(BigDecimal.ZERO);
        when(accountRepo.findById(1L)).thenReturn(Optional.of(account));

        RecurringOrder order = RecurringOrder.builder()
                .id(1L)
                .ownerId(1L)
                .ownerType("CLIENT")
                .listingId(1L)
                .direction("BUY")
                .mode(RecurringMode.BY_QUANTITY)
                .value(new BigDecimal("5"))
                .accountId(1L)
                .cadence(RecurringCadence.DAILY)
                .nextRun(LocalDateTime.now(ZoneOffset.UTC))
                .active(true)
                .build();

        when(recurringOrderRepo.save(any())).thenReturn(order);

        // Act
        recurringOrderService.executeOne(order);

        // Assert
        ArgumentCaptor<CreateOrderDto> captor = ArgumentCaptor.forClass(CreateOrderDto.class);
        verify(orderService).createOrder(captor.capture());
        CreateOrderDto createdOrder = captor.getValue();
        assertThat(createdOrder.getQuantity()).isEqualTo(5);
        assertThat(createdOrder.getOrderType()).isEqualTo("MARKET");
        assertThat(createdOrder.getDirection()).isEqualTo("BUY");
    }

    @Test
    void executeOne_byAmount_calculatesQuantityFromPrice() {
        // Arrange
        Listing listing = new Listing();
        listing.setId(1L);
        listing.setTicker("AAPL");
        listing.setPrice(new BigDecimal("200"));
        when(listingRepo.findById(1L)).thenReturn(Optional.of(listing));

        Account account = new Account();
        account.setId(1L);
        account.setAvailableBalance(new BigDecimal("2000"));
        account.setDailyLimit(new BigDecimal("10000"));
        account.setDailySpending(BigDecimal.ZERO);
        when(accountRepo.findById(1L)).thenReturn(Optional.of(account));

        RecurringOrder order = RecurringOrder.builder()
                .id(1L)
                .ownerId(1L)
                .ownerType("CLIENT")
                .listingId(1L)
                .direction("BUY")
                .mode(RecurringMode.BY_AMOUNT)
                .value(new BigDecimal("1000"))
                .accountId(1L)
                .cadence(RecurringCadence.DAILY)
                .nextRun(LocalDateTime.now(ZoneOffset.UTC))
                .active(true)
                .build();

        when(recurringOrderRepo.save(any())).thenReturn(order);

        // Act
        recurringOrderService.executeOne(order);

        // Assert
        ArgumentCaptor<CreateOrderDto> captor = ArgumentCaptor.forClass(CreateOrderDto.class);
        verify(orderService).createOrder(captor.capture());
        CreateOrderDto createdOrder = captor.getValue();
        assertThat(createdOrder.getQuantity()).isEqualTo(5); // floor(1000/200) = 5
    }

    @Test
    void executeOne_insufficientFunds_skipsAndAdvancesNextRun() {
        // Arrange
        Listing listing = new Listing();
        listing.setId(1L);
        listing.setTicker("AAPL");
        listing.setPrice(new BigDecimal("150"));
        when(listingRepo.findById(1L)).thenReturn(Optional.of(listing));

        Account account = new Account();
        account.setId(1L);
        account.setAvailableBalance(BigDecimal.ZERO);
        account.setDailyLimit(new BigDecimal("10000"));
        account.setDailySpending(BigDecimal.ZERO);
        when(accountRepo.findById(1L)).thenReturn(Optional.of(account));

        RecurringOrder order = RecurringOrder.builder()
                .id(1L)
                .ownerId(1L)
                .ownerType("CLIENT")
                .listingId(1L)
                .direction("BUY")
                .mode(RecurringMode.BY_QUANTITY)
                .value(new BigDecimal("5"))
                .accountId(1L)
                .cadence(RecurringCadence.DAILY)
                .nextRun(LocalDateTime.now(ZoneOffset.UTC))
                .active(true)
                .build();

        when(recurringOrderRepo.save(any())).thenReturn(order);

        // Act
        recurringOrderService.executeOne(order);

        // Assert
        verify(orderService, never()).createOrder(any());
        verify(recurringOrderRepo).save(any());
    }

    @Test
    void executeOne_quantityLessThanOne_skips() {
        // Arrange
        Listing listing = new Listing();
        listing.setId(1L);
        listing.setTicker("AAPL");
        listing.setPrice(new BigDecimal("200"));
        when(listingRepo.findById(1L)).thenReturn(Optional.of(listing));

        RecurringOrder order = RecurringOrder.builder()
                .id(1L)
                .ownerId(1L)
                .ownerType("CLIENT")
                .listingId(1L)
                .direction("BUY")
                .mode(RecurringMode.BY_AMOUNT)
                .value(new BigDecimal("0.5")) // floor(0.5/200) = 0
                .accountId(1L)
                .cadence(RecurringCadence.DAILY)
                .nextRun(LocalDateTime.now(ZoneOffset.UTC))
                .active(true)
                .build();

        when(recurringOrderRepo.save(any())).thenReturn(order);

        // Act
        recurringOrderService.executeOne(order);

        // Assert
        verify(orderService, never()).createOrder(any());
        verify(recurringOrderRepo).save(any());
    }
}
