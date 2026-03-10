package rs.raf.banka2_bek.auth.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import rs.raf.banka2_bek.auth.dto.*;
import rs.raf.banka2_bek.auth.model.PasswordResetRequestedEvent;
import rs.raf.banka2_bek.auth.model.PasswordResetToken;
import rs.raf.banka2_bek.auth.model.User;
import rs.raf.banka2_bek.auth.repository.PasswordResetTokenRepository;
import rs.raf.banka2_bek.auth.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ApplicationEventPublisher eventPublisher;

    public AuthService(UserRepository userRepository,
                       PasswordResetTokenRepository passwordResetTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.eventPublisher = eventPublisher;
    }

    public String register(RegisterRequestDto request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("User with this email already exists");
        }

        User user = new User();
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setUsername(request.getUsername());
        user.setPhone(request.getPhone());
        user.setAddress(request.getAddress());
        user.setActive(true);
        user.setRole("CLIENT");

        userRepository.save(user);

        return "User registered successfully";
    }

    public AuthResponseDto login(LoginRequestDto request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid email or password");
        }

        if (!user.isActive()) {
            throw new RuntimeException("User account is not active");
        }

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        return new AuthResponseDto(accessToken, refreshToken);
    }

    public String requestPasswordReset(PasswordResetRequestDto request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User with this email does not exist"));

        String tokenValue = UUID.randomUUID().toString();

        PasswordResetToken passwordResetToken = new PasswordResetToken();
        passwordResetToken.setToken(tokenValue);
        passwordResetToken.setUser(user);
        passwordResetToken.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        passwordResetToken.setUsed(false);

        passwordResetTokenRepository.save(passwordResetToken);

        eventPublisher.publishEvent(
                new PasswordResetRequestedEvent(user.getEmail(), tokenValue)
        );

        return "Password reset token generated and email event emitted";
    }

    public String resetPassword(PasswordResetDto reset){

        PasswordResetToken passwordResetToken = passwordResetTokenRepository.findByToken(reset.getToken())
                .orElseThrow(() -> new RuntimeException("Reset token does not exist"));


        if(passwordResetToken.getExpiresAt().isBefore(LocalDateTime.now())) return "Reset token has expired";

        if(passwordResetToken.getUsed()) return "Reset token has been already used";

        User user = passwordResetToken.getUser();
        String hashedNewPassword = passwordEncoder.encode(reset.getNewPassword());
        user.setPassword(hashedNewPassword);
        userRepository.save(user);
        passwordResetToken.setUsed(true);
        passwordResetTokenRepository.save(passwordResetToken);
        return "Password reset successfully!";

    }

    public RefreshTokenResponseDto  refreshToken(RefreshTokenRequestDto  request){
        String refreshToken = request.getRefreshToken();

        if (!jwtService.isRefreshToken(refreshToken))
            throw new RuntimeException("Invalid refresh token");

        String email = jwtService.extractEmail(refreshToken);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String newAccessToken = jwtService.generateAccessToken(user);

        return new RefreshTokenResponseDto(newAccessToken, refreshToken);
    }
}