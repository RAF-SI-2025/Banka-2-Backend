package rs.raf.banka2_bek.assistant.tool.handlers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelParserConfiguration;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.assistant.config.AssistantProperties;
import rs.raf.banka2_bek.assistant.tool.ToolDefinition;
import rs.raf.banka2_bek.assistant.tool.ToolHandler;
import rs.raf.banka2_bek.auth.util.UserContext;

import java.util.Map;

/**
 * calculator(expression) -> {result, expression}
 *
 * Koristi Spring SpEL u SANDBOX modu — {@link SimpleEvaluationContext} sa read-only
 * accessor-ima blokira reflection, file IO, network. Whitelist: aritmeticki
 * operatori (+ - * / %), parenteze, decimalni brojevi, basic math.
 *
 * Primeri:
 *   "100 * 0.14"         -> 14.0
 *   "100 + 100 * 0.20"   -> 120.0
 *   "(50 + 50) / 2"      -> 50.0
 *
 * Reference: plan §10.6.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CalculatorHandler implements ToolHandler {

    private static final SpelExpressionParser PARSER =
            new SpelExpressionParser(new SpelParserConfiguration(false, false));

    private final AssistantProperties properties;

    @Override
    public boolean isEnabled() {
        return properties.getTools().getCalculator().isEnabled();
    }

    @Override
    public String name() {
        return "calculator";
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(name())
                .description("Izracunava matematicki izraz (aritmetika + parenteze). " +
                        "Pozovi za bilo kakvo izracunavanje sa konkretnim brojevima " +
                        "(provizije, kamate, konverzije). NE pozivaj za teorijska " +
                        "objasnjenja formula.")
                .param(new ToolDefinition.Param("expression", "string",
                        "Matematicki izraz, npr. '100 * 0.14', '(50 + 75) / 2'.",
                        true, null, null))
                .build();
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> args, UserContext user) {
        String expression = String.valueOf(args.getOrDefault("expression", "")).trim();
        if (expression.isBlank()) {
            return Map.of("error", "expression je obavezan");
        }
        // Sanity check — odbaci sve sto nije aritmetika.
        if (!expression.matches("^[\\d+\\-*/().,%\\s]+$")) {
            return Map.of("error", "Dozvoljeni su samo brojevi, +, -, *, /, %, (, )",
                          "expression", expression);
        }
        try {
            EvaluationContext ctx = SimpleEvaluationContext.forReadOnlyDataBinding().build();
            Expression parsed = PARSER.parseExpression(expression);
            Object result = parsed.getValue(ctx);
            return Map.of(
                    "expression", expression,
                    "result", result == null ? "null" : result
            );
        } catch (SpelEvaluationException e) {
            log.warn("Calculator eval failed for '{}': {}", expression, e.getMessage());
            return Map.of("error", "Nije moguce izracunati izraz: " + e.getMessage(),
                          "expression", expression);
        }
    }
}
