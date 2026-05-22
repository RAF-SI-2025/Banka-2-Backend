package rs.raf.banka2_bek.order.service;

import org.springframework.data.domain.Page;
import rs.raf.banka2_bek.order.dto.CreateOrderDto;
import rs.raf.banka2_bek.order.dto.OrderDto;

import java.time.LocalDate;

public interface OrderService {

    /**
     * Kreiranje BUY ili SELL ordera.
     * Validacije:
     * 1. Da li korisnik ima dovoljno sredstava na racunu (za BUY)
     * 2. Da li korisnik poseduje dovoljno hartija (za SELL)
     * 3. Da li je agent presao dnevni limit -> status PENDING
     * 4. Da li agent ima needApproval flag -> status PENDING
     * 5. Klijentovi orderi su automatski APPROVED
     * 6. Izracunati approximatePrice = contractSize * pricePerUnit * quantity
     * 7. Provizija: Market 14% ili $7 (manje), Limit 24% ili $12 (manje)
     */
    OrderDto createOrder(CreateOrderDto dto);

    /**
     * Supervizor odobrava order.
     * 1. Proveriti da je order u statusu PENDING
     * 2. Proveriti da order nije vremenski ogranicen (settlementDate nije prosao)
     * 3. Postaviti status na APPROVED, approvedBy na ime supervizora
     * 4. Pokrenuti izvrsavanje ordera (asinhrono)
     */
    OrderDto approveOrder(Long orderId);

    /**
     * Supervizor odbija order.
     * 1. Proveriti da je order u statusu PENDING
     * 2. Postaviti status na DECLINED, approvedBy na ime supervizora
     */
    OrderDto declineOrder(Long orderId);

    /**
     * Parcijalno otkazivanje ordera koji nije u potpunosti ispunjen.
     * Specifikacija: "Treba omogućiti otkazivanje celog ili dela Order-a
     * koji još uvek nije ispunjen" (Celina 3, deo Portal Pregled ordera).
     * <p>
     * Ako je {@code quantityToCancel} null, >= remainingPortions ili <= 0,
     * ponasa se kao {@link #declineOrder}. Inace skracuje remainingPortions,
     * oslobadja pro-ratu rezervacije (BUY) ili akcija (SELL) i eventualni
     * deo agentovog usedLimit — status naloga ostaje APPROVED.
     */
    OrderDto cancelOrder(Long orderId, Integer quantityToCancel);

    /**
     * Pregled svih ordera (za supervizora).
     * Filtriranje po statusu: ALL, PENDING, APPROVED, DECLINED, DONE
     */
    Page<OrderDto> getAllOrders(String status, int page, int size);

    /**
     * Pregled ordera jednog korisnika (za korisnika) sa opcionim filterima.
     *
     * @param page       nula-bazirani broj stranice
     * @param size       velicina stranice
     * @param status     opcionalni filter po statusu (PENDING, APPROVED, DECLINED, DONE)
     * @param dateFrom   opcionalni filter — orderi kreirani posle ovog datuma (ukljucivo)
     * @param dateTo     opcionalni filter — orderi kreirani pre ovog datuma (ukljucivo)
     * @param listingType opcionalni filter po tipu hartije (STOCK, FUTURES, FOREX)
     */
    Page<OrderDto> getMyOrders(int page, int size, String status, LocalDate dateFrom, LocalDate dateTo, String listingType);

    /**
     * Detalji jednog ordera.
     */
    OrderDto getOrderById(Long orderId);
}
