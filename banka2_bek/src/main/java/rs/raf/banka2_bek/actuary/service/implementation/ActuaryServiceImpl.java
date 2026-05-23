package rs.raf.banka2_bek.actuary.service.implementation;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.actuary.dto.ActuaryInfoDto;
import rs.raf.banka2_bek.actuary.dto.UpdateActuaryLimitDto;
import rs.raf.banka2_bek.actuary.mapper.ActuaryMapper;
import rs.raf.banka2_bek.actuary.model.ActuaryInfo;
import rs.raf.banka2_bek.actuary.model.ActuaryType;
import rs.raf.banka2_bek.actuary.repository.ActuaryInfoRepository;
import rs.raf.banka2_bek.actuary.service.ActuaryService;
import rs.raf.banka2_bek.audit.model.AuditActionType;
import rs.raf.banka2_bek.audit.service.AuditLogService;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/*
 * TODO [B7 - Audit log | Nosilac: Stasa Dragovic]
 *
 * Pri promeni limita agentu (updateLimit) i pri resetovanju iskoriscenog
 * limita (resetUsedLimit / ActuaryLimitResetScheduler) evidentirati akciju
 * u audit servis:
 *   - ko je pokrenuo akciju (korisnicko ime / email inicijatora iz SecurityContext-a)
 *   - kada se desilo (LocalDateTime.now())
 *   - stara vrednost limita / iskoriscenog limita pre izmene
 *   - nova vrednost limita / iskoriscenog limita posle izmene
 *
 * Primer poziva (pseudokod):
 *   auditService.log(AuditEvent.ACTUARY_LIMIT_CHANGED, actuaryId, oldLimit, newLimit, initiator);
 *   auditService.log(AuditEvent.ACTUARY_LIMIT_RESET, actuaryId, oldUsed, 0, "SCHEDULER");
 */
@Service
@RequiredArgsConstructor
public class ActuaryServiceImpl implements ActuaryService {

    private final ActuaryInfoRepository actuaryInfoRepository;
    private final AuditLogService auditLogService;
    private final EmployeeRepository employeeRepository;

    @Override
    public List<ActuaryInfoDto> getAgents(String email, String firstName, String lastName, String position) {
        List<ActuaryInfo> agents = actuaryInfoRepository.findByTypeAndFilters(
                ActuaryType.AGENT, email, firstName, lastName, position
        );
        return agents.stream()
                .map(ActuaryMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public ActuaryInfoDto getActuaryInfo(Long employeeId) {
        ActuaryInfo info = actuaryInfoRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Actuary info for employee with ID " + employeeId + " not found."
                ));

        return ActuaryMapper.toDto(info);
    }
  

    @Override
    @Transactional
    public ActuaryInfoDto updateAgentLimit(Long employeeId, UpdateActuaryLimitDto dto) {
      
        String currentUsername = getAuthenticatedUsername();
        ActuaryInfo currentUserInfo = actuaryInfoRepository.findByEmployee_Email(currentUsername)
                .orElseThrow(() -> new IllegalStateException("Authenticated user is not an actuary."));

        if(currentUserInfo.getActuaryType() != ActuaryType.SUPERVISOR) {
           throw new IllegalStateException("Only supervisors can update agent limits.");
        }

        if(currentUserInfo.getEmployee().getId().equals(employeeId)) {
            throw new IllegalStateException("Cannot change own actuary info.");
        }

        ActuaryInfo targetUserInfo = actuaryInfoRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("User does not exist or isn't an actuary."));

        if(targetUserInfo.getActuaryType() != ActuaryType.AGENT) {
            throw new RuntimeException("Limits can only be updated for agents.");
        }

        BigDecimal oldLimit = targetUserInfo.getDailyLimit();
        Boolean oldNeedApproval = targetUserInfo.isNeedApproval();

        targetUserInfo.setDailyLimit(dto.getDailyLimit() != null ? dto.getDailyLimit() : targetUserInfo.getDailyLimit());
        targetUserInfo.setNeedApproval(dto.getNeedApproval() != null ? dto.getNeedApproval() : targetUserInfo.isNeedApproval());

        actuaryInfoRepository.save(targetUserInfo);

        Long actorId = currentUserInfo.getEmployee().getId();
        auditLogService.record(
                actorId, "EMPLOYEE", AuditActionType.LIMIT_CHANGED,
                "Agent limit updated for employee " + employeeId,
                "ACTUARY", employeeId,
                "dailyLimit=" + oldLimit + ",needApproval=" + oldNeedApproval,
                "dailyLimit=" + targetUserInfo.getDailyLimit() + ",needApproval=" + targetUserInfo.isNeedApproval()
        );

        return ActuaryMapper.toDto(targetUserInfo);
    }
  

    @Override
    @Transactional
    public ActuaryInfoDto resetUsedLimit(Long employeeId) {
      
        ActuaryInfo actuary = actuaryInfoRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new EntityNotFoundException("Actuary record not found for employee ID: " + employeeId));


        if (actuary.getActuaryType() != ActuaryType.AGENT) {
            throw new IllegalStateException("Reset is only allowed for Agents. Supervisors do not have limits.");
        }

        BigDecimal oldUsed = actuary.getUsedLimit();
        actuary.setUsedLimit(BigDecimal.ZERO);
        ActuaryInfo updatedActuary = actuaryInfoRepository.save(actuary);

        Long actorId;
        String actorType;
        try {
            String username = getAuthenticatedUsername();
            var empOpt = employeeRepository.findByEmail(username);
            if (empOpt.isPresent()) {
                actorId = empOpt.get().getId();
                actorType = "EMPLOYEE";
            } else {
                actorId = 0L;
                actorType = "SCHEDULER";
            }
        } catch (IllegalStateException e) {
            actorId = 0L;
            actorType = "SCHEDULER";
        }

        auditLogService.record(
                actorId, actorType, AuditActionType.USED_LIMIT_RESET,
                "Used limit reset for agent " + employeeId,
                "ACTUARY", employeeId,
                String.valueOf(oldUsed),
                "0"
        );

        return ActuaryMapper.toDto(updatedActuary);
    }

  
    @Override
    @Transactional
    public void resetAllUsedLimits() {
        List<ActuaryInfo> agents = actuaryInfoRepository.findAllByActuaryType(ActuaryType.AGENT);

        for (ActuaryInfo agent : agents) {
            resetUsedLimit(agent.getEmployee().getId());
        }
    }

    private String getAuthenticatedUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("Authenticated user is required.");
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }

        throw new IllegalStateException("Authenticated user is required.");
    }

}

