package rs.raf.banka2_bek.actuary.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import rs.raf.banka2_bek.actuary.dto.ActuaryInfoDto;
import rs.raf.banka2_bek.actuary.dto.UpdateActuaryLimitDto;
import rs.raf.banka2_bek.actuary.model.ActuaryInfo;
import rs.raf.banka2_bek.actuary.model.ActuaryType;
import rs.raf.banka2_bek.actuary.repository.ActuaryInfoRepository;
import rs.raf.banka2_bek.actuary.service.implementation.ActuaryServiceImpl;
import rs.raf.banka2_bek.employee.model.Employee;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActuaryServiceImplTest {

    @Mock
    private ActuaryInfoRepository actuaryInfoRepository;
    @Mock
    private rs.raf.banka2_bek.audit.service.AuditLogService auditLogService;
    @Mock
    private rs.raf.banka2_bek.employee.repository.EmployeeRepository employeeRepository;

    @InjectMocks
    private ActuaryServiceImpl actuaryService;


    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Employee mockEmployee;
    private ActuaryInfo mockActuaryInfo;
    private final Long RESET_EMPLOYEE_ID = 1L;
    // ──────────────────────────────────────────────────────────────────
    //  Helperi za kreiranje test podataka
    // ──────────────────────────────────────────────────────────────────


    @BeforeEach
    void setUp() {
        mockEmployee = Employee.builder()
                .id(RESET_EMPLOYEE_ID)
                .firstName("Luka")
                .lastName("Draskovic")
                .build();
        mockEmployee.setEmail("luka@banka2.rs");

        mockActuaryInfo = new ActuaryInfo();
        mockActuaryInfo.setId(100L);
        mockActuaryInfo.setEmployee(mockEmployee);
        mockActuaryInfo.setUsedLimit(new BigDecimal("500.00"));
        mockActuaryInfo.setDailyLimit(new BigDecimal("1000.00"));
        mockActuaryInfo.setNeedApproval(false);
    }
    private Employee createEmployee(Long id, String firstName, String lastName, String email) {
        Employee emp = Employee.builder()
                .id(id)
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .dateOfBirth(java.time.LocalDate.of(1990, 1, 1))
                .gender("M")
                .phone("123")
                .address("Adresa")
                .username(email)
                .password("pass")
                .saltPassword("salt")
                .position("Pozicija")
                .department("Dept")
                .active(true)
                .permissions(Set.of())
                .build();
        return emp;
    }

    private ActuaryInfo createAgentInfo(Long id, Employee employee,
                                        BigDecimal dailyLimit, BigDecimal usedLimit,
                                        boolean needApproval) {
        ActuaryInfo info = new ActuaryInfo();
        info.setId(id);
        info.setEmployee(employee);
        info.setActuaryType(ActuaryType.AGENT);
        info.setDailyLimit(dailyLimit);
        info.setUsedLimit(usedLimit);
        info.setNeedApproval(needApproval);
        return info;
    }

    private ActuaryInfo createSupervisorInfo(Long id, Employee employee) {
        ActuaryInfo info = new ActuaryInfo();
        info.setId(id);
        info.setEmployee(employee);
        info.setActuaryType(ActuaryType.SUPERVISOR);
        info.setDailyLimit(null);
        info.setUsedLimit(null);
        info.setNeedApproval(false);
        return info;
    }


    private void setAuthenticatedUser(String email) {
        UserDetails principal = org.springframework.security.core.userdetails.User.withUsername(email)
                .password("ignored")
                .authorities("ROLE_USER")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    // ══════════════════════════════════════════════════════════════════
    //  getAgents
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getAgents")
    class GetAgents {

        @Test
        @DisplayName("vraca sve agente bez filtera")
        void returnsAllAgents() {
            Employee marko = createEmployee(10L, "Marko", "Markovic", "marko.markovic@banka.rs");
            Employee jelena = createEmployee(11L, "Jelena", "Jovanovic", "jelena.jovanovic@banka.rs");

            ActuaryInfo agent1 = createAgentInfo(1L, marko,
                    new BigDecimal("100000"), new BigDecimal("15000"), false);
            ActuaryInfo agent2 = createAgentInfo(2L, jelena,
                    new BigDecimal("50000"), BigDecimal.ZERO, true);

            when(actuaryInfoRepository.findByTypeAndFilters(
                    ActuaryType.AGENT, null, null, null, null))
                    .thenReturn(List.of(agent1, agent2));

            List<ActuaryInfoDto> result = actuaryService.getAgents(null, null, null, null);

            assertEquals(2, result.size());
            assertEquals("Marko Markovic", result.get(0).getEmployeeName());
            assertEquals("Jelena Jovanovic", result.get(1).getEmployeeName());
        }

        @Test
        @DisplayName("vraca praznu listu ako nema agenata")
        void returnsEmptyList() {
            when(actuaryInfoRepository.findByTypeAndFilters(
                    ActuaryType.AGENT, null, null, null, null))
                    .thenReturn(Collections.emptyList());

            List<ActuaryInfoDto> result = actuaryService.getAgents(null, null, null, null);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("getActuaryInfo")
    class GetActuaryInfo {

        @Test
        @DisplayName("vraca aktuarske podatke za postojeceg zaposlenog")
        void returnsActuaryInfo() {
            Employee marko = createEmployee(10L, "Marko", "Markovic", "marko.markovic@banka.rs");
            ActuaryInfo agentInfo = createAgentInfo(1L, marko,
                    new BigDecimal("100000"), new BigDecimal("15000"), false);

            when(actuaryInfoRepository.findByEmployeeId(10L)).thenReturn(Optional.of(agentInfo));

            ActuaryInfoDto result = actuaryService.getActuaryInfo(10L);

            assertEquals(10L, result.getEmployeeId());
            assertEquals("AGENT", result.getActuaryType());
        }

        @Test
        @DisplayName("baca izuzetak ako zapis ne postoji")
        void notFound() {
            when(actuaryInfoRepository.findByEmployeeId(999L)).thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> actuaryService.getActuaryInfo(999L));

            assertTrue(ex.getMessage().contains("999"));
        }
    }

    @Nested
    @DisplayName("updateAgentLimit")
    class UpdateAgentLimit {

        @Test
        @DisplayName("supervizor moze da promeni dailyLimit i needApproval")
        void supervisorCanUpdateAgent() {
            setAuthenticatedUser("supervisor@banka.rs");

            Employee supervisorEmployee = createEmployee(20L, "Nina", "Nikolic", "supervisor@banka.rs");
            ActuaryInfo supervisorInfo = createSupervisorInfo(5L, supervisorEmployee);

            Employee agentEmployee = createEmployee(10L, "Marko", "Markovic", "marko@banka.rs");
            ActuaryInfo agentInfo = createAgentInfo(1L, agentEmployee,
                    new BigDecimal("100000"), new BigDecimal("15000"), false);

            UpdateActuaryLimitDto dto = new UpdateActuaryLimitDto();
            dto.setDailyLimit(new BigDecimal("250000"));
            dto.setNeedApproval(true);

            when(actuaryInfoRepository.findByEmployee_Email("supervisor@banka.rs")).thenReturn(Optional.of(supervisorInfo));
            when(actuaryInfoRepository.findByEmployeeId(10L)).thenReturn(Optional.of(agentInfo));
            when(actuaryInfoRepository.save(any(ActuaryInfo.class))).thenAnswer(inv -> inv.getArgument(0));

            ActuaryInfoDto result = actuaryService.updateAgentLimit(10L, dto);

            assertEquals(new BigDecimal("250000"), result.getDailyLimit());
            assertTrue(result.isNeedApproval());
            assertEquals(new BigDecimal("15000"), result.getUsedLimit());
            verify(actuaryInfoRepository).save(agentInfo);
        }

        @Test
        @DisplayName("supervizor zaposleni moze da menja samo prosledjena polja")
        void supervisorEmployeeCanPartiallyUpdateAgent() {
            setAuthenticatedUser("supervisor@banka.rs");

            Employee supervisor = createEmployee(20L, "Nina", "Nikolic", "supervisor@banka.rs");
            supervisor.setPermissions(Set.of("SUPERVISOR"));
            ActuaryInfo supervisorInfo = createSupervisorInfo(5L, supervisor);

            Employee agentEmployee = createEmployee(10L, "Marko", "Markovic", "marko@banka.rs");
            ActuaryInfo agentInfo = createAgentInfo(1L, agentEmployee,
                    new BigDecimal("100000"), new BigDecimal("15000"), false);

            UpdateActuaryLimitDto dto = new UpdateActuaryLimitDto();
            dto.setNeedApproval(true);

            when(actuaryInfoRepository.findByEmployee_Email("supervisor@banka.rs")).thenReturn(Optional.of(supervisorInfo));
            when(actuaryInfoRepository.findByEmployeeId(10L)).thenReturn(Optional.of(agentInfo));
            when(actuaryInfoRepository.save(any(ActuaryInfo.class))).thenAnswer(inv -> inv.getArgument(0));

            ActuaryInfoDto result = actuaryService.updateAgentLimit(10L, dto);

            assertEquals(new BigDecimal("100000"), result.getDailyLimit());
            assertTrue(result.isNeedApproval());
            verify(actuaryInfoRepository).save(agentInfo);
        }

        @Test
        @DisplayName("agent ne sme da menja tudje limite")
        void agentCannotUpdateAgentLimit() {
            setAuthenticatedUser("agent@banka.rs");

            Employee agent = createEmployee(20L, "A", "G", "agent@banka.rs");
            agent.setPermissions(Set.of("AGENT"));
            ActuaryInfo agentOwnInfo = createAgentInfo(5L, agent,
                    new BigDecimal("50000"), new BigDecimal("1000"), false);

            UpdateActuaryLimitDto dto = new UpdateActuaryLimitDto();
            dto.setDailyLimit(new BigDecimal("1"));

            when(actuaryInfoRepository.findByEmployee_Email("agent@banka.rs")).thenReturn(Optional.of(agentOwnInfo));

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> actuaryService.updateAgentLimit(10L, dto));

            assertEquals("Only supervisors can update agent limits.", ex.getMessage());
            verify(actuaryInfoRepository, never()).save(any());
        }

        @Test
        @DisplayName("nije dozvoljeno menjati supervizora")
        void cannotUpdateSupervisor() {
            setAuthenticatedUser("supervisor@banka.rs");

            Employee supervisorEmployee = createEmployee(20L, "Nina", "Nikolic", "supervisor@banka.rs");
            ActuaryInfo supervisorInfo = createSupervisorInfo(5L, supervisorEmployee);

            Employee targetSupervisor = createEmployee(30L, "Sara", "Savic", "nina@banka.rs");
            ActuaryInfo targetSupervisorInfo = createSupervisorInfo(6L, targetSupervisor);

            UpdateActuaryLimitDto dto = new UpdateActuaryLimitDto();
            dto.setDailyLimit(new BigDecimal("999"));

            when(actuaryInfoRepository.findByEmployee_Email("supervisor@banka.rs")).thenReturn(Optional.of(supervisorInfo));
            when(actuaryInfoRepository.findByEmployeeId(30L)).thenReturn(Optional.of(targetSupervisorInfo));

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> actuaryService.updateAgentLimit(30L, dto));

            assertTrue(ex.getMessage().contains("only be updated for agents"));
            verify(actuaryInfoRepository, never()).save(any());
        }

        @Test
        @DisplayName("ako nema autentifikacije baca exception")
        void unauthenticatedUpdateFails() {
            UpdateActuaryLimitDto dto = new UpdateActuaryLimitDto();
            dto.setNeedApproval(true);

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> actuaryService.updateAgentLimit(10L, dto));

            assertTrue(ex.getMessage().contains("Authenticated user is required"));
        }
    }

    @Nested
    @DisplayName("resetUsedLimit")
    class ResetUsedLimit {

        @Test
        @DisplayName("moze rucno da resetuje usedLimit agenta")
        void adminCanResetAgentLimit() {
            Employee agentEmployee = createEmployee(10L, "Marko", "Markovic", "marko@banka.rs");
            ActuaryInfo agentInfo = createAgentInfo(1L, agentEmployee,
                    new BigDecimal("100000"), new BigDecimal("15000.50"), false);

            when(actuaryInfoRepository.findByEmployeeId(10L)).thenReturn(Optional.of(agentInfo));
            when(actuaryInfoRepository.save(any(ActuaryInfo.class))).thenAnswer(inv -> inv.getArgument(0));

            ActuaryInfoDto result = actuaryService.resetUsedLimit(10L);

            assertEquals(BigDecimal.ZERO, result.getUsedLimit());
            verify(actuaryInfoRepository).save(agentInfo);
        }

        @Test
        @DisplayName("ne moze da resetuje supervizora")
        void cannotResetSupervisor() {
            Employee supervisor = createEmployee(20L, "Nina", "Nikolic", "supervisor@banka.rs");
            supervisor.setPermissions(Set.of("SUPERVISOR"));
            ActuaryInfo supervisorInfo = createSupervisorInfo(5L, supervisor);

            when(actuaryInfoRepository.findByEmployeeId(20L)).thenReturn(Optional.of(supervisorInfo));

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> actuaryService.resetUsedLimit(20L));

            assertTrue(ex.getMessage().contains("only allowed for Agents"));
            verify(actuaryInfoRepository, never()).save(any());
        }

        @Test
        @DisplayName("reset je idempotentan kada je usedLimit vec nula")
        void resetIsIdempotentWhenAlreadyZero() {
            Employee agentEmployee = createEmployee(10L, "Marko", "Markovic", "marko@banka.rs");
            ActuaryInfo agentInfo = createAgentInfo(1L, agentEmployee,
                    new BigDecimal("100000"), BigDecimal.ZERO, false);

            when(actuaryInfoRepository.findByEmployeeId(10L)).thenReturn(Optional.of(agentInfo));
            when(actuaryInfoRepository.save(any(ActuaryInfo.class))).thenAnswer(inv -> inv.getArgument(0));

            ActuaryInfoDto result = actuaryService.resetUsedLimit(10L);

            assertEquals(BigDecimal.ZERO, result.getUsedLimit());
            verify(actuaryInfoRepository).save(agentInfo);
        }
    }

    @Nested
    @DisplayName("resetAllUsedLimits")
    class ResetAllUsedLimits {

        @Test
        @DisplayName("resetuje usedLimit na 0 za sve agente")
        void resetsAllAgentsToZero() {
            Employee marko = createEmployee(10L, "Marko", "Markovic", "marko.markovic@banka.rs");
            Employee jelena = createEmployee(11L, "Jelena", "Jovanovic", "jelena.jovanovic@banka.rs");

            ActuaryInfo agent1 = createAgentInfo(1L, marko,
                    new BigDecimal("100000"), new BigDecimal("15000.50"), false);
            ActuaryInfo agent2 = createAgentInfo(2L, jelena,
                    new BigDecimal("50000"), new BigDecimal("999.99"), true);

            when(actuaryInfoRepository.findAllByActuaryType(ActuaryType.AGENT))
                    .thenReturn(List.of(agent1, agent2));
            when(actuaryInfoRepository.findByEmployeeId(10L)).thenReturn(Optional.of(agent1));
            when(actuaryInfoRepository.findByEmployeeId(11L)).thenReturn(Optional.of(agent2));
            when(actuaryInfoRepository.save(any(ActuaryInfo.class))).thenAnswer(inv -> inv.getArgument(0));

            actuaryService.resetAllUsedLimits();

            assertEquals(BigDecimal.ZERO, agent1.getUsedLimit());
            assertEquals(BigDecimal.ZERO, agent2.getUsedLimit());
            assertEquals(new BigDecimal("100000"), agent1.getDailyLimit());
            assertTrue(agent2.isNeedApproval());

            verify(actuaryInfoRepository).save(agent1);
            verify(actuaryInfoRepository).save(agent2);
        }

        @Test
        @DisplayName("ako nema agenata ne baca exception")
        void doesNothingWhenNoAgentsExist() {
            when(actuaryInfoRepository.findAllByActuaryType(ActuaryType.AGENT))
                    .thenReturn(Collections.emptyList());

            assertDoesNotThrow(() -> actuaryService.resetAllUsedLimits());

            verify(actuaryInfoRepository).findAllByActuaryType(ActuaryType.AGENT);
            verify(actuaryInfoRepository, never()).save(any());
        }

        @Test
        @DisplayName("resetuje i null usedLimit na nulu")
        void resetsNullUsedLimitToZero() {
            Employee marko = createEmployee(10L, "Marko", "Markovic", "marko.markovic@banka.rs");
            ActuaryInfo agent = createAgentInfo(1L, marko,
                    new BigDecimal("100000"), null, false);

            when(actuaryInfoRepository.findAllByActuaryType(ActuaryType.AGENT)).thenReturn(List.of(agent));
            when(actuaryInfoRepository.findByEmployeeId(10L)).thenReturn(Optional.of(agent));
            when(actuaryInfoRepository.save(any(ActuaryInfo.class))).thenAnswer(inv -> inv.getArgument(0));

            actuaryService.resetAllUsedLimits();

            assertEquals(BigDecimal.ZERO, agent.getUsedLimit());
            verify(actuaryInfoRepository).save(agent);
        }

        @Test
        @DisplayName("uvek trazi samo AGENT zapise iz repository-ja")
        void queriesOnlyAgentType() {
            when(actuaryInfoRepository.findAllByActuaryType(ActuaryType.AGENT))
                    .thenReturn(Collections.emptyList());

            actuaryService.resetAllUsedLimits();

            verify(actuaryInfoRepository).findAllByActuaryType(ActuaryType.AGENT);
            verify(actuaryInfoRepository, never()).findByEmployeeId(anyLong());
        }
    }

    @Nested
    @DisplayName("updateAgentLimit - supervisor flow")
    class UpdateAgentLimitSupervisorFlow {

        @org.junit.jupiter.api.AfterEach
        void clearSecurityContext() {
            SecurityContextHolder.clearContext();
        }

        @Test
        @DisplayName("supervizor moze da azurira limit i needApproval za agenta")
        void updatesLimitAndNeedApproval() {
            setAuthenticatedUser("supervisor@banka.rs");

            Employee supervisorEmployee = createEmployee(1L, "Nina", "Nikolic", "supervisor@banka.rs");
            Employee agentEmployee = createEmployee(2L, "Marko", "Markovic", "marko@banka.rs");

            ActuaryInfo supervisorInfo = createSupervisorInfo(100L, supervisorEmployee);
            ActuaryInfo agentInfo = createAgentInfo(200L, agentEmployee,
                    new BigDecimal("100000.00"), new BigDecimal("1000.00"), false);

            UpdateActuaryLimitDto dto = new UpdateActuaryLimitDto();
            dto.setDailyLimit(new BigDecimal("250000.00"));
            dto.setNeedApproval(true);

            when(actuaryInfoRepository.findByEmployee_Email("supervisor@banka.rs"))
                    .thenReturn(Optional.of(supervisorInfo));
            when(actuaryInfoRepository.findByEmployeeId(2L)).thenReturn(Optional.of(agentInfo));
            when(actuaryInfoRepository.save(any(ActuaryInfo.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ActuaryInfoDto result = actuaryService.updateAgentLimit(2L, dto);

            assertEquals(new BigDecimal("250000.00"), result.getDailyLimit());
            assertTrue(result.isNeedApproval());
            assertEquals("AGENT", result.getActuaryType());
            verify(actuaryInfoRepository).save(agentInfo);
        }

        @Test
        @DisplayName("azurira samo prosledjeno polje, ostala ostaju nepromenjena")
        void updatesOnlyProvidedField() {
            setAuthenticatedUser("supervisor@banka.rs");

            Employee supervisorEmployee = createEmployee(1L, "Nina", "Nikolic", "supervisor@banka.rs");
            Employee agentEmployee = createEmployee(2L, "Jelena", "Jovanovic", "jelena@banka.rs");

            ActuaryInfo supervisorInfo = createSupervisorInfo(100L, supervisorEmployee);
            ActuaryInfo agentInfo = createAgentInfo(201L, agentEmployee,
                    new BigDecimal("50000.00"), BigDecimal.ZERO, false);

            UpdateActuaryLimitDto dto = new UpdateActuaryLimitDto();
            dto.setDailyLimit(null);
            dto.setNeedApproval(true);

            when(actuaryInfoRepository.findByEmployee_Email("supervisor@banka.rs"))
                    .thenReturn(Optional.of(supervisorInfo));
            when(actuaryInfoRepository.findByEmployeeId(2L)).thenReturn(Optional.of(agentInfo));
            when(actuaryInfoRepository.save(any(ActuaryInfo.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ActuaryInfoDto result = actuaryService.updateAgentLimit(2L, dto);

            assertEquals(new BigDecimal("50000.00"), result.getDailyLimit());
            assertTrue(result.isNeedApproval());
        }

        @Test
        @DisplayName("baca izuzetak kada nema autentifikacije")
        void throwsWhenNoAuthentication() {
            SecurityContextHolder.clearContext();

            UpdateActuaryLimitDto dto = new UpdateActuaryLimitDto();
            dto.setDailyLimit(new BigDecimal("1"));

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> actuaryService.updateAgentLimit(2L, dto));

            assertTrue(ex.getMessage().contains("Authenticated user is required"));
            verifyNoInteractions(actuaryInfoRepository);
        }

        @Test
        @DisplayName("baca izuzetak kada ulogovani korisnik nije aktuar")
        void throwsWhenCurrentUserIsNotActuary() {
            setAuthenticatedUser("non-actuary@banka.rs");

            UpdateActuaryLimitDto dto = new UpdateActuaryLimitDto();
            dto.setNeedApproval(true);

            when(actuaryInfoRepository.findByEmployee_Email("non-actuary@banka.rs"))
                    .thenReturn(Optional.empty());

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> actuaryService.updateAgentLimit(2L, dto));

            assertTrue(ex.getMessage().contains("not an actuary"));
        }

        @Test
        @DisplayName("baca izuzetak kada aktuar nije supervizor")
        void throwsWhenCurrentUserIsNotSupervisor() {
            setAuthenticatedUser("agent@banka.rs");

            Employee currentAgentEmployee = createEmployee(1L, "Agent", "One", "agent@banka.rs");
            ActuaryInfo currentAgentInfo = createAgentInfo(300L, currentAgentEmployee,
                    new BigDecimal("10000.00"), BigDecimal.ZERO, false);

            UpdateActuaryLimitDto dto = new UpdateActuaryLimitDto();
            dto.setNeedApproval(true);

            when(actuaryInfoRepository.findByEmployee_Email("agent@banka.rs"))
                    .thenReturn(Optional.of(currentAgentInfo));

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> actuaryService.updateAgentLimit(2L, dto));

            assertTrue(ex.getMessage().contains("Only supervisors can update agent limits"));
        }

        @Test
        @DisplayName("baca izuzetak kada supervizor pokusa da menja sebe")
        void throwsWhenSupervisorUpdatesSelf() {
            setAuthenticatedUser("supervisor@banka.rs");

            Employee supervisorEmployee = createEmployee(77L, "Nina", "Nikolic", "supervisor@banka.rs");
            ActuaryInfo supervisorInfo = createSupervisorInfo(700L, supervisorEmployee);

            UpdateActuaryLimitDto dto = new UpdateActuaryLimitDto();
            dto.setDailyLimit(new BigDecimal("100"));

            when(actuaryInfoRepository.findByEmployee_Email("supervisor@banka.rs"))
                    .thenReturn(Optional.of(supervisorInfo));

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> actuaryService.updateAgentLimit(77L, dto));

            assertTrue(ex.getMessage().contains("Cannot change own actuary info"));
        }

        @Test
        @DisplayName("baca izuzetak kada ciljani zaposleni nije aktuar")
        void throwsWhenTargetDoesNotExist() {
            setAuthenticatedUser("supervisor@banka.rs");

            Employee supervisorEmployee = createEmployee(1L, "Nina", "Nikolic", "supervisor@banka.rs");
            ActuaryInfo supervisorInfo = createSupervisorInfo(100L, supervisorEmployee);

            UpdateActuaryLimitDto dto = new UpdateActuaryLimitDto();
            dto.setDailyLimit(new BigDecimal("100000"));

            when(actuaryInfoRepository.findByEmployee_Email("supervisor@banka.rs"))
                    .thenReturn(Optional.of(supervisorInfo));
            when(actuaryInfoRepository.findByEmployeeId(5L)).thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> actuaryService.updateAgentLimit(5L, dto));

            assertTrue(ex.getMessage().contains("isn't an actuary"));
        }

        @Test
        @DisplayName("baca izuzetak kada cilj nije agent")
        void throwsWhenTargetIsNotAgent() {
            setAuthenticatedUser("supervisor@banka.rs");

            Employee supervisorEmployee = createEmployee(1L, "Nina", "Nikolic", "supervisor@banka.rs");
            Employee secondSupervisorEmployee = createEmployee(2L, "Sara", "Savic", "sara@banka.rs");

            ActuaryInfo supervisorInfo = createSupervisorInfo(100L, supervisorEmployee);
            ActuaryInfo secondSupervisorInfo = createSupervisorInfo(200L, secondSupervisorEmployee);

            UpdateActuaryLimitDto dto = new UpdateActuaryLimitDto();
            dto.setDailyLimit(new BigDecimal("90000"));

            when(actuaryInfoRepository.findByEmployee_Email("supervisor@banka.rs"))
                    .thenReturn(Optional.of(supervisorInfo));
            when(actuaryInfoRepository.findByEmployeeId(2L)).thenReturn(Optional.of(secondSupervisorInfo));

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> actuaryService.updateAgentLimit(2L, dto));

            assertTrue(ex.getMessage().contains("only be updated for agents"));
        }
    }
}