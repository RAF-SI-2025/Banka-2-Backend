package rs.raf.banka2_bek.actuary.service;

import rs.raf.banka2_bek.actuary.dto.ActuaryInfoDto;
import rs.raf.banka2_bek.actuary.model.ActuaryInfo;
import rs.raf.banka2_bek.actuary.model.ActuaryType;
import rs.raf.banka2_bek.actuary.repository.ActuaryInfoRepository;
import rs.raf.banka2_bek.actuary.service.implementation.ActuaryServiceImpl;
import rs.raf.banka2_bek.employee.model.Employee;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActuaryServiceImplTest {

    @Mock
    private ActuaryInfoRepository actuaryInfoRepository;

    @InjectMocks
    private ActuaryServiceImpl actuaryService;

    // ──────────────────────────────────────────────────────────────────
    //  Helperi za kreiranje test podataka
    // ──────────────────────────────────────────────────────────────────

    private Employee createEmployee(Long id, String firstName, String lastName, String email) {
        Employee emp = Employee.builder()
                .id(id).firstName(firstName).lastName(lastName).build();
        emp.setEmail(email);
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

    // ══════════════════════════════════════════════════════════════════
    //  getAgents
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getAgents")
    class GetAgents {

        @Test
        @DisplayName("vraca sve agente bez filtera")
        void returnsAllAgents() {
            Employee marko = createEmployee(10L, "Marko", "Markovic",
                    "marko.markovic@banka.rs");
            Employee jelena = createEmployee(11L, "Jelena", "Jovanovic",
                    "jelena.jovanovic@banka.rs");

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
            assertEquals("marko.markovic@banka.rs", result.get(0).getEmployeeEmail());
            assertEquals("AGENT", result.get(0).getActuaryType());
            assertEquals(new BigDecimal("100000"), result.get(0).getDailyLimit());
            assertEquals(new BigDecimal("15000"), result.get(0).getUsedLimit());
            assertFalse(result.get(0).isNeedApproval());

            assertEquals("Jelena Jovanovic", result.get(1).getEmployeeName());
            assertTrue(result.get(1).isNeedApproval());
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

        @Test
        @DisplayName("prosledjuje email filter u repository")
        void filtersByEmail() {
            Employee marko = createEmployee(10L, "Marko", "Markovic",
                    "marko.markovic@banka.rs");

            ActuaryInfo agent1 = createAgentInfo(1L, marko,
                    new BigDecimal("100000"), BigDecimal.ZERO, false);

            when(actuaryInfoRepository.findByTypeAndFilters(
                    ActuaryType.AGENT, "marko", null, null, null))
                    .thenReturn(List.of(agent1));

            List<ActuaryInfoDto> result = actuaryService.getAgents("marko", null, null, null);

            assertEquals(1, result.size());
            assertEquals("marko.markovic@banka.rs", result.get(0).getEmployeeEmail());

            verify(actuaryInfoRepository).findByTypeAndFilters(
                    ActuaryType.AGENT, "marko", null, null, null);
        }

        @Test
        @DisplayName("prosledjuje firstName filter u repository")
        void filtersByFirstName() {
            Employee jelena = createEmployee(11L, "Jelena", "Jovanovic",
                    "jelena.jovanovic@banka.rs");

            ActuaryInfo agent = createAgentInfo(2L, jelena,
                    new BigDecimal("50000"), BigDecimal.ZERO, false);

            when(actuaryInfoRepository.findByTypeAndFilters(
                    ActuaryType.AGENT, null, "Jelena", null, null))
                    .thenReturn(List.of(agent));

            List<ActuaryInfoDto> result = actuaryService.getAgents(null, "Jelena", null, null);

            assertEquals(1, result.size());
            assertEquals("Jelena Jovanovic", result.get(0).getEmployeeName());
        }

        @Test
        @DisplayName("prosledjuje lastName filter u repository")
        void filtersByLastName() {
            Employee marko = createEmployee(10L, "Marko", "Markovic",
                    "marko.markovic@banka.rs");

            ActuaryInfo agent = createAgentInfo(1L, marko,
                    new BigDecimal("100000"), BigDecimal.ZERO, false);

            when(actuaryInfoRepository.findByTypeAndFilters(
                    ActuaryType.AGENT, null, null, "Markovic", null))
                    .thenReturn(List.of(agent));

            List<ActuaryInfoDto> result = actuaryService.getAgents(null, null, "Markovic", null);

            assertEquals(1, result.size());
            assertEquals("Marko Markovic", result.get(0).getEmployeeName());
        }

        @Test
        @DisplayName("prosledjuje position filter u repository")
        void filtersByPosition() {
            Employee marko = createEmployee(10L, "Marko", "Markovic",
                    "marko.markovic@banka.rs");

            ActuaryInfo agent = createAgentInfo(1L, marko,
                    new BigDecimal("100000"), BigDecimal.ZERO, false);

            when(actuaryInfoRepository.findByTypeAndFilters(
                    ActuaryType.AGENT, null, null, null, "Menadzer"))
                    .thenReturn(List.of(agent));

            List<ActuaryInfoDto> result = actuaryService.getAgents(null, null, null, "Menadzer");

            assertEquals(1, result.size());

            verify(actuaryInfoRepository).findByTypeAndFilters(
                    ActuaryType.AGENT, null, null, null, "Menadzer");
        }

        @Test
        @DisplayName("prosledjuje kombinaciju filtera u repository")
        void multipleFilters() {
            Employee marko = createEmployee(10L, "Marko", "Markovic",
                    "marko.markovic@banka.rs");
            Employee ana = createEmployee(12L, "Ana", "Markovic",
                    "ana.markovic@banka.rs");

            ActuaryInfo agent1 = createAgentInfo(1L, marko,
                    new BigDecimal("100000"), BigDecimal.ZERO, false);
            ActuaryInfo agent3 = createAgentInfo(3L, ana,
                    new BigDecimal("75000"), BigDecimal.ZERO, false);

            when(actuaryInfoRepository.findByTypeAndFilters(
                    ActuaryType.AGENT, null, null, "Markovic", "Menadzer"))
                    .thenReturn(List.of(agent1, agent3));

            List<ActuaryInfoDto> result = actuaryService.getAgents(
                    null, null, "Markovic", "Menadzer");

            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("filter bez pogodaka vraca praznu listu")
        void noMatchReturnsEmpty() {
            when(actuaryInfoRepository.findByTypeAndFilters(
                    ActuaryType.AGENT, "nepostojeci@email.com", null, null, null))
                    .thenReturn(Collections.emptyList());

            List<ActuaryInfoDto> result = actuaryService.getAgents(
                    "nepostojeci@email.com", null, null, null);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("uvek poziva findByTypeAndFilters sa AGENT tipom")
        void alwaysQueriesAgentType() {
            when(actuaryInfoRepository.findByTypeAndFilters(
                    eq(ActuaryType.AGENT), any(), any(), any(), any()))
                    .thenReturn(Collections.emptyList());

            actuaryService.getAgents(null, null, null, null);

            verify(actuaryInfoRepository).findByTypeAndFilters(
                    eq(ActuaryType.AGENT), any(), any(), any(), any());
            verify(actuaryInfoRepository, never()).findAll();
        }

        @Test
        @DisplayName("mapira rezultate u ActuaryInfoDto korektno")
        void mapsToDto() {
            Employee marko = createEmployee(10L, "Marko", "Markovic",
                    "marko.markovic@banka.rs");

            ActuaryInfo agent = createAgentInfo(1L, marko,
                    new BigDecimal("100000"), new BigDecimal("25000"), true);

            when(actuaryInfoRepository.findByTypeAndFilters(
                    ActuaryType.AGENT, null, null, null, null))
                    .thenReturn(List.of(agent));

            List<ActuaryInfoDto> result = actuaryService.getAgents(null, null, null, null);

            assertEquals(1, result.size());
            ActuaryInfoDto dto = result.get(0);
            assertEquals(1L, dto.getId());
            assertEquals(10L, dto.getEmployeeId());
            assertEquals("Marko Markovic", dto.getEmployeeName());
            assertEquals("marko.markovic@banka.rs", dto.getEmployeeEmail());
            assertEquals("AGENT", dto.getActuaryType());
            assertEquals(new BigDecimal("100000"), dto.getDailyLimit());
            assertEquals(new BigDecimal("25000"), dto.getUsedLimit());
            assertTrue(dto.isNeedApproval());
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  getActuaryInfo
    // ══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getActuaryInfo")
    class GetActuaryInfo {

        @Test
        @DisplayName("vraca aktuarske podatke za postojeceg agenta")
        void returnsAgentInfo() {
            Employee marko = createEmployee(10L, "Marko", "Markovic",
                    "marko.markovic@banka.rs");

            ActuaryInfo agentInfo = createAgentInfo(1L, marko,
                    new BigDecimal("100000"), new BigDecimal("15000"), false);

            when(actuaryInfoRepository.findByEmployeeId(10L))
                    .thenReturn(Optional.of(agentInfo));

            ActuaryInfoDto result = actuaryService.getActuaryInfo(10L);

            assertNotNull(result);
            assertEquals(1L, result.getId());
            assertEquals(10L, result.getEmployeeId());
            assertEquals("Marko Markovic", result.getEmployeeName());
            assertEquals("marko.markovic@banka.rs", result.getEmployeeEmail());
            assertEquals("AGENT", result.getActuaryType());
            assertEquals(new BigDecimal("100000"), result.getDailyLimit());
            assertEquals(new BigDecimal("15000"), result.getUsedLimit());
            assertFalse(result.isNeedApproval());
        }

        @Test
        @DisplayName("vraca aktuarske podatke za supervizora")
        void returnsSupervisorInfo() {
            Employee nina = createEmployee(20L, "Nina", "Nikolic",
                    "nina.nikolic@banka.rs");

            ActuaryInfo supervisorInfo = createSupervisorInfo(5L, nina);

            when(actuaryInfoRepository.findByEmployeeId(20L))
                    .thenReturn(Optional.of(supervisorInfo));

            ActuaryInfoDto result = actuaryService.getActuaryInfo(20L);

            assertNotNull(result);
            assertEquals(20L, result.getEmployeeId());
            assertEquals("Nina Nikolic", result.getEmployeeName());
            assertEquals("SUPERVISOR", result.getActuaryType());
            assertNull(result.getDailyLimit());
            assertNull(result.getUsedLimit());
            assertFalse(result.isNeedApproval());
        }

        @Test
        @DisplayName("baca izuzetak ako zapis ne postoji za dati employeeId")
        void notFound() {
            when(actuaryInfoRepository.findByEmployeeId(999L))
                    .thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> actuaryService.getActuaryInfo(999L));

            assertTrue(ex.getMessage().contains("999"));
        }
    }
}