package rs.raf.banka2_bek.auth.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Opciono.2 — Account lockout posle X neuspesnih login pokusaja.
 *
 * Spec Celina 1, Scenario 5: "Posle 4 neuspeha, peti unos pogresne lozinke
 * → sistem zaključava nalog na određeni vremenski period". Spec eksplicitno
 * navodi ovo kao "Za nadogradnju" — opciono.
 *
 * Razlikuje se od {@link rs.raf.banka2_bek.auth.config.AuthRateLimitFilter}:
 *   * RateLimitFilter limitira po IP-u (10 req/min) — sprecava brute force.
 *   * AccountLockout limitira po EMAIL-u (5 failed → 15min lock) —
 *     sprecava credential stuffing kad napadac probava razne IP-e.
 *
 * In-memory implementacija (Caffeine), ne preziva restart. Za production
 * deploy zahteva Redis ili sl. shared store da bi radio kroz multiple
 * instance. Za KT3 demo dovoljno.
 */
@Slf4j
@Service
public class AccountLockoutService {

    /** Posle ovoliko neuspeha — racun je lock-ovan. */
    @Value("${auth.lockout.max-failed-attempts:5}")
    private int maxFailedAttempts;

    /** Trajanje lock-a u minutama. */
    @Value("${auth.lockout.lock-duration-minutes:15}")
    private int lockDurationMinutes;

    /** Window u kome se broje neuspesi (resetuje se posle uspesnog login-a). */
    @Value("${auth.lockout.attempt-window-minutes:30}")
    private int attemptWindowMinutes;

    private Cache<String, AtomicInteger> failedAttempts;
    private Cache<String, Instant> lockedUntil;

    @PostConstruct
    public void init() {
        this.failedAttempts = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(attemptWindowMinutes))
                .maximumSize(50_000)
                .build();
        this.lockedUntil = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(lockDurationMinutes))
                .maximumSize(50_000)
                .build();
    }

    /**
     * Baca {@link AccountLockedException} ako je email trenutno lock-ovan.
     * Treba pozvati pre nego sto se proveri lozinka.
     */
    public void assertNotLocked(String email) {
        if (email == null) return;
        String key = normalize(email);
        Instant locked = lockedUntil.getIfPresent(key);
        if (locked != null && locked.isAfter(Instant.now())) {
            long secondsRemaining = locked.getEpochSecond() - Instant.now().getEpochSecond();
            throw new AccountLockedException(
                    "Account temporarily locked due to too many failed attempts; try again in "
                    + (secondsRemaining / 60 + 1) + " min", secondsRemaining);
        }
    }

    /**
     * Belezi neuspesan pokusaj i lockuje racun ako je dosegnut prag.
     */
    public void recordFailure(String email) {
        if (email == null) return;
        String key = normalize(email);
        AtomicInteger count = failedAttempts.get(key, k -> new AtomicInteger(0));
        int attempts = count.incrementAndGet();
        if (attempts >= maxFailedAttempts) {
            lockedUntil.put(key, Instant.now().plus(Duration.ofMinutes(lockDurationMinutes)));
            failedAttempts.invalidate(key);
            log.warn("Account locked: {} ({} failed attempts)", key, attempts);
        }
    }

    /**
     * Resetuje brojac (po uspesnom login-u).
     */
    public void recordSuccess(String email) {
        if (email == null) return;
        String key = normalize(email);
        failedAttempts.invalidate(key);
        // Lock se zadrzava ako je vec aktivan — ne unlock-ujemo eksplicitno;
        // korisnik ce moci tek posle isteka.
    }

    /** Vraca trenutni broj neuspesa za email; 0 ako nema upisa. */
    public int getFailureCount(String email) {
        if (email == null) return 0;
        AtomicInteger count = failedAttempts.getIfPresent(normalize(email));
        return count != null ? count.get() : 0;
    }

    /** Vraca true ako je email trenutno lock-ovan. */
    public boolean isLocked(String email) {
        if (email == null) return false;
        Instant locked = lockedUntil.getIfPresent(normalize(email));
        return locked != null && locked.isAfter(Instant.now());
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Throw-ovan kad korisnik pokusa login na lock-ovan racun.
     */
    public static class AccountLockedException extends RuntimeException {
        private final long secondsRemaining;

        public AccountLockedException(String message, long secondsRemaining) {
            super(message);
            this.secondsRemaining = secondsRemaining;
        }

        public long getSecondsRemaining() {
            return secondsRemaining;
        }
    }
}
