package rs.raf.banka2_bek.assistant.wizard.dto;

import rs.raf.banka2_bek.assistant.agentic.dto.AgentActionPreviewDto;

/**
 * Response of POST /assistant/wizard/{id}/select.
 *
 * If wizard not yet complete, {@code nextSlot} is populated and FE shows next prompt.
 * If wizard complete, {@code actionPreview} is populated (BE has dispatched the AgentAction)
 * and FE opens preview confirmation modal.
 *
 * @param status "AWAITING_NEXT_SLOT" / "COMPLETED" / "INVALID" / "EXPIRED" / "REJECTED"
 * @param nextSlot next prompt (only if AWAITING_NEXT_SLOT)
 * @param actionPreview preview of dispatched action (only if COMPLETED)
 * @param errorMessage on INVALID — reason
 */
public record SlotSelectResponseDto(
        String status,
        SlotPromptDto nextSlot,
        AgentActionPreviewDto actionPreview,
        String errorMessage
) {
    public static SlotSelectResponseDto awaitingNext(SlotPromptDto next) {
        return new SlotSelectResponseDto("AWAITING_NEXT_SLOT", next, null, null);
    }
    public static SlotSelectResponseDto completed(AgentActionPreviewDto preview) {
        return new SlotSelectResponseDto("COMPLETED", null, preview, null);
    }
    public static SlotSelectResponseDto invalid(SlotPromptDto reprompt, String reason) {
        return new SlotSelectResponseDto("INVALID", reprompt, null, reason);
    }
    public static SlotSelectResponseDto expired() {
        return new SlotSelectResponseDto("EXPIRED", null, null,
                "Sesija je istekla (10 min). Pokrenite ponovo.");
    }
}
