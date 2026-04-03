package rs.raf.banka2_bek.order.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import rs.raf.banka2_bek.order.model.Order;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for AonValidationService covering:
 * - Non-AON orders always pass
 * - AON orders pass when available >= remaining
 * - AON orders fail when available < remaining
 * - Edge: available exactly equals remaining
 */
@DisplayName("AonValidationService")
class AonValidationServiceTest {

    private final AonValidationService service = new AonValidationService();

    private Order buildOrder(boolean aon, int remainingPortions) {
        Order o = new Order();
        o.setId(1L);
        o.setAllOrNone(aon);
        o.setRemainingPortions(remainingPortions);
        return o;
    }

    @Test
    @DisplayName("non-AON order always returns true regardless of volume")
    void nonAon_alwaysTrue() {
        Order order = buildOrder(false, 100);

        assertThat(service.checkCanExecuteAon(order, 1)).isTrue();
        assertThat(service.checkCanExecuteAon(order, 0)).isTrue();
    }

    @Test
    @DisplayName("AON order returns true when available >= remaining")
    void aon_sufficientVolume() {
        Order order = buildOrder(true, 10);

        assertThat(service.checkCanExecuteAon(order, 10)).isTrue();
        assertThat(service.checkCanExecuteAon(order, 100)).isTrue();
    }

    @Test
    @DisplayName("AON order returns false when available < remaining")
    void aon_insufficientVolume() {
        Order order = buildOrder(true, 10);

        assertThat(service.checkCanExecuteAon(order, 9)).isFalse();
        assertThat(service.checkCanExecuteAon(order, 0)).isFalse();
    }

    @Test
    @DisplayName("AON order with remaining=1 passes with available=1")
    void aon_singleUnit() {
        Order order = buildOrder(true, 1);

        assertThat(service.checkCanExecuteAon(order, 1)).isTrue();
        assertThat(service.checkCanExecuteAon(order, 0)).isFalse();
    }

    @Test
    @DisplayName("AON order with remaining=0 always passes")
    void aon_zeroRemaining() {
        Order order = buildOrder(true, 0);

        assertThat(service.checkCanExecuteAon(order, 0)).isTrue();
    }
}
