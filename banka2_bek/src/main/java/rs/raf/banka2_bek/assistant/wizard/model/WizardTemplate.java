package rs.raf.banka2_bek.assistant.wizard.model;

import rs.raf.banka2_bek.auth.util.UserContext;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Template that drives a complete wizard for one write tool. Defines:
 * <ul>
 *   <li>Tool name to dispatch when wizard completes</li>
 *   <li>Title shown in UI</li>
 *   <li>Ordered list of slots</li>
 *   <li>Allowed user roles (CLIENT / EMPLOYEE)</li>
 *   <li>Optional argsBuilder hook — translates filled slots into the args map
 *       expected by WriteToolHandler.buildPreview/executeFinal. By default,
 *       args = filledSlots map directly.</li>
 * </ul>
 */
public record WizardTemplate(
        String toolName,
        String title,
        List<SlotDefinition> slots,
        List<String> allowedRoles,
        BiFunction<UserContext, Map<String, Object>, Map<String, Object>> argsBuilder
) {
    public static WizardTemplate simple(String toolName, String title, List<SlotDefinition> slots,
                                         List<String> allowedRoles) {
        return new WizardTemplate(toolName, title, slots, allowedRoles, (u, filled) -> filled);
    }
}
