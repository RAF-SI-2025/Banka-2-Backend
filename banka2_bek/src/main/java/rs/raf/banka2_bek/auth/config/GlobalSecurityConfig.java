package rs.raf.banka2_bek.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;


@Configuration
@EnableMethodSecurity
public class GlobalSecurityConfig  {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final InterbankAuthFilter interbankAuthFilter;
    private final AuthRateLimitFilter authRateLimitFilter;

    public GlobalSecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                                InterbankAuthFilter interbankAuthFilter,
                                AuthRateLimitFilter authRateLimitFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.interbankAuthFilter = interbankAuthFilter;
        this.authRateLimitFilter = authRateLimitFilter;
    }

    /**
     * CORS origin lista cita se iz {@code CORS_ALLOWED_ORIGINS} env var-a
     * (comma-separated). Ako je var empty — koristimo dev default
     * (localhost:3000 + 5173). Production override:
     * {@code CORS_ALLOWED_ORIGINS=https://banka.example.com,https://admin.example.com}.
     */
    // Default-i pokrivaju sve dev FE port-ove:
    //   3000 — standardni nginx host port (CLAUDE.md default)
    //   3500 — fallback kad Hyper-V/WinNAT na Windows-u rezervise 2996-3095 range
    //   5173 — Vite dev server (`npm run dev` direktno, bez Docker-a)
    @org.springframework.beans.factory.annotation.Value("${cors.allowed-origins:http://localhost:3000,http://localhost:3500,http://localhost:5173}")
    private String corsAllowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/error",
                                "/auth/register",
                                "/auth/login",
                                "/auth/password_reset/request",
                                "/auth/password_reset/confirm",
                                "/auth/refresh",
                                "/auth/logout",
                                "/auth-employee/activate",
                                "/auth-employee/activation-token/*/status",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/v3/api-docs",
                                "/exchange-rates",
                                "/exchange/calculate",
                                "/actuator/health",
                                "/actuator/info",
                                "/actuator/prometheus"
                        ).permitAll()
                        .requestMatchers("/employees/**").hasAnyRole("ADMIN", "EMPLOYEE")
                        .requestMatchers("/clients/**").hasAnyRole("ADMIN", "EMPLOYEE")
                        .requestMatchers(org.springframework.http.HttpMethod.PATCH, "/accounts/requests/*/approve").hasAnyRole("ADMIN", "EMPLOYEE")
                        .requestMatchers(org.springframework.http.HttpMethod.PATCH, "/accounts/requests/*/reject").hasAnyRole("ADMIN", "EMPLOYEE")
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/accounts/requests").hasAnyRole("ADMIN", "EMPLOYEE")
                        .requestMatchers("/accounts/bank").hasAnyRole("ADMIN", "EMPLOYEE")
                        .requestMatchers("/accounts/all/**").hasAnyRole("ADMIN", "EMPLOYEE")
                        .requestMatchers("/accounts/client/**").hasAnyRole("ADMIN", "EMPLOYEE")
                        .requestMatchers("/cards/*/unblock").hasAnyRole("ADMIN", "EMPLOYEE")
                        .requestMatchers("/cards/*/deactivate").hasAnyRole("ADMIN", "EMPLOYEE")
                        .requestMatchers(org.springframework.http.HttpMethod.PATCH, "/cards/requests/*/approve").hasAnyRole("ADMIN", "EMPLOYEE")
                        .requestMatchers(org.springframework.http.HttpMethod.PATCH, "/cards/requests/*/reject").hasAnyRole("ADMIN", "EMPLOYEE")
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/cards/requests").hasAnyRole("ADMIN", "EMPLOYEE")
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/loans/requests/my").authenticated()
                        // Spec Celina 2 §33: klijent moze da podnese zahtev za kredit
                        // (POST /loans). Approve/reject ostaje ADMIN/EMPLOYEE preko
                        // PATCH /loans/requests/* ruta dole.
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/loans").authenticated()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/loans/*/early-repayment").authenticated()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/loans/my").authenticated()
                        .requestMatchers("/loans/requests/**").hasAnyRole("ADMIN", "EMPLOYEE")
                        .requestMatchers(org.springframework.http.HttpMethod.GET,"/orders").hasAnyRole("ADMIN", "EMPLOYEE")
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/listings/refresh").hasAnyRole("ADMIN", "EMPLOYEE")
                        .requestMatchers("/actuaries/**").hasAnyAuthority("ROLE_ADMIN", "ADMIN", "SUPERVISOR")
                        .requestMatchers(HttpMethod.POST, "/orders").authenticated()
                        .requestMatchers(HttpMethod.GET, "/orders/my").authenticated()
                        .requestMatchers(HttpMethod.GET, "/orders/{id}").authenticated()
                        .requestMatchers("/orders/*/approve", "/orders/*/decline").hasAnyRole("ADMIN", "EMPLOYEE")
                        .requestMatchers("/portfolio/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/tax/my", "/tax/my/breakdown").authenticated()
                        .requestMatchers("/tax/**").hasAnyAuthority("ROLE_ADMIN", "ADMIN", "SUPERVISOR")
                        .requestMatchers(HttpMethod.GET, "/exchanges", "/exchanges/**").permitAll()
                        .requestMatchers(HttpMethod.PATCH, "/exchanges/*/test-mode").hasAnyRole("ADMIN", "EMPLOYEE")
                        .requestMatchers(HttpMethod.GET, "/options", "/options/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/options/*/exercise").authenticated()
                        .requestMatchers(HttpMethod.POST, "/options/generate").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/margin-accounts/*/withdraw").hasRole("CLIENT")
                        .requestMatchers(HttpMethod.POST, "/margin-accounts/*/deposit").hasRole("CLIENT")
                        .requestMatchers("/margin-accounts/**").authenticated()
                        // OTC: po Celini 4 (Nova) §145-148, samo SUPERVIZORI (od zaposlenih)
                        // i KLIJENTI sa permisijom TRADE_STOCKS smeju pristupiti.
                        // Agenti su EKSPLICITNO iskljuceni — finalna provera role
                        // (klijent vs zaposleni vs agent) i dodatne validacije rade
                        // se u OtcService (vidi ensureOtcAccess helper).
                        .requestMatchers("/otc/**").hasAnyAuthority(
                                "ROLE_ADMIN", "ROLE_CLIENT", "ADMIN", "SUPERVISOR", "CLIENT")
                        // Investicioni fondovi: po Celini 4 (Nova), supervizori vide
                        // discovery/details/create/portfolio + klijenti samo discovery+details.
                        .requestMatchers("/funds/**").authenticated()
                        // Profit Banke: samo supervizori (Celina 4 (Nova) §4393-4408).
                        .requestMatchers("/profit-bank/**").hasAnyAuthority(
                                "ROLE_ADMIN", "ADMIN", "SUPERVISOR")
                        // FE-facing wrapper za inter-bank OTC: /interbank/otc/** je
                        // JWT-authenticated (klijent/supervizor pristupaju iz svog
                        // browser-a), NE X-Api-Key. MORA biti DEKLARISAN PRE generic
                        // /interbank/** matcher-a (Spring uzima prvi match).
                        .requestMatchers("/interbank/otc/**").authenticated()
                        .requestMatchers("/interbank/payments/**").authenticated()
                        // Inter-bank /interbank endpoint je JEDINSTVEN ulaz za druge banke;
                        // InterbankAuthFilter validira X-Api-Key i postavlja ROLE_INTERBANK
                        // authority pre nego sto request stigne ovde (vidi protokol §2.10).
                        // /interbank — JEDINSTVEN ulaz za 2PC poruke izmedju banaka (§2.11)
                        // /public-stock, /negotiations/**, /user/{rn}/{id} — §3.x OTC pozivi
                        // Sve trazi ROLE_INTERBANK koji InterbankAuthFilter postavlja na osnovu
                        // valjanog X-Api-Key headera (§2.10).
                        .requestMatchers("/interbank/**", "/public-stock",
                                "/negotiations/**", "/user/*/**")
                                .hasAuthority("ROLE_INTERBANK")
                        // Arbitro asistent — svi autentifikovani korisnici (klijenti + zaposleni)
                        .requestMatchers("/assistant/**").authenticated()
                        // Stedna knjizica (Celina 2): klijentske rute su authenticated,
                        // admin rute zahtevaju ADMIN ili SUPERVISOR rolu.
                        // /admin/savings/** mora biti PRE anyRequest da uzme prednost.
                        .requestMatchers(HttpMethod.POST, "/savings/deposits").authenticated()
                        .requestMatchers(HttpMethod.GET, "/savings/deposits/my").authenticated()
                        .requestMatchers(HttpMethod.GET, "/savings/deposits/*").authenticated()
                        .requestMatchers(HttpMethod.GET, "/savings/deposits/*/transactions").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/savings/deposits/*/auto-renew").authenticated()
                        .requestMatchers(HttpMethod.POST, "/savings/deposits/*/withdraw-early").authenticated()
                        .requestMatchers(HttpMethod.GET, "/savings/rates").authenticated()
                        .requestMatchers("/admin/savings/**").hasAnyAuthority("ROLE_ADMIN", "ADMIN", "SUPERVISOR")
                        .anyRequest().authenticated()
                )
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        )
                // Filter chain order: rate limit najdublje, pa interbank API key,
                // pa JWT auth. Svaki invalidan auth pokusaj puca pre nego sto stigne
                // do JWT validacije (sprecava brute-force i side-channel napade).
                .addFilterBefore(authRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(interbankAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // Security response headers (defense-in-depth) — primenjuju se na sve
                // odgovore. nginx ima svoje header-e takodje, ali Spring dodaje za
                // direktan dev pristup BE-u (8080) gde nginx ne stoji.
                .headers(headers -> headers
                        .contentTypeOptions(c -> {})       // X-Content-Type-Options: nosniff
                        .frameOptions(f -> f.deny())       // X-Frame-Options: DENY (clickjacking)
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000)) // 1 godina HSTS
                        .referrerPolicy(r -> r.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
                                        .ReferrerPolicy.NO_REFERRER))
                        .permissionsPolicyHeader(p -> p.policy(
                                "geolocation=(), microphone=(), camera=()"))
                );

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Origin-i iz env var-a (comma-separated). Production: prebaci na
        // https://banka.example.com,https://admin.example.com — sve ostale rejectaj.
        List<String> origins = java.util.Arrays.stream(corsAllowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // Specificni header-i umesto "*" — wildcard sa allowCredentials=true je
        // forbidden po CORS spec-u (browser ce odbiti) i smanjuje attack surface.
        config.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "Accept", "X-Requested-With",
                "X-Api-Key", "X-Forwarded-For", "Cache-Control"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L); // preflight cache 1h

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

}