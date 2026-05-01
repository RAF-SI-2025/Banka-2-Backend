package rs.raf.banka2_bek.assistant.wizard.service;

import org.junit.jupiter.api.Test;
import rs.raf.banka2_bek.assistant.wizard.model.SlotOption;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests za STATIC helpers u SlotResolvers — fuzzy matcher, normalize,
 * mask account. Instance metodi koji rade DB lookup pokriveni su integracionim
 * smoke testom (vidi CLAUDE.md "Plati Milici" smoke test).
 */
class SlotResolversTest {

    @Test
    void maskAccount_truncatesMiddle() {
        assertThat(SlotResolvers.maskAccount("222000112345678912")).isEqualTo("222...8912");
        assertThat(SlotResolvers.maskAccount("12345678")).isEqualTo("123...5678");
    }

    @Test
    void maskAccount_returnsAsIs_forShortInput() {
        assertThat(SlotResolvers.maskAccount("1234567")).isEqualTo("1234567");
        assertThat(SlotResolvers.maskAccount(null)).isNull();
    }

    @Test
    void normalizeForMatch_lowercasesAndStripsDiacritics() {
        assertThat(SlotResolvers.normalizeForMatch("Milica Nikolić")).isEqualTo("milica nikolic");
        assertThat(SlotResolvers.normalizeForMatch("ČAĆA Ćira")).isEqualTo("caca cira");
        assertThat(SlotResolvers.normalizeForMatch("  trim me  ")).isEqualTo("trim me");
    }

    @Test
    void normalizeForMatch_returnsNull_forNull() {
        assertThat(SlotResolvers.normalizeForMatch(null)).isNull();
    }

    @Test
    void fuzzyMatch_findsByPrefixToken() {
        List<SlotOption> options = List.of(
                new SlotOption("v1", "Milica Nikolić", null),
                new SlotOption("v2", "Stefan Jovanović", null),
                new SlotOption("v3", "Lazar Ilić", null)
        );

        SlotOption match = SlotResolvers.fuzzyMatch(options, "Milici Nikolic");
        assertThat(match).isNotNull();
        assertThat(match.value()).isEqualTo("v1");

        SlotOption match2 = SlotResolvers.fuzzyMatch(options, "Stefan");
        assertThat(match2).isNotNull();
        assertThat(match2.value()).isEqualTo("v2");
    }

    @Test
    void fuzzyMatch_handlesDeclination() {
        List<SlotOption> options = List.of(
                new SlotOption("v1", "Milica Nikolic", null)
        );
        // "MILICI" (dative) treba da matchuje "Milica" preko 3-char prefix-a
        SlotOption match = SlotResolvers.fuzzyMatch(options, "MILICI");
        assertThat(match).isNotNull();
        assertThat(match.value()).isEqualTo("v1");
    }

    @Test
    void fuzzyMatch_returnsNull_onNoMatch() {
        List<SlotOption> options = List.of(
                new SlotOption("v1", "Milica Nikolic", null)
        );
        SlotOption match = SlotResolvers.fuzzyMatch(options, "Marko Petrovic");
        assertThat(match).isNull();
    }

    @Test
    void fuzzyMatch_returnsNull_onEmptyInputs() {
        assertThat(SlotResolvers.fuzzyMatch(null, "anything")).isNull();
        assertThat(SlotResolvers.fuzzyMatch(List.of(), "anything")).isNull();
        assertThat(SlotResolvers.fuzzyMatch(
                List.of(new SlotOption("v1", "Milica", null)), null)).isNull();
    }

    @Test
    void fuzzyMatch_skipsTooShortTokens() {
        // Tokeni < 3 chars se preskacu, mora ostati barem jedan smislen
        List<SlotOption> options = List.of(
                new SlotOption("v1", "Milica Nikolic", null)
        );
        // "ja" je <3 chars, "Milica" je smislen
        SlotOption match = SlotResolvers.fuzzyMatch(options, "Milica ja");
        assertThat(match).isNotNull();
    }
}
