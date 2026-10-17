package io.github.maxijaxi.sponsor.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class LangTest {
    @Test
    void germanClientLocalesMapToGerman() {
        assertEquals(Lang.DE, Lang.fromLocale("de_de", Lang.EN));
        assertEquals(Lang.DE, Lang.fromLocale("de_at", Lang.EN));
        assertEquals(Lang.DE, Lang.fromLocale("de_ch", Lang.EN));
        assertEquals(Lang.DE, Lang.fromLocale("DE-de", Lang.EN));
    }

    @Test
    void otherLocalesUseTheFallback() {
        assertEquals(Lang.EN, Lang.fromLocale("en_us", Lang.DE));
        assertEquals(Lang.EN, Lang.fromLocale("fr_fr", Lang.EN));
        assertEquals(Lang.DE, Lang.fromLocale("fr_fr", Lang.DE));
        assertEquals(Lang.EN, Lang.fromLocale("", Lang.EN));
        assertEquals(Lang.EN, Lang.fromLocale(null, Lang.EN));
        // "deu_..." ist keine Minecraft-Spielsprache und darf nicht als Deutsch erkannt werden.
        assertEquals(Lang.EN, Lang.fromLocale("deu_x", Lang.EN));
    }

    @Test
    void configCodes() {
        assertEquals(Lang.DE, Lang.fromCode(" DE ", Lang.EN));
        assertEquals(Lang.EN, Lang.fromCode("en", Lang.DE));
        assertEquals(Lang.EN, Lang.fromCode("xx", Lang.EN));
        assertEquals("de", Lang.DE.code());
    }

    @Test
    void coreTextsExistInBothLanguages() {
        for (CoreText text : CoreText.values()) {
            String en = text.format(Lang.EN);
            String de = text.format(Lang.DE);
            assertFalse(en.isBlank(), text.name());
            assertFalse(de.isBlank(), text.name());
        }
        assertEquals("Alice keeps access through other sponsors", CoreText.DETAIL_KEEPS_ACCESS.format(Lang.EN, "player", "Alice"));
    }
}
