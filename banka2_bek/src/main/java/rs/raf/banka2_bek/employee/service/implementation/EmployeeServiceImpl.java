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
import rs.raf.banka2_bek.investmentfund.service.InvestmentFundService;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
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
    private final InvestmentFundService investmentFundService;

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

    public EmployeeResponseDto getEmployeeById(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Employee with ID " + id + " not found."));
        return toResponse(employee);
    }

    public Page<EmployeeResponseDto> getEmployees(int page, int limit, String email,
                                                   String firstName, String lastName, String position) {
        Pageable pageable = PageRequest.of(page, limit);
        return employeeRepository.findByFilters(email, firstName, lastName, position, pageable)
                .map(this::toResponse);
    }

    @Transactional
    public EmployeeResponseDto updateEmployee(Long id, UpdateEmployeeRequestDto request) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Employee with ID " + id + " not found."));

        if (isAdminEmployee(employee)) {
            throw new IllegalStateException("Admin employees cannot be edited.");
        }

        // Spec §30 dozvoljava izmenu "svih informacija osim ID-a i passworda",
        // ALI email je identitet (login key, references u tokenima, audit log
        // co-Author trail) — Plan_Manuelnog_Testiranja.pdf i T1-009/T1-010
        // bug izvestaj traze read-only ponasanje. Defense-in-depth: kad neko
        // posalje PUT sa drugacijim email-om (npr. zaobilazi disabled FE polje
        // ili udje direktno na API), tihi no-op — ne ruzimo idempotent request
        // sa istim email-om ali odbijamo promenu. Razlog za 400 umesto tihog
        // ignore-a: FE mora videti da je akcija neuspela inace bi user mislio
        // da je promenio email pa ne moze posle da se uloguje sa starim.
        if (request.getEmail() != null && !request.getEmail().equals(employee.getEmail())) {
            throw new IllegalArgumentException(
                    "Email zaposlenog se ne moze menjati. Email je identifikator naloga.");
        }

        if (request.getFirstName() != null) employee.setFirstName(request.getFirstName());
        if (request.getLastName() != null) employee.setLastName(request.getLastName());
        if (request.getDateOfBirth() != null) employee.setDateOfBirth(request.getDateOfBirth());
        if (request.getGender() != null) employee.setGender(request.getGender());
        if (request.getPhone() != null) employee.setPhone(request.getPhone());
        if (request.getAddress() != null) employee.setAddress(request.getAddress());
        if (request.getPosition() != null) employee.setPosition(request.getPosition());
        if (request.getDepartment() != null) employee.setDepartment(request.getDepartment());
        if (request.getActive() != null) employee.setActive(request.getActive());
        if (request.getPermissions() != null) {
            // P5 — Spec Celina 4 (Nova) §3797-3879:
            // Ako se SUPERVISOR permisija UKLANJA supervizoru koji upravlja fondovima,
            // vlasnistvo svih njegovih fondova prebacuje se na admina koji menja
            // permisije (currentAdminId iz security konteksta — fallback: prvi
            // aktivan admin u bazi). Ovo MORA da se desi PRE save-a permisija
            // tako da audit log u InvestmentFundService.reassignFundManager
            // zabeleyi pravu vrednost menadzera.
            Set<String> oldPermissions = employee.getPermissions() != null
                    ? new HashSet<>(employee.getPermissions())
                    : new HashSet<>();
            Set<String> newPermissions = new HashSet<>(request.getPermissions());
            boolean wasSupervisor = oldPermissions.contains("SUPERVISOR")
                    || oldPermissions.contains("ADMIN");
            boolean isSupervisor = newPermissions.contains("SUPERVISOR")
                    || newPermissions.contains("ADMIN");
            if (wasSupervisor && !isSupervisor) {
                Long newManagerId = resolveCurrentAdminId(id);
                investmentFundService.reassignFundManager(id, newManagerId);
            }
            employee.setPermissions(request.getPermissions());
        }

        employeeRepository.save(employee);
        return toResponse(employee);
    }

    /**
     * Pronalazi admina koji ce primiti vlasnistvo nad fondovima nakon sto je
     * supervizor izgubio permisiju. Strategija (po prioritetu):
     *  1) Trenutno autentifikovani korisnik ako je admin (oni su izvrsili akciju)
     *  2) Prvi aktivan zaposleni sa ADMIN permisijom (fallback)
     *  3) {@code fallbackId} (originalni supervizor) ako ni 2 ne postoji —
     *     u tom slucaju reassignFundManager je no-op (oldId == newId).
     */
    private Long resolveCurrentAdminId(Long fallbackId) {
        try {
            org.springframework.security.core.Authentication auth =
                    org.springframework.security.core.context.SecurityContextHolder
                            .getContext().getAuthentication();
            if (auth != null && auth.getName() != null) {
                java.util.Optional<Employee> me = employeeRepository.findByEmail(auth.getName());
                if (me.isPresent() && me.get().getPermissions() != null
                        && me.get().getPermissions().contains("ADMIN")) {
                    return me.get().getId();
                }
            }
        } catch (RuntimeException ignored) {
            // Pad na fallback ispod
        }
        // Fallback: prvi admin
        return employeeRepository.findAll().stream()
                .filter(e -> Boolean.TRUE.equals(e.getActive()))
                .filter(e -> e.getPermissions() != null && e.getPermissions().contains("ADMIN"))
                .map(Employee::getId)
                .findFirst()
                .orElse(fallbackId);
    }

    @Transactional
    public void deactivateEmployee(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Employee with ID " + id + " not found."));

        if (isAdminEmployee(employee)) {
            throw new IllegalStateException("Admin employees cannot be deactivated.");
        }

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

    private boolean isAdminEmployee(Employee employee) {
        return employee.getPermissions() != null && employee.getPermissions().contains("ADMIN");
    }
}
