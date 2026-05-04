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
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ApplicationEventPublisher eventPublisher;
    private final AccountLockoutService accountLockoutService;

    public AuthService(UserRepository userRepository,
                       EmployeeRepository employeeRepository,
                       PasswordResetTokenRepository passwordResetTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       ApplicationEventPublisher eventPublisher,
                       AccountLockoutService accountLockoutService) {
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.eventPublisher = eventPublisher;
        this.accountLockoutService = accountLockoutService;
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
        user.setDateOfBirth(request.getDateOfBirth());
        user.setGender(request.getGender());
        user.setActive(true);
        user.setRole("CLIENT");

        userRepository.save(user);

        return "User registered successfully";
    }

    public AuthResponseDto login(LoginRequestDto request) {
        // Opciono.2 — proveri lockout pre nego sto se proveri lozinka.
        // Bacaja AccountLockedException ako je email lock-ovan (vidi
        // AuthService client error handling u GlobalExceptionHandler-u).
        accountLockoutService.assertNotLocked(request.getEmail());

        // First, try to find an employee with this email
        Optional<Employee> employeeOpt = employeeRepository.findByEmail(request.getEmail());
        if (employeeOpt.isPresent()) {
            Employee employee = employeeOpt.get();

            // Employee passwords are stored with salt concatenated
            String salt = employee.getSaltPassword();
            if (passwordEncoder.matches(request.getPassword() + salt, employee.getPassword())) {
                if (!Boolean.TRUE.equals(employee.getActive())) {
                    throw new RuntimeException("Employee account is not active");
                }

                accountLockoutService.recordSuccess(request.getEmail());
                String accessToken = jwtService.generateAccessToken(employee);
                String refreshToken = jwtService.generateRefreshToken(employee);
                return new AuthResponseDto(accessToken, refreshToken);
            }
        }

        // If not found as employee or password didn't match, try regular user
        Optional<User> userOpt = userRepository.findByEmail(request.getEmail());
        if (userOpt.isPresent()) {
            User user = userOpt.get();

            if (passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                if (!user.isActive()) {
                    throw new RuntimeException("User account is not active");
                }

                accountLockoutService.recordSuccess(request.getEmail());
                String accessToken = jwtService.generateAccessToken(user);
                String refreshToken = jwtService.generateRefreshToken(user);
                return new AuthResponseDto(accessToken, refreshToken);
            }
        }

        // Neither employee nor user found, or password didn't match — broji failure.
        accountLockoutService.recordFailure(request.getEmail());
        throw new RuntimeException("Invalid email or password");
    }

    public String requestPasswordReset(PasswordResetRequestDto request) {
        User user = userRepository.findByEmail(request.getEmail()).orElse(null);
        Employee employee = null;

        if (user == null) {
            employee = employeeRepository.findByEmail(request.getEmail()).orElse(null);
        }

        if (user == null && employee == null) {
            throw new RuntimeException("User with this email does not exist");
        }

        String tokenValue = UUID.randomUUID().toString();

        PasswordResetToken passwordResetToken = new PasswordResetToken();
        passwordResetToken.setToken(tokenValue);
        if (user != null) {
            passwordResetToken.setUser(user);
        } else {
            passwordResetToken.setEmployee(employee);
        }
        passwordResetToken.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        passwordResetToken.setUsed(false);

        passwordResetTokenRepository.save(passwordResetToken);

        String targetEmail = user != null ? user.getEmail() : employee.getEmail();
        eventPublisher.publishEvent(
            new PasswordResetRequestedEvent(targetEmail, tokenValue)
        );

        return "Password reset token generated and email event emitted";
    }

    public String resetPassword(PasswordResetDto reset){

        PasswordResetToken passwordResetToken = passwordResetTokenRepository.findByToken(reset.getToken())
                .orElseThrow(() -> new RuntimeException("Reset token does not exist"));


        if(passwordResetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Reset token has expired");
        }

        if(passwordResetToken.getUsed()) {
            throw new RuntimeException("Reset token has been already used");
        }

        User user = passwordResetToken.getUser();
        Employee employee = passwordResetToken.getEmployee();
        if (user == null && employee == null) {
            throw new RuntimeException("Reset token is not linked to a user");
        }

        if (user != null) {
            String hashedNewPassword = passwordEncoder.encode(reset.getNewPassword());
            user.setPassword(hashedNewPassword);
            userRepository.save(user);
        } else {
            String salt = employee.getSaltPassword();
            employee.setPassword(passwordEncoder.encode(reset.getNewPassword() + salt));
            employeeRepository.save(employee);
        }
        passwordResetToken.setUsed(true);
        passwordResetTokenRepository.save(passwordResetToken);
        return "Password reset successfully!";

    }

    public RefreshTokenResponseDto  refreshToken(RefreshTokenRequestDto  request){
        String refreshToken = request.getRefreshToken();

        if (!jwtService.isRefreshToken(refreshToken))
            throw new RuntimeException("Invalid refresh token");

        String email = jwtService.extractEmail(refreshToken);

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            Employee employee = employeeRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
            String newAccessToken = jwtService.generateAccessToken(employee);
            return new RefreshTokenResponseDto(newAccessToken, refreshToken);
        }

        String newAccessToken = jwtService.generateAccessToken(user);

        return new RefreshTokenResponseDto(newAccessToken, refreshToken);
    }
}