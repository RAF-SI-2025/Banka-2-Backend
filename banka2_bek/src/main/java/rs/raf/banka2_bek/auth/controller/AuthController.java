package rs.raf.banka2_bek.auth.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import rs.raf.banka2_bek.auth.dto.*;
import rs.raf.banka2_bek.auth.service.AuthService;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
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
    public ResponseEntity<RefreshTokenResponseDto> refresh(@RequestBody RefreshTokenRequestDto request) {
        return ResponseEntity.ok(authService.refreshToken(request));
    }

}