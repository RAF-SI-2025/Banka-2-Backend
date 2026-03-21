package rs.raf.banka2_bek.order.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import rs.raf.banka2_bek.order.model.Order;
import rs.raf.banka2_bek.order.model.OrderStatus;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    Page<Order> findByUserId(Long userId, Pageable pageable);

    List<Order> findByStatusAndIsDoneFalse(OrderStatus status);

    // TODO: Dodati query metode po potrebi:
    // - findByUserIdAndStatus(userId, status, pageable) - filtriranje po statusu za korisnika
    // - findByListingId(listingId, pageable) - svi orderi za jednu hartiju
}
