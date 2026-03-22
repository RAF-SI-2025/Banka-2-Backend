package rs.raf.banka2_bek.order.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import rs.raf.banka2_bek.order.model.Order;
import rs.raf.banka2_bek.order.model.OrderStatus;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    Page<Order> findByUserId(Long userId, Pageable pageable);

    List<Order> findByStatusAndIsDoneFalse(OrderStatus status);

    @Query("SELECT COALESCE(SUM(CASE WHEN o.direction = rs.raf.banka2_bek.order.model.OrderDirection.BUY " +
           "THEN o.quantity ELSE -o.quantity END), 0) FROM Order o " +
           "WHERE o.userId = :userId AND o.listing.id = :listingId AND o.isDone = true")
    int getNetPortfolioQuantity(@Param("userId") Long userId, @Param("listingId") Long listingId);
}
