package rs.raf.banka2_bek.otp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.auth.model.User;
import rs.raf.banka2_bek.auth.repository.UserRepository;
import rs.raf.banka2_bek.notification.service.MailSenderService;
import rs.raf.banka2_bek.otp.model.TotpSecret;
import rs.raf.banka2_bek.otp.repository.TotpSecretRepository;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private TotpService totpService;
    @Mock private TotpSecretRepository totpSecretRepository;
    @Mock private MailSenderService mailSenderService;

    private OtpService otpService;

    private static final int EMAIL_EXPIRY_MINUTES = 5;
    private static final String EMAIL = "user@test.com";
    private static final Long USER_ID = 7L;

    @BeforeEach
    void setUp() {
        otpService = new OtpService(
                userRepository, totpService, totpSecretRepository, mailSenderService, EMAIL_EXPIRY_MINUTES);
    }

    private User user() {
        User u = new User();
        u.setId(USER_ID);
        u.setEmail(EMAIL);
        return u;
    }

    @Nested
    @DisplayName("generateAndSend")
    class GenerateAndSend {

        @Test
        @DisplayName("generates new TOTP secret when user has none, sends no email")
        void generatesSecretWhenMissing() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user()));
            when(totpSecretRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
            when(totpService.generateSecret(USER_ID)).thenReturn("NEWSECRET");

            otpService.generateAndSend(EMAIL);

            verify(totpService).generateSecret(USER_ID);
            verifyNoInteractions(mailSenderService);
        }

        @Test
        @DisplayName("keeps existing secret when user already has one")
        void keepsExistingSecret() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user()));
            when(totpSecretRepository.findByUserId(USER_ID))
                    .thenReturn(Optional.of(TotpSecret.builder().userId(USER_ID).secret("EXIST").build()));

            otpService.generateAndSend(EMAIL);

            verify(totpService, never()).generateSecret(USER_ID);
            verifyNoInteractions(mailSenderService);
        }

        @Test
        @DisplayName("throws when user not found")
        void throwsWhenUserMissing() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> otpService.generateAndSend(EMAIL))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Korisnik nije pronadjen");
        }
    }

    @Nested
    @DisplayName("generateAndSendViaEmail")
    class GenerateAndSendViaEmail {

        @Test
        @DisplayName("computes current TOTP code and emails it")
        void emailsCurrentCode() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user()));
            String secret = "JBSWY3DPEHPK3PXP";
            when(totpSecretRepository.findByUserId(USER_ID))
                    .thenReturn(Optional.of(TotpSecret.builder().userId(USER_ID).secret(secret).build()));

            otpService.generateAndSendViaEmail(EMAIL);

            verify(mailSenderService).sendOtpMail(eq(EMAIL), anyString(), eq(EMAIL_EXPIRY_MINUTES));
        }
    }

    @Nested
    @DisplayName("getActiveOtp")
    class GetActiveOtp {

        @Test
        @DisplayName("returns active=true with 6-digit code and seconds left in 30s window")
        void returnsActiveCode() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user()));
            when(totpSecretRepository.findByUserId(USER_ID))
                    .thenReturn(Optional.of(TotpSecret.builder().userId(USER_ID).secret("JBSWY3DPEHPK3PXP").build()));

            Map<String, Object> result = otpService.getActiveOtp(EMAIL);

            assertThat(result.get("active")).isEqualTo(true);
            assertThat((String) result.get("code")).matches("\\d{6}");
            assertThat((long) result.get("expiresInSeconds")).isBetween(1L, 30L);
        }
    }

    @Nested
    @DisplayName("verify")
    class Verify {

        @Test
        @DisplayName("delegates to TotpService and returns verified=true on success")
        void verifiedTrue() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user()));
            when(totpService.verify(USER_ID, "123456")).thenReturn(true);

            Map<String, Object> result = otpService.verify(EMAIL, "123456");

            assertThat(result.get("verified")).isEqualTo(true);
            assertThat((String) result.get("message")).contains("uspesno");
        }

        @Test
        @DisplayName("returns verified=false when TOTP code mismatches")
        void verifiedFalse() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user()));
            when(totpService.verify(USER_ID, "999999")).thenReturn(false);

            Map<String, Object> result = otpService.verify(EMAIL, "999999");

            assertThat(result.get("verified")).isEqualTo(false);
            assertThat(result.get("blocked")).isEqualTo(false);
            assertThat((String) result.get("message")).contains("Pogresan");
        }

        @Test
        @DisplayName("returns 'nije pronadjen' when user not found")
        void userNotFound() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            Map<String, Object> result = otpService.verify(EMAIL, "123456");

            assertThat(result.get("verified")).isEqualTo(false);
            assertThat((String) result.get("message")).contains("nije pronadjen");
            verifyNoInteractions(totpService);
        }

        @Test
        @DisplayName("returns 'nije pronadjen' when no TOTP secret configured")
        void noSecret() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user()));
            when(totpService.verify(USER_ID, "123456"))
                    .thenThrow(new IllegalStateException("TOTP nije podesen za korisnika " + USER_ID));

            Map<String, Object> result = otpService.verify(EMAIL, "123456");

            assertThat(result.get("verified")).isEqualTo(false);
            assertThat((String) result.get("message")).contains("nije pronadjen");
        }
    }
}
