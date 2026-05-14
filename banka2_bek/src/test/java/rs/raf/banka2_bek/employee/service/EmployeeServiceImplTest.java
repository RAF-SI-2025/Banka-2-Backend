package rs.raf.banka2_bek.employee.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import rs.raf.banka2_bek.employee.dto.CreateEmployeeRequestDto;
import rs.raf.banka2_bek.employee.dto.UpdateEmployeeRequestDto;
import rs.raf.banka2_bek.employee.event.EmployeeAccountCreatedEvent;
import rs.raf.banka2_bek.employee.model.ActivationToken;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.ActivationTokenRepository;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;
import rs.raf.banka2_bek.employee.service.implementation.EmployeeServiceImpl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceImplTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private ActivationTokenRepository activationTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private EmployeeServiceImpl employeeService;

    private CreateEmployeeRequestDto createRequest;

    @BeforeEach
    void setUp() {
        createRequest = new CreateEmployeeRequestDto();
        createRequest.setFirstName("Petar");
        createRequest.setLastName("Petrovic");
        createRequest.setDateOfBirth(LocalDate.of(1990, 5, 20));
        createRequest.setGender("M");
        createRequest.setEmail("petar@test.com");
        createRequest.setPhone("+38160111222");
        createRequest.setAddress("Test");
        createRequest.setUsername("petar90");
        createRequest.setPosition("QA");
        createRequest.setDepartment("IT");
        createRequest.setPermissions(Set.of("VIEW_STOCKS"));
    }

    @Test
    void createEmployeeSetsInactiveAndSendsEvent() {
        when(employeeRepository.existsByEmail(createRequest.getEmail())).thenReturn(false);
        when(employeeRepository.existsByUsername(createRequest.getUsername())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));
        when(activationTokenRepository.save(any(ActivationToken.class))).thenAnswer(inv -> inv.getArgument(0));

        employeeService.createEmployee(createRequest);

        ArgumentCaptor<Employee> employeeCaptor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeRepository).save(employeeCaptor.capture());
        Employee saved = employeeCaptor.getValue();
        assertThat(saved.getActive()).isFalse();
        assertThat(saved.getSaltPassword()).isNotBlank();
        assertThat(saved.getPermissions()).contains("VIEW_STOCKS");

        ArgumentCaptor<ActivationToken> tokenCaptor = ArgumentCaptor.forClass(ActivationToken.class);
        verify(activationTokenRepository).save(tokenCaptor.capture());
        ActivationToken token = tokenCaptor.getValue();
        assertThat(token.getToken()).isNotBlank();
        assertThat(token.getExpiresAt()).isAfter(LocalDateTime.now().minusMinutes(1));
        assertThat(token.isUsed()).isFalse();

        ArgumentCaptor<EmployeeAccountCreatedEvent> eventCaptor = ArgumentCaptor.forClass(EmployeeAccountCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEmail()).isEqualTo(createRequest.getEmail());
    }

    @Test
    void createEmployeeRejectsDuplicateEmail() {
        when(employeeRepository.existsByEmail(createRequest.getEmail())).thenReturn(true);

        assertThatThrownBy(() -> employeeService.createEmployee(createRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email");
    }

    @Test
    void updateEmployeeBlocksAdminEdits() {
        Employee admin = Employee.builder()
                .id(1L)
                .permissions(Set.of("ADMIN"))
                .active(true)
                .build();

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(admin));

        UpdateEmployeeRequestDto request = new UpdateEmployeeRequestDto();
        request.setFirstName("New");

        assertThatThrownBy(() -> employeeService.updateEmployee(1L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Admin employees cannot be edited");
    }

    @Test
    void updateEmployeeRejectsEmailChange() {
        // Bug T1-010 (12.05.2026): pre fix-a BE je dozvoljavao izmenu email-a
        // i samo proveravao da li je email vec zauzet od drugog naloga (dup
        // check). Sad email se NE moze menjati uopste (read-only po Plan_
        // Manuelnog_Testiranja + security best practice — email je identitet
        // naloga). Test sad proverava da pokusaj postavljanja drugacijeg
        // email-a baca exception sa porukom o read-only ponasanju.
        Employee employee = Employee.builder()
                .id(2L)
                .email("original@test.com")
                .permissions(Set.of("VIEW_STOCKS"))
                .active(true)
                .build();

        when(employeeRepository.findById(2L)).thenReturn(Optional.of(employee));

        UpdateEmployeeRequestDto request = new UpdateEmployeeRequestDto();
        request.setEmail("dup@test.com");

        assertThatThrownBy(() -> employeeService.updateEmployee(2L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email zaposlenog se ne moze menjati");
    }

    @Test
    void deactivateEmployeeBlocksAdmin() {
        Employee admin = Employee.builder()
                .id(3L)
                .permissions(Set.of("ADMIN"))
                .active(true)
                .build();

        when(employeeRepository.findById(3L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> employeeService.deactivateEmployee(3L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Admin employees cannot be deactivated");
    }
}
