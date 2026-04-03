package rs.raf.banka2_bek.otp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.notification.service.MailNotificationService;
import rs.raf.banka2_bek.otp.model.OtpVerification;
import rs.raf.banka2_bek.otp.repository.OtpVerificationRepository;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock private OtpVerificationRepository otpRepository;
    @Mock private MailNotificationService mailNotificationService;

    private OtpService otpService;

    private static final int EXPIRY_MINUTES = 5;
    private static final int MAX_ATTEMPTS = 3;

    @BeforeEach
    void setUp() {
        otpService = new OtpService(otpRepository, mailNotificationService, EXPIRY_MINUTES, MAX_ATTEMPTS);
    }

    // ===== generateAndSend =====

    @Nested
    @DisplayName("generateAndSend")
    class GenerateAndSend {

        @Test
        @DisplayName("creates new OTP and saves it")
        void createsNewOtp() {
            when(otpRepository.findTopByEmailAndUsedFalseOrderByCreatedAtDesc("user@test.com"))
                    .thenReturn(Optional.empty());
            when(otpRepository.save(any(OtpVerification.class))).thenAnswer(inv -> inv.getArgument(0));

            otpService.generateAndSend("user@test.com");

            ArgumentCaptor<OtpVerification> captor = ArgumentCaptor.forClass(OtpVerification.class);
            verify(otpRepository).save(captor.capture());

            OtpVerification saved = captor.getValue();
            assertThat(saved.getEmail()).isEqualTo("user@test.com");
            assertThat(saved.getCode()).matches("\\d{6}");
            assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now());
            assertThat(saved.getExpiresAt()).isBefore(LocalDateTime.now().plusMinutes(EXPIRY_MINUTES + 1));
        }

        @Test
        @DisplayName("invalidates existing unused OTP before creating new one")
        void invalidatesExistingOtp() {
            OtpVerification existing = OtpVerification.builder()
                    .id(1L).email("user@test.com").code("123456")
                    .expiresAt(LocalDateTime.now().plusMinutes(3))
                    .used(false).attempts(0).build();

            when(otpRepository.findTopByEmailAndUsedFalseOrderByCreatedAtDesc("user@test.com"))
                    .thenReturn(Optional.of(existing));
            when(otpRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            otpService.generateAndSend("user@test.com");

            // Existing OTP should be marked as used
            assertThat(existing.getUsed()).isTrue();
            // save called twice: once for invalidation, once for new OTP
            verify(otpRepository, times(2)).save(any(OtpVerification.class));
        }

        @Test
        @DisplayName("does not send email (OTP shown on mobile app)")
        void doesNotSendEmail() {
            when(otpRepository.findTopByEmailAndUsedFalseOrderByCreatedAtDesc("user@test.com"))
                    .thenReturn(Optional.empty());
            when(otpRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            otpService.generateAndSend("user@test.com");

            verifyNoInteractions(mailNotificationService);
        }
    }

    // ===== generateAndSendViaEmail =====

    @Nested
    @DisplayName("generateAndSendViaEmail")
    class GenerateAndSendViaEmail {

        @Test
        @DisplayName("creates OTP and sends email")
        void createsAndSendsEmail() {
            when(otpRepository.findTopByEmailAndUsedFalseOrderByCreatedAtDesc("user@test.com"))
                    .thenReturn(Optional.empty());
            when(otpRepository.save(any(OtpVerification.class))).thenAnswer(inv -> inv.getArgument(0));

            otpService.generateAndSendViaEmail("user@test.com");

            ArgumentCaptor<OtpVerification> captor = ArgumentCaptor.forClass(OtpVerification.class);
            verify(otpRepository).save(captor.capture());

            OtpVerification saved = captor.getValue();
            assertThat(saved.getCode()).matches("\\d{6}");

            verify(mailNotificationService).sendOtpMail(
                    eq("user@test.com"), eq(saved.getCode()), eq(EXPIRY_MINUTES));
        }

        @Test
        @DisplayName("invalidates existing OTP before sending new one via email")
        void invalidatesExisting() {
            OtpVerification existing = OtpVerification.builder()
                    .id(1L).email("user@test.com").code("111111")
                    .expiresAt(LocalDateTime.now().plusMinutes(2))
                    .used(false).attempts(0).build();

            when(otpRepository.findTopByEmailAndUsedFalseOrderByCreatedAtDesc("user@test.com"))
                    .thenReturn(Optional.of(existing));
            when(otpRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            otpService.generateAndSendViaEmail("user@test.com");

            assertThat(existing.getUsed()).isTrue();
            verify(mailNotificationService).sendOtpMail(eq("user@test.com"), anyString(), eq(EXPIRY_MINUTES));
        }
    }

    // ===== getActiveOtp =====

    @Nested
    @DisplayName("getActiveOtp")
    class GetActiveOtp {

        @Test
        @DisplayName("returns active OTP details when valid OTP exists")
        void returnsActiveOtp() {
            OtpVerification otp = OtpVerification.builder()
                    .id(1L).email("user@test.com").code("654321")
                    .expiresAt(LocalDateTime.now().plusMinutes(3))
                    .used(false).attempts(1).build();

            when(otpRepository.findTopByEmailAndUsedFalseOrderByCreatedAtDesc("user@test.com"))
                    .thenReturn(Optional.of(otp));

            Map<String, Object> result = otpService.getActiveOtp("user@test.com");

            assertThat(result.get("active")).isEqualTo(true);
            assertThat(result.get("code")).isEqualTo("654321");
            assertThat((long) result.get("expiresInSeconds")).isPositive();
            assertThat(result.get("attempts")).isEqualTo(1);
            assertThat(result.get("maxAttempts")).isEqualTo(MAX_ATTEMPTS);
        }

        @Test
        @DisplayName("returns inactive when no OTP exists")
        void returnsInactiveWhenNoOtp() {
            when(otpRepository.findTopByEmailAndUsedFalseOrderByCreatedAtDesc("user@test.com"))
                    .thenReturn(Optional.empty());

            Map<String, Object> result = otpService.getActiveOtp("user@test.com");

            assertThat(result.get("active")).isEqualTo(false);
            assertThat((String) result.get("message")).contains("Nema aktivnog");
        }

        @Test
        @DisplayName("returns inactive when OTP is expired")
        void returnsInactiveWhenExpired() {
            OtpVerification expired = OtpVerification.builder()
                    .id(1L).email("user@test.com").code("111111")
                    .expiresAt(LocalDateTime.now().minusMinutes(1)) // expired
                    .used(false).attempts(0).build();

            when(otpRepository.findTopByEmailAndUsedFalseOrderByCreatedAtDesc("user@test.com"))
                    .thenReturn(Optional.of(expired));

            Map<String, Object> result = otpService.getActiveOtp("user@test.com");

            assertThat(result.get("active")).isEqualTo(false);
        }
    }

    // ===== verify =====

    @Nested
    @DisplayName("verify")
    class Verify {

        @Test
        @DisplayName("returns verified=true for correct code")
        void correctCode() {
            OtpVerification otp = OtpVerification.builder()
                    .id(1L).email("user@test.com").code("123456")
                    .expiresAt(LocalDateTime.now().plusMinutes(3))
                    .used(false).attempts(0).build();

            when(otpRepository.findTopByEmailAndUsedFalseOrderByCreatedAtDesc("user@test.com"))
                    .thenReturn(Optional.of(otp));
            when(otpRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Map<String, Object> result = otpService.verify("user@test.com", "123456");

            assertThat(result.get("verified")).isEqualTo(true);
            assertThat((String) result.get("message")).contains("uspesno");
            assertThat(otp.getUsed()).isTrue();
        }

        @Test
        @DisplayName("returns verified=false for wrong code and increments attempts")
        void wrongCode() {
            OtpVerification otp = OtpVerification.builder()
                    .id(1L).email("user@test.com").code("123456")
                    .expiresAt(LocalDateTime.now().plusMinutes(3))
                    .used(false).attempts(0).build();

            when(otpRepository.findTopByEmailAndUsedFalseOrderByCreatedAtDesc("user@test.com"))
                    .thenReturn(Optional.of(otp));
            when(otpRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Map<String, Object> result = otpService.verify("user@test.com", "999999");

            assertThat(result.get("verified")).isEqualTo(false);
            assertThat(result.get("blocked")).isEqualTo(false);
            assertThat((String) result.get("message")).contains("Pogresan");
            assertThat(otp.getAttempts()).isEqualTo(1);
        }

        @Test
        @DisplayName("returns verified=false when OTP not found")
        void otpNotFound() {
            when(otpRepository.findTopByEmailAndUsedFalseOrderByCreatedAtDesc("user@test.com"))
                    .thenReturn(Optional.empty());

            Map<String, Object> result = otpService.verify("user@test.com", "123456");

            assertThat(result.get("verified")).isEqualTo(false);
            assertThat(result.get("blocked")).isEqualTo(false);
            assertThat((String) result.get("message")).contains("nije pronadjen");
        }

        @Test
        @DisplayName("returns verified=false and marks used when OTP expired")
        void expiredOtp() {
            OtpVerification otp = OtpVerification.builder()
                    .id(1L).email("user@test.com").code("123456")
                    .expiresAt(LocalDateTime.now().minusMinutes(1)) // expired
                    .used(false).attempts(0).build();

            when(otpRepository.findTopByEmailAndUsedFalseOrderByCreatedAtDesc("user@test.com"))
                    .thenReturn(Optional.of(otp));
            when(otpRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Map<String, Object> result = otpService.verify("user@test.com", "123456");

            assertThat(result.get("verified")).isEqualTo(false);
            assertThat((String) result.get("message")).contains("istekao");
            assertThat(otp.getUsed()).isTrue();
        }

        @Test
        @DisplayName("blocks when max attempts already reached")
        void maxAttemptsReached() {
            OtpVerification otp = OtpVerification.builder()
                    .id(1L).email("user@test.com").code("123456")
                    .expiresAt(LocalDateTime.now().plusMinutes(3))
                    .used(false).attempts(MAX_ATTEMPTS).build(); // already at max

            when(otpRepository.findTopByEmailAndUsedFalseOrderByCreatedAtDesc("user@test.com"))
                    .thenReturn(Optional.of(otp));
            when(otpRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Map<String, Object> result = otpService.verify("user@test.com", "999999");

            assertThat(result.get("verified")).isEqualTo(false);
            assertThat(result.get("blocked")).isEqualTo(true);
            assertThat((String) result.get("message")).contains("otkazana");
            assertThat(otp.getUsed()).isTrue();
        }

        @Test
        @DisplayName("blocks on the attempt that reaches max attempts")
        void blocksOnLastAttempt() {
            OtpVerification otp = OtpVerification.builder()
                    .id(1L).email("user@test.com").code("123456")
                    .expiresAt(LocalDateTime.now().plusMinutes(3))
                    .used(false).attempts(MAX_ATTEMPTS - 1).build(); // one attempt left

            when(otpRepository.findTopByEmailAndUsedFalseOrderByCreatedAtDesc("user@test.com"))
                    .thenReturn(Optional.of(otp));
            when(otpRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Map<String, Object> result = otpService.verify("user@test.com", "999999");

            assertThat(result.get("verified")).isEqualTo(false);
            assertThat(result.get("blocked")).isEqualTo(true);
            assertThat(otp.getUsed()).isTrue();
            assertThat(otp.getAttempts()).isEqualTo(MAX_ATTEMPTS);
        }

        @Test
        @DisplayName("decrements remaining attempts message on wrong code")
        void remainingAttemptsMessage() {
            OtpVerification otp = OtpVerification.builder()
                    .id(1L).email("user@test.com").code("123456")
                    .expiresAt(LocalDateTime.now().plusMinutes(3))
                    .used(false).attempts(0).build();

            when(otpRepository.findTopByEmailAndUsedFalseOrderByCreatedAtDesc("user@test.com"))
                    .thenReturn(Optional.of(otp));
            when(otpRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Map<String, Object> result = otpService.verify("user@test.com", "000000");

            assertThat((String) result.get("message")).contains("Preostalo pokusaja: 2");
        }

        @Test
        @DisplayName("correct code succeeds even after failed attempts")
        void correctCodeAfterFailedAttempts() {
            OtpVerification otp = OtpVerification.builder()
                    .id(1L).email("user@test.com").code("123456")
                    .expiresAt(LocalDateTime.now().plusMinutes(3))
                    .used(false).attempts(2).build(); // 2 failed, 1 left

            when(otpRepository.findTopByEmailAndUsedFalseOrderByCreatedAtDesc("user@test.com"))
                    .thenReturn(Optional.of(otp));
            when(otpRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Map<String, Object> result = otpService.verify("user@test.com", "123456");

            assertThat(result.get("verified")).isEqualTo(true);
            assertThat(otp.getUsed()).isTrue();
        }
    }
}
