package rs.raf.banka2_bek.assistant.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import rs.raf.banka2_bek.assistant.config.AssistantProperties;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    private AssistantProperties properties;
    private RateLimiter limiter;

    @BeforeEach
    void setUp() {
        properties = new AssistantProperties();
        properties.setRateLimitPerUserPerMin(3); // niski limit za test
        limiter = new RateLimiter(properties);
    }

    @Test
    void allowsUpToConfiguredLimitPerUser() {
        assertThat(limiter.tryAcquire(1L, "CLIENT")).isTrue();
        assertThat(limiter.tryAcquire(1L, "CLIENT")).isTrue();
        assertThat(limiter.tryAcquire(1L, "CLIENT")).isTrue();
        assertThat(limiter.tryAcquire(1L, "CLIENT")).isFalse();
    }

    @Test
    void differentUsersHaveSeparateBuckets() {
        assertThat(limiter.tryAcquire(1L, "CLIENT")).isTrue();
        assertThat(limiter.tryAcquire(1L, "CLIENT")).isTrue();
        assertThat(limiter.tryAcquire(1L, "CLIENT")).isTrue();
        // 1L:CLIENT je iscrpljen, ali 2L:CLIENT i 1L:EMPLOYEE jos imaju svoj bucket
        assertThat(limiter.tryAcquire(1L, "CLIENT")).isFalse();
        assertThat(limiter.tryAcquire(2L, "CLIENT")).isTrue();
        assertThat(limiter.tryAcquire(1L, "EMPLOYEE")).isTrue();
    }
}
