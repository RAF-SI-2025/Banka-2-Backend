package rs.raf.banka2_bek.otp;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import rs.raf.banka2_bek.IntegrationTestCleanup;
import rs.raf.banka2_bek.auth.model.User;
import rs.raf.banka2_bek.auth.repository.UserRepository;
import rs.raf.banka2_bek.otp.model.TotpSecret;
import rs.raf.banka2_bek.otp.repository.TotpSecretRepository;
import rs.raf.banka2_bek.otp.service.OtpService;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class OtpServiceIntegrationTest {

    @Autowired private OtpService otpService;
    @Autowired private UserRepository userRepository;
    @Autowired private TotpSecretRepository totpSecretRepository;
    @Autowired private DataSource dataSource;

    private static final String EMAIL = "totp-it@test.com";

    @BeforeEach
    void cleanAndSeed() {
        IntegrationTestCleanup.truncateAllTables(dataSource);
        userRepository.save(new User("Test", "User", EMAIL, "x", true, "CLIENT"));
    }

    @Test
    @DisplayName("generateAndSend creates a persisted TOTP secret for the user")
    void generateAndSendPersistsSecret() {
        otpService.generateAndSend(EMAIL);

        Long userId = userRepository.findByEmail(EMAIL).orElseThrow().getId();
        Optional<TotpSecret> stored = totpSecretRepository.findByUserId(userId);

        assertThat(stored).isPresent();
        assertThat(stored.get().getSecret()).isNotBlank();
    }

    @Test
    @DisplayName("verify returns verified=true for the current TOTP code")
    void verifyAcceptsCurrentCode() {
        otpService.generateAndSend(EMAIL);

        Long userId = userRepository.findByEmail(EMAIL).orElseThrow().getId();
        String secret = totpSecretRepository.findByUserId(userId).orElseThrow().getSecret();
        String code = String.format("%06d", new GoogleAuthenticator().getTotpPassword(secret));

        Map<String, Object> result = otpService.verify(EMAIL, code);

        assertThat(result.get("verified")).isEqualTo(true);
    }

    @Test
    @DisplayName("verify returns verified=false for an obviously wrong code")
    void verifyRejectsWrongCode() {
        otpService.generateAndSend(EMAIL);

        Map<String, Object> result = otpService.verify(EMAIL, "000000");

        assertThat(result.get("verified")).isEqualTo(false);
        assertThat(result.get("blocked")).isEqualTo(false);
    }

    @Test
    @DisplayName("getActiveOtp returns active=true with a 6-digit code and seconds left")
    void getActiveOtpReturnsCurrentCode() {
        otpService.generateAndSend(EMAIL);

        Map<String, Object> result = otpService.getActiveOtp(EMAIL);

        assertThat(result.get("active")).isEqualTo(true);
        assertThat((String) result.get("code")).matches("\\d{6}");
        assertThat((long) result.get("expiresInSeconds")).isBetween(1L, 30L);
    }
}
