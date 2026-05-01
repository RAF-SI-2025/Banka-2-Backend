package rs.raf.banka2_bek.assistant.wizard.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import rs.raf.banka2_bek.assistant.wizard.dto.SlotSelectRequestDto;
import rs.raf.banka2_bek.assistant.wizard.dto.SlotSelectResponseDto;
import rs.raf.banka2_bek.assistant.wizard.service.WizardService;
import rs.raf.banka2_bek.auth.util.UserResolver;

import java.util.UUID;

/**
 * REST endpoints for Phase 4.5 wizard flow.
 *
 * <ul>
 *   <li>POST /assistant/wizard/{wizardId}/select — apply user choice, advance</li>
 *   <li>POST /assistant/wizard/{wizardId}/cancel — abort wizard</li>
 * </ul>
 */
@RestController
@RequestMapping("/assistant/wizard")
@RequiredArgsConstructor
public class WizardController {

    private final WizardService wizardService;
    private final UserResolver userResolver;

    @PostMapping("/{wizardId}/select")
    public ResponseEntity<SlotSelectResponseDto> select(@PathVariable UUID wizardId,
                                                        @Valid @RequestBody SlotSelectRequestDto body) {
        var user = userResolver.resolveCurrent();
        SlotSelectResponseDto resp = wizardService.select(wizardId, body.getSlotName(), body.getValue(), user);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/{wizardId}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable UUID wizardId) {
        var user = userResolver.resolveCurrent();
        boolean cancelled = wizardService.cancel(wizardId, user);
        return cancelled ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
