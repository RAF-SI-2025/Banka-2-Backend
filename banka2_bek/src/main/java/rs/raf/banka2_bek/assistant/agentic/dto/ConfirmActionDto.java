package rs.raf.banka2_bek.assistant.agentic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Body za POST /assistant/actions/{uuid}/confirm.
 * Inline edit na FE-u moze poslati izmenjene parametre — BE ih validira pre
 * izvrsenja (re-resolves preview).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfirmActionDto {

    /** OTP kod ako akcija zahteva (requiresOtp=true). */
    private String otpCode;

    /** Edit-ovani parametri (FE inline edit) — null za nemenjano. */
    private Map<String, Object> editedParameters;
}
