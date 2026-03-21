package rs.raf.banka2_bek.order.service.implementation;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import rs.raf.banka2_bek.actuary.repository.ActuaryInfoRepository;
import rs.raf.banka2_bek.order.dto.CreateOrderDto;
import rs.raf.banka2_bek.order.dto.OrderDto;
import rs.raf.banka2_bek.order.model.OrderStatus;
import rs.raf.banka2_bek.order.repository.OrderRepository;
import rs.raf.banka2_bek.order.service.OrderService;
import rs.raf.banka2_bek.stock.repository.ListingRepository;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final ListingRepository listingRepository;
    private final ActuaryInfoRepository actuaryInfoRepository;

    @Override
    public OrderDto createOrder(CreateOrderDto dto) {
        // TODO: Implementirati kreiranje ordera
        //
        // KORAK 1: Validacija ulaznih podataka
        //   - Parsirati orderType (MARKET, LIMIT, STOP, STOP_LIMIT)
        //   - Parsirati direction (BUY, SELL)
        //   - Proveriti da listing postoji
        //   - Za LIMIT order: limitValue mora biti zadat
        //   - Za STOP order: stopValue mora biti zadat
        //   - Za STOP_LIMIT: oba moraju biti zadati
        //
        // KORAK 2: Odrediti cenu
        //   - MARKET: pricePerUnit = listing.ask (za BUY) ili listing.bid (za SELL)
        //   - LIMIT: pricePerUnit = limitValue
        //   - STOP: pricePerUnit = stopValue
        //   - STOP_LIMIT: pricePerUnit = stopValue
        //   - approximatePrice = contractSize * pricePerUnit * quantity
        //
        // KORAK 3: Validacija sredstava (za BUY)
        //   - Dohvatiti racun po accountId
        //   - Proveriti da racun ima dovoljno sredstava (balance >= approximatePrice + provizija)
        //   - Provizija: Market = min(14% * approxPrice, $7), Limit = min(24% * approxPrice, $12)
        //
        // KORAK 4: Odrediti status
        //   - Ako je korisnik KLIJENT: status = APPROVED, approvedBy = "No need for approval"
        //   - Ako je korisnik AGENT:
        //     a) Ako agent.needApproval == true -> PENDING
        //     b) Ako agent.usedLimit + approximatePrice > agent.dailyLimit -> PENDING
        //     c) Inace -> APPROVED, approvedBy = "No need for approval"
        //   - Ako je korisnik SUPERVISOR: status = APPROVED, approvedBy = "No need for approval"
        //
        // KORAK 5: Kreirati i sacuvati Order entitet
        //   - contractSize = listing.contractSize (ili 1 ako nije zadat)
        //   - remainingPortions = quantity
        //   - isDone = false
        //   - afterHours = proveriti da li je berza zatvorena ili manje od 4h do zatvaranja
        //   - createdAt = now()
        //   - lastModification = now()
        //
        // KORAK 6: Azurirati agentov usedLimit (ako je APPROVED)
        //   - agent.usedLimit += approximatePrice (konvertovano u RSD ako je druga valuta)
        //
        // KORAK 7: Ako je APPROVED, pokrenuti izvrsavanje (asinhrono)
        //   - TODO: Ovo ce biti implementirano u buducim sprintovima
        //
        throw new UnsupportedOperationException("TODO: Implementirati createOrder");
    }

    @Override
    public OrderDto approveOrder(Long orderId) {
        // TODO: Implementirati odobravanje ordera
        // 1. Naci order po ID-ju, proveriti da je PENDING
        // 2. Proveriti da settlement date nije prosao (za futures/opcije)
        //    - Ako jeste, automatski DECLINE
        // 3. Postaviti status = APPROVED
        // 4. Postaviti approvedBy = ime ulogovanog supervizora
        // 5. Postaviti lastModification = now()
        // 6. Sacuvati
        // 7. Pokrenuti izvrsavanje (asinhrono) - buduci sprint
        throw new UnsupportedOperationException("TODO: Implementirati approveOrder");
    }

    @Override
    public OrderDto declineOrder(Long orderId) {
        // TODO: Implementirati odbijanje ordera
        // 1. Naci order po ID-ju, proveriti da je PENDING
        // 2. Postaviti status = DECLINED
        // 3. Postaviti approvedBy = ime ulogovanog supervizora
        // 4. Postaviti lastModification = now()
        // 5. Sacuvati
        throw new UnsupportedOperationException("TODO: Implementirati declineOrder");
    }

    @Override
    public Page<OrderDto> getAllOrders(String status, int page, int size) {
        // TODO: Implementirati
        // 1. Ako je status "ALL" ili null, dohvatiti sve ordere
        // 2. Inace, filtrirati po statusu (PENDING, APPROVED, DECLINED, DONE)
        // 3. Sortirati po createdAt DESC
        // 4. Mapirati u OrderDto
        throw new UnsupportedOperationException("TODO: Implementirati getAllOrders");
    }

    @Override
    public Page<OrderDto> getMyOrders(int page, int size) {
        // TODO: Implementirati
        // 1. Dohvatiti email iz SecurityContext
        // 2. Naci userId na osnovu emaila
        // 3. Dohvatiti ordere za tog korisnika
        // 4. Mapirati u OrderDto
        throw new UnsupportedOperationException("TODO: Implementirati getMyOrders");
    }

    @Override
    public OrderDto getOrderById(Long orderId) {
        // TODO: Implementirati
        // 1. Naci order po ID-ju
        // 2. Proveriti da korisnik ima pristup (svoj order ili supervizor)
        // 3. Mapirati u OrderDto
        throw new UnsupportedOperationException("TODO: Implementirati getOrderById");
    }
}
