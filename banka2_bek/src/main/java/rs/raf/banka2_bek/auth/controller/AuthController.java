package rs.raf.banka2_bek.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import rs.raf.banka2_bek.auth.dto.*;
import rs.raf.banka2_bek.auth.service.AuthService;
import rs.raf.banka2_bek.auth.service.JwtBlacklistService;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtBlacklistService blacklistService;

    public AuthController(AuthService authService, JwtBlacklistService blacklistService) {
        this.authService = authService;
        this.blacklistService = blacklistService;
    }

    @PostMapping("/register")
    public ResponseEntity<MessageResponseDto> register(@Valid @RequestBody RegisterRequestDto request) {
        return ResponseEntity.ok(new MessageResponseDto(authService.register(request)));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login(@Valid @RequestBody LoginRequestDto request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/password_reset/request")
    public ResponseEntity<MessageResponseDto> requestPasswordReset(@Valid @RequestBody PasswordResetRequestDto request) {
        return ResponseEntity.ok(new MessageResponseDto(authService.requestPasswordReset(request)));
    }

    @PostMapping("/password_reset/confirm")
    public ResponseEntity<MessageResponseDto> confirmPasswordReset(@Valid @RequestBody PasswordResetDto reset) {
        return ResponseEntity.ok(new MessageResponseDto(authService.resetPassword(reset)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshTokenResponseDto> refresh(@Valid @RequestBody RefreshTokenRequestDto request) {
        return ResponseEntity.ok(authService.refreshToken(request));
    }

    /**
     * Opciono.1 — Logout. Povlaci access token iz Authorization headera i
     * stavlja ga na blacklist do isteka TTL-a (15 min). Frontend treba
     * dodatno da obrise sessionStorage.
     */
    @PostMapping("/logout")
    public ResponseEntity<MessageResponseDto> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            blacklistService.blacklist(authHeader.substring(7));
        }
        return ResponseEntity.ok(new MessageResponseDto("Uspesno odjavljen."));
    }
}