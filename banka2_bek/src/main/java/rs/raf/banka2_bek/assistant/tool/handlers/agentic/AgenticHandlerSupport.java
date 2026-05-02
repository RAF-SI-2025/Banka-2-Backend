package rs.raf.banka2_bek.assistant.tool.handlers.agentic;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.repository.ListingRepository;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Pomocni komponent za WriteToolHandler-e — pretvaranje LLM args u BE entitete.
 * Centralizuje resolver-e koji se cesto koriste (broj racuna → Account, listingId
 * → Listing, ime primaoca, itd).
 */
@Component
@RequiredArgsConstructor
public class AgenticHandlerSupport {

    final AccountRepository accountRepository;
    final ClientRepository clientRepository;
    final EmployeeRepository employeeRepository;
    final ListingRepository listingRepository;

    public BigDecimal getBigDecimal(java.util.Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v == null) return null;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        if (v instanceof String s && !s.isBlank()) {
            try { return new BigDecimal(s.trim()); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    public Long getLong(java.util.Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s && !s.isBlank()) {
            try { return Long.parseLong(s.trim()); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    public Integer getInt(java.util.Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s && !s.isBlank()) {
            try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    public Boolean getBool(java.util.Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v == null) return null;
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return Boolean.parseBoolean(s);
        return null;
    }

    public String getString(java.util.Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v == null) return null;
        return v.toString().trim();
    }

    public String maskAccount(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 6) return accountNumber;
        return accountNumber.substring(0, 3) + "****" + accountNumber.substring(accountNumber.length() - 4);
    }

    public Optional<Account> findAccountByNumber(String number) {
        if (number == null) return Optional.empty();
        return accountRepository.findByAccountNumber(number);
    }

    public Optional<Listing> findListing(Long id) {
        if (id == null) return Optional.empty();
        return listingRepository.findById(id);
    }

    public Optional<Listing> findListingByTicker(String ticker) {
        if (ticker == null) return Optional.empty();
        return listingRepository.findByTicker(ticker.trim().toUpperCase());
    }

    public String resolveOwnerName(String accountNumber) {
        Account a = accountRepository.findByAccountNumber(accountNumber).orElse(null);
        if (a == null) return null;
        Client c = a.getClient();
        if (c != null) return c.getFirstName() + " " + c.getLastName();
        if (a.getName() != null && !a.getName().isBlank()) return a.getName();
        return null;
    }
}
