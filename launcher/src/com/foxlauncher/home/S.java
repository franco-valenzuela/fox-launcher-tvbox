package com.foxlauncher.home;

import java.util.Locale;

/**
 * The launcher's two languages. Every text on screen is written once in Spanish and once in
 * English, side by side where it is used; a box set to Spanish shows Spanish and any other
 * language shows English.
 */
final class S {

    private S() {
    }

    static boolean spanish() {
        return "es".equals(Locale.getDefault().getLanguage());
    }

    /** Picks the text for the language the box is set to. */
    static String t(String es, String en) {
        return spanish() ? es : en;
    }
}
