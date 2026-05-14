package rs.raf.banka2_bek.employee.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import rs.raf.banka2_bek.employee.dto.CreateEmployeeRequestDto;
import rs.raf.banka2_bek.employee.dto.EmployeeResponseDto;
import rs.raf.banka2_bek.employee.dto.UpdateEmployeeRequestDto;
import rs.raf.banka2_bek.employee.model.ActivationToken;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.ActivationTokenRepository;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;
import rs.raf.banka2_bek.employee.service.implementation.EmployeeServiceImpl;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceImplMoreTest {

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

    private Employee buildEmployee(Long id, String email, Set<String> perms, boolean active) {
        return Employee.builder()
                .id(id)
                .firstName("F")
                .lastName("L")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .gender("M")
                .email(email)
                .phone("+381")
                .address("Addr")
                .username("user" + id)
                .position("Dev")
                .department("IT")
                .active(active)
                .permissions(perms)
                .build();
    }

    @Test
    void createEmployeeNullPermissionsDefaultsToEmptySet() {
        CreateEmployeeRequestDto req = new CreateEmployeeRequestDto();
        req.setFirstName("A"); req.setLastName("B");
        req.setDateOfBirth(LocalDate.of(1990, 1, 1));
        req.setGender("M"); req.setEmail("a@b.com"); req.setPhone("+1");
        req.setAddress("x"); req.setUsername("u"); req.setPosition("p");
        req.setDepartment("d"); req.setPermissions(null);

        when(employeeRepository.existsByEmail("a@b.com")).thenReturn(false);
        when(employeeRepository.existsByUsername("u")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("enc");
        when(employeeRepository.save(any(Employee.class))).thenAnswer(i -> i.getArgument(0));
        when(activationTokenRepository.save(any(ActivationToken.class))).thenAnswer(i -> i.getArgument(0));

        EmployeeResponseDto resp = employeeService.createEmployee(req);

        assertThat(resp).isNotNull();
        assertThat(resp.getPermissions()).isEmpty();
    }

    @Test
    void createEmployeeRejectsDuplicateUsername() {
        CreateEmployeeRequestDto req = new CreateEmployeeRequestDto();
        req.setEmail("a@b.com"); req.setUsername("dupuser");
        when(employeeRepository.existsByEmail("a@b.com")).thenReturn(false);
        when(employeeRepository.existsByUsername("dupuser")).thenReturn(true);

        assertThatThrownBy(() -> employeeService.createEmployee(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("username");
    }

    @Test
    void getEmployeeByIdReturnsDto() {
        Employee e = buildEmployee(10L, "x@y.com", Set.of("VIEW_STOCKS"), true);
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(e));

        EmployeeResponseDto resp = employeeService.getEmployeeById(10L);

        assertThat(resp.getId()).isEqualTo(10L);
        assertThat(resp.getEmail()).isEqualTo("x@y.com");
        assertThat(resp.getPermissions()).contains("VIEW_STOCKS");
    }

    @Test
    void getEmployeeByIdNotFoundThrows() {
        when(employeeRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> employeeService.getEmployeeById(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void getEmployeesPaginatesAndMaps() {
        Employee e = buildEmployee(1L, "a@b.com", Set.of(), true);
        Page<Employee> page = new PageImpl<>(List.of(e));
        when(employeeRepository.findByFilters(eq("a"), eq("F"), eq("L"), eq("Dev"), any(Pageable.class)))
                .thenReturn(page);

        Page<EmployeeResponseDto> result = employeeService.getEmployees(0, 10, "a", "F", "L", "Dev");

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getEmail()).isEqualTo("a@b.com");
    }

    @Test
    void updateEmployeeUpdatesAllFields() {
        // Spec §30 + Bug T1-010 (12.05.2026): email read-only. Test sad koristi
        // isti email (idempotent — BE prihvata) i menja sva ostala polja.
        Employee e = buildEmployee(5L, "old@x.com", Set.of("VIEW_STOCKS"), true);
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(e));
        when(employeeRepository.save(any(Employee.class))).thenAnswer(i -> i.getArgument(0));

        UpdateEmployeeRequestDto r = new UpdateEmployeeRequestDto();
        r.setEmail("old@x.com"); // isti email — no-op
        r.setFirstName("NF"); r.setLastName("NL");
        r.setDateOfBirth(LocalDate.of(2000, 2, 2));
        r.setGender("F"); r.setPhone("+999"); r.setAddress("NewAddr");
        r.setPosition("Lead"); r.setDepartment("Mgmt");
        r.setActive(false);
        r.setPermissions(Set.of("TRADE_STOCKS"));

        EmployeeResponseDto resp = employeeService.updateEmployee(5L, r);

        assertThat(resp.getEmail()).isEqualTo("old@x.com");
        assertThat(resp.getFirstName()).isEqualTo("NF");
        assertThat(resp.getLastName()).isEqualTo("NL");
        assertThat(resp.getDateOfBirth()).isEqualTo(LocalDate.of(2000, 2, 2));
        assertThat(resp.getGender()).isEqualTo("F");
        assertThat(resp.getPhone()).isEqualTo("+999");
        assertThat(resp.getAddress()).isEqualTo("NewAddr");
        assertThat(resp.getPosition()).isEqualTo("Lead");
        assertThat(resp.getDepartment()).isEqualTo("Mgmt");
        assertThat(resp.getActive()).isFalse();
        assertThat(resp.getPermissions()).contains("TRADE_STOCKS");
        verify(employeeRepository).save(any(Employee.class));
    }

    @Test
    void updateEmployeeNoFieldsDoesNothingButSaves() {
        Employee e = buildEmployee(6L, "k@k.com", Set.of("VIEW_STOCKS"), true);
        when(employeeRepository.findById(6L)).thenReturn(Optional.of(e));
        when(employeeRepository.save(any(Employee.class))).thenAnswer(i -> i.getArgument(0));

        UpdateEmployeeRequestDto r = new UpdateEmployeeRequestDto();

        EmployeeResponseDto resp = employeeService.updateEmployee(6L, r);

        assertThat(resp.getEmail()).isEqualTo("k@k.com");
        verify(employeeRepository).save(any(Employee.class));
    }

    @Test
    void updateEmployeeNotFoundThrows() {
        when(employeeRepository.findById(77L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> employeeService.updateEmployee(77L, new UpdateEmployeeRequestDto()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void updateEmployeeEmailChangeIsRejected() {
        // Bug T1-010 (12.05.2026): pokusaj promene email-a na drugi vrednost
        // se odbija sa IllegalArgumentException — email je identitet zaposlenog.
        Employee e = buildEmployee(7L, "a@a.com", Set.of("VIEW_STOCKS"), true);
        when(employeeRepository.findById(7L)).thenReturn(Optional.of(e));

        UpdateEmployeeRequestDto r = new UpdateEmployeeRequestDto();
        r.setEmail("b@b.com"); // razlicit email — odbija se
        assertThatThrownBy(() -> employeeService.updateEmployee(7L, r))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email zaposlenog se ne moze menjati");
    }

    @Test
    void deactivateEmployeeSetsInactive() {
        Employee e = buildEmployee(8L, "k@k.com", Set.of("VIEW_STOCKS"), true);
        when(employeeRepository.findById(8L)).thenReturn(Optional.of(e));
        when(employeeRepository.save(any(Employee.class))).thenAnswer(i -> i.getArgument(0));

        employeeService.deactivateEmployee(8L);

        assertThat(e.getActive()).isFalse();
        verify(employeeRepository).save(e);
    }

    @Test
    void deactivateEmployeeNotFoundThrows() {
        when(employeeRepository.findById(88L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> employeeService.deactivateEmployee(88L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void deactivateEmployeeAlreadyInactiveThrows() {
        Employee e = buildEmployee(9L, "k@k.com", Set.of("VIEW_STOCKS"), false);
        when(employeeRepository.findById(9L)).thenReturn(Optional.of(e));

        assertThatThrownBy(() -> employeeService.deactivateEmployee(9L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already deactivated");
    }
}
