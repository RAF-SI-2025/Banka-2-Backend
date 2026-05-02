package rs.raf.banka2_bek.assistant.wizard.model;

import rs.raf.banka2_bek.auth.util.UserContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Definition of one slot (parameter) within a wizard.
 *
 * <p>Wizard execution model:</p>
 * <ol>
 *   <li>BE iterates wizard's slot list in order</li>
 *   <li>For each slot, calls {@code prefill(filled, user)} — if returns non-null, slot is auto-filled and skipped</li>
 *   <li>Otherwise, calls {@code optionsResolver(user, filled)} (CHOICE) or sends prompt directly (TEXT/NUMBER/CONFIRM)</li>
 *   <li>FE shows agent_choice event, user picks → BE validates via {@code validator}</li>
 *   <li>On valid → next slot. On invalid → re-emit prompt with error</li>
 *   <li>When all slots filled → BE builds tool args via {@code argsBuilder} and dispatches AgentAction</li>
 * </ol>
 *
 * @param name slot key (matches WriteToolHandler argument name where possible)
 * @param prompt human-readable question shown to user
 * @param type CHOICE / TEXT / NUMBER / CONFIRM
 * @param optionsResolver for CHOICE — produces list of options based on user + previously filled slots
 * @param prefill if returns non-null, slot is auto-filled (preskoci pitanje korisniku) — koristi se kad
 *                je iz user message vec ekstraktovan parametar (npr. "Plati MILICI 100" → recipient + amount)
 * @param validator validates user input/selection. Returns error string if invalid, null if OK.
 *                  For CHOICE, validation is automatic (mora biti u options list).
 * @param transformer converts raw user input (string) into typed slot value (BigDecimal, Long, ...)
 *                    Default: pass-through string. CHOICE always uses {@link SlotOption#value()} directly.
 */
public record SlotDefinition(
        String name,
        String prompt,
        SlotType type,
        BiFunction<UserContext, Map<String, Object>, List<SlotOption>> optionsResolver,
        BiFunction<UserContext, Map<String, Object>, Optional<Object>> prefill,
        Function<Object, String> validator,
        Function<String, Object> transformer
) {
    public static SlotDefinitionBuilder builder(String name) {
        return new SlotDefinitionBuilder(name);
    }

    public static class SlotDefinitionBuilder {
        private final String name;
        private String prompt = "";
        private SlotType type = SlotType.TEXT;
        private BiFunction<UserContext, Map<String, Object>, List<SlotOption>> optionsResolver = (u, f) -> List.of();
        private BiFunction<UserContext, Map<String, Object>, Optional<Object>> prefill = (u, f) -> Optional.empty();
        private Function<Object, String> validator = v -> null;
        private Function<String, Object> transformer = s -> s;

        SlotDefinitionBuilder(String name) {
            this.name = name;
        }

        public SlotDefinitionBuilder prompt(String p) { this.prompt = p; return this; }
        public SlotDefinitionBuilder type(SlotType t) { this.type = t; return this; }
        public SlotDefinitionBuilder options(BiFunction<UserContext, Map<String, Object>, List<SlotOption>> r) {
            this.optionsResolver = r; return this;
        }
        public SlotDefinitionBuilder prefill(BiFunction<UserContext, Map<String, Object>, Optional<Object>> p) {
            this.prefill = p; return this;
        }
        public SlotDefinitionBuilder validate(Function<Object, String> v) { this.validator = v; return this; }
        public SlotDefinitionBuilder transform(Function<String, Object> t) { this.transformer = t; return this; }

        public SlotDefinition build() {
            return new SlotDefinition(name, prompt, type, optionsResolver, prefill, validator, transformer);
        }
    }
}
