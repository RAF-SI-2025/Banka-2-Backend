package rs.raf.banka2_bek.auth.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Opciono.1 — JWT token blacklist za logout.
 *
 * JWT je stateless po dizajnu, sto znaci da "logout" na BE strani zahteva
 * ili rotaciju JWT secreta (kompleksno i invalidira SVE tokene), ili
 * odrzavanje liste invalidovanih tokena dok god ne istekne TTL.
 *
 * Ova implementacija drzi {@link Cache} sa SHA-256 hash-om token-a kao
 * kljucem (ne pun JWT — sigurnost ako se cache exfiltrira). TTL-ova su
 * usaglaseni sa JWT access-token expiration-om (15 min); posle tog
 * vremena token je vec invalid, pa nema svrhe drzati u memoriji.
 *
 * NAPOMENA: in-memory implementacija ne preziva restart servera niti
 * radi kroz multiple instance. Za production deploy zahteva Redis ili
 * sl. shared store. Za KT3 demo (single instance + container restart =
 * fresh deploy) je dovoljno.
 */
@Service
public class JwtBlacklistService {

    /** Mora biti >= JWT access token expiration (15 min) iz JwtService. */
    private static final Duration BLACKLIST_TTL = Duration.ofMinutes(20);

    private Cache<String, Long> blacklist;

    @PostConstruct
    public void init() {
        this.blacklist = Caffeine.newBuilder()
                .expireAfterWrite(BLACKLIST_TTL)
                .maximumSize(50_000)
                .build();
    }

    /**
     * Dodaje token u blacklist. Idempotentno — ponovni poziv samo refresh-uje
     * TTL.
     */
    public void blacklist(String token) {
        if (token == null || token.isBlank()) return;
        String key = hash(token);
        blacklist.put(key, System.currentTimeMillis());
    }

    /**
     * Vraca true ako je token na blacklist-i (logged out i jos nije isteklo).
     */
    public boolean isBlacklisted(String token) {
        if (token == null || token.isBlank()) return false;
        return blacklist.getIfPresent(hash(token)) != null;
    }

    /** SHA-256 hex hash; sigurnije od cuvanja celog tokena u memoriji. */
    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 je obavezan u svakoj JVM implementaciji — ovo se nikad
            // ne desava. Fallback bi smanjio sigurnost (ne smemo cuvati clear-text).
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
