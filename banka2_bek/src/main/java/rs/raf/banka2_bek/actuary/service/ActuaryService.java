package rs.raf.banka2_bek.actuary.service;

import rs.raf.banka2_bek.actuary.dto.ActuaryInfoDto;
import rs.raf.banka2_bek.actuary.dto.UpdateActuaryLimitDto;

import java.util.List;

public interface ActuaryService {

    // TODO: Implementirati sve metode u ActuaryServiceImpl

    /**
     * Vraca listu svih agenata (za supervizor portal).
     * Filtriranje po email-u, imenu, prezimenu i poziciji.
     */
    List<ActuaryInfoDto> getAgents(String email, String firstName, String lastName, String position);

    /**
     * Vraca aktuarske podatke za odredjenog zaposlenog.
     */
    ActuaryInfoDto getActuaryInfo(Long employeeId);

    /**
     * Supervizor menja limit i/ili needApproval za agenta.
     * Validacija: samo supervizor moze menjati, samo za agente.
     */
    ActuaryInfoDto updateAgentLimit(Long employeeId, UpdateActuaryLimitDto dto);

    /**
     * Resetuje usedLimit na 0 za odredjenog agenta.
     * Moze biti pozvano:
     * 1. Rucno od strane supervizora (ovaj endpoint)
     * 2. Automatski na kraju svakog dana u 23:59 (@Scheduled)
     */
    ActuaryInfoDto resetUsedLimit(Long employeeId);

    /**
     * Automatski reset svih agenata na kraju dana.
     * Ovo se poziva iz @Scheduled metode u 23:59.
     */
    void resetAllUsedLimits();
}
