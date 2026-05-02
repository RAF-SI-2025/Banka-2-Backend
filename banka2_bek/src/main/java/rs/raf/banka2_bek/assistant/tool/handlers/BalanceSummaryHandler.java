package rs.raf.banka2_bek.assistant.tool.handlers;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.assistant.config.AssistantProperties;
import rs.raf.banka2_bek.assistant.tool.ToolDefinition;
import rs.raf.banka2_bek.assistant.tool.ToolHandler;
import rs.raf.banka2_bek.auth.util.UserContext;
import rs.raf.banka2_bek.auth.util.UserRole;
import rs.raf.banka2_bek.exchange.ExchangeService;
import rs.raf.banka2_bek.exchange.dto.ExchangeRateDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * get_user_balance_summary() -> {accountCount, currencies[], totalRsdEquivalent, accounts[]}
 *
 * Vraca SUMARIZOVAN pregled korisnikovih racuna — broj racuna, valute u kojima
 * ima sredstva, ukupan ekvivalent u RSD. NE vraca pune brojeve racuna (privatnost).
 *
 * Klijenti dobijaju svoje racune. Zaposleni dobijaju prazan rezultat sa
 * objasnjenjem da nemaju licne racune u sistemu (trguju sa bankinih).
 */
@Component
@RequiredArgsConstructor
public class BalanceSummaryHandler implements ToolHandler {

    private final AccountRepository accountRepository;
    private final ExchangeService exchangeService;
    private final AssistantProperties properties;

    @Override
    public boolean isEnabled() {
        return properties.getTools().getInternal().isBalanceSummary();
    }

    @Override
    public String name() {
        return "get_user_balance_summary";
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(name())
                .description("Vraca sazet pregled korisnikovih racuna: broj aktivnih " +
                        "racuna, valute u kojima ima sredstva, ukupan ekvivalent u RSD " +
                        "i listu po-racunu (maskirani brojevi). Pozovi kad korisnik " +
                        "pita 'koliko ukupno imam', 'koje racune imam', 'kakvo mi je stanje'.")
                .build();
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> args, UserContext user) {
        if (UserRole.isEmployee(user.userRole())) {
            return Map.of(
                    "accountCount", 0,
                    "note", "Zaposleni nemaju licne bankovne racune u sistemu. Trguju sa bankinih racuna."
            );
        }
        List<Account> accounts = accountRepository.findByClientId(user.userId());
        if (accounts.isEmpty()) {
            return Map.of("accountCount", 0, "note", "Nemate aktivne bankovne racune.");
        }
        Set<String> currencies = new HashSet<>();
        BigDecimal totalRsd = BigDecimal.ZERO;
        List<Map<String, Object>> rows = new ArrayList<>();
        List<ExchangeRateDto> rates = exchangeService.getAllRates();

        for (Account a : accounts) {
            if (a.getCurrency() == null) continue;
            String code = a.getCurrency().getCode();
            currencies.add(code);
            BigDecimal balance = a.getBalance() != null ? a.getBalance() : BigDecimal.ZERO;
            BigDecimal rsdEquivalent = toRsd(balance, code, rates);
            totalRsd = totalRsd.add(rsdEquivalent);
            rows.add(Map.of(
                    "accountNumberMasked", maskAccount(a.getAccountNumber()),
                    "currency", code,
                    "balance", balance.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                    "availableBalance", a.getAvailableBalance() != null
                            ? a.getAvailableBalance().setScale(2, RoundingMode.HALF_UP).toPlainString()
                            : "0.00"
            ));
        }
        return Map.of(
                "accountCount", accounts.size(),
                "currencies", currencies,
                "totalRsdEquivalent", totalRsd.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                "accounts", rows
        );
    }

    private static BigDecimal toRsd(BigDecimal amount, String currency, List<ExchangeRateDto> rates) {
        if ("RSD".equalsIgnoreCase(currency)) return amount;
        for (ExchangeRateDto r : rates) {
            if (currency.equalsIgnoreCase(r.getCurrency())) {
                double rate = r.getRate();
                if (rate <= 0) return BigDecimal.ZERO;
                return amount.divide(BigDecimal.valueOf(rate), 4, RoundingMode.HALF_UP);
            }
        }
        return BigDecimal.ZERO;
    }

    private static String maskAccount(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 7) return "***";
        return accountNumber.substring(0, 3) + "..." + accountNumber.substring(accountNumber.length() - 4);
    }
}
