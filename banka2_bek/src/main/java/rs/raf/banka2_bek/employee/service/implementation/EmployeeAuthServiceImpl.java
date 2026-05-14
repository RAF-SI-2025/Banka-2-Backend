package rs.raf.banka2_bek.employee.service.implementation;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.employee.dto.ActivationTokenStatusDto;
import rs.raf.banka2_bek.employee.event.EmployeeActivationConfirmationEvent;
import rs.raf.banka2_bek.employee.model.ActivationToken;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.ActivationTokenRepository;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;
import rs.raf.banka2_bek.employee.service.EmployeeAuthService;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Implementation of {@link EmployeeAuthService}.
 * Authors: Aleksa Vucinic (avucinic6020rn@raf.rs), Petar Poznanovic (ppoznanovic4917rn@raf.rs)
 */
@Service
@RequiredArgsConstructor
public class EmployeeAuthServiceImpl implements EmployeeAuthService {

    private final ActivationTokenRepository activationTokenRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;

    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public void activateAccount(String tokenValue, String newPassword) {
        ActivationToken token = activationTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new IllegalArgumentException("Invalid activation token."));

        if (token.isUsed() || token.isInvalidated()) {
            throw new IllegalArgumentException("Activation token already used or invalidated.");
        }

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Activation token has expired.");
        }

        Employee employee = token.getEmployee();

        if (Boolean.TRUE.equals(employee.getActive())) {
            throw new IllegalStateException("Account is already active.");
        }

        // Set the new password (hashed with existing salt)
        String salt = employee.getSaltPassword();
        employee.setPassword(passwordEncoder.encode(newPassword + salt));
        employee.setActive(true);

        token.setUsed(true);
        token.setUsedAt(LocalDateTime.now());
        token.setInvalidated(true);

        employeeRepository.save(employee);
        activationTokenRepository.save(token);

        eventPublisher.publishEvent(new EmployeeActivationConfirmationEvent(this, employee.getEmail(), employee.getFirstName()));
    }

    /**
     * Spec Sc 9 + ad-hoc bag 12.05.2026: vraca stanje tokena bez bacanja
     * izuzetka, tako da FE moze da pre-check-uje pre renderovanja forme.
     *
     * Status redosled provere odgovara redosledu u {@link #activateAccount}:
     *   1) Token ne postoji  → INVALID
     *   2) used || invalidated → USED
     *   3) expiresAt < now → EXPIRED
     *   4) employee.active == true → ALREADY_ACTIVE (token jos validan ali nalog je vec aktivan)
     *   5) Sve OK → VALID
     */
    @Override
    public ActivationTokenStatusDto getTokenStatus(String tokenValue) {
        if (tokenValue == null || tokenValue.isBlank()) {
            return ActivationTokenStatusDto.builder().status("INVALID").build();
        }
        Optional<ActivationToken> opt = activationTokenRepository.findByToken(tokenValue);
        if (opt.isEmpty()) {
            return ActivationTokenStatusDto.builder().status("INVALID").build();
        }
        ActivationToken token = opt.get();

        if (token.isUsed() || token.isInvalidated()) {
            // Vracamo email (radi UX-a — "ovaj nalog je vec aktiviran") ali se
            // status razlikuje od ALREADY_ACTIVE jer je u ovom slucaju token
            // potrosen, FE moze prikazati "Link za aktivaciju je vec iskoriscen".
            Employee emp = token.getEmployee();
            return ActivationTokenStatusDto.builder()
                    .status("USED")
                    .expiresAt(token.getExpiresAt())
                    .email(emp != null ? emp.getEmail() : null)
                    .build();
        }

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            return ActivationTokenStatusDto.builder()
                    .status("EXPIRED")
                    .expiresAt(token.getExpiresAt())
                    .build();
        }

        Employee employee = token.getEmployee();
        if (employee != null && Boolean.TRUE.equals(employee.getActive())) {
            return ActivationTokenStatusDto.builder()
                    .status("ALREADY_ACTIVE")
                    .expiresAt(token.getExpiresAt())
                    .email(employee.getEmail())
                    .build();
        }

        return ActivationTokenStatusDto.builder()
                .status("VALID")
                .expiresAt(token.getExpiresAt())
                .email(employee != null ? employee.getEmail() : null)
                .build();
    }
}
