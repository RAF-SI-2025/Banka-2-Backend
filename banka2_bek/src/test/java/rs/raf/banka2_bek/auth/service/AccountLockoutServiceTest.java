package rs.raf.banka2_bek.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AccountLockoutService — failed attempts + lockout")
class AccountLockoutServiceTest {

    private AccountLockoutService service;

    @BeforeEach
    void setUp() {
        service = new AccountLockoutService();
        ReflectionTestUtils.setField(service, "maxFailedAttempts", 5);
        ReflectionTestUtils.setField(service, "lockDurationMinutes", 15);
        ReflectionTestUtils.setField(service, "attemptWindowMinutes", 30);
        service.init();
    }

    @Test
    @DisplayName("Novi email: failure count = 0, isLocked = false")
    void freshEmail_zeroFailures() {
        assertThat(service.getFailureCount("alice@example.com")).isEqualTo(0);
        assertThat(service.isLocked("alice@example.com")).isFalse();
    }

    @Test
    @DisplayName("recordFailure inkrementira brojac")
    void recordFailure_incrementsCount() {
        service.recordFailure("alice@example.com");
        service.recordFailure("alice@example.com");

        assertThat(service.getFailureCount("alice@example.com")).isEqualTo(2);
    }

    @Test
    @DisplayName("Posle 5 neuspeha — racun je lock-ovan i 5. recordFailure baca AccountLockedException")
    void fiveFailures_locksAccount() {
        // Spec Sc 5 (Bug T1-015 12.05.2026): 5. neuspesni pokusaj sam baca
        // AccountLockedException — korisnik vidi lockout poruku na 5. pokusaju,
        // ne tek na 6. Prvih 4 pokusaja su tihi.
        String email = "alice@example.com";
        for (int i = 0; i < 4; i++) {
            service.recordFailure(email);
        }
        assertThat(service.isLocked(email)).isFalse();

        assertThatThrownBy(() -> service.recordFailure(email))
                .isInstanceOf(AccountLockoutService.AccountLockedException.class)
                .hasMessageContaining("Nalog je privremeno zakljucan");

        assertThat(service.isLocked(email)).isTrue();
    }

    @Test
    @DisplayName("Lock se ne aktivira pre 5 neuspeha")
    void fourFailures_doesNotLock() {
        String email = "alice@example.com";
        for (int i = 0; i < 4; i++) {
            service.recordFailure(email);
        }

        assertThat(service.isLocked(email)).isFalse();
    }

    @Test
    @DisplayName("recordSuccess resetuje brojac neuspeha")
    void recordSuccess_resetsCount() {
        String email = "alice@example.com";
        service.recordFailure(email);
        service.recordFailure(email);
        assertThat(service.getFailureCount(email)).isEqualTo(2);

        service.recordSuccess(email);

        assertThat(service.getFailureCount(email)).isEqualTo(0);
    }

    @Test
    @DisplayName("assertNotLocked baca AccountLockedException kad je lock aktivan")
    void assertNotLocked_throwsWhenLocked() {
        String email = "alice@example.com";
        // Prvih 4 ne bacaju; 5. baca i postavlja lock. Hvatamo da nastavimo.
        for (int i = 0; i < 4; i++) {
            service.recordFailure(email);
        }
        try {
            service.recordFailure(email);
        } catch (AccountLockoutService.AccountLockedException expected) {
            // ocekivano
        }

        assertThatThrownBy(() -> service.assertNotLocked(email))
                .isInstanceOf(AccountLockoutService.AccountLockedException.class)
                .hasMessageContaining("Nalog je privremeno zakljucan");
    }

    @Test
    @DisplayName("assertNotLocked ne baca kad nije lock-ovan")
    void assertNotLocked_noOpWhenNotLocked() {
        service.assertNotLocked("alice@example.com");
        // Bez exception-a → pass
    }

    @Test
    @DisplayName("Email je case-insensitive")
    void emailNormalization() {
        service.recordFailure("Alice@Example.com");
        service.recordFailure("ALICE@example.COM");

        assertThat(service.getFailureCount("alice@example.com")).isEqualTo(2);
    }

    @Test
    @DisplayName("Razliciti email-ovi se nezavisno broje")
    void differentEmails_independent() {
        // 5. neuspeh za alice baca exception — hvatamo da bismo nastavili sa bob.
        for (int i = 0; i < 4; i++) service.recordFailure("alice@example.com");
        try {
            service.recordFailure("alice@example.com");
        } catch (AccountLockoutService.AccountLockedException expected) {
            // ok
        }
        service.recordFailure("bob@example.com");

        assertThat(service.isLocked("alice@example.com")).isTrue();
        assertThat(service.isLocked("bob@example.com")).isFalse();
        assertThat(service.getFailureCount("bob@example.com")).isEqualTo(1);
    }

    @Test
    @DisplayName("Null email je no-op")
    void nullEmail_noOp() {
        service.recordFailure(null);
        service.recordSuccess(null);
        service.assertNotLocked(null);

        assertThat(service.getFailureCount(null)).isEqualTo(0);
        assertThat(service.isLocked(null)).isFalse();
    }
}
