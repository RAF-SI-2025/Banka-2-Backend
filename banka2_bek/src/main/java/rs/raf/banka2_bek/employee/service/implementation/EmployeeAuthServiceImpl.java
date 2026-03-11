package rs.raf.banka2_bek.employee.service.implementation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.employee.model.ActivationToken;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.ActivationTokenRepository;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;
import rs.raf.banka2_bek.employee.service.EmployeeAuthService;

import java.time.LocalDateTime;

/**
 * Implementation of {@link EmployeeAuthService}.
 * Authors: Aleksa Vucinic (avucinic6020rn@raf.rs), Petar Poznanovic (ppoznanovic4917rn@raf.rs)
 */
@Service
@RequiredArgsConstructor
public class EmployeeAuthServiceImpl implements EmployeeAuthService {

    private final ActivationTokenRepository activationTokenRepository;
    private final EmployeeRepository employeeRepository;

    @Override
    @Transactional
    public void activateAccount(String tokenValue) {
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

        employee.setActive(true);

        token.setUsed(true);
        token.setUsedAt(LocalDateTime.now());
        token.setInvalidated(true);

        employeeRepository.save(employee);
        activationTokenRepository.save(token);
    }
}
