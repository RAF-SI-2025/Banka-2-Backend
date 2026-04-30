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

    public GlobalSecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, InterbankAuthFilter interbankAuthFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.interbankAuthFilter = interbankAuthFilter;
    }

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
                                "/auth-employee/activate",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/v3/api-docs",
                                "/exchange-rates",
                                "/exchange/calculate"
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
                        .requestMatchers("/loans/requests/**").hasAnyRole("ADMIN", "EMPLOYEE")
                        .requestMatchers(org.springframework.http.HttpMethod.GET,"/orders").hasAnyRole("ADMIN", "EMPLOYEE")
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/listings/refresh").hasAnyRole("ADMIN", "EMPLOYEE")
                        .requestMatchers("/actuaries/**").hasAnyAuthority("ROLE_ADMIN", "ADMIN", "SUPERVISOR")
                        .requestMatchers(HttpMethod.POST, "/orders").authenticated()
                        .requestMatchers(HttpMethod.GET, "/orders/my").authenticated()
                        .requestMatchers(HttpMethod.GET, "/orders/{id}").authenticated()
                        .requestMatchers("/orders/*/approve", "/orders/*/decline").hasAnyRole("ADMIN", "EMPLOYEE")
                        .requestMatchers("/portfolio/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/tax/my").authenticated()
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
                        // Inter-bank /interbank endpoint je JEDINSTVEN ulaz za druge banke,
                        // X-Api-Key auth se proverava u kontroleru (vidi protokol §2.10).
                        // TODO: kad se implementira ApiKey filter (registrovan kao
                        //       jwtAuthenticationFilter alternativa za ove pathove),
                        //       zameniti permitAll sa custom matcher-om koji preskace JWT.
                        .requestMatchers("/interbank/**").hasAuthority("ROLE_INTERBANK")
                        .requestMatchers( "/negotiations/**",
                                "/public-stock", "/user/*/**").permitAll()
                        // Arbitro asistent — svi autentifikovani korisnici (klijenti + zaposleni)
                        .requestMatchers("/assistant/**").authenticated()
                        .anyRequest().authenticated()
                )
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        )
                .addFilterBefore(interbankAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);


        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:3000", "http://localhost:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

}