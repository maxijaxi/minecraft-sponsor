package io.github.maxijaxi.sponsor.core;

import java.util.Locale;

/** Unterstützte Sprachen. Englisch ist der Standard, Deutsch bekommen Clients mit deutscher Spielsprache. */
public enum Lang {
    EN("en"),
    DE("de");

    private final String code;

    Lang(String code) {
        this.code = code;
    }

    /** Kürzel wie in der Config ({@code "en"}, {@code "de"}). */
    public String code() {
        return code;
    }

    /** {@code "en"}/{@code "de"} (Groß-/Kleinschreibung egal), sonst {@code fallback}. */
    public static Lang fromCode(String code, Lang fallback) {
        if (code != null) {
            for (Lang lang : values()) {
                if (lang.code.equalsIgnoreCase(code.strip())) {
                    return lang;
                }
            }
        }
        return fallback;
    }

    /**
     * Sprache zu einer Minecraft-Spielsprache wie {@code "de_de"}, {@code "de_at"} oder {@code "en_us"}.
     * Nicht unterstützte Sprachen (z. B. {@code "fr_fr"}) ergeben {@code fallback}.
     */
    public static Lang fromLocale(String locale, Lang fallback) {
        if (locale == null) {
            return fallback;
        }
        String language = locale.strip().toLowerCase(Locale.ROOT);
        int separator = language.indexOf('_') >= 0 ? language.indexOf('_') : language.indexOf('-');
        if (separator >= 0) {
            language = language.substring(0, separator);
        }
        return fromCode(language, fallback);
    }
}
