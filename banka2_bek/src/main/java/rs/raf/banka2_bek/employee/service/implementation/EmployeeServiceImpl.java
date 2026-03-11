package rs.raf.banka2_bek.employee.service.implementation;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.employee.model.ActivationToken;
import rs.raf.banka2_bek.employee.repository.ActivationTokenRepository;
import rs.raf.banka2_bek.employee.dto.*;
import rs.raf.banka2_bek.employee.event.EmployeeAccountCreatedEvent;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;
import rs.raf.banka2_bek.employee.service.EmployeeService;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashSet;
import java.util.UUID;

/**
 * Implementation of {@link EmployeeService}.
 * Authors: Aleksa Vucinic (avucinic6020rn@raf.rs), Petar Poznanovic (ppoznanovic4917rn@raf.rs)
 */
@Service
@RequiredArgsConstructor
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final ActivationTokenRepository activationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Transactional
    public EmployeeResponseDto createEmployee(CreateEmployeeRequestDto request) {
        if (employeeRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("An employee with this email already exists.");
        }
        if (employeeRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("An employee with this username already exists.");
        }

        String salt = generateSalt();
        String tempPassword = UUID.randomUUID().toString();

        Employee employee = Employee.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .dateOfBirth(request.getDateOfBirth())
                .gender(request.getGender())
                .email(request.getEmail())
                .phone(request.getPhone())
                .address(request.getAddress())
                .username(request.getUsername())
                .password(passwordEncoder.encode(tempPassword + salt))
                .saltPassword(salt)
                .position(request.getPosition())
                .department(request.getDepartment())
                .active(false)
                .permissions(request.getPermissions() != null ? request.getPermissions() : new HashSet<>())
                .build();

        employeeRepository.save(employee);

        String tokenValue = UUID.randomUUID().toString();
        ActivationToken activationToken = ActivationToken.builder()
                .token(tokenValue)
                .employee(employee)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .used(false)
                .invalidated(false)
                .build();

        activationTokenRepository.save(activationToken);

        eventPublisher.publishEvent(
                new EmployeeAccountCreatedEvent(this, employee.getEmail(), employee.getFirstName(), tokenValue)
        );

        EmployeeResponseDto response = toResponse(employee);
        return response;
    }

    public Page<EmployeeResponseDto> getEmployees(int page, int limit, String email,
                                                   String firstName, String lastName, String position) {
        Pageable pageable = PageRequest.of(page, limit);
        return employeeRepository.findByFilters(email, firstName, lastName, position, pageable)
                .map(this::toResponse);
    }

    public EmployeeResponseDto updateEmployee(Long id, UpdateEmployeeRequestDto request) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Employee with ID " + id + " not found."));

        if (request.getFirstName() != null) employee.setFirstName(request.getFirstName());
        if (request.getLastName() != null) employee.setLastName(request.getLastName());
        if (request.getDateOfBirth() != null) employee.setDateOfBirth(request.getDateOfBirth());
        if (request.getGender() != null) employee.setGender(request.getGender());
        if (request.getPhone() != null) employee.setPhone(request.getPhone());
        if (request.getAddress() != null) employee.setAddress(request.getAddress());
        if (request.getPosition() != null) employee.setPosition(request.getPosition());
        if (request.getDepartment() != null) employee.setDepartment(request.getDepartment());
        if (request.getActive() != null) employee.setActive(request.getActive());
        if (request.getPermissions() != null) employee.setPermissions(request.getPermissions());

        employeeRepository.save(employee);
        return toResponse(employee);
    }

    public void deactivateEmployee(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Employee with ID " + id + " not found."));

        if (!employee.getActive()) {
            throw new IllegalStateException("Account is already deactivated.");
        }

        employee.setActive(false);
        employeeRepository.save(employee);
    }

    private String generateSalt() {
        byte[] saltBytes = new byte[16];
        SECURE_RANDOM.nextBytes(saltBytes);
        return Base64.getEncoder().encodeToString(saltBytes);
    }

    private EmployeeResponseDto toResponse(Employee employee) {
        return EmployeeResponseDto.builder()
                .id(employee.getId())
                .firstName(employee.getFirstName())
                .lastName(employee.getLastName())
                .dateOfBirth(employee.getDateOfBirth())
                .gender(employee.getGender())
                .email(employee.getEmail())
                .phone(employee.getPhone())
                .address(employee.getAddress())
                .username(employee.getUsername())
                .position(employee.getPosition())
                .department(employee.getDepartment())
                .active(employee.getActive())
                .permissions(employee.getPermissions())
                .build();
    }
}
