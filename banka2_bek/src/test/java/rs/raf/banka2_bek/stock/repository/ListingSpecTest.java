package rs.raf.banka2_bek.stock.repository;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.model.ListingType;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListingSpecTest {

    private Root<Listing> root;
    private CriteriaQuery<?> query;
    private CriteriaBuilder cb;
    private Predicate predicate;
    private Expression expression;
    private Path path;

    @BeforeEach
    void setUp() {
        root = mock(Root.class);
        query = mock(CriteriaQuery.class);
        cb = mock(CriteriaBuilder.class);
        predicate = mock(Predicate.class);
        expression = mock(Expression.class);
        path = mock(Path.class);

        when(root.get(anyString())).thenReturn(path);
        when(cb.equal(any(Expression.class), any())).thenReturn(predicate);
        when(cb.lower(any(Expression.class))).thenReturn(expression);
        when(cb.like(any(Expression.class), anyString())).thenReturn(predicate);
        when(cb.or(any(Predicate.class), any(Predicate.class))).thenReturn(predicate);
        when(cb.greaterThanOrEqualTo(any(Expression.class), any(Comparable.class))).thenReturn(predicate);
        when(cb.lessThanOrEqualTo(any(Expression.class), any(Comparable.class))).thenReturn(predicate);
        when(cb.and(any(Predicate[].class))).thenReturn(predicate);
    }

    @Test
    void withFiltersAllNullsExceptTypeAddsOnlyTypePredicate() {
        Specification<Listing> spec = ListingSpec.withFilters(
                ListingType.STOCK, null, null, null, null, null, null);

        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isNotNull();
        verify(cb, never()).like(any(Expression.class), anyString());
        verify(cb, never()).greaterThanOrEqualTo(any(Expression.class), any(Comparable.class));
        verify(cb, never()).lessThanOrEqualTo(any(Expression.class), any(Comparable.class));
    }

    @Test
    void withFiltersBlankSearchAndExchangeSkipsLike() {
        Specification<Listing> spec = ListingSpec.withFilters(
                ListingType.FUTURES, "   ", "", null, null, null, null);

        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isNotNull();
        verify(cb, never()).like(any(Expression.class), anyString());
    }

    @Test
    void withFiltersSearchProvidedAddsTickerNameLike() {
        Specification<Listing> spec = ListingSpec.withFilters(
                ListingType.STOCK, "AAPL", null, null, null, null, null);

        spec.toPredicate(root, query, cb);

        verify(cb, atLeast(2)).like(any(Expression.class), anyString());
        verify(cb).or(any(Predicate.class), any(Predicate.class));
    }

    @Test
    void withFiltersExchangePrefixAddsLike() {
        Specification<Listing> spec = ListingSpec.withFilters(
                ListingType.STOCK, null, "NYSE", null, null, null, null);

        spec.toPredicate(root, query, cb);

        verify(cb, atLeastOnce()).like(any(Expression.class), anyString());
    }

    @Test
    void withFiltersPriceMinOnly() {
        Specification<Listing> spec = ListingSpec.withFilters(
                ListingType.STOCK, null, null, new BigDecimal("10"), null, null, null);

        spec.toPredicate(root, query, cb);

        verify(cb).greaterThanOrEqualTo(any(Expression.class), any(Comparable.class));
        verify(cb, never()).lessThanOrEqualTo(any(Expression.class), any(Comparable.class));
    }

    @Test
    void withFiltersPriceMaxOnly() {
        Specification<Listing> spec = ListingSpec.withFilters(
                ListingType.STOCK, null, null, null, new BigDecimal("100"), null, null);

        spec.toPredicate(root, query, cb);

        verify(cb).lessThanOrEqualTo(any(Expression.class), any(Comparable.class));
    }

    @Test
    void withFiltersSettlementDateFromAndTo() {
        Specification<Listing> spec = ListingSpec.withFilters(
                ListingType.FUTURES, null, null, null, null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        spec.toPredicate(root, query, cb);

        verify(cb, atLeastOnce()).greaterThanOrEqualTo(any(Expression.class), any(Comparable.class));
        verify(cb, atLeastOnce()).lessThanOrEqualTo(any(Expression.class), any(Comparable.class));
    }

    @Test
    void withFiltersAllFiltersSet() {
        Specification<Listing> spec = ListingSpec.withFilters(
                ListingType.STOCK,
                "tesla",
                "NAS",
                new BigDecimal("10"),
                new BigDecimal("1000"),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31));

        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isNotNull();
        verify(cb).and(any(Predicate[].class));
    }
}
