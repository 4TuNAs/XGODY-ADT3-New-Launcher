package com.homeprojector.settings;

import java.util.Locale;

/** Two-language text: Russian when the system language is Russian or Ukrainian, English otherwise. */
final class L {
    private L() {
    }

    static boolean ru() {
        String lang = Locale.getDefault().getLanguage();
        return "ru".equals(lang) || "uk".equals(lang);
    }

    static String t(String en, String ru) {
        return ru() ? ru : en;
    }
}
