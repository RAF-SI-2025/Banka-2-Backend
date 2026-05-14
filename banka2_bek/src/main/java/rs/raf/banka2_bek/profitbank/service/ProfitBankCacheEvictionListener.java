package rs.raf.banka2_bek.profitbank.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.order.event.OrderCompletedEvent;

/**
 * Sluša {@link OrderCompletedEvent} i invalidira <code>actuary-profit</code>
 * Caffeine cache kad order predje u DONE.
 *
 * <p>Bez evict-a, supervizor bi video stale profit izracune dok TTL ne istekne
 * (5 min); ovo daje real-time osvezavanje cim fill engine zavrsi nalog.</p>
 *
 * <p>SpEL <code>condition</code> filtrira na zaposlene ordere (ActuaryProfitService
 * vec ignorise CLIENT i FUND), izbegavajuci nepotrebne evict-e za klijentske
 * trgovine. <code>allEntries=true</code> jer cache trenutno ima jedan key.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProfitBankCacheEvictionListener {

    @EventListener
    @CacheEvict(
            value = ProfitBankCacheConfig.ACTUARY_PROFIT_CACHE,
            allEntries = true,
            condition = "#event.userRole() == 'EMPLOYEE' || #event.userRole() == 'ROLE_EMPLOYEE'"
    )
    public void onOrderCompleted(OrderCompletedEvent event) {
        log.debug("Evicting actuary-profit cache after order #{} ({} #{}) completed",
                event.orderId(), event.userRole(), event.userId());
    }
}
