package rs.raf.banka2_bek.order.event;

/**
 * Spring application event emit-ovan kad order predje u status DONE.
 *
 * <p>Konzumenti mogu da prate ovu informaciju za invalidaciju cached izvedenih
 * polja (npr. <code>actuary-profit</code> Caffeine cache u
 * {@code ProfitBankCacheConfig}). Slanje ide kroz {@code ApplicationEventPublisher}
 * iz {@code OrderExecutionService} posle uspesnog `orderRepository.save(order)`.</p>
 *
 * @param orderId   PK order-a koji je upravo zavrsen
 * @param userId    vlasnik order-a (klijent.id, employee.id, ili fund.id ako je FUND)
 * @param userRole  "CLIENT" / "EMPLOYEE" / "FUND"
 * @param fundId    ne-null ako je trade u ime fonda; inace null
 */
public record OrderCompletedEvent(Long orderId, Long userId, String userRole, Long fundId) {
}
