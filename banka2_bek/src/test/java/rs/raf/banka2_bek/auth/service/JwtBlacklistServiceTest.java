package rs.raf.banka2_bek.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtBlacklistService — token blacklist")
class JwtBlacklistServiceTest {

    private JwtBlacklistService service;

    @BeforeEach
    void setUp() {
        service = new JwtBlacklistService();
        service.init();
    }

    @Test
    @DisplayName("Token koji nije blacklisted vraca false")
    void unknownToken_isNotBlacklisted() {
        assertThat(service.isBlacklisted("eyJhbG.unused.token")).isFalse();
    }

    @Test
    @DisplayName("Posle blacklist() token postaje blacklisted")
    void blacklist_marksTokenAsBlacklisted() {
        String token = "eyJhbG.real.token123";

        service.blacklist(token);

        assertThat(service.isBlacklisted(token)).isTrue();
    }

    @Test
    @DisplayName("Razliciti tokeni se nezavisno tretiraju")
    void differentTokens_independentBlacklisting() {
        String tokenA = "tokenA";
        String tokenB = "tokenB";

        service.blacklist(tokenA);

        assertThat(service.isBlacklisted(tokenA)).isTrue();
        assertThat(service.isBlacklisted(tokenB)).isFalse();
    }

    @Test
    @DisplayName("Null token ne baca i ne blacklisting-uje nista")
    void nullToken_doesNothing() {
        service.blacklist(null);

        assertThat(service.isBlacklisted(null)).isFalse();
        assertThat(service.isBlacklisted("")).isFalse();
    }

    @Test
    @DisplayName("Empty token ne baca i ne blacklisting-uje nista")
    void emptyToken_doesNothing() {
        service.blacklist("");
        service.blacklist("   ");

        assertThat(service.isBlacklisted("")).isFalse();
        assertThat(service.isBlacklisted("   ")).isFalse();
    }

    @Test
    @DisplayName("Idempotentno: ponovni blacklist istog tokena radi")
    void blacklist_idempotent() {
        String token = "same.token.twice";
        service.blacklist(token);
        service.blacklist(token);

        assertThat(service.isBlacklisted(token)).isTrue();
    }
}
