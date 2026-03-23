package rs.raf.banka2_bek.actuary.mapper;

import rs.raf.banka2_bek.actuary.dto.ActuaryInfoDto;
import rs.raf.banka2_bek.actuary.model.ActuaryInfo;

/**
 * Centralizovano mapiranje ActuaryInfo <-> ActuaryInfoDto.
 */
public final class ActuaryMapper {

    private ActuaryMapper() {}

    public static ActuaryInfoDto toDto(ActuaryInfo info) {
        if (info == null) return null;

        ActuaryInfoDto dto = new ActuaryInfoDto();
        dto.setId(info.getId());
        dto.setActuaryType(info.getActuaryType() != null ? info.getActuaryType().name() : null);
        dto.setDailyLimit(info.getDailyLimit());
        dto.setUsedLimit(info.getUsedLimit());
        dto.setNeedApproval(info.isNeedApproval());

        if (info.getEmployee() != null) {
            dto.setEmployeeId(info.getEmployee().getId());
            dto.setEmployeeName(info.getEmployee().getFirstName() + " " + info.getEmployee().getLastName());
            dto.setEmployeeEmail(info.getEmployee().getEmail());
            dto.setEmployeePosition(info.getEmployee().getPosition());
        }

        return dto;
    }
}
