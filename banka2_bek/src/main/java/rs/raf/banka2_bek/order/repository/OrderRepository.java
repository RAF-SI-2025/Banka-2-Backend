package rs.raf.banka2_bek.order.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import rs.raf.banka2_bek.order.model.Order;
import rs.raf.banka2_bek.order.model.OrderStatus;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    /**
     * Pessimistic write lock — koristi se u approve/decline flow-u da spreci
     * race izmedju supervizorskih akcija i scheduler fill-ova.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    Page<Order> findByUserId(Long userId, Pageable pageable);

    List<Order> findByStatusAndIsDoneFalse(OrderStatus status);

    List<Order> findByUserIdAndUserRole(Long userId, String userRole);
    @Query("SELECT o FROM Order o WHERE o.isDone = false " +
           "AND o.status IN (rs.raf.banka2_bek.order.model.OrderStatus.PENDING, " +
           "rs.raf.banka2_bek.order.model.OrderStatus.APPROVED)")
    List<Order> findActiveNonDone();

    @Query("SELECT COALESCE(SUM(CASE WHEN o.direction = rs.raf.banka2_bek.order.model.OrderDirection.BUY " +
           "THEN o.quantity ELSE -o.quantity END), 0) FROM Order o " +
           "WHERE o.userId = :userId AND o.listing.id = :listingId AND o.isDone = true")
    int getNetPortfolioQuantity(@Param("userId") Long userId, @Param("listingId") Long listingId);

    List<Order> findByIsDoneTrue();
}
