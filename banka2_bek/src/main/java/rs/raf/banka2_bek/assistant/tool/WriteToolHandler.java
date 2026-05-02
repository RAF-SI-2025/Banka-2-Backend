package rs.raf.banka2_bek.assistant.tool;

import rs.raf.banka2_bek.auth.util.UserContext;

import java.util.List;
import java.util.Map;

/**
 * Specijalizovan handler za AGENTIC mode write akcije (Phase 4 v3.5).
 *
 * Razlika od plain {@link ToolHandler}:
 *  - {@link #execute(Map, UserContext)} se NE poziva direktno iz AssistantService-a
 *    kad model emituje tool_call. Umesto toga, AssistantService kreira
 *    {@code AgentAction} sa status=PENDING + emit-uje SSE preview event.
 *  - Posle user confirm-a, AgentActionGateway poziva {@link #execute} sa
 *    finalnim (mozda edit-ovanim) parametrima.
 *  - {@link #buildPreview(Map, UserContext)} mora vratiti human-readable
 *    summary + structured display fields PRE bilo kakvog state mutacije.
 *  - {@link #requiresOtp()} kontrolise da li FE otvara VerificationModal
 *    posle Potvrdi dugmeta.
 *
 * Sve write handler-e su Spring bean-ovi anotirani sa {@code @Component}, pa
 * ih ToolRegistry automatski pokupi (uvek je {@link ToolHandler} podtip).
 */
public interface WriteToolHandler extends ToolHandler {

    /**
     * Vraca preview (summary + display fields + warnings) BEZ menjanja state-a.
     * Resolves human-readable nazive (broj racuna → ime primaoca, listingId →
     * ticker simbol, fundId → naziv fonda).
     *
     * @param args parametri koje je LLM proslijedio u tool_call
     * @param user trenutni korisnik
     * @return preview objekat za AgentAction.summary + parametersJson
     * @throws IllegalArgumentException za neispravne parametre — agent dobija
     *         tool_result sa error messageom umesto preview-a
     */
    PreviewResult buildPreview(Map<String, Object> args, UserContext user);

    /**
     * Marker — write tool, AssistantService treba da preusmeri na
     * AgentActionGateway umesto direkt dispatch-a.
     */
    default boolean isWrite() { return true; }

    /** True znaci da confirm endpoint zahteva otpCode polje. */
    default boolean requiresOtp() { return false; }

    /**
     * Lista permisija koje su DOZVOLJENE za izvrsenje. Agentic mode + tool
     * registracija proverava role kompozit (KLIJENT / AGENT / SUPERVIZOR /
     * ADMIN) — vrate prazna lista znaci sve role.
     */
    default List<String> allowedRoles() { return List.of(); }

    /**
     * Backward-compat: write handler ne radi direktan execute kroz read flow.
     * Vracamo placeholder rezultat — agentic gateway ce zvati {@link #executeFinal}.
     */
    @Override
    default Map<String, Object> execute(Map<String, Object> args, UserContext user) {
        throw new UnsupportedOperationException(
                "WriteToolHandler ne podrzava direktan execute van agentic gateway-a. " +
                "Tool name: " + name());
    }

    /**
     * Pravi izvrsni metod — poziva ga AgentActionGateway POSLE user confirm-a.
     * Ovde je dozvoljeno menjanje state-a (POST/PATCH/DELETE preko service layer-a).
     *
     * @param args finalni parametri (posle inline edit-a, ako je bilo)
     * @param user trenutni korisnik (re-checked za ownership)
     * @param otpCode OTP code ako {@link #requiresOtp()} == true; ignorisan inace
     * @return rezultat akcije za audit log + asistent tool_result poruku
     */
    Map<String, Object> executeFinal(Map<String, Object> args, UserContext user, String otpCode);

    /**
     * Strukturisan preview (human-readable summary + display polja + opciono warnings).
     */
    record PreviewResult(String summary, Map<String, Object> displayFields, List<String> warnings) {
        public PreviewResult(String summary, Map<String, Object> displayFields) {
            this(summary, displayFields, List.of());
        }
    }
}
