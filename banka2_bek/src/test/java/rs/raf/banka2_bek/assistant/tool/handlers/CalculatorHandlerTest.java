package rs.raf.banka2_bek.assistant.tool.handlers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import rs.raf.banka2_bek.assistant.config.AssistantProperties;
import rs.raf.banka2_bek.auth.util.UserContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CalculatorHandlerTest {

    private CalculatorHandler handler;
    private UserContext user = new UserContext(1L, "CLIENT");

    @BeforeEach
    void setUp() {
        AssistantProperties props = new AssistantProperties();
        props.getTools().getCalculator().setEnabled(true);
        handler = new CalculatorHandler(props);
    }

    @Test
    void evaluatesSimpleArithmetic() {
        Map<String, Object> result = handler.execute(Map.of("expression", "100 * 0.14"), user);
        // SpEL koristi double aritmetiku; IEEE 754 daje 14.000000000000002 — koristimo isCloseTo
        assertThat(((Number) result.get("result")).doubleValue()).isCloseTo(14.0, within(1e-9));
        assertThat(result).containsEntry("expression", "100 * 0.14");
    }

    @Test
    void handlesParenthesesAndFloatDivision() {
        // SpEL int aritmetika → 125/2 = 62. Korisnik mora upisati 2.0 za float deljenje.
        Map<String, Object> result = handler.execute(Map.of("expression", "(50 + 75) / 2.0"), user);
        assertThat(((Number) result.get("result")).doubleValue()).isCloseTo(62.5, within(1e-9));
    }

    @Test
    void integerDivisionBehavesLikeJavaLong() {
        // Ovo je dokumentovano ponasanje (SpEL koristi Java long za int operande)
        Map<String, Object> result = handler.execute(Map.of("expression", "(50 + 75) / 2"), user);
        assertThat(result.get("result")).isEqualTo(62);
    }

    @Test
    void rejectsExpressionWithLetters() {
        Map<String, Object> result = handler.execute(Map.of("expression", "T(123)"), user);
        assertThat(result).containsKey("error");
        // Sanity guard regex blokira slova/zagrade na pocetku
        assertThat(String.valueOf(result.get("error"))).contains("Dozvoljeni");
    }

    @Test
    void rejectsEmptyExpression() {
        Map<String, Object> result = handler.execute(Map.of("expression", ""), user);
        assertThat(result).containsKey("error");
    }

    @Test
    void exposesNameAndDefinition() {
        assertThat(handler.name()).isEqualTo("calculator");
        assertThat(handler.definition().getName()).isEqualTo("calculator");
        assertThat(handler.definition().getDescription()).contains("matematicki");
    }
}
