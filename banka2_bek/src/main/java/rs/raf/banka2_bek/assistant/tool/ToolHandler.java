package rs.raf.banka2_bek.assistant.tool;

import rs.raf.banka2_bek.auth.util.UserContext;

import java.util.Map;

/**
 * Ugovor za pojedinacni Arbitro alat.
 * Implementacije idu u assistant/tool/handlers/.
 */
public interface ToolHandler {

    /** Mora biti jedinstveno; OpenAI tool name format (snake_case). */
    String name();

    /** OpenAI tool spec za system prompt registry. */
    ToolDefinition definition();

    /**
     * Izvrsi tool poziv. Args dolazi iz LLM-ovog tool_call.function.arguments
     * dekodirano u Map. UserContext se prosledjuje za authorization na
     * read-only handlers (BalanceSummary itd).
     *
     * Vraca Map koji ce biti JSON-serijalizovan u tool_result poruci.
     */
    Map<String, Object> execute(Map<String, Object> args, UserContext user);

    /**
     * Vraca true samo ako je alat omogucen kroz properties (npr. internal
     * handlers su iza assistant.tools.internal.* flag-ova).
     */
    default boolean isEnabled() { return true; }
}
