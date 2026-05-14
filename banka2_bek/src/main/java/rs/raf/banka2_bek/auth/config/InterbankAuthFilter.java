package rs.raf.banka2_bek.auth.config;


import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import rs.raf.banka2_bek.interbank.config.InterbankProperties;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class InterbankAuthFilter extends OncePerRequestFilter {

    private final InterbankProperties props;

    /**
     * Putanje koje protokol §3 rezervise za inter-bank pozive (zahtevaju X-Api-Key).
     * Pored generic /interbank ulaza za 2PC (§2.11), tu su i §3 OTC negotiation rute.
     *
     * VAZNO: /interbank/otc/** i /interbank/payments/** su FE-facing wrapper rute
     * koje koriste JWT auth (klijent/supervizor pristupa iz browser-a). NE smemo
     * ih preuzeti u ovaj filter jer X-Api-Key header nije prisutan u FE zahtevu.
     */
    private static boolean isInterbankPath(String uri) {
        // FE wrapper rute — preskoci, JwtAuthenticationFilter ce ih obraditi.
        if (uri.startsWith("/interbank/otc")
                || uri.startsWith("/interbank/payments")) {
            return false;
        }
        return uri.startsWith("/interbank")
                || uri.startsWith("/public-stock")
                || uri.startsWith("/negotiations")
                || uri.startsWith("/user/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        if (!isInterbankPath(request.getRequestURI()))
        {
            filterChain.doFilter(request, response); return;
        }

        String key = request.getHeader("X-Api-Key");
        Optional<InterbankProperties.PartnerBank> match = props.findByApiKey(key);

        if (match.isEmpty())
        {
            response.setStatus(401); return;
        }
        var auth = new UsernamePasswordAuthenticationToken(match.get().getRoutingNumber(), null, List.of(new SimpleGrantedAuthority("ROLE_INTERBANK")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        filterChain.doFilter(request, response);
    }
}
