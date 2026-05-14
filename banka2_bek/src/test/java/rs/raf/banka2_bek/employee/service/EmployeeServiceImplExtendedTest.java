package rs.raf.banka2_bek.employee.service;

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
import rs.raf.banka2_bek.employee.dto.*;
import rs.raf.banka2_bek.employee.event.EmployeeAccountCreatedEvent;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.ActivationTokenRepository;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;
import rs.raf.banka2_bek.employee.service.implementation.EmployeeServiceImpl;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceImplExtendedTest {

    @Mock private EmployeeRepository employeeRepository;
    @Mock private ActivationTokenRepository activationTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ApplicationEventPublisher eventPublisher;
    @InjectMocks private EmployeeServiceImpl service;

    private CreateEmployeeRequestDto buildReq() {
        CreateEmployeeRequestDto r = new CreateEmployeeRequestDto();
        r.setFirstName("Marko"); r.setLastName("P"); r.setDateOfBirth(LocalDate.of(1990,1,1));
        r.setGender("M"); r.setEmail("m@b.rs"); r.setPhone("060"); r.setAddress("BG");
        r.setUsername("marko"); r.setPosition("Dev"); r.setDepartment("IT");
        r.setPermissions(Set.of("READ")); return r;
    }

    private Employee emp(Long id, boolean active, Set<String> perms) {
        return Employee.builder().id(id).firstName("M").lastName("P")
                .dateOfBirth(LocalDate.of(1990,1,1)).gender("M").email("m@b.rs")
                .phone("0").address("B").username("m").password("h").saltPassword("s")
                .position("D").department("I").active(active)
                .permissions(perms != null ? perms : Set.of()).build();
    }

    @Test void createEmployee_ok() {
        when(employeeRepository.existsByEmail("m@b.rs")).thenReturn(false);
        when(employeeRepository.existsByUsername("marko")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("e");
        when(employeeRepository.save(any(Employee.class))).thenAnswer(i -> { Employee e = i.getArgument(0); e.setId(1L); return e; });
        when(activationTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        EmployeeResponseDto r = service.createEmployee(buildReq());
        assertThat(r.getId()).isEqualTo(1L);
        verify(eventPublisher).publishEvent(any(EmployeeAccountCreatedEvent.class));
    }

    @Test void createEmployee_dupEmail() {
        when(employeeRepository.existsByEmail("m@b.rs")).thenReturn(true);
        assertThatThrownBy(() -> service.createEmployee(buildReq())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void createEmployee_dupUsername() {
        when(employeeRepository.existsByEmail("m@b.rs")).thenReturn(false);
        when(employeeRepository.existsByUsername("marko")).thenReturn(true);
        assertThatThrownBy(() -> service.createEmployee(buildReq())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void getById_ok() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(emp(1L, true, null)));
        assertThat(service.getEmployeeById(1L).getId()).isEqualTo(1L);
    }

    @Test void getById_notFound() {
        when(employeeRepository.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getEmployeeById(9L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void getEmployees_page() {
        Page<Employee> p = new PageImpl<>(Collections.singletonList(emp(1L, true, null)));
        when(employeeRepository.findByFilters(any(), any(), any(), any(), any(Pageable.class))).thenReturn(p);
        assertThat(service.getEmployees(0, 10, null, null, null, null).getTotalElements()).isEqualTo(1);
    }

    @Test void update_ok() {
        // Spec §30 + Bug T1-010 (12.05.2026): email se NE moze menjati. Test sad
        // proverava update sa istim email-om (idempotent) + drugim ne-email
        // poljem (firstName).
        Employee e = emp(1L, true, Set.of("READ"));
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(e));
        when(employeeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        UpdateEmployeeRequestDto req = new UpdateEmployeeRequestDto();
        req.setFirstName("N");
        req.setEmail("m@b.rs"); // isti email kao postojeci → no-op
        assertThat(service.updateEmployee(1L, req).getFirstName()).isEqualTo("N");
    }

    @Test void update_admin_fails() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(emp(1L, true, Set.of("ADMIN"))));
        assertThatThrownBy(() -> service.updateEmployee(1L, new UpdateEmployeeRequestDto())).isInstanceOf(IllegalStateException.class);
    }

    @Test void update_emailChange_rejected() {
        // Bug T1-010: pokusaj promene email-a vraca IllegalArgumentException sa
        // jasnom porukom. Pre 12.05.2026 BE je propustao izmenu email-a.
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(emp(1L, true, Set.of("READ"))));
        UpdateEmployeeRequestDto req = new UpdateEmployeeRequestDto(); req.setEmail("d@b.rs");
        assertThatThrownBy(() -> service.updateEmployee(1L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email zaposlenog se ne moze menjati");
    }

    @Test void deactivate_ok() {
        Employee e = emp(1L, true, Set.of("READ"));
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(e));
        when(employeeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        service.deactivateEmployee(1L);
        assertThat(e.getActive()).isFalse();
    }

    @Test void deactivate_admin_fails() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(emp(1L, true, Set.of("ADMIN"))));
        assertThatThrownBy(() -> service.deactivateEmployee(1L)).isInstanceOf(IllegalStateException.class);
    }

    @Test void deactivate_alreadyInactive_fails() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(emp(1L, false, Set.of("READ"))));
        assertThatThrownBy(() -> service.deactivateEmployee(1L)).isInstanceOf(IllegalStateException.class);
    }

    @Test void deactivate_notFound() {
        when(employeeRepository.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deactivateEmployee(9L)).isInstanceOf(IllegalArgumentException.class);
    }
}
