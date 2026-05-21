package rs.raf.banka2_bek.otp.service;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.auth.model.User;
import rs.raf.banka2_bek.auth.repository.UserRepository;
import rs.raf.banka2_bek.notification.service.MailSenderService;
import rs.raf.banka2_bek.otp.repository.TotpSecretRepository;

import java.time.Instant;
import java.util.Map;

/*
 * TODO [B3 - TOTP verifikacioni kod | Nosilac: Nikola Stamenkovic]
 *
 * Trenutna implementacija koristi jednokratni 6-cifreni OTP kod sa rokom vaznosti
 * od 5 minuta koji se cuva u tabeli otp_verifications i salje korisniku emailom
 * ili prikazuje u mobilnoj aplikaciji.
 *
 * Zadatak: zameniti ovu logiku delegacijom na novi TotpService koji implementira
 * vremenski zasnovane jednokratne kodove po standardu RFC 6238 (TOTP):
 *   - Vremenski prozor: 30 sekundi po kodu (TOTP standard).
 *   - Algoritam: HMAC-SHA1 ili HMAC-SHA256, tajni kljuc per-korisnik.
 *   - Tolerancija: dozvoliti +/-1 vremenski prozor radi sinhronizacije casovnika.
 *
 * KRITICNO - potpis metode verify(...) mora ostati nepromenjen:
 *   public Map<String, Object> verify(String email, String code)
 * Svi postojeci pozivaoci (OtcService, PaymentServiceImpl, SavingsDepositService,
 * itd.) ne smeju biti modifikovani - samo interna implementacija verify() se
 * menja tako da delegira proveru na TotpService umesto na OtpVerificationRepository.
 *
 * Takodje zadrzati isti potpis generateAndSend(String email) i
 * generateAndSendViaEmail(String email) - ovi pozivaoci takodje ostaju nepromenjeni.
 * Interna implementacija moze preskociti generisanje i cuvanje koda u bazu
 * (TOTP kod se generise on-the-fly iz tajnog kljuca i trenutnog vremena).
 */
@Service
public class OtpService {

    private static final long TOTP_WINDOW_SECONDS = 30L;

    private final UserRepository userRepository;
    private final TotpService totpService;
    private final TotpSecretRepository totpSecretRepository;
    private final MailSenderService mailSenderService;
    private final int emailExpiryMinutes;

    public OtpService(UserRepository userRepository,
                      TotpService totpService,
                      TotpSecretRepository totpSecretRepository,
                      MailSenderService mailSenderService,
                      @Value("${otp.expiry-minutes:5}") int emailExpiryMinutes) {
        this.userRepository = userRepository;
        this.totpService = totpService;
        this.totpSecretRepository = totpSecretRepository;
        this.mailSenderService = mailSenderService;
        this.emailExpiryMinutes = emailExpiryMinutes;
    }

    @Transactional
    public void generateAndSend(String email) {
        ensureSecret(email);
    }

    @Transactional
    public void generateAndSendViaEmail(String email) {
        String secret = ensureSecret(email);
        String code = currentCode(secret);
        mailSenderService.sendOtpMail(email, code, emailExpiryMinutes);
    }

    @Transactional
    public Map<String, Object> getActiveOtp(String email) {
        String secret = ensureSecret(email);
        String code = currentCode(secret);
        long secondsLeft = TOTP_WINDOW_SECONDS - (Instant.now().getEpochSecond() % TOTP_WINDOW_SECONDS);

        return Map.of(
                "active", true,
                "code", code,
                "expiresInSeconds", secondsLeft,
                "attempts", 0,
                "maxAttempts", 0);
    }

    @Transactional
    public Map<String, Object> verify(String email, String code) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return Map.of(
                    "verified", false,
                    "blocked", false,
                    "message", "Verifikacioni kod nije pronadjen. Zatrazite novi kod.");
        }

        boolean ok;
        try {
            ok = totpService.verify(user.getId(), code);
        } catch (IllegalStateException ex) {
            return Map.of(
                    "verified", false,
                    "blocked", false,
                    "message", "Verifikacioni kod nije pronadjen. Zatrazite novi kod.");
        }

        if (ok) {
            return Map.of(
                    "verified", true,
                    "message", "Transakcija uspesno verifikovana");
        }

        return Map.of(
                "verified", false,
                "blocked", false,
                "message", "Pogresan verifikacioni kod.");
    }

    private String ensureSecret(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Korisnik nije pronadjen: " + email));

        return totpSecretRepository.findByUserId(user.getId())
                .map(rs.raf.banka2_bek.otp.model.TotpSecret::getSecret)
                .orElseGet(() -> totpService.generateSecret(user.getId()));
    }

    private String currentCode(String secret) {
        int raw = new GoogleAuthenticator().getTotpPassword(secret);
        return String.format("%06d", raw);
    }
}
