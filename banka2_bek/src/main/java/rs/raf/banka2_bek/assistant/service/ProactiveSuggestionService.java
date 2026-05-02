package rs.raf.banka2_bek.assistant.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountStatus;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.assistant.config.AssistantProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 5 — proaktivne ponude. Analizira korisnikov state i emituje
 * preporuke koje FE moze prikazati kao tooltip iznad FAB-a.
 *
 * Strategije (low-risk, samo sugestije bez akcija):
 *   1. "Imas X RSD nedirano vec mesec dana" → predlozi fond.
 *   2. "Tvoj racun je u valuti razlicitoj od listinga" → upozori pri OTC-u.
 *   3. "Imas pending order vise od 24h" → predlozi cancel.
 *
 * NAPOMENA: ne emituje action_preview event (to je rezervisano za stvarne
 * akcije). Umesto toga, vraca listu {@link ProactiveSuggestion} objekata
 * koje FE povlaci na zahtev (GET /assistant/suggestions). Tako sprecimo
 * spam — FE odlucuje kad i kako prikazati.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProactiveSuggestionService {

    private final AssistantProperties properties;
    private final AccountRepository accountRepository;

    public record ProactiveSuggestion(
            String id,
            String type,            // "idle_funds" | "stale_order" | "fx_warning"
            String title,
            String message,
            String actionRoute,     // FE deep link (npr. "/funds")
            int priority            // 1-10, vise = vaznije
    ) {}

    /**
     * Vraca top-3 proaktivne sugestije za usera, sortirane po prioritetu desc.
     * Ako nema relevantnih, vraca prazan list.
     */
    @Transactional(readOnly = true)
    public List<ProactiveSuggestion> getSuggestions(Long userId, String userRole) {
        if (!properties.getAgentic().isEnabled()) return List.of();

        List<ProactiveSuggestion> result = new ArrayList<>();

        // Strategija 1: idle funds
        try {
            result.addAll(detectIdleFunds(userId, userRole));
        } catch (Exception e) {
            log.warn("Idle funds detection failed: {}", e.getMessage());
        }

        // Sortiraj po priority desc i ogranici na top 3
        return result.stream()
                .sorted((a, b) -> Integer.compare(b.priority(), a.priority()))
                .limit(3)
                .toList();
    }

    /**
     * Detektuje racune sa stanjem >= 50000 RSD (ili ekvivalent) koji nisu
     * dirani vec mesec dana. Vraca po jednu sugestiju po racunu (max 1).
     */
    private List<ProactiveSuggestion> detectIdleFunds(Long userId, String userRole) {
        if (!"CLIENT".equals(userRole)) return List.of();

        List<Account> accounts;
        try {
            accounts = accountRepository.findAll().stream()
                    .filter(a -> a.getClient() != null
                            && a.getClient().getId().equals(userId)
                            && a.getStatus() == AccountStatus.ACTIVE)
                    .toList();
        } catch (Exception e) {
            return List.of();
        }

        BigDecimal threshold = new BigDecimal("50000");
        LocalDateTime monthAgo = LocalDateTime.now().minusDays(30);

        for (Account a : accounts) {
            BigDecimal balance = a.getBalance();
            if (balance == null || balance.compareTo(threshold) < 0) continue;
            // Heuristika: createdAt > 30 dana znaci da je racun vec dovoljno
            // star da je idle preporuka relevantna. Ne traceujemo "poslednju
            // tx" zato sto bi bilo skupo (Payment + Transfer + Order JOIN).
            LocalDateTime created = a.getCreatedAt();
            if (created != null && created.isAfter(monthAgo)) continue;
            return List.of(new ProactiveSuggestion(
                    "idle-" + a.getId(),
                    "idle_funds",
                    "Razmisli o investiciji",
                    "Imas " + balance.toPlainString() + " " +
                            (a.getCurrency() == null ? "RSD" : a.getCurrency().getCode()) +
                            " na racunu vec duze vreme. Razmotri ulaganje u investicioni fond.",
                    "/funds",
                    7
            ));
        }
        return List.of();
    }
}
