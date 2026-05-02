package rs.raf.banka2_bek.assistant.wizard.dto;

import rs.raf.banka2_bek.assistant.wizard.model.SlotOption;

import java.util.List;
import java.util.UUID;

/**
 * Payload of the {@code agent_choice} SSE event sent to FE.
 *
 * FE renders this as a choice card in the chat (buttons or input field).
 *
 * @param wizardId        active wizard session UUID
 * @param toolName        which tool the wizard is collecting params for
 * @param title           friendly title (e.g. "Novo placanje")
 * @param slotName        which slot is being asked (matches WriteToolHandler arg name)
 * @param prompt          the question text shown to user
 * @param type            CHOICE / TEXT / NUMBER / CONFIRM
 * @param options         for CHOICE — list of buttons
 * @param stepIndex       1-based position of current slot
 * @param totalSteps      total slot count
 * @param previousSelections summary of slots already filled (FE shows in dimmed list)
 * @param errorMessage    optional error from previous selection (re-prompt case)
 */
public record SlotPromptDto(
        UUID wizardId,
        String toolName,
        String title,
        String slotName,
        String prompt,
        String type,
        List<SlotOption> options,
        int stepIndex,
        int totalSteps,
        List<PreviousSelection> previousSelections,
        String errorMessage
) {
    public record PreviousSelection(String slotName, String label) {}
}
