package rs.raf.banka2_bek.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SecurityBeansConfig {

    /**
     * BCrypt strength 12 = ~250ms hash time on commodity hardware (4× sporije od
     * default-a 10). OWASP 2024 preporuka za Spring 4 / Java 21+: cost faktor 12+
     * radi otpornosti na GPU brute-force napade. Prelaz sa default-a 10 → 12 pomera
     * krajnji vreme login-a sa ~50ms na ~250ms — neprimetno za korisnika, znacajna
     * razlika za napadaca koji probabavi miliarde lozinki/s na GPU clusteru.
     * Postojeci hashovi u bazi (cost=10) ce i dalje verifikovati ispravno —
     * Spring BCrypt prepoznaje cost iz svake hash stringa u $2a$10$... formatu.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
