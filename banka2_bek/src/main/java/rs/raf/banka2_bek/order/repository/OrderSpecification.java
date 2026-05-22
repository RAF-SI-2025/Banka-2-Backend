package rs.raf.banka2_bek.order.repository;

import org.springframework.data.jpa.domain.Specification;
import rs.raf.banka2_bek.order.model.Order;
import rs.raf.banka2_bek.order.model.OrderStatus;
import rs.raf.banka2_bek.stock.model.ListingType;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class OrderSpecification {

    private OrderSpecification() {}

    public static Specification<Order> hasUserId(Long userId) {
        return (root, query, cb) -> userId == null ? cb.conjunction()
                : cb.equal(root.get("userId"), userId);
    }

    public static Specification<Order> hasStatus(OrderStatus status) {
        return (root, query, cb) -> status == null ? cb.conjunction()
                : cb.equal(root.get("status"), status);
    }

    public static Specification<Order> createdAfter(LocalDate dateFrom) {
        return (root, query, cb) -> dateFrom == null ? cb.conjunction()
                : cb.greaterThanOrEqualTo(root.get("createdAt"), dateFrom.atStartOfDay());
    }

    public static Specification<Order> createdBefore(LocalDate dateTo) {
        return (root, query, cb) -> dateTo == null ? cb.conjunction()
                : cb.lessThan(root.get("createdAt"), dateTo.plusDays(1).atStartOfDay());
    }

    public static Specification<Order> hasListingType(ListingType listingType) {
        return (root, query, cb) -> {
            if (listingType == null) {
                return cb.conjunction();
            }
            return cb.equal(root.get("listing").get("listingType"), listingType);
        };
    }
}
